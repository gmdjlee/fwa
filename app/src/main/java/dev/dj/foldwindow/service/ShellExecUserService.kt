package dev.dj.foldwindow.service

import dev.dj.foldwindow.IShellExec
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * P4-1 Shizuku UserService 구현체.
 *
 * Shizuku 매니저가 이 클래스를 shell UID 프로세스(root 아님)에서 리플렉션으로 로드·실행한다 —
 * 일반 Android `<service>` 컴포넌트가 아니므로 AndroidManifest 등록이 필요 없다(Shizuku 표준
 * UserService 규약). **인자 없는 생성자 필수**.
 *
 * `am`(Activity Manager) 셸 명령은 shell uid 에서 실행 가능하므로 [run] 은 단순히 `sh -c` 로
 * 위임한다. 표준 출력/표준 에러를 모두 모아 "종료코드\n출력" 형태로 반환한다 — 실패 사유를
 * 호출자가 그대로 로그에 남길 수 있게 한다(조용한 실패 금지).
 */
class ShellExecUserService : IShellExec.Stub() {

    override fun run(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = buildString {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { appendLine(it) }
                }
                BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                    lines.forEach { appendLine(it) }
                }
            }
            val exitCode = process.waitFor()
            "$exitCode\n$output"
        } catch (e: Exception) {
            "-1\n${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Shizuku 표준 규약: UserService 프로세스는 명시적으로 자기 자신을 종료해야 한다. */
    fun destroy() {
        System.exit(0)
    }
}
