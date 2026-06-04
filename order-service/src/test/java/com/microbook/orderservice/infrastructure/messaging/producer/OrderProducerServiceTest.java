package com.microbook.orderservice.infrastructure.messaging.producer;

import com.microbook.orderservice.domain.event.OrderEvent;
import com.microbook.orderservice.domain.model.Order;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del OrderProducerService usando @EmbeddedKafka.
 *
 * ¿Por qué @EmbeddedKafka y no mockear KafkaTemplate?
 * ═════════════════════════════════════════════════════
 * Mockear KafkaTemplate con Mockito verifica que se LLAMA al método send(),
 * pero no que el mensaje llega correctamente al broker ni que se serializa
 * bien como JSON.
 *
 * @EmbeddedKafka levanta un broker Kafka real en memoria durante el test.
 * El test verifica el flujo COMPLETO:
 *   OrderProducerService.publishOrderCreated()
 *     → KafkaTemplate.send()
 *       → broker embebido
 *         → consumer del test
 *           → mensaje recibido y deserializado correctamente
 *
 * @DirtiesContext: reinicia el contexto Spring entre tests.
 * Necesario cuando se usa EmbeddedKafka para evitar contaminación de estado.
 *
 * Ruta: order-service/src/test/java/…/infrastructure/messaging/
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(
        partitions = 1,
        topics     = {"orders"}
)
@DirtiesContext
@DisplayName("OrderProducerService — integración con EmbeddedKafka")
class OrderProducerServiceTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private OrderProducerService producerService;
    private KafkaMessageListenerContainer<String, OrderEvent> container;
    private BlockingQueue<ConsumerRecord<String, OrderEvent>> records;

    @BeforeEach
    void setUp() {
        // Configurar consumer del test para leer lo que el producer publica
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group-" + UUID.randomUUID(), // grupo único por test
                "true",
                embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.microbook.orderservice.domain.event");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                OrderEvent.class.getName());

        records = new LinkedBlockingQueue<>();

        var factory = new DefaultKafkaConsumerFactory<String, OrderEvent>(consumerProps);
        var containerProps = new ContainerProperties("orders");
        containerProps.setMessageListener(
                (MessageListener<String, OrderEvent>) records::add
        );

        container = new KafkaMessageListenerContainer<>(factory, containerProps);
        container.start();

        // Esperar a que el container esté listo para consumir
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        container.stop();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Order buildOrder() {
        return Order.create("item-uuid-456", 3);
    }

    private static final String CORRELATION_ID = "test-correlation-" + UUID.randomUUID();

    // ══════════════════════════════════════════════════════════════════════════
    // publishOrderCreated()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("publishes ORDER_CREATED event with correct fields")
    void publishesOrderCreated_withCorrectFields() throws InterruptedException {
        var order = buildOrder();

        producerService.publishOrderCreated(order, CORRELATION_ID);

        // Esperar hasta 5s a que llegue el mensaje (el broker es async)
        var record = records.poll(5, TimeUnit.SECONDS);
        assertThat(record).isNotNull();

        var event = record.value();
        assertThat(event.eventType()).isEqualTo(OrderEvent.EventType.ORDER_CREATED);
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.itemId()).isEqualTo(order.getItemId());
        assertThat(event.quantity()).isEqualTo(order.getQuantity());
        assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(event.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("uses orderId as Kafka message key for partition ordering")
    void usesOrderId_asMessageKey() throws InterruptedException {
        var order = buildOrder();

        producerService.publishOrderCreated(order, CORRELATION_ID);

        var record = records.poll(5, TimeUnit.SECONDS);
        assertThat(record).isNotNull();

        // La clave del mensaje Kafka debe ser el orderId
        // para que todos los eventos de una orden vayan a la misma partición
        assertThat(record.key()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("eventId is unique for each published event")
    void eventId_isUnique_perPublication() throws InterruptedException {
        var order = buildOrder();

        producerService.publishOrderCreated(order, CORRELATION_ID);
        producerService.publishOrderCreated(order, CORRELATION_ID);

        var record1 = records.poll(5, TimeUnit.SECONDS);
        var record2 = records.poll(5, TimeUnit.SECONDS);

        assertThat(record1).isNotNull();
        assertThat(record2).isNotNull();

        // Cada publicación genera un eventId distinto aunque sea la misma orden
        assertThat(record1.value().eventId()).isNotEqualTo(record2.value().eventId());
    }

    @Test
    @DisplayName("generates correlationId when null is provided")
    void generatesCorrelationId_whenNullProvided() throws InterruptedException {
        var order = buildOrder();

        // null como correlationId → el producer genera uno automáticamente
        producerService.publishOrderCreated(order, null);

        var record = records.poll(5, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.value().correlationId()).isNotBlank();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // publishOrderConfirmed()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("publishes ORDER_CONFIRMED event with CONFIRMED status")
    void publishesOrderConfirmed() throws InterruptedException {
        var order = buildOrder();
        order.confirm();

        producerService.publishOrderConfirmed(order, CORRELATION_ID);

        var record = records.poll(5, TimeUnit.SECONDS);
        assertThat(record).isNotNull();

        var event = record.value();
        assertThat(event.eventType()).isEqualTo(OrderEvent.EventType.ORDER_CONFIRMED);
        assertThat(event.status()).isEqualTo("CONFIRMED");
        assertThat(event.correlationId()).isEqualTo(CORRELATION_ID);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // publishOrderCancelled()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("publishes ORDER_CANCELLED event with CANCELLED status")
    void publishesOrderCancelled() throws InterruptedException {
        var order = buildOrder();
        order.cancel();

        producerService.publishOrderCancelled(order, CORRELATION_ID);

        var record = records.poll(5, TimeUnit.SECONDS);
        assertThat(record).isNotNull();

        var event = record.value();
        assertThat(event.eventType()).isEqualTo(OrderEvent.EventType.ORDER_CANCELLED);
        assertThat(event.status()).isEqualTo("CANCELLED");
    }
}