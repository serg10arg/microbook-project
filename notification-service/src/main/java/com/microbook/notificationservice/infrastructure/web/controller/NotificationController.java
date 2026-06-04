package com.microbook.notificationservice.infrastructure.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controller mínimo del notification-service.
 *
 * Por ahora solo expone un endpoint de health para verificar que el servicio
 * arranca y responde correctamente antes de implementar la lógica real.
 *
 * Lo que viene en fases posteriores (no tocar hasta entonces):
 *   Fase 3 → infrastructure/messaging/consumer/  (Kafka listener de OrderEvents)
 *   Fase 6 → infrastructure/web/controller/      (endpoints reales de notificaciones)
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /**
     * GET /api/notifications/health
     *
     * Devuelve el estado del servicio con un timestamp.
     * Útil para verificar que el servicio está vivo antes de conectar
     * los otros servicios con él en las fases siguientes.
     *
     * Nota: Spring Actuator (/actuator/health) ya provee un health check
     * estándar. Este endpoint es adicional, específico del dominio,
     * y es el punto de entrada que usaremos para probar el servicio ahora.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "service",   "notification-service",
                "status",    "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}