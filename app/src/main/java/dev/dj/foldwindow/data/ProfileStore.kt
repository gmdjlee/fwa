package dev.dj.foldwindow.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.dj.foldwindow.domain.Placement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "FWProfileStore"

/**
 * 프로세스 전역 단일 인스턴스. DataStore 는 동일 파일에 다중 인스턴스가 생기면 예외를 던지므로
 * 반드시 최상위 프로퍼티 델리게이트로 유지한다(클래스 멤버로 옮기지 말 것) — [ProfileStore] 객체가
 * 여러 개 생성돼도(서비스/액티비티 각자 lazy 생성) 항상 같은 DataStore<Preferences> 인스턴스를 공유한다.
 *
 * [SharedPreferencesMigration] 은 최초 접근 시 1회, 레거시 "bubble_prefs" SharedPreferences
 * 파일의 키를 동일한 이름으로 그대로 Preferences 로 복사하고 원본 파일을 삭제한다. 키 이름은
 * [ProfileStoreMapping.KEY_BUBBLE_ENABLED] 등과 반드시 일치해야 하며, 바뀌면 기존 사용자의
 * 버블 활성 상태/위치가 조용히 유실된다(마이그레이션 계약 — CLAUDE.md "조용한 실패 금지"와 별개로
 * 여기서는 이름 불변 자체가 데이터 보존의 전제조건이다).
 *
 * [실기기 검증 리뷰 지적 반영, 2026-07-25] `corruptionHandler` 없이는 기본 `NoOpCorruptionHandler`
 * 가 적용돼 `fwa_store.preferences_pb` 파일이 손상되면(쓰기 도중 전원 차단 등) 모든 읽기/쓰기가
 * `CorruptionException` 을 영구적으로 던진다 — [FloatingLauncherService.onCreate] 의
 * `runBlocking { store.bubblePosition() }` 이 서비스 시작마다 크래시하는 부팅 크래시 루프로
 * 이어진다(레거시 SharedPreferences 는 손상 시 그냥 빈 값을 돌려줄 뿐 이런 실패 모드가 없었다).
 * [ReplaceFileCorruptionHandler] 로 손상 감지 시 빈 Preferences 로 재시작해 레거시와 동등한
 * 의미론(손상 시 기본값 복구)을 유지한다. 원본 예외는 성공적으로 교체되면 삼켜지므로 여기서
 * 반드시 로그로 남긴다(조용한 실패 금지).
 */
private val Context.fwaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fwa_store",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(TAG, "fwaDataStore 손상 감지 — 빈 Preferences 로 재시작(레거시 SharedPreferences 손상 처리와 동등)", ex)
        emptyPreferences()
    },
    produceMigrations = { ctx ->
        listOf(SharedPreferencesMigration(ctx, ProfileStoreMapping.LEGACY_PREFS_NAME))
    },
)

/**
 * 버블 켬/위치, 앱별 "마지막 성공 placement" 를 담는 DataStore 접근 계층 (P3-3).
 *
 * 왜 DataStore 인가: SharedPreferences 는 동기 API 라 메인 스레드 I/O 위험이 있고, 이 값들만
 * 예외적으로 SharedPreferences 를 썼던 이유(P3-1 KDoc "[미해결]" 메모)가 이제 해소됐다 — 이 클래스가
 * 프로젝트에 유일하게 남아 있던 getSharedPreferences 직접 호출을 전부 대체한다.
 *
 * 마이그레이션 계약: [fwaDataStore] 의 [SharedPreferencesMigration] 이 최초 접근 시 레거시
 * "bubble_prefs" 파일을 이관하고 삭제한다. 따라서 이 클래스 밖에서
 * `getSharedPreferences("bubble_prefs", ...)` 를 직접 호출하는 코드가 남아 있으면 안 된다 —
 * 이관 후에는 원본 파일이 없으므로 그런 코드는 조용히 빈 값만 읽는 버그가 된다.
 *
 * API 는 전부 suspend 스냅샷 함수다(Flow 미노출) — 호출부(FloatingLauncherService/BootReceiver/
 * OnboardingActivity/ArrangerAccessibilityService)가 전부 일회성 읽기/쓰기만 필요로 한다.
 *
 * [실기기 검증 리뷰 지적 반영, 2026-07-25] 호출부 중 다수(FloatingLauncherService 의 버블 활성/위치
 * 저장, ArrangerAccessibilityService 의 마지막 성공 placement 저장)가 결과를 기다리지 않는
 * fire-and-forget `scope.launch { ... }` 다. `DataStore.edit`/`.data` 는 디스크 풀·IO 오류 시
 * `IOException` 을 던지는데, launch 안에서 미처리 예외는 CoroutineExceptionHandler 가 없으면
 * 프로세스를 죽인다(레거시 `SharedPreferences.apply()` 는 호출자에게 절대 던지지 않았으므로
 * 이관 자체가 "조용한 유실"을 "크래시"로 악화시킬 뻔했다). 그래서 이 클래스의 모든 읽기/쓰기를
 * [safeRead]/[safeWrite] 로 감싸 계층에서 일괄 방어한다 — 개별 호출부(4곳)를 전부 고칠 필요 없이
 * 여기 한 곳에서 예외를 잡고 Log.w 로 드러낸 뒤 안전한 기본값으로 폴백한다. `CancellationException`
 * 은 코루틴 협조적 취소를 깨지 않도록 그대로 다시 던진다.
 */
class ProfileStore(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 읽기 실패(디스크 오류 등 — 손상 자체는 [fwaDataStore] 의 corruptionHandler 가 먼저 처리한다)를
     * [default] 로 폴백시키며 Log.w 로 드러낸다. CancellationException 은 취소 전파를 위해 재던진다.
     */
    private suspend fun <T> safeRead(default: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "DataStore 읽기 실패 — 기본값($default) 으로 폴백", e)
            default
        }

    /**
     * 쓰기 실패를 삼키고 Log.w 로 드러낸다(호출부는 fire-and-forget 이라 크래시하면 안 된다).
     *
     * [실기기 검증 리뷰 지적 반영, 2026-07-25] `withContext(NonCancellable)` 로 감싸는 이유: 레거시
     * `SharedPreferences.apply()` 는 호출자가 곧바로 종료돼도(액티비티 finish, 서비스 onDestroy 의
     * `serviceScope.cancel()`) `QueuedWork` 가 프로세스 생존 구간 동안 디스크 반영을 보장했다.
     * `DataStore.edit` 은 일반 suspend 함수라 스코프가 취소되면 커밋 도중이라도 즉시 중단될 수 있어,
     * 이관 전에는 없던 "쓰기 유실" 회귀가 생긴다(예: 버블 위치/활성 상태 저장 도중 서비스가 죽으면
     * 그 쓰기가 반영되지 않은 채 사라진다). `NonCancellable` 문맥에서는 [block] 이 이미 시작된 이상
     * 외부 취소와 무관하게 끝까지 실행되므로 apply() 와 동등한 "완료 보장" 의미론을 되찾는다.
     * 이 함수를 호출하는 모든 지점(FloatingLauncherService 의 버블 활성/위치 저장 2곳,
     * ArrangerAccessibilityService 의 마지막 성공 placement 저장, OnboardingActivity 의 버블
     * 활성 해제)이 한 번에 이 보장을 받는다. `CancellationException` 재던짐은 [block] 내부에서
     * (예: 중첩된 `withTimeout`) 명시적으로 발생한 취소류 예외를 취소가 아닌 일반 예외로 오인해
     * 삼키지 않도록 유지한다 — `NonCancellable` 자체는 외부 취소 신호를 차단할 뿐, 이런 예외의
     * 전파 경로를 바꾸지 않는다.
     */
    private suspend fun safeWrite(block: suspend () -> Unit) {
        withContext(NonCancellable) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "DataStore 쓰기 실패 — 무시(다음 성공한 쓰기가 있을 때까지 이전 값 유지)", e)
            }
        }
    }

    suspend fun isBubbleEnabled(): Boolean =
        safeRead(false) { appContext.fwaDataStore.data.first()[KEY_BUBBLE_ENABLED] ?: false }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        safeWrite { appContext.fwaDataStore.edit { prefs -> prefs[KEY_BUBBLE_ENABLED] = enabled } }
    }

    /** x/y 둘 다 저장돼 있어야 non-null 을 반환한다 — 하나만 있으면 좌표로서 의미가 없다 */
    suspend fun bubblePosition(): Pair<Int, Int>? = safeRead(null) {
        val prefs = appContext.fwaDataStore.data.first()
        val x = prefs[KEY_BUBBLE_X]
        val y = prefs[KEY_BUBBLE_Y]
        if (x != null && y != null) x to y else null
    }

    suspend fun saveBubblePosition(x: Int, y: Int) {
        safeWrite {
            appContext.fwaDataStore.edit { prefs ->
                prefs[KEY_BUBBLE_X] = x
                prefs[KEY_BUBBLE_Y] = y
            }
        }
    }

    /** target 패키지의 마지막 성공 배치 위치. 저장된 적 없거나 값이 오염/조회 실패했으면 null(호출부가 상위 폴백을 탄다) */
    suspend fun lastSuccessfulPlacement(packageName: String): Placement? = safeRead(null) {
        val key = stringPreferencesKey(ProfileStoreMapping.placementKeyFor(packageName))
        val raw = appContext.fwaDataStore.data.first()[key]
        ProfileStoreMapping.placementFromStorage(raw)
    }

    suspend fun saveLastSuccessfulPlacement(packageName: String, placement: Placement) {
        safeWrite {
            val key = stringPreferencesKey(ProfileStoreMapping.placementKeyFor(packageName))
            appContext.fwaDataStore.edit { prefs ->
                prefs[key] = ProfileStoreMapping.placementToStorage(placement)
            }
        }
    }

    private companion object {
        val KEY_BUBBLE_ENABLED = booleanPreferencesKey(ProfileStoreMapping.KEY_BUBBLE_ENABLED)
        val KEY_BUBBLE_X = intPreferencesKey(ProfileStoreMapping.KEY_BUBBLE_X)
        val KEY_BUBBLE_Y = intPreferencesKey(ProfileStoreMapping.KEY_BUBBLE_Y)
    }
}
