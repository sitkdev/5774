# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Kotlin Multiplatform (Android + iOS) Compose Multiplatform **app template**. The
shared code in `composeApp/` is intentionally near-empty (`App.kt` is a bare
`MaterialTheme {}`) — the repo's real value is the **build/obfuscation machinery** and
the **Python iOS App Store publishing pipeline** (`scripts/publish/`) that turn this
template into a per-client app and ship it. Each client app starts from this template,
gets a new bundle ID / name, and is published via the pipeline.

Shared module package: `com.trid.test.kmpsample`. iOS targets: `iosArm64`,
`iosSimulatorArm64` (static `ComposeApp` framework). Android min SDK 28 (note:
`config.gradle.kts` sets 28, `libs.versions.toml` says 24 — `config.gradle.kts` wins),
JVM target 11.

## Build & run

```shell
./gradlew :composeApp:assembleDebug      # Android debug APK
./gradlew :composeApp:bundleRelease      # Android release AAB (runs obfuscation + removeProguardMap)
```

iOS builds run through Xcode (`iosApp/iosApp.xcodeproj`) or Codemagic — not Gradle
directly. There is no test source set in this template; don't assume `./gradlew test`
does anything meaningful.

## Key dependencies (commonMain)

Koin (DI), **Circuit** (Slack's navigation/UDF framework — use this for screens, not
raw Navigation-Compose), Ktor (client; OkHttp on Android, Darwin on iOS),
multiplatform-settings (persistence), Coil 3 (images), kotlinx-serialization, Kermit
(logging). Versions are centralized in `gradle/libs.versions.toml`.

## Per-app configuration (`composeApp/config.gradle.kts`)

`config.gradle.kts` is the single place that defines app identity via `rootProject.extra`:
`app_name`, `bundle`, `versionCode`, `versionName`, SDK levels, Java version. The
template ships placeholders (`"CHANGE NAME"`, `com.test.bundle`). The publish pipeline
edits these per client. `composeApp/build.gradle.kts` reads them all through
`rootProject.extra[...]`.

## The obfuscation system — read before touching the build

`composeApp/build.gradle.kts` randomizes obfuscation on first build by generating
`composeApp/setup.txt` with three values (each picked once via `Random` and then
**persisted** so rebuilds are stable):

- **Resource obfuscation** (`0`/`1`): `sq.res-guard` plugin renames DRAWABLE resources.
- **Strings obfuscation** (`0`/`1`): `stringfog` XOR-encrypts string literals in the app package.
- **Code obfuscation** (`0`/`1`/`2`): selects the ProGuard dictionary — `0` none,
  `1` `valacuz.pro` (valacuz dict generator), `2` `pumpkin.pro` (CleverPumpkin dict).

Each maps to a different ProGuard file set and plugin config. Deleting `setup.txt`
re-rolls the obfuscation profile. These plugins come from JitPack and are wired in
`settings.gradle.kts` via `resolutionStrategy.eachPlugin`.

Custom Gradle tasks in `composeApp/build.gradle.kts`:
- `rebundle` (runs as `finalizedBy("preBuild")`): rewrites the `src/main/java` package
  directory tree and manifest to match `extra["bundle"]`.
- `removeProguardMap` (after `bundleRelease`, only when `CI_BUILD != "true"`): strips the
  `proguard.map` out of the AAB and saves it alongside as `proguard.map`.
- Crashlytics mapping upload is disabled in `afterEvaluate`.

## iOS App Store publishing pipeline (`scripts/publish/`)

A Python tool (entry point `python -m scripts.publish` from repo root; `-y`/`--yes` for
unattended) that automates the entire iOS release. Full Ukrainian-language docs in
`scripts/publish/README.md`. The orchestrator is `flow.py::run()`, which runs numbered
steps 0–13 plus a manual checklist:

1. Reads app metadata from a **Jira** ticket (ticket number inferred from the **project
   directory name's leading digits**, e.g. `1234-foo/` → ticket `1234`) and a linked
   IF-story.
2. Edits project files: `iosApp/Configuration/Config.xcconfig`, `project.pbxproj`
   (bundle ID + team), `codemagic.yaml` (env group `"9999"` → ticket number),
   `Fastfile` review info, `gradle.properties`, and `config.gradle.kts` bundle/team
   (`project_edits.py`).
3. Pushes a `ios_release` branch to a **production GitHub account** via a temporary
   remote, triggers **Codemagic** workflows (`ios_kmp_release` builds/signs/uploads to
   TestFlight; `upload_ios_metadata` pushes fastlane metadata).
4. Generates App Store copy (subtitle/description/keywords/categories) via the
   **Claude Agent SDK** (`metadata_gen.py`, model `claude-sonnet-4-6`), writes English
   into a shared **Google Sheet** (auto-translates to ~34 locales), creates a
   **Telegraph** support page, then validates/shrinks every locale against App Store
   char limits (`metadata_check.py`, limits in `constants.py`).

Config & secrets come from `../Utils/local.properties` (a sibling `Utils/` dir, NOT in
this repo) via `config.py`; per-project secrets cache in `scripts/.publish-cache.json`
(gitignored). Cross-module constants (sheet ID, JQL, workflow IDs, char limits) live in
`scripts/publish/constants.py` — change them there, not inline.

Codemagic env vars are keyed to a **group named after the ticket number**; `codemagic.yaml`
ships with the placeholder group `"9999"` that step 2 rewrites.

## Gotchas

- `gradle.properties` has `org.gradle.daemon=false` and `configuration-cache=warn`;
  builds are slower but the publish pipeline rewrites this file, so don't hand-tune it.
- The project directory name is load-bearing for the publish pipeline (ticket inference).
- `local.properties` is committed-empty/placeholder here; real secrets are in the sibling
  `Utils/` folder, not this repo.
