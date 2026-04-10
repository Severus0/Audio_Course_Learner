package com.languageapp.audiocourselearner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnnotationDialog(
    audioFileName: String,
    initialTimestampMs: Long,
    initialPhrase: String,
    isEditMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (timestampMs: Long, phrase: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    // Format timestamp
    val totalSeconds = initialTimestampMs / 1000
    val initialTimeStr = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    var dialogTimestampText by remember { mutableStateOf(initialTimeStr) }
    var dialogPhraseText by remember { mutableStateOf(initialPhrase) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!isEditMode) "Add Annotation" else "Edit Annotation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Audio: $audioFileName", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                OutlinedTextField(
                    value = dialogTimestampText,
                    onValueChange = { dialogTimestampText = it },
                    label = { Text("Timestamp (MM:SS)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dialogPhraseText,
                    onValueChange = { dialogPhraseText = it },
                    label = { Text("Phrase / Sentence") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val parts = dialogTimestampText.split(":")
                    if (parts.size == 2) {
                        val mm = parts[0].toLong()
                        val ss = parts[1].toLong()
                        val newMs = (mm * 60 + ss) * 1000
                        onSave(newMs, dialogPhraseText)
                    }
                } catch (e: Exception) {}
            }) { Text(if (!isEditMode) "Add" else "Save") }
        },
        dismissButton = {
            Row {
                if (isEditMode && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, "Delete")
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}