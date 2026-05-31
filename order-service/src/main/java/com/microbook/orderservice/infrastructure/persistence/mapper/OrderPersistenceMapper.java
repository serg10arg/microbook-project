package com.microbook.orderservice.infrastructure.persistence.mapper;

import com.microbook.orderservice.domain.model.Order;
import com.microbook.orderservice.infrastructure.persistence.entity.OrderJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderPersistenceMapper {

    OrderJpaEntity toJpaEntity(Order order);

    default Order toDomain(OrderJpaEntity entity) {
        return Order.reconstitute(
                entity.getId(),
                entity.getItemId(),
                entity.getQuantity(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
