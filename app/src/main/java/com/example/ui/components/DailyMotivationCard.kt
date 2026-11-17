package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class MotivationItem(
    val quote: String,
    val author: String,
)

val studyMotivationList =
    listOf(
        MotivationItem(
            "🔥 Raat kitni bhi lambi ho, subah hone se nahi rok sakti! Uttho, kitaab uthao, aur phad do exam ko!",
            "Agni Aspiration",
        ),
        MotivationItem(
            "⚡ Aaram haram hai! Jab tak rank nahi aati, rukna mana hai. Padhai aisi karo ki sapne sach ho jayein!",
            "Topper's Creed",
        ),
        MotivationItem(
            "🚀 Duniya so rahi hai, tum apna future bana rahe ho. Ye raatein tumhari hain, inhein mat gawao!",
            "Midnight Warrior",
        ),
        MotivationItem(
            "💯 Pain of discipline is far less than the pain of regret. Give your 100% today and conquer your goals!",
            "Focus Master",
        ),
        MotivationItem(
            "⚡ Padhai me itni aag laga do ki distraction khud door bhaag jaye. Tu hi rank layega!",
            "StudyShield Legend",
        ),
    )

@Composable
fun DailyMotivationCard(
    modifier: Modifier = Modifier,
) {
  var currentIndex by
      remember {
        mutableIntStateOf((System.currentTimeMillis() / 86400000L).toInt() % studyMotivationList.size)
      }
  var isLiked by remember { mutableStateOf(false) }
  val currentItem = studyMotivationList[currentIndex]

  Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  1.5.dp,
                  Brush.linearGradient(
                      listOf(
                          MaterialTheme.colorScheme.primary,
                          MaterialTheme.colorScheme.tertiary,
                      )
                  ),
                  RoundedCornerShape(24.dp),
              ),
  ) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(20.dp)
    ) {
      Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
              }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "🔥 Daily Blood-Boiling Motivation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
          }

          Row {
            IconButton(
                onClick = { currentIndex = (currentIndex + 1) % studyMotivationList.size }
            ) {
              Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Next Quote",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(onClick = { isLiked = !isLiked }) {
              Icon(
                  imageVector =
                      if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                  contentDescription = "Like Quote",
                  tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = currentItem.quote,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "— ${currentItem.author}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
