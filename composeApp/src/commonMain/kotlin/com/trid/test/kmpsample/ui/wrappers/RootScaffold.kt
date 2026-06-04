package com.trid.test.kmpsample.ui.wrappers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.trid.test.kmpsample.getPlatform
import com.trid.test.kmpsample.ui.theme.AppGradients.AppBg


internal val isIOS get() = getPlatform().name.startsWith("iOS")

@Composable
fun RootScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    background: Brush = AppBg,
    content: @Composable (PaddingValues) -> Unit,
) {
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    softwareKeyboard?.hide()
                    focusManager.clearFocus()
                }
            )

    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        )
        Scaffold(
            modifier = modifier.fillMaxSize().imePadding(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = topBar,
            bottomBar = bottomBar,
            contentWindowInsets = if (isIOS) {
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            } else {
                WindowInsets.safeDrawing
            },
            content = content,
        )
    }
}
