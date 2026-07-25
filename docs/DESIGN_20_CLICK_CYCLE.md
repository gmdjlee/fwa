# DESIGN — #20 클릭-사이클 에스컬레이션 (step3 피커 탭 · PaneSwapper 전환 클릭)

> 2026-07-25 저녁 검토 확정. **구현 전 · 실기기 [미검증]**.
> 근거 분석: 코드 경로 감사 + 실측 증거 대조 + AOSP 소스 검증 (3 병렬) → 설계 심판 → Advisor 적대 검증.
> 대상 열린 질문: PROGRESS.md #20 (step3 피커 탭 변동성 · "창 전환" ACTION_CLICK 무효).

---

## 1. 근본 원인 (확정 수준별)

### [code-certain] AOSP 소스 검증 — ACTION_CLICK=true 는 성공 신호가 아니다

`View.performAccessibilityActionInternal` 의 ACTION_CLICK 처리:
```java
case ACTION_CLICK: { if (isClickable()) { performClickInternal(); return true; } }
```
- **true 는 isClickable 만 보장. `performClickInternal()` 반환값은 폐기된다.** 리스너 부재/무효과 여도 true.
- a11y 클릭은 **히트테스트·z-order·부모 onInterceptTouchEvent·onTouchEvent 전부 우회** — One UI Recents/분할-선택의 터치 파이프라인 기반 활성화 로직이 전혀 실행되지 않을 수 있다.
- 반대로 true 는 "창 live + 노드 shown" 은 보장 (창 전환 중 드롭 클래스는 false 반환) — 실측 실패가 전부 true 였으므로 클릭은 실제 뷰에서 실행됐고, 무효는 performClick **하류**에서 발생.
- 검증 소스: aosp-mirror/platform_frameworks_base — View.java, AccessibilityInteractionController.java, AccessibilityInteractionClient.java, MotionEventInjector.java (세션 스크래치패드 보관).

### [code-certain] 기존 코드의 구조 결함 3종

1. **제스처 폴백 = 죽은 코드**: `clickWhenFound` (SplitEntry.kt:624-663) 의 `tapNodeCenter` 폴백은 ACTION_CLICK **false 반환 시에만** 발동. 실측 실패 4건 전부 true 반환 → 폴백 실행 이력 0회. DividerPopupRotator 동형. PaneSwapper 는 폴백 자체가 없음.
2. **예산 소진 구조**: step3 시도 2600ms 중 클릭 ~200ms 후 잔여 ~2400ms 를 죽은 클릭의 단일 수렴 폴링에 전부 소진 → 전략2 는 예산 ~0ms 로 발화. PaneSwapper 도 단일 클릭 + ~2.5s 단일 폴링.
3. **LAUNCH_ADJACENT 자해**: 전략2 는 분할-선택 파괴 실측 + 구조 성공 0회 + 시도 1 의 전체화면 낙착이 시도 2·3 전제조건을 오염 → 3연속 동일 실패 결정론 (상태 머신은 같은 step 만 재시도, ArrangeStateMachine.kt:234-242).

### [inferred] 시그니처별 메커니즘

- **무효 클릭 (2회, 액티비티 생성 이벤트 없음)**: isClickable=true 인데 활성화가 터치 파이프라인 구동(리스너 없음)인 컨테이너에 도달, 또는 런처 내부 상태 가드가 리스너를 no-op 시킴.
- **startActivityFromRecents 오라우팅 (1회)**: `clickableAncestorOrSelf` (maxDepth 10) 가 일시적 non-clickable 피커 래퍼를 지나쳐 일반 Recents 태스크 컨테이너(클릭 = 전체화면 열기)에 도달했을 가능성. a11y 클릭은 노드 identity 로 라우팅되고 히트테스트를 안 하므로 이 클래스가 구조적으로 가능. 좌표 탭은 구조상 이 클래스 불가능 (손가락과 동일한 최상위 히트).
- **"창 전환" 무효 (2회, 회전 직후 한정)**: 같은 팝업의 회전 노드는 6/6 성공 → 팝업이 아니라 **회전 여파 컨텍스트**가 독. 전이 정착 전 시스템측 억제 추정 → 메커니즘 교체보다 **시간에 걸친 재시도**가 1차 해법.

### 제스처 탭 근거 상태

- 런처 창 대상 주입 실증: 핸들 탭 6/6, Recents 카드 홀드드래그 반복 성공. 유일한 경성 거부는 dismiss 깊이 **드래그** 한정 (FLAG_INJECTED_FROM_ACCESSIBILITY 선별 거부 추정).
- **피커 리스트 노드에 대한 제스처 탭 실측 데이터 = 0건** (폴백 미발동이라 미실행) — 반증 아님·미실행. 계측 필수.

---

## 2. 설계

### 2-1. 신규 순수 도메인 모듈 — `domain/ClickCyclePlan.kt`

```kotlin
enum class ClickMechanism { GESTURE_TAP, A11Y_ACTION }
data class ClickCyclePlan(val findSliceMs: Long, val verifySliceMs: Long,
                          val mechanisms: List<ClickMechanism>) {
    fun mechanismFor(cycle: Int) = mechanisms.getOrElse(cycle) { mechanisms.last() }
    companion object {
        val PICKER       = ClickCyclePlan(600, 800, [GESTURE_TAP, GESTURE_TAP, A11Y_ACTION])
        val POPUP_SWITCH = ClickCyclePlan(600, 800, [A11Y_ACTION, GESTURE_TAP, GESTURE_TAP])
    }
}
```

- **PICKER gesture-first 근거**: 최악 실측 조합(4/7 세션 실패) + 오라우팅 클래스가 a11y 전용 + 탭 6/6 인접 실증. ACTION_CLICK 은 최종 사이클로 유지.
- **POPUP_SWITCH a11y-first 근거**: 동일 팝업 계열 ACTION_CLICK 6/6 + 유튜브 스왑 성공 — 검증된 메커니즘을 검증된 컨텍스트에서 1순위 유지.
- 신규 상수 근거: verifySliceMs=800 — 실측 수렴 ~160ms 의 5배 + doubleTapTimeout(~300ms) 초과(연속 탭 더블탭 오인 방지). findSliceMs=600 — 통상 첫 150ms 폴에서 발견, 4폴 여유.
- **검증 상수 무변경**: POLL_INTERVAL_MS=150, TAP_DURATION_MS=50, entryStepTimeoutMs=3000/2600, SWAP_TIMEOUT_MS=3000, MAX_TAP_ATTEMPTS=3, POPUP_POLL_TIMEOUT_MS=700.
- JVM 테스트: 프로파일별 메커니즘 순서, mechanismFor 상한 클램프, verifySlice ≥ 400ms 불변식, 슬라이스 합 예산 초과 금지.
- **롤백 레버**: 메커니즘 순서 = 데이터. 실기기에서 gesture-first 신규 실패 클래스 발견 시 PICKER 를 `[A11Y_ACTION, GESTURE_TAP, GESTURE_TAP]` 으로 1줄 뒤집기.

### 2-2. SplitEntry.kt

신규 `clickUntilCondition(budgetMs, what, plan, find, condition)` — 사이클 0..2, 잔여 예산 내:

1. **디스패치 직전 선체크**: `condition()` true → 즉시 성공 (매 사이클. 늦은 정착·늦은 클릭 흡수)
2. `pollForValue(min(findSliceMs, remaining))` 로 노드 확보. 실패 → 다음 사이클
3. **스테일 가드**: `node.refresh()` false → phantom, 탭 생략·재폴링
4. 디스패치 (plan.mechanismFor(cycle)):
   - GESTURE_TAP: **오버레이 가드** 선행 — 자기 터치 가능 오버레이 존재 시 Log.e + 명시 실패. 이후 `tapNodeCenter(node)`
   - A11Y_ACTION: 기존 `clickableAncestorOrSelf` + ACTION_CLICK, false 면 같은 사이클 내 즉시 tapNodeCenter
5. **검증 슬라이스**: `pollUntil(min(verifySliceMs, remaining)) { condition() }` → 성공 시 종료
6. 3사이클 후 잔여 예산 tail poll → 실패 시 Log.w "budget exhausted after 3 cycles"

적용:
- `step3PlacePartner`: 진입 선체크 유지. 전략1+**전략2 블록 전체를** `clickUntilCondition(..., PICKER, ::findPanelPickerNode) { isSplitPairPresent(ctx) }` 로 교체. **LAUNCH_ADJACENT 삭제** (분할-선택 파괴 실측·구조 0회·예산 ~0ms 발화·재시도 오염원). `ctx.panelIntent` 자체와 타 사용처는 무변경.
- `menuStep4TapPartnerInPicker`: 동일 교체 (조건 = `isSplitPairPresentLeftRight`).
- **무변경**: menuStep2/3 (5/6+ 실측, 유일 실패는 해소된 잔존 태스크 함정), menuStep5, clickWhenFound 자체(잔여 호출자용), 전 셀렉터.

### 2-3. PaneSwapper.kt

- `swap()` 에 `dividerSettled: suspend (budgetMs) -> Boolean` 파라미터 추가 (서비스가 공급).
- **정착 게이트** (핸들 탭 루프 앞): 디바이더 bounds 2연속 폴 동일 = 정착. 실패 시 로그 후 **속행** (게이트는 best-effort, 사이클 루프가 안전망). 신규 `SETTLE_GATE_BUDGET_MS=800`. 조건 기반 — ADR-2 준수. 회전 여파 독 컨텍스트 직격 대응.
- 핸들 탭/팝업 탐색 루프: 무변경.
- **단일 performAction 블록 → 사이클 루프** (POPUP_SWITCH, 0..2):
  1. 선체크 `isSwapped()` — **involution 가드** (스왑 2회 = 원위치. 착지한 스왑에 재클릭 절대 금지)
  2. 매 사이클 스위치 노드 re-find. 팝업 소멸 ∧ !isSwapped → 핸들 재탭 1회 + 팝업 재폴 (재탭 직전 isSwapped **재확인** — 적대 검증 수정 ②)
  3. 사이클 0 = ACTION_CLICK / 1·2 = refresh + bounds + 제스처 탭 (`tapNodeBounds`, 빈 bounds 가드 포함)
  4. 검증 슬라이스 800ms (`SWAP_VERIFY_SLICE_MS`)
- 전략2 더블탭 폴백: 유지 (팝업 미발견 시에만 — 검토 범위 밖·무해).

### 2-4. ArrangerAccessibilityService.kt

- **포렌식 (무행동 변경)**: 세션 중 `TYPE_VIEW_CLICKED` 이벤트 로그 — `View.performClick` 은 리스너 유무 무관 이 이벤트를 무조건 발화 (AOSP 검증) → "잘못된/리스너 없는 뷰에서 실행" vs "실행 자체 없음" 판별. #20 잔여 미지 해소용. 이벤트 마스크에 TYPE_VIEW_CLICKED 추가 (가산적).
- `dividerSettled` 람다 공급: TYPE_SPLIT_SCREEN_DIVIDER bounds 2연속(150ms 간격) 동일 + 비어있지 않음.
- 타임아웃 무변경.

### 2-5. 무변경 (회귀 방어)

DividerPopupRotator (6/6 — 새 증거 없이 건드리지 않음) · ArrangeStateMachine 및 domain 재시도 의미론 전부 · DividerDragger · GestureDrags · config/window_profiles.json · 전 셀렉터. **스텝 되감기(재시도 시 step1/2 로 후퇴)는 이번 diff 제외** — 검증된 재시도 의미론 변경이라 독자 측정 캠페인 필요, 열린 질문 후속으로 기록.

---

## 3. Advisor 적대 검증 결과 (2026-07-25 저녁)

판정: **sound-with-amendments** (수정 3건 위 설계에 반영됨).

| 공격 | 결과 |
|---|---|
| ADR-2 위장 위반 | 통과 — 전 슬라이스 = 조건 폴링 창, 정착 게이트 = bounds 조건 |
| 스테일 bounds → 오탭 | 통과 — 매 사이클 re-find + refresh() + 빈 bounds 가드 |
| 이중 발화 (늦은 클릭 착지) | 통과 — 매 디스패치 직전 선체크 + involution 가드. 선체크↔디스패치 TOCTOU 수 ms 잔존(소) |
| One UI 피커 탭 거부 (미측정) | 통과 — 사다리가 ACTION_CLICK 으로 종결 + mech 로그로 표면화 |
| 예산 소진 | 조건부 — 통상 시도당 ~2.5 사이클. 최악 시 3번째 사이클 미실행 가능 → 해당 경로 [미검증] 표기 |
| 검증 경로 회귀 | 통과 — 회귀 표면 = step3/menuStep4/스왑 클릭 블록만. 프로파일 1줄 롤백 레버 |
| 세션 레벨 근본 원인 잔존 | **부분** — 시도 내 완화. 3시도 전멸 여전히 가능. 포렌식 로그로 원인 특정 후 스텝 되감기 재검토 (후속) |
| 조용한 실패 | 통과 — 전 실패 경로 로그 |

수정 반영:
1. **오버레이 가드 판정원** = in-process 상태 (`FloatingLauncherService` 창 부착 여부 등). a11y 창 목록 사용 금지 — touchable 플래그 미노출 + 자기 오버레이가 목록 자체를 오염 (함정 #25).
   **구현 후 확정 (2026-07-25 9차, qa 관찰 대응)**: 가드 발동 시 해당 사이클 스킵이 아니라 **`clickUntilCondition` 전체를 즉시 실패**시킨다 — 의도된 동작. 오버레이 존재는 제스처만의 문제가 아니라 세션 불변식 위반이며, 함정 #22 실측상 **ACTION_CLICK 경로도** 오버레이 존재 시 파트너 전체화면 낙착(A/B 2/2). a11y 사이클로 에스컬레이션하면 실측된 오라우팅 + 분할-선택 파괴로 직행하므로, 사다리를 계속 타는 것이 아니라 큰 소리로 즉시 실패하는 것이 옳다. 사이클-스킵으로 "수정" 하지 말 것.
2. **팝업 재오픈 분기** 재탭 직전 isSwapped 재확인 (비멱등 스왑 + #25 재구축 지연 TOCTOU).
3. **오라우팅 자기치유 상호작용 명시**: PanelActivity 자가 가드(비멀티윈도우 → finishAndRemoveTask)가 전체화면 낙착을 자동 정리 → 다음 사이클 선체크가 흡수. LAUNCH_ADJACENT 삭제로 오염원 자체 감소.

---

## 4. 로그 시그니처 (E2E 증거용, grep 안정)

```
clickCycle: [step3 panel-picker] cycle=%d mech=gesture|a11y dispatched=%b
clickCycle: [%s] converged cycle=%d mech=%s elapsedMs=%d     (선체크 수렴은 mech=none)
clickCycle: [%s] budget exhausted after 3 cycles
clickCycle: [%s] own touchable overlay present — tap would be swallowed
swap: settleGate ok in %dms | swap: settle gate timeout — proceeding
swap: switch-click cycle=%d mech=%s result=%b | swap: converged cycle=%d mech=%s
FORENSIC viewClicked pkg=%s cls=%s viewId=%s
```
mech= 필드로 매 런이 자기 계측 — 컨텍스트별 gesture vs a11y 승수 집계가 logcat 에서 직접 나옴.

## 5. 실기기 검증 프로토콜

- **Gate 0**: `testDebugUnitTest`(신규 ClickCyclePlan 테스트 포함) + `assembleDebug`. 재설치 후 접근성 재활성화 (함정 #6).
- **Gate 1 회귀 (n=3 each, 전부 green 유지)**: ① 유튜브 DRAG E2E (broadcast) — converged + residual=0 ② 넷플릭스 MENU E2E — step2~5 통과, 회전 경로 무변화.
- **Gate 2 독 컨텍스트 재현**: ① 유튜브 무override 연속 ≥5회 — ENTRY_STEP_FAILED 0건 기준 (과거 세션 실패율 ~50% → 연속 5회 무실패의 우연 확률 ~3%, 통계적으로 유의). cycle-0 미스 시 cycle-1/2 회복 로그 확인. FORENSIC 소스 비교. ② 회전 여파 스왑 ≥3회 (MENU + PaneSwapper 유발 배치) — 매회 수렴, settleGate 지연·승리 사이클 기록.
- **Gate 3**: logcat 집계 → DEVICE_FACTS 신규 절. 미실행 경로 명시 [미검증] (예상: 피커 cycle-2 a11y, 스왑 cycle-1/2, 팝업 재오픈 분기). PROGRESS #20/#25 갱신 + LAUNCH_ADJACENT 삭제 결정 로그.

## 6. 검토 방법론 기록

멀티에이전트 검토: 분석 3 병렬 완료 (코드 감사 / 증거 대조 / AOSP 소스 검증). 설계 3안·적대 검증 에이전트는 사용량 한도로 미실행 — 심판이 분석 도시에 기반해 후보 3안(A: 사이클 에스컬레이션 a11y-first / B: 계측 우선 / C: gesture-primary 전면)을 자체 구성·채점 (A 7.25 > C 6.93 > B 6.4), A 구조 + C 의 피커 한정 gesture-first·하드닝·B 의 계측을 접목. 적대 검증은 Advisor 직접 수행 (§3).
