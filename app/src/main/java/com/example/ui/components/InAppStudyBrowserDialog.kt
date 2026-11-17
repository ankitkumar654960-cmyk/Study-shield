package com.example.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

@Composable
fun InAppStudyBrowserDialog(
    url: String,
    title: String,
    onDismiss: () -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = title.ifBlank { "In-App Study Browser" },
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  maxLines = 1,
              )
              Text(
                  text = url,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                  maxLines = 1,
              )
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Filled.Close, contentDescription = "Close Browser", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          AndroidView(
              factory = { context ->
                WebView(context).apply {
                  layoutParams = ViewGroup.LayoutParams(
                      ViewGroup.LayoutParams.MATCH_PARENT,
                      ViewGroup.LayoutParams.MATCH_PARENT
                  )
                  settings.javaScriptEnabled = true
                  settings.domStorageEnabled = true
                  settings.loadWithOverviewMode = true
                  settings.useWideViewPort = true
                  webViewClient = WebViewClient()
                  val finalUrl = if (url.contains("youtube.com/watch?v=")) {
                    url.replace("watch?v=", "embed/")
                  } else if (url.contains("youtu.be/")) {
                    url.replace("youtu.be/", "www.youtube.com/embed/")
                  } else {
                    url
                  }
                  loadUrl(finalUrl)
                }
              },
              modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}
