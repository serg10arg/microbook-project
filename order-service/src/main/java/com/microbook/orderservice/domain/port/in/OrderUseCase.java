package com.microbook.orderservice.domain.port.in;

import com.microbook.orderservice.domain.model.Order;

import java.util.List;

/**
 * Puerto de entrada: define qué puede hacer el sistema con Orders.
 *
 * El controller llama esta interfaz.
 * La implementación vive en application/service/OrderService.
 *
 * Los Commands son records de Java 21: inmutables y sin boilerplate.
 * Viajan entre la capa web y la capa de aplicación sin exponer
 * los DTOs HTTP al dominio.
 */
public interface OrderUseCase {

    List<Order> findAll();

    Order findById(String id);

    Order create(CreateOrderCommand command);

    Order confirm(String id);

    Order cancel(String id);

    // ── Commands ──────────────────────────────────────────────────────────────

    /**
     * Datos necesarios para crear una orden.
     *
     * itemId: referencia al Item en item-service (se validará en Fase 2
     *         cuando hagamos la llamada HTTP a item-service via WebClient).
     */
    record CreateOrderCommand(String itemId, int quantity) {}
}