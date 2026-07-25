# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-25 (Phase 2 실기기 검증 — DoD ①③④ 달성. ② 넷플릭스: 우회 경로 실측 확정, 자동화 미구현)
**현재 Phase:** Phase 2 — 유튜브 계열 완료. 넷플릭스(비리사이저블) 분기 구현이 다음 세션 과제
**다음 행동 (새 세션 착수 목록):**
1. `SplitEntry` 분기 구현: 대상 `ActivityInfo.resizeMode == UNRESIZEABLE` → 메뉴 레시피 (카드 탭 → "분할 화면으로 열기" → 피커 파트너 탭 → 디바이더 팝업 "시계 방향으로 회전" → 상하 전환). 전부 DEVICE_FACTS 실측 근거 있음
2. step2/step3 성공 조건 강화: 팝업(프리폼) 오탐 차단 — 대상 창 전폭(≥90% 폭)·상단 도킹 요구, LAUNCH_ADJACENT 폴백 성공 판정을 `PaneGeometry.isTopBottomSplit` 기반으로 교체
3. 넷플릭스 재생-분할 유지 조건 실기기 탐구 (Day 0 수동으론 가능 확인됨. 재생 먼저 → 분할 진입 순서 의심)
4. 완료 후 Phase 3 (플로팅 UI + 프로파일) 착수
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
| Phase 2 액추에이터 | 🟢 3/4 완료 | 실기기 E2E 성공(유튜브 상단×2·하단×1). DoD ① 검은띠0 ✅ ③ 상하전환 ✅("창 전환" 실측) ④ 실패노출 ✅. ② 넷플릭스: 우회 경로(메뉴→좌우→회전→상하) 수동 실측 완료, SplitEntry 분기 자동화 미구현. 109 테스트 통과 |
| Phase 3 UI | ⬜ 미착수 | |
| Phase 4 확장 | ⬜ 조건부 | #5 판정에 따라 범위 결정 |

## 작성된 코드

| 파일 | 상태 |
|---|---|
| `domain/SplitPlanner.kt` | ✅ P1-1 반영. `foldSevenLandscape()` = divider 14px / minPane 181px (실측). 테스트 22개 |
| `domain/LetterboxDetector.kt` | ✅ v2 하이브리드. 순흑(0.97) 우선 → luma 통계 기반 적응 폴백(`ADAPTIVE_*` 상수 4종). `resolveAspect` 진입점 불변. 테스트 26개 |
| `domain/ArrangeStateMachine.kt` | ✅ P1-4 신규. 순수 리듀서 `reduce(state, event, config)`. 상태 8종·실패 사유 7종 전부 테스트 커버. 시간은 이벤트 nowMs로만 유입(ADR-2). 스크린샷 백오프(`MeasureLetterbox.notBeforeMs`) 도메인 강제. 테스트 20개 |
| `domain/Profiles.kt` | ✅ P1-2 신규. `AspectSource`/`PartnerMode`/모델 4종 + `validate()` 위치 특정 에러. 순수 Kotlin |
| `domain/AspectResolver.kt` | ✅ P1-3 신규. ADR-1 3단 폴백(PROFILE→MEASURED→PRESET). `DEFAULT_MIN_MEASUREMENT_CONFIDENCE=0.25f`(미검증). 테스트 9개 |
| `data/WindowProfilesParser.kt` | ✅ P1-2 신규. kotlinx-serialization DTO→domain 매핑, 예외 누출 없이 `ProfilesParseResult`. 테스트 14개(실제 SSOT 파일 파싱 포함) |
| `platform/ScreenshotSampler.kt` | ✅ v2 확장. 행별 luma 평균/분산 산출 추가. 실기기 미검증 |
| `domain/PaneGeometry.kt` | ✅ P2-2 신규. 가시 교집합(오프스크린 슬라이드 함정 대응)·페인 간격 휴리스틱·상하 분할 판정. 순수 Kotlin. 테스트 12개 |
| `platform/DividerLocator.kt` | ✅ P2-2. `TYPE_SPLIT_SCREEN_DIVIDER` 핸들 중심 1차 → PaneGeometry 간격 휴리스틱 폴백. 실기기 미검증 |
| `platform/DividerDragger.kt` | ✅ P2-4 **실기기 검증**. SINGLE_STROKE 기본(확정). HOLD_THEN_MOVE 는 GestureDrags 위임 |
| `platform/PaneSwapper.kt` | ✅ **실기기 검증**. "창 전환" 셀렉터 실측 매치, 하단 배치 E2E 성공 |
| `platform/SplitEntry.kt` | ✅ P2-3 **실기기 검증**. 드래그 레시피 3단계(Recents→카드 홀드드래그→피커 탭). 구 메뉴 레시피는 [반증] 주석으로 보존 |
| `platform/GestureDrags.kt` | ✅ 신규·실기기 검증. 2-페이즈 홀드드래그(1px 드리프트 홀드 + continueStroke 이동, 타이밍 가드). API 함정 4종 문서화 |
| `service/ArrangerAccessibilityService.kt` | ✅ P2-1·P2-6. 상태 머신 구동(Main 한정), 5종 효과 실행기, ADR-1 사전 실측, ADR-5 폐루프 배선, 실패 7종 한국어 토스트. 실기기 미검증 |
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
6. Recents 분할 진입 셀렉터의 다국어 안정성 (한국어만 검증됨)
7. `AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE = 0.25f` — 실기기 미검증. ADAPTIVE 경로 신뢰도 상한이 0.6이므로 그 이하로 유지해야 함. 실사용 데이터로 튜닝
8. ~~PaneSwapper 셀렉터~~ → **"창 전환" 실측 확정** (팝업 노드 3종: "App pair 추가 위치"/"창 전환"/"시계 방향으로 회전"). 하단 배치 E2E 성공
9. ~~step 파트너 경로~~ → **피커 노드 탭이 1차 확정**. LAUNCH_ADJACENT 는 분할 선택 상태를 파괴(전체화면 강탈)해 최후 폴백으로 강등
10. `defaults.closedLoopCorrection` JSON 토글 미배선 (ADR-5 보정은 항상 켜짐). Phase 3에서 배선 (서비스 코드에 TODO)
11. 대상 앱 라벨 조회 — `<queries>` 블록으로 실기기 정상 동작 확인 (label=YouTube 조회 성공)
12. 사전 실측 오염: 플레이어 컨트롤 오버레이/앰비언트 글로우가 떠 있으면 종횡비 오측 (1.333/1.12 관측). 신뢰도 필터 또는 이중 샷 비교 검토 — Phase 3
13. 필러박스 맹점: 과소 이동 시 `residualBars=0` 으로 verified 오판 가능. 열 방향 잔여 검출 추가 검토 — Phase 3
14. BOTTOM 배치 최적화: 현재 상단 도킹 후 "창 전환" 스왑. step2 드롭 지점을 하단 가장자리로 바꾸면 스왑 생략 가능한지 실기기 확인 — Phase 3
15. step2 성공 조건 폴링 예산: 드래그가 1.1s 소모해 잔여 폴링 ~1.4s. 전환 애니메이션이 길면 재시도 낭비 — 예산 분리 검토
16. 넷플릭스(UNRESIZEABLE) 분기 자동화: 우회 경로 전체를 수동 실측으로 확정 (DEVICE_FACTS "비리사이저블 앱 분기"). `SplitEntry` 에 메뉴 레시피 구현 필요
17. 분할 상태에서 넷플릭스 재생 시작 → 분할 이탈 관측. Day 0 수동으론 분할 유지 가능 확인 — 재현 조건(진입 순서) 미상, 실기기 탐구 필요
18. step2/3 성공 조건 오탐: 팝업(프리폼) 창이 높이 15~75% 조건 통과. 전폭·상단 도킹 조건 보강 + LAUNCH_ADJACENT 성공 판정을 isTopBottomSplit 로 교체

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
