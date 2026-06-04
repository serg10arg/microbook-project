package com.microbook.orderservice.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio que representa un cambio de estado en una Order.
 *
 * ¿Por qué record y no clase mutable (como en el libro)?
 * ═══════════════════════════════════════════════════════
 * El libro usa una clase con getters/setters:
 *
 *   public class OrderEvent {
 *       private String orderId;
 *       private String orderStatus;
 *       public void setOrderId(String id) { ... }
 *   }
 *
 * Los eventos de dominio deben ser INMUTABLES: representan algo que
 * ya ocurrió y no puede cambiar. Un evento "OrderCreated" que modifica
 * su propio orderId no tiene sentido semántico.
 *
 * Con record de Java 21:
 *   - Inmutabilidad garantizada por el compilador
 *   - equals() / hashCode() / toString() generados automáticamente
 *   - Cero boilerplate
 *   - Jackson lo serializa/deserializa a JSON sin configuración extra
 *
 * ¿Qué es correlationId?
 * ══════════════════════
 * Un UUID que viaja con el evento desde su origen hasta su destino.
 * Permite rastrear una operación completa a través de múltiples
 * microservicios en los logs:
 *
 *   item-service  → POST /api/items   correlationId=abc-123
 *   order-service → publica evento    correlationId=abc-123
 *   notif-service → consume evento    correlationId=abc-123
 *
 * Con el mismo correlationId, un log centralizado (ELK, Grafana) puede
 * reconstruir el flujo completo. Imprescindible para debugging en prod.
 *
 * ¿Qué es eventId?
 * ═════════════════
 * UUID único por cada publicación del evento.
 * Permite al consumer detectar duplicados (idempotencia):
 * si el mismo eventId llega dos veces, el consumer lo ignora.
 *
 * Ruta: order-service/src/main/java/com/microbook/orderservice/domain/event/
 */
public record OrderEvent(

        /** Identificador único de ESTE evento (para idempotencia en el consumer). */
        String eventId,

        /**
         * Identificador de correlación: viaja con la operación de negocio
         * a través de todos los servicios para trazar el flujo completo.
         */
        String correlationId,

        /** Tipo del evento: ORDER_CREATED, ORDER_CONFIRMED, ORDER_CANCELLED. */
        EventType eventType,

        /** ID de la orden que originó el evento. */
        String orderId,

        /** ID del item referenciado por la orden. */
        String itemId,

        /** Cantidad de la orden. */
        int quantity,

        /** Estado de la orden en el momento del evento. */
        String status,

        /** Momento exacto en que ocurrió el evento (siempre en UTC). */
        Instant occurredAt

) {

    /** Tipos posibles de eventos de Order. */
    public enum EventType {
        ORDER_CREATED,
        ORDER_CONFIRMED,
        ORDER_CANCELLED
    }

    // ── Factory methods ───────────────────────────────────────────────────────
    // Nombrados con el tipo de evento: más legible que new OrderEvent(...)
    // y garantizan que eventType y status sean siempre consistentes.

    /**
     * Crea un evento ORDER_CREATED.
     *
     * @param correlationId ID de correlación de la operación de negocio
     * @param orderId       ID de la orden recién creada
     * @param itemId        ID del item referenciado
     * @param quantity      Cantidad solicitada
     */
    public static OrderEvent orderCreated(String correlationId,
                                          String orderId,
                                          String itemId,
                                          int quantity) {
        return new OrderEvent(
                UUID.randomUUID().toString(),   // eventId único por publicación
                requireNonBlank(correlationId, "correlationId"),
                EventType.ORDER_CREATED,
                requireNonBlank(orderId, "orderId"),
                requireNonBlank(itemId, "itemId"),
                quantity,
                "PENDING",
                Instant.now()
        );
    }

    /**
     * Crea un evento ORDER_CONFIRMED.
     */
    public static OrderEvent orderConfirmed(String correlationId,
                                            String orderId,
                                            String itemId,
                                            int quantity) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                requireNonBlank(correlationId, "correlationId"),
                EventType.ORDER_CONFIRMED,
                requireNonBlank(orderId, "orderId"),
                requireNonBlank(itemId, "itemId"),
                quantity,
                "CONFIRMED",
                Instant.now()
        );
    }

    /**
     * Crea un evento ORDER_CANCELLED.
     */
    public static OrderEvent orderCancelled(String correlationId,
                                            String orderId,
                                            String itemId,
                                            int quantity) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                requireNonBlank(correlationId, "correlationId"),
                EventType.ORDER_CANCELLED,
                requireNonBlank(orderId, "orderId"),
                requireNonBlank(itemId, "itemId"),
                quantity,
                "CANCELLED",
                Instant.now()
        );
    }

    // ── Validación compacta ───────────────────────────────────────────────────

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * Constructor compacto del record: valida invariantes al construir.
     * Se ejecuta tanto en los factory methods como en la deserialización Jackson.
     */
    public OrderEvent {
        Objects.requireNonNull(eventId,       "eventId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(eventType,     "eventType must not be null");
        Objects.requireNonNull(orderId,       "orderId must not be null");
        Objects.requireNonNull(occurredAt,    "occurredAt must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
    }
}