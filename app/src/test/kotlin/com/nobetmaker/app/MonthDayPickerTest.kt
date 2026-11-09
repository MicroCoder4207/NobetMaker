package com.nobetmaker.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.MonthDayPicker
import com.nobetmaker.app.ui.NobetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** İstek penceresindeki gün seçici yalnızca o ayı gösterir ve dokunulan günleri seçer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthDayPickerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun yalnizcaAyinGunleriSecilir() {
        val month = YearMonth.of(2026, 11)
        var picked by mutableStateOf(setOf<LocalDate>())
        rule.setContent {
            NobetTheme {
                Surface {
                    MonthDayPicker(
                        month, selected = { it in picked }, onTap = { d -> picked = if (d in picked) picked - d else picked + d },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        rule.onNodeWithText("Kasım 2026").assertExists()
        // Önceki ve sonraki ayın günleri gösterilmez: 30 bir kez (30 Kasım), 31 hiç yok.
        assertEquals(1, rule.onAllNodesWithText("30").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("31").fetchSemanticsNodes().size)
        rule.onNodeWithText("4").performClick()
        rule.onNodeWithText("12").performClick()
        rule.onNodeWithText("4").performClick()
        assertEquals(setOf(month.atDay(12)), picked)
        rule.onNodeWithText("5").performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView.rootView
            val bmp = Bitmap.createBitmap(root.width, root.height / 2, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File("build/screens").mkdirs()
            File("build/screens/05b-gun-secici.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
