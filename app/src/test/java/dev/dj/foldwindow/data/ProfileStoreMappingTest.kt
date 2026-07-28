package dev.dj.foldwindow.data

import dev.dj.foldwindow.domain.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProfileStoreMapping] 은 순수 Kotlin(android import 없음)이라 JVM 에서 직접 테스트한다.
 * 특히 [ProfileStoreMapping.KEY_BUBBLE_ENABLED] 등 레거시 키 상수는 SharedPreferencesMigration
 * 계약이므로, 값이 실수로 바뀌는 회귀를 여기서 문서화하듯 고정한다.
 */
class ProfileStoreMappingTest {

    // ── placement 왕복 ───────────────────────────────────────────

    @Test
    fun `TOP round-trips through storage`() {
        val stored = ProfileStoreMapping.placementToStorage(Placement.TOP)
        assertEquals("TOP", stored)
        assertEquals(Placement.TOP, ProfileStoreMapping.placementFromStorage(stored))
    }

    @Test
    fun `BOTTOM round-trips through storage`() {
        val stored = ProfileStoreMapping.placementToStorage(Placement.BOTTOM)
        assertEquals("BOTTOM", stored)
        assertEquals(Placement.BOTTOM, ProfileStoreMapping.placementFromStorage(stored))
    }

    // ── 오염값 방어 ──────────────────────────────────────────────

    @Test
    fun `null raw value maps to null`() {
        assertNull(ProfileStoreMapping.placementFromStorage(null))
    }

    @Test
    fun `blank raw value maps to null`() {
        assertNull(ProfileStoreMapping.placementFromStorage("   "))
    }

    @Test
    fun `lowercase top is not accepted`() {
        assertNull(ProfileStoreMapping.placementFromStorage("top"))
    }

    @Test
    fun `unknown enum-like value maps to null`() {
        assertNull(ProfileStoreMapping.placementFromStorage("LEFT"))
    }

    @Test
    fun `garbage value maps to null without throwing`() {
        assertNull(ProfileStoreMapping.placementFromStorage("!!not-a-placement??"))
    }

    // ── placementKeyFor ──────────────────────────────────────────

    @Test
    fun `placementKeyFor produces distinct keys per package`() {
        val youtube = ProfileStoreMapping.placementKeyFor("com.google.android.youtube")
        val tving = ProfileStoreMapping.placementKeyFor("net.cj.cjhv.gs.tving")
        assertTrue(youtube != tving)
        assertTrue(youtube.startsWith("last_placement."))
        assertTrue(youtube.endsWith("com.google.android.youtube"))
    }

    @Test
    fun `placementKeyFor rejects blank packageName`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileStoreMapping.placementKeyFor("   ")
        }
    }

    // ── 레거시 키 이름 고정(마이그레이션 계약) ─────────────────────

    @Test
    fun `legacy key constants are frozen for SharedPreferencesMigration contract`() {
        assertEquals("bubble_prefs", ProfileStoreMapping.LEGACY_PREFS_NAME)
        assertEquals("bubble_enabled", ProfileStoreMapping.KEY_BUBBLE_ENABLED)
        assertEquals("bubble_x", ProfileStoreMapping.KEY_BUBBLE_X)
        assertEquals("bubble_y", ProfileStoreMapping.KEY_BUBBLE_Y)
    }

    // ── measuredAspectKeyFor (DESIGN #12 §6) ──────────────────────

    @Test
    fun `measuredAspectKeyFor produces distinct keys per package`() {
        val youtube = ProfileStoreMapping.measuredAspectKeyFor("com.google.android.youtube")
        val tving = ProfileStoreMapping.measuredAspectKeyFor("net.cj.cjhv.gs.tving")
        assertTrue(youtube != tving)
        assertTrue(youtube.startsWith("measured_aspect."))
        assertTrue(youtube.endsWith("com.google.android.youtube"))
    }

    @Test
    fun `measuredAspectKeyFor rejects blank packageName`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileStoreMapping.measuredAspectKeyFor("   ")
        }
    }

    // ── aspectFromStorage 오염값 방어 (DESIGN #12 §6) ──────────────

    @Test
    fun `aspectFromStorage accepts value within range`() {
        val restored = ProfileStoreMapping.aspectFromStorage(1.7778f)
        assertEquals(1.7778f, restored!!, 0.0001f)
    }

    @Test
    fun `aspectFromStorage null maps to null`() {
        assertNull(ProfileStoreMapping.aspectFromStorage(null))
    }

    @Test
    fun `aspectFromStorage NaN maps to null`() {
        assertNull(ProfileStoreMapping.aspectFromStorage(Float.NaN))
    }

    @Test
    fun `aspectFromStorage positive infinity maps to null`() {
        assertNull(ProfileStoreMapping.aspectFromStorage(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `aspectFromStorage below MIN_ASPECT maps to null`() {
        assertNull(ProfileStoreMapping.aspectFromStorage(0.9f))
    }

    @Test
    fun `aspectFromStorage above MAX_ASPECT maps to null`() {
        assertNull(ProfileStoreMapping.aspectFromStorage(4.1f))
    }

    // ── panelWidgetModeFromStorage 허용값 왕복 (P4-2) ─────────────

    @Test
    fun `panelWidgetModeFromStorage accepts CLOCK`() {
        assertEquals("CLOCK", ProfileStoreMapping.panelWidgetModeFromStorage("CLOCK"))
    }

    @Test
    fun `panelWidgetModeFromStorage accepts MEMO`() {
        assertEquals("MEMO", ProfileStoreMapping.panelWidgetModeFromStorage("MEMO"))
    }

    @Test
    fun `panelWidgetModeFromStorage accepts BLACK`() {
        assertEquals("BLACK", ProfileStoreMapping.panelWidgetModeFromStorage("BLACK"))
    }

    // ── panelWidgetModeFromStorage 오염값 방어 ────────────────────

    @Test
    fun `panelWidgetModeFromStorage null raw value maps to null`() {
        assertNull(ProfileStoreMapping.panelWidgetModeFromStorage(null))
    }

    @Test
    fun `panelWidgetModeFromStorage blank raw value maps to null`() {
        assertNull(ProfileStoreMapping.panelWidgetModeFromStorage("   "))
    }

    @Test
    fun `panelWidgetModeFromStorage lowercase clock is not accepted`() {
        assertNull(ProfileStoreMapping.panelWidgetModeFromStorage("clock"))
    }

    @Test
    fun `panelWidgetModeFromStorage unknown value maps to null`() {
        assertNull(ProfileStoreMapping.panelWidgetModeFromStorage("PURPLE"))
    }

    // ── sanitizePanelMemo 절단 경계 ────────────────────────────────

    @Test
    fun `sanitizePanelMemo leaves text under the limit unchanged`() {
        val text = "a".repeat(10)
        assertEquals(text, ProfileStoreMapping.sanitizePanelMemo(text))
    }

    @Test
    fun `sanitizePanelMemo leaves text exactly at the limit unchanged`() {
        val text = "a".repeat(ProfileStoreMapping.PANEL_MEMO_MAX_CHARS)
        val result = ProfileStoreMapping.sanitizePanelMemo(text)
        assertEquals(ProfileStoreMapping.PANEL_MEMO_MAX_CHARS, result.length)
        assertEquals(text, result)
    }

    @Test
    fun `sanitizePanelMemo truncates text exceeding the limit`() {
        val text = "a".repeat(ProfileStoreMapping.PANEL_MEMO_MAX_CHARS + 500)
        val result = ProfileStoreMapping.sanitizePanelMemo(text)
        assertEquals(ProfileStoreMapping.PANEL_MEMO_MAX_CHARS, result.length)
        assertEquals("a".repeat(ProfileStoreMapping.PANEL_MEMO_MAX_CHARS), result)
    }

    @Test
    fun `sanitizePanelMemo handles empty string`() {
        assertEquals("", ProfileStoreMapping.sanitizePanelMemo(""))
    }
}
