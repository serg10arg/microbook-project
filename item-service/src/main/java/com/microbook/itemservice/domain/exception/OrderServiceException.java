package com.microbook.itemservice.domain.exception;

/**
 * Jerarquía de excepciones para errores al comunicarse con order-service.
 *
 * ¿Por qué una jerarquía y no solo RuntimeException?
 * ══════════════════════════════════════════════════
 * Cada subclase representa una causa distinta:
 *
 *   OrderServiceException          ← raíz: cualquier error con order-service
 *     ├── OrderNotFound            ← 404: la orden no existe
 *     ├── OrderServiceUnavailable  ← 503 / timeout: servicio caído
 *     └── OrderServiceInternalError ← 5xx: error interno del servidor remoto
 *
 * El servicio que llama al cliente puede capturar a distinto nivel:
 *
 *   catch (OrderNotFound e)              → informar al usuario "orden no encontrada"
 *   catch (OrderServiceUnavailable e)    → activar fallback o circuit breaker
 *   catch (OrderServiceException e)      → error genérico, loguear y propagar
 *
 * Si solo usáramos RuntimeException, el caller no podría distinguir
 * un 404 (error del usuario) de un 503 (error de infraestructura).
 *
 * Ruta de este archivo:
 *   item-service/src/main/java/com/microbook/itemservice/domain/exception/
 */
public class OrderServiceException extends RuntimeException {

    private final int httpStatus;

    public OrderServiceException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public OrderServiceException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() { return httpStatus; }

    // ── Subclases específicas ─────────────────────────────────────────────────

    /** 404 — la orden solicitada no existe en order-service. */
    public static class OrderNotFound extends OrderServiceException {
        private final String orderId;

        public OrderNotFound(String orderId) {
            super("Order not found in order-service: " + orderId, 404);
            this.orderId = orderId;
        }

        public String getOrderId() { return orderId; }
    }

    /** 503 / timeout — order-service no está disponible o tardó demasiado. */
    public static class OrderServiceUnavailable extends OrderServiceException {
        public OrderServiceUnavailable(String message, Throwable cause) {
            super(message, 503, cause);
        }
    }

    /** 5xx — order-service devolvió un error interno del servidor. */
    public static class OrderServiceInternalError extends OrderServiceException {
        public OrderServiceInternalError(int status, String detail) {
            super("order-service returned server error %d: %s".formatted(status, detail), status);
        }
    }
}