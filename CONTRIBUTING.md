# Contributing to MyMilo Native

MyMilo Native is the real MyMilo Android app: native Kotlin,
offline-first. This document describes how the project is built,
verified, and released, so a change you make locally behaves the
same way it will in CI.

## Prerequisites

- **JDK 17** (the build compiles to Java 17 targets).
- **Gradle 8.7** — the Gradle wrapper is *not* committed
  (`gradle/wrapper/` is git-ignored); generate it once, see below.
- **Android SDK** with compileSdk / targetSdk 34, minSdk 26.
- For the on-device engine: **Android NDK 27.2.12479018** and
  **CMake 3.22.1** (the versions CI installs).

## One-time setup

```bash
# The native engine builds llama.cpp via JNI. It is cloned at a
# pinned tag, never vendored (git-ignored) — fetch it once:
git clone --depth 1 --branch b11541 \
  https://github.com/ggml-org/llama.cpp.git \
  app/src/main/cpp/llama.cpp

# Generate the Gradle wrapper (once):
gradle wrapper --gradle-version 8.7
```

## Build

```bash
./gradlew assembleDebug     # day-to-day build
./gradlew assembleRelease   # release build (unsigned locally)
```

Release signing exists only in CI: the keystore is decoded from
repository secrets into a temp file for the job and passed via
the `MYMILO_KEYSTORE` / `MYMILO_KEYSTORE_PASSWORD` /
`MYMILO_KEY_ALIAS` / `MYMILO_KEY_PASSWORD` environment
variables (see `app/build.gradle.kts`). Without them a local
release build is unsigned and CI fails at packaging — by
design, so an unsigned build can never be published by
accident.

## Testing and ship gates

There is **no automated test suite in this repo yet** — no unit
or instrumented tests under `app/src`. Be honest about that in
your PR: say how you verified the change (build plus on-device
behaviour).

What CI enforces on every push to `main`
(`.github/workflows/android.yml`):

1. The release APK compiles and packages
   (`./gradlew assembleRelease`).
2. **16 KB alignment gate:** every native library inside the
   APK is checked for 16 KB ELF alignment. Phones with 16 KB
   memory pages refuse misaligned apps at install time
   (v0.14.1); this check keeps that failure from shipping
   again. If you touch native code or `CMakeLists.txt`, this
   is the gate that will catch you.
3. The APK and the R8 mapping file are uploaded as artifacts,
   and the APK is published as the `build-N` GitHub release.

## Versioning and releases

- `versionName` lives in `app/build.gradle.kts` (currently
  **0.14.5**). Bump it there when a change is user-visible.
- `versionCode` is `500 + MYMILO_BUILD_NUMBER`, where CI passes
  its run number — so app build `500 + N` is GitHub release
  `build-N`. Local builds default to build number 0.
- Releases are published by CI from `main` only; there are no
  hand-cut releases. Record user-visible changes in
  `CHANGELOG.md` under `[Unreleased]`.

## Pull requests

1. Branch off `main`; keep the change to one topic.
2. Build locally (`assembleDebug`, plus `assembleRelease` if
   you touched build config, native code, or ProGuard rules).
3. If you changed behaviour, say how you verified it on a
   device — there is no test suite to hide behind.
4. Open the PR against `main` and let the owner merge. Never
   push directly to `main`: every push to `main` publishes a
   release.

Bug reports and feature requests use the issue templates in
`.github/ISSUE_TEMPLATE/`.
