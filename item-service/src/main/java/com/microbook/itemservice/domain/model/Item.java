package com.microbook.itemservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio pura.
 *
 * Regla de Clean Architecture: esta clase NO importa nada de Spring,
 * JPA ni ningún framework. Es el núcleo del sistema y debe poder
 * existir y testearse sin ninguna dependencia externa.
 */
public class Item {

    private final String id;
    private String name;
    private String description;
    private int quantity;
    private final Instant createdAt;
    private Instant updatedAt;

    // Constructor privado — obliga a usar el factory method
    private Item(String id, String name, String description, int quantity,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method para crear un nuevo Item.
     * Genera su propio ID y timestamps: el dominio controla su identidad.
     */
    public static Item create(String name, String description, int quantity) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");

        Instant now = Instant.now();
        return new Item(UUID.randomUUID().toString(), name, description, quantity, now, now);
    }

    /**
     * Factory method para reconstruir un Item existente desde persistencia.
     */
    public static Item reconstitute(String id, String name, String description,
                                    int quantity, Instant createdAt, Instant updatedAt) {
        return new Item(id, name, description, quantity, createdAt, updatedAt);
    }

    /**
     * Lógica de negocio: actualizar los datos del item.
     * La entidad es responsable de su propio estado, no el service.
     */
    public void update(String name, String description, int quantity) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");

        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    // --- Getters (sin setters: mutación sólo vía métodos de dominio) ---

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public int getQuantity()       { return quantity; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item item)) return false;
        return Objects.equals(id, item.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Item{id='%s', name='%s', quantity=%d}".formatted(id, name, quantity);
    }
}