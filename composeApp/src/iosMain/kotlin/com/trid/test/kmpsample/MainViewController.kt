package com.trid.test.kmpsample

import androidx.compose.ui.window.ComposeUIViewController
import com.trid.test.kmpsample.di.initKoin

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}
