package com.itantra.offlinevoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itantra.offlinevoice.ui.theme.Emergency
import com.itantra.offlinevoice.ui.theme.Success

enum class FeedbackKind { LOADING, SUCCESS, ERROR }

/** Reusable labelled feedback surface; state is never communicated by colour alone. */
@Composable
fun FeedbackState(kind: FeedbackKind, title: String, detail: String) {
    val color = when (kind) { FeedbackKind.LOADING -> MaterialTheme.colorScheme.primary; FeedbackKind.SUCCESS -> Success; FeedbackKind.ERROR -> Emergency }
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        when (kind) {
            FeedbackKind.LOADING -> CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp, color = color)
            FeedbackKind.SUCCESS -> Icon(Icons.Default.CheckCircle, null, tint = color)
            FeedbackKind.ERROR -> Icon(Icons.Default.ErrorOutline, null, tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.titleMedium, color = color); Text(detail, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun VoiceLinkDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceLinkBottomSheet(show: Boolean, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (show) ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}
