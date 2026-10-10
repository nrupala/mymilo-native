# Pair spec — Budget Engine (PROTOTYPE)

- **Skill:** `budget-engine` · **Archetype:** Engine
- **Job (from the skill's own text):** run the BEE 7-step
  household report — real numbers in, a one-page plan out,
  everything inflation-adjusted, no lectures.
- **Egress class:** stays home (Aetheris default). Household
  numbers are sensitive: the layout states this up front,
  and the workspace never suggests an external brain for it.

## Layout — guided wizard, one step visible at a time

Header: step rail (7 dots + names), progress kept on device;
a run can be paused and resumed. Each step card asks for
exactly what that step needs, in plain words, with the
reason one line under ("so the inflation view is real").

1. **Income picture** — rows: source + monthly take-home.
   Add/remove rows. Total shown as they type.
2. **Spending map** — category rows (housing, food,
   transport, …) with monthly amounts; "not sure" per row
   is allowed and flagged, never blocked.
3. **Inflation-real view** — computed panel: this year's
   spending in today's dollars vs last year's; no typing.
4. **Gaps** — the engine's read: where spending and the
   household's stated priorities disagree (one priority
   picker feeds this: dropdown — debt, savings, breathing
   room, other).
5. **Scenarios** — three toggles (job loss 3 months / rate
   rise / a big purchase with an amount) → effect on the
   plan, shown as deltas.
6. **Levers** — the engine ranks the 3 highest-impact moves;
   each lever card: effort vs return, accept/skip.
7. **Plan** — the artifact: a one-page document (income,
   map, real view, chosen levers, next actions). Save to
   Library · Share · Re-run next month.

## Run mechanics

Steps 1–2 and 5 collect structured inputs (device-side
forms). The engine's reasoning (steps 3–4, 6–7 synthesis)
runs as ONE forced-skill turn on the server with the
collected numbers in a fixed format, so the answer uses the
skill's method on real data — not a chat guessing. The plan
is stored as an Artifact (markdown) linked to the run's
chat; the chat remains for follow-up questions.

## Failure modes the layout must survive

- Missing numbers → the step says what's missing and why;
  "estimate it for me" is an explicit, labeled choice.
- Offline → forms still collect; the reasoning step queues
  and says so plainly.
- Server returns no plan → the inputs are kept; retry is
  one tap; nothing typed is ever lost.

## Pair gates

- Accuracy: steps/inputs match the skill body exactly
  (7 steps, inflation-real rule, no-shame rule visible).
- Precision: forced run uses budget-engine; the plan
  artifact materializes in the Library.
- Recall: scenarios + levers + plan all present; the
  "ask for numbers" rule honored by the forms.
- Density: one step per screen at the new type scale;
  no scrolling inside a step card on a standard phone.
