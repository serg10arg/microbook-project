package com.microbook.itemservice.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Configuración global de OpenAPI 3 con Springdoc.
 *
 * Centraliza dos cosas:
 *   1. Metadatos de la API (título, versión, contacto)
 *   2. Responses de error reutilizables (400, 404, 500)
 *      basados en ProblemDetail (RFC 9457)
 *
 * Así los controllers no repiten la definición del esquema de error
 * en cada endpoint: solo referencian la respuesta por nombre.
 *
 * Ruta: item-service/src/main/java/com/microbook/itemservice/infrastructure/config/
 *
 * Dependencia necesaria en item-service/pom.xml:
 * <dependency>
 *   <groupId>org.springdoc</groupId>
 *   <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
 *   <version>2.5.0</version>
 * </dependency>
 *
 * URLs disponibles una vez arrancado el servicio:
 *   Swagger UI   → http://localhost:8080/swagger-ui.html
 *   OpenAPI JSON → http://localhost:8080/v3/api-docs
 *   OpenAPI YAML → http://localhost:8080/v3/api-docs.yaml
 */
@Configuration
public class OpenApiConfig {

    /**
     * Metadatos globales de la API.
     *
     * Aparecen en la cabecera del Swagger UI y en el openapi.json
     * que se comparte con otros equipos o herramientas de generación de clientes.
     */
    @Bean
    public OpenAPI itemServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Item Service API")
                        .description("""
                                Microservicio de gestión de Items.
                                
                                Implementado con **Clean Architecture** (domain / application / infrastructure).
                                Los errores siguen el estándar **RFC 9457 Problem Details for HTTP APIs**.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo microbook")
                                .email("dev@microbook.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    /**
     * Customizer global: añade los schemas de error reutilizables
     * al componente "responses" de la especificación OpenAPI.
     *
     * Una vez registrados aquí, los controllers los referencian con:
     *   @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
     *
     * Resultado: el esquema de ProblemDetail aparece una sola vez
     * en la especificación y todos los endpoints lo reutilizan.
     */
    @Bean
    public OpenApiCustomizer problemDetailResponsesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }

            // Schema de ProblemDetail (RFC 9457)
            // Refleja exactamente lo que devuelve GlobalExceptionHandler
            Schema<?> problemDetailSchema = new Schema<>()
                    .type("object")
                    .description("Error estándar RFC 9457 — Problem Details for HTTP APIs")
                    .addProperty("type",     new Schema<>().type("string")
                            .description("URI que identifica el tipo de error")
                            .example("https://api.microbook.com/errors/item-not-found"))
                    .addProperty("title",    new Schema<>().type("string")
                            .description("Título corto y legible del problema")
                            .example("Item not found"))
                    .addProperty("status",   new Schema<>().type("integer")
                            .description("Código HTTP del error")
                            .example(404))
                    .addProperty("detail",   new Schema<>().type("string")
                            .description("Explicación detallada del problema")
                            .example("Item not found with id: abc-123"))
                    .addProperty("instance", new Schema<>().type("string")
                            .description("URI del recurso que originó el error")
                            .example("/api/items/abc-123"));

            // Schema de error de validación (extiende ProblemDetail con campo "errors")
            Schema<?> validationErrorSchema = new Schema<>()
                    .type("object")
                    .description("Error de validación — incluye los campos que fallaron")
                    .addProperty("type",   new Schema<>().type("string")
                            .example("https://api.microbook.com/errors/validation-error"))
                    .addProperty("title",  new Schema<>().type("string")
                            .example("Validation error"))
                    .addProperty("status", new Schema<>().type("integer").example(400))
                    .addProperty("detail", new Schema<>().type("string")
                            .example("Request validation failed"))
                    .addProperty("errors", new Schema<>()
                            .type("object")
                            .description("Mapa campo → mensaje de error")
                            .example(Map.of("name", "name must not be blank",
                                    "quantity", "quantity must be >= 0")));

            // Registrar responses reutilizables en components.responses
            openApi.getComponents()
                    .addSchemas("ProblemDetail", problemDetailSchema)
                    .addSchemas("ValidationError", validationErrorSchema);

            if (openApi.getComponents().getResponses() == null) {
                openApi.getComponents().setResponses(new java.util.LinkedHashMap<>());
            }

            var responses = openApi.getComponents().getResponses();

            responses.put("BadRequest", buildResponse(
                    HttpStatus.BAD_REQUEST,
                    "Datos de entrada inválidos — uno o más campos fallaron la validación",
                    "ValidationError"));

            responses.put("NotFound", buildResponse(
                    HttpStatus.NOT_FOUND,
                    "El recurso solicitado no existe",
                    "ProblemDetail"));

            responses.put("Conflict", buildResponse(
                    HttpStatus.CONFLICT,
                    "Conflicto de estado — la operación no es válida en el estado actual",
                    "ProblemDetail"));

            responses.put("InternalServerError", buildResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error interno del servidor",
                    "ProblemDetail"));
        };
    }

    private ApiResponse buildResponse(HttpStatus status, String description, String schemaName) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(
                                new Schema<>().$ref("#/components/schemas/" + schemaName))));
    }
}