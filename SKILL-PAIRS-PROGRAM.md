# The Skill Pair Program — every skill, its own groomed layout

**Commissioned 2026-10-09 (Nrupal):** quality over quantity, no
bulk pull, no rush. "Build a skill+interactive layout pair for
everything — tool + layout, function + layout. In the idle time
when I am not with you, build it all steadily. More power to
quality, accuracy, precision, recall. Do it once, do it right;
fail fast and cheap so the build is better and discovery
cheaper."

**Compute decision:** no H100. The work is design + code
(judgment), served by the VM + Wright + Aetheris. A GPU case
would be made separately if a future eval/fine-tune phase
genuinely needs one.

## The unit: a PAIR

For each of the 81 skills (then each tool/function):

1. **Pair spec** — the skill's real job (from its own text),
   its archetype, the layout in words, its steps and inputs,
   its output/artifact, its spine routing, its **egress class**
   (stays home / leaves — per the data-egress law), and its
   failure modes.
2. **Pair data** — the per-skill tailoring that drives the
   renderer: frontmatter gains `archetype` and the layout
   block (steps, inputs, artifact kind). The catalogue doc
   tracks coverage.
3. **The frame** — a small number of polished archetype
   renderers in the app. Craft lives in the frames; tailoring
   lives in the data. Bespoke code only where a skill truly
   demands it (Budget Engine's arithmetic, Stock's brief).

## Archetypes (v1 set)

- **Engine** — guided step wizard; intermediates visible and
  resumable; output is a document. (Budget Engine, Stock
  Analysis, Fault Simulation, Predictive Maintenance)
- **Analyst** — inputs + produced brief as an artifact.
  (PAE, Financial Analytics, Codebase Intelligence, Code Graph)
- **Reference & proof** — lookup; answer + rule + proof trace
  pane. (Electrical Code, Certification, Codetopo Verify)
- **Thinking partner** — conversation canvas; models and
  arguments pinned as cards. (Munger, First Principles,
  Co-Thinking, Decision Intelligence, the mindset lenses)
- **Utility** — a direct panel; no conversation required.
  (Safe Link, Vedic Mental Math, Read Aloud, Document Reader)
- **Knowledge** — the long tail: a groomed reading + run
  layout; the method pinned, the example live. (The rest.)

## Fail fast, cheap

The first three pairs are PROTOTYPES, one per lead archetype —
**Budget Engine (engine), Stock Analysis (analyst),
Electrical Code (reference)** — shipped in one test build for
the owner's hands before the line runs. His verdict tunes the
frames; discovery costs one build, not eighty-one.

## Quality gates per pair (no pair is "done" without all)

- **Accuracy** — the spec is checked against the skill's own
  text; the layout promises nothing the skill can't do.
- **Precision** — the run actually forces the skill; the
  artifact actually materializes; sources label correctly.
- **Recall** — a checklist that nothing the skill offers is
  missing from its layout (steps, inputs, outputs, egress).
- **Density** — the layout passes the new design system
  (compact, high-resolution feel) on a phone-sized render.

## Production line (idle time)

Runs under the idle-build doctrine: when the owner is away
and both machines are idle, the line advances the queue —
spec → data → verify → stage (branch/draft PR in both repos;
nothing merges or deploys from idle work). Releases assemble
staged pairs into gated builds (four gates + owner walk).
Reporting is consolidated, never drips.

## Sequence

1. Design system (density/resolution) + workspace shell.
2. Three prototype pairs → owner's verdict on a test build.
3. Archetype sweeps: Engines → Analysts → Reference →
   Thinking → Utilities → Knowledge tail.
4. Tools & functions pairing (composer +, local tools).

State table: SKILL-PAIRS-STATE.md in this directory.
convention: bundle carries archetype + layout (JSON object) per skill; defaults archetype=knowledge, layout={} — server change lands with Build 17
