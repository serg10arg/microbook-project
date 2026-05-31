package com.microbook.orderservice.domain.port.out;

import com.microbook.orderservice.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    List<Order> findAll();

    Optional<Order> findById(String id);

    Order save(Order order);
}
