package com.balaji.provision.ui.profile

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balaji.provision.R

enum class LegalPage(val title: String, @RawRes val rawText: Int) {
    PRIVACY("Privacy policy", R.raw.privacy_policy),
    TERMS("Terms of use", R.raw.terms_of_use),
    ACCOUNT_DELETION("Account deletion", R.raw.account_deletion)
}

@Composable
fun LegalScreen(page: LegalPage, onBack: () -> Unit) {
    val body = rememberRawText(page.rawText)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                SelectionContainer {
                    Text(
                        body,
                        modifier = Modifier.padding(top = 14.dp, bottom = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberRawText(@RawRes resourceId: Int): String {
    val context = LocalContext.current
    return remember(resourceId) {
        context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    }
}
