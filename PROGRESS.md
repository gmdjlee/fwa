# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-25 오후 4차 (**P3-2 실기기 검증 완료 + 결함 3건 수정** — ① dismissSplit 드래그 가설 반증→패널 finish 방식 재구현·E2E 성공 ② 메뉴 재탭 경합 실측→풀스크린 스크림 재구성 ③ 스크림發 a11y 창 목록 false-negative→isSplitActive 자체 폴링. 131 테스트·빌드 통과, 전 결함 실기기 E2E 재검증)
**현재 Phase:** Phase 3 진행 중 — P3-1·P3-4 완료, P3-2 **완료(실기기 E2E 검증)**, 잔여 P3-3·P3-5
**다음 행동 (착수 목록):**
1. 미커밋 변경 커밋 (P3-2 검증 수정 3건: ArrangerAccessibilityService/FloatingLauncherService/PanelActivity + 문서 — 사용자 승인 후)
2. P3-1 잔여 실기기 검증: 부팅 후 버블 자동 복귀(BootReceiver [미검증]), 알림 권한 플로우. P3-2 잔여: dismissSplit 인텐트 폴백(instance null) [미검증], "분할 없음" 2s 대기 체감 확인
3. Phase 3 계속: P3-3 DataStore 프로파일 (버블 prefs 이관 포함), P3-5 FoldingFeature. **#12 신뢰도 필터 우선 검토** — 영상 시작 직후 탭 시 1.6 오측 실측 (DEVICE_FACTS 참조)
4. **#20/#25 step3 피커 탭 변동성**: 메뉴發 배치 4회 중 2회 step3 3연속 실패 실측 (클릭 무효/오라우팅, DEVICE_FACTS P3-2 절 참조) — 좌표 탭 제스처 대체 검토
5. Phase 3 중 검토: 회전×2 폴백 실기기 검증 기회 확보
**다음 행동:** 실기기 검증 절차:

```bash
./gradlew :app:installDebug
# 재설치 후 접근성 재활성화 필수 (함정 #6). probe 병행 시 콜론으로 연결
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService
# 유튜브 가로 전체화면 재생 상태에서:
adb shell am broadcast -a dev.dj.foldwindow.ARRANGE --es placement top
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
| Phase 3 UI | 🔄 진행 중 | P3-1 버블 ✅ + P3-4 온보딩 ✅ (실기기 E2E: 버블 탭→배치 4.1초, 버블 숨김/복원 검증). P3-2 확장 메뉴 ✅ **실기기 E2E 완료** (메뉴發 배치 verified·분할 해제 성공·재탭 닫기만·프리셋 렌더·가로/세로 클램프 — 결함 3건 발견·수정·재검증, DEVICE_FACTS P3-2 절). 잔여: P3-3 DataStore / P3-5 FoldingFeature. 부팅 복귀 [미검증] |
| Phase 4 확장 | ⬜ 조건부 | #5 판정에 따라 범위 결정 |

## 작성된 코드

| 파일 | 상태 |
|---|---|
| `domain/SplitPlanner.kt` | ✅ P1-1 반영. `foldSevenLandscape()` = divider 14px / minPane 181px (실측). 테스트 22개 |
| `domain/LetterboxDetector.kt` | ✅ v2 하이브리드. 순흑(0.97) 우선 → luma 통계 기반 적응 폴백(`ADAPTIVE_*` 상수 4종). `resolveAspect` 진입점 불변. 테스트 26개 |
| `domain/ArrangeStateMachine.kt` | ✅ P1-4 + `closedLoopCorrection` 플래그 (false 면 ADR-5 보정 생략, 잔여 정직 보고 — PROFILE 소스 오보정 실측 대응). 순수 리듀서, 시간은 이벤트 nowMs 만(ADR-2). 테스트 24개 |
| `domain/Profiles.kt` | ✅ P1-2 신규. `AspectSource`/`PartnerMode`/모델 4종 + `validate()` 위치 특정 에러. 순수 Kotlin |
| `domain/AspectResolver.kt` | ✅ P1-3 신규. ADR-1 3단 폴백(PROFILE→MEASURED→PRESET). `DEFAULT_MIN_MEASUREMENT_CONFIDENCE=0.25f`(미검증). 테스트 9개 |
| `data/WindowProfilesParser.kt` | ✅ P1-2 신규. kotlinx-serialization DTO→domain 매핑, 예외 누출 없이 `ProfilesParseResult`. 테스트 14개(실제 SSOT 파일 파싱 포함) |
| `platform/ScreenshotSampler.kt` | ✅ v2 확장. 행별 luma 평균/분산 산출 추가. 실기기 미검증 |
| `domain/PaneGeometry.kt` | ✅ P2-2 + 확장. 가시 교집합·간격 휴리스틱·상하/좌우 분할 판정·분할선택 페인 판정·`pickPaneLike`(최소화 플레이어 팝업 오염 필터, 실기기 근거). 순수 Kotlin. 테스트 30개 |
| `platform/DividerLocator.kt` | ✅ P2-2. `TYPE_SPLIT_SCREEN_DIVIDER` 핸들 중심 1차 → PaneGeometry 간격 휴리스틱 폴백. 실기기 미검증 |
| `platform/DividerDragger.kt` | ✅ P2-4 **실기기 검증**. SINGLE_STROKE 기본(확정). HOLD_THEN_MOVE 는 GestureDrags 위임 |
| `platform/PaneSwapper.kt` | ⚠️ 유튜브 세션 실기기 성공 / **넷플릭스 회전 직후 컨텍스트에선 "창 전환" ACTION_CLICK 무효 2회 실측** (원인 미상). 핸들 탭 재시도 3회 도입. 실패 시 서비스가 회전×2 폴백 |
| `platform/SplitEntry.kt` | ✅ P2-3 **실기기 검증** (DRAG·MENU 양 경로). `EntryRecipe` 분기: DRAG 3단계(유튜브 회귀 E2E 통과) / MENU 5단계(UNRESIZEABLE 전용, E2E 6회 성공). 2026-07-25 오후 2차: step2 유령 매치 재폴링 + 목표 상태 선체크(정착 지연 흡수) 수정, 동일 선체크를 step3·menuStep3~5 에 보강 |
| `platform/ResizeModeDetector.kt` | ✅ 신규 **실기기 검증**. `privateFlags` 필드 리플렉션 allowed / 상수 리플렉션 denied(max-target-o) → 폴백 비트 **1<<11** (0x8c000910 교차 검증). 실패 시 null → DRAG 폴백 |
| `platform/DividerPopupRotator.kt` | ✅ 신규. 핸들 탭→"시계 방향으로 회전" 클릭 공용화 (MENU step5 + 서비스 회전×2 폴백). step5 경로 실기기 검증, 회전×2 폴백은 미발동 **미검증** |
| `platform/GestureDrags.kt` | ✅ 신규·실기기 검증. 2-페이즈 홀드드래그(1px 드리프트 홀드 + continueStroke 이동, 타이밍 가드). API 함정 4종 문서화 |
| `service/ArrangerAccessibilityService.kt` | ✅ **실기기 검증** (유튜브+넷플릭스). 상태 머신 구동, 레시피 선택 배선, pre-measure **페인 크롭**, `pickPaneLike` 위치 판정, `purgeStalePanelTasks`, 스왑 실패 시 회전×2 폴백, `closedLoopCorrection` 배선(PROFILE 시 off), 세션 `dragTimeoutMs=12s` 오버라이드. P3-2 `dismissSplit()` **실기기 E2E 검증**: 디바이더 드래그 아님(반증) — `PanelActivity.instance.finishAndRemoveTask()` + 인텐트 폴백[미검증], 진입 체크 = `isSplitActive` 자체 2s 조건 폴링(스크림發 a11y 목록 비원자 재구축 대응), 150ms/3s 해소 폴링, 실패 전부 토스트. `awaitWindowsSettled()` = beginSession freshness 게이트 |
| `service/ArrangeTriggerReceiver.kt` | ✅ adb 디버그 트리거 (`dev.dj.foldwindow.ARRANGE`). 버블 도입 후에도 회귀용으로 유지 |
| `service/FloatingLauncherService.kt` | ✅ P3-1 + P3-2 확장 메뉴 **실기기 E2E 검증**. 탭=배치, 드래그=이동+스냅, **롱프레스=메뉴 열기**(위/아래 배치·분할 해제·프리셋(JSON SSOT 파싱 캐시)·설정). 메뉴 = **풀스크린 투명 스크림** FrameLayout 창 (ACTION_OUTSIDE 방식은 디스패치 순서 경합 실측으로 폐기 — 스크림이 모든 터치 선점, 재탭=닫기만 구조 보장). 함정 #22: 모든 트리거 직전 + `setBubbleHiddenForArrange(true)` 시 메뉴 제거. 위치/켬 상태 SharedPreferences (P3-3 에서 DataStore 이관) |
| `service/BootReceiver.kt` | ✅ P3-1 신규. BOOT_COMPLETED 시 bubble_enabled+오버레이 권한 확인 후 FGS 재기동. **[미검증]** 실부팅 |
| `ui/OnboardingActivity.kt` | ✅ P3-4 신규 **실기기 검증** (권한 감지·버블 토글·안내 렌더 확인). 권한 카드 3종 + 버블 시작/중지 + 사용 안내 (넷플릭스 "배치 후 재생" 포함). MAIN/LAUNCHER 진입점 |
| `ui/PanelActivity.kt` | ✅ P2-5 + P3-2. 검정 배경+시계 파트너 창. 라벨 "FW Panel" = SplitEntry 피커 셀렉터 계약. P3-2: `instance` 정적 참조 + `EXTRA_FINISH_PANEL` (dismissSplit 의 패널 finish 경로 — finish 실기기 검증, 인텐트 폴백 [미검증]). MAIN/LAUNCHER 노출은 Phase 3 재검토 |
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
7. `AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE = 0.25f` — 실기기 미검증. ADAPTIVE 경로 신뢰도 상한이 0.6이므로 그 이하로 유지해야 함. 실사용 데이터로 튜닝
8. ~~PaneSwapper 셀렉터~~ → **"창 전환" 실측 확정** (팝업 노드 3종: "App pair 추가 위치"/"창 전환"/"시계 방향으로 회전"). 하단 배치 E2E 성공
9. ~~step 파트너 경로~~ → **피커 노드 탭이 1차 확정**. LAUNCH_ADJACENT 는 분할 선택 상태를 파괴(전체화면 강탈)해 최후 폴백으로 강등
10. ~~`defaults.closedLoopCorrection` JSON 토글 미배선~~ → **배선 완료** (2026-07-25 오후). 추가 규칙: aspectSource=PROFILE 이면 무조건 보정 생략 (오염 측정이 프로파일을 덮어쓰는 실측 사고 2회 대응)
11. 대상 앱 라벨 조회 — `<queries>` 블록으로 실기기 정상 동작 확인 (label=YouTube 조회 성공)
12. 사전 실측 오염: 플레이어 컨트롤 오버레이/앰비언트 글로우가 떠 있으면 종횡비 오측 (1.333/1.12 관측. **추가 실측 2026-07-25 오후 2차: 영상 시작 직후 탭 → 추천화면/인트로 오염 1.6 오측(conf 0.60), 어두운 장면이라 verify residual=0 오판**). 신뢰도 필터 또는 이중 샷 비교 검토 — Phase 3 우선순위 상향
13. 필러박스 맹점: 과소 이동 시 `residualBars=0` 으로 verified 오판 가능. 열 방향 잔여 검출 추가 검토 — Phase 3
14. BOTTOM 배치 최적화: 현재 상단 도킹 후 "창 전환" 스왑. step2 드롭 지점을 하단 가장자리로 바꾸면 스왑 생략 가능한지 실기기 확인 — Phase 3
15. ~~step2 성공 조건 폴링 예산~~ → **실질 해소** (2026-07-25 오후 2차). 실측: 잔여 폴링 ~370ms 로 애니메이션 정착 불가 → 실패 판정. 예산 분리 대신 **다음 시도의 목표 상태 선체크**가 늦은 정착을 흡수하는 설계 채택 — 회귀 E2E 에서 시도1 실패→시도2 선체크 즉시 성공 실증. 타임아웃 값 무변경
16. ~~넷플릭스(UNRESIZEABLE) 분기 자동화~~ → **완료 + 실기기 E2E 검증** (2026-07-25 오후, 6회). 감지 = privateFlags 필드 리플렉션 + 폴백 비트 1<<11 (실측 교차 검증)
17. ~~넷플릭스 재생-분할 관계~~ → **특성 규명 완료**: 분할 페인 안에서 재생 시작 = 유지 / 재생 중 메뉴 진입 = 재생 세션이 "최소화된 플레이어" 팝업으로 분리 (3회+ 재현, One UI 동작). v1 지침 = "배치 후 재생". Phase 3 온보딩/토스트에 안내 반영
18. ~~step2/3 성공 조건 오탐~~ → **완결** (2026-07-25 오후 2차). 유튜브 DRAG 회귀 E2E 통과. `isSplitSelectTopPane` 임계(전폭≥90%, `EDGE_DOCK_TOLERANCE_PX=40`) 는 ground truth(대상 페인 `[0,0][2184,977]`, 도킹 0px)로 **[검증]**. 회귀에서 발견된 실버그 2종(유령 매치/성공 미인지)은 SplitEntry 수정 완료
19. 회전 결과 페인 위치 비결정 (넷플릭스 상단 3회/하단 2회) — 원인 미상. 하단 낙착 시 교정 체인: PaneSwapper(재시도 3회) → 회전×2 폴백(**미발동·미검증**) → 실패 시 하단 유지+토스트
20. "창 전환" ACTION_CLICK 무효 (회전 직후 컨텍스트 2회 실측, 유튜브 세션에선 성공) — 원인 탐구 필요. 좌표 탭 제스처로 대체 시도 검토. **2026-07-25 오후 4차 보강: step3 피커 탭도 동일 계열 변동성 실측** — 메뉴發 배치 4회 중 2회 step3 3연속 실패(클릭 무효 2회 = 액티비티 생성 이벤트 없음 / `startActivityFromRecents` 오라우팅 1회), 직후 재시도는 성공. 성공 시그니처 = `startActivityAsUser:launcher` (DEVICE_FACTS P3-2 절)
21. PROFILE 보정 생략으로 verify 측정값(residual 122~224)은 보고 전용 — 컨트롤 오버레이 오염이라 신뢰 낮음. Phase 3 신뢰도 필터(#12)와 함께 재설계
22. 버블 오버레이 존재 시 피커發 파트너가 전체화면 낙착하는 **메커니즘 불명** (One UI WM 라우팅 추정) — 현재 경험 법칙(세션 중 버블 숨김)으로 해소. One UI 업데이트 시 재검증 필요
23. P3-1 잔여 [미검증]: BootReceiver 실부팅 복귀, specialUse FGS 의 BOOT_COMPLETED 시작 허용, 버블 제스처 임계값 실사용감, 30s 안전 타이머 vs 최장 세션(교정 체인 포함) 여유
24. ~~P3-2 [미검증] 전체~~ → **2026-07-25 오후 4차 실기기 검증으로 전부 해소**: ① 드래그 해제 가정 **반증** (dispatchGesture 스냅백 2/2 vs 동일 기하 input swipe 성공 3/3) → **패널 finish 방식으로 재구현·E2E 성공** ② 클램프 가로/세로 실기기 정상 ③ DOWN 스냅샷 방어 **실패 실측** (OUTSIDE 선행 디스패치 → 재탭이 배치 오발화) → **풀스크린 스크림으로 구조 해결·E2E 확인** ④ 프리셋 6종 최초 롱프레스에 정상 렌더. 잔여 [미검증]: dismissSplit 인텐트 폴백(instance null 희귀 경로), "분할 없음" 시 2s 대기 후 토스트 체감
25. 스크림 부산물 (해결·기록): 풀스크린 터치 가능 오버레이가 떠 있는 동안 하위 창이 a11y `getWindows()` 에서 가림-제외되고, 제거 직후 재구축이 **비원자적** (앱 창 먼저, 디바이더 나중) — `isSplitActive` false-negative 2/2 실측. dismiss 는 목표 조건 자체 폴링으로 해결. `beginSession` 의 `awaitWindowsSettled`(APPLICATION≥1 약한 게이트)도 같은 원리에 취약할 수 있음 — 배치 경로에서 유사 증상 재현 시 동일 패턴 적용

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
