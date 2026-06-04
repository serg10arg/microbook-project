package com.microbook.itemservice.infrastructure.web.controller;

import com.microbook.itemservice.domain.port.in.ItemUseCase;
import com.microbook.itemservice.infrastructure.web.dto.ItemDto;
import com.microbook.itemservice.infrastructure.web.mapper.ItemWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Controller REST de Items, documentado con Springdoc / OpenAPI 3.
 *
 * Estrategia de documentación:
 * ════════════════════════════
 * ① @Tag                 → agrupa todos los endpoints bajo "Items" en Swagger UI
 * ② @Operation           → describe qué hace cada endpoint (summary + description)
 * ③ @Parameter           → documenta parámetros de path/query con ejemplos
 * ④ @ApiResponses        → lista TODOS los códigos HTTP posibles, incluidos los de error
 * ⑤ ref = "#/components" → reutiliza los schemas de error definidos en OpenApiConfig
 *                          (sin repetir la definición de ProblemDetail en cada endpoint)
 *
 * Lo que NO hacemos:
 * ══════════════════
 * No duplicamos la validación en las anotaciones de Swagger.
 * Springdoc lee @NotBlank, @Size, @Min de los records ItemDto.CreateRequest
 * automáticamente y los muestra en el Swagger UI sin que lo repitamos aquí.
 */
@RestController
@RequestMapping("/api/items")
@Tag(
        name = "Items",
        description = "Gestión del catálogo de Items — CRUD completo con validación y manejo de errores RFC 9457"
)
public class ItemController {

    private final ItemUseCase itemUseCase;
    private final ItemWebMapper mapper;

    public ItemController(ItemUseCase itemUseCase, ItemWebMapper mapper) {
        this.itemUseCase = itemUseCase;
        this.mapper = mapper;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/items
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Listar todos los items",
            description = "Devuelve el catálogo completo de items. La lista puede estar vacía si no hay items creados."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description  = "Lista de items (puede ser vacía [])",
                    content      = @Content(
                            mediaType = "application/json",
                            array     = @ArraySchema(schema = @Schema(implementation = ItemDto.Response.class)),
                            examples  = @ExampleObject(
                                    name  = "Lista con un item",
                                    value = """
                        [
                          {
                            "id":          "550e8400-e29b-41d4-a716-446655440000",
                            "name":        "Laptop Dell XPS",
                            "description": "High performance laptop",
                            "quantity":    10,
                            "createdAt":   "2024-01-15T10:30:00Z",
                            "updatedAt":   "2024-01-15T10:30:00Z"
                          }
                        ]
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref          = "#/components/responses/InternalServerError"
            )
    })
    @GetMapping
    public ResponseEntity<List<ItemDto.Response>> findAll() {
        var items = itemUseCase.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/items/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Obtener un item por ID",
            description = "Devuelve un item concreto dado su UUID. Retorna 404 si el item no existe."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description  = "Item encontrado",
                    content      = @Content(
                            mediaType = "application/json",
                            schema    = @Schema(implementation = ItemDto.Response.class),
                            examples  = @ExampleObject(
                                    value = """
                        {
                          "id":          "550e8400-e29b-41d4-a716-446655440000",
                          "name":        "Laptop Dell XPS",
                          "description": "High performance laptop",
                          "quantity":    10,
                          "createdAt":   "2024-01-15T10:30:00Z",
                          "updatedAt":   "2024-01-15T10:30:00Z"
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    ref          = "#/components/responses/NotFound"       // reutiliza el schema global
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref          = "#/components/responses/InternalServerError"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ItemDto.Response> findById(
            @Parameter(
                    description = "UUID del item a consultar",
                    example     = "550e8400-e29b-41d4-a716-446655440000",
                    required    = true
            )
            @PathVariable String id) {

        var item = itemUseCase.findById(id);
        return ResponseEntity.ok(mapper.toResponse(item));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/items
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Crear un nuevo item",
            description = """
                Crea un item en el catálogo. El sistema genera automáticamente el ID y los timestamps.
                
                **Validaciones:**
                - `name`: obligatorio, entre 2 y 100 caracteres
                - `description`: obligatorio, máximo 500 caracteres
                - `quantity`: mayor o igual a 0
                
                Devuelve **201 Created** con el header `Location` apuntando al nuevo recurso.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description  = "Item creado correctamente",
                    headers      = @Header(
                            name        = "Location",
                            description = "URL del item recién creado",
                            schema      = @Schema(
                                    type    = "string",
                                    example = "/api/items/550e8400-e29b-41d4-a716-446655440000"
                            )
                    ),
                    content      = @Content(
                            mediaType = "application/json",
                            schema    = @Schema(implementation = ItemDto.Response.class),
                            examples  = @ExampleObject(
                                    value = """
                        {
                          "id":          "550e8400-e29b-41d4-a716-446655440000",
                          "name":        "Laptop Dell XPS",
                          "description": "High performance laptop",
                          "quantity":    10,
                          "createdAt":   "2024-01-15T10:30:00Z",
                          "updatedAt":   "2024-01-15T10:30:00Z"
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description  = "Datos de entrada inválidos",
                    content      = @Content(
                            mediaType = "application/json",
                            schema    = @Schema(ref = "#/components/schemas/ValidationError"),
                            examples  = @ExampleObject(
                                    name  = "Nombre vacío",
                                    value = """
                        {
                          "type":   "https://api.microbook.com/errors/validation-error",
                          "title":  "Validation error",
                          "status": 400,
                          "detail": "Request validation failed",
                          "errors": {
                            "name":     "name must not be blank",
                            "quantity": "quantity must be >= 0"
                          }
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref          = "#/components/responses/InternalServerError"
            )
    })
    @PostMapping
    public ResponseEntity<ItemDto.Response> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos del item a crear",
                    required    = true,
                    content     = @Content(
                            mediaType = "application/json",
                            schema    = @Schema(implementation = ItemDto.CreateRequest.class),
                            examples  = @ExampleObject(
                                    name  = "Item válido",
                                    value = """
                            {
                              "name":        "Laptop Dell XPS",
                              "description": "High performance laptop for development",
                              "quantity":    10
                            }
                            """
                            )
                    )
            )
            @Valid @RequestBody ItemDto.CreateRequest request) {

        var command = mapper.toCreateCommand(request);
        var created = itemUseCase.create(command);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/items/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Actualizar un item existente",
            description = "Reemplaza completamente los datos de un item. Retorna 404 si el item no existe."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description  = "Item actualizado correctamente",
                    content      = @Content(
                            mediaType = "application/json",
                            schema    = @Schema(implementation = ItemDto.Response.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    ref          = "#/components/responses/BadRequest"
            ),
            @ApiResponse(
                    responseCode = "404",
                    ref          = "#/components/responses/NotFound"
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref          = "#/components/responses/InternalServerError"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ItemDto.Response> update(
            @Parameter(
                    description = "UUID del item a actualizar",
                    example     = "550e8400-e29b-41d4-a716-446655440000",
                    required    = true
            )
            @PathVariable String id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Nuevos datos del item (reemplazo completo)",
                    required    = true,
                    content     = @Content(
                            mediaType = "application/json",
                            schema    = @Schema(implementation = ItemDto.UpdateRequest.class),
                            examples  = @ExampleObject(
                                    value = """
                            {
                              "name":        "Laptop Dell XPS v2",
                              "description": "Updated high performance laptop",
                              "quantity":    15
                            }
                            """
                            )
                    )
            )
            @Valid @RequestBody ItemDto.UpdateRequest request) {

        var command = mapper.toUpdateCommand(request);
        var updated = itemUseCase.update(id, command);
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/items/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
            summary     = "Eliminar un item",
            description = "Elimina permanentemente un item del catálogo. Retorna 404 si el item no existe."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description  = "Item eliminado correctamente — sin cuerpo de respuesta"
            ),
            @ApiResponse(
                    responseCode = "404",
                    ref          = "#/components/responses/NotFound"
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref          = "#/components/responses/InternalServerError"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(
                    description = "UUID del item a eliminar",
                    example     = "550e8400-e29b-41d4-a716-446655440000",
                    required    = true
            )
            @PathVariable String id) {

        itemUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}