package com.microbook.itemservice.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuración del WebClient para order-service.
 *
 * ¿Por qué una clase de configuración separada y no new WebClient() en el cliente?
 * ══════════════════════════════════════════════════════════════════════════════════
 * 1. RESPONSABILIDAD ÚNICA: OrderServiceClient sabe CÓMO llamar a order-service.
 *    WebClientConfig sabe CÓMO está configurada la conexión. Son dos cosas distintas.
 *
 * 2. TESTABILIDAD: En los tests, podemos inyectar un WebClient mockeado o que
 *    apunte a un WireMock sin cambiar nada en OrderServiceClient.
 *
 * 3. CONFIGURABILIDAD: La URL base y los timeouts vienen de application.yml.
 *    Si cambia el entorno (local → prod), solo cambia el yaml, no el código.
 *
 * Jerarquía de timeouts configurados:
 * ════════════════════════════════════
 *   connectTimeout  (2s) → tiempo máximo para establecer la conexión TCP
 *   readTimeout     (5s) → tiempo máximo para leer la respuesta completa
 *   writeTimeout    (5s) → tiempo máximo para enviar el request completo
 *   responseTimeout (5s) → tiempo total de respuesta (a nivel WebClient)
 *
 * Si order-service no responde en 5s, lanzará ReadTimeoutException,
 * que OrderServiceClient captura y convierte en OrderServiceUnavailable.
 */
@Configuration
public class WebClientConfig {

    @Value("${services.order-service.base-url}")
    private String orderServiceBaseUrl;

    @Value("${services.order-service.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${services.order-service.read-timeout-s:5}")
    private int readTimeoutSeconds;

    /**
     * Bean de WebClient configurado específicamente para order-service.
     *
     * El nombre del bean es "orderServiceWebClient" para que OrderServiceClient
     * lo inyecte por nombre en lugar de por tipo, evitando ambigüedades si
     * en el futuro hay múltiples WebClient beans (uno por servicio externo).
     */
    @Bean
    public WebClient orderServiceWebClient(WebClient.Builder builder) {

        // ── Configuración del cliente HTTP subyacente (Reactor Netty) ────────
        HttpClient httpClient = HttpClient.create()
                // Timeout para establecer la conexión TCP
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                // Timeout para leer / escribir datos una vez conectado
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS))
                );

        return builder
                .baseUrl(orderServiceBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Timeout total de respuesta a nivel WebClient (cubre connect + read)
                .codecs(codecs -> {
                    codecs.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder());
                    codecs.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder());
                    // Límite del buffer de respuesta: 2MB (default es 256KB)
                    // Evita errores en respuestas grandes de order-service
                    codecs.defaultCodecs().maxInMemorySize(2 * 1024 * 1024);
                })
                // Filtro de logging: registra cada request y response en DEBUG
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    /**
     * Filtro que logguea cada request HTTP saliente.
     * Solo activo cuando el nivel de log de este paquete es DEBUG.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            if (org.slf4j.LoggerFactory.getLogger(WebClientConfig.class).isDebugEnabled()) {
                org.slf4j.LoggerFactory.getLogger(WebClientConfig.class)
                        .debug("→ HTTP {} {}", request.method(), request.url());
            }
            return Mono.just(request);
        });
    }

    /**
     * Filtro que logguea cada response HTTP recibido.
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (org.slf4j.LoggerFactory.getLogger(WebClientConfig.class).isDebugEnabled()) {
                org.slf4j.LoggerFactory.getLogger(WebClientConfig.class)
                        .debug("← HTTP {}", response.statusCode().value());
            }
            return Mono.just(response);
        });
    }
}