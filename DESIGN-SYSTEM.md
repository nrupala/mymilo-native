# MyMilo design system v1 — density & resolution

The complaint this answers (owner, 2026-10-09): Perplexity
renders *denser* — finer type, tighter spacing, more meaning
per screen — so it feels higher-resolution on the same phone.
Ours rendered sparse. This system makes compact the default
everywhere; every later surface inherits it.

## Type scale (sp / line-height)

- Display 20/26 semibold — screen titles only
- Title 16/22 semibold — card & section headings
- Body 15/21 regular — messages, blurbs, body copy
- Secondary 13/18 regular — descriptions, metadata rows
- Caption 12/16 — timestamps, source lines, footers
- Badge 11 semibold — chips, egress labels (the floor;
  nothing renders smaller)

Rules: body text never below 15 in reading surfaces; a
screen shows its primary action in Title or larger; two
lines of preview beat one line of preview + whitespace.

## Spacing scale (dp)

4 · 8 · 12 · 16 · 24 — nothing off-scale.
Screen edge padding 16 · card padding 12 · row vertical
padding 10 · section gap 16 · hairline dividers between
rows, not boxes around everything.

## Density targets

- Skill row: 56dp — name + one-line blurb + meta caption
  (★ pin at the right edge)
- Chat row in lists: 64dp with two-line preview
- Category header: 28dp, caption-caps, letter-spaced
- Composer: single row growing to 5 lines; the + button,
  text field, mic, send — all 40dp targets (compact but
  still thumb-safe per accessibility minimums)

## Shape & motion

- Radius: cards 10, sheets 16, chips full-round
- Elevation: flat + hairlines; one raised surface level
  for cards on the canvas
- Motion: 150–200ms ease-out; no springs, no bounce —
  speed reads as quality

## Doctrine checks (every screen, before it ships)

1. One obvious next action.
2. Plain words in the primary layer; jargon one layer
   down (jargon on the surface = a defect).
3. Every pixel earns its place: if removing a gap loses
   no meaning, the gap goes.
4. Egress honesty is visual: a source that sends data
   off the owner's systems carries its label in Caption
   at the point of choice, never in a footnote elsewhere.
