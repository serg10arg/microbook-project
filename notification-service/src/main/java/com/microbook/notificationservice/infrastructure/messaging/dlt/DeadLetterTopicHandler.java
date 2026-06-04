package com.microbook.notificationservice.infrastructure.messaging.dlt;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listener del Dead Letter Topic (DLT).
 *
 * ¿Qué es el DLT y por qué existe?
 * ══════════════════════════════════
 * Cuando un mensaje falla todos los reintentos configurados en
 * DefaultErrorHandler, el DeadLetterPublishingRecoverer lo mueve
 * automáticamente al topic "orders.DLT" (nombre del topic original + ".DLT").
 *
 * Sin DLT, un mensaje que siempre falla bloquearía la partición:
 * el consumer no avanzaría nunca a los mensajes siguientes.
 *
 * Con DLT:
 *   ✓ El consumer principal continúa procesando normalmente
 *   ✓ Los mensajes fallidos se preservan para análisis
 *   ✓ Se pueden republicar al topic original una vez corregido el bug
 *   ✓ Audit trail completo: cuándo falló, qué excepción, en qué offset
 *
 * Headers que añade DeadLetterPublishingRecoverer automáticamente:
 *   kafka_dlt-original-topic       → topic de origen ("orders")
 *   kafka_dlt-original-partition   → partición de origen
 *   kafka_dlt-original-offset      → offset del mensaje fallido
 *   kafka_dlt-exception-fqcn       → clase de la excepción
 *   kafka_dlt-exception-message    → mensaje de la excepción
 *   kafka_dlt-exception-stacktrace → stack trace completo
 *
 * Ruta: notification-service/src/main/java/…/infrastructure/messaging/dlt/
 */
@Component
public class DeadLetterTopicHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterTopicHandler.class);

    /**
     * Escucha el DLT del topic "orders".
     *
     * groupId separado ("notification-dlt-group") para que este listener
     * tenga su propio offset y no interfiera con el consumer principal.
     *
     * Spring Kafka nombra el DLT automáticamente: "orders" + ".DLT" = "orders.DLT"
     */
    @KafkaListener(
            topics  = "orders.DLT",
            groupId = "notification-dlt-group"
    )
    public void handleDeadLetter(
            ConsumerRecord<String, Object> record,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_FQCN,      required = false)
            String exceptionClass,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE,   required = false)
            byte[] exceptionMessage,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC,      required = false)
            byte[] originalTopic,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_OFFSET,     required = false)
            byte[] originalOffset,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_PARTITION,  required = false)
            byte[] originalPartition) {

        // Extraer información de diagnóstico de los headers
        String topic     = originalTopic     != null ? new String(originalTopic,     StandardCharsets.UTF_8) : "unknown";
        String offset    = originalOffset    != null ? new String(originalOffset,    StandardCharsets.UTF_8) : "unknown";
        String partition = originalPartition != null ? new String(originalPartition, StandardCharsets.UTF_8) : "unknown";
        String exMsg     = exceptionMessage  != null ? new String(exceptionMessage,  StandardCharsets.UTF_8) : "unknown";

        // Propagar correlationId al MDC si está disponible en el mensaje
        extractCorrelationId(record);

        try {
            log.error(
                    """
                    ══ DEAD LETTER EVENT ══════════════════════════════════════════
                      key            : {}
                      original topic : {} partition={} offset={}
                      exception      : {}
                      exception msg  : {}
                      payload        : {}
                    ═══════════════════════════════════════════════════════════════
                    """,
                    record.key(),
                    topic, partition, offset,
                    exceptionClass,
                    exMsg,
                    record.value()
            );

            /*
             * TODO Fase 6: enviar alerta a Prometheus/Grafana
             *   meterRegistry.counter("kafka.dlt.events",
             *       "topic", topic, "exception", exceptionClass).increment();
             *
             * TODO producción: enviar alerta a PagerDuty / Slack / email
             * para que un humano revise el mensaje y decida si republicarlo.
             */

        } finally {
            MDC.clear();
        }
    }

    /**
     * Extrae el correlationId del payload si es un OrderEvent,
     * para que aparezca en los logs del DLT y se pueda rastrear.
     */
    private void extractCorrelationId(ConsumerRecord<String, Object> record) {
        try {
            if (record.value() instanceof java.util.Map<?, ?> map) {
                Object corrId = map.get("correlationId");
                if (corrId != null) MDC.put("correlationId", corrId.toString());
            }
        } catch (Exception ignored) {
            // Si no podemos extraer el correlationId no es un error crítico
        }
    }
}