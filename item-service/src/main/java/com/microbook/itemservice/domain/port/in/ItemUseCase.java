package com.microbook.itemservice.domain.port.in;

import com.microbook.itemservice.domain.model.Item;

import java.util.List;

/**
 * Puerto de entrada (driving port): define QUÉ puede hacer el sistema con Items.
 *
 * Es una interfaz del dominio. El controller la llama.
 * La implementación vive en application/service.
 *
 * Regla: los parámetros son tipos primitivos o value objects del dominio,
 * nunca DTOs de la capa web ni entidades JPA.
 */
public interface ItemUseCase {

    /** Obtiene todos los items. */
    List<Item> findAll();

    /** Obtiene un item por su ID. Lanza ItemNotFoundException si no existe. */
    Item findById(String id);

    /**
     * Crea un nuevo item.
     *
     * @param command datos necesarios para la creación
     * @return el item creado con su ID asignado
     */
    Item create(CreateItemCommand command);

    /**
     * Actualiza un item existente.
     *
     * @param id      identificador del item a actualizar
     * @param command nuevos datos
     * @return el item actualizado
     */
    Item update(String id, UpdateItemCommand command);

    /** Elimina un item. Lanza ItemNotFoundException si no existe. */
    void delete(String id);

    // -------------------------------------------------------------------------
    // Commands (value objects para encapsular los datos de entrada)
    // Los records de Java 21 son perfectos: inmutables y concisos.
    // -------------------------------------------------------------------------

    record CreateItemCommand(String name, String description, int quantity) {}

    record UpdateItemCommand(String name, String description, int quantity) {}
}