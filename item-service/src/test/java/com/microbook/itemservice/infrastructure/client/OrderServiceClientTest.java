package com.microbook.itemservice.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.microbook.itemservice.domain.exception.OrderServiceException;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios del OrderServiceClient usando WireMock.
 *
 * ¿Por qué WireMock y no Mockito para este test?
 * ═══════════════════════════════════════════════
 * OrderServiceClient hace llamadas HTTP reales a través de WebClient.
 * Mockear WebClient con Mockito es posible pero frágil: hay que mockear
 * WebClient → RequestHeadersUriSpec → RequestHeadersSpec → ResponseSpec…
 * un flujo de 4-5 objetos encadenados que hace los tests ilegibles.
 *
 * WireMock levanta un servidor HTTP real en un puerto aleatorio y
 * nos permite definir responses esperados declarativamente.
 * El cliente ni siquiera sabe que está hablando con un mock.
 *
 * Ventaja adicional: estos tests verifican que la serialización/deserialización
 * JSON funciona correctamente, algo que Mockito no puede hacer.
 *
 * Dependencia necesaria en item-service/pom.xml:
 *   <dependency>
 *     <groupId>org.wiremock</groupId>
 *     <artifactId>wiremock-standalone</artifactId>
 *     <version>3.5.4</version>
 *     <scope>test</scope>
 *   </dependency>
 *
 * Ruta de este archivo:
 *   item-service/src/test/java/com/microbook/itemservice/infrastructure/client/
 */
@DisplayName("OrderServiceClient")
class OrderServiceClientTest {

    private static WireMockServer wireMock;
    private static ObjectMapper objectMapper;
    private OrderServiceClient client;

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private static final String ORDER_ID = "order-uuid-123";
    private static final String ITEM_ID  = "item-uuid-456";

    @BeforeAll
    static void startWireMock() {
        // Puerto 0 → WireMock elige un puerto libre automáticamente.
        // Evita conflictos si otro proceso ya usa un puerto fijo.
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();  // limpiar stubs entre tests: cada test parte de cero

        // WebClient apuntando al WireMock local.
        // En producción, WebClientConfig lo configura con la URL real.
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();

        client = new OrderServiceClient(webClient);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findById()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns order when server responds 200")
        void returnsOrder_on200() throws Exception {
            var order = new OrderResponse(ORDER_ID, ITEM_ID, 3, "PENDING",
                    Instant.parse("2024-01-01T10:00:00Z"),
                    Instant.parse("2024-01-01T10:00:00Z"));

            wireMock.stubFor(get(urlEqualTo("/api/orders/" + ORDER_ID))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(order))));

            OrderResponse result = client.findById(ORDER_ID);

            assertThat(result.id()).isEqualTo(ORDER_ID);
            assertThat(result.itemId()).isEqualTo(ITEM_ID);
            assertThat(result.quantity()).isEqualTo(3);
            assertThat(result.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("throws OrderNotFound when server responds 404")
        void throwsOrderNotFound_on404() {
            wireMock.stubFor(get(urlEqualTo("/api/orders/" + ORDER_ID))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withBody("{\"title\":\"Order not found\"}")));

            assertThatThrownBy(() -> client.findById(ORDER_ID))
                    .isInstanceOf(OrderServiceException.OrderNotFound.class)
                    .satisfies(ex -> {
                        var notFound = (OrderServiceException.OrderNotFound) ex;
                        assertThat(notFound.getOrderId()).isEqualTo(ORDER_ID);
                        assertThat(notFound.getHttpStatus()).isEqualTo(404);
                    });
        }

        @Test
        @DisplayName("throws OrderServiceInternalError when server responds 500")
        void throwsInternalError_on500() {
            wireMock.stubFor(get(urlEqualTo("/api/orders/" + ORDER_ID))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal server error")));

            assertThatThrownBy(() -> client.findById(ORDER_ID))
                    .isInstanceOf(OrderServiceException.OrderServiceInternalError.class)
                    .satisfies(ex ->
                            assertThat(((OrderServiceException) ex).getHttpStatus()).isEqualTo(500)
                    );
        }

        @Test
        @DisplayName("WireMock receives the call even with response delay")
        void wiremockReceivesCall_withDelay() throws Exception {
            // Verifica que el cliente completa la llamada aunque haya latencia.
            // El timeout real se prueba en tests de integración con WebClientConfig.
            wireMock.stubFor(get(urlEqualTo("/api/orders/slow-order"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(100)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new OrderResponse("slow-order", ITEM_ID, 1, "PENDING",
                                            Instant.now(), Instant.now())))));

            assertThatCode(() -> client.findById("slow-order"))
                    .doesNotThrowAnyException();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/orders/slow-order")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findAll()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns list of orders on 200")
        void returnsList_on200() throws Exception {
            var order1 = new OrderResponse("id-1", ITEM_ID, 2, "PENDING",
                    Instant.now(), Instant.now());
            var order2 = new OrderResponse("id-2", ITEM_ID, 5, "CONFIRMED",
                    Instant.now(), Instant.now());

            wireMock.stubFor(get(urlEqualTo("/api/orders"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(
                                    new Object[]{order1, order2}))));

            var result = client.findAll();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo("id-1");
            assertThat(result.get(1).status()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("returns empty list when server returns empty array")
        void returnsEmptyList_whenNoOrders() {
            wireMock.stubFor(get(urlEqualTo("/api/orders"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("[]")));

            assertThat(client.findAll()).isEmpty();
        }

        @Test
        @DisplayName("throws OrderServiceInternalError on 503")
        void throwsInternalError_on503() {
            wireMock.stubFor(get(urlEqualTo("/api/orders"))
                    .willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(() -> client.findAll())
                    .isInstanceOf(OrderServiceException.OrderServiceInternalError.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // create()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("sends POST with correct body and returns created order")
        void createsOrder_on201() throws Exception {
            var request  = new CreateOrderRequest(ITEM_ID, 3);
            var response = new OrderResponse(ORDER_ID, ITEM_ID, 3, "PENDING",
                    Instant.now(), Instant.now());

            wireMock.stubFor(post(urlEqualTo("/api/orders"))
                    .withHeader(HttpHeaders.CONTENT_TYPE,
                            containing(MediaType.APPLICATION_JSON_VALUE))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(response))));

            var result = client.create(request);

            assertThat(result.id()).isEqualTo(ORDER_ID);
            assertThat(result.status()).isEqualTo("PENDING");

            // Verificar que WireMock recibió exactamente una llamada POST
            wireMock.verify(1, postRequestedFor(urlEqualTo("/api/orders")));
        }

        @Test
        @DisplayName("throws OrderServiceException on 400 Bad Request")
        void throwsException_on400() {
            wireMock.stubFor(post(urlEqualTo("/api/orders"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"title\":\"Validation error\"}")));

            assertThatThrownBy(() -> client.create(new CreateOrderRequest(ITEM_ID, -1)))
                    .isInstanceOf(OrderServiceException.class)
                    .satisfies(ex ->
                            assertThat(((OrderServiceException) ex).getHttpStatus()).isEqualTo(400)
                    );
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // confirm()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("confirms order and returns updated order with CONFIRMED status")
        void confirmsOrder_on200() throws Exception {
            var confirmed = new OrderResponse(ORDER_ID, ITEM_ID, 3, "CONFIRMED",
                    Instant.now(), Instant.now());

            wireMock.stubFor(post(urlEqualTo("/api/orders/" + ORDER_ID + "/confirm"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(objectMapper.writeValueAsString(confirmed))));

            var result = client.confirm(ORDER_ID);

            assertThat(result.status()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("throws OrderServiceException with 409 on invalid state transition")
        void throwsConflict_on409() {
            wireMock.stubFor(post(urlEqualTo("/api/orders/" + ORDER_ID + "/confirm"))
                    .willReturn(aResponse()
                            .withStatus(409)
                            .withBody("{\"title\":\"Invalid state transition\"}")));

            assertThatThrownBy(() -> client.confirm(ORDER_ID))
                    .isInstanceOf(OrderServiceException.class)
                    .satisfies(ex ->
                            assertThat(((OrderServiceException) ex).getHttpStatus()).isEqualTo(409)
                    );
        }

        @Test
        @DisplayName("throws OrderNotFound when order does not exist")
        void throwsOrderNotFound_on404() {
            wireMock.stubFor(post(urlEqualTo("/api/orders/ghost/confirm"))
                    .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> client.confirm("ghost"))
                    .isInstanceOf(OrderServiceException.OrderNotFound.class);
        }
    }
}