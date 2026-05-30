package com.microbook.itemservice.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTOs de la capa web: lo que entra y sale por HTTP.
 *
 * Son records de Java 21: inmutables, concisos y sin boilerplate.
 * Viven en infrastructure/web porque son un detalle de transporte HTTP,
 * no pertenecen al dominio.
 *
 * Separar Request de Response permite evolucionar ambos
 * de forma independiente sin romper el dominio.
 */
public final class ItemDto {

    // Previene instanciación de la clase contenedora
    private ItemDto() {}

    /**
     * Payload de creación de un item (POST /api/items).
     * Las anotaciones de validación van aquí, no en la entidad de dominio.
     */
    public record CreateRequest(

            @NotBlank(message = "name must not be blank")
            @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
            String name,

            @NotBlank(message = "description must not be blank")
            @Size(max = 500, message = "description must not exceed 500 characters")
            String description,

            @Min(value = 0, message = "quantity must be >= 0")
            int quantity

    ) {}

    /**
     * Payload de actualización de un item (PUT /api/items/{id}).
     */
    public record UpdateRequest(

            @NotBlank(message = "name must not be blank")
            @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
            String name,

            @NotBlank(message = "description must not be blank")
            @Size(max = 500, message = "description must not exceed 500 characters")
            String description,

            @Min(value = 0, message = "quantity must be >= 0")
            int quantity

    ) {}

    /**
     * Respuesta para un item (GET, POST, PUT).
     * Siempre proyecta todos los campos relevantes; no expone datos internos.
     */
    public record Response(
            String id,
            String name,
            String description,
            int quantity,
            Instant createdAt,
            Instant updatedAt
    ) {}
}