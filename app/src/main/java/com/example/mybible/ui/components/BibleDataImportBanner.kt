package com.example.mybible.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun BibleDataImportBanner(
    modifier: Modifier = Modifier,
    label: String,
    detail: String,
    done: Int,
    total: Int
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            if (total > 0) {
                val fraction = done.toFloat() / total.toFloat()
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BibleDataImportErrorBanner(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
