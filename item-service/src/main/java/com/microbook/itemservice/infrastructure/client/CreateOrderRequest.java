package com.microbook.itemservice.infrastructure.client;

/**
 * Payload que se envía a order-service al crear una orden.
 *
 * Record inmutable: serializado a JSON por Jackson automáticamente.
 * Corresponde al OrderDto.CreateRequest de order-service.
 */
public record CreateOrderRequest(
        String itemId,
        int quantity
) {}