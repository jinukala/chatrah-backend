---
inclusion: always
---

# Compound Engineering Workflow

The loop: **Brainstorm → Plan → Work → Review → Compound → Repeat**

80% of effort goes to Brainstorm, Plan, and Review. 20% to Work and Compound.
Skip Compound and you've done traditional engineering with AI help — gains don't accumulate.

---

## Phase 1: Brainstorm

Use when requirements are fuzzy or you're not sure what to build.

**Goal**: Turn a vague idea into a clear problem statement with constraints and options.

**Steps**:
1. State the idea in one sentence
2. Answer: What problem does this solve? Who benefits? What are the constraints?
3. Research the codebase — what existing patterns apply?
4. Propose 2–3 approaches with tradeoffs
5. Pick one and capture the decision rationale

**Output**: `docs/brainstorms/YYYY-MM-DD-{feature-slug}.md`

**Template**:
```markdown
# Brainstorm: {Feature Name}
Date: YYYY-MM-DD

## Problem
What are we solving and why?

## Constraints
- Technical constraints
- Business constraints

## Existing Patterns That Apply
- Which current code patterns are relevant to this feature?

## Approaches Considered

### Option A: {name}
- Pros:
- Cons:

### Option B: {name}
- Pros:
- Cons:

### Option C: {name}
- Pros:
- Cons:

## Decision
Chosen: Option X
Rationale: Why this fits the codebase and constraints

## Open Questions
- Questions to resolve before planning

## Next Step
What happens after this doc (usually: resolve open questions → create plan)
```

> **Reference**: See `docs/brainstorms/example-2026-03-02-add-employee-changes-endpoint.md`
> for a real example showing how to evaluate options and capture a decision with rationale.

**For every codebase, always ask**:
- Does this touch a core data flow? (e.g. read path, write path, cache)
- Does this require a new endpoint or extend an existing one?
- Does this need a new external client or extend an existing one?
- Are there rollback requirements for multi-step operations?
- What auth/headers are required?

---

## Phase 2: Plan

Turn the brainstorm decision into an implementation blueprint.

**Goal**: A plan detailed enough that an AI agent can execute it without supervision.

**Steps**:
1. List every file that needs to change and why
2. Define the method signatures before writing any code
3. Identify edge cases and how to handle them
4. Define the test cases (happy path + error scenarios)
5. Get explicit sign-off before moving to Work

**Output**: `docs/plans/YYYY-MM-DD-{feature-slug}.md`

**Template**:
```markdown
# Plan: {Feature Name}
Date: YYYY-MM-DD
Brainstorm: docs/brainstorms/YYYY-MM-DD-{feature-slug}.md
Status: DRAFT | APPROVED | IN_PROGRESS | DONE

## Summary
One paragraph describing what will be built.

## Files to Change
| File | Change Type | Reason |
|------|-------------|--------|
| `{Controller}.java` | Add method | New endpoint |
| `{Service}.java` | Add method | Business logic |
| `{Mapper}.java` | Add mapper | New response type |

## New Files
| File | Purpose |
|------|---------|
| None | — |

## Method Signatures
```
// {Service}
public {ReturnType} {methodName}({RequestType} request);

// {Mapper}
public static {ResponseType} to{ResponseType}({Model} model);
```

## Implementation Steps
1. Step one (specific, actionable)
2. Step two
3. ...

## Async / Reactive Chain
```
// Pseudocode or real code showing the full async chain
return step1()
    .then(result -> step2(result))
    .catch(error -> handleError(error));
```

## Edge Cases
| Case | Handling |
|------|---------|
| Auth header missing | Throw MissingParameterException before any I/O |
| Resource not found | Throw domain NotFoundException |
| Downstream unavailable | Propagate failure, log at ERROR |
| Multi-step partial failure | Rollback completed steps, log at ERROR |

## Test Cases
- [ ] Happy path: {description}
- [ ] Error: missing auth header
- [ ] Error: resource not found
- [ ] Error: downstream unavailable
- [ ] Error: rollback scenario (if multi-step)

## Approval
- [ ] Plan reviewed and approved
```

> **Reference**: See `docs/plans/example-2026-03-02-add-employee-changes-endpoint.md`
> for a real example showing method signatures, a full reactive chain, and edge case table.

**The plan must address**:
- Which endpoint/method is being implemented
- Whether async/event patterns are needed
- Rollback strategy for any multi-step operations
- All error scenarios mapped to specific exception types

---

## Phase 3: Work

Execute the approved plan. The agent implements; you monitor.

**Steps**:
1. Create a feature branch: `git checkout -b feature/{slug}`
2. Execute plan steps in order
3. After each file change: verify it compiles
4. Run tests
5. Run formatter
6. Track progress against the plan checklist

**Rules during Work**:
- Do not deviate from the approved plan without flagging it
- If a step fails, adapt the plan — don't silently work around it
- Keep commits small and focused: one logical change per commit
- Never commit secrets, never use `System.out`, never block the event loop

**Commit message format**:
```
feat: add {feature} endpoint

Implements {method} in {Controller}.
Adds {service method} with rollback for {failure scenario}.

Plan: docs/plans/YYYY-MM-DD-{slug}.md
```

---

## Phase 4: Review

Catch issues before they ship. Capture learnings for the Compound step.

**Run these checks in parallel**:

### Security Review
- [ ] No secrets or credentials in code
- [ ] All inputs validated before use
- [ ] Auth headers validated at every service entry point
- [ ] Service secrets injected via config, not hardcoded
- [ ] No PII in log statements

### Async / Reactive Correctness
- [ ] All async methods return the correct async type (e.g. `Uni<T>`, `Promise`, `Observable`)
- [ ] No blocking calls inside async chains
- [ ] Side effects use the correct operator (e.g. `.invoke()` not `.transform()`)
- [ ] Error handling uses specific exception types, not catch-all
- [ ] Rollback implemented for multi-step operations

### Code Quality
- [ ] Formatter applied
- [ ] No wildcard imports
- [ ] No magic numbers or strings — use constants or enums
- [ ] Methods are focused (< 30 lines ideally)
- [ ] No God classes (< 300 lines)

### Test Coverage
- [ ] Happy path tested
- [ ] Missing auth header tested
- [ ] Not-found scenario tested
- [ ] Rollback scenario tested (if applicable)
- [ ] Test names follow `should{Behavior}_when{Condition}`

### Architecture
- [ ] Controller delegates immediately to service — no business logic in controller
- [ ] Mapper/transformer is stateless — no injection
- [ ] Helper methods are static where stateless
- [ ] New patterns consistent with existing codebase patterns

**Output**: `todos/` files for any findings

**Finding format**:
```markdown
# {P1|P2|P3}: {Short title}
Status: ready | pending
Reviewer: {which check found this}

## Issue
What's wrong and where.

## Fix
What needs to change.

## Why It Matters
Impact if not fixed.
```

**Priority guide**:
- P1 — Blocks merge: security issue, broken async chain, missing rollback, test failure
- P2 — Fix before next feature: code quality, missing test coverage, style violation
- P3 — Nice to fix: minor refactor, documentation gap

> **Reference**: See `todos/example-p1-reactive-failure-swallowed.md` for a real finding.

---

## Phase 5: Compound

**This is where the gains accumulate. Never skip it.**

After every completed feature or significant bug fix:

**Steps**:
1. Answer: What worked in the plan? What needed adjustment?
2. Answer: What did the agent get wrong initially?
3. Answer: What edge cases were discovered during testing?
4. Answer: What questions came up repeatedly?
5. Codify answers into `AGENTS.md`, steering files, or a solution doc
6. Create a solution doc in `docs/solutions/`

**Output**: `docs/solutions/YYYY-MM-DD-{slug}.md`

**Template**:
```markdown
---
date: YYYY-MM-DD
feature: {feature name}
category: {grpc | rest | reactive | testing | security | config | k8s}
tags: [tag1, tag2]
---

# Solution: {Feature Name}

## Problem Solved
What was built and what problem it addressed.

## What Worked
Patterns and approaches that should be reused.

## What Needed Adjustment
Parts of the plan that changed during implementation and why.

## Mistakes to Avoid
Specific things the agent got wrong that should be prevented next time.

## Reusable Pattern
```
// Annotated code snippet capturing the key pattern
```

## AGENTS.md Updates Made
- Added rule: ...
- Updated pattern: ...

## Related Solutions
- docs/solutions/...
```

> **Reference**: See `docs/solutions/example-2026-03-02-add-employee-changes-endpoint.md`
> for a real example showing how to capture mistakes and extract a reusable pattern.

**After writing the solution doc, update `AGENTS.md`**:
- Add a learning entry under `<!-- LEARNINGS:START -->`
- Update any pattern that was refined
- Add any new non-negotiable rule discovered

---

## Quick Reference

| Phase | Time % | Output |
|-------|--------|--------|
| Brainstorm | 20% | `docs/brainstorms/` |
| Plan | 30% | `docs/plans/` |
| Work | 10% | Feature branch + PR |
| Review | 30% | `todos/` findings |
| Compound | 10% | `docs/solutions/` + `AGENTS.md` update |

## Starting a New Feature

```
1. Create brainstorm doc
2. Get clarity on requirements
3. Create plan doc → get approval
4. git checkout -b feature/{slug}
5. Implement per plan
6. Run tests + formatter
7. Run review checklist → create todos/
8. Fix P1s, address P2s
9. Create PR
10. Write solution doc → update AGENTS.md
```
