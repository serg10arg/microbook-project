package com.microbook.itemservice.infrastructure.persistence.repository;

import com.microbook.itemservice.domain.model.Item;
import com.microbook.itemservice.domain.port.out.ItemRepository;
import com.microbook.itemservice.infrastructure.persistence.mapper.ItemPersistenceMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de repositorio (patrón Adapter).
 *
 * Conecta el puerto de salida del dominio (ItemRepository)
 * con la implementación concreta de Spring Data JPA.
 *
 * El dominio sólo conoce ItemRepository (su puerto).
 * Spring Data JPA sólo conoce SpringDataItemRepository.
 * Este adaptador es el único que conoce ambos.
 *
 * Beneficio: cambiar de H2 → PostgreSQL → MongoDB implica sólo
 * reemplazar este adaptador, sin tocar domain ni application.
 */
@Component
public class ItemRepositoryAdapter implements ItemRepository {

    private final SpringDataItemRepository springDataRepo;
    private final ItemPersistenceMapper mapper;

    public ItemRepositoryAdapter(SpringDataItemRepository springDataRepo,
                                 ItemPersistenceMapper mapper) {
        this.springDataRepo = springDataRepo;
        this.mapper = mapper;
    }

    @Override
    public List<Item> findAll() {
        return springDataRepo.findAll()
                .stream()
                .map(mapper::toDomain)
                .toList();  // Java 16+ — lista inmutable
    }

    @Override
    public Optional<Item> findById(String id) {
        return springDataRepo.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Item save(Item item) {
        var entity = mapper.toJpaEntity(item);
        var saved  = springDataRepo.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public void deleteById(String id) {
        springDataRepo.deleteById(id);
    }

    @Override
    public boolean existsById(String id) {
        return springDataRepo.existsById(id);
    }
}