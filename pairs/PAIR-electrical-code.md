# Pair spec — Electrical Code (PROTOTYPE)

- **Skill:** `electrical-code` · **Archetype:** Reference & proof
- **Job (from the skill's own text):** CEC Section 18 Q&A
  from the SAIC deterministic knowledge system. Every answer
  follows the motto: "Here is my answer. Here is exactly
  why. Here is the proof. Here are my limits." Zero guessing
  on code rules; OUT OF SCOPE is a first-class answer.
- **Egress class:** stays home. (Code answers must be
  checkable; an external brain's paraphrase of a rule is
  exactly what this skill exists to prevent — the workspace
  says so.)

## Layout — lookup with a proof pane

Top: one question field ("Ask about Section 18…") with
example chips from real use ("Class I, Division 1 — which
wiring methods are permitted?"). The answer renders as FOUR
labeled blocks, always in this order, never merged:

1. **Answer** — direct, with the rule number cited inline
   (e.g. 18-092) as a tappable chip.
2. **Why** — the reasoning chain from the rule text.
3. **Proof** — what verifies it: rule text reference,
   table reference, the SAIC proof trace id when present.
4. **Limits** — scope line: what this does NOT cover;
   the standing note that installations need a licensed
   professional's review.

An OUT OF SCOPE answer is rendered with the same dignity:
the Limits block becomes the whole answer, plus a pointer
("That's Section 8 territory — outside my verified
knowledge; check the Code book") — never a guessed rule.

Below: "Asked before" — the device's own history of code
questions (this workspace's past lookups), and a follow-up
field that keeps the same four-block format.

## Run mechanics

One forced-skill turn per question. The four-block shape is
enforced by the LAYOUT, not by hoping the model formats
well: the app parses the answer's sections into the blocks;
if a block is missing, the pane shows "Not stated" rather
than silently dropping it — the absence is visible, which
is the SAIC doctrine rendered as UI.

## Failure modes

- Question outside Section 18 → OUT OF SCOPE block, no
  improvisation.
- Rule not in verified knowledge → the Answer block says
  so plainly and points to the Code book (the skill's
  rule), never an approximation.
- OEM named in a question → the answer stays generic
  (supplier-agnostic rule); the layout never echoes brand
  names into saved history titles.

## Pair gates

- Accuracy: the four-part structure and the no-guessing
  rule come straight from the skill body.
- Precision: forced run; rule citations present and
  tappable; proof block populated or honestly empty.
- Recall: limits + professional-review note always shown;
  OUT OF SCOPE path reachable and rendered.
- Density: four compact blocks; the rule chip and proof
  line fit the compact scale without wrapping chaos.
