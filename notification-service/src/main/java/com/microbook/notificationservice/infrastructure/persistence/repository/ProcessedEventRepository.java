package com.microbook.notificationservice.infrastructure.persistence.repository;

import com.microbook.notificationservice.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio para verificar y registrar eventos procesados.
 *
 * existsByEventId() es el método crítico para la idempotencia:
 * se llama antes de procesar cada evento para detectar duplicados.
 *
 * La query generada por Spring Data es:
 *   SELECT COUNT(*) > 0 FROM processed_events WHERE event_id = ?
 *
 * El índice UNIQUE en event_id (definido en ProcessedEventEntity)
 * garantiza que no pueden existir dos registros con el mismo eventId
 * aunque dos threads intenten insertarlo simultáneamente — la BD
 * lanzará una excepción de constraint violation que se convierte en skip.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, Long> {

    boolean existsByEventId(String eventId);
}