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

## Documentation

The server repo carries the full documentation set — user guide,
skills catalogue, operations, code guide, connectors, and the
human/machine/agent model:
[github.com/nrupala/mymilo](https://github.com/nrupala/mymilo/tree/main/docs).
Design specs for the road ahead live in this repo:
`SPEC-SOURCES-VAULT.md` (token vault + multi-source router +
on-device packs) and `ROADMAP-DEVICE-ASSISTANT.md`.
