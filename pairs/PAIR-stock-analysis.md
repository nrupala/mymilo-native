# Pair spec — Stock Analysis (PROTOTYPE)

- **Skill:** `stock-analysis` · **Archetype:** Analyst
- **Job (from the skill's own text):** the research-analyst
  7-step method — fundamentals, technicals, macro, peers,
  valuation (Bull/Base/Bear), risk map, report — with every
  conclusion traced to an assumption. Education only; never
  a buy/sell call. The exchange is respected exactly:
  `.to` is TSX/CAD, never substituted.
- **Egress class:** the ticker itself is the query; when the
  brain is Aetheris the live market data comes from the
  server's own feeds. The workspace states the data path.

## Layout — brief room

Top: a single input row — **Ticker** (with exchange helper:
type `CNQ.to`, the chip shows "CNQ · TSX · CAD" before
running — the currency check is visible, not hidden) and a
**Run analysis** button. Below, the brief as it builds:

- **Step rail** — the 7 steps light up as the report's
  sections land (the answer streams into its sections, not
  one wall of text).
- **The brief (artifact)** — sections in the method's fixed
  order; the header carries ticker · exchange · currency ·
  date · data sources (the server's sources line). The
  Bull/Base/Bear block renders as three columns/cards with
  their assumptions listed under each number.
- **Risk map** — Probability × Impact × Timing shown as a
  small grid, not bullets (the skill demands the framework).
- **Footer** — the education-only line, verbatim spirit:
  "Research and education. Not a recommendation."
- Actions: Save brief to Library · Share · Re-run (fresh
  data) · Ask a follow-up (opens the run's chat).

## Run mechanics

One forced-skill turn; the server's market-data context does
the live numbers. The app does not compute valuation — it
presents the method's output in the method's shape. If live
data is absent, the brief's header says so (the skill's
"state assumptions" rule) — the layout reserves a visible
"Data: live / assumptions used" badge so staleness can
never hide.

## Failure modes

- Unknown ticker → plain error before any run ("Couldn't
  find CNQ.x — check the exchange suffix"), no credits spent.
- Mid-run disconnect → the partial brief is kept and
  labeled partial; resume continues the same artifact.
- US/Canada mix-up → impossible by construction: the
  exchange chip is chosen before the run and printed on
  the artifact.

## Pair gates

- Accuracy: 7 sections in order; variant perception
  (consensus vs differentiated) present in every report.
- Precision: forced run; artifact saved; sources shown.
- Recall: ticker-resolution rules, assumption tracing,
  stale-data labeling, no-recommendation rule — all
  visible in the layout, none dropped.
- Density: brief reads at the compact scale; the three
  scenario cards fit without horizontal scroll.
