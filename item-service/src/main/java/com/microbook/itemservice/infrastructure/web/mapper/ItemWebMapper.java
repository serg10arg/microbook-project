package com.microbook.itemservice.infrastructure.web.mapper;

import com.microbook.itemservice.domain.model.Item;
import com.microbook.itemservice.domain.port.in.ItemUseCase.CreateItemCommand;
import com.microbook.itemservice.domain.port.in.ItemUseCase.UpdateItemCommand;
import com.microbook.itemservice.infrastructure.web.dto.ItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre los DTOs de la capa web y los objetos del dominio.
 *
 * ¿Qué problema resuelve?
 * ════════════════════════
 * El controller recibe un ItemDto.CreateRequest (JSON del cliente).
 * El use case acepta un CreateItemCommand (objeto del dominio).
 * El use case devuelve un Item (entidad de dominio).
 * El controller debe responder con un ItemDto.Response (JSON al cliente).
 *
 * Sin este mapper, el controller tendría que hacer la conversión manual:
 *
 *   // Sin mapper (código del libro, todo mezclado)
 *   Item item = new Item();
 *   item.setName(request.name());
 *   item.setDescription(request.description());
 *   ...
 *   ItemDto.Response response = new ItemDto.Response(
 *       item.getId(), item.getName(), ...
 *   );
 *
 * Con MapStruct, todo eso desaparece y el controller queda limpio.
 *
 * ¿Cómo funciona MapStruct?
 * ══════════════════════════
 * MapStruct lee esta interfaz en TIEMPO DE COMPILACIÓN (no en runtime)
 * y genera una clase ItemWebMapperImpl.java en target/generated-sources/.
 * Puedes abrir ese archivo para ver exactamente qué código genera.
 *
 * La clase generada es un @Component de Spring, así que se inyecta
 * normalmente en ItemController con el constructor.
 *
 * Ventajas sobre conversión manual:
 *   ✓ Cero reflexión → máximo rendimiento
 *   ✓ Errores detectados en build, no en runtime
 *   ✓ El controller queda con 0 líneas de mapeo
 *   ✓ Si añades un campo al DTO, MapStruct avisa si falta el mapeo
 *
 * componentModel = "spring":
 * ════════════════════════════
 * Le dice a MapStruct que registre el mapper como @Component de Spring
 * para que pueda ser inyectado con @Autowired o por constructor.
 * Sin esto, MapStruct genera la clase pero Spring no la conoce.
 */
@Mapper(componentModel = "spring")
public interface ItemWebMapper {

    /**
     * CreateRequest (entrada HTTP) → CreateItemCommand (entrada del use case).
     *
     * MapStruct mapea los campos por nombre automáticamente:
     *   request.name()        → command.name()
     *   request.description() → command.description()
     *   request.quantity()    → command.quantity()
     *
     * Como CreateItemCommand es un record de Java 21, MapStruct usa
     * su constructor canónico para crearlo.
     */
    CreateItemCommand toCreateCommand(ItemDto.CreateRequest request);

    /**
     * UpdateRequest (entrada HTTP) → UpdateItemCommand (entrada del use case).
     * Misma lógica que toCreateCommand.
     */
    UpdateItemCommand toUpdateCommand(ItemDto.UpdateRequest request);

    /**
     * Item (dominio) → Response (salida HTTP).
     *
     * Mapeo explícito de todos los campos para que sea legible
     * y no dependa de coincidencias de nombres implícitas:
     *
     *   item.getId()          → response.id()
     *   item.getName()        → response.name()
     *   item.getDescription() → response.description()
     *   item.getQuantity()    → response.quantity()
     *   item.getCreatedAt()   → response.createdAt()
     *   item.getUpdatedAt()   → response.updatedAt()
     *
     * Nota: Item tiene getters explícitos (no usa Lombok) porque es una
     * entidad de dominio pura. MapStruct los detecta automáticamente.
     */
    @Mapping(target = "id",          source = "id")
    @Mapping(target = "name",        source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "quantity",    source = "quantity")
    @Mapping(target = "createdAt",   source = "createdAt")
    @Mapping(target = "updatedAt",   source = "updatedAt")
    ItemDto.Response toResponse(Item item);
}