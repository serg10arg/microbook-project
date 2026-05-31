package com.microbook.orderservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio Order.
 *
 * Mismas reglas que Item:
 *   - Sin anotaciones de Spring ni JPA
 *   - Constructor privado, factory methods públicos
 *   - La lógica de negocio vive aquí, no en el servicio
 *
 * Un Order representa la intención de adquirir un Item en una cantidad dada.
 * Su ciclo de vida: PENDING → CONFIRMED → CANCELLED
 */
public class Order {

    /**
     * Estados posibles de una orden.
     * Se modelan como enum del dominio, no como String en la BD.
     */
    public enum Status {
        PENDING,      // recién creada, esperando confirmación
        CONFIRMED,    // procesada y confirmada
        CANCELLED     // cancelada antes de confirmarse
    }

    private final String id;
    private final String itemId;      // referencia al item en item-service
    private int quantity;
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Order(String id, String itemId, int quantity,
                  Status status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.itemId = itemId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method: crea una Order nueva en estado PENDING.
     * El dominio asigna su propio ID y timestamps.
     */
    public static Order create(String itemId, int quantity) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");

        Instant now = Instant.now();
        return new Order(
                UUID.randomUUID().toString(),
                itemId, quantity,
                Status.PENDING,
                now, now
        );
    }

    /**
     * Factory method: reconstruye una Order existente desde persistencia.
     */
    public static Order reconstitute(String id, String itemId, int quantity,
                                     Status status, Instant createdAt, Instant updatedAt) {
        return new Order(id, itemId, quantity, status, createdAt, updatedAt);
    }

    /**
     * Lógica de negocio: confirmar la orden.
     * Solo se puede confirmar si está en PENDING.
     */
    public void confirm() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException(
                    "Order can only be confirmed when PENDING, current status: " + this.status);
        }
        this.status = Status.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    /**
     * Lógica de negocio: cancelar la orden.
     * No se puede cancelar si ya está CONFIRMED.
     */
    public void cancel() {
        if (this.status == Status.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel a CONFIRMED order");
        }
        this.status = Status.CANCELLED;
        this.updatedAt = Instant.now();
    }

    // ── Getters (sin setters: mutación solo vía métodos de dominio) ──────────

    public String getId()          { return id; }
    public String getItemId()      { return itemId; }
    public int getQuantity()       { return quantity; }
    public Status getStatus()      { return status; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Order{id='%s', itemId='%s', quantity=%d, status=%s}"
                .formatted(id, itemId, quantity, status);
    }
}