package dev.dj.foldwindow.platform

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * [측정 2026-07-25, Fold 7 / One UI 8 / targetSdk 36] 인스턴스 필드(`privateFlags`) 리플렉션은
 * hiddenapi `unsupported,test-api` 로 **allowed** (정상 동작 실측 확인).
 * static 상수 필드(`PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE`) 리플렉션은
 * hiddenapi `max-target-o` 로 **denied** — `NoSuchFieldException` 발생 실측 확인.
 *
 * 상수 리플렉션 실패는 더 이상 전체 판정 실패로 취급하지 않는다. 필드값(`privateFlags`)만
 * 확보되면 아래 [FALLBACK_UNRESIZEABLE_BIT] 하드코딩 값으로 비트 검사를 계속한다.
 * 향후 기기/버전에서 상수 리플렉션이 다시 허용될 가능성에 대비해 리플렉션 시도 자체는 유지한다.
 * 인스턴스 필드 리플렉션 자체가 실패하는 경우(필드 부재, 패키지 조회 실패 등)에만 null 을
 * 반환해 호출측(`ArrangerAccessibilityService`)이 DRAG 레시피로 안전하게 폴백하게 한다.
 *
 * DEVICE_FACTS.md 실측 근거: 넷플릭스가 이 플래그를 선언 → 드래그 레시피가 팝업(프리폼)으로
 * 라우팅되어 상하 분할-선택 진입 불가. MENU 레시피(회전 우회)가 유일한 진입 경로로 확정됨.
 */
object ResizeModeDetector {

    private const val TAG = "FWResizeModeDetector"
    private const val PRIVATE_FLAGS_FIELD_NAME = "privateFlags"
    private const val UNRESIZEABLE_CONST_FIELD_NAME = "PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE"

    // [측정 2026-07-25] 실기기 로그: package=com.netflix.mediaclient privateFlags=0x8c000910 bit=0x1000
    // unresizeable=false — 이전 1 shl 12 지정은 오판이었다. 0x8c000910 비트 분해 교차 검증:
    //   0x10  = 1 shl 4  = HAS_DOMAIN_URLS (dumpsys 명칭 일치)
    //   0x100 = 1 shl 8  = PARTIALLY_DIRECT_BOOT_AWARE (dumpsys 명칭 일치)
    //   0x800 = 1 shl 11 = ACTIVITIES_RESIZE_MODE_UNRESIZEABLE (dumpsys 명칭 일치)
    // AOSP ApplicationInfo.java 상수 정의: RESIZEABLE = 1 shl 10, UNRESIZEABLE = 1 shl 11,
    // RESIZEABLE_VIA_SDK_VERSION = 1 shl 12 — 즉 1 shl 12 는 VIA_SDK_VERSION 이지 UNRESIZEABLE 이 아니다.
    private const val FALLBACK_UNRESIZEABLE_BIT = 1 shl 11

    /**
     * @return true=UNRESIZEABLE 확정, false=리사이저블 확정, null=판정 불가(필드 리플렉션 실패,
     *   패키지 조회 실패 등). null 을 반환하는 것은 조용한 실패가 아니라 명시적 미판정 상태다 —
     *   호출측이 로그로 원인을 남기고 안전한 기본값(DRAG)으로 폴백한다.
     */
    // lint SoonBlockedPrivateApi/DiscouragedPrivateApi 억제: 폴백 비트(FALLBACK_UNRESIZEABLE_BIT)가
    // 이미 예외를 대응하므로 기능적 문제 없음 — 위 KDoc 실측 근거 참고(상수 리플렉션은 hiddenapi
    // max-target-o 로 denied 될 수 있으나 runCatching + 폴백값으로 이미 흡수됨).
    @Suppress("SoonBlockedPrivateApi", "DiscouragedPrivateApi")
    fun isActivitiesUnresizeable(pm: PackageManager, packageName: String): Boolean? =
        runCatching {
            val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)

            val privateFlagsField = ApplicationInfo::class.java
                .getDeclaredField(PRIVATE_FLAGS_FIELD_NAME)
                .apply { isAccessible = true }
            val privateFlags = privateFlagsField.getInt(appInfo)

            val unresizeableFlagValue = runCatching {
                ApplicationInfo::class.java
                    .getDeclaredField(UNRESIZEABLE_CONST_FIELD_NAME)
                    .apply { isAccessible = true }
                    .getInt(null)
            }.onFailure { e ->
                Log.w(
                    TAG,
                    "isActivitiesUnresizeable: 상수 리플렉션 실패(hiddenapi max-target-o denied 추정) " +
                        "— 폴백 비트 0x${FALLBACK_UNRESIZEABLE_BIT.toString(16)} 사용",
                    e,
                )
            }.getOrDefault(FALLBACK_UNRESIZEABLE_BIT)

            val unresizeable = (privateFlags and unresizeableFlagValue) != 0
            Log.i(
                TAG,
                "isActivitiesUnresizeable: package=$packageName " +
                    "privateFlags=0x%x bit=0x%x unresizeable=%b".format(
                        privateFlags,
                        unresizeableFlagValue,
                        unresizeable,
                    ),
            )
            unresizeable
        }.onFailure { e ->
            Log.w(
                TAG,
                "isActivitiesUnresizeable: 리플렉션/조회 실패 (package=$packageName) — 판정 불가, " +
                    "호출측 DRAG 폴백 기대",
                e,
            )
        }.getOrNull()
}
