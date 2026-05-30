package com.microbook.itemservice.infrastructure.persistence.mapper;

import com.microbook.itemservice.domain.model.Item;
import com.microbook.itemservice.infrastructure.persistence.entity.ItemJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre la entidad de dominio (Item) y la entidad JPA (ItemJpaEntity).
 *
 * MapStruct genera la implementación en tiempo de compilación:
 * cero reflexión en runtime, máximo rendimiento, errores detectados en build.
 *
 * componentModel = "spring" → MapStruct registra el mapper como @Component
 * para que Spring lo inyecte donde se necesite.
 */
@Mapper(componentModel = "spring")
public interface ItemPersistenceMapper {

    /**
     * Dominio → JPA: se usa antes de guardar en BD.
     */
    ItemJpaEntity toJpaEntity(Item item);

    /**
     * JPA → Dominio: se usa al leer de BD, usando el factory method reconstitute.
     *
     * No llamamos al constructor de Item directamente porque es privado.
     * Usamos Item.reconstitute() para respetar las reglas del dominio.
     */
    @Mapping(target = "id",          source = "id")
    @Mapping(target = "name",        source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "quantity",    source = "quantity")
    @Mapping(target = "createdAt",   source = "createdAt")
    @Mapping(target = "updatedAt",   source = "updatedAt")
    default Item toDomain(ItemJpaEntity entity) {
        return Item.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getQuantity(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}