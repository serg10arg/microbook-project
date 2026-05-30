package com.microbook.itemservice.infrastructure.persistence.repository;

import com.microbook.itemservice.infrastructure.persistence.entity.ItemJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad ItemJpaEntity.
 *
 * ¿Por qué existe esta interfaz y qué hace Spring con ella?
 * ══════════════════════════════════════════════════════════
 * Al extender JpaRepository<T, ID>, Spring Data genera en tiempo de
 * arranque una implementación completa en memoria que incluye:
 *
 *   findAll()          → SELECT * FROM items
 *   findById(id)       → SELECT * FROM items WHERE id = ?
 *   save(entity)       → INSERT o UPDATE según si el ID ya existe
 *   deleteById(id)     → DELETE FROM items WHERE id = ?
 *   existsById(id)     → SELECT COUNT(*) FROM items WHERE id = ?
 *   count()            → SELECT COUNT(*) FROM items
 *   ... y más
 *
 * No necesitas escribir SQL ni implementar ningún método.
 * Spring los genera automáticamente.
 *
 * Parámetros de tipo en JpaRepository<T, ID>:
 *   T  = ItemJpaEntity  → el tipo de la entidad que gestiona
 *   ID = String         → el tipo del campo @Id de esa entidad
 *
 * ¿Por qué String y no Long como en el libro?
 * ════════════════════════════════════════════
 * El libro usa Long porque genera IDs secuenciales (1, 2, 3...).
 * Nosotros usamos UUIDs (String) generados en Item.create(),
 * lo que permite crear el ID en el dominio sin depender de la BD.
 * Ventaja: puedes saber el ID antes de hacer el INSERT.
 *
 * ¿Por qué está en infrastructure y no en domain?
 * ══════════════════════════════════════════════════
 * Esta interfaz extiende JpaRepository, que es parte de Spring Data JPA
 * (un detalle de infraestructura). El dominio no la conoce directamente.
 * El dominio solo conoce ItemRepository (su puerto de salida), que es
 * una interfaz pura sin dependencias de framework.
 * ItemRepositoryAdapter es quien conecta ambas.
 *
 * Cómo añadir queries derivadas (si las necesitas más adelante):
 * ══════════════════════════════════════════════════════════════
 * Spring Data puede generar queries a partir del nombre del método:
 *
 *   List<ItemJpaEntity> findByName(String name);
 *   → SELECT * FROM items WHERE name = ?
 *
 *   List<ItemJpaEntity> findByNameContainingIgnoreCase(String keyword);
 *   → SELECT * FROM items WHERE LOWER(name) LIKE LOWER('%keyword%')
 *
 *   List<ItemJpaEntity> findByQuantityGreaterThan(int min);
 *   → SELECT * FROM items WHERE quantity > ?
 *
 *   boolean existsByName(String name);
 *   → SELECT COUNT(*) FROM items WHERE name = ? (devuelve true/false)
 *
 * Para queries más complejas, usa @Query con JPQL o SQL nativo:
 *
 *   @Query("SELECT i FROM ItemJpaEntity i WHERE i.quantity = 0")
 *   List<ItemJpaEntity> findOutOfStock();
 */
public interface SpringDataItemRepository extends JpaRepository<ItemJpaEntity, String> {

    /*
     * Por ahora no necesitamos métodos adicionales: JpaRepository
     * ya provee todo lo que ItemRepositoryAdapter necesita.
     *
     * A medida que el proyecto crezca (Cap. 4 en adelante), añade
     * aquí los queries derivados o @Query que necesites.
     */
}