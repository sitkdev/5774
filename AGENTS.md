# AGENTS.md

## First Read
- Trust executable config over prose. `CLAUDE.md` is still useful for build, obfuscation, and publish machinery, but it predates the current collection app; `NAVIGATION.md` is stale about Koin/HomeScreen, so inspect `composeApp/src/commonMain/kotlin/com/trid/test/kmpsample/navigation/` for the live graph.
- This is a single-module Kotlin Multiplatform app: `:composeApp`, shared package `com.trid.test.kmpsample`, Android + iOS only (`iosArm64`, `iosSimulatorArm64`).

## Verify
- Android Kotlin: `./gradlew :composeApp:compileDebugKotlinAndroid`.
- iOS shared Kotlin: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.
- Android APK / manifest / resources end-to-end: `./gradlew :composeApp:assembleDebug`.
- Release AAB: `./gradlew :composeApp:bundleRelease`; this runs obfuscation and `removeProguardMap` unless `CI_BUILD=true`.
- Full iOS app builds run through Xcode/Codemagic, not directly through Gradle; the Gradle iOS command above is a focused shared-code compile check.
- No test source sets, ktlint, detekt, or pre-commit config are present; do not assume `./gradlew test` is meaningful here.
- After adding files under `composeResources`, run `./gradlew :composeApp:generateResourceAccessorsForCommonMain --no-configuration-cache` if `Res.drawable.*` or `Res.font.*` accessors look stale.

## Build And Identity
- `composeApp/config.gradle.kts` is the authoritative source for `app_name`, `bundle`, SDK levels, Java target, and version; it overrides contradictory values in `libs.versions.toml` such as minSdk.
- `composeApp/setup.txt` is generated once and persisted; it controls resource, string, and code obfuscation. Do not delete it unless intentionally rerolling the obfuscation profile.
- Obfuscation plugins are resolved through JitPack in `settings.gradle.kts` via `resolutionStrategy.eachPlugin`.
- `rebundle` is finalized by `preBuild`; if `composeApp/src/main/java` exists, it rewrites that package tree and the manifest to match `extra["bundle"]`.
- `gradle.properties` has `org.gradle.daemon=false` and `org.gradle.configuration-cache=warn`; builds are slower, and configuration cache can leave Compose resource accessors stale.

## App Architecture
- Entry points: Android `MainApplication` starts Koin with `androidContext(...)`, Android `MainActivity` calls `App()`, and iOS `MainViewController()` calls `initKoin()` before `App()`.
- `App()` wraps `AppNavHost()` in `AppTheme`; `RootScaffold` provides the full-bleed gradient and owns safe-drawing insets.
- Use Slack Circuit, not Navigation-Compose. Add a destination by updating `Screens.kt`, `Presenters.kt`, and `AppCircuit.kt` together.
- Current graph is Dashboard-hub: `LoadingScreen` root auto-resets to `DashboardScreen` after 2.5s; Dashboard opens Collections, Showcase, Add Artifact, and Details via item taps.
- `GuardedNavigator` is the navigator passed into Circuit and exposed through `LocalGuardedNavigator`; it debounces navigation for 400ms and prevents duplicate top pushes.
- Do not re-add `safeDrawing` padding to `AppTheme` or set an opaque `Scaffold` container in `RootScaffold`; that brings back the status-bar/gradient strip bug.
- If touching no-network flow, verify `NoConnectionScreen(onReconnect)` is wired to `NoConnectionUiEvent.Retry` in `AppCircuit.kt`, not left as a no-op.

## Data, Storage, DI
- `CollectionsRepository` is the single source of truth for collections/artifacts; presenters observe its `StateFlow`s and mutate through repository methods.
- All app data persistence must use `StorageHelper` with `encrypted = true`; Android uses `EncryptedSharedPreferences`, iOS uses `KeychainSettings`.
- `StorageHelper` is a Koin singleton class, not a global object. Inject it with `koinInject<StorageHelper>()`, `getKoin().get<StorageHelper>()`, or `KoinPlatform.getKoin()` from non-composable presenter code.
- Do not reintroduce a storage `ContentProvider` or context holder; Android context comes from Koin `androidContext()`.
- User photos are currently stored as base64 `ArtifactImage.Bytes` in encrypted storage; acceptable for demo/small images, not production-scale media storage.

## Media And Legal
- Use `rememberGalleryPicker` and `rememberCameraPicker`; both return `ByteArray?` through `MediaPicker.launch()`.
- Android gallery uses Photo Picker (`PickVisualMediaRequest`) and no storage permission; Android camera uses `TakePicture` through `${applicationId}.fileprovider` and `composeApp/src/androidMain/res/xml/file_paths.xml`.
- iOS gallery authorization intentionally uses `PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)`; camera uses AVFoundation permission plus `UIImagePickerController`.
- Keep `iosApp/iosApp/Info.plist` camera/photo usage descriptions in sync with media changes.
- Legal links are platform-asymmetric: Android has Collectra Privacy only; iOS has Nilexis Privacy plus Terms. Reuse `LegalLinks()` and `LocalUriHandler`; no WebView is wired.

## Compose Resources
- Generated Compose resource package is `mic_kmp_sample.composeapp.generated.resources`, derived from `rootProject.name`, not `com.trid.test.kmpsample`.
- Common vector XML must use literal colors like `#FFFFFFFF`; Compose Multiplatform `vectorResource` crashes on `@android:color/...` references in `commonMain` resources.
- For model-stored drawable names, resolve through `DrawableKeys.resolve()` or `ArtifactThumb()` instead of scattering string-to-`Res.drawable.*` maps.
- Custom drawables are the 12 gold `ic_*` PNGs plus `outline_wifi.xml`; `compose-multiplatform.xml` is only the template placeholder.

## Publishing Pipeline
- iOS publishing starts from repo root with `python -m scripts.publish` (`-y` for unattended); setup dependencies with `pip install -r scripts/requirements.txt`.
- The publish script infers the Jira ticket from leading digits in the project directory name; this folder name (`5773`) is load-bearing.
- Secrets live in sibling `../Utils/local.properties`, not in this repo. The per-project cache `scripts/.publish-cache.json` is gitignored.
- `codemagic.yaml` env group `"9999"` is a placeholder rewritten to the ticket number; workflows are `ios_kmp_release` and `upload_ios_metadata`.
