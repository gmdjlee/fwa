package dev.dj.foldwindow.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import dev.dj.foldwindow.BuildConfig
import dev.dj.foldwindow.IShellExec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * P4-1 Shizuku 셸 실행 래퍼.
 *
 * `Shizuku.newProcess` 는 비공개 API라 사용하지 않는다 — 공식 지원 경로인
 * UserService(AIDL, [ShellExecUserService]) 를 바인드해 shell UID 프로세스에서 `am` 명령을
 * 실행한다(DESIGN_P41_FREEFORM.md §4 후보 A).
 *
 * ADR-2 준수: `bindUserService` 연결 완료는 조건 폴링([BIND_POLL_INTERVAL_MS] 간격,
 * [BIND_TIMEOUT_MS] 데드라인)으로 기다린다. 고정 지연 없음. 최초 [exec] 호출 시 1회만
 * 바인드하고 이후 세션 동안 재사용한다 — binder 사망 시([ServiceConnection.onServiceDisconnected])
 * 상태를 리셋해 다음 [exec] 이 재바인드하도록 한다.
 */
object ShizukuShell {
    private const val TAG = "FWArranger.Shizuku"
    private const val REQUEST_CODE = 30_201
    private const val BIND_POLL_INTERVAL_MS = 50L
    private const val BIND_TIMEOUT_MS = 3_000L

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellExecUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    @Volatile
    private var binder: IShellExec? = null

    @Volatile
    private var binding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service?.let { IShellExec.Stub.asInterface(it) }
            binding = false
            Log.i(TAG, "ShellExecUserService 연결됨")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "ShellExecUserService 연결 끊김 — 다음 exec 이 재바인드함")
            binder = null
            binding = false
        }
    }

    /**
     * Shizuku 가용 여부(설치·실행·권한 허용 전부). Shizuku 앱 미설치/미실행 기기에서도 예외 없이
     * false 를 반환한다 — 이 결과로 버블 메뉴의 "팝업으로 열기" 항목 노출 여부를 결정한다.
     */
    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Shizuku 런타임 권한을 요청한다. 결과는 [onResult] 로 1회 전달되고 리스너는 즉시 해제된다.
     * 바인더 자체가 없으면(Shizuku 미실행) 요청 없이 곧바로 false 를 전달한다.
     */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            onResult(false)
            return
        }
        lateinit var listener: Shizuku.OnRequestPermissionResultListener
        listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                Shizuku.removeRequestPermissionResultListener(listener)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure {
                Log.w(TAG, "requestPermission 실패", it)
                Shizuku.removeRequestPermissionResultListener(listener)
                onResult(false)
            }
    }

    /**
     * [command] 를 shell UID 프로세스에서 실행하고 "종료코드\n출력" 문자열을 반환한다.
     * 실패(바인드 실패/타임아웃/원격 예외)는 null — 호출자가 실패를 명시적으로 처리해야 한다
     * (조용한 실패 금지).
     */
    suspend fun exec(command: String, timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            if (!ensureBound()) {
                Log.w(TAG, "exec: UserService 바인드 실패 — cmd=$command")
                return@withTimeoutOrNull null
            }
            runCatching { binder?.run(command) }
                .onFailure { Log.w(TAG, "exec 실패: cmd=$command", it) }
                .getOrNull()
        }
    }

    /** 이미 바인드돼 있으면 즉시 true. 아니면 bindUserService 를 걸고 연결을 조건 폴링한다. */
    private suspend fun ensureBound(): Boolean {
        if (binder != null) return true
        if (!binding) {
            binding = true
            runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
                .onFailure { e ->
                    Log.w(TAG, "bindUserService 실패", e)
                    binding = false
                }
        }
        val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            while (binder == null && binding) {
                delay(BIND_POLL_INTERVAL_MS)
            }
            binder != null
        }
        return bound ?: false
    }
}
