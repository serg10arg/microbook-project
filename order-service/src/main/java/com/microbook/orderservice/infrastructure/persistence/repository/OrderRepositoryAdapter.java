package com.microbook.orderservice.infrastructure.persistence.repository;

import com.microbook.orderservice.domain.model.Order;
import com.microbook.orderservice.domain.port.out.OrderRepository;
import com.microbook.orderservice.infrastructure.persistence.mapper.OrderPersistenceMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository springDataRepo;
    private final OrderPersistenceMapper mapper;

    public OrderRepositoryAdapter(SpringDataOrderRepository springDataRepo,
                                  OrderPersistenceMapper mapper) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
    }

    @Override
    public List<Order> findAll() {
        return springDataRepo.findAll()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Order> findById(String id) {
        return springDataRepo.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Order save(Order order) {
        var entity = mapper.toJpaEntity(order);
        var saved  = springDataRepo.save(entity);
        return mapper.toDomain(saved);
    }
}
