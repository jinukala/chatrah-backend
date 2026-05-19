---
inclusion: always
---

# Project Context — [Your Service Name]

> Fill in every section below for your service.
> The employee-data-svc values are shown as inline examples where helpful.

---

## What This Service Does

> Example (employee-data-svc):
> Quarkus gRPC microservice that manages employee data using a cache-aside pattern.
> Clients query EDS first. On miss, the service fetches from Person Service and asynchronously
> persists to EDS for future queries.

[Describe your service here — purpose, pattern, clients]

---

## Technology Stack

| Concern | Technology |
|---------|-----------|
| Framework | [e.g. Quarkus 3.x / Spring Boot 3.x / Express] |
| Language | [e.g. Java 21 / TypeScript / Python 3.12] |
| Build | [e.g. Gradle + Spotless / Maven / npm] |
| Primary API | [e.g. gRPC / REST / GraphQL] |
| External API | [e.g. REST via MicroProfile REST Client] |
| Async | [e.g. SmallRye Mutiny / Project Reactor / async-await] |
| Database | [e.g. PostgreSQL / DynamoDB / Redis] |
| Deployment | [e.g. Kubernetes + Kustomize / ECS / Lambda] |
| Observability | [e.g. OpenTelemetry + Grafana LGTM] |

---

## Source Structure

> Example (employee-data-svc):
> ```
> src/main/java/com/workforcesoftware/employeedatasvc/
> ├── application/controllers/   EdsGrpcController — thin gRPC adapter, no business logic
> ├── service/                   EmployeeDataService — all business logic lives here
> ├── restclient/                EmployeeRestClient — Person Service integration
> ├── mappers/                   EmployeeMapper — stateless gRPC ↔ model conversion
> ├── model/                     EmployeeModel, EmployeeIdModel
> ├── helpers/                   EdsHelper (request builders), EmployeeIdAttribute
> ├── constants/                 AssociationType, ObjectType enums
> └── exceptions/                EdsRuntimeException, EmployeeNotFoundException
> ```

```
[Your package/directory tree here — one-line description per package]
```

---

## Request Flow

> Example (employee-data-svc):
> ```
> gRPC Request
>     → EdsGrpcController          (validate proto, delegate immediately)
>     → EmployeeDataService        (validate account header, business logic)
>     → DataAccessService (gRPC)   (query EDS)
>     → [on miss] EmployeeRestClient (fetch from Person Service)
>     → [async] event.fireAsync()  (persist to EDS via @ObservesAsync)
>     → Response with FieldMask
> ```

```
[Your request flow here — step by step from entry point to response]
```

---

## Key Domain Concepts

> Example (employee-data-svc):
> Exactly ONE identifier per request — validated by `EmployeeIdModel`:
> - `employeeGuid`
> - `clientPersonId`
> - `displayId`

[Describe your core domain entities, identifiers, and validation rules]

---

## Internal Libraries

| Library | Purpose |
|---------|---------|
| [library name] | [what it does] |

---

## Required Headers / Auth

| Header / Token | Purpose | Validated by |
|----------------|---------|-------------|
| [header name] | [purpose] | [where validated] |

---

## Configuration Profiles

| Profile | Config file | Used for |
|---------|------------|---------|
| (default) | `application.properties` | Common to all |
| `dev` | `application-dev.properties` | Local dev mode |
| `test` | `application-test.properties` | Test execution |
| production | `[path]` | Deployed clusters |

Key properties:
- [property]: [what it controls]

---

## Running Locally

```bash
# Dev mode (hot reload)
[command]

# Run tests
[command]

# Format code
[command]
```

---

## Health & Observability

- Health endpoint: [URL]
- Dev UI / dashboard: [URL]
- Tracing: [tool and setup]

---

## Secrets Management

- Local dev: [where secrets live]
- Staging/prod: [how secrets are managed]
- Never commit: [what must never be committed]
