package com.trid.test.kmpsample

import androidx.compose.runtime.Composable
import com.trid.test.kmpsample.navigation.AppNavHost
import com.trid.test.kmpsample.ui.theme.AppTheme

@Composable
fun App() {
    AppTheme {
        AppNavHost()
    }
}
