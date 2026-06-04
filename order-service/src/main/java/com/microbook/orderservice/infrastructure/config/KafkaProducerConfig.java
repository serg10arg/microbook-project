package com.microbook.orderservice.infrastructure.config;

import com.microbook.orderservice.domain.event.OrderEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración programática del producer Kafka para order-service.
 *
 * ¿Por qué configuración programática y no solo application.yml?
 * ══════════════════════════════════════════════════════════════
 * El libro pone todo en application.properties/yml, lo que funciona
 * para casos simples. La configuración programática da tres ventajas:
 *
 *   1. TYPE SAFETY: los valores son constantes tipadas (ProducerConfig.ACKS_CONFIG)
 *      en lugar de strings "spring.kafka.producer.acks". Un typo en el yml
 *      no falla en compilación; un typo en la constante sí.
 *
 *   2. TESTABILIDAD: en tests podemos crear un ProducerFactory apuntando
 *      a un broker embebido (EmbeddedKafka) sin tocar la configuración real.
 *
 *   3. LEGIBILIDAD: todas las decisiones de configuración están en un solo
 *      lugar con su justificación como comentario.
 *
 * Decisiones de configuración:
 * ════════════════════════════
 * KEY   = StringSerializer   → la clave del mensaje es el orderId (String)
 * VALUE = JsonSerializer     → el valor es OrderEvent serializado como JSON
 *
 * acks = "all"               → el broker confirma cuando TODOS los réplicas
 *                              han recibido el mensaje. Máxima durabilidad.
 *                              Costo: mayor latencia vs acks=1 o acks=0.
 *
 * retries = 3                → reintenta hasta 3 veces si el broker no responde.
 *                              Combinado con acks=all evita pérdida de mensajes.
 *
 * idempotent = true          → garantiza que los reintentos no producen duplicados
 *                              (requiere acks=all y retries>0).
 *
 * Ruta: order-service/src/main/java/com/microbook/orderservice/infrastructure/config/
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * ProducerFactory: fábrica de producers configurada una vez.
     * Cada KafkaTemplate obtiene su producer de aquí.
     */
    @Bean
    public ProducerFactory<String, OrderEvent> orderEventProducerFactory() {
        Map<String, Object> config = new HashMap<>();

        // ── Conexión ─────────────────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // ── Serialización ─────────────────────────────────────────────────────
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Incluir el tipo de clase Java en el header del mensaje Kafka.
        // Permite al consumer deserializar al tipo correcto sin configuración extra.
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);

        // ── Durabilidad ───────────────────────────────────────────────────────
        // "all" = esperar confirmación de leader + todas las réplicas in-sync
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Reintentar hasta 3 veces ante fallos transitorios del broker
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Idempotencia: evita duplicados en caso de reintento
        // (requiere acks=all y retries > 0)
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // ── Rendimiento ───────────────────────────────────────────────────────
        // Tiempo máximo de espera antes de enviar un batch aunque no esté lleno
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Tamaño máximo del batch de mensajes antes de enviar
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate<String, OrderEvent>:
     *   K = String      → clave del mensaje (orderId)
     *   V = OrderEvent  → valor del mensaje (evento serializado a JSON)
     *
     * Es el bean que inyecta OrderProducerService para publicar eventos.
     */
    @Bean
    public KafkaTemplate<String, OrderEvent> orderEventKafkaTemplate() {
        KafkaTemplate<String, OrderEvent> template =
                new KafkaTemplate<>(orderEventProducerFactory());

        // Observación: en producción activar transacciones si necesitas
        // garantía de atomicidad entre BD y Kafka (Outbox Pattern).
        // template.setTransactionIdPrefix("order-tx-");

        return template;
    }
}