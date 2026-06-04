package com.microbook.notificationservice.infrastructure.messaging.consumer;

import com.microbook.notificationservice.infrastructure.persistence.entity.ProcessedEventEntity;
import com.microbook.notificationservice.infrastructure.persistence.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Consumer Kafka idempotente para OrderEvent.
 *
 * El libro tiene:
 *   public void consume(OrderEvent event) {
 *       System.out.println("Received order event: " + event.getOrderId());
 *   }
 *
 * Problemas de esa implementación:
 *   ✗ No es idempotente: si el mensaje llega dos veces, actúa dos veces
 *   ✗ No hace ACK/NACK manual: si falla, el mensaje se pierde
 *   ✗ Sin logging estructurado: imposible depurar en producción
 *   ✗ Sin correlationId: imposible rastrear el flujo completo
 *
 * Nuestra implementación corrige todo eso con el patrón:
 *
 *   ┌─ recibir mensaje
 *   │
 *   ├─ ¿ya procesé este eventId? (tabla processed_events)
 *   │     SÍ → skip + ACK (no hacer nada, confirmar offset)
 *   │     NO → continuar
 *   │
 *   ├─ procesar lógica de negocio (enviar notificación)
 *   │
 *   ├─ guardar eventId en processed_events (misma transacción)
 *   │
 *   └─ ACK (commit offset) → Kafka sabe que el mensaje fue procesado
 *
 * La atomicidad entre "procesar" y "guardar eventId" es crítica:
 * si el proceso cae entre esos dos pasos, el próximo intento
 * procesará el mensaje de nuevo (correcto: no hay registro del eventId).
 *
 * Ruta: notification-service/src/main/java/…/infrastructure/messaging/consumer/
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = Objects.requireNonNull(processedEventRepository);
    }

    /**
     * Listener principal del topic "orders".
     *
     * topics  = "orders"                     → escucha el topic de eventos de Order
     * groupId = "notification-group"         → grupo de consumers del servicio
     * containerFactory = "kafkaListenerContainerFactory" → usa nuestra config con reintentos + DLT
     *
     * ConsumerRecord<String, Object>: recibimos el mensaje completo (clave + valor + headers + metadata)
     * en lugar del valor directo, para poder acceder a los headers de diagnóstico.
     *
     * Acknowledgment ack: para confirmar el offset manualmente SOLO si el procesamiento fue exitoso.
     */
    @KafkaListener(
            topics           = "orders",
            groupId          = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOrderEvent(
            ConsumerRecord<String, Object> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        // Extraer el evento del record
        Map<String, Object> payload = extractPayload(record);
        if (payload == null) {
            log.warn("Received non-map payload on topic={} partition={} offset={} — skipping",
                    topic, partition, offset);
            ack.acknowledge();
            return;
        }

        String eventId       = (String) payload.get("eventId");
        String correlationId = (String) payload.get("correlationId");
        String eventType     = extractEventType(payload);
        String orderId       = (String) payload.get("orderId");

        // Propagar al MDC para que aparezca en todos los logs de este mensaje
        MDC.put("correlationId", correlationId != null ? correlationId : "unknown");
        MDC.put("eventId",       eventId       != null ? eventId       : "unknown");
        MDC.put("orderId",       orderId        != null ? orderId       : "unknown");

        try {
            log.info("Received {} eventId='{}' orderId='{}' topic={} partition={} offset={}",
                    eventType, eventId, orderId, topic, partition, offset);

            // ── IDEMPOTENCIA: verificar si ya procesamos este evento ──────────
            if (eventId != null && isDuplicate(eventId)) {
                log.info("Duplicate event detected eventId='{}' — skipping", eventId);
                ack.acknowledge();  // confirmar offset para no recibirlo de nuevo
                return;
            }

            // ── PROCESAR: lógica de negocio según el tipo de evento ──────────
            processEvent(eventType, payload);

            // ── REGISTRAR: guardar eventId EN LA MISMA TRANSACCIÓN ───────────
            if (eventId != null) {
                markAsProcessed(eventId, eventType, orderId);
            }

            // ── ACK: confirmar offset solo si todo fue bien ───────────────────
            ack.acknowledge();

            log.info("Successfully processed {} eventId='{}' orderId='{}'",
                    eventType, eventId, orderId);

        } catch (DataIntegrityViolationException e) {
            // Condición de carrera: dos instancias del consumer intentaron
            // insertar el mismo eventId simultáneamente — uno ganó, el otro
            // debe hacer skip. El índice UNIQUE de la BD lo garantiza.
            log.warn("Race condition on eventId='{}' — another instance processed it first, skipping",
                    eventId);
            ack.acknowledge();

        } catch (Exception e) {
            // NO llamar ack.acknowledge() → Kafka reintentará este mensaje
            // según el backoff configurado en DefaultErrorHandler.
            log.error("Failed to process {} eventId='{}' orderId='{}' — will retry",
                    eventType, eventId, orderId, e);
            throw e;   // re-lanzar para que DefaultErrorHandler gestione los reintentos

        } finally {
            MDC.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lógica de negocio por tipo de evento
    // ─────────────────────────────────────────────────────────────────────────

    private void processEvent(String eventType, Map<String, Object> payload) {
        switch (eventType) {
            case "ORDER_CREATED"   -> handleOrderCreated(payload);
            case "ORDER_CONFIRMED" -> handleOrderConfirmed(payload);
            case "ORDER_CANCELLED" -> handleOrderCancelled(payload);
            default -> log.warn("Unknown event type='{}' — ignoring", eventType);
        }
    }

    private void handleOrderCreated(Map<String, Object> payload) {
        String orderId = (String) payload.get("orderId");
        String itemId  = (String) payload.get("itemId");
        log.info("ORDER_CREATED → sending 'order received' notification orderId='{}' itemId='{}'",
                orderId, itemId);
        /*
         * TODO: integrar con servicio de email / push / SMS
         * notificationSender.sendOrderCreated(orderId, itemId);
         */
    }

    private void handleOrderConfirmed(Map<String, Object> payload) {
        String orderId = (String) payload.get("orderId");
        log.info("ORDER_CONFIRMED → sending 'order confirmed' notification orderId='{}'", orderId);
        /*
         * TODO: integrar con servicio de email / push / SMS
         * notificationSender.sendOrderConfirmed(orderId);
         */
    }

    private void handleOrderCancelled(Map<String, Object> payload) {
        String orderId = (String) payload.get("orderId");
        log.info("ORDER_CANCELLED → sending 'order cancelled' notification orderId='{}'", orderId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de idempotencia
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isDuplicate(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    private void markAsProcessed(String eventId, String eventType, String orderId) {
        processedEventRepository.save(
                new ProcessedEventEntity(eventId, eventType, orderId, Instant.now())
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de deserialización
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(ConsumerRecord<String, Object> record) {
        if (record.value() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String extractEventType(Map<String, Object> payload) {
        Object type = payload.get("eventType");
        if (type instanceof Map<?, ?> enumMap) {
            // Jackson deserializa enums con @type como mapa si no hay typeinfo.
            // Se usa get() + null-check en lugar de getOrDefault() porque Map<?,?>
            // captura el valor como wildcard y el compilador rechaza pasar String como V.
            Object name = enumMap.get("name");
            return name != null ? name.toString() : "UNKNOWN";
        }
        return type != null ? type.toString() : "UNKNOWN";
    }
}