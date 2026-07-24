# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-25 (P1-1·P1-4·Detector v2 완료, qa-verifier 통과)
**현재 Phase:** Phase 1 — 도메인 확정 (진행 중)
**다음 행동:** P1-2(window_profiles.json 스키마+로더)·P1-3(AspectResolver 3단 폴백) 착수

---

## Phase 상태

| Phase | 상태 | 비고 |
|---|---|---|
| Day 0 수동 검증 | ✅ 완료 | #1·#2·#3 통과, #4는 대체 불가로 판정 |
| 부트스트랩 | ✅ 완료 | AGP 8.11.1 / Kotlin 2.1.0 / Gradle 8.13 wrapper / compileSdk 36. assembleDebug·testDebugUnitTest 통과 (32/32) |
| Phase 0 프로브 | ✅ 완료 | 3회 실행(전체화면/분할활성/가로영상). #5 ✅ #6 ❌ #7 ✅(분할 중에만). E는 유튜브 앰비언트 모드로 미검출 → Detector v2 필요. `docs/DEVICE_FACTS.md` 확정 |
| Phase 1 도메인 | 🔄 진행 중 | P1-1 ✅ / P1-4 ✅ / Detector v2 ✅ (전체 68 테스트 통과, qa-verifier 승인). 남은 것: P1-2, P1-3 |
| Phase 2 액추에이터 | ⬜ 미착수 | |
| Phase 3 UI | ⬜ 미착수 | |
| Phase 4 확장 | ⬜ 조건부 | #5 판정에 따라 범위 결정 |

## 작성된 코드

| 파일 | 상태 |
|---|---|
| `domain/SplitPlanner.kt` | ✅ P1-1 반영. `foldSevenLandscape()` = divider 14px / minPane 181px (실측). 테스트 22개 |
| `domain/LetterboxDetector.kt` | ✅ v2 하이브리드. 순흑(0.97) 우선 → luma 통계 기반 적응 폴백(`ADAPTIVE_*` 상수 4종). `resolveAspect` 진입점 불변. 테스트 26개 |
| `domain/ArrangeStateMachine.kt` | ✅ P1-4 신규. 순수 리듀서 `reduce(state, event, config)`. 상태 8종·실패 사유 7종 전부 테스트 커버. 시간은 이벤트 nowMs로만 유입(ADR-2). 스크린샷 백오프(`MeasureLetterbox.notBeforeMs`) 도메인 강제. 테스트 20개 |
| `platform/ScreenshotSampler.kt` | ✅ v2 확장. 행별 luma 평균/분산 산출 추가. 실기기 미검증 |
| `probe/ProbeAccessibilityService.kt` | ✅ 실기기 검증 완료 (3회 실행) |
| `probe/ProbeReport.kt` | ✅ 완성 |
| `probe/ProbeActivity.kt` | ✅ 실기기 검증 완료 |
| `probe/ProbeTriggerReceiver.kt` | ✅ 신규. adb 브로드캐스트로 프로브 트리거 (`RUN_PROBE`). Phase 0 이후 제거 대상 |
| Gradle / Manifest / 접근성 XML | ✅ 부트스트랩에서 확정. AGP 8.11.1, Gradle 8.13 wrapper, `org.gradle.java.home`=Android Studio JBR(머신 종속 경로 주의) |

## 열린 질문

1. `ScreenshotSampler` 의 `rowStride` 축소가 종횡비 역산 정밀도에 미치는 영향 — 실측으로 확인
2. One UI 디바이더 드래그 시 `continueStroke` 분할이 단일 스트로크보다 안정적인지 — Phase 2에서 비교
3. wavve 패키지명 확인 필요
4. ~~minPaneHeight 실측~~ → 세로 좌우 분할 181px 확정. 가로 상하 분할은 미검증 (DEVICE_FACTS 참조)
5. ~~LetterboxDetector v2 설계~~ → 구현 완료. 남은 것: `ADAPTIVE_*` 상수(분산≤400, luma≤90, ref±28)의 실기기 재검증 — 앰비언트 영상에서 프로브 E 재실행해 16:9 스냅 확인. 순흑 조건(넷플릭스 등) E 실측도 아직 0건. `ScreenshotSampler`의 luma 통계 산출도 실기기 미검증
6. Recents 분할 진입 셀렉터의 다국어 안정성 (한국어만 검증됨)

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
