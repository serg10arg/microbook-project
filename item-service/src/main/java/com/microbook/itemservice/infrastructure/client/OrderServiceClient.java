package com.microbook.itemservice.infrastructure.client;

import com.microbook.itemservice.domain.exception.OrderServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.List;

/**
 * Cliente HTTP tipado para order-service.
 *
 * ¿Por qué un cliente tipado en lugar de WebClient directo?
 * ══════════════════════════════════════════════════════════
 * El libro inyecta RestTemplate directamente en el controller:
 *
 *   restTemplate.getForObject("http://order-service/orders/" + id, String.class)
 *
 * Eso tiene tres problemas:
 *   1. La URL, el tipo de respuesta y el manejo de error están dispersos
 *      por todo el código que necesita comunicarse con order-service.
 *   2. No hay un lugar único donde configurar timeouts, headers, o reintentos.
 *   3. No se puede testear el comportamiento de error sin arrancar un servidor real.
 *
 * Con un cliente tipado:
 *   - Toda comunicación con order-service pasa por ESTA clase.
 *   - Los errores HTTP se traducen a excepciones de dominio significativas.
 *   - El cliente se puede mockear en tests con @MockBean.
 *   - Si order-service cambia su URL base, se cambia en un solo lugar.
 *
 * Nota sobre WebClient y el bloqueo:
 * ════════════════════════════════════
 * WebClient es reactivo por naturaleza (devuelve Mono/Flux).
 * Como item-service usa Spring MVC (no WebFlux), llamamos .block()
 * para obtener el resultado de forma síncrona.
 *
 * Esto es correcto aquí porque:
 *   - Necesitamos la respuesta inmediatamente para continuar el flujo
 *   - Spring MVC gestiona sus propios threads del Servlet container
 *   - No tenemos Event Loop que bloquear (eso es problema de Netty/WebFlux)
 *
 * Si en el futuro migramos a WebFlux, solo cambia este cliente —
 * el resto del código no se toca.
 */
@Component
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final WebClient webClient;

    /**
     * WebClient se inyecta configurado desde WebClientConfig.
     * El cliente NO conoce la URL base ni el timeout — esa
     * responsabilidad es de la configuración.
     */
    public OrderServiceClient(WebClient orderServiceWebClient) {
        this.webClient = orderServiceWebClient;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/orders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Obtiene todas las órdenes de order-service.
     *
     * @return lista de órdenes (puede estar vacía, nunca null)
     * @throws OrderServiceException.OrderServiceUnavailable si order-service no responde
     */
    public List<OrderResponse> findAll() {
        log.debug("Calling order-service GET /api/orders");

        try {
            List<OrderResponse> orders = webClient
                    .get()
                    .uri("/api/orders")
                    .retrieve()
                    // onStatus: intercepta códigos HTTP específicos ANTES
                    // de deserializar la respuesta. Aquí mapeamos 5xx → excepción.
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException.OrderServiceInternalError(
                                            response.statusCode().value(), body))
                    )
                    .bodyToFlux(OrderResponse.class)
                    .collectList()
                    .block();                    // bloqueo síncrono — ver javadoc de clase

            log.debug("order-service returned {} orders", orders != null ? orders.size() : 0);
            return orders != null ? orders : List.of();

        } catch (WebClientRequestException e) {
            // WebClientRequestException = el servidor no respondió (timeout, connection refused)
            log.error("order-service unreachable on GET /api/orders: {}", e.getMessage());
            throw new OrderServiceException.OrderServiceUnavailable(
                    "order-service is unreachable", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/orders/{id}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Obtiene una orden por su ID.
     *
     * @param  orderId identificador de la orden
     * @return la orden encontrada
     * @throws OrderServiceException.OrderNotFound        si order-service devuelve 404
     * @throws OrderServiceException.OrderServiceUnavailable si order-service no responde
     * @throws OrderServiceException.OrderServiceInternalError si order-service devuelve 5xx
     */
    public OrderResponse findById(String orderId) {
        log.debug("Calling order-service GET /api/orders/{}", orderId);

        try {
            OrderResponse order = webClient
                    .get()
                    .uri("/api/orders/{id}", orderId)
                    .retrieve()
                    // 404 → OrderNotFound (error del usuario, no de infraestructura)
                    .onStatus(
                            status -> status == HttpStatus.NOT_FOUND,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException.OrderNotFound(orderId))
                    )
                    // 5xx → OrderServiceInternalError (fallo del servidor remoto)
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException.OrderServiceInternalError(
                                            response.statusCode().value(), body))
                    )
                    .bodyToMono(OrderResponse.class)
                    .block();

            log.debug("order-service returned order id={}", orderId);
            return order;

        } catch (OrderServiceException e) {
            // Re-lanzar excepciones de dominio tal cual — ya están tipadas
            throw e;

        } catch (WebClientRequestException e) {
            log.error("order-service unreachable on GET /api/orders/{}: {}", orderId, e.getMessage());
            throw new OrderServiceException.OrderServiceUnavailable(
                    "order-service is unreachable while fetching order: " + orderId, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/orders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crea una nueva orden en order-service.
     *
     * @param  request datos de la orden a crear
     * @return la orden creada con su ID asignado
     * @throws OrderServiceException.OrderServiceUnavailable si order-service no responde
     * @throws OrderServiceException.OrderServiceInternalError si order-service devuelve 5xx
     */
    public OrderResponse create(CreateOrderRequest request) {
        log.info("Calling order-service POST /api/orders itemId='{}' quantity={}",
                request.itemId(), request.quantity());

        try {
            OrderResponse created = webClient
                    .post()
                    .uri("/api/orders")
                    // bodyValue: serializa el record a JSON automáticamente con Jackson
                    .bodyValue(request)
                    .retrieve()
                    // 400 Bad Request: el payload que enviamos no es válido
                    .onStatus(
                            status -> status == HttpStatus.BAD_REQUEST,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException(
                                            "Bad request to order-service: " + body, 400))
                    )
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException.OrderServiceInternalError(
                                            response.statusCode().value(), body))
                    )
                    .bodyToMono(OrderResponse.class)
                    .block();

            log.info("order-service created order id='{}'",
                    created != null ? created.id() : "null");
            return created;

        } catch (OrderServiceException e) {
            throw e;

        } catch (WebClientRequestException e) {
            log.error("order-service unreachable on POST /api/orders: {}", e.getMessage());
            throw new OrderServiceException.OrderServiceUnavailable(
                    "order-service is unreachable while creating order", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/orders/{id}/confirm
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Confirma una orden existente en order-service.
     *
     * @param  orderId identificador de la orden a confirmar
     * @return la orden con status actualizado a CONFIRMED
     * @throws OrderServiceException.OrderNotFound si la orden no existe
     * @throws OrderServiceException              si la transición de estado no es válida (409)
     */
    public OrderResponse confirm(String orderId) {
        log.info("Calling order-service POST /api/orders/{}/confirm", orderId);

        try {
            return webClient
                    .post()
                    .uri("/api/orders/{id}/confirm", orderId)
                    .retrieve()
                    .onStatus(
                            status -> status == HttpStatus.NOT_FOUND,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException.OrderNotFound(orderId))
                    )
                    // 409 Conflict: transición de estado inválida (ej: confirmar una cancelada)
                    .onStatus(
                            status -> status == HttpStatus.CONFLICT,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException(
                                            "Invalid state transition in order-service: " + body, 409))
                    )
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            response -> response.bodyToMono(String.class)
                                    .map(body -> new OrderServiceException.OrderServiceInternalError(
                                            response.statusCode().value(), body))
                    )
                    .bodyToMono(OrderResponse.class)
                    .block();

        } catch (OrderServiceException e) {
            throw e;

        } catch (WebClientRequestException e) {
            log.error("order-service unreachable on POST /api/orders/{}/confirm: {}", orderId, e.getMessage());
            throw new OrderServiceException.OrderServiceUnavailable(
                    "order-service is unreachable while confirming order: " + orderId, e);
        }
    }
}