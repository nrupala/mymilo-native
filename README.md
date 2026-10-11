# MyMilo Native (Android)

The **real** MyMilo Android app — native Kotlin, offline-first.
(The Capacitor thin client lives in
[mymilo-android](https://github.com/nrupala/mymilo-android) as a variant.)

**Owner:** Nrupal Akolkar · Built with Muse by Meta

## The design (Nrupal's spec)

- **Mostly offline.** App activity lives on the phone: a Room database
  holds sessions, messages, facts, and the skill bundle. Chats, history
  search, and local tools work with zero signal.
- **Skills are dual-homed.** The MyMilo server is the source of truth;
  the app syncs the full skill bundle and matches triggers **on-device** —
  no round-trip to decide which skill applies.
- **Server = escalation.** When a request needs a real model, the app
  goes online to MyMilo on Aetheris, which answers with its local
  models, cloud models, or search tools.
- **Honest origins.** Every reply is labeled: *on this phone*,
  *on-device model*, or *MyMilo server*.

### Routing order per message

1. Local tools (calculator, conversions) — instant, offline
2. Local skill match — decided on-device
3. On-device model — offline answers (milestone 2)
4. MyMilo server — heavy models + search, when online
5. Offline + needs server → queued, sent on reconnect

## Build

CI (GitHub Actions) builds the **release-signed** APK on every push
to `main` and publishes it as a release (one signing identity since
v0.6.0 — in-app updates install cleanly over older builds). Local
build:

```bash
gradle wrapper --gradle-version 8.7   # once
./gradlew assembleDebug
```

## Status

- **Milestone 1:** native shell, Room DB, skill sync + local matching,
  local tools, server escalation, offline queue.
- **v0.3.0:** skins (Midnight/Daylight/Ocean/Ember) + bundled Inter type, startup token re-validation, saved-address migration.
- **Milestone 2 (next):** on-device LLM for offline answers — now
  designed as hardware-fit downloadable packs (Gemma / Qwen / Llama)
  in `SPEC-SOURCES-VAULT.md`, alongside the token vault and the
  multi-source router.

## v0.4.0 — voice loop
- Mic button: dictate a prompt (phone's speech recognition), auto-sends.
- Replies read aloud by the phone's own TTS engine (toggle in top bar, on by default).

## v0.5.0 — assistant role + auto-update
- **Assistant:** pick MyMilo in the drawer ("Make Milo your assistant") or
  Android's default-apps settings; the assistant gesture then opens a Milo
  overlay that listens, answers, and speaks. Honest limits: no always-on
  wake word (Android reserves hotword for the system assistant).
- **Ask-Milo shortcut:** long-press the app icon → voice prompt.
- **Auto-update:** the app checks GitHub releases daily (and on demand in
  the drawer), downloads newer builds, and hands them to the system
  installer — one tap to apply.

## v0.6.0 — release signing
- Updates hub in the drawer; every build signed with the one
  release identity, so updates preserve the app's data and token.

## v0.7.x — the usable surface
- Answers show their **sources** (skill, memory, web, market data).
- Thread rename/delete, drawer search, long-press message actions
  (copy / share / read aloud / ask again), share & save chats.
- v0.7.1 fixed the in-app updater (the download folder gained its
  sharing permission — the first builds could download but not
  install).

## v0.8.x — Guide, catalogue, About
- **Skills catalogue:** all 81 skills on the phone, shelved by
  category, each with a plain-language "what it can do" and an
  example; star favorites; tap one and run it on purpose (the
  server runs the named skill).
- **Help & guide:** quick start, use cases with Try-it buttons,
  FAQ — the same content as the server's `/guide` page.
- **About:** builder credit (Owned by Nrupal Akolkar · Built with
  Muse by Meta), links to both repos, and a Support button
  whose destination is set on the server.
- v0.8.1: assessment fixes — support link captured at connect
  time; system Back returns to chat from the sub-screens.

## v0.9.x — Skill workspaces
- Every skill gets a **workspace**: what it does, its steps or
  answer shape, what to have ready, an example, and a run box —
  a room fitted to the skill, not a pop-up (Skill Pair Program).
- The composer's **+ button** attaches a skill to the next
  message, in the current chat.
- Denser type scale across the app (DESIGN-SYSTEM.md).

## v0.14.1 — Install fix for newer phones
- **Fixed: the app refused to install on phones with
  16 KB memory pages (Galaxy S25 line and newer).** The
  on-device engine library was linked with the older
  4 KB alignment; those phones reject such apps at
  install time. The library is now 16 KB-aligned, and
  CI verifies the alignment inside every APK before it
  can ship — the failure can't recur silently.

## v0.14.0 — Yours first
- The Add-a-source list now leads with a **Yours**
  section: **Pipavia** — your company, the house behind
  your products — and **Conduit**, your own connector
  marketplace (24 tools of live SEC filings &
  fundamentals data).
- Conduit is added as what it is: a connector, not a
  chat brain. Save your Conduit key in the vault like
  any other; it never appears in the brain picker, and
  its card says plainly where data goes.

## v0.13.0 — A model that lives on your phone
- **Models on this phone** (Sources & keys): your phone
  is measured first — chip, memory, free space — and
  every model carries its honest verdict for your phone:
  runs well, works slower, or too big. The best fit is
  starred. No downloading 2 GB to find out.
- Five curated models (Qwen 2.5 and Llama 3.2, 0.5B–3B)
  download once from the publishers' site, with progress,
  cancel, and resume. Then they're yours: delete or swap
  any time.
- Chats set to **This phone** now answer with the model
  you picked — fully offline, airplane-mode capable.
  Powered by llama.cpp running on the phone itself.

## v0.12.0 — Milo acts on the phone
- **Call, text, open apps — from chat.** "Call Natasha",
  "Text Sam saying I'll be late", "Open WhatsApp". Milo
  finds the person in your contacts (or the app on your
  phone), shows you a confirmation card, and acts only
  when you tap.
- Permissions are asked in plain words, at the moment
  they're needed — contacts to find people by name, phone
  to place the call, SMS to send the text. Each ask has a
  no-permission fallback: your dialer opens with the
  number filled in, or your messaging app opens with the
  text ready.
- Names and numbers are looked up on the phone and never
  uploaded. Actions work on every brain — they're phone
  tasks, not model questions.

## v0.11.0 — The provider cards
- **Add a source is now its own draw-down of cards** — one
  per provider, each pre-filled with its address and a
  sensible starting model; you add only your key:
  OpenRouter, OpenCode Zen, Anthropic, OpenAI, Codex,
  Google Gemini, xAI (Grok), Mistral, DeepSeek, Cloudflare
  Workers AI, GitHub Copilot, plus a custom address.
- Every card states, in plain words, where chats sent to
  that provider go — the same line the brain picker shows.

## v0.10.0 — Sources & Vault
- **One vault for every key:** Android Keystore-encrypted,
  write-only — a saved key can never be shown again, only
  replaced or deleted. The Aetheris device key moves into the
  vault automatically.
- **Sources & keys:** Aetheris plus your own OpenRouter,
  OpenCode Zen, or any OpenAI-compatible address — several
  named keys per source, one in use.
- **A brain picker on every chat** (This phone · Aetheris ·
  your sources). Outside sources answer directly from the
  phone with your key, and every option states — at the point
  of choice — where your words go. The "Where your data goes"
  screen lists every destination in plain words.

## Documentation

The server repo carries the full documentation set — user guide,
skills catalogue, operations, code guide, connectors, and the
human/machine/agent model:
[github.com/nrupala/mymilo](https://github.com/nrupala/mymilo/tree/main/docs).
Design specs for the road ahead live in this repo:
`SPEC-SOURCES-VAULT.md` (token vault + multi-source router +
on-device packs) and `ROADMAP-DEVICE-ASSISTANT.md`.
