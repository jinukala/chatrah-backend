---
inclusion: always
---

# Coding Standards — [Your Service Name]

> Fill in every section for your stack. The employee-data-svc values are shown as examples.
> Code patterns (async, DI, API, error handling) live in `api-patterns.md`.
> This file covers style, structure, testing, git, and security rules only.

---

## Formatting

> Example (employee-data-svc):
> - Google Java Format enforced by Spotless — run `./gradlew spotlessApply` before every commit
> - 100-character line limit, 2-space indentation
> - No wildcard imports
> - Static imports for mapper utility methods

- [Your formatter and command]
- [Line limit and indentation]
- [Import rules]

---

## Naming

| Thing | Convention | Example |
|-------|-----------|---------|
| Classes | [e.g. PascalCase] | [e.g. `EmployeeDataService`] |
| Methods / variables | [e.g. camelCase] | [e.g. `getEmployee`] |
| Constants | [e.g. UPPER_SNAKE_CASE] | [e.g. `MAX_RETRY_COUNT`] |
| Packages / modules | [e.g. lowercase] | [e.g. `com.example`] |
| Test classes | [e.g. `{ClassName}Test`] | [e.g. `EmployeeDataServiceTest`] |
| Test methods | `should{Behavior}_when{Condition}` | `shouldThrowException_whenAccountMissing` |

---

## Code Structure

- One public class per file
- Method order: public → protected → private
- [Import group order for your stack]
- Prefer `final` / `const` / `val` on parameters and local variables where possible
- No magic numbers or strings — use constants or enums

---

## Null Safety

> Example (employee-data-svc):
> - Use `StringUtil.isNullOrEmpty()` for string checks
> - Avoid returning `null` — throw a domain exception or use `Optional`
> - Validate inputs at method entry, not deep inside logic

- [Your null safety utilities and rules]

---

## Testing

### Structure

> Example (employee-data-svc — Quarkus + JUnit 5):
> ```java
> @QuarkusTest
> class EmployeeDataServiceTest {
>
>     @InjectMock
>     DataAccessService dataAccessService;
>
>     @Inject
>     EmployeeDataService service;
>
>     @Test
>     void shouldReturnEmployee_whenFoundInEds() {
>         // Given
>         when(dataAccessService.queryObjects(any()))
>             .thenReturn(Uni.createFrom().item(mockResponse));
>
>         // When
>         var result = service.getEmployee(request).await().indefinitely();
>
>         // Then
>         assertThat(result.getEmployeeId()).isEqualTo("123");
>     }
> }
> ```

```
[Your test class structure here]
```

### Coverage requirements

Every service method needs tests for:
- [ ] Happy path
- [ ] Missing auth header
- [ ] Not-found scenario
- [ ] Rollback scenario (if multi-step operation)

### Mocking rules

- Mock external dependencies, never the class under test
- [Your mocking framework and annotations]
- [Shared setup/teardown approach]

---

## Logging

> Example (employee-data-svc): Use `WFSLogger` — never `System.out.println` or `@Slf4j`.

Use [your logger] — never `System.out` or equivalent.

| Level | When |
|-------|------|
| `ERROR` | Exceptions, failures, rollbacks |
| `WARN` | Unexpected but recoverable |
| `INFO` | Important business events (created, updated, deleted) |
| `DEBUG` | Diagnostic detail |

Rules:
- Always log before throwing an exception
- Include context (account ID, resource ID) — never PII or secrets
- Log rollback attempts at ERROR level

---

## Security

- Never commit secrets — use env var references in config
- Service-to-service secrets via config injection only — never hardcoded
- Auth headers validated at every service entry point
- No PII in log statements
- Validate all external input before use

---

## Git Commits

```
<type>: <short summary>

<detail if needed>

Plan: docs/plans/YYYY-MM-DD-{slug}.md
```

Types: `feat` · `fix` · `refactor` · `test` · `docs` · `style` · `chore`

Keep commits small and focused — one logical change per commit.

---

## Code Review Checklist

- [ ] Formatter applied
- [ ] Tests pass
- [ ] No wildcard imports, no `System.out`, no secrets
- [ ] DocStrings / JavaDoc on all public API methods
- [ ] Tests cover happy path + error scenarios
- [ ] Async patterns correct (see `api-patterns.md`)
- [ ] Plan doc referenced in commit message

---

## Anti-Patterns

> Real examples from employee-data-svc:

```java
// ❌ Blocking in reactive chain
uni.onItem().transform(item -> {
    Thread.sleep(1000); // kills the event loop
    return item;
});

// ❌ Swallowing exceptions
try { riskyOp(); } catch (Exception e) { /* silent */ }

// ❌ Magic numbers
if (status == 404) { ... }
// ✅
private static final int HTTP_NOT_FOUND = 404;

// ❌ God class — keep classes under ~300 lines
// ❌ Catching generic Exception unless re-throwing with context

// ❌ Swallowing async failures (Mutiny-specific)
.onFailure().call(() -> Uni.createFrom().voidItem())
// ✅ Let failures propagate; use .invoke() for side-effect logging only
.onFailure().invoke(t -> logger.error("Operation failed", t))
```

> Add your own anti-patterns here as you discover them during Review phases.

---

## DocStrings / JavaDoc

Required on all public methods:

```java
/**
 * [One-line summary of what this method does.]
 *
 * @param request [description of the parameter]
 * @return [description of the return value]
 * @throws MissingParameterException if [condition]
 * @throws NotFoundException if [condition]
 */
public ReturnType methodName(RequestType request) { ... }
```
