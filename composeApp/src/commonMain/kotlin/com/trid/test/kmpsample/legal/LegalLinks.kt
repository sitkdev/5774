package com.trid.test.kmpsample.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.trid.test.kmpsample.ui.theme.AppAccent

data class LegalLink(val title: String, val url: String)

expect fun legalLinks(): List<LegalLink>

@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val links = remember { legalLinks() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            val url = link.url
            Text(
                text = link.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AppAccent.Gold,
                    textDecoration = TextDecoration.Underline,
                ),
                modifier = Modifier.clickable { uriHandler.openUri(url) },
            )
        }
    }
}
