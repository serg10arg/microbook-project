package com.microbook.orderservice.application.service;

import com.microbook.orderservice.domain.exception.OrderNotFoundException;
import com.microbook.orderservice.domain.model.Order;
import com.microbook.orderservice.domain.port.in.OrderUseCase;
import com.microbook.orderservice.domain.port.out.OrderRepository;
import com.microbook.orderservice.infrastructure.messaging.producer.OrderProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * OrderService actualizado para publicar eventos Kafka tras cada
 * transición de estado.
 *
 * Orden de operaciones en los métodos que escriben:
 * ══════════════════════════════════════════════════
 *   1. Persistir el cambio en la BD (@Transactional)
 *   2. Publicar el evento Kafka
 *
 * ¿Por qué en este orden?
 * La BD es la fuente de verdad. Si Kafka falla, el dato está guardado
 * y podemos reprocesar el evento (Outbox Pattern en Fase 3).
 * Si persistiéramos DESPUÉS de publicar el evento y la BD fallara,
 * el consumer procesaría un evento que no existe en la BD.
 *
 * El correlationId se recibe como parámetro desde el controller,
 * que lo extrae del header HTTP X-Correlation-ID del request entrante.
 * Así la cadena de trazabilidad es:
 *   Cliente → controller → service → Kafka → notification-service
 *   con el mismo correlationId en todos los logs.
 *
 * Ruta: order-service/src/main/java/…/application/service/
 */
@Service
@Transactional(readOnly = true)
public class OrderService implements OrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderProducerService producerService;

    public OrderService(OrderRepository orderRepository,
                        OrderProducerService producerService) {
        this.orderRepository  = Objects.requireNonNull(orderRepository);
        this.producerService  = Objects.requireNonNull(producerService);
    }

    @Override
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Override
    public Order findById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    @Transactional
    public Order create(CreateOrderCommand command) {
        return create(command, null);   // sin correlationId externo
    }

    /**
     * Variante de create() que acepta un correlationId externo.
     * El controller llama a esta versión pasando el header X-Correlation-ID.
     */
    @Transactional
    public Order create(CreateOrderCommand command, String correlationId) {
        log.info("Creating order itemId='{}' quantity={}", command.itemId(), command.quantity());

        // 1. Persistir
        Order order = Order.create(command.itemId(), command.quantity());
        Order saved = orderRepository.save(order);

        // 2. Publicar evento DESPUÉS de guardar en BD
        producerService.publishOrderCreated(saved, correlationId);

        log.info("Order created and event published id='{}'", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Order confirm(String id) {
        return confirm(id, null);
    }

    @Transactional
    public Order confirm(String id, String correlationId) {
        log.info("Confirming order id='{}'", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        order.confirm();   // lógica de negocio en el dominio

        Order saved = orderRepository.save(order);
        producerService.publishOrderConfirmed(saved, correlationId);   // evento tras persistir

        return saved;
    }

    @Override
    @Transactional
    public Order cancel(String id) {
        return cancel(id, null);
    }

    @Transactional
    public Order cancel(String id, String correlationId) {
        log.info("Cancelling order id='{}'", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        order.cancel();

        Order saved = orderRepository.save(order);
        producerService.publishOrderCancelled(saved, correlationId);   // evento tras persistir

        return saved;
    }
}