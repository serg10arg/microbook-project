package com.microbook.itemservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entidad JPA: representación de Item en la base de datos.
 *
 * IMPORTANTE: esta clase vive en infrastructure, NO en domain.
 * La entidad de dominio (Item) y la entidad JPA (ItemJpaEntity) son
 * objetos distintos con propósitos distintos.
 *
 * La entidad JPA puede cambiar libremente (índices, columnas, lazy loading)
 * sin afectar al dominio. El mapper convierte entre ambas.
 */
@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class ItemJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}