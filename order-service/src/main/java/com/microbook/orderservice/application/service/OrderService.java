package com.microbook.orderservice.application.service;

import com.microbook.orderservice.domain.exception.OrderNotFoundException;
import com.microbook.orderservice.domain.model.Order;
import com.microbook.orderservice.domain.port.in.OrderUseCase;
import com.microbook.orderservice.domain.port.out.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicación: orquesta el flujo entre el dominio y la infraestructura.
 *
 * Patrón idéntico al ItemService:
 *   - @Transactional(readOnly = true) por defecto
 *   - Los métodos que escriben sobreescriben con @Transactional
 *   - La lógica de negocio (confirm, cancel) está en Order, no aquí
 *   - Inyección por constructor
 *
 * TODO Fase 2: confirm() y cancel() deberán notificar a otros servicios.
 *              Por ahora solo mutan el estado local.
 */
@Service
@Transactional(readOnly = true)
public class OrderService implements OrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    @Override
    public List<Order> findAll() {
        log.debug("Fetching all orders");
        return orderRepository.findAll();
    }

    @Override
    public Order findById(String id) {
        log.debug("Fetching order id={}", id);
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    @Transactional
    public Order create(CreateOrderCommand command) {
        log.info("Creating order itemId='{}' quantity={}", command.itemId(), command.quantity());

        /*
         * TODO Fase 2: antes de crear la orden, validar que el item existe
         * llamando a item-service via WebClient:
         *
         *   itemServiceClient.getItem(command.itemId())   // lanza excepción si no existe
         *
         * Por ahora asumimos que el itemId es válido.
         */

        Order order = Order.create(command.itemId(), command.quantity());
        Order saved = orderRepository.save(order);

        log.info("Order created id='{}'", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Order confirm(String id) {
        log.info("Confirming order id='{}'", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        order.confirm();   // lógica de negocio en el dominio

        Order saved = orderRepository.save(order);
        log.info("Order confirmed id='{}'", id);
        return saved;
    }

    @Override
    @Transactional
    public Order cancel(String id) {
        log.info("Cancelling order id='{}'", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        order.cancel();    // lógica de negocio en el dominio

        Order saved = orderRepository.save(order);
        log.info("Order cancelled id='{}'", id);
        return saved;
    }
}