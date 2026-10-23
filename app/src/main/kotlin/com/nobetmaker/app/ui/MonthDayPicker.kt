package com.nobetmaker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val weekdayShort = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

/**
 * Yalnızca verilen ayın günlerini gösteren takvim; başka aya geçilemez.
 * [selected] dolu daire, [inRange] aralığın ara günleri olarak boyanır.
 */
@Composable
fun MonthDayPicker(
    month: YearMonth,
    selected: (LocalDate) -> Boolean,
    onTap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    inRange: (LocalDate) -> Boolean = { false },
) {
    val scheme = MaterialTheme.colorScheme
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(month.title(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth()) {
            weekdayShort.forEachIndexed { i, d ->
                Text(
                    d, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                    color = if (i >= 5) scheme.secondary else scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).height(40.dp).padding(1.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val isSel = selected(date)
                            val between = !isSel && inRange(date)
                            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                            Box(
                                (if (between) Modifier.fillMaxSize() else Modifier.size(38.dp))
                                    .clip(if (between) RoundedCornerShape(8.dp) else CircleShape)
                                    .background(
                                        when {
                                            isSel -> scheme.primary
                                            between -> scheme.primaryContainer
                                            else -> scheme.surface
                                        },
                                    )
                                    .clickable { onTap(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayNum.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSel -> scheme.onPrimary
                                        between -> scheme.onPrimaryContainer
                                        weekend -> scheme.secondary
                                        else -> scheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
