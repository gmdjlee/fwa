# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-25 오후 (넷플릭스 MENU 레시피 **실기기 E2E 완료** — 트리거→배치 4.7초, 디바이더 1235 정확. Phase 2 DoD ② 달성. 131 테스트 통과)
**현재 Phase:** Phase 2 **완료** → Phase 3 (플로팅 UI + 프로파일) 착수 대기
**다음 행동 (새 세션 착수 목록):**
1. 유튜브 DRAG 레시피 회귀 확인 1회 (step2 강화 조건 `isSplitSelectTopPane` — `EDGE_DOCK_TOLERANCE_PX=40` [미검증]. 오늘 세션은 넷플릭스만 돌림)
2. Phase 3 착수: P3-1 플로팅 버블 (adb 트리거 → 버블 대체), P3-4 온보딩. 설계 시 오늘 발견 반영 — 넷플릭스류는 "배치 후 재생" 순서 안내 필요 (재생 중 진입 시 팝업 분리, DEVICE_FACTS 참조)
3. Phase 3 중 검토: 회전×2 폴백 실기기 검증 기회 확보, "창 전환" 무효 원인 탐구 (열린 질문 참조)
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
| Phase 2 액추에이터 | ✅ 완료 | DoD ① 검은띠0 ✅ ② 넷플릭스 ✅(MENU 레시피 E2E 6회, 4.7초, 디바이더 1235 정확 — 단 재생은 "배치 후 시작" 순서 필요) ③ 상하전환 ✅ ④ 실패노출 ✅. 131 테스트. 잔여: 유튜브 회귀 1회 확인, 회전×2 폴백 미발동(미검증) |
| Phase 3 UI | ⬜ 미착수 | |
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
| `platform/SplitEntry.kt` | ✅ P2-3 **실기기 검증**. `EntryRecipe` 분기: DRAG 3단계 / MENU 5단계(UNRESIZEABLE 전용, E2E 6회 성공). step2/3 성공 조건 PaneGeometry 도메인 판정. 회전은 `DividerPopupRotator` 위임 |
| `platform/ResizeModeDetector.kt` | ✅ 신규 **실기기 검증**. `privateFlags` 필드 리플렉션 allowed / 상수 리플렉션 denied(max-target-o) → 폴백 비트 **1<<11** (0x8c000910 교차 검증). 실패 시 null → DRAG 폴백 |
| `platform/DividerPopupRotator.kt` | ✅ 신규. 핸들 탭→"시계 방향으로 회전" 클릭 공용화 (MENU step5 + 서비스 회전×2 폴백). step5 경로 실기기 검증, 회전×2 폴백은 미발동 **미검증** |
| `platform/GestureDrags.kt` | ✅ 신규·실기기 검증. 2-페이즈 홀드드래그(1px 드리프트 홀드 + continueStroke 이동, 타이밍 가드). API 함정 4종 문서화 |
| `service/ArrangerAccessibilityService.kt` | ✅ **실기기 검증** (유튜브+넷플릭스). 상태 머신 구동, 레시피 선택 배선, pre-measure **페인 크롭**, `pickPaneLike` 위치 판정, `purgeStalePanelTasks`, 스왑 실패 시 회전×2 폴백, `closedLoopCorrection` 배선(PROFILE 시 off), 세션 `dragTimeoutMs=12s` 오버라이드 |
| `service/ArrangeTriggerReceiver.kt` | ✅ adb 디버그 트리거 (`dev.dj.foldwindow.ARRANGE`). Phase 3에서 플로팅 버블로 대체 |
| `ui/PanelActivity.kt` | ✅ P2-5. 검정 배경+시계 파트너 창. 라벨 "FW Panel" = SplitEntry 피커 셀렉터 계약. MAIN/LAUNCHER 노출은 Phase 3 재검토 |
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
12. 사전 실측 오염: 플레이어 컨트롤 오버레이/앰비언트 글로우가 떠 있으면 종횡비 오측 (1.333/1.12 관측). 신뢰도 필터 또는 이중 샷 비교 검토 — Phase 3
13. 필러박스 맹점: 과소 이동 시 `residualBars=0` 으로 verified 오판 가능. 열 방향 잔여 검출 추가 검토 — Phase 3
14. BOTTOM 배치 최적화: 현재 상단 도킹 후 "창 전환" 스왑. step2 드롭 지점을 하단 가장자리로 바꾸면 스왑 생략 가능한지 실기기 확인 — Phase 3
15. step2 성공 조건 폴링 예산: 드래그가 1.1s 소모해 잔여 폴링 ~1.4s. 전환 애니메이션이 길면 재시도 낭비 — 예산 분리 검토
16. ~~넷플릭스(UNRESIZEABLE) 분기 자동화~~ → **완료 + 실기기 E2E 검증** (2026-07-25 오후, 6회). 감지 = privateFlags 필드 리플렉션 + 폴백 비트 1<<11 (실측 교차 검증)
17. ~~넷플릭스 재생-분할 관계~~ → **특성 규명 완료**: 분할 페인 안에서 재생 시작 = 유지 / 재생 중 메뉴 진입 = 재생 세션이 "최소화된 플레이어" 팝업으로 분리 (3회+ 재현, One UI 동작). v1 지침 = "배치 후 재생". Phase 3 온보딩/토스트에 안내 반영
18. ~~step2/3 성공 조건 오탐~~ → **강화 완료 + MENU 경로로 간접 검증**. 잔여: 유튜브 DRAG 회귀 1회 확인 (`EDGE_DOCK_TOLERANCE_PX=40` [미검증])
19. 회전 결과 페인 위치 비결정 (넷플릭스 상단 3회/하단 2회) — 원인 미상. 하단 낙착 시 교정 체인: PaneSwapper(재시도 3회) → 회전×2 폴백(**미발동·미검증**) → 실패 시 하단 유지+토스트
20. "창 전환" ACTION_CLICK 무효 (회전 직후 컨텍스트 2회 실측, 유튜브 세션에선 성공) — 원인 탐구 필요. 좌표 탭 제스처로 대체 시도 검토
21. PROFILE 보정 생략으로 verify 측정값(residual 122~224)은 보고 전용 — 컨트롤 오버레이 오염이라 신뢰 낮음. Phase 3 신뢰도 필터(#12)와 함께 재설계

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
