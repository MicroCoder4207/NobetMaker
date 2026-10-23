package com.nobetmaker.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import com.nobetmaker.app.ui.title
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.solver.ProblemBuilder
import com.nobetmaker.core.solver.Solver
import java.io.File
import java.time.YearMonth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Uygulamayı JVM üzerinde (Robolectric) açan arayüz testlerinin ortak yardımcıları.
 * Ekran görüntüleri app/build/screens altına yazılır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AppUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    protected val out = File("build/screens").apply { mkdirs() }

    @Before
    fun registerDialogLayout() {
        IdlingRegistry.getInstance().register(DialogLayoutIdling)
        // FileProvider klasör yollarını süreç boyunca saklar; her Robolectric testinin geçici klasörü farklıdır.
        runCatching {
            (androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<*, *>).clear()
        }
    }

    @After
    fun unregisterDialogLayout() {
        IdlingRegistry.getInstance().unregister(DialogLayoutIdling)
    }

    // ------------------------------------------------------------ veri

    protected val app: NobetApp get() = rule.activity.application as NobetApp
    protected fun data(): AppData = app.repository.data.value
    protected fun update(transform: (AppData) -> AppData) {
        rule.runOnIdle { app.repository.update(transform).getOrThrow() }
        rule.waitForIdle()
    }

    /** Ayın taslak listesini çözücüyle hazırlar (arayüzden üretmekten hızlı). */
    protected fun draftMonth(ym: YearMonth) = update { d0 ->
        val d = DataOps.ensureMonth(d0, ym)
        val pr = ProblemBuilder.build(d, ym, DataOps.currentAssignments(d, ym), DataOps.quotaMap(d.month(ym)))
        DataOps.applySolution(d, pr, Solver(pr).solve(seeds = 2, iterations = 3_000).solutions.first().assignment)
    }

    /** Veri koşulu sağlanana kadar bekler (ViewModel güncellemeleri arka planda yazılır). */
    protected fun waitForData(timeout: Long = 10_000, what: String = "veri", condition: (AppData) -> Boolean) {
        try {
            rule.waitUntil(timeout) { condition(data()) }
        } catch (e: Throwable) {
            throw AssertionError("Beklenen değişiklik olmadı: $what", e)
        }
    }

    // ------------------------------------------------------------ arayüz

    protected fun waitForText(text: String, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun exists(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    /** Ana penceredeki düğme ya da metne kaydırıp tıklar. */
    protected fun click(text: String, substring: Boolean = false) {
        // Bazı bileşenler (ör. yüzen düğme) metni yalnızca birleştirilmemiş ağaçta gösterir.
        val merged = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        val node = rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = !merged).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    protected fun clickDesc(desc: String) {
        rule.onNodeWithContentDescription(desc).performClick()
        rule.waitForIdle()
    }

    /** Açık pencere (diyalog) içindeki tıklanabilir öğe. */
    protected fun inDialog(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        rule.onNode(hasText(text, substring = substring) and hasAnyAncestor(isDialog()) and hasClickAction())

    protected fun clickInDialog(text: String, substring: Boolean = false) {
        val node = inDialog(text, substring)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    protected fun toggleInDialog(label: String) {
        rule.onNode(isToggleable() and hasText(label) and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
    }

    protected fun toggle(label: String) {
        val node = rule.onNode(isToggleable() and hasText(label))
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    protected fun typeInDialog(label: String, text: String) {
        rule.onNode(hasSetTextAction() and hasText(label) and hasAnyAncestor(isDialog())).performTextInput(text)
        rule.waitForIdle()
    }

    protected fun type(label: String, text: String, clear: Boolean = false) {
        val node = rule.onNode(hasSetTextAction() and hasText(label))
        if (clear) node.performTextClearance()
        node.performTextInput(text)
        rule.waitForIdle()
    }

    /**
     * Açık tarih seçicide günü seçip onaylar. Gün hücrelerinin metni tam tarihtir
     * (ör. "9 Kasım 2026 Pazartesi"); [monthName] gösterilen aydır.
     */
    protected fun pickDay(day: Int, monthName: String) {
        waitForText("Tamam")
        val cell = Regex("^$day $monthName ")
        val matcher = SemanticsMatcher("gün $day") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { cell.containsMatchIn(it.text) } == true
        } and hasAnyAncestor(isDialog()) and hasClickAction()
        rule.onNode(matcher).performClick()
        rule.waitForIdle()
        clickInDialog("Tamam")
    }

    /** Açılır menüden seçim. */
    protected fun pickFromMenu(text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(isPopup()) and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    /** Etiketli sayaçta (Stepper) "+" ya da "−" düğmesi. */
    protected fun stepper(label: String, plus: Boolean, inDialog: Boolean = false) {
        var m: SemanticsMatcher = hasText(if (plus) "+" else "−") and hasClickAction() and hasAnySibling(hasText(label))
        if (inDialog) m = m and hasAnyAncestor(isDialog())
        val node = rule.onNode(m)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    /** Kartın (kişi, dönem, istek) yanındaki simge düğmesi. */
    protected fun iconNextTo(text: String, desc: String, substring: Boolean = true) {
        val node = rule.onAllNodes(hasContentDescription(desc) and hasClickAction() and hasAnySibling(hasAnyDescendantOrSelfText(text, substring))).onFirst()
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun hasAnyDescendantOrSelfText(text: String, substring: Boolean): SemanticsMatcher =
        hasText(text, substring = substring) or androidx.compose.ui.test.hasAnyDescendant(hasText(text, substring = substring))

    protected fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Takvim ekranında istenen aya gelir. */
    protected fun goToMonth(ym: YearMonth) {
        repeat(36) {
            val shown = shownMonth() ?: error("Takvimde ay başlığı yok")
            if (shown == ym) return
            clickDesc(if (ym.isBefore(shown)) "Önceki ay" else "Sonraki ay")
        }
        error("${ym.title()} açılamadı")
    }

    private fun shownMonth(): YearMonth? {
        for (y in 2023..2030) for (m in 1..12) if (exists(YearMonth.of(y, m).title())) return YearMonth.of(y, m)
        return null
    }

    /** "Liste kaydedildi" gibi bildirimler düğmelerin üstünü kapatabilir; süresini doldurur. */
    protected fun waitSnackbarGone() {
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
    }

    /** Pencerenin bütün görünümlerini (alt sayfalar ve diyaloglar dahil) yazılım tuvaline çizer. */
    protected fun shot(name: String) {
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView.rootView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            root.draw(canvas)
            WindowRoots.all().filter { it !== root && it.isShown && it.width > 0 }.forEach { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                canvas.save()
                canvas.translate(loc[0].toFloat(), loc[1].toFloat())
                v.draw(canvas)
                canvas.restore()
            }
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

/** Robolectric'te açık olan bütün pencere kökleri (ana pencere, diyaloglar, alt sayfalar, açılır menüler). */
object WindowRoots {
    @Suppress("UNCHECKED_CAST")
    fun all(): List<View> = runCatching {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val instance = cls.getMethod("getInstance").invoke(null)
        val field = cls.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(instance) as List<View>).toList()
    }.getOrDefault(emptyList())

    fun composeRoots(): List<ViewRootForTest> = all().flatMap { find(it) }

    private fun find(v: View): List<ViewRootForTest> = when (v) {
        is ViewRootForTest -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { find(v.getChildAt(it)) }
        else -> emptyList()
    }
}

/**
 * Robolectric diyalog pencerelerini yeniden çizmez. Metin alanı olan bir diyalogda Compose'un ikinci
 * yerleşim turu çizimde yapıldığı için hiç gerçekleşmez ve test "boşta değil" diye takılır (gerçek
 * cihazda olmaz). Her boşta kontrolünde bekleyen yerleşimi elle tamamlatır.
 */
object DialogLayoutIdling : IdlingResource {
    override fun getName() = "DialogLayoutIdling"

    override fun isIdleNow(): Boolean {
        for (root in WindowRoots.composeRoots()) {
            if (root.hasPendingMeasureOrLayout && root.view.isAttachedToWindow) runCatching { root.measureAndLayoutForTest() }
        }
        return true
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {}
}
