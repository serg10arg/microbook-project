package com.microbook.itemservice.infrastructure.client;


import java.time.Instant;

/**
 * DTO de respuesta de order-service.
 *
 * Es un record de Java 21: inmutable, sin boilerplate.
 *
 * Vive en infrastructure/client porque es un detalle de transporte HTTP,
 * no un objeto del dominio de item-service. Si order-service cambia
 * su contrato de respuesta, solo cambia este record y el cliente.
 *
 * El campo status es String (no enum) de forma deliberada:
 * no queremos que item-service conozca los estados de dominio de order-service.
 * Desacoplamiento de modelos entre microservicios.
 */
public record OrderResponse(
        String id,
        String itemId,
        int quantity,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
