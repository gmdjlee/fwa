# DESIGN #12 — 측정 신뢰도: 단일 프레임 점수 폐기, 2-샷 합치(consensus) 게이트

> 2026-07-25 저녁 11차 검토 확정. 구현 전 [미검증].
> 관련: PROGRESS 열린 질문 #12(본건)·#7(신뢰도 하한)·#13(필러박스 맹점)·#21(verify 보고 전용), P3-3 보류 항목(측정 캐싱).

## 0. 한 줄 요약

오염 프레임이 conf 0.60~0.97 로 실측됐다 — 단일 프레임 신뢰도 점수는 오염을 원리적으로 못 거른다.
신뢰도의 정의를 "한 프레임이 얼마나 깨끗한가"에서 **"시각·스캔 축·화면 컨텍스트가 다른 두 측정이 합치하는가"** 로 교체한다.
진입 전 행축 pre-measure(기존) × 진입 후 열축 confirm(신규)의 합치가 MEASURED 채택 조건.

## 1. 문제와 실측 근거

사고 목록 (전부 실기기, DEVICE_FACTS):

| 사고 | 프레임 | 측정값 | conf | 결과 | 현 방어 |
|---|---|---|---|---|---|
| 분할/홈 UI 혼입 ① | 전체화면 스캔 | 1.14 | 0.91 | 오배치 | 페인 크롭 (해결) |
| 분할/홈 UI 혼입 ② | 전체화면 스캔 | 2.95 | 0.97 | 페인 ~575px 압착 | 페인 크롭 (해결) |
| 컨트롤 오버레이 | pre-measure | 1.333 / 1.12 | 미기록 | 오측 | 없음 |
| 인트로/추천 화면 | pre-measure | 1.6 | 0.60 | divider 1372 과소 배치 | 없음 |
| verify 컨트롤 오염 | 드래그 직후 재측정 | residual 122~224 | — | PROFILE 배치 과축소(1235→1011) | PROFILE 보정 생략 (부분) |
| verify 암장면/글로우 은폐 | 1.6 사고의 verify | residual=0 오판 | — | 오배치 고착 | 없음 |

구조적 사실:

1. `confidence` = 띠의 순도 × 경계 대비 (`LetterboxDetector.confidenceOf`). **"이 프레임이 실제 영상인가"는 측정 대상이 아니다.** 추천 화면의 어두운 영역도 깨끗한 띠로 보인다.
2. 오염 conf(0.60~0.97) ≥ 정상 ADAPTIVE conf 상한(0.6) → `DEFAULT_MIN_MEASUREMENT_CONFIDENCE`(0.25) 를 어떤 값으로 올려도 오염은 통과하고 정상 ADAPTIVE 측정(실측 conf 0.57~0.60)만 죽는다. 열린 질문 #7 의 "0.25 튜닝" 접근 자체가 막다른 길.
3. 오염원은 전부 **일시적**이다: 컨트롤 자동 숨김 ~3s, 인트로/추천 화면은 재생 진행으로 소멸. 반면 진짜 레터박스는 지속한다. → 시간 분리가 유일하게 견고한 판별 신호.
4. verify 는 순흑 임계 단독(`residualBars`)이라 앰비언트 글로우/암장면에서 잔여를 0 으로 은폐한다 — 최저 신뢰 컴포넌트다.

## 2. 기각한 대안

| 대안 | 기각 근거 |
|---|---|
| 신뢰도 임계 상향 | §1-2. 어떤 임계도 오염과 정상을 분리 못 함 |
| `KNOWN_ASPECTS` 축소 (1.6/1.333 제거) | 16:10·4:3 은 정당한 영상 AR. 스냅 실패는 미스냅 raw 채택으로 이어질 뿐 차단이 아님 |
| 띠 대칭성 휴리스틱 | 유튜브 전체화면 컨트롤은 상단 타이틀+하단 스크럽 = 화면 기준 상하 대칭 → 대칭 오염이 통과 가능. 사고 당시 밴드 기하 미기록이라 판별력 검증 불가 (→ §5 로깅부터) |
| verify 강화 단독 (사후 보정 의존) | ADR-5 보정은 정확히 1회 예산 — 오염 계획에 소진하면 끝. verify 자체가 은폐(#12 사고)·오염(#21) 실측된 최저 신뢰 컴포넌트다. 최종 방어를 거기 걸 수 없다 |
| 이중 샷 (pre 2연속) | 레이트리밋 ~1s → 트리거 직후 순수 +1s 지연. 동일 컨텍스트 연속 샷은 수 초 지속하는 인트로를 못 거름 |

## 3. 확정 설계 — 2-샷 합치 게이트

### 3.1 원리

MEASURED 채택 조건 = 서로 다른 (시각, 스캔 축, 컨텍스트)의 두 측정이 상대 오차 3% 이내 합치:

- **측정 A** (기존 pre-measure): 트리거 시각 t0, 전체화면, **행축**. 전체화면 2184×1968 AR≈1.11 < 모든 영상 AR → 항상 상하 띠.
- **측정 B** (신규 confirm): 진입 완료 후 첫 드래그 직전 (t0+2~4s), 분할 페인 크롭, **양축 스캔**. 기본 분할 페인 AR≈2.2 > 대부분 영상 AR → 좌우 필러박스(열축). 2.3+ 영상만 행축.

오염은 일시적·컨텍스트 종속(§1-3) → 두 측정을 **같은 값으로** 오염시킬 확률이 구조적으로 낮다. 컨트롤(수평 줄무늬)은 행축을 오염시키는 방식과 열축을 오염시키는 방식이 다르다.

지연 비용: MEASURED 후보 세션만 +~0.3s (스크린샷+스캔 1회). PROFILE / aspectOverride / pre 실패 세션 = 0.

### 3.2 세션 플로우

```
beginSession:
  pre = preMeasureAspect()                    # 기존. 행축
  resolved#1 = resolve(profile, pre, preset)  # 기존. 잠정 계획용
  plan#1 → Start(dividerCenterY)              # 진입 단계는 aspect 를 소비하지 않음 — 잠정이어도 무해

handleDragDividerTo (첫 드래그 && source==MEASURED 후보일 때만):
  confirm = confirmMeasure()                  # 신규. 페인 크롭, 양축
  agreed  = MeasurementConsensus.agree(pre, confirm, paneAspect)
  agreed != null → resolvedAspect = MEASURED(agreed); 재계획; realTargetY 갱신
  agreed == null → resolvedAspect = PRESET 폴백; 재계획; realTargetY 갱신; 양측 값 로그
  이후 기존 스왑 체크 → 드래그
```

**머신 무변경.** `handleDragDividerTo` 는 이미 머신의 targetY 를 자문 값으로 취급하고 서비스측 `realTargetY` 로 덮어쓰는 선례(스왑 실패 재계획)를 갖는다 — 같은 메커니즘 재사용. Dragging 세션 예산 12s 가 +0.3s 를 수용. 보정 재드래그(Verifying→Dragging 재진입)에서는 confirm 을 반복하지 않는다 (세션 1회 플래그).

### 3.3 합치 규칙 (순수 도메인 `MeasurementConsensus`)

| pre | confirm | 판정 |
|---|---|---|
| 유효 | 유효, snap 값 동일 | 합치 → snap 값 |
| 유효 | 유효, snap 불일치·relΔ(raw)≤3% | 합치 → confirm 값 (크롭이 더 깨끗하고 더 최신) |
| 유효 | 유효, relΔ>3% | 불합치 → PRESET |
| 유효 | 양축 무띠 ∧ \|paneAR−pre\|/pre ≤3% | 합치 → pre 값 (무띠 자체가 "영상 AR ≈ 페인 AR" 증거) |
| 유효 | 양축 무띠 ∧ 그 외 | 불합치 → PRESET |
| 유효 | **양축 모두 띠** | 불합치 → PRESET (aspect-fit 영상은 한 축만 띠 가능 — 양축 띠 = 추천 화면류 물증) |
| 유효 | 측정 실패 (샷/rect/레이트리밋) | 불합치 → PRESET (확인 불가 ≠ 무죄. 사후 수렴은 verify 보정 담당 §3.5) |
| null | — | confirm 생략, PRESET (기존 동작과 동일. confirm 단독 채택 금지 — 단일 프레임 신뢰로의 회귀) |

per-측정 conf ≥ 0.25 게이트는 유지하되 역할 격하: "후보 자격"만 부여. **채택은 합치가 결정한다.**

### 3.4 confirm 의 축 문제 — 신규 도메인 로직 (#13 과 공유)

분할 페인 2184×~977 에 16:9 영상 → 높이 제약 → **좌우 필러박스**. 행 스캔은 무용. 필요물:

- **열축 스캔**: `ScreenshotSampler` 에 transpose 산출 (colDarkRatio/colMeanLuma/colVariance). 기존 `LetterboxScan` 재사용 — entries=열(좌→우), `width` 자리=페인 높이(동일 stride 좌표계 환산).
- **`LetterboxDetector.resolveAspectPillarbox(scan)`**: `detectHybrid` 그대로 재사용, 역산만 역수 — aspect = band.height(=콘텐츠 폭) / scan.width(=페인 높이).
- **양축 실행 + exactly-one 규칙** (§3.3 표).

이 로직이 **#13 필러박스 맹점의 도메인 기반과 동일**하다. v1 에 `residualColumns`(residualBars 의 열축 판)를 verify 에 **로그 보고로만** 동봉한다 — `verified` 플래그 의미론 변경(토스트 변화)은 v1.5 로 분리해 한 변경에 싣지 않는다.

### 3.5 폴백 방향의 안전성

불합치 → PRESET(기본 16:9):

- 대부분 콘텐츠가 16:9 → 오염 케이스 대부분에서 결과적으로 정답 (1.6 사고: 불합치→PRESET 1.7778 = 정답이었을 값).
- 비-16:9 정상 콘텐츠가 오탐으로 PRESET 낙착 시 → PRESET 은 `closedLoopCorrection` ON → verify 의 `correctedTargetY` 기존 경로가 수렴 (영화 2.35 등 자가 치유, 보정 1회 비용).
- 즉 게이트 **오탐 비용 = 보정 드래그 1회**, 게이트 부재 시 **미탐 비용 = 오배치 고착** (1.6 사고 실증). 비대칭이 게이트 도입에 유리.

### 3.6 롤백 레버 (#20 관례: 1줄 토글)

`defaults.requireMeasurementAgreement: Boolean` (기본 **true**). false → 기존 동작(pre 단독 + 0.25 게이트). `WindowProfilesParser` + config 스키마 확장.

## 4. 인접 결함 — 함께 수정 (실증 확인됨)

`aspectOverride` (메뉴 프리셋 선택 / adb `--ef aspect`, PROGRESS 표기 "종횡비 **강제**") 가 tier ③ presetAspect 로만 주입돼 **tier ② 측정에 밀린다** — 사용자가 21:9 를 명시 선택해도 측정 1.778 이 조용히 이김. 문서화된 "강제" 의미론 위반.

수정: aspectOverride 존재 시 resolve 생략, **tier 0 직접 채택** + pre/confirm 샷 전부 생략 (지연 0, 레이트리밋 예산 절약, 회귀 명령 의미론 정상화). **ADR-5 보정도 생략** — verify 측정이 사용자 "강제"를 재차 덮어쓰면 안 됨 (PROFILE 보정 생략과 동일 논리, 잔여값은 보고만).

## 5. 로깅 (오늘의 공백 메움 — 필수)

- 모든 측정: axis / method / raw / snapped / conf / bandTop·bandBottom (stride 환산 px).
- 합치 판정: 양측 요약 + 판정 결과 1줄.
- 목적: §2 대칭성 휴리스틱 등 후속 판별력 검증의 데이터 기반. 사고 당시 밴드 기하 미기록이 현재 설계 검증을 막고 있다 — 반복 금지.

## 6. 측정 캐싱 함의 (후속 — 본 설계로 차단 해제)

P3-3 에서 보류된 측정 캐싱의 admission predicate 확정: **합치 통과 ∧ Done(verified=true)**.
ProfileStore `measuredAspect(pkg)` 설계는 후속 작업. 합치 없는 캐싱 = 오염 고착 (실측 사고 2회) — 본 게이트가 전제조건.

**→ 2026-07-26 12차 설계 확정·구현 완료 (JVM 204 테스트·qa PASS 결함 0, 실기기 [미검증] — DEVICE_FACTS 미검증 표):**

- 티어: PROFILE > MEASURED > **CACHED** > PRESET (`AspectSource.CACHED` 신설, PRESET 처럼 JSON 금지). 캐시 = "같은 앱의 이중 검증된 사전값(prior)" — 신규 합치 측정을 절대 이기지 않는다.
- 적용 2지점: ① beginSession resolve (pre 실패/저신뢰 시 PRESET 전에 CACHED) ② finishConfirm 불합치 폴백 (`sessionCachedAspect ?: sessionPresetAspect`). confirm 발동 조건(`requireAgreement && source==MEASURED`) 무변경 — CACHED 세션은 confirm 을 돌리지 않는다 (§3.3 "confirm 단독 채택 금지" 보존).
- admission 구현: finishConfirm 에서 `ConsensusResult.agreed` 일 때만 `consensusAdoptedAspect` 기록 → reportTerminal Done(verified=true) ∧ 레버 on 에서만 저장. requireAgreement=false 롤백 세션은 confirm 자체가 미실행이라 저장도 자동 차단 — 단일 프레임 값의 캐시 유입 경로 부재 (qa 쓰기 지점 전수 확인 2곳: 리셋·합치 기록뿐). CACHED/PRESET/PROFILE 세션 자기 갱신 없음. placement 저장과 달리 effectivePlacement 조건 무관 (종횡비 = 콘텐츠 속성, 위치와 직교).
- 불합치 폴백을 PRESET 대신 캐시 우선으로 바꾼 근거: §3.5 의 PRESET 논리 자체가 "사전확률" 논증 — 캐시는 같은 앱의 합치∧verified 이력이라 정적 16:9 보다 정보량 우위, 치유 경로 동일 (CACHED 도 closedLoopCorrection ON — 기존 식 `source != PROFILE` 자동 커버). 오류 비용 = 보정 1회로 대칭.
- 무 TTL·last-write-wins·패키지당 float 1개 (`measured_aspect.<pkg>`). 저장/조회 양측 범위 검증 (NaN/∞/1.0..4.0 밖 → 저장 거부/조회 null — placement 오염→null 패턴 미러).
- 롤백 레버: `defaults.cacheMeasuredAspect` (키 부재=true, 시드 JSON 무수정 — `requireMeasurementAgreement` 선례). false → 조회·폴백·저장 전부 종전 동작.
- 기각: pre×캐시 합치로 confirm 샷 생략 (+~0.3s 절약, 레이트리밋 여유) — G1~G5 채택 직후 새 채택 경로 추가는 실기기 검증 부담 > 이득. v1.5 재고 후보.

## 7. v1 범위

**IN**: `MeasurementConsensus` + 열축 도메인(detect/역산/residualColumns) + 샘플러 transpose + confirm 배선(handleDragDividerTo 선두) + PRESET 폴백 + JSON 토글 + aspectOverride tier 0 + 측정/합치 로깅
**OUT (v1.5 이월)**: `verified` 플래그에 열축 반영(#13 완결) · 적응형 residual(글로우 은폐 — §5 데이터 수집 후) · ~~측정 캐싱(§6)~~(12차 구현 완료) · 대칭성 휴리스틱

## 8. 구현 전 확인 항목 (Worker 브리프 포함용)

1. `FloatingLauncherService` 프리셋 메뉴 → `startArrange(aspectOverride)` 전달 경로 확인 (§4 수정 범위 확정).
2. confirm 레이트리밋: pre(t0)→confirm(t0+진입) 간격 1s 미만인 초고속 진입 시 `SCREENSHOT_MIN_INTERVAL_MS` 백오프 (기존 `handleMeasureLetterbox` waitUntil 패턴 재사용). 진입 실측 2~4s 라 통상 무대기.
3. confirm→verify 간격: 드래그(0.3~1.1s)+정착 600ms ≈ 0.9~1.7s — 기존 waitUntil 백오프가 흡수하는지 수치 재확인.
4. 진입 직후 페인 리사이즈가 플레이어 컨트롤을 소환하는지 (미지 — 소환돼도 confirm 오염→불합치→PRESET, 안전 방향. G3 로 빈도만 관찰).

## 9. 검증 게이트

**JVM**: 열축 detect/역산/residualColumns (행축 테스트 미러 + 비대칭 케이스) · 합치 표 §3.3 전 행 · resolve 결합 (합치→MEASURED / 불합치→PRESET / override tier 0) · 파서 토글 왕복.
**실기기**:
- **G1 사고 재현**: 영상 시작 직후 버블 탭 (1.6 사고 조건) → 불합치 로그 + PRESET + 최종 residual=0
- **G2 컨트롤**: 플레이어 탭 후 3s 내 버블 탭 → pre 오염 → 불합치 → PRESET 정상 배치
- **G3 회귀**: 유튜브 클린 재생 3회 → 합치 → MEASURED 채택, residual=0, 총 소요 증가 ≤ +0.5s
- **G4 앰비언트**: ADAPTIVE pre (conf 0.57~0.60 실측 선례) × confirm 합치 → MEASURED 유지 (정상 ADAPTIVE 를 죽이지 않음을 확인)
- **G5 오버라이드**: 메뉴 프리셋 21:9 → 측정 생략·즉시 21:9 계획 로그
