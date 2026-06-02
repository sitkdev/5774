# Navigation Architecture (Slack Circuit)

KMP app, shared module `composeApp`, package `com.trid.test.kmpsample`.
Navigation library: **Slack Circuit 0.33.1** (`libs.circuit` = `circuit-foundation`,
only this declared; it transitively brings runtime/backstack). No `circuitx-*`.

## Files (all under `composeApp/src/commonMain/.../navigation/` unless noted)
- `Screens.kt` — Circuit `Screen` keys: `LoadingScreen`, `NoConnectionScreen`, `HomeScreen`
  (all `data object`, annotated `@CommonParcelize`).
- `CommonParcelize.kt` — `@OptionalExpectation expect annotation class CommonParcelize`.
  androidMain actual: `CommonParcelize.android.kt` = `actual typealias CommonParcelize = kotlinx.parcelize.Parcelize`.
  iOS needs no actual (OptionalExpectation drops it).
- `GuardedNavigator.kt` — spam-click guard wrapping Circuit `Navigator`. Also `LocalGuardedNavigator`
  CompositionLocal + `rememberGuardedNavigator`.
- `Presenters.kt` — presenters + states/events for the 3 screens. States carry `eventSink`.
- `AppCircuit.kt` — `buildAppCircuit()` registers presenters/UIs via `addPresenter<S,State>`/`addUi<S,State>`.
  Also private UI wrappers (`LoadingUi`/`NoConnectionUi`/`HomeUi`) that wrap the empty `ui.*Screen` composables.
- `AppNavHost.kt` — `AppNavHost()`: builds Circuit (remembered), roots backstack at `LoadingScreen`,
  `rememberCircuitNavigator` with no-op `onRootPop`, wraps in `GuardedNavigator`, hosts `NavigableCircuitContent`.
- `App.kt` — calls `AppNavHost()` inside `AppTheme { }`. Signature unchanged (`fun App()`).

## Navigation flow
LoadingScreen (root) --Ready--> resetRoot(HomeScreen) ; --NoConnection--> goTo(NoConnectionScreen)
NoConnectionScreen --Retry--> resetRoot(LoadingScreen). resetRoot wipes backstack so back can't return to Loading.

## Spam-click prevention (GuardedNavigator)
- Debounce window `DEFAULT_DEBOUNCE_MILLIS = 400L` using `kotlin.time.TimeSource.Monotonic` (no kotlinx-datetime).
- `goTo` also drops a push if `peek() == screen` (duplicate-top guard).
- All mutating ops wrapped in runCatching + Kermit logging.
- Back-press lock on Loading & NoConnection via `BackHandler(enabled=true){}` (empty) — `androidx.compose.ui.backhandler.BackHandler`, which is `@ExperimentalComposeUiApi` + `@Deprecated` (use `NavigationEventHandler` later). Needs `@OptIn(ExperimentalComposeUiApi::class)`.
- Anti spam-close: `onRootPop` no-op so back at root never exits the app.

## Circuit 0.33.1 API gotchas (verified against sources)
- `Navigator` members: goTo, forward, backward, pop, peek, peekBackStack, peekNavStack, resetRoot.
  `resetRoot(newRoot, options: StateOptions = StateOptions.Default)` — the `saveState/restoreState` boolean form is an EXTENSION fn.
- `NavStackList` is in `com.slack.circuit.runtime.navigation`.
- `rememberSaveableBackStack(root: Screen)` from `com.slack.circuit.backstack`.
- `NavigableCircuitContent(navigator, backStack)` + `CircuitCompositionLocals(circuit){}` from `com.slack.circuit.foundation`.
- `addPresenter<S,State>{ screen, navigator, context -> }` and `addUi<S,State>{ state, modifier -> }`.
- `Screen` is an `expect interface`: on Android it extends `Parcelable` (hence @CommonParcelize); iOS is bare.

## Parcelize wiring (KMP)
- `kotlin-parcelize` applied in `composeApp/build.gradle.kts` as `id("org.jetbrains.kotlin.plugin.parcelize")`
  WITHOUT version (plugin already on classpath via Kotlin — versioned alias fails with "already on the classpath").
- androidTarget compilerOptions adds: `-P` then
  `plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=com.trid.test.kmpsample.navigation.CommonParcelize`
  so parcelize generates Parcelable impls for `@CommonParcelize` `data object`s (the typealias FQN alone is NOT auto-detected).

## DI
- Koin is NOT initialized (no startKoin anywhere). Circuit instance is provided via `remember` in `AppNavHost`, NOT Koin (lower risk; avoids touching both platform entry points). To migrate later: `startKoin` in commonMain `initKoin()`, call from MainActivity + MainViewController, `single { buildAppCircuit() }`, inject via `koinInject()`.

## Known pre-existing issue (NOT from navigation work)
- iOS target fails to compile because `App.kt` uses `@Preview` from `androidx.compose.ui.tooling.preview`
  which has no iOS artifact on the classpath (only `compose.uiTooling` as Android debugImplementation).
  Pre-existing in HEAD. Fix (if desired): add `org.jetbrains.compose.components:components-ui-tooling-preview` to commonMain.
- Android (`compileDebugKotlinAndroid`) compiles clean.
