package com.microbook.notificationservice.infrastructure.config;

import com.microbook.notificationservice.infrastructure.messaging.dlt.DeadLetterTopicHandler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del consumer Kafka para notification-service.
 *
 * El libro crea un ConcurrentKafkaListenerContainerFactory vacío
 * sin manejo de errores — cualquier excepción en el listener queda
 * silenciada o deja el consumer en bucle infinito.
 *
 * Aquí configuramos tres capas de resiliencia:
 *
 * ══════════════════════════════════════════════════════════════════
 * CAPA 1 — Reintentos con ExponentialBackOff
 * ══════════════════════════════════════════════════════════════════
 * Si el listener lanza una excepción (ej: base de datos temporalmente
 * caída), el DefaultErrorHandler reintenta el mismo mensaje con
 * backoff exponencial:
 *
 *   Intento 1 → fallo → espera 1s
 *   Intento 2 → fallo → espera 2s
 *   Intento 3 → fallo → espera 4s
 *   Intento 4 → fallo → mueve al DLT (Dead Letter Topic)
 *
 * ══════════════════════════════════════════════════════════════════
 * CAPA 2 — Dead Letter Topic (DLT)
 * ══════════════════════════════════════════════════════════════════
 * Tras agotar los reintentos, DeadLetterPublishingRecoverer mueve el
 * mensaje al topic "orders.DLT". Esto permite:
 *
 *   - El consumer PRINCIPAL continúa procesando mensajes nuevos
 *     (no se bloquea en un mensaje problemático)
 *   - Los mensajes del DLT se pueden inspeccionar, corregir y
 *     republicar manualmente al topic original
 *   - Audit trail completo de mensajes que fallaron
 *
 * ══════════════════════════════════════════════════════════════════
 * CAPA 3 — Listener del DLT (DeadLetterTopicHandler)
 * ══════════════════════════════════════════════════════════════════
 * Un listener separado monitorea el DLT, logguea y alerta.
 * En Fase 6 se conectará a las métricas de Prometheus.
 *
 * Ruta: notification-service/src/main/java/…/infrastructure/config/
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,           groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");

        // Deserialización
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Confiar en el package del evento para deserialización automática
        config.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.microbook.orderservice.domain.event");

        // NO hacer commit automático del offset:
        // el offset se hace commit solo cuando el listener termina SIN excepción.
        // Si el listener falla, el mensaje se reprocesa.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Factory con DefaultErrorHandler configurado con:
     *   - ExponentialBackOff:          3 reintentos, backoff 1s → 2s → 4s
     *   - DeadLetterPublishingRecoverer: mueve al DLT tras agotar reintentos
     *
     * El KafkaTemplate inyectado aquí es el del PRODUCER (necesario para
     * que el recoverer pueda escribir en el DLT). Se configurará en
     * KafkaProducerConfig (ver nota al final de esta clase).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate,
            DeadLetterTopicHandler dltHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory());

        // ── Backoff exponencial ───────────────────────────────────────────────
        var backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1_000L);   // 1s primer reintento
        backOff.setMultiplier(2.0);            // duplica en cada intento
        backOff.setMaxInterval(10_000L);       // nunca más de 10s entre reintentos
        backOff.setMaxElapsedTime(30_000L);    // máximo 30s en total de reintentos

        // ── Recoverer: publica en DLT tras agotar reintentos ──────────────────
        // DeadLetterPublishingRecoverer añade automáticamente al DLT:
        //   - El mensaje original completo (headers + body)
        //   - Headers de diagnóstico: excepción, stack trace, topic origen, offset
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // ── Error handler: une backoff + recoverer ────────────────────────────
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Excepciones que NO deben reintentarse (fallos permanentes):
        // Si el mensaje tiene JSON inválido, reintentar 3 veces no lo arreglará.
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                IllegalArgumentException.class
        );

        factory.setCommonErrorHandler(errorHandler);

        // MANUAL_IMMEDIATE: el offset se confirma en el momento exacto en que
        // el listener llama ack.acknowledge(). Requerido para inyectar Acknowledgment
        // como parámetro en el método — AckMode.RECORD no lo permite.
        factory.getContainerProperties()
                .setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}