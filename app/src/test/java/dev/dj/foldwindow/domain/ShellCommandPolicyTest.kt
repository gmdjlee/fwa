package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShellCommandPolicy] 의 허용 목록 판정 검증 (W2, docs/IMPROVEMENT_PLAN_2026-07-29.md
 * §F3+F4+S2+S3 / docs/CODE_REVIEW_2026-07-29.md S2).
 *
 * 배경: argv 전환으로 셸 파싱 자체가 사라졌으므로(S3 해소) 이 정책의 목적은 "인젝션 방어"가
 * 아니라 shell UID(2000) 권한의 최소화(S2 해소)다. 이 파일은 "실제로 필요한 3종 외에는 전부
 * 거부한다"는 경계를 못 박는다.
 */
class ShellCommandPolicyTest {

    // ── 허용 3종: 해피 패스 ─────────────────────────────────────────

    @Test
    fun `am start 조합은 허용된다`() {
        val argv = listOf("am", "start", "--windowingMode", "5", "-n", "dev.dj.foldwindow/.MainActivity")

        assertTrue(ShellCommandPolicy.isAllowed(argv))
    }

    @Test
    fun `am stack list 조합은 허용된다`() {
        assertTrue(ShellCommandPolicy.isAllowed(listOf("am", "stack", "list")))
    }

    @Test
    fun `am task resize 조합은 허용된다`() {
        val argv = listOf("am", "task", "resize", "5", "0", "0", "100", "100")

        assertTrue(ShellCommandPolicy.isAllowed(argv))
    }

    // ── 크기 하한: 실행파일만으로는 서브커맨드가 없어 판정 불가 ──────────

    @Test
    fun `빈 argv 는 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(emptyList()))
    }

    @Test
    fun `크기 1 인 argv 는 서브커맨드가 없어 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(listOf("am")))
    }

    // ── 허용 목록 밖: 미허용 서브커맨드·실행파일 ─────────────────────

    @Test
    fun `am kill 처럼 허용되지 않은 서브커맨드는 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(listOf("am", "kill")))
    }

    @Test
    fun `pm list 처럼 허용 목록에 없는 실행파일은 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(listOf("pm", "list")))
    }

    @Test
    fun `sh -c 로 셸을 재도입하려는 시도는 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(listOf("sh", "-c", "am start -n evil")))
    }

    // ── 대소문자 구분: 정확 일치만 허용 ──────────────────────────────

    @Test
    fun `실행파일을 대문자로 바꾸면 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(listOf("AM", "start")))
    }

    @Test
    fun `서브커맨드를 대문자로 바꾸면 거부된다`() {
        assertFalse(ShellCommandPolicy.isAllowed(listOf("am", "START")))
    }

    // ── NUL 인자: execve 절단 방어 ────────────────────────────────────

    @Test
    fun `허용 조합이어도 인자에 NUL 이 섞이면 거부된다`() {
        // 검사기가 보는 문자열과 execve 가 실제로 넘길 인자가 달라지는 절단 우회 시나리오.
        val poisoned = "dev.dj.foldwindow/.MainActivity" + 0.toChar() + "trailing-garbage"
        val argv = listOf("am", "start", "-n", poisoned)

        assertFalse(ShellCommandPolicy.isAllowed(argv))
    }

    // ── MAX_ARGV_SIZE 경계 ────────────────────────────────────────────

    @Test
    fun `argv 크기가 정확히 MAX_ARGV_SIZE 면 허용된다`() {
        val argv = listOf("am", "start") + List(ShellCommandPolicy.MAX_ARGV_SIZE - 2) { "arg$it" }
        assertEquals(ShellCommandPolicy.MAX_ARGV_SIZE, argv.size)

        assertTrue(ShellCommandPolicy.isAllowed(argv))
    }

    @Test
    fun `argv 크기가 MAX_ARGV_SIZE 를 1 넘으면 거부된다`() {
        val argv = listOf("am", "start") + List(ShellCommandPolicy.MAX_ARGV_SIZE - 1) { "arg$it" }
        assertEquals(ShellCommandPolicy.MAX_ARGV_SIZE + 1, argv.size)

        assertFalse(ShellCommandPolicy.isAllowed(argv))
    }
}
