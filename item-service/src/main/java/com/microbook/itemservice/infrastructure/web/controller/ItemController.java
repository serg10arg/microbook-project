package com.microbook.itemservice.infrastructure.web.controller;

import com.microbook.itemservice.domain.port.in.ItemUseCase;
import com.microbook.itemservice.infrastructure.web.dto.ItemDto;
import com.microbook.itemservice.infrastructure.web.mapper.ItemWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Controller REST: adaptador de entrada HTTP.
 *
 * Responsabilidades ÚNICAS:
 *   1. Mapear HTTP → Command (via ItemWebMapper)
 *   2. Delegar al use case
 *   3. Mapear el resultado → HTTP Response
 *
 * Lo que NO hace:
 *   - Lógica de negocio (eso es ItemService)
 *   - Acceso a datos (eso es el repositorio)
 *   - Manejo de excepciones (eso es GlobalExceptionHandler)
 *
 * El controller es "thin" por diseño. Si tiene más de ~50 líneas de lógica,
 * hay algo que debería estar en el servicio.
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemUseCase itemUseCase;
    private final ItemWebMapper mapper;

    // El controller depende del puerto (interfaz), no de la implementación concreta
    public ItemController(ItemUseCase itemUseCase, ItemWebMapper mapper) {
        this.itemUseCase = itemUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<List<ItemDto.Response>> findAll() {
        var items = itemUseCase.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemDto.Response> findById(@PathVariable String id) {
        var item = itemUseCase.findById(id);
        return ResponseEntity.ok(mapper.toResponse(item));
    }

    @PostMapping
    public ResponseEntity<ItemDto.Response> create(@Valid @RequestBody ItemDto.CreateRequest request) {
        var command = mapper.toCreateCommand(request);
        var created = itemUseCase.create(command);

        // Buena práctica REST: devolver 201 Created con Location header
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemDto.Response> update(
            @PathVariable String id,
            @Valid @RequestBody ItemDto.UpdateRequest request) {

        var command = mapper.toUpdateCommand(request);
        var updated = itemUseCase.update(id, command);
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        itemUseCase.delete(id);
        return ResponseEntity.noContent().build();  // 204 No Content
    }
}