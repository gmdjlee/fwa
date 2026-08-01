package dev.dj.foldwindow.service

import dev.dj.foldwindow.IShellExec
import dev.dj.foldwindow.domain.ShellCommandPolicy
import java.util.concurrent.TimeUnit

/**
 * P4-1 Shizuku UserService 구현체.
 *
 * Shizuku 매니저가 이 클래스를 shell UID 프로세스(root 아님)에서 리플렉션으로 로드·실행한다 —
 * 일반 Android `<service>` 컴포넌트가 아니므로 AndroidManifest 등록이 필요 없다(Shizuku 표준
 * UserService 규약). **인자 없는 생성자 필수**.
 *
 * **AIDL 이 `String run(String command)` + `sh -c` 에서 `String run(in String[] argv, long
 * timeoutMs)` 로 바뀐 이유**: 셸 파싱이 사라지면 인용/이스케이프 문제 자체가 소멸한다 —
 * 작은따옴표로 `$` 확장을 막던 패턴(예: YouTube `Shell$HomeActivity`)이 "패키지매니저에서 온
 * 값이니 안전"이라는 전제에 의존하고 있었는데, argv 전달은 그 전제를 아예 필요 없게 만든다.
 * 대신 [ShellCommandPolicy] 허용 목록으로 shell UID(2000) 권한의 사용 범위를 제한한다
 * (목적은 "인젝션 방어"가 아니라 "권한 최소화" — [ShellCommandPolicy] KDoc 참고).
 *
 * ⚠ AIDL 시그니처를 또 바꾼다면 `versionCode` 를 반드시 올려라 —
 * `Shizuku.UserServiceArgs.version(BuildConfig.VERSION_CODE)` 가 UserService 프로세스 재생성을
 * 결정하므로, 시그니처가 바뀌었는데 버전이 같으면 **구 바이너리가 재사용돼 `AbstractMethodError`**
 * 가 난다.
 *
 * 표준 출력/표준 에러를 [ProcessBuilder.redirectErrorStream] 로 한 스트림에 합쳐 "종료코드\n출력"
 * 형태로 반환한다 — 실패 사유를 호출자가 그대로 로그에 남길 수 있게 한다(조용한 실패 금지).
 */
class ShellExecUserService : IShellExec.Stub() {

    override fun run(argv: Array<String>?, timeoutMs: Long): String {
        val args = argv?.toList().orEmpty()
        if (!ShellCommandPolicy.isAllowed(args)) {
            return "-1\nblocked by policy: ${args.take(2).joinToString(" ")}"
        }
        return try {
            // F4: stdout/stderr 를 파이프 1개로 합친다 — 별도 스트림을 순차로 소진하면 자식이
            // 안 읽힌 스트림(예: stderr 64KB)을 채운 채 상호 대기에 빠질 수 있다.
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            process.outputStream.close() // 자식이 stdin 입력을 기다리며 멎지 않도록 즉시 닫는다

            // F3: 읽기를 별도 스레드로 분리하는 이유 — 자식이 stdout 을 닫지 않으면 읽기가
            // 영원히 블록돼 아래 process.waitFor(timeoutMs, ...) 에 도달조차 못 한다. 읽기와
            // 대기를 분리해야 타임아웃이 실효를 갖는다.
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { r ->
                        r.forEachLine { line -> synchronized(output) { output.appendLine(line) } }
                    }
                }
            }.apply {
                isDaemon = true
                name = "fw-shell-reader"
                start()
            }

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly() // 스트림이 끊기므로 reader 스레드도 곧 풀린다
                reader.join(READER_JOIN_ON_TIMEOUT_MS)
                return "-1\ntimeout after ${timeoutMs}ms"
            }
            reader.join(READER_JOIN_MS)
            val out = synchronized(output) { output.toString() }
            "${process.exitValue()}\n$out"
        } catch (e: Exception) {
            "-1\n${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Shizuku 표준 규약: UserService 프로세스는 명시적으로 자기 자신을 종료해야 한다. */
    fun destroy() {
        System.exit(0)
    }

    private companion object {
        /** 정상 종료 시 reader 스레드가 잔여 출력을 다 모을 때까지 기다리는 시간. */
        const val READER_JOIN_MS = 1_000L

        /**
         * 타임아웃(`destroyForcibly`) 이후 reader 스레드 정리 대기. 프로세스 강제 종료로 스트림이
         * 곧바로 끊기므로 정상 종료 케이스([READER_JOIN_MS])보다 짧게 잡아도 충분하다.
         */
        const val READER_JOIN_ON_TIMEOUT_MS = 500L
    }
}
