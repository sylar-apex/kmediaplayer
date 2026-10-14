package org.app.kmediaplayer.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.app.kmediaplayer.KMediaPlayer

/** Asks for a URL and opens it in the video player. */
@Composable
fun NetworkStreamDialog(onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    PlayerDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network Stream") },
        text = {
            Column {
                Text("Please enter a network URL:")
                Text("Example: http://www.example.com/video.mkv", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
            }
        },
        confirmButton = {
            TextButton({
                val u = url.trim()
                if (u.contains("://")) { KMediaPlayer.playUrl(u); onDismiss() }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onDismiss) { Text("CANCEL") } },
    )
}
