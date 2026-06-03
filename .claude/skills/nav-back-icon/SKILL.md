---
name: nav-back-icon
description: >-
  Build a top-of-screen navigation icon (a "back"/"up" button) for this
  KMP + Compose Multiplatform + Circuit app. The icon sits at the top of a
  screen and, when tapped, drives the navigation component to return to the
  previous screen — or, when there's nothing to pop, resets to the Dashboard
  hub (the menu screen). Use this skill whenever the user asks for a back
  button, back arrow, up navigation, a top-bar back icon, a "return to
  previous screen" or "return to menu" control, or wants to add a navigation
  icon to a screen's header — even if they don't say the word "back". Wires
  through the project's GuardedNavigator (debounce is already handled) and the
  Circuit eventSink(Back) pattern, draws a KMP-safe chevron via Canvas (no new
  drawable needed), and includes a press animation and contentDescription.
---

# Navigation back icon

A reusable top-of-screen icon that returns the user to the previous screen, and
falls back to the Dashboard hub (the app's menu screen) when there's nothing left
to pop. It is built for this app's exact navigation stack — Slack **Circuit** with
the project's **`GuardedNavigator`** — so it slots into existing screens without
new plumbing.

## What you're building and why it fits this project

Three project facts shape the design — honor them and the icon "just works":

1. **`GuardedNavigator` already debounces.** Every `goTo`/`pop`/`resetRoot` is
   debounced (400 ms) and duplicate-guarded (`navigation/GuardedNavigator.kt`).
   So the icon does **not** need its own click throttle — spam taps are safe.
   Don't reinvent that guard; it makes the component simpler.

2. **Screens go back via `eventSink(...Back)`.** Existing screens
   (`CollectionsUiEvent.Back`, `ShowcaseUiEvent.Back`, `ArtifactDetailsUiEvent.Back`)
   route a `Back` event through their presenter to `navigator.pop()`. This is the
   idiomatic path: UI fires an event, the **presenter** owns the navigation
   decision. Prefer it.

3. **There's no back-arrow drawable.** `composeResources/drawable/` only has the
   collectible icons + `outline_wifi.xml`. material-icons-extended is **not** a
   dependency, and `@android:color` refs crash CMP vector rendering. So draw the
   chevron with **Canvas** — it's KMP-safe, themeable via `colorScheme`, and adds
   no asset/obfuscation surface.

## The decision: pop vs. return to menu

"Return to previous screen / menu screen" is one rule:

- If the backstack has **more than one** entry → `pop()` (go to the previous screen).
- If we're already at the **root** of the visible stack → `resetRoot(DashboardScreen)`
  (return to the hub/menu). This mirrors `LoadingPresenter`'s use of `resetRoot` and
  keeps the icon meaningful even on a screen that was reached via `resetRoot`.

`navigator.peekBackStack().size` tells you which case you're in. Put this logic in
the **presenter** when using the eventSink path, or inside the component when using
the `LocalGuardedNavigator` fallback.

## The component

Drop this into `ui/components/CollectorUi.kt` (the shared component file) or its own
file in `ui/components/`. It's intentionally presentation-only: it takes an `onClick`
so the *caller* owns where "back" goes.

```kotlin
package com.trid.test.kmpsample.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Top-of-screen navigation icon. Renders a left-pointing chevron inside a
 * circular 44.dp touch target, scales down slightly while pressed, and calls
 * [onClick] on tap. The caller decides what "back" means (pop vs. return to
 * the Dashboard hub) — see the screen-wiring examples in the skill.
 *
 * No throttle here on purpose: GuardedNavigator debounces every transition, so
 * a fast double-tap can't double-navigate.
 */
@Composable
fun NavBackIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f)

    Canvas(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                onClick(label = contentDescription, action = null)
            },
    ) {
        // Chevron pointing left, centered, sized to ~40% of the target.
        val w = size.width
        val h = size.height
        val armX = w * 0.40f          // right edge of the chevron arms
        val tipX = w * 0.34f          // left tip
        val midY = h * 0.5f
        val spread = h * 0.16f        // vertical reach of each arm
        val stroke = (w * 0.07f)

        val path = Path().apply {
            moveTo(armX, midY - spread)
            lineTo(tipX, midY)
            lineTo(armX, midY + spread)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
```

Tune `tint`, the background alpha, or the 44.dp target to match the screen, but keep
the touch target ≥ 44.dp for accessibility and leave the press animation in — it's the
tactile feedback users expect from a header control.

## Wiring it into a screen

### Preferred: through the presenter's `Back` event

This matches how `CollectionsScreen`, `ShowcaseScreen`, etc. already work. The presenter
owns the pop-vs-hub decision, keeping it testable and out of the UI.

**1. Add/confirm a `Back` event** on the screen's `UiEvent` (most screens already have one):

```kotlin
sealed interface ArtifactDetailsUiEvent : CircuitUiEvent {
    // ...existing events...
    data object Back : ArtifactDetailsUiEvent
}
```

**2. Handle it in the presenter** with the pop-vs-hub rule:

```kotlin
ArtifactDetailsUiEvent.Back -> {
    if (navigator.peekBackStack().size > 1) navigator.pop()
    else navigator.resetRoot(DashboardScreen)
}
```

**3. Place the icon at the top of the screen UI:**

```kotlin
Column(Modifier.fillMaxSize()) {
    NavBackIcon(
        onClick = { state.eventSink(ArtifactDetailsUiEvent.Back) },
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
    // ...rest of the screen...
}
```

The screens render inside `RootScaffold`, which already applies `safeDrawing` insets
(see `AppNavHost.kt`), so the icon won't collide with the status bar — a small
`padding` is enough; don't add manual inset handling.

### Fallback: `LocalGuardedNavigator` (no presenter change)

When you want a self-contained header control and don't want to touch the presenter, read
the navigator straight from the composition local and put the decision in the component’s
caller:

```kotlin
val navigator = LocalGuardedNavigator.current
NavBackIcon(
    onClick = {
        if (navigator.peekBackStack().size > 1) navigator.pop()
        else navigator.resetRoot(DashboardScreen)
    },
    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
)
```

Prefer the eventSink path for screens that already have a presenter; use this for ad-hoc
or deeply nested UI where threading an event through would be noise.

### As a Scaffold top bar (optional)

`RootScaffold` accepts a `topBar` slot. If a screen wants a titled header with the icon:

```kotlin
RootScaffold(
    topBar = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavBackIcon(onClick = { state.eventSink(...Back) })
            SectionHeader(title = "Details", modifier = Modifier.padding(start = 8.dp))
        }
    },
) { padding -> /* content */ }
```

`SectionHeader` already exists in `CollectorUi.kt` and matches the app's typography.

## Checklist before you finish

- Back goes to the previous screen, and to **Dashboard** when the stack is at its root —
  verify with `peekBackStack().size`, not by assuming.
- The icon has a `contentDescription` (default "Back") so screen readers announce it.
- No manual click throttle was added — `GuardedNavigator` owns debounce.
- No new drawable was introduced; the chevron is Canvas-drawn and tinted via `colorScheme`.
- It compiles for **both** Android and iOS (commonMain only; nothing platform-specific here).
