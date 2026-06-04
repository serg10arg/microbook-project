package com.microbook.orderservice.infrastructure.messaging.producer;

import com.microbook.orderservice.domain.event.OrderEvent;
import com.microbook.orderservice.domain.event.OrderEvent.EventType;
import com.microbook.orderservice.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio productor de eventos Kafka para Order.
 *
 * Responsabilidades:
 * ══════════════════
 *   1. Construir el OrderEvent correcto para cada transición de estado
 *   2. Determinar la CLAVE del mensaje (orderId) para garantizar ordering
 *   3. Publicar al topic correcto
 *   4. Loguear confirmación o error de entrega
 *   5. Propagar el correlationId en el MDC para trazabilidad
 *
 * ¿Por qué orderId como clave del mensaje?
 * ═════════════════════════════════════════
 * Kafka garantiza ordering DENTRO de una misma partición.
 * Al usar orderId como clave, todos los eventos de la misma orden
 * van siempre a la misma partición → el consumer los recibe en orden:
 *
 *   ORDER_CREATED → ORDER_CONFIRMED  (siempre en este orden para un orderId)
 *
 * Si usáramos null como clave (round-robin), los eventos de una misma
 * orden podrían llegar en desorden al consumer.
 *
 * ¿Por qué KafkaTemplate.send() devuelve CompletableFuture?
 * ══════════════════════════════════════════════════════════
 * El envío a Kafka es asíncrono por naturaleza. send() devuelve
 * inmediatamente un CompletableFuture que se completa cuando el broker
 * confirma la recepción (con acks=all, cuando todas las réplicas confirman).
 *
 * Dos estrategias:
 *   fire-and-forget: send() sin esperar → máximo rendimiento, sin garantías
 *   with-callback:   whenComplete() → log de confirmación/error sin bloquear
 *
 * Usamos with-callback: no bloqueamos el hilo de negocio pero sabemos
 * si el mensaje llegó o no.
 *
 * Ruta: order-service/src/main/java/…/infrastructure/messaging/producer/
 */
@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);

    /** Nombre del topic en Kafka. Configurable desde application.yml. */
    @Value("${kafka.topics.orders:orders}")
    private String ordersTopic;

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OrderProducerService(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API pública: un método por transición de estado
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Publica un evento ORDER_CREATED cuando se crea una nueva orden.
     *
     * @param order         la orden recién creada
     * @param correlationId ID de correlación del request HTTP original
     */
    public void publishOrderCreated(Order order, String correlationId) {
        var event = OrderEvent.orderCreated(
                resolveCorrelationId(correlationId),
                order.getId(),
                order.getItemId(),
                order.getQuantity()
        );
        publish(event);
    }

    /**
     * Publica un evento ORDER_CONFIRMED cuando se confirma una orden.
     *
     * @param order         la orden confirmada
     * @param correlationId ID de correlación del request HTTP original
     */
    public void publishOrderConfirmed(Order order, String correlationId) {
        var event = OrderEvent.orderConfirmed(
                resolveCorrelationId(correlationId),
                order.getId(),
                order.getItemId(),
                order.getQuantity()
        );
        publish(event);
    }

    /**
     * Publica un evento ORDER_CANCELLED cuando se cancela una orden.
     *
     * @param order         la orden cancelada
     * @param correlationId ID de correlación del request HTTP original
     */
    public void publishOrderCancelled(Order order, String correlationId) {
        var event = OrderEvent.orderCancelled(
                resolveCorrelationId(correlationId),
                order.getId(),
                order.getItemId(),
                order.getQuantity()
        );
        publish(event);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Publicación interna con logging y callback
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Publica el evento en Kafka con:
     *   - orderId como clave → ordering garantizado por partición
     *   - MDC con correlationId y eventId → trazabilidad en logs
     *   - Callback asíncrono → log de confirmación o error sin bloquear
     */
    private void publish(OrderEvent event) {
        // Propagar correlationId al MDC antes de loguear
        // El MDC se limpia automáticamente cuando el thread termina
        MDC.put("correlationId", event.correlationId());
        MDC.put("eventId",       event.eventId());
        MDC.put("orderId",       event.orderId());

        try {
            log.info("Publishing {} eventId='{}' orderId='{}' topic='{}'",
                    event.eventType(), event.eventId(), event.orderId(), ordersTopic);

            /*
             * send(topic, key, value):
             *   topic → destino en Kafka
             *   key   → orderId: determina la partición (ordering garantizado)
             *   value → el evento serializado como JSON
             */
            CompletableFuture<SendResult<String, OrderEvent>> future =
                    kafkaTemplate.send(ordersTopic, event.orderId(), event);

            // Callback asíncrono: se ejecuta cuando el broker confirma o rechaza
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    // Error al publicar: loguear con contexto completo
                    log.error("Failed to publish {} eventId='{}' orderId='{}': {}",
                            event.eventType(), event.eventId(), event.orderId(),
                            ex.getMessage(), ex);
                    /*
                     * TODO Fase 3 (Outbox Pattern):
                     * Aquí se guardaría el evento en la tabla outbox para
                     * reintentarlo automáticamente. Por ahora solo loguea.
                     */
                } else {
                    var metadata = result.getRecordMetadata();
                    log.info("Published {} eventId='{}' orderId='{}' → topic='{}' partition={} offset={}",
                            event.eventType(),
                            event.eventId(),
                            event.orderId(),
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset());
                }
            });

        } finally {
            // Limpiar MDC siempre, incluso si send() lanza excepción
            MDC.remove("correlationId");
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Si el correlationId viene del request HTTP (via header X-Correlation-ID),
     * lo reutilizamos. Si no viene (llamada interna, test, etc.), generamos uno nuevo.
     *
     * Esto garantiza que siempre hay un correlationId, nunca null.
     */
    private String resolveCorrelationId(String correlationId) {
        return (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();
    }
}