# Changelog

All notable changes to MyMilo Native are recorded here, from
this file's creation onward. It starts at the current release,
**v0.14.5** (`versionName` in `app/build.gradle.kts`).

The earlier release history is not re-written here: it lives in
the README's Status section (v0.3.0 through v0.14.x) and in the
GitHub Releases, which CI publishes as `build-N` — `versionCode`
is `500 + N`, so every app build maps back to its release.

## [Unreleased]

## [0.14.5]

- The launch crash is fixed at the root: the three local-model
  state flows the startup refresh writes are declared above the
  ViewModel init block, so they are initialized before any
  startup code can touch them (see the v0.14.5 note in
  README.md).
