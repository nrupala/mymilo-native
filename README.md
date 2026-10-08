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

CI (GitHub Actions) builds the debug APK on every push to `main` and
publishes it as a release. Local build:

```bash
gradle wrapper --gradle-version 8.7   # once
./gradlew assembleDebug
```

## Status

- **Milestone 1:** native shell, Room DB, skill sync + local matching,
  local tools, server escalation, offline queue.
- **v0.3.0:** skins (Midnight/Daylight/Ocean/Ember) + bundled Inter type, startup token re-validation, saved-address migration.
- **Milestone 2 (next):** on-device LLM for offline answers.

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
