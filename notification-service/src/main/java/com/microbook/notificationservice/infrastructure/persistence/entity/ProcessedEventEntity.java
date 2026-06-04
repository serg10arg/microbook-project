package com.microbook.notificationservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Registro de eventos ya procesados — la clave de la idempotencia.
 *
 * ¿Por qué necesitamos esta tabla?
 * ══════════════════════════════════
 * Kafka garantiza entrega "at-least-once": un mismo mensaje puede
 * llegar DOS veces al consumer en estos escenarios:
 *
 *   1. El consumer procesó el mensaje pero crasheó ANTES de hacer commit
 *      del offset → Kafka lo reenvía al reiniciar.
 *
 *   2. El producer reintentó el envío por un fallo de red transitorio
 *      (incluso con idempotencia en el producer, el consumer puede verlo
 *      dos veces si tiene particiones reasignadas).
 *
 *   3. Rebalanceo de particiones: cuando un consumer nuevo se une al
 *      grupo, Kafka reasigna particiones y puede retroceder el offset.
 *
 * Sin idempotencia, enviaríamos la misma notificación dos veces al usuario.
 *
 * Con esta tabla:
 *   - Antes de procesar: SELECT COUNT(*) WHERE event_id = ?
 *   - Si ya existe → skip (log + return)
 *   - Si no existe  → procesar + INSERT event_id
 *
 * El INSERT se hace en la misma transacción que la lógica de negocio.
 * Si el negocio falla y hace rollback, el event_id tampoco se guarda
 * → el próximo intento lo procesa correctamente.
 *
 * Ruta: notification-service/src/main/java/…/infrastructure/persistence/entity/
 */
@Entity
@Table(
        name = "processed_events",
        indexes = @Index(name = "idx_event_id", columnList = "event_id", unique = true)
)
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * El eventId del OrderEvent (UUID único por publicación del producer).
     * Es la clave de idempotencia: si ya existe, el evento ya fue procesado.
     */
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    /** Tipo del evento procesado — útil para debugging y métricas. */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    /** orderId del evento — para poder buscar eventos por orden en caso de debug. */
    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    /** Momento en que se procesó el evento. */
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    // ── Constructores ─────────────────────────────────────────────────────────

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId, String eventType,
                                String orderId, Instant processedAt) {
        this.eventId     = eventId;
        this.eventType   = eventType;
        this.orderId     = orderId;
        this.processedAt = processedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long    getId()          { return id; }
    public String  getEventId()     { return eventId; }
    public String  getEventType()   { return eventType; }
    public String  getOrderId()     { return orderId; }
    public Instant getProcessedAt() { return processedAt; }
}