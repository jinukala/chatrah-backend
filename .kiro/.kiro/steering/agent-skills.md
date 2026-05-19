---
inclusion: manual
---

# Agent Skills

Skills are focused, reusable agent capabilities invoked on demand. Each skill performs one specific job with a clear input and output. Skills complement steering files (which set context) and can be composed into larger workflows.

Skills follow the [agentskills.io](https://agentskills.io) open specification. Each skill is a directory under `skills/` containing a `SKILL.md` file with standardized YAML frontmatter (`name`, `description`) and Markdown instructions.

## When to Use Skills

Use a skill when:
- A task matches a known multi-step pattern listed below
- You need a structured, repeatable output (checklist, gap report, formatted entries)
- The Compound workflow calls for a specific artifact (summary, learning entries, pattern extraction)

Do not use a skill when:
- The task is a one-off question or simple lookup
- The job is already covered by a steering file (e.g., coding standards, API patterns)

## Invocation

Invoke any skill by name with its required input:

> "Using the skill {skill-name}, {action} {input}"

Example: `"Using the skill summarize-plan, summarize docs/plans/2026-03-02-add-employee-changes-endpoint.md"`

## Skill Catalog

| Skill | Phase | Input | Output |
|-------|-------|-------|--------|
| `summarize-plan` | Pre-Work | Plan doc path | 3–5 bullet summary: changes, rollback strategy, riskiest step |
| `check-reactive-chain` | Work / Review | File path or code block | Checklist: failure handling, side effects, rollback, exception types |
| `generate-test-cases` | Pre-Work | Method signature + edge case table | Test method names in `should{Behavior}_when{Condition}` format |
| `extract-reusable-pattern` | Compound | Code snippet or file path + method | Annotated code block for solution doc `## Reusable Pattern` section |
| `diff-plan-vs-implementation` | Pre-Review | Plan doc path + changed file list | Gap report: implemented, deviated, missing, unplanned |
| `update-learnings` | Compound | Solution doc path | Formatted `[YYYY-MM-DD] Category: lesson` entries for `AGENTS.md` |

### summarize-plan

Sanity-check a plan before the agent starts Work.

- Location: `skills/summarize-plan/SKILL.md`
- Input: Plan doc path (e.g. `docs/plans/2026-03-02-add-employee-changes-endpoint.md`)
- Output: 3–5 bullet summary covering what changes, the rollback strategy, and the riskiest step
- Invoke: `"Using the skill summarize-plan, summarize docs/plans/[slug].md"`

### check-reactive-chain

Validate any reactive method for correctness.

- Location: `skills/check-reactive-chain/SKILL.md`
- Input: File path or pasted code block containing a reactive chain
- Output: Checklist covering:
  - `.onFailure()` placement relative to `.transformToUni()`
  - Side effects use `.invoke()`, not `.transform()`
  - Failures propagate (not swallowed via `.call(() -> Uni.createFrom().voidItem())`)
  - Rollback implemented for multi-step operations
  - Specific exception types, not catch-all `Exception`
- Invoke: `"Using the skill check-reactive-chain, review the deleteAndRecreate method in EmployeeDataService.java"`

### generate-test-cases

Produce a test checklist from method signatures and edge cases before writing test code.

- Location: `skills/generate-test-cases/SKILL.md`
- Input: Method signature + edge case table from the plan doc
- Output: Test method names in `should{Behavior}_when{Condition}` format covering:
  - Happy path
  - Missing auth header
  - Not-found scenario
  - Rollback scenario (if multi-step)
  - Every error case from the edge case table
- Invoke: `"Using the skill generate-test-cases, generate test cases for the updateEmployee method based on docs/plans/[slug].md"`

### extract-reusable-pattern

Capture a non-obvious code pattern during Compound phase for future reuse.

- Location: `skills/extract-reusable-pattern/SKILL.md`
- Input: Code snippet, or file path + method name
- Output: Annotated code block with inline comments explaining the problem solved, the non-obvious decision, and reuse caveats. Ready for a solution doc's `## Reusable Pattern` section.
- Invoke: `"Using the skill extract-reusable-pattern, extract the pattern from deleteAndRecreate in EmployeeDataService.java"`

### diff-plan-vs-implementation

Verify implementation matches the approved plan before Review.

- Location: `skills/diff-plan-vs-implementation/SKILL.md`
- Input: Plan doc path + list of changed files
- Output: Gap report with four sections:
  1. Steps implemented as written
  2. Steps that deviated (with reasons)
  3. Plan steps not implemented
  4. Code changes not in the plan
- Invoke: `"Using the skill diff-plan-vs-implementation, compare docs/plans/[slug].md against the changes in EmployeeDataService.java and EmployeeMapper.java"`

### update-learnings

Extract formatted learning entries at the end of every Compound phase.

- Location: `skills/update-learnings/SKILL.md`
- Input: Solution doc path (e.g. `docs/solutions/[slug].md`)
- Output: Entries formatted as `- [YYYY-MM-DD] Category: What was learned and why it matters` for the `<!-- LEARNINGS:START -->` block in `AGENTS.md`
- Categories: `Reactive` · `Testing` · `Security` · `Config` · `Code Gen` · `Imports` · `Architecture`
- Invoke: `"Using the skill update-learnings, extract learnings from docs/solutions/[slug].md"`

## Adding a New Skill

Add a skill when you find yourself giving the same multi-step instruction across multiple features.

1. Create a directory `skills/{skill-name}/` with a `SKILL.md` inside
2. The `SKILL.md` must have YAML frontmatter with `name` and `description` fields per the [agentskills.io spec](https://agentskills.io/specification)
3. The `name` must match the directory name (lowercase, hyphens only)
4. Add an entry to the catalog table and a detail section in this file

A skill should be scoped to one job, reusable across features, and composable with other skills.
