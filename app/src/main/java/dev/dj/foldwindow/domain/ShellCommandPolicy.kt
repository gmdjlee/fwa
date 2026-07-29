package dev.dj.foldwindow.domain

/**
 * P4-1 Shizuku 셸 실행([dev.dj.foldwindow.service.ShellExecUserService])이 shell UID(2000)
 * 권한으로 수행할 명령의 허용 목록.
 *
 * [IMPROVEMENT_PLAN_2026-07-29.md §F3+F4+S2+S3] AIDL 을 `sh -c` 문자열 전달에서 argv 배열
 * 전달로 전환하면서 셸 파싱 자체가 사라졌다 — 즉 **이 목록의 목적은 "셸 인젝션 방어"가 아니다**
 * (애초에 인젝션할 셸이 없다). 목적은 **shell UID 권한의 최소화**다: 앱 내 어떤 코드 경로가
 * 실수로든 오남용되든 `ShellExecUserService.run` 을 호출할 수 있는 이상, 실제로 필요한 3종
 * (`am start` / `am stack list` / `am task resize`,
 * [dev.dj.foldwindow.service.ArrangerAccessibilityService] 의 `performStartPopup` 참고) 밖의
 * 임의 명령이 shell 권한으로 실행되는 사고를 막는다.
 *
 * 순수 Kotlin — 이 파일 어디에도 `android.*` import 가 없다(CLAUDE.md 아키텍처 철칙,
 * `ArchitectureTest` 로 기계 검증). `Array<String>` 이 아니라 [List] 를 인자로 받는 이유도
 * 도메인 순수성 관례: 배열은 `==` 가 참조 동등성이라 테스트/호출부에서 의도치 않은 함정을
 * 유발하기 쉽다 — 호출부(`ShellExecUserService`, `ShizukuShell`)가 `Array.toList()` 로 변환해
 * 넘긴다.
 */
object ShellCommandPolicy {

    /** 실제로 필요한 3종: `am start` / `am stack list` / `am task resize`. */
    private val ALLOWED: Map<String, Set<String>> = mapOf(
        "am" to setOf("start", "stack", "task"),
    )

    /**
     * argv 최대 길이. 실사용 중 최장 명령은 `am task resize <id> <l> <t> <r> <b>` 로 7개
     * 토큰이다 — 여유를 포함해 넉넉히 잡되, 정책이 사실상 무제한이 되지 않도록 상한을 둔다.
     */
    const val MAX_ARGV_SIZE = 16

    /** NUL 문자의 코드 포인트. [isAllowed] 에서 execve 인자 절단 방어에 쓴다. */
    private const val NUL_CODE_POINT = 0

    /**
     * [argv] 가 허용 목록을 통과하면 true. 아래를 전부 만족해야 한다:
     *
     * - 크기가 `2..[MAX_ARGV_SIZE]` 범위(포함) 안에 있어야 한다 — 실행파일 + 서브커맨드
     *   최소 2개가 필요하고, 상한을 넘는 argv 는 예상 범위 밖이라 거부한다.
     * - `argv[0]`(실행파일)과 `argv[1]`(서브커맨드) 조합이 [ALLOWED] 에 **정확히 일치**해야
     *   한다 — **대소문자 구분**. `"AM" != "am"`, `"START" != "start"` 이므로 대소문자를 바꿔
     *   허용 목록을 우회하는 시도를 차단한다.
     * - 어떤 인자에도 NUL(코드 포인트 0) 문자가 없어야 한다. JVM 문자열은 NUL 을 담을 수
     *   있지만 execve 로 전달되는 인자는 NUL 에서 절단될 수 있다 — 검사에 통과한 문자열과
     *   실제 실행되는 인자가 달라지는 방어 우회를 막는다.
     */
    fun isAllowed(argv: List<String>): Boolean {
        if (argv.size < 2 || argv.size > MAX_ARGV_SIZE) return false
        if (argv.any { arg -> arg.any { c -> c.code == NUL_CODE_POINT } }) return false
        return ALLOWED[argv[0]]?.contains(argv[1]) == true
    }
}
