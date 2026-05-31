package com.microbook.orderservice.infrastructure.web.controller;

import com.microbook.orderservice.domain.port.in.OrderUseCase;
import com.microbook.orderservice.infrastructure.web.dto.OrderDto;
import com.microbook.orderservice.infrastructure.web.mapper.OrderWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Controller REST para Order.
 *
 * Endpoints:
 *   GET  /api/orders          → lista todas las órdenes
 *   GET  /api/orders/{id}     → obtiene una orden por id
 *   POST /api/orders          → crea una orden nueva (status: PENDING)
 *   POST /api/orders/{id}/confirm  → confirma una orden PENDING
 *   POST /api/orders/{id}/cancel   → cancela una orden no CONFIRMED
 *
 * Los endpoints de confirm y cancel usan POST porque representan
 * una transición de estado (acción), no una mutación de recurso (PUT/PATCH).
 * Es una convención REST para operaciones de workflow.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderUseCase orderUseCase;
    private final OrderWebMapper mapper;

    public OrderController(OrderUseCase orderUseCase, OrderWebMapper mapper) {
        this.orderUseCase = orderUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto.Response>> findAll() {
        var orders = orderUseCase.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto.Response> findById(@PathVariable String id) {
        var order = orderUseCase.findById(id);
        return ResponseEntity.ok(mapper.toResponse(order));
    }

    @PostMapping
    public ResponseEntity<OrderDto.Response> create(
            @Valid @RequestBody OrderDto.CreateRequest request) {

        var command = mapper.toCreateCommand(request);
        var created = orderUseCase.create(command);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderDto.Response> confirm(@PathVariable String id) {
        var confirmed = orderUseCase.confirm(id);
        return ResponseEntity.ok(mapper.toResponse(confirmed));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDto.Response> cancel(@PathVariable String id) {
        var cancelled = orderUseCase.cancel(id);
        return ResponseEntity.ok(mapper.toResponse(cancelled));
    }
}