---
inclusion: manual
---

# Multi-Agent Patterns

Single agents are good at sequential tasks. Multi-agent patterns are good at parallel work,
specialization, and catching what one agent misses.

This file defines when and how to split work across multiple agents in this workflow.

---

## When to Use Multiple Agents

Use a single agent when:
- The task is sequential and fits in one context window
- One phase of the compound loop (brainstorm, plan, work, review, compound)
- The task is under ~3 files and ~200 lines of change

Use multiple agents when:
- Review needs to be independent from the agent that wrote the code
- Work can be parallelized across files with no shared state
- You want a second opinion on a plan before approving it
- A large feature spans multiple services or layers

---

## Pattern 1: Reviewer Agent (most common)

**Problem**: The agent that wrote the code has context bias — it knows what it intended,
so it's less likely to catch what it actually did wrong.

**Solution**: Run Phase 4 (Review) with a fresh agent that has no memory of the Work phase.

```
Agent A (Writer)          Agent B (Reviewer)
─────────────────         ──────────────────
Phase 1: Brainstorm  →
Phase 2: Plan        →    [reads plan, no code context]
Phase 3: Work        →
                          Phase 4: Review
                          ↓
                          todos/p1-*.md, todos/p2-*.md
Agent A fixes P1s    ←
```

**How to run**:
1. Agent A completes Work and commits
2. Start a new session (fresh context)
3. Give the reviewer agent: the plan doc + the changed files only
4. Run `/review` or invoke the review prompt
5. Reviewer produces `todos/` findings with no knowledge of the implementation intent

**Why it works**: The reviewer sees the code as a reader would, not as the author would.
It catches missing edge cases, incorrect reactive chains, and security gaps that the writer
rationalized away.

---

## Pattern 2: Parallel Work Agents

**Problem**: Large features touch multiple independent layers (e.g. controller + service +
mapper + tests). Running them sequentially is slow and one agent accumulates too much context.

**Solution**: Split Work phase across agents by layer, with a coordinator that merges.

```
Coordinator Agent
  ↓ assigns tasks based on approved plan
  ├── Agent A: service layer (EmployeeDataService.java)
  ├── Agent B: mapper + helper (EmployeeMapper.java, EdsHelper.java)
  └── Agent C: tests (EmployeeDataServiceTest.java)
  ↓ coordinator reviews diffs, resolves conflicts
  → single PR
```

**Rules for parallel work**:
- Each agent gets: the approved plan + only the files it owns
- No agent modifies a file owned by another agent
- Interfaces (method signatures) must be finalized in the plan before splitting — agents
  can't negotiate signatures mid-work
- Coordinator does a final pass to verify method calls across boundaries compile

**When to use**: Features with 4+ file changes where layers are clearly independent.
Not worth the coordination overhead for small changes.

---

## Pattern 3: Plan Challenger

**Problem**: Plans have blind spots. The agent that wrote the plan is invested in it.

**Solution**: Before approving a plan, run a second agent whose only job is to find holes.

```
Agent A (Planner)     Agent B (Challenger)
─────────────────     ────────────────────
Phase 2: Plan    →    [reads plan only]
                      Asks:
                      - What edge cases are missing?
                      - What's the rollback if step 3 fails?
                      - Is the reactive chain correct?
                      - What happens if the downstream is slow?
                      ↓
                      List of gaps → Agent A addresses them
                      ↓
                      Plan updated → Status: APPROVED
```

**How to run**:
> "You are a plan challenger. Read docs/plans/[slug].md and find every gap:
>  missing edge cases, incomplete rollback strategy, untested error scenarios,
>  incorrect async chain assumptions. Do not suggest implementation — only find holes."

**When to use**: High-risk features (multi-step mutations, new external integrations,
security-sensitive changes). Overkill for simple additive changes.

---

## Pattern 4: Compound Synthesizer

**Problem**: After several features, `AGENTS.md` learnings accumulate but aren't organized.
Patterns repeat across solution docs without being generalized.

**Solution**: Periodically run a synthesis agent that reads all solution docs and extracts
higher-order patterns.

```
Synthesis Agent
  Input: docs/solutions/*.md (all solution docs)
  Output:
    - Generalized patterns worth adding to api-patterns.md
    - Rules that appear in multiple learnings → promote to Non-Negotiable Rules
    - Anti-patterns that keep recurring → add to coding-standards.md
    - Gaps in the workflow itself → update compound-workflow.md
```

**How to run** (after every 3–5 features):
> "Read all files in docs/solutions/. Identify:
>  1. Patterns that appear in 2+ solution docs — generalize them for api-patterns.md
>  2. Mistakes that appear in 2+ 'Mistakes to Avoid' sections — promote to AGENTS.md rules
>  3. Workflow steps that were consistently skipped or adapted — update compound-workflow.md
>  Produce a summary of proposed updates, not the updates themselves."

**When to use**: Every sprint boundary or after every 3–5 completed features.

---

## Agent Handoff Protocol

When passing work between agents, always include:

1. The plan doc — what was supposed to happen
2. The relevant changed files — what actually happened
3. The current `AGENTS.md` — the rules in force
4. A one-sentence handoff note — where things stand

Example handoff note:
> "Work phase complete. EmployeeDataService.updateEmployee and EmployeeMapper changes are done.
>  Tests are written but the rollback scenario test is failing — see the plan's test cases section.
>  Handing to reviewer agent for Phase 4."

Never hand off with just "here's the code" — the receiving agent needs the plan to review against.

---

## Anti-Patterns in Multi-Agent Work

- **Telephone game**: Agent A tells Agent B what to do in natural language instead of sharing
  the plan doc. Information degrades. Always share the source doc.
- **Overlapping ownership**: Two agents editing the same file. Pick one owner per file.
- **Skipping the handoff note**: The receiving agent has no context and re-derives assumptions
  that may differ from the original plan.
- **Parallel agents without finalized signatures**: Agents negotiate interfaces mid-work and
  produce incompatible method signatures. Finalize all signatures in the plan first.
