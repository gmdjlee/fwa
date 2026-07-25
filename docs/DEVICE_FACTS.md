# DEVICE_FACTS — 실기기 측정값

> ⚠ 이 파일의 숫자는 **측정값**이다. 근거 없이 바꾸지 마라 (CLAUDE.md 함정 #7).
> 바꿀 때는 새 측정 방법과 날짜를 함께 기록한다.
> 표기 규칙: **[측정]** = 실측, **[추정]** = 계산/유추, **[미검증]** = 아직 확인 못 함.

---

## 측정 이력

| 항목 | 값 |
|---|---|
| 측정 일시 | 2026-07-25 |
| 측정 대상 | samsung SM-F966N (Galaxy Z Fold 7), Android 16 / API 36 |
| 측정 방법 | 프로브 리포트 3종 (`probe_report.md` 전체화면세로 / `probe_report_split.md` 분할활성 / `probe_report_fullscreen.md` 가로전체화면) + Advisor의 `adb dumpsys window` · `input swipe` · `screencap` 실측 |
| 프로브 빌드 | `dev.dj.foldwindow` (Phase 0 probe) |

**좌표계 주의:** 자연 방향(세로)은 **1968(W) × 2184(H)**, 시청 시나리오(가로)는 **2184(W) × 1968(H)**.
삼성 공식 스펙 "2184 × 1968"은 가로 표기다. 세로에서는 좌우 분할, 가로에서는 상하 분할이 된다.

---

## 대상 기기

| 항목 | 값 | 출처 | 상태 |
|---|---|---|---|
| 모델 | Galaxy Z Fold 7 (SM-F966N) | dumpsys | [측정] |
| 내부 화면 (세로) | 1968 × 2184 px | 프로브 D / dumpsys | [측정] |
| 내부 화면 (가로) | 2184 × 1968 px | 프로브 D / dumpsys | [측정] |
| density | **2.25 (360 dpi)** | 프로브 D | [측정] |
| dp 크기 | 875 × 971 dp (세로) / 971 × 875 (가로) | 프로브 D | [측정] |
| smallestScreenWidthDp | 875 | 프로브 D | [측정] |
| OS | Android 16 / API 36 | 프로브 A | [측정] |
| One UI 버전 | `one_ui_version` 설정값 **비어 있음** (조회 실패). One UI 8 로 추정 | 사양 | [추정] |

> 기존 파일의 `density 2.0(추정) → 984×1092 dp` 는 **폐기**. 실측 2.25 / 875×971 dp 로 교정.

---

## Day 0 수동 검증 (완료 — 유지)

| # | 항목 | 결과 | 방법 |
|---|---|---|---|
| 1 | 계산 비율에서 유튜브 검은 띠 제거 | ✅ 가능 | 수동 디바이더 조작 |
| 2 | 디바이더 임의 비율 허용 | ✅ **자유 조절 가능. 프리셋 스냅 없음** | 수동 드래그 |
| 3 | 넷플릭스/티빙 분할 화면 허용 | ✅ 허용 | 수동 |
| 4 | 삼성 「앱별 화면 비율」로 대체 가능 | ❌ 불가 — 앱별 개별 설정 + 상시 고정 | 수동 |

---

## 미지수 #5·#6·#7 확정

| # | 항목 | 확정 | 근거 |
|---|---|---|---|
| 5 | 팝업 화면이 AOSP freeform 기반인가 | ✅ **freeform 지원** | 프로브 A: `FEATURE_FREEFORM_WINDOW_MANAGEMENT=true`, `force_resizable_activities=1`. Phase 4 P4-1(Shizuku 팝업) 경로 유지 |
| 6 | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 동작하는가 | ❌ **미지원 (FAILS)** | 3개 프로브 런 전부 `performGlobalAction` 반환값 **false** + Advisor adb 확인: 분할 진입 **전**과 분할 **중** 모두 false. One UI 8 미지원 판정. → Recents 폴백을 Phase 2 기본 전략으로 승격 |
| 7 | `TYPE_SPLIT_SCREEN_DIVIDER` 노출되는가 | ✅ **노출됨 (단, 분할 활성 중에만 / 핸들 영역만)** | `probe_report_split.md`(분할활성)에서만 노출: bounds `950,981,1018,1202`. 1·3차 런의 "미노출"은 **분할 비활성 상태 측정이라 무효**. 노출 bounds는 디바이더 전체가 아니라 **드래그 핸들 68×221px**. → DividerLocator는 이 핸들 중심을 드래그 기준점으로 사용 |

**분기 판정 요약**
- `dividerExposed = true` → `TYPE_SPLIT_SCREEN_DIVIDER`로 좌표 확보. 휴리스틱 폴백은 후순위. **단 분할 진입 이후에만 유효** (진입 전에는 창이 없음).
- `splitAction verdict = FAILS` → **Recents 폴백을 Phase 2(P2-3) 기본 전략으로 승격**.
- `hasFreeformFeature = true` → **Phase 4 P4-1(Shizuku 팝업) 유지**.

---

## WindowGeometry 실측값

전부 `probe_report_split.md`(세로 좌우 분할)와 Advisor의 `dumpsys window` 실측 기준. px 단위.

| 항목 | 세로(좌우 분할) | 가로(상하 분할) | 상태 |
|---|---|---|---|
| usable 크기 | 1968 × 2184 | 2184 × 1968 | [측정] |
| density | 2.25 (360 dpi) | 2.25 (360 dpi) | [측정] |
| **dividerThickness (시각 간격)** | **14 px** | 14 px (세로값 유용, 대칭 가정) | 세로 [측정] / 가로 [미검증] |
| 드래그 핸들 크기 | 68 × 221 px | 221 × 68 px (회전, 대칭 가정) | 세로 [측정] / 가로 [미검증] |
| SurfaceFlinger 디바이더 창 폭 | 154 px | 154 px (대칭 가정) | 세로 [측정] / 가로 [미검증] |
| 최소 페인 (가시) | 181 px | 미측정 | 세로 [측정] / 가로 [미검증] |

### 디바이더 기하 상세 (세로 좌우 분할, [측정])

같은 "디바이더"가 세 가지 다른 값으로 조회된다 — **혼동 주의**:

| 무엇 | 값 | 용도 |
|---|---|---|
| 접근성 `TYPE_SPLIT_SCREEN_DIVIDER` 창 bounds | `950,981,1018,1202` = **68×221px 핸들**, 중심 `(984, 1092)` | DividerLocator의 드래그 **기준점** (핸들 중심) |
| SurfaceFlinger `StageCoordinatorSplitDivider` frame | `Rect(907,0-1061,2184)` = **154px 폭 × 전체 높이**, 중심 X **984** | 물리 디바이더 서피스 (접근성엔 안 보임) |
| 두 앱 페인 사이 **시각 간격** | 좌 페인 우변 **977** ↔ 우 페인 좌변 **991** = **14px** | `SplitPlanner.dividerThickness` 에 쓸 값 |

- 좌 페인(ProbeActivity) frame `Rect(0,0-977,2184)`, 우 페인(YouTube) frame `Rect(991,0-1968,2184)`. [측정]
- 페인 사이 실제로 콘텐츠가 빠지는 폭은 14px 뿐이다.
- **교정:** `probe_report_split.md` 의 `dividerThickness=221` 은 핸들의 **세로 길이**를 두께로 오독한 것. 폐기하고 **14** 로 교체.

### 최소 페인 실측 (세로 좌우 분할, [측정])

`input swipe` 로 디바이더를 좌측 끝(목표 x=60)까지 드래그:
- 디바이더가 목표까지 가지 않고 **최소 스냅 위치에서 정지**. 이때 좌 페인 가시 폭 **181px**, 디바이더 창 frame `Rect(111,0-265,2184)`.
- **함정:** 이때 앱 창은 리사이즈되지 않고 **화면 밖으로 슬라이드**된다. ProbeActivity frame = `Rect(-1592,0-181,2184)` → 창 폭은 여전히 ≈1773px인데 가시 영역만 181px.
  - 폐루프 보정(ADR-5)에서 창 `getBoundsInScreen()` 을 그대로 종횡비 역산에 넣으면 음수 left에 속는다. **가시 교집합(화면 ∩ 창)** 으로 클램프해서 해석할 것.
- 가로(상하 분할) 방향 최소값은 **미측정**. 세로 181px 을 참고값으로만 기록.

---

## 분할 진입 전략 (#6 FAILS → 드래그 레시피 확정, 2026-07-25 실기기 E2E 검증)

`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 미지원 + **메뉴 레시피 반증** → **P2-3 기본 = Recents 카드 드래그 레시피**.

### [반증] 구 메뉴 레시피 ("분할 화면으로 열기")

- 메뉴 탭 경로는 **가로 화면에서 좌우 분할**을 생성함 (실측 스크린샷 확인). 레터박스 제거는 상하 분할이 필수이므로 **이 경로는 폐기**.
- "가로 = 상하 분할" 이라는 기존 가정은 이 진입 경로에 한해 **반증**됨.

### [측정] 드래그 레시피 (3단계 — E2E 4회 성공: 상단 배치 2회 · 하단 배치 1회 · BBB 재검증 1회)

| 단계 | 동작 | 근거 / 셀렉터 |
|---|---|---|
| 1 | Recents 열기 (`GLOBAL_ACTION_RECENTS`) | 성공 조건: 대상 카드 아이콘 노드 출현. `content-desc = "고급 옵션, <앱이름>, 버튼"` |
| 2 | 카드 아이콘을 **상단 가장자리로 홀드 드래그** (홀드 500ms + 이동 600ms, 드롭 y≈150) | `input draganddrop 592 322 1092 150 800` 상당. **상하 분할 선택 상태** 진입 (대상 상단 ~50%, 하단 "앱 선택" 피커). 성공 조건: 대상 창 가시 높이 15~75% |
| 3 | 피커에서 파트너("FW Panel") 노드 탭 | launcher `FromRecentActivity`. **LAUNCH_ADJACENT 는 분할 선택 상태를 파괴함(전체화면 강탈) — 최후 폴백 전용** |

### [측정] 디바이더 조작 (상하 분할, 가로)

- 초기 디바이더 중심 Y ≈ **984** (반반 분할). **평범한 단일 스와이프로 이동 가능** — `input swipe 1092 984 1092 1235 500` 으로 16:9 완전 배치 달성. `DividerDragger` 는 `SINGLE_STROKE` 가 기본.
- 핸들 탭 → 팝업 메뉴 노드 (content-desc, [측정]): **"App pair 추가 위치" / "창 전환" / "시계 방향으로 회전"**. 상하 전환은 "창 전환" 클릭 — `PaneSwapper` 전략 1로 실증 성공.
- 페인 창 핸들 desc: `"<앱이름> 창 핸들"`, 기타 `"최소화된 플레이어"`.

### [측정] 접근성 제스처 API 함정 (GestureDrags 구현 근거)

1. `willContinue=true` 스트로크의 `onCompleted` 는 **"주입 큐 수락" 의미로 ~4ms 만에 도착** — 완료 아님. 주입 자체는 duration 대로 재생됨.
2. **제로 길이 경로**(moveTo만 / 같은 점 lineTo)의 continueStroke 는 **+6ms 취소됨**. 홀드 스트로크는 1px 드리프트 경로 필수.
3. 원 스트로크와 continueStroke 를 **한 GestureDescription 에 넣으면 8ms 가짜 완료** — 반드시 두 번의 `dispatchGesture` 로 분리.
4. 롱프레스 인식엔 실제 홀드 시간 확보 필요 — 완료 콜백 직후 이동을 잇지 말고 holdMs 만큼 타이밍 가드.

### [측정] 개발 환경 함정

- **삼성 Freecess**: 포그라운드 서비스 없는 앱 프로세스를 백그라운드 동결 → 브로드캐스트 미달. 우회: `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow` + deviceidle whitelist. 근본 해결은 Phase 3 포그라운드 서비스.
- **암시적 브로드캐스트 (API 26+)**: `am broadcast -a <action>` 만으로는 매니페스트 리시버에 미배달 (`result=0` 인데 리시버 무실행). 반드시 `-n <pkg>/<receiver>` 명시.
- **파트너 액티비티 잔존 태스크**: `singleTask` 또는 백그라운드 잔존 태스크가 피커 탭 시 전체화면으로 재사용돼 분할 파괴. `PanelActivity` 는 launchMode 기본 + "멀티윈도우 아님 감지 시 `finishAndRemoveTask`" 자가 가드로 해결.

### [측정] 비리사이저블 앱 분기 (넷플릭스, 2026-07-25)

- 넷플릭스 = `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE` 선언 (기기 `force_resizable_activities=1` 로 분할 자체는 가능).
- **드래그 레시피는 팝업(프리폼)으로 라우팅됨** — One UI 가 선언 플래그를 보고 분할 존 대신 팝업으로 보냄. 상하 분할 선택 진입 불가.
- **우회 경로 실측 성공**: Recents 카드 메뉴 "분할 화면으로 열기" → **좌우** 분할 선택 → 피커에서 파트너 탭 (FW Panel 은 "많이 사용한 앱" 섹션에서 발견 가능 — 최근 앱 카드는 자가 소멸로 없음) → 디바이더 핸들 탭 → 팝업 **"시계 방향으로 회전"** 버튼 → **상하 분할로 전환됨**.
- 팝업 버튼 시각 배치 (좌→우): 회전 / 창 전환(⇆) / App pair(☆).
- **함정: 분할 상태에서 재생 시작 시 분할 이탈** — 상하 분할(넷플릭스 상단) 상태에서 영상 재생을 시작하자 재생 화면이 팝업/전체화면으로 이탈. Day 0 수동 검증에서는 분할 유지가 가능했음 — 재현 조건(재생을 먼저 시작한 뒤 분할 진입?) [미검증]. 다음 세션 탐구 대상.
- step2/step3 성공 조건 허점 실측: 팝업(프리폼) 창도 "가시 높이 15~75%" 를 통과 (오탐). 전폭(≥90%)·상단 도킹 조건 보강 필요.

> 셀렉터 문자열은 **한국어 로케일 실측값**. 다국어 [미검증].

---

## 검은 띠 실측 (프로브 E) — 실패 + 근본 원인

세 런 모두 검은 띠 **미검출** (스냅 안 됨). 근본 원인은 검출기 버그가 아니라 **입력 가정 위배**.

### 근본 원인: YouTube 앰비언트 모드 ([측정])

- 가로 전체화면 16:9 영상(Big Buck Bunny)에서 상하 띠가 **순흑이 아니라 영상 색이 번진 어두운 글로우**로 채워짐 (YouTube 기본 "앰비언트 모드").
- `screencap` 실측: 상단 띠 행들의 darkRatio(luma≤24) = **0.000**, 하단 최대 **0.66**.
- `LetterboxDetector` 의 순흑 임계 **0.97** 도달 불가 → 미검출.
- **검출기 코드 자체는 정상.** 입력 가정(띠=순흑)이 YouTube 기본 설정에서 깨진 것.
- 넷플릭스 등 대부분 플레이어는 순흑 띠를 유지하므로 **순흑 경로는 유지**한다.

### E 재검증 조건

다음 중 하나면 E 재실행:
1. **순흑 띠 플레이어**(넷플릭스/티빙 등) 또는 YouTube 앰비언트 모드 OFF 상태에서 재측정, 또는
2. **LetterboxDetector v2**(아래 Phase 1 권고) 구현 후 앰비언트 영상으로 재측정.

---

## 참고 계산표 (2184 × 1968 px 가로, 인셋/디바이더 0 가정 — 유지)

`SplitPlannerTest` 기대값과 일치해야 한다. **실배치 시에는 dividerThickness=14 + 시스템바 인셋을 usableHeight에서 차감**한 뒤 계산할 것.

| 영상 비율 | 영상 창 높이 | 전체 대비 | 반대편 잔여 |
|---|---:|---:|---:|
| 4:3 (1.333) | 1638 px | 83.2% | 330 px |
| 16:10 (1.600) | 1365 px | 69.4% | 603 px |
| 16:9 (1.778) | 1229 px | 62.4% | 739 px |
| 1.85:1 | 1181 px | 60.0% | 787 px |
| 2:1 | 1092 px | 55.5% | 876 px |
| 21:9 (2.370) | 921 px | 46.8% | 1047 px |
| 2.35:1 | 929 px | 47.2% | 1039 px |
| 2.39:1 | 914 px | 46.4% | 1054 px |

---

## 프로브 재실행 절차

- **트리거:** `ProbeTriggerReceiver` (액션 `dev.dj.foldwindow.probe.RUN_PROBE`, exported).
  ```bash
  adb shell am broadcast -a dev.dj.foldwindow.probe.RUN_PROBE
  adb pull /sdcard/Android/data/dev.dj.foldwindow/files/probe_report.md ./docs/
  ```
- **함정 #6:** 재설치(`installDebug`)하면 접근성 서비스가 꺼진다. 재실행 전 반드시 재활성화:
  ```bash
  adb shell settings put secure enabled_accessibility_services \
    dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService
  ```
- 분할/앰비언트 관련 측정은 **상태 의존**이다: #7은 분할 활성 중에, E는 순흑 띠 조건에서 찍어야 유효.

---

## 남은 미검증 항목 [미검증]

| 항목 | 현재 상태 | 확정 방법 |
|---|---|---|
| 가로(상하 분할) 최소 페인 높이 | 세로 좌우 181px만 측정 | 가로 분할에서 디바이더를 끝까지 드래그해 실측 |
| 가로(상하 분할) 디바이더 기하 | 세로값(14px/68×221) 대칭 가정 | 가로 분할 상태 dumpsys 실측 |
| One UI 정확 버전 | 설정값 비어 있음 | 다른 조회 경로 필요 |
| Recents 셀렉터 다국어 | 한국어만 | 영어 등 로케일에서 content-desc/text 확인 |
| wavve 등 국내 OTT 패키지명 | 미확인 | 대상 앱 실행 후 foreground 패키지 조회 |
| E 종횡비 역산 실측 | 미검출로 0건 | 순흑 플레이어 또는 Detector v2 로 재측정 |

---

## Phase 1·2 권고

### P1-1 — SplitPlanner 기본값 반영 (제안 diff) — ✅ 2026-07-25 반영 완료 (`foldSevenLandscape()`)

`app/src/main/java/dev/dj/foldwindow/domain/SplitPlanner.kt` 의 `foldSevenLandscapePlaceholder()`.
`SplitPlannerTest` 은 자체 `fold7()` 헬퍼(divider=0/minPane=0)를 쓰므로 이 팩토리 변경은 기존 테스트를 깨지 않는다. P1-1에서 대응 테스트를 추가하며 반영할 것.

```diff
-        fun foldSevenLandscapePlaceholder() = WindowGeometry(
+        // Galaxy Z Fold 7 내부 화면 가로(상하 분할). DEVICE_FACTS.md 2026-07-25 실측.
+        fun foldSevenLandscape() = WindowGeometry(
             usableLeft = 0,
-            usableTop = 0,
+            usableTop = 0,          // TODO: 상태바 인셋 실측 후 반영 (현재 미확정)
             usableWidth = 2184,
             usableHeight = 1968,
-            dividerThickness = 0,
-            minPaneHeight = 0,
+            dividerThickness = 14,  // [측정] 세로 좌우분할 시각 간격. 가로 대칭 가정
+            minPaneHeight = 181,    // [측정, 세로 좌우] 잠정. 가로 상하분할 [미검증]
         )
```

주의:
- `dividerThickness=14` 는 세로 실측값의 가로 전용. 가로 상하 분할 실측 전까지 [미검증] 주석 유지.
- `minPaneHeight=181`: 16:9 이상값(1229px)·파트너(739px) 모두 181 초과이므로 **정상 종횡비에서 클램프를 유발하지 않음** → `16:9 → 잔여 띠 0px` DoD 영향 없음. 초광각(>≈12:1)에서만 작동.
- `usableTop`(시스템바 인셋)은 아직 실측 없음. 프로브 D의 insets 값을 확인해 채울 것(현재 리포트에 없어 0 유지).

### P2-2 — DividerLocator

- `TYPE_SPLIT_SCREEN_DIVIDER` 조회를 1차 경로로. **단 분할 활성 이후에만** 창이 존재하므로 진입 완료를 상태 조건으로 폴링한 뒤 조회.
- 노출 bounds는 핸들(68×221)뿐이므로 **핸들 중심**(세로 예: `(984,1092)`)을 드래그 기준점으로 잡는다.
- 휴리스틱 폴백은 후순위(창 미조회 시): 화면 중앙 가로 밴드에서 두 APPLICATION 페인의 경계(시각 간격 14px)를 비율로 추정.

### P2-3 — SplitEntry (기본 = Recents 폴백)

- `performGlobalAction(TOGGLE_SPLIT_SCREEN)` 은 제거하거나 시도 후 즉시 실패 처리. **기본 경로는 Recents 레시피**(위 4단계).
- 상태 머신(ADR-2): 각 단계는 다음 노드 출현을 **조건 폴링**으로 확인하고 넘어간다. `postDelayed` 금지.
- 셀렉터는 한국어 문자열 + 패키지/역할 폴백 병행.

### P2-4 — DividerDragger

- `dispatchGesture` 로 핸들 중심 → `plan.dividerCenterY` 목표까지 이동.
- **함정:** 최소 스냅에 걸리면 창이 리사이즈가 아니라 오프스크린으로 슬라이드된다. 목표가 최소 페인 아래로 내려가지 않게 `minPaneHeight` 로 클램프(SplitPlanner가 이미 처리).
- 폐루프 재측정 시 창 bounds는 **화면과의 가시 교집합**으로 해석.

### LetterboxDetector v2 설계 분기 (E 재검증 근거)

순흑 단독 임계(0.97)는 YouTube 앰비언트 모드에서 실패. 대안 비교 권고:

| 방안 | 아이디어 | 장점 | 약점 |
|---|---|---|---|
| (a) 행 균일도/저디테일 | 띠 후보 행의 색 분산·엣지 밀도가 낮음을 검출 | 앰비언트 글로우도 잡음 | 저디테일 콘텐츠 오검출 위험 |
| (b) 모서리 색 적응 임계 | 네 모서리 색을 배경 기준으로 삼아 임계 동적화 | 비순흑 배경 대응 | 배경 그라디언트에 약함 |
| (c) 하이브리드 | 순흑(0.97) 우선 → 실패 시 (a)/(b) | 넷플릭스 등 안전 + 폴백 | 복잡도 |

- **권고:** (c) 하이브리드. 순흑 경로(넷플릭스/티빙 등 대다수)는 그대로 두고, 미검출 시에만 (a)+(b) 폴백. `domain/` 순수성 유지 위해 입력은 행별 통계(밝기/분산)로 추상화해 넘길 것.
