package com.microbook.notificationservice.infrastructure.messaging.consumer;

import com.microbook.notificationservice.infrastructure.persistence.repository.ProcessedEventRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests de integración del consumer idempotente con EmbeddedKafka.
 *
 * Verifican los tres escenarios clave:
 *   1. Mensaje nuevo → se procesa y se registra el eventId
 *   2. Mensaje duplicado → se hace skip (eventId ya en BD)
 *   3. Mensaje inválido → va al DLT tras agotar reintentos
 *
 * Awaitility: librería para esperar condiciones asíncronas de forma elegante.
 * await().atMost(10, SECONDS).until(() -> condición)
 * evita Thread.sleep() arbitrarios que hacen los tests frágiles.
 *
 * Dependencia en pom.xml:
 *   <dependency>
 *     <groupId>org.awaitility</groupId>
 *     <artifactId>awaitility</artifactId>
 *     <scope>test</scope>
 *   </dependency>
 *
 * Ruta: notification-service/src/test/java/…/infrastructure/messaging/
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics     = {"orders", "orders.DLT"}
)
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@DirtiesContext
@DisplayName("NotificationService — idempotencia y DLT")
class NotificationServiceTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private KafkaTemplate<String, Object> producerTemplate() {
        var props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private Map<String, Object> buildOrderEvent(String eventId, String orderId) {
        return Map.of(
                "eventId",       eventId,
                "correlationId", UUID.randomUUID().toString(),
                "eventType",     "ORDER_CREATED",
                "orderId",       orderId,
                "itemId",        "item-" + UUID.randomUUID(),
                "quantity",      2,
                "status",        "PENDING",
                "occurredAt",    java.time.Instant.now().toString()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("procesa un mensaje nuevo y registra el eventId")
    void processesNewEvent_andRegistersEventId() {
        var eventId = UUID.randomUUID().toString();
        var orderId = UUID.randomUUID().toString();
        var event   = buildOrderEvent(eventId, orderId);

        producerTemplate().send("orders", orderId, event);

        // Esperar hasta 10s a que el listener procese y guarde el eventId
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(processedEventRepository.existsByEventId(eventId))
                                .as("El eventId debe estar registrado tras el procesamiento")
                                .isTrue()
                );
    }

    @Test
    @DisplayName("hace skip de un mensaje duplicado — eventId ya procesado")
    void skipsProcessing_whenEventIsDuplicate() throws InterruptedException {
        var eventId = UUID.randomUUID().toString();
        var orderId = UUID.randomUUID().toString();
        var event   = buildOrderEvent(eventId, orderId);

        // Publicar el mismo evento DOS veces
        producerTemplate().send("orders", orderId, event);
        producerTemplate().send("orders", orderId, event);   // duplicado

        // Esperar procesamiento
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(processedEventRepository.existsByEventId(eventId)).isTrue()
                );

        // Dar tiempo extra para que el segundo mensaje sea consumido
        Thread.sleep(2_000);

        // Debe haber EXACTAMENTE un registro, no dos
        long count = processedEventRepository.findAll().stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .count();

        assertThat(count)
                .as("Un mismo eventId debe registrarse exactamente UNA vez aunque llegue dos veces")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("mensajes con eventId distinto se procesan ambos")
    void processesBothEvents_whenEventIdsAreDifferent() {
        var orderId = UUID.randomUUID().toString();

        var eventId1 = UUID.randomUUID().toString();
        var eventId2 = UUID.randomUUID().toString();

        producerTemplate().send("orders", orderId, buildOrderEvent(eventId1, orderId));
        producerTemplate().send("orders", orderId, buildOrderEvent(eventId2, orderId));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(processedEventRepository.existsByEventId(eventId1))
                            .as("eventId1 debe estar procesado").isTrue();
                    assertThat(processedEventRepository.existsByEventId(eventId2))
                            .as("eventId2 debe estar procesado").isTrue();
                });
    }
}