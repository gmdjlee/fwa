package dev.dj.foldwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CLAUDE.md 의 "철칙 — `domain/` 에 `import android.*` 이 들어가면 리뷰 거부" 를 기계화한다.
 *
 * 이 철칙은 이 프로젝트의 유일한 회귀 방어선(실기기 없이 검증 가능한 표면적)인데도 도입 전까지
 * **사람의 리뷰에만 의존**했다. 실제로는 잘 지켜지고 있었지만(도입 시점 위반 0건) 그 상태를
 * 유지해 주는 장치가 없었다 — 소스 파일을 읽어 검사하는 몇 줄짜리 JVM 테스트면 영구히 보장된다.
 *
 * 단위 테스트의 작업 디렉터리는 모듈 루트(`app/`)이므로 아래 상대 경로가 해석된다.
 */
class ArchitectureTest {

    @Test
    fun `domain 은 android·androidx·serialization 을 import 하지 않는다`() {
        val forbidden = Regex("""^import\s+(android|androidx|kotlinx\.serialization)\b""", RegexOption.MULTILINE)
        val offenders = File("src/main/java/dev/dj/foldwindow/domain")
            .walkTopDown().filter { it.extension == "kt" }
            .filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * `data/ProfileStoreMapping.kt` 는 domain/ 은 아니지만 ProfileStore(DataStore 접근 계층)가 쓰는
     * 키/직렬화 상수를 android 의존 없이 테스트하기 위해 의도적으로 순수 Kotlin 으로 분리된 파일이다
     * (파일 자체 KDoc 참고). 이 계약을 동결한다 — 현재는 통과한다.
     */
    @Test
    fun `data ProfileStoreMapping 은 android·androidx 를 import 하지 않는다`() {
        val forbidden = Regex("""^import\s+(android|androidx)\b""", RegexOption.MULTILINE)
        val file = File("src/main/java/dev/dj/foldwindow/data/ProfileStoreMapping.kt")
        assertTrue("ProfileStoreMapping.kt 를 찾지 못함 — 경로 확인 필요", file.exists())
        val offenders = if (forbidden.containsMatchIn(file.readText())) listOf(file.name) else emptyList()
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * 가드: 경로 오타로 위 `domain` 테스트가 대상 파일을 하나도 못 찾으면 `offenders` 가 항상
     * 빈 목록이 되어 테스트가 "항상 통과"(vacuous pass)해 버린다 — 이 테스트의 유일한 실패 모드를
     * 막는다. `.kt` 파일이 실제로 1개 이상 발견돼야 위 테스트가 의미를 갖는다.
     */
    @Test
    fun `domain 디렉터리 가드 — kt 파일을 실제로 찾았는지 확인한다`() {
        val ktFileCount = File("src/main/java/dev/dj/foldwindow/domain")
            .walkTopDown().count { it.extension == "kt" }
        assertTrue(
            "domain 디렉터리에서 .kt 파일을 하나도 찾지 못함 — 경로 오타로 순수성 테스트가 " +
                "vacuous pass 하는 사고를 막는 가드",
            ktFileCount > 0,
        )
    }
}
