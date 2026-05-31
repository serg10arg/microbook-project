package com.microbook.orderservice.infrastructure.web.dto;

import com.microbook.orderservice.domain.model.Order;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * DTOs de la capa web para Order.
 *
 * Misma convención que ItemDto:
 *   - Records de Java 21: inmutables y sin boilerplate
 *   - Validaciones aquí, no en la entidad de dominio
 *   - CreateRequest y Response separados para evolucionar independientemente
 *
 * Response incluye statusLabel (String) además de status (enum)
 * para que el cliente reciba el nombre legible directamente.
 */
public final class OrderDto {

    private OrderDto() {}

    /**
     * Payload de creación de una orden (POST /api/orders).
     */
    public record CreateRequest(

            @NotBlank(message = "itemId must not be blank")
            String itemId,

            @Min(value = 1, message = "quantity must be >= 1")
            int quantity

    ) {}

    /**
     * Respuesta completa de una orden (GET, POST).
     */
    public record Response(
            String id,
            String itemId,
            int quantity,
            Order.Status status,
            Instant createdAt,
            Instant updatedAt
    ) {}
}