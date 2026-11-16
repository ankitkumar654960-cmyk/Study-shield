package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberXp
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.SapphireFocus
import com.example.ui.theme.ShieldRose
import com.example.ui.viewmodel.DayStudyPoint
import com.example.ui.viewmodel.SubjectDistribution

@Composable
fun WeeklyBarChart(
    points: List<DayStudyPoint>,
    dailyGoalMinutes: Long = 60L,
    modifier: Modifier = Modifier,
) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  val maxMinutes = (points.maxOfOrNull { it.minutes } ?: 0L).coerceAtLeast(dailyGoalMinutes).coerceAtLeast(30L)

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(
        modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 4.dp)
    ) {
      val barCount = points.size.coerceAtLeast(1)
      val slotWidth = size.width / barCount
      val barWidth = (slotWidth * 0.48f).coerceAtMost(34.dp.toPx())

      // Goal dashed/subtle line
      val goalRatio = (dailyGoalMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
      val goalY = size.height - (goalRatio * (size.height - 12.dp.toPx()))
      drawLine(
          color = secondary.copy(alpha = 0.55f),
          start = Offset(0f, goalY),
          end = Offset(size.width, goalY),
          strokeWidth = 1.5.dp.toPx(),
      )

      // Baseline
      drawLine(
          color = outline,
          start = Offset(0f, size.height),
          end = Offset(size.width, size.height),
          strokeWidth = 1.dp.toPx(),
      )

      points.forEachIndexed { index, pt ->
        val ratio = (pt.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
        val barHeight = (ratio * (size.height - 12.dp.toPx())).coerceAtLeast(if (pt.minutes > 0) 6.dp.toPx() else 3.dp.toPx())
        val left = index * slotWidth + (slotWidth - barWidth) / 2f
        val top = size.height - barHeight
        val brush =
            if (pt.minutes >= dailyGoalMinutes) {
              Brush.verticalGradient(listOf(primary, secondary))
            } else {
              Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.45f)))
            }
        drawRoundRect(
            brush = brush,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
      points.forEach { pt ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
              text = pt.shortLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
              text = if (pt.minutes > 0) "${pt.minutes}m" else "-",
              style = MaterialTheme.typography.labelMedium,
              color =
                  if (pt.minutes > 0) MaterialTheme.colorScheme.onSurface
                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        }
      }
    }
  }
}

@Composable
fun TrendAreaChart(
    points: List<DayStudyPoint>,
    modifier: Modifier = Modifier,
) {
  val primary = MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
  val maxVal = (points.maxOfOrNull { it.minutes } ?: 0L).coerceAtLeast(60L)

  Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
    // 3 horizontal gridlines
    for (i in 0..2) {
      val y = size.height * (i / 2f)
      drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }

    if (points.isEmpty()) return@Canvas
    val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width

    val linePath = Path()
    val areaPath = Path()
    val coords =
        points.mapIndexed { idx, pt ->
          val x = idx * stepX
          val ratio = (pt.minutes.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
          val y = size.height - (ratio * (size.height - 16.dp.toPx())) - 4.dp.toPx()
          Offset(x, y)
        }

    coords.forEachIndexed { idx, offset ->
      if (idx == 0) {
        linePath.moveTo(offset.x, offset.y)
        areaPath.moveTo(offset.x, size.height)
        areaPath.lineTo(offset.x, offset.y)
      } else {
        val prev = coords[idx - 1]
        val midX = (prev.x + offset.x) / 2f
        linePath.cubicTo(midX, prev.y, midX, offset.y, offset.x, offset.y)
        areaPath.cubicTo(midX, prev.y, midX, offset.y, offset.x, offset.y)
      }
    }
    areaPath.lineTo(coords.last().x, size.height)
    areaPath.close()

    drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(listOf(primary.copy(alpha = 0.35f), Color.Transparent)),
    )
    drawPath(
        path = linePath,
        color = primary,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
    coords.forEach { pt ->
      drawCircle(color = primary, radius = 4.dp.toPx(), center = pt)
    }
  }
}

@Composable
fun SubjectDonutChart(
    breakdown: List<SubjectDistribution>,
    modifier: Modifier = Modifier,
) {
  val palette = listOf(EmeraldGlow, AmberXp, SapphireFocus, ShieldRose, Color(0xFFA78BFA))
  if (breakdown.isEmpty()) {
    Text(
        text = "Complete a focus session to unlock subject mastery distribution.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  Row(
      modifier = modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Canvas(modifier = Modifier.size(118.dp).padding(6.dp)) {
      val strokeWidth = 18.dp.toPx()
      var startAngle = -90f
      breakdown.take(5).forEachIndexed { index, item ->
        val sweep = (item.percentage / 100f) * 360f
        drawArc(
            color = palette[index % palette.size],
            startAngle = startAngle,
            sweepAngle = (sweep - 2f).coerceAtLeast(4f),
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        startAngle += sweep
      }
    }

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      breakdown.take(5).forEachIndexed { index, item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
          ) {
            Box(
                modifier =
                    Modifier.size(10.dp)
                        .clip(CircleShape)
                        .background(palette[index % palette.size])
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.subject,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
              text = "${item.minutes}m (${item.percentage.toInt()}%)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
