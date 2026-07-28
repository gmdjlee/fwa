# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-28 밤 **17차 실기기 캠페인** — Phase 4 구현분 4종 검증 **실질 완료** (adb 주도, DEVICE_FACTS 17차 절): **P4-2 5/5** · **P4-4 4.5/5** · **P4-3 6/7** (신규 수단 `cmd device_state state 0/reset` 에뮬레이션으로 커버 게이트 전 경로 발화 — 핵심 미지수였던 "닫힘 중 패널 finish 의 분할 해소" 에뮬 기준 해소) · **P4-1 7/7** (Shizuku v13.6.0 adb 설치·스타터 활성 — 온보딩 3분기·메뉴 출현·재바인드·유튜브/넷플릭스 팝업 E2E bounds 정확·DRM 재생 Secure layer 팝업 내 구동). 부수: 12차 캐시 폴백 실전 2회 발동 실증. **⚠ 신규 중대 결함 발견**: step3 패널 소환 경로 부재 — 피커 「FW Panel」 노드 = recents 태스크 카드인데 `finishAndRemoveTask`(커버 해제·dismissSplit·purge·자가 가드)가 카드를 제거하면 배치 전체 불능 (node-not-found 3전멸, 재현 4회 + 원인 결정 실험 완료). purge 는 세션 시작마다 자충. 과거 캠페인은 카드 상시 잔존 우연에 의존했음. **P4-3 실배포 전 수정 필수** — DEVICE_FACTS 17차 「신규 결함」 절에 수정 방향 3후보
**직전(P4-1 프로브+구현):** 프로브 F1~F6 게이트 통과·후보 A(Shizuku 셸) 채택(`7e1d912`) → 구현 완료(UserService AIDL + PopupPlanner·StackListParser + startPopup 5단 폴링 + 메뉴 조건부 노출 + 온보딩 카드). 테스트 271·빌드 PASS. 그 전 Phase 4 구현(`9c36905`)·16차·15차 = DEVICE_FACTS 각 절 참조
**현재 Phase:** Phase 4 — **전 항목 구현 + 17차 검증 완료**. 잔여 = Phase B 물리 3건(아래 1번) + step3 소환 결함 수정. Phase 3 완료 확정
**다음 행동 (착수 목록):**
1. **step3 패널 소환 결함 수정** (최우선 — P4-3 활성 상태의 일상 재생산 결함): 후보 ① step3 전 패널 프리론치(런처 MRU 갱신, split-select 진입 전) ② purge 를 Done 후로 이동 ③ 피커 앱그리드 폴백. DEVICE_FACTS 17차 「신규 결함」 절 = 브리프 원천
2. **Phase B 물리 확인 3건** (사용자 조작): P4-3 항목1·2 물리 접기(화면 꺼짐 실상태) · P4-1 DRM 육안(팝업 재생 실화면) · (참고) 600ms 재펴기는 미검증 수용
2. P3-5 발 v1.5 후보: 포그라운드 안정성 윈도(폴드 전환 중 월렛 quick 카드가 event-tracked 오염 → 게이트5 통과 실측) · 재열기 멈칫 Done-후 분할 잔존(수동 해제로 복구, 키가드 게이트는 정당 사용례 훼손으로 기각) · 각도 대역 경계값 실측
3. #12 v1.5 후보 축적분: BOTH_AXES_BARS 시 보정 생략(G1 드리프트 실측) · 적응형 residual(글로우 필러박스 블라인드 G5 실측) · 비-16:9 콘텐츠 합치·캐시 실측 · flex 게이트2↔startArrange TOCTOU(이론상, DEVICE_FACTS 기록)
4. P3-2 잔여 [미검증]: dismissSplit 인텐트 폴백(instance null 희귀 경로). 회전×2 폴백 검증 기회 확보
5. #20 잔여 미발동 경로는 자연 발생 대기 (mech 로그 상시 계측) — 3시도 전멸 재발 시 FORENSIC viewId 로 특정 후 스텝 되감기 재검토

**개발 환경 주의 (실측 누적)**: Git Bash 에서 gradlew 는 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` 프리픽스 필수 — 없으면 installDebug 가 조용히 실패해 구버전 APK 로 검증하게 됨 (실제 발생, 40분 소모). screencap 은 `-d 4630946449689556883` (멀티 디스플레이). adb 롱프레스 시뮬레이션은 `input swipe x y x y 1200` (700ms 는 경계 실패). **10차 추가**: Git Bash 가 `/sdcard/...` 인자를 로컬 경로로 변환해 파괴 — 원격 명령은 `adb shell "cat /sdcard/x"` 처럼 통째 인용, `adb pull` 은 `MSYS_NO_PATHCONV=1` 프리픽스. E2E 리셋 = 패널 페인 탭+`keyevent 4` (PanelActivity finish → 상대 앱 전체화면 복귀). 유튜브 상태 셋업 = `am force-stop` 후 `am start -a VIEW -d '...watch?v=aqz-KE-bpKQ&t=120'` (기존 태스크는 딥링크 미라우팅) → 컨트롤 탭 → 전체화면 버튼 (2184×1968 가로에서 ≈1466,858). 회전 강제 = `accelerometer_rotation 0` + `user_rotation 1` (외부 요인으로 리셋되니 캠페인 중 재확인). **13차 추가**: Shorts 진입이 포트레이트 강제 + 잠금 무시하며, 복귀 후 settings put 만으론 WM 재평가 안 됨 — `adb shell cmd window user-rotation lock 1` 이 즉시 적용 (`free` 로 해제). pre-null(측정 실패) 상태 유도 = 세로 직캠(MPD직캠 검색) immersive 전체화면 — 다크 UI 는 상태바/엣지 순흑 행 때문에 pre 가 항상 후보 생성(한쪽 밴드 0 허용). 종료 대기 = `adb logcat -s FWArranger -e "arrange (done|failed)" -m 1`. **15차 추가**: 13차 `user-rotation lock` 잔재가 남아 있으면 FoldingFeature orientation 이 물리 자세와 무관하게 VERTICAL — 폴드 검증 전 `cmd window user-rotation free` 확인 필수. 재설치 후 `settings put secure enabled_accessibility_services` 를 **동일 값**으로 put 하면 no-op (서비스 재기동 로그 안 나옴) — 기동 로그 관찰하려면 `none` 으로 토글 후 재설정.
**실기기 검증 절차 (참고):**

```bash
./gradlew :app:installDebug
# 재설치 후 접근성 재활성화 필수 (함정 #6). probe 병행 시 콜론으로 연결
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService
# 유튜브 가로 전체화면 재생 상태에서 (⚠ -n 필수 — 액션만으로는 implicit broadcast 제한으로 수신 안 됨, 7차 실측):
adb shell am broadcast -a dev.dj.foldwindow.ARRANGE -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver --es placement top
# 하단 배치: --es placement bottom / 종횡비 강제: --ef aspect 1.7778 / 취소: --ez cancel true
```

---

## Phase 상태

| Phase | 상태 | 비고 |
|---|---|---|
| Day 0 수동 검증 | ✅ 완료 | #1·#2·#3 통과, #4는 대체 불가로 판정 |
| 부트스트랩 | ✅ 완료 | AGP 8.11.1 / Kotlin 2.1.0 / Gradle 8.13 wrapper / compileSdk 36. assembleDebug·testDebugUnitTest 통과 (32/32) |
| Phase 0 프로브 | ✅ 완료 | 3회 실행(전체화면/분할활성/가로영상). #5 ✅ #6 ❌ #7 ✅(분할 중에만). E는 유튜브 앰비언트 모드로 미검출 → Detector v2 필요. `docs/DEVICE_FACTS.md` 확정 |
| Phase 1 도메인 | ✅ 완료 | P1-1·P1-2·P1-3·P1-4·Detector v2 전부 완료. 전체 91 테스트 통과, qa-verifier PASS. 완료 기준 3항목(16:9 잔여 0px / 상태머신 실패 경로 / JSON 로더 거부) 전부 테스트로 입증 |
| Phase 2 액추에이터 | ✅ 완료 | DoD ① 검은띠0 ✅ ② 넷플릭스 ✅(MENU 레시피 E2E 6회, 4.7초, 디바이더 1235 정확 — 단 재생은 "배치 후 시작" 순서 필요) ③ 상하전환 ✅ ④ 실패노출 ✅. 131 테스트. 유튜브 DRAG 회귀 ✅ (1차 실패→step2 수정→2차 통과, 4.2초 residual=0). 잔여: 회전×2 폴백 미발동(미검증) |
| Phase 3 UI | ✅ 완료 | DoD 3항목(콜드부팅 버블 복귀 · 프로파일 유지 · 권한 미부여 무크래시 안내) 실기기 검증 완료. P3-1 버블 ✅ + P3-4 온보딩 ✅ (실기기 E2E: 버블 탭→배치 4.1초, 버블 숨김/복원 검증). P3-2 확장 메뉴 ✅ **실기기 E2E 완료** (메뉴發 배치 verified·분할 해제 성공·재탭 닫기만·프리셋 렌더·가로/세로 클램프 — 결함 3건 발견·수정·재검증, DEVICE_FACTS P3-2 절). P3-3 DataStore ✅ **실기기 검증 완료** (141 테스트 + 7차 실기기 #26 5항목 전부 — 이관·goAsync 부팅·placement 복원 E2E·corruption 복구·중지 레이스). P3-5 FoldingFeature ✅ **15차 실기기 검증 5항목 전부 통과** (결함 2건 현장 수정: UiContext 3-인자 채택·닫기 오발화 2층 방어. qa PASS 230/230, DEVICE_FACTS 15차 절) |
| Phase 4 확장 | 🔄 검증 완료·결함 수정 대기 | P4-1·2·3·4 구현 + **17차 실기기 검증 완료** (P4-2 5/5 · P4-3 6/7 에뮬 · P4-4 4.5/5 · P4-1 7/7, DEVICE_FACTS 17차 절). 잔여 = **step3 패널 소환 결함 수정(필수)** + Phase B 물리 3건(물리 접기·DRM 육안) |

## 작성된 코드

| 파일 | 상태 |
|---|---|
| `domain/SplitPlanner.kt` | ✅ P1-1 반영. `foldSevenLandscape()` = divider 14px / minPane 181px (실측). 테스트 22개 |
| `domain/LetterboxDetector.kt` | ✅ v2 하이브리드. 순흑(0.97) 우선 → luma 통계 기반 적응 폴백(`ADAPTIVE_*` 상수 4종). `resolveAspect` 진입점 불변. 11차 #12: `resolveAspectPillarbox` 열축 역산(band.height/scan.width) 추가 — 기존 함수 무변경. 테스트 37개 |
| `domain/MeasurementConsensus.kt` | ✅ 11차 #12 신규 + **실기기 검증** (G1~G5, 판정 6경로 발동 실증). 순수 Kotlin — 2-샷 합치 판정 (`classifyAxis`/`classifyConfirm`/`agree`, verdict 9종, DESIGN_12 §3.3 코드화). classifyAxis minConfidence(0.25) — 크롬 그라디언트 conf 0.08 유사 밴드 오승격 차단 (실측 대응). 테스트 25개 |
| `domain/FlexModePolicy.kt` | ✅ 14차 신규 → **15차 각도 게이트 확장 + 실기기 검증**. 순수 Kotlin — 800ms 디바운스·진입당 1회 arm·이탈 disarm 유지 + `onHingeAngle`/`isAngleStable`(대역 45~135 ∧ 침묵≥600ms ∨ 600ms 윈도 스프레드≤8°, 센서 무가용 시 통과 격하, 불안정 시 armed 유지). 근거: 닫기 체류 실측 ~2s(3표본) — 시간 단독 판별 불가. 테스트 24개 |
| `domain/CoverDismissPolicy.kt` | ✅ P4-3 신규. 순수 Kotlin — armed(비UNKNOWN 관측 후) 상태에서 UNKNOWN 진입 시 600ms 디바운스 예약 → 발화 시점 `shouldDismissNow` 재검증 → 에피소드당 1회 래치, 콜드스타트 UNKNOWN 보호, 재열림 시 재-arm. 테스트 14개. 실기기 미검증 |
| `platform/FoldStateMonitor.kt` | ✅ 14차 신규 → **15차 실기기 판정 반영**. 후보 체인 = ①서비스 자신(One UI 8 거부 — assertUiContext, 타 OS 대비 유지) ②**3-인자 `createWindowContext(display,...)` 실채택**(방출 수신 확인, SDK 31 가드) ③createDisplayContext 체인(예비). 구 2-인자 후보는 생성 불가 판명·삭제. 커버 디스플레이 기하(1080×2520)로도 방출 지속 실측 |
| `platform/HingeAngleMonitor.kt` | ✅ 15차 신규 **실기기 검증**. `Sensor.TYPE_HINGE_ANGLE` 래퍼 (Fold 7 노출 확정 — 도 단위, 노트북 ≈90.0, 닫힘 0.0, on-change 방출). start/stop 멱등, arm 수명주기 연동(배터리 위생), 센서 부재 시 Log.w 1회 후 무동작 |
| `domain/ArrangeStateMachine.kt` | ✅ P1-4 + `closedLoopCorrection` 플래그 (false 면 ADR-5 보정 생략, 잔여 정직 보고 — PROFILE 소스 오보정 실측 대응). 순수 리듀서, 시간은 이벤트 nowMs 만(ADR-2). 테스트 24개 |
| `domain/ClickCyclePlan.kt` | ✅ #20 신규 (9차) **10차 실기기 검증**. 순수 Kotlin — 클릭 메커니즘 사이클 계획. `PICKER`(gesture-first)/`POPUP_SWITCH`(a11y-first) 프로파일 = 데이터(1줄 롤백 레버), `mechanismFor` 클램프, verifySlice≥400ms 불변식. 테스트 12개. 실기기: 피커 cycle-0 14/15·cycle-1 회복 1회, 스왑 cycle-0 4/4 (cycle-2/스왑 제스처 사이클 미발동 [미검증]) |
| `domain/Profiles.kt` | ✅ P1-2 신규. `AspectSource`/`PartnerMode`/모델 4종 + `validate()` 위치 특정 에러. 순수 Kotlin. 12차 §6: `AspectSource.CACHED`(리졸버 출력 전용, JSON 금지)·`defaults.cacheMeasuredAspect` 레버·MIN/MAX_ASPECT 공개 승격(값 불변, 캐시 오염 검증 공유). 14차 P3-5: `defaults.flexAutoTopPlacement` 레버(부재=true). P4-3: `defaults.coverAutoDismiss` 레버(부재=true) |
| `domain/AspectResolver.kt` | ✅ P1-3 신규 + 12차 §6 확장. ADR-1 폴백 4단(PROFILE→MEASURED→**CACHED**→PRESET) — `cachedAspect` 기본 null 파라미터라 기존 호출부 무영향, 유효 측정이 캐시를 절대 못 이김. `DEFAULT_MIN_MEASUREMENT_CONFIDENCE=0.25f`. 테스트 15개 |
| `data/WindowProfilesParser.kt` | ✅ P1-2 신규. kotlinx-serialization DTO→domain 매핑, 예외 누출 없이 `ProfilesParseResult`. 11차 #12: `requireMeasurementAgreement` 토글 (키 부재=true, 1줄 롤백 레버 — 시드 JSON 무수정). 12차 §6: `cacheMeasuredAspect` 토글 동형 추가. 14차 P3-5: `flexAutoTopPlacement` 토글 동형 추가. P4-3: `coverAutoDismiss` 토글 동형. 테스트 24개(실제 SSOT 파일 파싱 포함) |
| `data/ProfileStore.kt` | ✅ P3-3 신규. Preferences DataStore(`fwa_store`) 래퍼 — 버블 enabled/x/y + 앱별 마지막 성공 placement. `SharedPreferencesMigration("bubble_prefs")` 무손실 이관(레거시 키 이름 동결 계약), `ReplaceFileCorruptionHandler`→emptyPreferences(부팅 크래시 루프 방지), 쓰기 = `safeWrite` NonCancellable(레거시 apply() 의 종료 후 반영 보장 대체), 읽기 = safeRead 예외 방어. **7차 실기기 검증**: 이관 무손실·corruption 복구·NonCancellable 완주 (DEVICE_FACTS P3-3 절). 12차 §6: `measuredAspect`/`saveMeasuredAspect` (키 `measured_aspect.<pkg>`, 저장측도 범위 검증 후 거부 — 이중 방어) → **13차 실기기 검증** (pb 키 실물 판독, 레버 OFF 중 보존 확인). P4-2: `panelWidgetMode`/`panelMemo` (저장측 허용집합 재검증·절단, safeWrite 경유) |
| `data/ProfileStoreMapping.kt` | ✅ P3-3 신규. 순수 Kotlin 매핑 — placement 직렬화 왕복(오염값→null, 크래시 금지)·키 네임스페이스·레거시 키 상수. 12차 §6: `measuredAspectKeyFor`·`aspectFromStorage`(NaN/∞/1.0..4.0 밖 → null, domain 공개 상수 공유). JVM 테스트 18개 (키 이름 동결 회귀 테스트 포함). P4-2: 위젯 모드 허용집합 검증·`PANEL_MEMO_MAX_CHARS=4000` 절단 (테스트 29개) |
| `platform/ScreenshotSampler.kt` | ✅ v2 확장. 행별 luma 평균/분산 산출 추가. 11차 #12: `toPillarboxScan` 열축 전치판 (entries=열, width 자리=height/colStride — 역산 좌표계 계약) + **실기기 검증·현장 튜닝 2건** — sideMarginPct 0.005(최외곽 열 var 404~501: 코너 누출+엣지 렌더링 물증), edgeMarginPct 0.12(플레이어 크롬 y-대역 제외). 수정 후 글로우 밴드 214/214 대칭 3/3 재현. 기존 `toLetterboxScan` 무변경 |
| `domain/PaneGeometry.kt` | ✅ P2-2 + 확장. 가시 교집합·간격 휴리스틱·상하/좌우 분할 판정·분할선택 페인 판정·`pickPaneLike`(최소화 플레이어 팝업 오염 필터, 실기기 근거). 순수 Kotlin. 테스트 30개 |
| `platform/DividerLocator.kt` | ✅ P2-2. `TYPE_SPLIT_SCREEN_DIVIDER` 핸들 중심 1차 → PaneGeometry 간격 휴리스틱 폴백. 실기기 미검증 |
| `platform/DividerDragger.kt` | ✅ P2-4 **실기기 검증**. SINGLE_STROKE 기본(확정). HOLD_THEN_MOVE 는 GestureDrags 위임 |
| `platform/PaneSwapper.kt` | ✅ 9차 #20 재구성 → **10차 실기기 검증** (회전 여파 컨텍스트 4/4 수렴 — 과거 무효 2회 실측 컨텍스트 동형 전승): 정착 게이트(`dividerSettled` 주입, 핸들 bounds 2연속 동일, 실측 ~154ms ok) + 스위치 클릭 = POPUP_SWITCH 사이클(a11y→gesture→gesture, involution 가드·팝업 소멸 시 재탭 전 isSwapped 재확인·검증 슬라이스 800ms — cycle-0 a11y 로 전승, cycle-1/2·재탭 분기 [미검증]). 핸들탭/팝업탐색 루프·더블탭 폴백 무변경. 실패 시 서비스 회전×2 폴백 유지(미발동) |
| `platform/SplitEntry.kt` | ✅ P2-3 **실기기 검증** (DRAG·MENU 양 경로). `EntryRecipe` 분기: DRAG 3단계(유튜브 회귀 E2E 통과) / MENU 5단계(UNRESIZEABLE 전용, E2E 6회 성공). 2026-07-25 오후 2차: step2 유령 매치 재폴링 + 목표 상태 선체크 수정. 9차 #20 → **10차 실기기 검증**: step3/menuStep4 = `clickUntilCondition` PICKER 사이클(gesture→gesture→a11y) 재작성 — 15세션 cycle-0 gesture 14회(176~362ms)·cycle-1 회복 1회(오착지 FORENSIC 특정), ENTRY_STEP_FAILED 0 (과거 무override ~50% 실패 → 5연속 무실패). **LAUNCH_ADJACENT 전략2 삭제** 무회귀 확인. cycle-2 a11y·오버레이 가드 발동 [미검증]. menuStep2/3/5·step1/2·셀렉터 무변경 |
| `platform/ResizeModeDetector.kt` | ✅ 신규 **실기기 검증**. `privateFlags` 필드 리플렉션 allowed / 상수 리플렉션 denied(max-target-o) → 폴백 비트 **1<<11** (0x8c000910 교차 검증). 실패 시 null → DRAG 폴백 |
| `platform/DividerPopupRotator.kt` | ✅ 신규. 핸들 탭→"시계 방향으로 회전" 클릭 공용화 (MENU step5 + 서비스 회전×2 폴백). step5 경로 실기기 검증, 회전×2 폴백은 미발동 **미검증** |
| `platform/GestureDrags.kt` | ✅ 신규·실기기 검증. 2-페이즈 홀드드래그(1px 드리프트 홀드 + continueStroke 이동, 타이밍 가드). API 함정 4종 문서화 |
| `service/ArrangerAccessibilityService.kt` | ✅ **실기기 검증** (유튜브+넷플릭스). 상태 머신 구동, 레시피 선택 배선, pre-measure **페인 크롭**, `pickPaneLike` 위치 판정, `purgeStalePanelTasks`, 스왑 실패 시 회전×2 폴백, `closedLoopCorrection` 배선(PROFILE 시 off), 세션 `dragTimeoutMs=12s` 오버라이드. P3-2 `dismissSplit()` **실기기 E2E 검증**: 디바이더 드래그 아님(반증) — `PanelActivity.instance.finishAndRemoveTask()` + 인텐트 폴백[미검증], 진입 체크 = `isSplitActive` 자체 2s 조건 폴링(스크림發 a11y 목록 비원자 재구축 대응), 150ms/3s 해소 폴링, 실패 전부 토스트. `awaitWindowsSettled()` = beginSession freshness 게이트. P3-3: placement 결정 체인에 `ProfileStore.lastSuccessfulPlacement` 2순위 삽입(override>last-success>profile>defaults>TOP, placementSource 로그), Done ∧ effective==desired 시에만 저장(지역 캡처 후 launch) — **7차 실기기 E2E 검증** (OVERRIDE 저장 → LAST_SUCCESS 복원, residual=0). 9차 #20 → **10차 실기기 검증**: `awaitDividerSettled` 공급(4/4 ~154ms ok), `TYPE_VIEW_CLICKED` 포렌식 수신 검증(성공 클릭=FrameLayout/null·오착지=icon_container·카드=task_icon 판별 실증), 오버레이 가드 람다 주입(발동 [미검증]). 11차 #12: `confirmMeasuredAspect` 합치 게이트 (handleDragDividerTo 선두, 세션 1회, MEASURED 만 발동, 레이트리밋 조건부 대기, realTargetY 덮어쓰기 선례 재사용 — 머신 무변경), aspectOverride tier 0 (측정·보정 전부 생략), verify residualCols 로그 병기(MeasureResult 인자 불변), `logMeasurement` 밴드 기하 상시 기록 — **실기기 G1~G5 통과** (9/9 done, DEVICE_FACTS 11차 절). 12차 §6 캐싱 배선: beginSession 캐시 조회(override 세션 생략)+decision 로그 `cachedAspect=`, finishConfirm 불합치 폴백 cached→preset, reportTerminal Done 에서 admission(합치∧verified∧레버) 저장, cleanupSession 3필드 리셋 → **13차 실기기 4항목 전부 검증** (저장·폴백/decision 양 지점 CACHED 낙착·confirm 미실행·레버 회귀, DEVICE_FACTS 13차 절). 14차 P3-5: `FoldStateMonitor` 구동(onServiceConnected, 기본 런처 해석 포함) + `onFoldPosture` 디바운스 예약(delay 후 shouldTriggerNow 재검증 — ADR-2 예외 요건 충족) + `evaluateFlexAutoTrigger` 5단 게이트(거부 시 disarm·토스트 없음) + placement 체인 FLEX 티어 + `sessionPlacementSource` 세션 필드(cleanupSession 리셋) + FLEX 세션 last-success 저장 억제 — 15차 실기기 검증 통과. P4-3: `coverPolicy` 배선(onFoldPosture 의 flex 조기 return 앞 삽입, UNKNOWN 디바운스→`evaluateCoverAutoDismiss` 게이트 4종→패널 직접 finish, dismissSplit 미경유·토스트 없음). P4-4: `foregroundPackageForExport`/`startArrangeWhenForeground`(150ms/5s 사전 조건 폴링 — 머신 밖, placement 는 기존 체인) — P4 분 실기기 미검증 |
| `service/ArrangeTriggerReceiver.kt` | ✅ adb 디버그 트리거 (`dev.dj.foldwindow.ARRANGE`). 버블 도입 후에도 회귀용으로 유지 |
| `service/FloatingLauncherService.kt` | ✅ P3-1 + P3-2 확장 메뉴 **실기기 E2E 검증**. 탭=배치, 드래그=이동+스냅, **롱프레스=메뉴 열기**(위/아래 배치·분할 해제·프리셋(JSON SSOT 파싱 캐시)·설정). 메뉴 = **풀스크린 투명 스크림** FrameLayout 창 (ACTION_OUTSIDE 방식은 디스패치 순서 경합 실측으로 폐기 — 스크림이 모든 터치 선점, 재탭=닫기만 구조 보장). 함정 #22: 모든 트리거 직전 + `setBubbleHiddenForArrange(true)` 시 메뉴 제거. 위치/켬 상태 P3-3 에서 `ProfileStore`(DataStore) 이관 완료 — 초기 위치는 onCreate 1회 runBlocking 스냅샷(runCatching 폴백), 쓰기는 serviceScope.launch(store 계층 NonCancellable). 9차 #20: `bubbleAttached`/`menuView` @Volatile + companion `hasAttachedOverlayWindow()` (제스처 탭 오버레이 가드 판정원). 16차: `HIDE_SAFETY_TIMEOUT_MS` 30s→90s (이론 최악 ≈70s 근거) + 제스처 실사용감 검증 (오분류 0). P4-4: 메뉴 「앱 페어 바로가기 만들기」+`exportAppPair`(dismissMenu 선행·2s 식별 폴링·`requestPinShortcut` 지원 체크) — 실기기 미검증 |
| `service/BootReceiver.kt` | ✅ P3-1 신규 + P3-3 개편. BOOT_COMPLETED 시 bubble_enabled+오버레이 권한 확인 후 FGS 재기동. 실부팅 검증은 구 동기 prefs 코드로 통과(5차) — P3-3 에서 goAsync+IO 코루틴(finally finish 보장)으로 재작성 → **7차 실부팅 재검증 통과** (로그·FGS 기동·버블 가시, 회귀 해소) |
| `ui/OnboardingActivity.kt` | ✅ P3-4 신규 **실기기 검증** (권한 감지·버블 토글·안내 렌더 확인). 권한 카드 3종 + 버블 시작/중지 + 사용 안내 (넷플릭스 "배치 후 재생" 포함). MAIN/LAUNCHER 진입점. P3-3: 중지 = NonCancellable(enabled=false 쓰기→stopService, 액티비티 파괴에도 완주) + stopInProgress 재진입 가드 — **7차 근사 + 16차 물리 폴드 접기 실기 검증** (양 경로 시퀀스 완주). 16차: 알림 권한 플로우 전 구간 검증 (회수 감지·다이얼로그·허용 즉시 반영) |
| `ui/PanelActivity.kt` | ✅ P2-5 + P3-2. 검정 배경+시계 파트너 창. 라벨 "FW Panel" = SplitEntry 피커 셀렉터 계약. P3-2: `instance` 정적 참조 + `EXTRA_FINISH_PANEL` (dismissSplit 의 패널 finish 경로 — finish 실기기 검증, 인텐트 폴백 [미검증]). MAIN/LAUNCHER 노출은 Phase 3 재검토. P4-2: 위젯 3종(시계/메모/검정)+하단 모드 버튼 상시 표시, 메모 500ms 디바운스+ON_PAUSE flush(DisposableEffect — Activity 메서드 무접촉) — 기존 자가 가드·라벨 계약 무변경 (실기기 미검증) |
| `ui/PanelWidgetMode.kt` | ✅ P4-2 신규. UI 표시 선호 enum(CLOCK/MEMO/BLACK)+`fromStorage` CLOCK 폴백 — 도메인 `PartnerMode{BLACK}`(JSON 스키마 소속)과 의도적 분리 |
| `ui/PairShortcutActivity.kt` | ✅ P4-4 신규. 트램펄린 — extra 검증→접근성 확인(꺼짐 시 온보딩 유도)→대상 앱 실행(NEW_TASK)→`startArrangeWhenForeground`→finish, 전 구간 runCatching. exported+excludeFromRecents+noHistory+전용 taskAffinity+반투명 테마. 실기기 미검증 |
| `probe/ProbeAccessibilityService.kt` | ✅ 실기기 검증 완료 (3회 실행) |
| `probe/ProbeReport.kt` | ✅ 완성 |
| `probe/ProbeActivity.kt` | ✅ 실기기 검증 완료 |
| `probe/ProbeTriggerReceiver.kt` | ✅ 신규. adb 브로드캐스트로 프로브 트리거 (`RUN_PROBE`). Phase 0 이후 제거 대상 |
| Gradle / Manifest / 접근성 XML | ✅ 부트스트랩에서 확정. AGP 8.11.1, Gradle 8.13 wrapper, `org.gradle.java.home`=Android Studio JBR(머신 종속 경로 주의) |

## 열린 질문

1. `ScreenshotSampler` 의 `rowStride` 축소가 종횡비 역산 정밀도에 미치는 영향 — 실측으로 확인
2. ~~드래그 전략 비교~~ → **SINGLE_STROKE 확정** (실기기: 단일 스와이프로 984→1236 정확 이동). HOLD_THEN_MOVE 는 GestureDrags 로 재구현돼 카드 드래그 진입에만 사용
3. wavve 패키지명 확인 필요
4. ~~minPaneHeight 실측~~ → 세로 좌우 분할 181px 확정. 가로 상하 분할은 미검증 (DEVICE_FACTS 참조)
5. ~~LetterboxDetector v2 설계~~ → 구현 완료. 남은 것: `ADAPTIVE_*` 상수(분산≤400, luma≤90, ref±28)의 실기기 재검증 — 앰비언트 영상에서 프로브 E 재실행해 16:9 스냅 확인. 순흑 조건(넷플릭스 등) E 실측도 아직 0건. `ScreenshotSampler`의 luma 통계 산출도 실기기 미검증
6. Recents 분할 진입 셀렉터의 다국어 안정성 (한국어만 검증됨). MENU 레시피 추가로 대상 확대: `SPLIT_MENU_TEXT_EN`·`ROTATE_DESC_EN` 도 [미검증]
7. ~~`AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE = 0.25f` 튜닝~~ → **접근 자체 기각** (11차 #12 검토): 오염 conf 0.60~0.97 ≥ 정상 ADAPTIVE 상한 0.6 — 어떤 임계도 오염/정상 분리 불가. 0.25 는 "후보 자격" 게이트로 역할 격하·값 유지, 채택은 합치 게이트가 결정 (DESIGN_12 §3.3)
8. ~~PaneSwapper 셀렉터~~ → **"창 전환" 실측 확정** (팝업 노드 3종: "App pair 추가 위치"/"창 전환"/"시계 방향으로 회전"). 하단 배치 E2E 성공
9. ~~step 파트너 경로~~ → **피커 노드 탭이 1차 확정**. LAUNCH_ADJACENT 는 분할 선택 상태를 파괴(전체화면 강탈)해 최후 폴백으로 강등
10. ~~`defaults.closedLoopCorrection` JSON 토글 미배선~~ → **배선 완료** (2026-07-25 오후). 추가 규칙: aspectSource=PROFILE 이면 무조건 보정 생략 (오염 측정이 프로파일을 덮어쓰는 실측 사고 2회 대응)
11. 대상 앱 라벨 조회 — `<queries>` 블록으로 실기기 정상 동작 확인 (label=YouTube 조회 성공)
12. 사전 실측 오염: 플레이어 컨트롤 오버레이/앰비언트 글로우가 떠 있으면 종횡비 오측 (1.333/1.12 관측. **추가 실측 2026-07-25 오후 2차: 영상 시작 직후 탭 → 추천화면/인트로 오염 1.6 오측(conf 0.60), 어두운 장면이라 verify residual=0 오판**). → **11차 해소 완결**: 검토→구현(qa PASS)→**실기기 G1~G5 전부 통과** (DEVICE_FACTS 11차 절). 2-샷 합치 게이트 (`docs/DESIGN_12_MEASUREMENT_CONSENSUS.md`) — 사고 클래스 3종(엔드스크린 conf 0.70·컨트롤 snap 1.5·과거 1.6) 전부 차단 실증, 클린 경로는 SNAP_AGREE→MEASURED 3/3. 잔여 = v1.5 후보(다음 행동 2번)와 좁은 갭(재트리거 시 디바이더 이미 목표 4px 이내면 Dragging 스킵 → confirm 미발동 — pre 단독 = 종전 동작, 회귀 아님)
13. 필러박스 맹점: 과소 이동 시 `residualBars=0` 으로 verified 오판 가능. → 열축 도메인 로직이 #12 confirm 측정과 동일 기반 — v1 에서 `residualColumns` **로그 보고까지** 동봉, `verified` 의미론 반영은 v1.5 (DESIGN_12 §3.4·§7). **11차 실측 보강**: 순흑 residual 은 글로우 필러박스에 블라인드 확정 (G5: 16:9-in-21:9 실재 필러박스에서 residualCols=0) — v1.5 는 적응형 residual 필요
14. BOTTOM 배치 최적화: 현재 상단 도킹 후 "창 전환" 스왑. step2 드롭 지점을 하단 가장자리로 바꾸면 스왑 생략 가능한지 실기기 확인 — Phase 3
15. ~~step2 성공 조건 폴링 예산~~ → **실질 해소** (2026-07-25 오후 2차). 실측: 잔여 폴링 ~370ms 로 애니메이션 정착 불가 → 실패 판정. 예산 분리 대신 **다음 시도의 목표 상태 선체크**가 늦은 정착을 흡수하는 설계 채택 — 회귀 E2E 에서 시도1 실패→시도2 선체크 즉시 성공 실증. 타임아웃 값 무변경
16. ~~넷플릭스(UNRESIZEABLE) 분기 자동화~~ → **완료 + 실기기 E2E 검증** (2026-07-25 오후, 6회). 감지 = privateFlags 필드 리플렉션 + 폴백 비트 1<<11 (실측 교차 검증)
17. ~~넷플릭스 재생-분할 관계~~ → **특성 규명 완료**: 분할 페인 안에서 재생 시작 = 유지 / 재생 중 메뉴 진입 = 재생 세션이 "최소화된 플레이어" 팝업으로 분리 (3회+ 재현, One UI 동작). v1 지침 = "배치 후 재생". Phase 3 온보딩/토스트에 안내 반영
18. ~~step2/3 성공 조건 오탐~~ → **완결** (2026-07-25 오후 2차). 유튜브 DRAG 회귀 E2E 통과. `isSplitSelectTopPane` 임계(전폭≥90%, `EDGE_DOCK_TOLERANCE_PX=40`) 는 ground truth(대상 페인 `[0,0][2184,977]`, 도킹 0px)로 **[검증]**. 회귀에서 발견된 실버그 2종(유령 매치/성공 미인지)은 SplitEntry 수정 완료
19. 회전 결과 페인 위치 비결정 — 원인 미상. 10차 표본 보강: 이 세션 TOP 7/7 (누적 TOP 10 / BOTTOM 2 — 상단 편향). 하단 낙착 시 교정 체인: PaneSwapper(10차 4/4 수렴) → 회전×2 폴백(**미발동·미검증**) → 실패 시 하단 유지+토스트
20. **[10차 실기기 Gate 1~3 통과 — 주 경로 완결]** 15/15 done·ENTRY_STEP_FAILED 0. 피커 cycle-1 회복 실증 + FORENSIC 이 무효 클릭 = **오착지(icon_container) 클래스**임을 물증화 (실행 자체 없음 아님). 회전 여파 스왑 4/4 수렴 (정착 게이트 ~154ms). 잔여 = 미발동 경로 [미검증] 목록 (DEVICE_FACTS 10차 절)과 3시도 전멸 클래스 재발 시 스텝 되감기 재검토만. — (이하 이력) "창 전환" ACTION_CLICK 무효 (회전 직후 컨텍스트 2회 실측, 유튜브 세션에선 성공) — 원인 탐구 필요. 좌표 탭 제스처로 대체 시도 검토. **2026-07-25 오후 4차 보강: step3 피커 탭도 동일 계열 변동성 실측** — 메뉴發 배치 4회 중 2회 step3 3연속 실패(클릭 무효 2회 = 액티비티 생성 이벤트 없음 / `startActivityFromRecents` 오라우팅 1회), 직후 재시도는 성공. 성공 시그니처 = `startActivityAsUser:launcher` (DEVICE_FACTS P3-2 절). → **2026-07-25 저녁 8차 검토 완결**: AOSP 소스 검증 — ACTION_CLICK=true 는 isClickable 만 보장(performClick 결과 폐기, 히트테스트·터치 파이프라인 우회) → 무효·오라우팅 두 클래스 모두 설명. 기존 제스처 폴백은 true 반환 시 도달 불가 = 죽은 코드 실증. "창 전환" 무효는 팝업이 아니라 회전-여파 컨텍스트가 독(회전 노드 6/6 대조) → 정착 게이트 + 재시도가 1차 해법. 설계 확정 = 클릭-사이클 에스컬레이션(`docs/DESIGN_20_CLICK_CYCLE.md`), 구현·실기기 [미검증]. 잔여 미지(3시도 전멸 시 세션 레벨 원인)는 TYPE_VIEW_CLICKED 포렌식으로 특정 후 스텝 되감기 재검토
21. PROFILE 보정 생략으로 verify 측정값(residual 122~224)은 보고 전용 — 컨트롤 오버레이 오염이라 신뢰 낮음. → #12 설계에서 verify = "최저 신뢰 컴포넌트" 판정 (은폐·오염 양방향 실측), 사후 보정 단독 방어 기각 근거 (DESIGN_12 §2). 적응형 residual(글로우 은폐 대응)은 §5 측정 로깅 데이터 수집 후 v1.5
22. 버블 오버레이 존재 시 피커發 파트너가 전체화면 낙착하는 **메커니즘 불명** (One UI WM 라우팅 추정) — 현재 경험 법칙(세션 중 버블 숨김)으로 해소. One UI 업데이트 시 재검증 필요
23. P3-1 잔여: ~~BootReceiver 실부팅 복귀, specialUse FGS 의 BOOT_COMPLETED 시작 허용~~ → **2026-07-25 오후 5차 실부팅 검증 통과** (BOOT_COMPLETED 수신 로그·FGS 자동 기동·접근성 유지·버블 가시 전부 확인). 잔여 → **16차 전부 해소**: 제스처 무불만·오분류 0(시스템 표준 임계 확정), 타이머는 이론 최악 ≈70s 산정으로 90s 상향 (DEVICE_FACTS 16차)
24. ~~P3-2 [미검증] 전체~~ → **2026-07-25 오후 4차 실기기 검증으로 전부 해소**: ① 드래그 해제 가정 **반증** (dispatchGesture 스냅백 2/2 vs 동일 기하 input swipe 성공 3/3) → **패널 finish 방식으로 재구현·E2E 성공** ② 클램프 가로/세로 실기기 정상 ③ DOWN 스냅샷 방어 **실패 실측** (OUTSIDE 선행 디스패치 → 재탭이 배치 오발화) → **풀스크린 스크림으로 구조 해결·E2E 확인** ④ 프리셋 6종 최초 롱프레스에 정상 렌더. 잔여 [미검증]: dismissSplit 인텐트 폴백(instance null 희귀 경로), "분할 없음" 시 2s 대기 후 토스트 체감
25. 스크림 부산물 (해결·기록): 풀스크린 터치 가능 오버레이가 떠 있는 동안 하위 창이 a11y `getWindows()` 에서 가림-제외되고, 제거 직후 재구축이 **비원자적** (앱 창 먼저, 디바이더 나중) — `isSplitActive` false-negative 2/2 실측. dismiss 는 목표 조건 자체 폴링으로 해결. `beginSession` 의 `awaitWindowsSettled`(APPLICATION≥1 약한 게이트)도 같은 원리에 취약할 수 있음 — 배치 경로에서 유사 증상 재현 시 동일 패턴 적용. **10차 부수 관측**: broadcast 트리거 15세션(버블/스크림 창 미개입 경로)에서 창 목록發 증상 0건 — 취약 가설은 스크림·버블 창 존재 시나리오에 한정된 채 유지
26. ~~P3-3 실기기 [미검증] 목록~~ → **2026-07-25 오후 7차 실기기 검증으로 전부 해소** (DEVICE_FACTS P3-3 절): ① 이관 — 구버전에 x/y 주입 후 업데이트 설치, 3키 무손실 이관+원본 삭제+버블 위치 복원 ② goAsync 실부팅 — 로그·FGS 기동·접근성 유지 (회귀 해소) ③ placement — OVERRIDE bottom 저장 → 무override 3회 전부 LAST_SUCCESS/BOTTOM 결정, 3회차 done residual=0 ④ corruption — 가비지 주입 후 서비스 기동, 손상 감지 로그+emptyPreferences 복구+무크래시, enabled 재기록 ⑤ 중지 레이스 — 탭+즉시 back(finish) 근사, 쓰기·stopService 완주 → **16차 물리 폴드 접기 실기 통과** (잔여 0). 부차 미해결: 손상 1차 감지가 주체 미상 초기 store 접근에서 발생 (결과는 동일한 정상 복구)

27. **[17차 신규 — 미해결·최우선]** step3 패널 소환 경로 부재: 피커 「FW Panel」 노드 = recents 태스크 카드. `finishAndRemoveTask`(커버 자동 해제 P4-3·dismissSplit·purgeStalePanelTasks·패널 자가 가드)가 카드 제거 시 배치 전체 불능 (node-not-found 3전멸 ×4 재현). purge 는 세션 시작마다 자충(살려둔 패널 태스크도 제거). 복구는 런처 경유 실행(수동 피커 탭)만 유효 — `am start` 직접 실행은 자가 가드가 다시 제거. 수정 후보 3종 = DEVICE_FACTS 17차 「신규 결함」 절

## 결정 로그

| 날짜 | 결정 | 근거 |
|---|---|---|
| Day 0 | Tier 1(접근성+오버레이) 경로 확정, Shizuku는 Phase 4 선택 | 디바이더 임의 비율 허용 확인 |
| Day 0 | MediaProjection 재렌더링 경로 폐기 | DRM 차단, 지연/발열 |
| Day 0 | 삼성 기본 「앱별 화면 비율」 대체 불가 판정 | 상시 고정이라 시청 시에만 쓸 수 없음 |
| 2026-07-25 | P2-3 기본 경로 = Recents 폴백 확정 | #6 FAILS: `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 이 분할 전/중 모두 false (실기기 3회) |
| 2026-07-25 | DividerLocator 1차 경로 = `TYPE_SPLIT_SCREEN_DIVIDER` 핸들 중심 | #7 ✅: 분할 활성 중 핸들 68×221 노출. 페인 간 실간격 14px |
| 2026-07-25 | LetterboxDetector v2 하이브리드로 확장 결정 | 유튜브 앰비언트 모드가 띠를 글로우로 채워 순흑 임계(0.97) 불성립 (screencap 실측 darkRatio 0.000) |
| 2026-07-25 | ArrangeStateMachine: 검증 실패는 Failed가 아니라 `Done(verified=false)` | 드래그까지 성공한 배치를 측정 실패 때문에 버리지 않는다. verified=false로 노출해 조용한 실패 금지 원칙 유지 |
| 2026-07-25 | ADR-5 보정은 정확히 1회, 이후 잔여값 보고하고 종료 | 무한 보정 루프 방지. 스크린샷 레이트 리밋(~1회/초)과도 정합 |
| 2026-07-25 | Detector v2 폴백은 위아래 띠 모두 존재할 때만 유효 판정 | 한쪽만 어두운 UI 요소 오검출 방지. 전면 저디테일 장면은 MIN_CONTENT_FRACTION 가드로 거부 |
| 2026-07-25 | kotlinx-serialization은 `data/` 에만 격리, domain 모델은 순수 데이터 클래스 + DTO 매핑 | domain 순수성 철칙(kotlin-stdlib only) 유지. DTO 중복 비용보다 회귀 방어선 가치가 큼 |
| 2026-07-25 | assets srcDir을 `../config` 로 직결 | `config/window_profiles.json` SSOT를 복제 없이 APK에 탑재. 리포지토리 추적 파일 1개 유지 |
| 2026-07-25 | 시드 JSON에서 `aspectSource=MEASURED` 는 aspect null 강제 | 시드 의미론 단순화. 측정값 캐싱은 Phase 3 DataStore에서 재검토 |
| 2026-07-25 | 실측 채택 최소 신뢰도 0.25 (파라미터화, 기본값) | 순흑(≈1.0)·ADAPTIVE(≤0.6) 둘 다 통과 가능한 보수적 하한. 실기기 튜닝 대상 |
| 2026-07-25 | Phase 2: Hilt 미도입, 수동 주입 유지 | 빌드에 Hilt 미설정. Phase 2 규모에 과잉. Phase 3에서 재검토 |
| 2026-07-25 | 상하 전환(스왑) 실패 시 실제 페인 배치 기준으로 계획 재계산 후 드래그 | 레터박스 제거(핵심 가치)를 우선 보장. 위치 불일치는 최종 토스트로 고지 — 조용한 실패 아님 |
| 2026-07-25 | step4 파트너 진입 = `LAUNCH_ADJACENT` 1차, 피커 노드 탭 폴백 | 로케일 무관 경로 우선. 둘 다 실기기 미검증이라 이중화 |
| 2026-07-25 | `PanelActivity` MAIN/LAUNCHER 노출 수용 (라벨 "FW Panel") | 분할 파트너 피커 목록에 뜨려면 필요. 앱 서랍 오염은 Phase 3 재검토 |
| 2026-07-25 | 순수 기하 로직을 `domain/PaneGeometry.kt` 로 추출 | ADR-4: JVM 테스트 표면 최대화. platform 은 얇은 매핑만 |
| 2026-07-25 | 진입 레시피 = Recents 카드 **드래그**(3단계)로 전면 교체 | 메뉴 "분할 화면으로 열기"는 가로에서 **좌우 분할** 생성 (실측 반증). 드래그-투-탑만 상하 분할 |
| 2026-07-25 | `DividerDragger` 기본 전략 = SINGLE_STROKE | 실측: 평범한 스와이프로 디바이더 정확 이동. willContinue 조합은 API 함정 다수 (DEVICE_FACTS 참조) |
| 2026-07-25 | 파트너 배치 1차 = 피커 노드 탭, LAUNCH_ADJACENT 는 최후 폴백 | LAUNCH_ADJACENT 가 분할 선택 상태를 전체화면으로 파괴 (실측) |
| 2026-07-25 | `PanelActivity` = launchMode 기본 + 멀티윈도우 이탈 시 `finishAndRemoveTask` 자가 가드 | singleTask/잔존 태스크가 피커 탭에서 전체화면 재사용돼 분할 파괴 (실측: 깜빡임 루프) |
| 2026-07-25 | 측정 타이밍: 드래그 완료 후 600ms 정착 대기 + 매 드래그 직전 핸들 재조회 | 50ms 후 측정 시 잔여 218px 오측·스테일 핸들 허공 스와이프 (실측) |
| 2026-07-25 | 검증 잔여는 `LetterboxDetector.residualBars`(상한 거부 없음)로 측정 | 완전 배치(띠 0)가 "측정 실패"로 오판되던 의미론 구멍 해소 |
| 2026-07-25 | 타깃 결정 = 활성 창 1차, 이벤트 추적 폴백 | 오버레이 앱(sidegesturepad)이 이벤트 추적 오염 (실측) |
| 2026-07-25 | 비리사이저블 앱(넷플릭스류) = 메뉴 경로 + "시계 방향으로 회전" 우회 분기 확정 | 드래그 레시피가 팝업으로 라우팅됨 (UNRESIZEABLE 선언, 실측). 회전 버튼으로 좌우→상하 전환 실측 성공 |
| 2026-07-25 | Phase 2 는 DoD ①③④ 로 마감, ② 넷플릭스 분기는 이월 | 우회 경로 실측은 끝났고 자동화만 남음. 세션 컨텍스트 한계로 분리 |
| 2026-07-25 | 진입 레시피를 `EntryRecipe` enum(DRAG 3단계/MENU 5단계)으로 분기, 감지 = `ApplicationInfo.privateFlags` 리플렉션 | DEVICE_FACTS 실측 근거(넷플릭스 = privateFlags UNRESIZEABLE 선언). 상수값도 리플렉션으로 읽어 AOSP 버전 변화 대비. 리플렉션 실패(비SDK 차단) 시 null → DRAG 기본값으로 안전 폴백 |
| 2026-07-25 | step2/3 성공 조건을 `PaneGeometry` 도메인 판정으로 교체 | 팝업(프리폼) 오탐 실측(열린 질문 #18). 판정 로직을 domain 으로 옮겨 JVM 테스트 표면 확대(ADR-4) — 신규 판정 3종 13케이스 커버 |
| 2026-07-25 | 상태 머신 무변경으로 MENU 5단계 수용 | `entryStepCount` 가 이미 파라미터화돼 있어 config 값만 교체. "머신은 N단계 중 k번째만 안다" 설계가 검증됨 |
| 2026-07-25 오후 | UNRESIZEABLE 폴백 비트 = 1<<11 하드코딩 (상수 리플렉션 denied 시) | 실측: 상수는 max-target-o denied, 필드는 allowed. privateFlags=0x8c000910 비트 분해로 dumpsys 명칭 교차 검증 |
| 2026-07-25 오후 | 넷플릭스 프로파일 = PROFILE 1.7778 고정 (MEASURED 폐기) | "DRM 이면 검게 나와 폴백" 가정 반증 — 분할/홈 UI 섞인 프레임이 고신뢰 오측(1.14/2.95) 생성 |
| 2026-07-25 오후 | pre-measure 는 대상 페인 크롭으로만 스캔 | 전체 화면 스캔이 분할 상태에서 항상 오염됨 (실측 2회) |
| 2026-07-25 오후 | aspectSource=PROFILE 이면 ADR-5 보정 생략 + `closedLoopCorrection` JSON 토글 배선 | 드래그 직후 재측정이 컨트롤 오버레이 오염(residual 122~224)으로 정확한 배치를 과축소 (실측 2회). 프로파일이 진실, 측정은 보고만 |
| 2026-07-25 오후 | 세션 시작 시 `purgeStalePanelTasks()` | 프로세스 강제 종료로 자가 가드 미실행 시 잔존 태스크 카드가 피커 탭 무력화 → ENTRY_STEP_FAILED (실측). 자기 앱 태스크만 선별 제거라 안전 |
| 2026-07-25 오후 | 스왑 실패 대응 = PaneSwapper 탭 재시도 3회 → 회전×2 폴백 → 하단 유지+토스트 | "창 전환" ACTION_CLICK 무효 2회 실측. 회전 노드 클릭은 6회 전부 동작해 신뢰 가능한 대체 수단 |
| 2026-07-25 오후 | 세션 `dragTimeoutMs=12s` 오버라이드 (도메인 기본값 3s 불변) | 위치 교정 체인(스왑 3s+회전×2)이 Dragging 상태 안에서 실행돼 기본 예산으로 DRAG_TIMEOUT (실측) |
| 2026-07-25 오후 2차 | 진입 스텝 재시도 = "목표 상태 선체크 우선" 설계 (타임아웃 증액 대신) | 유튜브 DRAG 회귀 실측: 드래그 성공 후 정착이 폴링 잔여(~370ms)를 초과하면 실패 판정 → 다음 시도가 성공을 몰라봄. 선체크가 늦은 정착을 흡수하면 실패 보고 지연 없이 해소. E2E 실증 |
| 2026-07-25 오후 2차 | 셀렉터 매치는 유효 bounds 확보까지 불인정 (step2) | `structural-clickable-label` 이 bounds 조회 불가 유령 노드를 매치해 시도가 수 ms 만에 소진 (실측 2회). 빈 bounds 는 재폴링 계속 |
| 2026-07-25 오후 2차 | structural 셀렉터에 크기 가드 (bounds ≤ 화면폭/10) | 유효 bounds 의 대형 오매치(카드 본체 중심 1092,833) → 오드래그로 세션 파괴 실측. 아이콘 ~90px vs 카드 수백 px |
| 2026-07-25 오후 2차 | 버블 = 독립 specialUse FGS + TYPE_APPLICATION_OVERLAY, 접근성 서비스에 얹지 않음 | 접근성 꺼진 상태에서도 버블이 온보딩 유도 가능해야 함. Freecess 동결 근본 해결 겸함 (DEVICE_FACTS) |
| 2026-07-25 오후 2차 | 배치 세션 중 버블 창 제거(removeView), 세션 종료 시 복원 | A/B 실측: 오버레이 존재 시 피커發 PanelActivity 전체화면 낙착 (ON 실패 2회 / OFF 즉시 성공). 숨김 소유자 = 액추에이터 세션 (모든 트리거 경로 커버) |
| 2026-07-25 오후 2차 | `PANEL_LABEL_CANDIDATES` = "FW Panel" 단독 | "FoldWindow" 후보가 P3-4 온보딩 라벨과 충돌 — 피커 오클릭으로 분할-선택 파괴 실측 |
| 2026-07-25 오후 2차 | 버블 오버레이엔 클래식 View, 온보딩엔 Compose | 오버레이 창에 Compose 는 lifecycle owner 함정. service/ 레이어라 Compose 규칙 비대상 |
| 2026-07-25 오후 3차 | P3-2 메뉴 트리거 = 롱프레스 (탭=즉시 배치 유지), 온보딩은 메뉴 "설정" 항목으로 이동 | 원터치 배치가 핵심 가치 제안 — 탭에 메뉴를 얹으면 터치 수 증가 |
| 2026-07-25 오후 3차 | 분할 해제 = 디바이더를 자기 패널 페인 쪽 가장자리로 드래그 + isSplitActive 폴링 | 전용 API 부재. 패널 쪽으로 접어야 시청 앱이 전체화면 복귀. 자기 페인 미발견 시 하단 폴백. [미검증 #24] |
| 2026-07-25 오후 3차 | 프리셋 메뉴 = window_profiles.json SSOT 파싱 (하드코딩 복제 금지), 파싱 실패 시 섹션 생략 | SSOT 원칙. `PROFILES_ASSET_NAME` 상수를 WindowProfilesParser 로 이전해 공유 |
| 2026-07-25 오후 3차 | 메뉴도 배치 트리거 전 반드시 창 제거 (`dismissMenu` 선행 + `setBubbleHiddenForArrange(true)` 에 포함) | 함정 #22 (오버레이 존재 시 피커發 파트너 전체화면 낙착) 가 메뉴 창에도 동일 적용된다고 가정 |
| 2026-07-25 오후 4차 | 분할 해제 = **PanelActivity finish 방식** (디바이더 드래그 폐기) | 실측: dispatchGesture 는 dismiss 깊이에서 스냅백(2/2), 동일 기하 input swipe 는 성공(3/3) — 접근성 주입만 거부됨. 패널 finish 는 분할 해소+상대 앱 전체화면 복귀 실측. E2E "dismissSplit: 성공" |
| 2026-07-25 오후 4차 | 확장 메뉴 = **풀스크린 투명 스크림** 창 (ACTION_OUTSIDE 폐기) | 실측: OUTSIDE 가 버블 DOWN 보다 선행 디스패치 → DOWN 스냅샷 방어 무력 (재탭→배치 오발화, 재롱프레스→재열림). 스크림은 경합 클래스를 구조적으로 제거. 재탭=닫기만 E2E 확인 |
| 2026-07-25 오후 4차 | dismiss 진입 체크 = `isSplitActive` **자체를 2s 조건 폴링** (약한 freshness 게이트 불충분) | 실측: 스크림 제거 후 a11y 창 목록 재구축 비원자적 — APPLICATION≥1 게이트 통과 후에도 디바이더 미관측 false-negative 2/2. 목표 조건 직접 폴링으로 해결, E2E 통과 |
| 2026-07-25 오후 6차 | P3-3 이관 = `SharedPreferencesMigration("bubble_prefs")`, 레거시 키 이름(`bubble_enabled`/`bubble_x`/`bubble_y`) **동결 계약** — 회귀 테스트로 고정 | 마이그레이션이 키 이름 그대로 이관 + 원본 파일 삭제. 이름이 어긋나면 기존 사용자 설정 유실. 직접 `getSharedPreferences` 호출은 리포지토리에서 0건으로 소거 (잔존 사용처 = 삭제된 파일 부활 버그) |
| 2026-07-25 오후 6차 | placement 복원 우선순위 = override > **last-success** > profile > defaults > TOP. 저장은 Done ∧ effective==desired 만 | 마지막 실사용 선택이 정적 JSON 보다 사용자 의도에 가까움. 스왑 실패로 낙착한 위치(effective≠desired)를 저장하면 사용자가 고르지 않은 값이 기본값을 오염 (#19/#20 실측 존재). placementSource 로그로 어느 티어가 이겼는지 노출 |
| 2026-07-25 오후 6차 | ProfileStore 쓰기 = `NonCancellable` + `ReplaceFileCorruptionHandler`→emptyPreferences. 측정 종횡비 캐싱은 P3-3 에서 **제외 유지** | 리뷰 실지적: 레거시 apply() 는 컴포넌트 종료 후에도 QueuedWork 로 반영 보장 — 코루틴 취소가 쓰기 유실/stopService 미호출 회귀를 만듦. corruptionHandler 부재는 부팅 크래시 루프 위험. 캐싱은 #12 신뢰도 필터 전엔 오염 고착 위험 (실측 사고 2회) |
| 2026-07-25 오후 6차 | BootReceiver = goAsync + IO 코루틴, `finally { pendingResult.finish() }` | 메인 스레드 동기 IO 제거. 부팅 시점 조용한 실패 금지 의미론(로그만·크래시/토스트 금지)과 검증된 로그 시그니처는 불변. 실부팅 재검증 필요 (#26) |
| 2026-07-25 저녁 8차 | #20 대응 = **클릭-사이클 에스컬레이션** (매 디스패치 직전 성공조건 선체크 + 검증 슬라이스 800ms 폴링 + 사이클별 메커니즘 전환: 피커 gesture→gesture→a11y / 스왑 a11y→gesture→gesture) + **step3 LAUNCH_ADJACENT 폴백 삭제** + PaneSwapper 정착 게이트(디바이더 bounds 2연속 동일). 구현 전 [미검증] | AOSP 소스: ACTION_CLICK true 는 isClickable 만 보장·performClick 결과 폐기·히트테스트 우회 (View.java 검증). 실측: 무효 클릭 전부 true 반환 → 기존 폴백 도달 불가. LAUNCH_ADJACENT 는 분할-선택 파괴 실측·구조 성공 0회·예산 ~0ms 발화·재시도 오염원. 검증 상수 무변경, 메커니즘 순서 = 데이터(1줄 롤백 레버). 상세 `docs/DESIGN_20_CLICK_CYCLE.md` |
| 2026-07-25 저녁 10차 | **LAUNCH_ADJACENT 삭제 확정** + 클릭-사이클 설계 실기기 채택 확정 (피커 gesture-first·스왑 a11y-first 프로파일 유지, 롤백 레버 미사용) | Gate 1~3 실측: 15/15 done·ENTRY_STEP_FAILED 0·전략2 부재 회귀 0. cycle-1 회복 1회 실증 + FORENSIC 오착지 특정. 회전 여파 스왑 4/4 (과거 무효 컨텍스트 동형 전승). DEVICE_FACTS 10차 절 = SSOT |
| 2026-07-25 저녁 11차 | #12 = **2-샷 합치 게이트** (pre 행축 × 진입 후 confirm 열축·페인 크롭, relΔ≤3% 합치 시만 MEASURED, 불합치·확인불가 = PRESET 폴백 + verify 보정 수렴). 임계 상향·대칭성 휴리스틱·verify 강화 단독·pre 2연속 전부 기각. 머신 무변경 (handleDragDividerTo 선두, realTargetY 선례). 토글 `defaults.requireMeasurementAgreement` 기본 true. aspectOverride 는 tier 0 승격 (측정이 사용자 "강제"를 이기는 인접 결함 수정, 보정도 생략). 캐싱 admission = 합치∧verified. **구현 완료 (qa PASS 182/182·머신 diff 0)** |
| 2026-07-25 밤 11차 | #12 실기기 G1~G5 **전부 통과 — 합치 게이트 채택 확정**. 현장 수정 2건: `toPillarboxScan` sideMarginPct 0.005 + edgeMarginPct 0.12, `classifyAxis` minConfidence — 전부 **스캔 입력 범위** 수정이고 도메인 판정 상수(ADAPTIVE_* 4종)는 무변경 | 오염 = 상수가 아니라 입력 문제 (픽셀 물증: 최외곽 열 var 404~501 / 크롬 아이콘 luma 176~188·raw 소수 7자리 동일 재현 = 정적 물증). 수정 후 클린 3/3 SNAP_AGREE·사고 클래스 3종 차단·tier 0 정상. 함정 #7 준수 — 상수 대신 입력 정화. DEVICE_FACTS 11차 절 = SSOT | 오염 프레임 conf 0.60~0.97 실측 — 단일 프레임 점수는 "프레임이 영상인가"를 측정하지 않음. 오염원은 전부 일시적 ↔ 띠는 지속 → 시간·축·컨텍스트 분리가 유일하게 견고한 신호. 오탐 비용(보정 1회) ≪ 미탐 비용(오배치 고착, 1.6 사고 실증). 상세 `docs/DESIGN_12_MEASUREMENT_CONSENSUS.md` |
| 2026-07-26 12차 | #12 §6 측정 캐싱 v1 = `AspectSource.CACHED` 티어 (MEASURED 아래·PRESET 위) + admission "합치∧verified∧레버" + 불합치 폴백도 cached 우선 + 무 TTL last-write-wins + 레버 `defaults.cacheMeasuredAspect`(부재=true). CACHED 세션은 confirm 미실행·자기 갱신 없음 | 캐시 = 같은 앱의 이중 검증된 사전값 — §3.5 의 PRESET "사전확률" 논증을 그대로 승계하되 정보량 우위, 오류 비용은 보정 1회로 동일(CACHED 도 closedLoopCorrection ON). requireAgreement=false 포함 confirm 미실행 세션은 구조적으로 저장 불가 — 단일 프레임 값 유입 차단(오염 고착 사고 2회 재발 방지). pre×캐시 합치로 confirm 샷 생략(+0.3s)은 G1~G5 검증 직후 새 채택 경로 부담으로 기각(v1.5 재고). qa PASS 204/204 결함 0 |
| 2026-07-26 13차 | #12 §6 측정 캐싱 **실기기 검증 4항목 전부 통과 — v1 채택 확정** (코드·상수 무변경, 현장 수정 0건) | 10 세션 10/10 done. 저장 admission·CACHED 낙착(폴백/decision 양 지점)·자기 갱신 차단·레버 회귀 전부 로그+pb 물증. 원복 세션에서 사고 클래스 1.333 오염이 캐시 폴백으로 무해화 — §6 기대 효용 실전 재현. pre-null 유도법(세로 영상 immersive)·`cmd window user-rotation` 함정은 DEVICE_FACTS 13차 절 기록 |
| 2026-07-26 14차 | P3-5 = placement 체인 **FLEX 티어**(OVERRIDE 다음·LAST_SUCCESS 앞) + 자동 트리거는 `startArrange(null,null)` 로 동일 티어 경유(자동·수동 단일 메커니즘) + FLEX 세션 last-success 저장 억제(종횡비 캐시는 직교라 유지) + 레버 `flexAutoTopPlacement`(부재=true) + 게이트 거부 시 disarm·로그만(토스트 없음) + 디바운스 800ms·진입당 1회 arm | 노트북 자세에선 하단 페인이 책상에 눕는 물리 — TOP 강제가 옳고 명시 override 만 예외. 자동화 결정값이 사용자 선호(last-success)를 오염 금지(P3-3 저장 조건 원칙 연장). 자동 스킵은 사용자 시작 행위가 아니라 조용한 실패 금지 비대상. 닫기 동작의 HALF_OPENED 일시 통과 오발화는 800ms 디바운스+display-off 게이트 2중 방어. UiContext 수용 불확실은 폴백 체인+기능 무력 폴백으로 흡수(크래시 금지) — 전부 실기기 검증 대상 |
| 2026-07-28 15차 | UiContext = **3-인자 `createWindowContext(display, TYPE_ACCESSIBILITY_OVERLAY, null)` 채택** (서비스 자신·2-인자 전멸 실측). 구 2-인자 후보 삭제 | 에러 메시지가 해법 명시 — display 명시 연결만 UI 컨텍스트 성립. 실기기: 후보 ② 방출 수신, 격하 경로(전멸 시 무크래시)도 실증 |
| 2026-07-28 15차 | 닫기 오발화 방어 = **힌지 각도 안정성 게이트** (시간 상수 증액 기각) + **FLEX 세션 자세-이탈 취소** 2층 | 닫기 체류 실측 ~2s(3표본) — 800ms 디바운스 반증, display-off 게이트는 닫는 중 화면 켜져 무력. 상수 증액은 느린 닫기 꼬리에 재차 뚫리는 타이밍 도박 → ADR-2 정신대로 조건 기반(각도 정지 = 속도 무관 신호). 멈칫 동반 느린 닫기는 게이트 통과 불가피 → 자세 이탈 = 의도 번복 신호로 진행 중 세션 취소 (기존 cancel 경로 재사용, 머신 무변경). 정상 닫기 2/2 차단 + 취소 1/1 + Done-후 유지 전부 실증 |
| 2026-07-28 15차 | FLEX 라벨 의미론 확정 (qa CONDITIONAL 회신): FLEX = "위치를 자세가 자동 결정" — **트리거 출처 무관** (버블 탭 무override 포함). 취소·저장억제 대상 기준 = 이 라벨. OVERRIDE(명시 위치)만 불가침 | 위치 결정 근거가 물리 자세라면 근거 소멸(이탈) 시 취소가 정합. 사용자 "명시 선택"과 "알아서" 위임의 경계가 원칙적 보호선 — 14차 단일 메커니즘 결정의 연장 |
| 2026-07-28 15차 | 재열기 멈칫(대역 내 ≥1.4s 정지) 오발화는 **수용**, 키가드 게이트 기각. Done 후 자동 되돌리기 없음 | 물리 신호만으론 정당한 "닫힌 채→노트북 자세로 열기"와 구분 불가. 키가드 게이트는 그 정당 사용례(잠긴 채 자세 잡고 얼굴 인식)를 죽임. 늦은 펴기 시 완주 분할 잔존은 수동 해제로 복구 — v1.5 재검토 |
| 2026-07-28 P4 | Phase 4 스코프 = P4-2·P4-3·P4-4 즉시 구현, **P4-1 은 설계 문서+프로브(F1~F6) 선행 후 구현** (`docs/DESIGN_P41_FREEFORM.md`) | freeform 실행 메커니즘(One UI 8 이 셸 `--windowingMode` 를 수용하는지 등)이 전부 미측정 — 맹목 구현은 #12/#20 선례(설계→프로브→구현) 위반. 기기 미연결로 프로브는 다음 세션 이월 |
| 2026-07-28 P4 | P4-3 발화 = `dismissSplit()` 미경유, `PanelActivity.instance` 직접 `finishAndRemoveTask()`. 자동 스킵/실패 = 로그만(토스트 금지) | dismissSplit 의 isSplitActive 2s 폴링은 커버 디스플레이 a11y 창 목록 상태가 미지수라 신뢰 불가. 패널 finish = 분할 해소 트리거는 실측 확정 사실(P3-2). 자동 트리거 스킵은 조용한 실패 금지 비대상(P3-5 flex 선례) |
| 2026-07-28 P4 | P4-3 수동 dismissSplit × 커버 자동 해제 레이스 = 게이트 미추가 수용 | 양 경로가 동일한 패널 finish 로 수렴 — 이중 finish 무해. 발생 창도 "해제 메뉴 탭+600ms 내 완전 접기"로 극소. 세션 중 접기로 게이트에 막힌 닫힘 에피소드는 소진(재열기 전 패널 잔존) — v1 한계로 수용, 17차 관찰 |
| 2026-07-28 P4 | P4-2 위젯 모드 = ui 전용 enum, 도메인 `PartnerMode{BLACK}` 비확장. 자막 위젯 v1 제외 | PartnerMode 는 JSON 프로파일 스키마 소속 — UI 표시 선호와 결합하면 시드 의미론 오염. 자막은 미디어 세션 의존 투기 구현이라 범위 밖 |
| 2026-07-28 P4 | P4-4 = 자체 트램펄린 pinned shortcut(삼성 App Pair 포맷 비의존) + 전면 대기 = **머신 밖 사전 조건 폴링**(150ms/5s) + placement 는 기존 체인 재사용 | 삼성 App Pair 포맷은 비공개 런처 내부라 재현 불가. 사전 폴링을 머신 밖에 둬 상태 머신 무변경 원칙 유지. 새 placement 티어 금지(자동화 값이 사용자 선호 오염 금지 원칙 연장) |
| 2026-07-28 P4-1 | 실행 경로 = **후보 A(Shizuku 셸 명령) 채택, 후보 B(binder/HiddenApiBypass) 기각** | 프로브 F2·F3 통과 — `am start --windowingMode 5` + `am task resize` 픽셀 정확 실측. hidden API 무접촉이 유지비용 최소 |
| 2026-07-28 P4-1 | Shizuku 실행 = **UserService(AIDL) 방식**, `Shizuku.newProcess` 사용 금지 | `newProcess` 는 비공개 API — 버전 간 파손 위험. UserService 는 공개 지원 경로, shell uid 프로세스에서 `sh -c` 실행으로 동일 능력 |
| 2026-07-28 P4-1 | `StackListParser` 를 **domain/ 에 배치** (셸 출력 파싱) | 문자열 파싱은 android 비의존 순수 로직 — JVM 테스트 표면 확대 원칙. 실기기 원문 46행 대조 10/10 + 원문 픽스처 테스트로 회귀 방어 |
| 2026-07-28 P4-1 | 팝업 경로는 `ArrangeStateMachine` **비사용** — 단순 명령 + 검증 폴링 5단(창 출현→taskId→resize→bounds 검증) | 진입 스텝이 셸 명령 1개라 머신이 과잉(설계 문서 §4 미결 → 확정). 머신 무변경 원칙 유지. 각 단계 타임아웃 + 명시적 실패(ADR-2) |
| 2026-07-28 16차 | 버블 숨김 안전 타이머 30s→**90s** | 이론 최악 세션(MENU 5스텝×3시도×3s + 디바이더 4s + 드래그 12s + verify) ≈70s > 30s. 조기 복원 = 세션 중 오버레이 재출현 = 함정 #22 자충수. 워치독 비대칭(늦은 복원 무해/이른 복원 유해) + 실측 최장 12s. 제스처 임계는 시스템 표준 유지 확정 (16차 무불만·오분류 0) |
