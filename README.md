# microbook-project

Implementación progresiva del libro **"The Art of Decoding Microservices"**
(Sumit Bhatnagar & Roshan Mahant) aplicando Clean Architecture,
código limpio y patrones de diseño con Spring Boot 3 y Java 21.

---

## Estructura del proyecto

```
microbook-project/               ← root (POM padre, sin código)
├── service-registry/            ← Eureka Server        (Cap. 4)
├── api-gateway/                 ← Spring Cloud Gateway (Cap. 4)
├── item-service/                ← Microservicio Items  (Cap. 4)
├── order-service/               ← Microservicio Órdenes (Cap. 4)
├── notification-service/        ← Microservicio Notif.  (Cap. 4)
├── infrastructure/
│   ├── docker/                  ← Dockerfiles          (Cap. 5)
│   └── k8s/                     ← Manifests Kubernetes (Cap. 5)
├── docs/
│   └── adr/                     ← Architecture Decision Records
├── docker-compose.yml           ← Entorno local completo
└── pom.xml                      ← POM padre multi-módulo
```

Cada microservicio sigue **Clean Architecture**:

```
src/main/java/com/microbook/{service}/
├── domain/
│   ├── model/          ← Entidades de negocio (sin frameworks)
│   ├── port/in/        ← Interfaces de casos de uso
│   ├── port/out/       ← Interfaces de repositorio
│   └── exception/      ← Excepciones de dominio
├── application/
│   └── service/        ← Orquestación: usa puertos, gestiona tx
└── infrastructure/
    ├── web/            ← Controllers, DTOs, mappers HTTP
    ├── persistence/    ← Entidades JPA, repositorios, mappers
    ├── messaging/      ← Producers y consumers Kafka
    └── config/         ← Beans de configuración Spring
```

---

## Requisitos previos

| Herramienta | Versión mínima |
|-------------|----------------|
| Java        | 21             |
| Maven       | 3.9+           |
| Docker      | 24+            |
| Git         | 2.40+          |

---

## Inicio rápido

### 1. Clonar el repositorio

```bash
git clone https://github.com/TU_USUARIO/microbook-project.git
cd microbook-project
```

### 2. Compilar todos los módulos desde la raíz

```bash
mvn clean install -DskipTests
```

### 3. Levantar el entorno local con Docker Compose

```bash
docker-compose up -d
```

### 4. Ejecutar un módulo individualmente

```bash
cd item-service
mvn spring-boot:run
```

Accede a la API en `http://localhost:8080/api/items`

---

## Progreso por capítulo

| Capítulo | Tema                                 | Estado |
|----------|--------------------------------------|--------|
| Cap. 4   | RESTful + Clean Architecture         | ✅     |
| Cap. 4   | Comunicación síncrona / asíncrona    | 🔄     |
| Cap. 4   | Event-Driven (Kafka)                 | 🔄     |
| Cap. 4   | Service Discovery + API Gateway      | 🔄     |
| Cap. 4   | Resiliencia (Resilience4j)           | 🔄     |
| Cap. 5   | Testing (Unit + Integration + E2E)   | 🔄     |
| Cap. 5   | Docker + Kubernetes                  | 🔄     |
| Cap. 6   | Seguridad (JWT + OAuth2)             | ⏳     |
| Cap. 6   | Logging + Métricas + Trazas          | ⏳     |

✅ Completo · 🔄 En progreso · ⏳ Pendiente

---

## Convenciones del proyecto

- **Ramas**: `main` (estable) · `develop` (integración) · `feature/cap4-kafka` (trabajo)
- **Commits**: formato convencional → `feat(item-service): add create endpoint`
- **Tests**: cada módulo tiene tests unitarios en `src/test/`
