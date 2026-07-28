package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 * 셸 명령(`am stack list`) 출력을 파싱하는 로직이라 android 비의존적이며 100% JVM 테스트 가능하다.
 */

/**
 * `am stack list` 출력에서 패키지의 taskId 를 뽑는다 (P4-1).
 *
 * 태스크 행은 단일 물리 행이다 — taskId·컴포넌트·bounds·visible·topActivity 가 개행 없이
 * 한 줄에 온다 (실기기 `am stack list` 46행 원문 대조 완료, DEVICE_FACTS.md 「P4-1 프로브 F1~F6」 절).
 * RootTask 헤더 행과 ` configuration={...}` 행이 태스크 행 사이에 끼어 다행 구조로 보이지만,
 * 태스크 행 자체는 아래처럼 자립적 단일 행이다. 실측 원문:
 * ```
 *   taskId=4991: com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity bounds=[0,0][1968,2184] userId=0 visible=false topActivity=ComponentInfo{com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity}
 * ```
 * 컴포넌트 클래스명에 `$` 가 올 수 있다(예: YouTube `.app.honeycomb.Shell$HomeActivity`).
 */
object StackListParser {

    /** 패키지명은 `/` 앞까지 통째로 캡처한다 — "com.foo" 가 "com.foobar" 부분 문자열에 매치되지 않는다. */
    private val TASK_LINE = Regex("""taskId=(\d+):\s+([\w.]+)/\S+.*?visible=(true|false)""")

    /** [packageName] 의 태스크 중 visible=true 를 우선 채택, 없으면 첫 매치. 없으면 null. */
    fun taskIdFor(stackListOutput: String, packageName: String): Int? {
        val matches = stackListOutput.lineSequence()
            .mapNotNull { line -> TASK_LINE.find(line) }
            .filter { it.groupValues[2] == packageName }
            .toList()
        if (matches.isEmpty()) return null

        val visible = matches.firstOrNull { it.groupValues[3] == "true" }
        return (visible ?: matches.first()).groupValues[1].toIntOrNull()
    }
}
