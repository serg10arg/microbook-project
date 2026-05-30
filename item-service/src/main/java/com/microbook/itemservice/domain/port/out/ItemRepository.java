package com.microbook.itemservice.domain.port.out;

import com.microbook.itemservice.domain.model.Item;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida (driven port): define cómo el dominio necesita
 * acceder a la persistencia, SIN saber nada de JPA, SQL ni H2.
 *
 * La implementación concreta vive en infrastructure/persistence.
 * Si mañana cambiamos de H2 a PostgreSQL o a MongoDB, el dominio
 * no se toca — sólo la implementación del puerto.
 */
public interface ItemRepository {

    List<Item> findAll();

    Optional<Item> findById(String id);

    Item save(Item item);

    void deleteById(String id);

    boolean existsById(String id);
}