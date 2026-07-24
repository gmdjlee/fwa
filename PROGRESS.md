# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-25 (Phase 0 실기기 프로브 완료)
**현재 Phase:** Phase 1 — 도메인 확정 (착수 대기)
**다음 행동:** P1-1(실측값 반영)·P1-4(상태 머신)부터 착수. LetterboxDetector v2(하이브리드)를 P1 범위에 추가

---

## Phase 상태

| Phase | 상태 | 비고 |
|---|---|---|
| Day 0 수동 검증 | ✅ 완료 | #1·#2·#3 통과, #4는 대체 불가로 판정 |
| 부트스트랩 | ✅ 완료 | AGP 8.11.1 / Kotlin 2.1.0 / Gradle 8.13 wrapper / compileSdk 36. assembleDebug·testDebugUnitTest 통과 (32/32) |
| Phase 0 프로브 | ✅ 완료 | 3회 실행(전체화면/분할활성/가로영상). #5 ✅ #6 ❌ #7 ✅(분할 중에만). E는 유튜브 앰비언트 모드로 미검출 → Detector v2 필요. `docs/DEVICE_FACTS.md` 확정 |
| Phase 1 도메인 | ⬜ 미착수 | Phase 0 결과 의존 |
| Phase 2 액추에이터 | ⬜ 미착수 | |
| Phase 3 UI | ⬜ 미착수 | |
| Phase 4 확장 | ⬜ 조건부 | #5 판정에 따라 범위 결정 |

## 작성된 코드

| 파일 | 상태 |
|---|---|
| `domain/SplitPlanner.kt` | ✅ 완성. 테스트 16개 |
| `domain/LetterboxDetector.kt` | ✅ 완성. 테스트 14개 |
| `platform/ScreenshotSampler.kt` | ✅ 완성. 실기기 미검증 |
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
5. LetterboxDetector v2: 하이브리드(순흑 우선 → 균일도/적응 임계 폴백) 설계 — 유튜브 앰비언트 모드 대응. 순흑 조건(넷플릭스 등) E 실측도 아직 0건
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
