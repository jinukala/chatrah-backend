---
inclusion: fileMatch
fileMatchPattern: '**/controllers/**,**/service/**,**/mappers/**,**/helpers/**,**/restclient/**,**/client/**,**/model/**,**/exceptions/**'
---

# API Patterns — [Your Service Name]

Single source of truth for all code patterns in this service.
Style rules and testing structure live in `coding-standards.md`.

> Fill in each section for your stack.
> The employee-data-svc (Quarkus + Mutiny + gRPC) patterns are shown as concrete examples.
> Replace or extend them for your framework.

---

## Dependency Injection

> Example (employee-data-svc — Quarkus CDI):
> ```java
> @GrpcClient("dal-control-svc")
> DataAccessService dataAccessService;
>
> @RestClient @Inject EmployeeRestClient employeeRestClient;
>
> @Inject WFSLogger wfsLogger;
> @Inject MetadataHeadersContext metadataHeadersContext;
> @Inject Event<EmployeeModel> employeePersistEvent;
> ```
> Services are `@ApplicationScoped`. Controllers are `@GrpcService`.

```
[Your DI patterns here]
```

---

## Async / Reactive Patterns

> Example (employee-data-svc — SmallRye Mutiny):

### The golden rule: keep the chain fluent, never break it

```java
// ✅ Fluent chain
return service.getData()
    .onItem().transform(this::map)
    .onItem().transformToUni(this::enrich)
    .onFailure(SpecificException.class).recoverWithUni(this::handle);

// ❌ Breaking the chain
Uni<Data> uni = service.getData();
Uni<Mapped> mapped = uni.onItem().transform(this::map); // don't do this
```

### Transform vs invoke

```java
// Synchronous transformation — use transform
.onItem().transform(model -> Mapper.toResponse(model))

// Async transformation — use transformToUni
.onItem().transformToUni(id -> dataService.getObject(id))

// Side effects (logging, events) — use invoke, never transform
.invoke(model -> logger.debug("Processing: " + model.getId()))
```

### Rollback pattern (multi-step operations)

```java
// ✅ Correct: .onFailure() scoped to the association step only
return dataService.createObject(createRequest)
    .onItem().transformToUni(createResponse -> {
        return dataService.createAssociation(buildAssociationRequest(createResponse))
            .onFailure().call(throwable -> {
                logger.error("Association failed, rolling back", throwable);
                return dataService.deleteObject(createResponse.getId());
            })
            .replaceWith(createResponse);
    });

// ❌ Wrong: .onFailure() placed after .transformToUni() catches failures from BOTH steps
return dataService.createObject(createRequest)
    .onItem().transformToUni(r -> dataService.createAssociation(r))
    .onFailure().call(t -> rollback()); // catches inner Uni failures too — don't do this
```

### Async fire-and-forget (cache / side-effect persistence)

```java
// Fire — non-blocking
.invoke(model -> persistEvent.fireAsync(model))

// Observe — runs asynchronously in background
public void persistAsync(@ObservesAsync Model entity) {
    createEntity(entity)
        .subscribe().with(
            success -> logger.info("Persisted"),
            failure -> logger.error("Persist failed", failure)
        );
}
```

> Replace with your framework's equivalent (e.g. `@EventListener` + `@Async` in Spring,
> `process.nextTick` in Node, `asyncio.create_task` in Python).

---

## Error Handling

### Auth header validation (required at every service entry)

> Example (employee-data-svc):
> ```java
> private String validateAndGetAccountInternalId() {
>     String accountId = metadataHeadersContext.getAccountInternalId();
>     if (StringUtil.isNullOrEmpty(accountId)) {
>         wfsLogger.error("Missing required header: X-Account-Internal-Id");
>         throw new MissingParameterException(ExceptionDomain.WFS, List.of(violation));
>     }
>     return accountId;
> }
> ```

```
[Your auth validation pattern here]
```

### Downstream 404 → domain exception

```java
// ✅ Map HTTP 404 to a domain exception, propagate everything else
return restClient.getResource(id)
    .onFailure(ClientWebApplicationException.class)
    .recoverWithUni(throwable -> {
        if (((ClientWebApplicationException) throwable).getResponse().getStatus() == 404) {
            return Uni.createFrom().failure(new NotFoundException("Resource not found: " + id));
        }
        return Uni.createFrom().failure(throwable);
    });
```

### Always use specific failure types

```java
// ✅ Specific — only handles HTTP errors
.onFailure(ClientWebApplicationException.class).recoverWithUni(this::handleHttp)

// ❌ Catches everything — masks real errors
.onFailure().recoverWithUni(this::handleAll)
```

---

## Controller / Handler Pattern

Controllers are thin adapters. Zero business logic.

> Example (employee-data-svc — gRPC):
> ```java
> @GrpcService
> public class EdsGrpcController implements EmployeeService {
>
>     @Inject
>     EmployeeDataService employeeDataService;
>
>     @Override
>     public Uni<CreateEmployeeResponse> createEmployee(CreateEmployeeRequest request) {
>         return employeeDataService.createEmployee(request);
>     }
> }
> ```

```
[Your controller pattern here — one line per endpoint, delegate immediately]
```

---

## Service Method Structure

```
[Your service method structure here]

// Pattern:
// 1. Validate auth / headers first
// 2. Build domain model from request
// 3. Execute business logic reactively / asynchronously
// 4. Map result to response type
```

> Example (employee-data-svc):
> ```java
> public Uni<GetEmployeeResponse> getEmployee(GetEmployeeRequest request) {
>     String account = validateAndGetAccountInternalId();
>     wfsLogger.debug("Received getEmployee for account: " + account);
>     EmployeeIdModel idModel = new EmployeeIdModel(request);
>     return findEmployeeById(idModel, account)
>         .onItem().transform(emp -> EmployeeMapper.toGetEmployeeResponse(emp, request.getReadMask()));
> }
> ```

---

## Mapper / Transformer Pattern

Mappers are stateless utility classes — no injection, no state.

> Example (employee-data-svc):
> ```java
> public class EmployeeMapper {
>     private EmployeeMapper() {} // prevent instantiation
>
>     public static CreateEmployeeResponse toCreateEmployeeResponse(EmployeeModel model) {
>         return CreateEmployeeResponse.newBuilder()
>             .setEmployeeId(model.getId())
>             .build();
>     }
> }
> ```

```
[Your mapper/transformer pattern here]
```

---

## External Client Pattern

> Example (employee-data-svc — MicroProfile REST Client):
> ```java
> @RegisterRestClient(configKey = "employee-api")
> @Path("/employee/v1")
> @ClientHeaderParam(name = "X-Service-Secret", value = "${service-secret}")
> public interface EmployeeRestClient {
>
>     @GET
>     @Path("/{account}")
>     Uni<EmployeeModel> getEmployee(
>         @PathParam("account") String account,
>         @QueryParam("employeeGuid") String guid
>     );
> }
> ```

```
[Your external client pattern here — how to call downstream services, inject auth, map errors]
```

---

## Query Patterns

> Example (employee-data-svc — EDS association attribute query):
> ```java
> QueryObjectsRequest request = EdsHelper.generateQueryByAssociationAttributesRequest(
>     attr.getPath(), attr.getValue());
>
> return dataAccessService.queryObjects(request)
>     .onItem().transformToUni(response -> {
>         if (response.getObjectsCount() == 0) {
>             return Uni.createFrom().failure(new NotFoundException("Not found"));
>         }
>         return Uni.createFrom().item(
>             EmployeeMapper.toModel(response.getObjects(0)));
>     });
> ```

```
[Your data store query patterns here — how to query, paginate, handle empty results]
```

---

## Header Propagation

> Example (employee-data-svc):
> ```java
> // From gRPC metadata context
> String account = metadataHeadersContext.getAccountInternalId();
>
> // Static header on REST client
> @ClientHeaderParam(name = "X-Service-Secret", value = "${service-secret}")
> ```

```
[Your header propagation patterns here]
```

---

## Performance

- Avoid blocking calls inside async chains — offload to a worker pool if needed
- Use field masks / projections to limit data transfer — don't fetch and discard fields
- [Add your stack-specific performance rules here]
