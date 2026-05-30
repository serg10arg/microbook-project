package com.microbook.itemservice.application.service;

import com.microbook.itemservice.domain.exception.ItemNotFoundException;
import com.microbook.itemservice.domain.model.Item;
import com.microbook.itemservice.domain.port.in.ItemUseCase;
import com.microbook.itemservice.domain.port.out.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicación: orquesta el flujo entre el dominio y la infraestructura.
 *
 * Responsabilidades:
 *   1. Validar que los datos de entrada tienen sentido (precondiciones)
 *   2. Coordinar dominio + repositorio
 *   3. Gestionar transacciones
 *
 * Lo que NO hace:
 *   - Lógica de negocio (eso es responsabilidad de Item)
 *   - Mapeo HTTP / JSON (eso es responsabilidad del controller)
 *   - Acceso directo a la BD (eso es responsabilidad del repositorio)
 */
@Service
@Transactional(readOnly = true)   // Por defecto read-only; los métodos que escriben lo sobreescriben
public class ItemService implements ItemUseCase {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;

    // Inyección por constructor: recomendada sobre @Autowired en campo
    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = Objects.requireNonNull(itemRepository);
    }

    @Override
    public List<Item> findAll() {
        log.debug("Fetching all items");
        return itemRepository.findAll();
    }

    @Override
    public Item findById(String id) {
        log.debug("Fetching item id={}", id);
        return itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
    }

    @Override
    @Transactional
    public Item create(CreateItemCommand command) {
        log.info("Creating item name='{}'", command.name());

        // El dominio crea el objeto con sus propias reglas (ID, timestamps)
        Item item = Item.create(command.name(), command.description(), command.quantity());
        Item saved = itemRepository.save(item);

        log.info("Item created id='{}'", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Item update(String id, UpdateItemCommand command) {
        log.info("Updating item id='{}'", id);

        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));

        // La lógica de mutación es del dominio, no del service
        item.update(command.name(), command.description(), command.quantity());

        Item saved = itemRepository.save(item);
        log.info("Item updated id='{}'", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void delete(String id) {
        log.info("Deleting item id='{}'", id);

        if (!itemRepository.existsById(id)) {
            throw new ItemNotFoundException(id);
        }

        itemRepository.deleteById(id);
        log.info("Item deleted id='{}'", id);
    }
}