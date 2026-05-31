package com.microbook.orderservice.infrastructure.web.mapper;

import com.microbook.orderservice.domain.model.Order;
import com.microbook.orderservice.domain.port.in.OrderUseCase.CreateOrderCommand;
import com.microbook.orderservice.infrastructure.web.dto.OrderDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre los DTOs web y los objetos del dominio.
 *
 * Convierte:
 *   CreateRequest  → CreateOrderCommand  (HTTP → use case)
 *   Order (domain) → Response            (use case → HTTP)
 */
@Mapper(componentModel = "spring")
public interface OrderWebMapper {

    CreateOrderCommand toCreateCommand(OrderDto.CreateRequest request);

    @Mapping(target = "id",         source = "id")
    @Mapping(target = "itemId",     source = "itemId")
    @Mapping(target = "quantity",   source = "quantity")
    @Mapping(target = "status",     source = "status")
    @Mapping(target = "createdAt",  source = "createdAt")
    @Mapping(target = "updatedAt",  source = "updatedAt")
    OrderDto.Response toResponse(Order order);
}