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
### [측정] MENU 레시피 자동화 실기기 검증 (2026-07-25 오후, E2E 6회)

- **MENU 5단계 자동화 성공** — 6회 실행 중 5회 전 단계 1차 시도 통과, 트리거→완료 ~4.7초. 실패 1회는 잔존 태스크 함정(아래) 이며 실패 노출도 정상 동작.
- **최종 육안 확인 (사용자):** 상단 페인 16:9 영상 재생 시 위아래 검은 띠 **0**, 하단 파트너 패널(시계/검정) 정상 — 넷플릭스에서 DoD ① 성립.
- **UNRESIZEABLE 감지 확정:** `privateFlags` **필드** 리플렉션 = hiddenapi `unsupported` → allowed. **상수** `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE` = `max-target-o` → **denied** (NoSuchFieldException). 폴백 비트 **1<<11 = 0x800** — 실측 `privateFlags=0x8c000910` 비트 분해(0x10=HAS_DOMAIN_URLS, 0x100=PARTIALLY_DIRECT_BOOT_AWARE, 0x800=UNRESIZEABLE)로 dumpsys 명칭과 교차 검증 완료. ⚠️ 최초 가정 1<<12 는 오답이었음 (VIA_SDK_VERSION).
- **회전 결과 페인 위치는 비결정** — 같은 절차로 넷플릭스가 상단 3회 / 하단 2회. 원인 미상(회전 전 좌우 배치 의존 추정).
- **"창 전환" ACTION_CLICK 무효 2회 실측** (회전 직후 컨텍스트, result=true 인데 3초 대기에도 실배치 불변) — 유튜브 세션에선 동일 셀렉터로 성공했던 것과 대조. 원인 미상. 대응: PaneSwapper 탭 재시도(3회) + **회전×2 폴백**(`DividerPopupRotator`) 구현 — 폴백 경로 자체는 이후 런에서 발동하지 않아 [미검증].
- **재생 중 메뉴 진입 → 재생 세션이 "최소화된 플레이어" 팝업으로 분리** (3회+ 재현). 반대로 **분할 페인 안에서 재생 시작 → 분할 유지** (실측). Day 0 "수동 분할 유지 가능" 관측과 정합 — **순서가 결정 변수**. v1 넷플릭스 사용법 = 원터치 배치 → 페인에서 재생.
- **잔존 패널 태스크 함정:** 프로세스 강제 종료(재설치 등)로 `finishAndRemoveTask` 가드가 못 돌면 피커 "최근 앱"에 죽은 FW Panel 카드가 남고, 셀렉터가 그걸 탭 → 자가 가드 즉시 종료 → 3회 소진 `ENTRY_STEP_FAILED` (실측). 해소: `purgeStalePanelTasks()` 세션 시작 시 자기 태스크 청소 — 적용 후 1개 제거·1차 성공 실측.
- **측정 오염 2종 실측:** ① pre-measure 전체 화면 스캔이 분할/홈 UI 를 띠로 오인 — aspect 1.14(conf 0.91)/2.95(conf 0.97) 고신뢰 오측 → 페인 크롭으로 수정 + 넷플릭스 프로파일 PROFILE 1.7778 고정. ② 드래그 직후 재측정이 플레이어 컨트롤 오버레이 오염 residual 122~224 → **PROFILE 소스는 ADR-5 보정 생략**(잔여값 보고만). `defaults.closedLoopCorrection` JSON 토글 배선 완료.
- step2/3 오탐 보강(`isSplitSelectTopPane` 전폭≥90%·상단 도킹≤40px)은 MENU 경로 E2E 로 간접 검증. DRAG 레시피(유튜브) 회귀는 아래 별도 절 — **1차 실패 후 수정, 2차 통과**.

### [측정] DRAG 레시피 유튜브 회귀 (2026-07-25 오후 2차 세션, E2E 2회)

- **1차 실행 = ENTRY_STEP_FAILED(step2) — 그러나 드래그는 물리적으로 성공해 있었다** (스크린샷 확인: 분할-선택 상태 도달). 판정/재시도 설계 버그 2종 실측:
  1. **유령 매치 즉시 실패**: `structural-clickable-label` 셀렉터가 bounds 조회 불가 노드를 매치 → 시도가 수 ms 만에 소진 (시도 1·3). 매치는 됐는데 `getBoundsInScreen` 이 빈 rect.
  2. **성공 미인지 재시도**: 시도 2 드래그 성공 후 폴링 잔여 예산 ~370ms(시도 예산 2.6s − 노드 탐색 1.1s − 드래그 1.1s) 안에 전환 애니메이션 미정착 → 실패 판정. 다음 시도는 이미 사라진 Recents 카드를 재탐색 → 영원히 실패.
- **수정** (`SplitEntry.kt`): ① step2 폴링 루프가 매 주기 "분할-선택 상태 이미 도달" 을 먼저 확인(이전 시도의 늦은 정착 흡수), ② bounds 빈 매치는 시도 종료가 아니라 재폴링, ③ 동일 패턴 선체크를 step3·menuStep3~5 에도 추가. 타임아웃/기하 상수는 무변경.
- **2차 실행 = 통과**: 트리거→Done 4.2초, `verified=true residual=0px`, 육안 검은띠 0. 시도 1이 정착 지연으로 실패했으나 시도 2가 선체크로 즉시 성공 — 수정 로직이 설계 그대로 발동.
- **분할-선택 상단 페인 ground truth** (dumpsys window, 정착 후): 대상(유튜브) frame `[0,0][2184,977]` = **전폭 100%·상단 도킹 0px**, 피커(FromRecentActivity) `[0,991][2184,1968]`, 간격 14px. → `isSplitSelectTopPane` 임계(전폭≥90%, `EDGE_DOCK_TOLERANCE_PX=40`) **[검증]** — 가로 상하 문맥에서 간격 14px 대칭 가정도 추가 근거 확보.
- **Detector v2 ADAPTIVE 경로 실기기 첫 실증**: 유튜브 앰비언트 글로우 띠(순흑 아님)에서 pre-measure 가 **1.7778 정확 측정** (conf 0.57~0.60, 2회 재현). E 재검증 조건 ② 충족 — v2 폴백이 실전에서 작동함을 확인.
- **MEASURED 소스 폐루프 최초 무오염 성공**: verify 단계 residual=0px — 넷플릭스 세션의 오염(residual 122~224)과 달리 유튜브 전체화면→분할 경로에서는 폐루프 보정이 정상 동작.
- **structural 셀렉터 함정 2종 추가 실측**: ① bounds 조회 불가 유령 노드 매치 (수 ms 시도 소진), ② **유효 bounds 의 대형 오매치** — Recents 카드 본체(중심 1092,833)를 아이콘으로 오인해 오드래그, 세션 파괴. 대응 = 빈 bounds 재폴링 + **크기 가드** (bounds ≤ 화면폭/10 ≈218px. 실측 아이콘 ~90px, 카드 본체 수백 px).

### [측정] 버블 오버레이 × 분할 피커 상호작용 (2026-07-25, Phase 3 P3-1)

- **오버레이 창(TYPE_APPLICATION_OVERLAY, 버블)이 떠 있는 동안 분할 피커에서 파트너를 탭하면 PanelActivity 가 분할 페인이 아니라 전체화면으로 launch 된다.** A/B 실측: 버블 ON = 실패 2회 (자가 가드 "fullscreen 감지 종료" 발화, 쌍 미수렴 ×3) / 버블 OFF 동일 빌드·경로 = 즉시 성공(160ms 수렴). 메커니즘 불명 (One UI WM 라우팅 추정) — 경험 법칙으로 대응.
- 참고: 버블 창은 접근성 창 목록에 **TYPE_SYSTEM**(wm type 2038)으로 보고됨 — `TYPE_APPLICATION` 필터 기반 기하 판정은 오염하지 않는다.
- **대응 (실기기 검증 완료)**: 액추에이터 세션 시작(beginSession) 시 `FloatingLauncherService.setBubbleHiddenForArrange(true)` 로 버블 창 자체를 removeView, 세션 종료(cleanupSession — Done/Failed/Cancel 수렴점)에서 복원 + 버블 쪽 30s 안전 타이머. 적용 후 버블 탭 E2E 통과 (4.1초, verified=true).
- **파생 함정**: 피커 셀렉터 후보에 앱 서랍 노출 라벨("FoldWindow" = OnboardingActivity)이 있으면 재시도가 온보딩을 오클릭해 분할-선택을 파괴 (실측 1회). `PANEL_LABEL_CANDIDATES` 는 "FW Panel" 단독으로 축소.
- 유튜브 영상 시작 직후 버블 탭 시 pre-measure 오측 실측 1건: 추천 화면/인트로 프레임 오염으로 aspect 1.6 (conf 0.60) — divider 1372 로 과소 배치, verify 는 어두운 장면이라 residual=0 오판. 열린 질문 #12(신뢰도 필터) 근거 보강.

> 셀렉터 문자열은 **한국어 로케일 실측값**. 다국어 [미검증].

### [측정] P3-2 확장 메뉴·분할 해제 실기기 검증 (2026-07-25 오후 4차)

1. **분할 해제는 디바이더 드래그로 불가** — `dispatchGesture` SINGLE_STROKE 로 디바이더를 화면끝−40px 까지 드래그하면 `onCompleted` 콜백은 정상 수신되지만 디바이더가 원위치로 스냅백하고 분할이 유지된다 (가로 1928@622ms · 세로 2144@652ms, 2/2 재현). **완전히 동일한 기하·시간의 `adb input swipe` 는 3/3 해제 성공** (가로 1960@800ms·1928@610ms, 세로 2144@650ms). 판정: One UI 가 dismiss 깊이의 디바이더 드래그에서만 접근성 주입 제스처를 거부한다 (원인 불명, 경험 법칙). dismiss 깊이 미만의 배치 드래그는 dispatchGesture 로 계속 정상 동작.
2. **패널 finish → 분할 해소**: 분할 활성 중 PanelActivity 를 finish 하면 (BACK 키 실측) 분할이 즉시 해소되고 상대 앱이 전체화면으로 자동 복귀한다. dismissSplit v2 = `PanelActivity.instance.finishAndRemoveTask()` + `isSplitActive` 폴링으로 재구현, E2E 성공 ("dismissSplit: 성공", divider 창 0, 유튜브 전체화면 복귀). 인텐트 폴백 경로(instance null 시 EXTRA_FINISH_PANEL)는 [미검증].
3. **ACTION_OUTSIDE 디스패치 순서**: 메뉴 창(FLAG_WATCH_OUTSIDE_TOUCH)의 ACTION_OUTSIDE 가 버블 창의 ACTION_DOWN 보다 **먼저** 디스패치된다 (실측: 재탭 시 DOWN 스냅샷 방어 무력 → 닫기 대신 startArrange 오발화 / 재롱프레스 → 닫힘+재열림 2회). 대응: 메뉴를 풀스크린 투명 스크림 창으로 재구성해 경합 클래스 자체를 제거 — 재탭 = 닫기만 E2E 확인.
4. **풀스크린 터치 가능 오버레이 = a11y 창 목록 가림-제외**: 스크림이 떠 있는 동안 하위 창들이 서비스 `getWindows()` 에서 제외되고, removeView 직후에도 스냅샷이 잠시 유지된다. **재구축은 비원자적** — TYPE_APPLICATION 이 먼저 돌아오고 TYPE_SPLIT_SCREEN_DIVIDER 는 나중 (실측: "APPLICATION ≥1" 게이트 통과 직후에도 `isSplitActive` false-negative 2/2, 같은 순간 dumpsys accessibility 는 정상). 대응: dismiss 진입 체크를 `isSplitActive` 자체의 2s 조건 폴링으로 교체 후 E2E 통과. 소형(WRAP_CONTENT) 창이던 구 메뉴에선 미발생 — 가림 면적이 원인.
5. **step3 피커 탭 변동성 (#20 확장)**: 메뉴發 배치 4회 중 2회가 step3 3연속 실패 → ENTRY_STEP_FAILED (직후 재시도는 성공). events 버퍼 실측: 실패 시 클릭이 무효(액티비티 생성 이벤트 자체 없음, 2회)이거나 `startActivityFromRecents` 로 오라우팅(전체화면 낙착, 1회), 성공 시엔 `startActivityAsUser:com.sec.android.app.launcher` (정상 파트너 배치). "창 전환" ACTION_CLICK 무효(#20)와 동일 계열 — 좌표 탭 제스처 대체 검토 근거 보강.
6. 개발 편의: `adb input swipe x y x y 700` 롱프레스 시뮬레이션은 발화 경계에 걸림 (1/3 발화) — **1200ms 권장**. 실손가락 홀드는 무관. 세로(포트레이트) 방향에서도 배치 파이프라인 정상 동작 실측 (verified=true, residual=0).
7. **실부팅 복귀 (P3-1 #23)**: `adb reboot` 실측 — BOOT_COMPLETED 수신("boot: 버블 자동 복귀 시작"), specialUse FGS 자동 기동 허용, 접근성 서비스 유지, 홈 화면 버블 가시 전부 확인. 분할 없는 상태의 "분할 해제" 는 2.0s 폴링 후 "분할 화면이 아닙니다" 토스트 (설계값 그대로).

### [측정] P3-3 DataStore 이관·placement 복원 실기기 검증 (2026-07-25 오후 7차, PROGRESS #26 해소)

1. **① SharedPreferencesMigration 실이관 [검증]**: 구버전(P3-2 빌드) 설치 상태에서 `bubble_prefs.xml` 에 enabled=true/x=1500/y=300 주입 → `installDebug` 업데이트 설치 → 첫 store 접근 시 3키 **무손실 이관** (pb 디코딩: x=1500(varint DC 0B), y=300(AC 02), enabled=true) + **원본 XML 삭제** 확인. 버블도 주입 x 좌표 그대로 복원 (화면상 y 는 저장값 +~100px — WindowManager 좌표계의 상태바 오프셋, 저장값 자체는 무손실).
2. **② goAsync 부팅 복귀 [검증, 회귀 해소]**: P3-3 재작성 코드로 `adb reboot` 실측 — "boot: 버블 자동 복귀 시작" 로그, FGS 자동 기동(부팅 후 수 초 내 createTime), 접근성 유지, 홈 버블 가시. 5차(구 동기 코드)와 동일 결과 = goAsync+IO 코루틴 재작성 무회귀.
3. **③ 마지막 성공 placement 저장→복원 E2E [검증]**: 유튜브 가로 전체화면에서 OVERRIDE bottom 배치 성공(5.1s, residual=0, effective==desired) → pb 에 `last_placement.com.google.android.youtube=BOTTOM` 기록 확인 → **무override 트리거 3회 전부 `placementSource=LAST_SUCCESS placement=BOTTOM` 결정**. 3회차 `done verified=true residual=0 effective=BOTTOM` 로 완결. (1·2회차는 ENTRY_STEP_FAILED — 기존 #20/#25 step3 피커 변동성 그대로 재현: "클릭 후 분할 쌍 미수렴" ×3 → 전략2 폴백도 실패, P3-3 로직과 무관. 이번 세션 누적 무override 3회 중 2회 실패로 변동성 표본 보강)
4. **④ corruptionHandler [검증]**: 프로세스 킬 → `fwa_store.preferences_pb` 에 가비지 텍스트 주입 → 버블 시작 탭 → `FloatingLauncherService.onCreate` 의 runBlocking 읽기에서 CorruptionException 감지 ("fwaDataStore 손상 감지" Log.e + 스택) → emptyPreferences 재시작 → **FGS 정상 기동 (크래시/크래시 루프 없음)**, onStartCommand 가 enabled=true 재기록. 손상 데이터는 리셋(x/y 유실) = 레거시 SharedPreferences 손상 의미론과 동등. 부차 관찰: 별도 1회차 손상 감지가 서비스 기동 전 프로세스 스타트 +166ms 의 **주체 미상 백그라운드 store 접근**에서 발생 — 결과는 동일(무크래시 복구)하나 접근 주체 특정은 미해결.
5. **⑤ 온보딩 중지 취소 레이스 [근사 검증]**: `input tap(중지); input keyevent BACK` 연속 실행 — 액티비티 finish→lifecycleScope 취소, 폴드 접기와 동일 메커니즘의 근사. 결과: enabled=false 쓰기 완료 + stopService 완주(FGS 소멸) + 무크래시 + 여타 키(x/y·placement) 보존. NonCancellable 시퀀스 보장 동작. **물리 폴드 접기 중 탭 자체는 [미검증]** (구성상 파괴가 아니라 pause 일 가능성도 있어 실익 낮음).
6. **운영 함정 실측 3건**: ① adb 배치 트리거는 **`-n dev.dj.foldwindow/.service.ArrangeTriggerReceiver` 컴포넌트 지정 필수** — 액션만으로는 implicit broadcast 제한으로 수신 0건 (PROGRESS 의 구 명령이 이 형태였음, 수정). ② `am force-stop` 후 접근성 서비스가 재바인드되지 않음 (설정값은 유지되나 연결 끊김) — settings put 재설정으로 재바인드 (함정 #6 계열). ③ 온보딩의 `accessibilityGranted = instance != null` 은 onResume 스냅샷이라 백그라운드 재바인드가 즉시 반영 안 됨 — 홈→재진입 필요 (개발 중 혼동 포인트, 실사용 무해).

### [측정] #20 클릭-사이클 에스컬레이션 실기기 검증 (2026-07-25 저녁 10차 — Gate 1~3 통과)

빌드 = 커밋 9985b99 (9차 구현). 총 **15 arrange 세션, 15/15 done, ENTRY_STEP_FAILED 0건**. LAUNCH_ADJACENT 삭제 후 회귀 없음.

| Gate | 구성 | 결과 |
|---|---|---|
| 1 회귀 | 유튜브 DRAG(broadcast, OVERRIDE top) ×3 | 3/3 converged, **residual=0**, 피커 cycle-0 gesture 327~362ms |
| 1 회귀 | 넷플릭스 MENU(top) ×3 | 3/3 step2~5 전 단계 1차 통과, rotateOnce 1회로 TOP 착지, residual=122(보고 전용) |
| 2① 독 컨텍스트 | 유튜브 무override 연속 ×5 | **실패 0** (과거 실패율 ~50% → 우연 확률 ~3%). 4회 cycle-0(176~333ms), **1회 cycle-1 회복**(1157ms) |
| 2② 회전 여파 스왑 | 넷플릭스 bottom ×4 (회전 TOP 착지 → 스왑 강제) | **스왑 4/4 수렴**: settleGate ok 153~154ms, switch-click cycle=0 mech=a11y → 800ms 검증 슬라이스 내 수렴 |

**핵심 실증 2건**:
1. **사이클 회복 실작동** (Gate2① run4): cycle-0 제스처 탭이 오착지 → FORENSIC `TYPE_VIEW_CLICKED` 가 착지점을 `launcher:id/icon_container` 로 특정 → 검증 슬라이스 미수렴 → cycle-1 re-find·재탭 → 수렴. **무효 클릭 = "실행 자체 없음" 이 아니라 "다른 뷰에 착지" 클래스**임을 최초 물증화 (성공 클릭 착지점은 일관되게 `FrameLayout viewId=null`, menuStep2 카드 클릭은 `task_icon`).
2. **회전 여파 독 컨텍스트 전승**: 과거 "창 전환" ACTION_CLICK 무효 2회와 동형 컨텍스트(MENU 회전 직후 스왑)에서 정착 게이트+검증 슬라이스 조합으로 4/4 수렴. 정착 게이트는 매회 ~154ms 로 조기 통과 (timeout-속행 경로 미발동).

부수 관측:
- 회전 착지 이 세션 **TOP 7/7** (#19 비결정 표본 보강 — 누적 TOP 10, BOTTOM 2).
- P3-3 placement 체인 회귀 겸증: 무override 5회 전부 `placementSource=LAST_SUCCESS` 결정 정상.
- 분할 해제 리셋 = 패널 페인 탭+BACK (PanelActivity finish) 15회 전부 정상 동작.
- 유튜브 MEASURED 경로 conf 0.53~0.59 로 1.7778 정확 측정 8/8 (BBB 앰비언트).

**미발동 경로 [미검증]** (설계 §5 예상과 일치): 피커 cycle-2 a11y 폴백 · 스왑 cycle-1/2 제스처 · 팝업 소멸→재탭 분기 · involution 가드 실개입 · budget-exhausted tail · 오버레이 가드 발동(세션 중 버블 자동 숨김이라 정상적으로 미발동) · 회전×2 폴백(스왑 전승이라 미발동).

### [측정] #12 측정 합치 게이트 실기기 검증 (2026-07-25 밤 11차 — G1~G5 통과)

빌드 = 커밋 2474ff3 + 현장 수정 2건(아래). 총 9 arrange 세션 9/9 done. 트리거 = broadcast(OVERRIDE top), 유튜브 BBB 앰비언트.

| Gate | 시나리오 | 결과 |
|---|---|---|
| G1 사고 재현 | 추천 엔드스크린에서 트리거 | pre = **conf 0.70 쓰레기**(PURE_BLACK 1.197, band 118/26 — 구 시스템이면 divider 1780 채택) → confirm 양축 띠(2.55 conf 1.0 / 2.16 conf 0.999) → **BOTH_AXES_BARS** → PRESET 1236. "aspect-fit 영상 불가능" 규칙이 실제 추천 화면에서 발동 |
| G2 컨트롤 오염 | 플레이어 탭 직후 트리거 | pre = band **370/160 비대칭**(스크럽 바 하단 잠식) → raw 1.519 → **snap 1.5** conf 0.566 (사고 클래스 1.333/1.12 동형) → confirm NoBars → 페인 AR 2.23 vs 1.5 = 48% 괴리 → **NO_BARS_INCONSISTENT** → PRESET → residual=0 |
| G3 클린 회귀 ×3 | 전체화면 재생 중 트리거 | **3/3 SNAP_AGREE → MEASURED 채택**, residual=0, 총 소요 4.2~4.3s (기준선 4.1~4.7 내 — confirm +~0.3s 흡수) |
| G4 앰비언트 | G1~G3 전체 | ADAPTIVE pre conf 0.51~0.59 ×5 전부 후보 게이트 통과, 클린 3회는 합치 채택 — 게이트가 정상 ADAPTIVE 를 죽이지 않음 |
| G5 오버라이드 | `--ef aspect 2.3704` | `preMeasure=none`(pre 생략)·confirm/consensus 로그 0건(생략)·`aspectSource=PRESET aspectOverride=2.3704`·divider **928**(21:9 정확) — tier 0 완전 작동 |

**confirm 크롭의 실측 오염원 2종 → 현장 수정 2건 (전부 픽셀 물증 후 수정)**:
1. **최외곽 열 오염**: 페인 크롭 열축 스캔에서 entries [0..3]·[1089..1091] 분산 404~501 > ADAPTIVE_MAX_VARIANCE(400) — 라운드 코너 배경 누출(세로 마진 5%=49px < 코너 반경 ~80px) + 최외곽 열 엣지 렌더링(x=0 열 코너 밖에서도 luma 76 vs 글로우 36) + 우측 엣지 스트립(x≥2176 전 높이 luma 77~80). 첫/끝 entry 불통과 → adaptive "한쪽 스트립 0 → null" → 실재 글로우 밴드(각 ~112 entries, var≈8) 전체 소실 → NoBars 오판. **수정 = `toPillarboxScan` sideMarginPct 0.005 (entries 축 좌우 ~11px 제외)**
2. **플레이어 크롬 오염**: 분할 진입 리사이즈 직후 유튜브가 페인에 크롬 상시 표시 — 축소 아이콘(x≈2126~2155, y≈890~945/977, luma 176~188)이 우측 스트립을 9 entries 에서 결정론 차단(두 세션 raw 소수 7자리 동일 = 정적 물증), 상단 타이틀 그라디언트가 행축 유사 밴드 conf 0.08 생성 → BOTH_AXES_BARS 오판정. **수정 = edgeMarginPct 0.05→0.12 (크롬 y-대역 제외, 977px 기준 상하 117px) + `classifyAxis` minConfidence(0.25) — 저신뢰 밴드는 BARS_MEASURED 불승격**. 수정 후 cols band **214/214 완벽 대칭** 3/3 재현

부수 관측:
- ADAPTIVE_MAX_VARIANCE(400)·MAX_BAR_LUMA(90) 등 도메인 상수는 **무변경** — 오염은 상수가 아니라 입력(스캔 범위) 문제였음 (함정 #7 준수)
- confirm 축 판독은 페인 오버레이 상태에 따라 BARS_MEASURED/NoBars/BothAxes 로 변동하나 세 변형 전부 안전 처리 실증 (합치·불합치 판정 각 3경로 발동)
- **글로우 필러박스에서 residualCols 순흑 블라인드**: G5 의 16:9-in-21:9 페인(실측 필러박스 존재)에서 verify residualCols=0 — 열린 질문 #13 v1.5(적응형 residual) 근거 보강
- **BOTH_AXES_BARS 세션의 ADR-5 보정이 비영상 콘텐츠를 쫓음** (G1: 엔드스크린 verify residual 118 → 1236→1099 드리프트 1회, 정직 보고 종료) — v1.5 후보: BOTH_AXES_BARS 판정 시 보정도 생략 (콘텐츠 비영상 물증이므로)
- 진입 직후 페인 컨트롤 소환은 **상시** (DESIGN_12 §8-4 미지 해소) — 12% 마진이 구조적으로 흡수

**[미검증] 잔여**: 비-16:9 콘텐츠(영화 2.35 등)의 합치 채택 실측 0건 (16:9 만 검증) · `requireMeasurementAgreement=false` 롤백 레버 미실사용 · confirm 레이트리밋 백오프 대기 분기(진입 2~4s 라 자연 미발동) · 메뉴 프리셋 UI發 tier 0 (adb 경로만 검증, 배선은 코드 확인 완료)

### [측정] #12 §6 측정 캐싱 v1 실기기 검증 (2026-07-26 오전 13차 — 4항목 전부 통과)

빌드 = 커밋 2e5028b (레버 세션만 시드 JSON `cacheMeasuredAspect:false` 임시 수정 빌드, 종료 후 원복·재설치). 총 10 arrange 세션 10/10 done. 유튜브만 사용 (BBB 앰비언트 + MPD직캠 세로 영상).

| # | 항목 | 시나리오 | 물증 |
|---|---|---|---|
| ① | 합치 세션 → 저장 | 클린 BBB 전체화면 트리거 | SNAP_AGREE→MEASURED, done verified=true residual=0 직후 `aspect cache save: pkg=com.google.android.youtube aspect=1.7777778 (합치∧verified)` + pb 실물: 키 `measured_aspect.com.google.android.youtube`, float LE `39 8e e3 3f` = 1.7777778 (run-as od 판독) |
| ② | 불합치 폴백 CACHED 낙착 | 일시정지+컨트롤/홈 피드/Shorts 3연 (pre 쓰레기 PURE_BLACK 1.126~1.139 conf 0.997~1.0, 구 시스템이면 divider 1780) | 3세션 전부 decision `cachedAspect=1.7777778` 채움 → confirm 불합치 (RAW_DISAGREE ×1, BOTH_AXES_BARS ×2) → `→ aspect=1.7777778 source=CACHED`. residual 0/46/38 (46·38 은 비영상 콘텐츠 위 보정 1회 정직 보고 — CACHED 도 closedLoopCorrection ON 정상) |
| ③ | CACHED 세션 confirm 미실행 | **세로 직캠 immersive 전체화면** (MPD직캠, 필러박스 전용) 트리거 | `measure[pre/rows]: 밴드 없음` = pre **null** → decision `aspectSource=CACHED preMeasure=none` (divider 1236 즉시 계획) → 세션 전체에 confirm/consensus/cache save 로그 **0건** (자기 갱신 구조 차단 실증). done verified=true residual=0 |
| ④ | 레버 false 회귀 | 시드 `cacheMeasuredAspect:false` 빌드로 ③·② 동형 3세션 | read측: pre-null 동형에서 `cachedAspect=none`·`aspectSource=PRESET`(1.7778, divider 1235) / 불합치 폴백도 `source=PRESET` (cached 티어 건너뜀 — 세션 ② 와 동일 RAW_DISAGREE 에서 A/B 대조). write측: SNAP_AGREE+verified=true residual=0 (① 동형) 인데 save 로그 **0건** + pb 불변. 기존 캐시 값은 삭제되지 않고 보존 (레버 = read/write 차단만) |

**원복 확인 세션** (레버 원복 재설치 후): decision `cachedAspect=1.7777778` 재등장 + 보너스 실증 — confirm 이 구름 장면을 4:3 으로 오측(ADAPTIVE 1.333, band 247/185 — **사고 클래스 1.333 재현**) → RAW_DISAGREE → CACHED 폴백 → residual=0. 과거 오염 고착 사고가 §6 에서 캐시로 무해화되는 실전 시나리오.

부수 관측·운영 함정:
- **pre-null 유도 조건 = "필러박스 전용 콘텐츠의 immersive 전체화면"** (세로 영상 fullscreen). 비-immersive 다크 UI 에선 상태바/하단 엣지의 순흑 행이 항상 PURE_BLACK 후보를 만들어 pre 가 절대 null 이 안 됨 — **한쪽 밴드 0 도 허용**되는 순흑 경로 실측 (band 28/0, 24/12, 6/44). 홈 피드·Shorts(랜드)·일시정지 전부 conf 0.997~1.0 쓰레기 후보 생성
- **Shorts 진입은 포트레이트 강제 + user_rotation 잠금 무시**. 복귀 후 `settings put system user_rotation` 만으론 WM 이 재평가하지 않음 — **`adb shell cmd window user-rotation lock 1` 이 즉시 적용** (free 로 해제). 캠페인 중 회전 상태 재확인 필수 (기존 노트 강화)
- 세로 영상 전체화면(포트레이트 immersive) 상태에서 트리거해도 파이프라인 정상 — decision 시점 geometry 는 가로(2184×1968) 로 잡혀 divider 1236 계획, 배치도 가로 분할로 완료 (세로 영상은 페인 안에서 좌우 필러박스, rows residual=0 → verified — 순흑 블라인드 #13 의미론 그대로)
- 유튜브 재설치 후에도 전체화면 재생 유지 (분할만 해소) — 레버 A/B 빌드 교체 중 상태 재셋업 불필요했음

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
| #20 잔여 미발동 경로 (10차 Gate 통과 후) | 주 경로 전부 [측정] 해소 (위 10차 절). 잔여: 피커 cycle-2 a11y·스왑 cycle-1/2 제스처·팝업 재오픈 분기·involution 가드·budget-exhausted·오버레이 가드 발동·회전×2 폴백 | 자연 발생 대기 (mech 로그가 상시 계측) — 3시도 전멸 재발 시 FORENSIC viewId 로 원인 특정 후 스텝 되감기 재검토 |
| 가로(상하 분할) 디바이더 기하 | 세로값(14px/68×221) 대칭 가정 | 가로 분할 상태 dumpsys 실측 |
| One UI 정확 버전 | 설정값 비어 있음 | 다른 조회 경로 필요 |
| Recents 셀렉터 다국어 | 한국어만 | 영어 등 로케일에서 content-desc/text 확인 |
| wavve 등 국내 OTT 패키지명 | 미확인 | 대상 앱 실행 후 foreground 패키지 조회 |
| E 종횡비 역산 실측 | 미검출로 0건 | 순흑 플레이어 또는 Detector v2 로 재측정 |
| ~~#12 §6 측정 캐싱 v1 (12차 구현)~~ | **13차 실기기 4항목 전부 [측정] 해소** (위 13차 절) — 저장·CACHED 낙착(폴백/decision 양 지점)·confirm 미실행·레버 회귀 전부 물증 확보 | 잔여 [미검증]: 캐시 값이 1.7778 이외인 앱(비-16:9 콘텐츠 캐시) 실측 — 11차 잔여와 동일 갭 |

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

---

## P3-5 FoldingFeature 실기기 검증 (2026-07-27~28 15차 — 5항목 전부 통과)

검증 기기 = SM-F966N / One UI 8 / API 36. 5항목 **전부 최종 빌드 기준 통과**.
단 항목 1·3 은 **수정 전 빌드에서 실패가 실측**됐고 그 물증이 설계를 바꿨으므로 항목별로 이력을 남긴다
(항목 3 은 "완전 닫기는 800ms 미만으로 지나간다"는 **설계 가정 자체가 반증**된 경우다).

### 컴파일타임에 이미 해소된 사항 (참고 — 아래 실기기 항목과 구분할 것)

- androidx.window 1.3.0 sources jar 직접 확인 결과: `WindowInfoTracker.windowLayoutInfo(Context)` 의
  `@UiContext` 파라미터 애너테이션은 `@Retention(SOURCE)` 수준의 문서화/린트 마커이며 Kotlin
  `@RequiresOptIn` 마커가 아니다. 따라서 `@OptIn` 없이 정상 컴파일된다 — 브리프가 조건부로 언급한
  "experimental 이면 opt-in" 분기는 발동하지 않았다(코드에 opt-in 없음, 의도적).
- `FoldingFeature.bounds`(→ `DisplayFeature.bounds`)는 `android.graphics.Rect` — `platform/FoldStateMonitor`
  의 힌지 좌표 로그(`hingeBounds=...`)가 그대로 실좌표로 쓸 수 있다.

### [측정] 항목 1 — `@UiContext` 수용 컨텍스트 (수정 필요했음 → 3-인자 WindowContext 채택)

| 후보 | 결과 | 물증 |
|---|---|---|
| ① 서비스 자신(`AccessibilityService`) | ❌ **구독 거부** | androidx.window 1.3.0 `WindowLayoutComponentImpl.assertUiContext` → `IllegalArgumentException: Context must be a UI Context with display association, which should be an Activity, WindowContext or InputMethodService` |
| (구) 2-인자 `createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)` | ❌ **생성 자체 불가** | `UnsupportedOperationException: Tried to obtain display from a Context not associated with one` — 서비스 컨텍스트에 display 연결이 없다. 구독까지 가지도 못함 → **죽은 코드로 삭제** |
| ② **3-인자 `createWindowContext(display, TYPE_ACCESSIBILITY_OVERLAY, null)`** | ✅ **채택 (방출 수신 확인)** | display = `DisplayManager.getDisplay(DEFAULT_DISPLAY)`, API 31+ 가드 |

- 두 실패 에러가 가리키는 원인은 동일하다 — **"display 를 명시적으로 연결하라"**. 3-인자 경로가 그 요구를 정확히 충족한다.
- **전멸 시 조용한 격하 실증** (수정 전 빌드에서 관측): 크래시 없음 · `Log.w` 만 남기고 폴드 감지 기능만 꺼짐 · `arranger service connected` 정상. 설계 의도(기능 격하 ≠ 서비스 사망) 그대로 동작.

### [측정] 항목 2 — 노트북 자세 방출 + `orientation` 의미론 실측

| 상태 | posture | hingeBounds |
|---|---|---|
| 노트북 자세 + **가로 창** | `HALF_OPENED_HORIZONTAL` | `Rect(0, 984 - 2184, 984)` — 내부 화면 가로 좌표, 힌지 수평선 **y=984** |
| 노트북 자세 + **포트레이트 고정(회전 잠금)** | `HALF_OPENED_VERTICAL` | `Rect(984, 0 - 984, 2184)` |
| 닫힘/전환 중 (방출 지속, **커버 디스플레이 기하 1080×2520**) | HALF_OPENED_* | `Rect(540, 0 - 540, 2520)` / `Rect(0, 540 - 2520, 540)` |
| 완전 닫힘 | `UNKNOWN` | null |

- **`orientation` 은 물리 힌지 방향이 아니라 창 상대 좌표 의미론이다.** 회전 잠금으로 포트레이트가 고정돼 있으면 물리적으로 노트북 자세여도 `HALF_OPENED_VERTICAL` 이 나온다. 13차 캠페인의 `cmd window user-rotation lock 1` 잔재 때문에 실측으로 발견됨.
  - **⚠ 함정: 검증/사용 전 `adb shell cmd window user-rotation free` 확인 필수.**
  - 함의 [추정]: FLEX 티어 조건이 `HALF_OPENED_HORIZONTAL` 이므로(항목 5) 회전 잠금 상태에서는 자동 배치가 통째로 발화하지 않는다. 실사용 영향은 별도 측정 필요 [미검증].
- 닫는 도중에도 방출이 끊기지 않고 **좌표계가 내부 화면 → 커버 디스플레이로 갈아탄다** — 힌지 좌표를 소비하는 쪽은 디스플레이 기하를 함께 봐야 한다.

### [측정] 항목 3 — 완전 닫기 오발화: 설계 가정 반증 → 2층 방어 구현·검증

**설계 가정 반증**: 닫기 동작의 HALF_OPENED 대역 체류 시간 3표본 = **2.1s / 1.95s / 1.2s**.
전부 `FlexModePolicy.DEFAULT_STABILITY_MS`(800ms) 초과 — "완전 닫기는 800ms 미만으로 통과한다"는 가정은 **반증**됐다.

**오발화 물증** (수정 전 빌드, 07-27 23:31): 대역 진입 **+803ms** 에 트리거 → 1.15s 뒤 완전 닫힘 → 닫힌 기기에서 Recents 진입 시도 3회 → `ENTRY_STEP_FAILED`.
`display-off` 게이트는 +800ms 시점에 **화면이 아직 켜져 있어 무력**했다.
→ 디바운스 상수 증액은 "느린 닫기 꼬리"에 다시 뚫리는 타이밍 도박(ADR-2 위반)이라 기각하고, 조건 신호 2층으로 대응.

#### 방어 1층 — 힌지 각도 안정성 게이트 [측정]

신규 `platform/HingeAngleMonitor` + `domain/FlexModePolicy` 확장.

- **`Sensor.TYPE_HINGE_ANGLE` Fold 7 실노출 확인**: 도(deg) 단위, 노트북 자세 **≈90.0**, 완전 닫힘 **0.0**, **on-change 방출**(정지 시 침묵 = 샘플 부재 자체가 정지의 증거).
- 게이트 = 대역 **[45°, 135°]** ∧ (**침묵 ≥600ms** ∨ **600ms 윈도 스프레드 ≤8°**).
- **정상 속도 닫기 2/2 차단** (스윕 90→0 로그로 가시).
- 센서 무가용 시 **통과로 격하** (기능 보존 — 종전 디바운스 단독 의미론).

#### 방어 2층 — FLEX 세션 자세-이탈 취소 [측정]

`service/ArrangerAccessibilityService`. 1층을 통과하는 잔여 클래스를 세션 중에 되돌린다.

- **멈칫 동반 느린 닫기**(대역 내 정지 ≥850ms)는 1층을 통과해 발화한다 → 이후 자세가 `HALF_OPENED_HORIZONTAL` 을 이탈하면 진행 중 FLEX 세션을 **기존 cancel 경로로 취소**.
- 실증: `flex session cancelled: posture-exit` → `Failed(reason=CANCELLED)` (07-28 00:04:42).
- **수동 세션(OVERRIDE 등)은 비대상** — 사용자가 명시적으로 건 배치는 자세로 취소하지 않는다.
- **Done 이후엔 취소하지 않는다** — 완료 배치 유지 실증 (Done 3.2s 후 펴기, 분할 유지, 07-28 00:05).

### [측정] 항목 4 — E2E 자동 상단 배치 (2회 재현)

유튜브 가로 전체화면 → 노트북 자세 → **접기에서 완료까지 7.1s** (트리거 지연 ≈1.05s = 디바운스 800ms + 각도 침묵 600ms **중첩** + 조건 폴링 250ms).

로그 체인:

```
fold posture changed: FLAT -> HALF_OPENED_HORIZONTAL
hinge angle=90.0                      (1샘플 후 침묵)
flex auto-arrange trigger: target=com.google.android.youtube (source=active-window)
arrange decision: aspectSource=CACHED placement=TOP placementSource=FLEX dividerCenterY=1236
  → DRAG 3스텝 전부 1시도 통과 → 드래그 → verify residualRows=84
  → ADR-5 보정 1회 (target 1153) → residual 0
arrange done: verified=true residual=0 adjusted=true desired=TOP effective=TOP
```

- **픽셀 물증**: 상단 페인 영상 **풀블리드(검은 띠 0)**, 하단 FW Panel.
- 부수 실증: `purgeStalePanelTasks` 잔존 패널 1개 제거 · 힌지 모니터 수명주기 정상(arm 시 start, 트리거/이탈 시 stop).

### [측정] 항목 5 — FLEX last-success 비오염

| 단계 | 트리거 | 결정 | 결과 |
|---|---|---|---|
| 기준선 | 수동 `--es placement bottom` | `placementSource=OVERRIDE placement=BOTTOM` | done → **BOTTOM 저장** |
| 검증 | (항목 4 의 FLEX TOP 세션 done 직후) 평지(FLAT)에서 수동 **무override** 트리거 | **`placementSource=LAST_SUCCESS placement=BOTTOM`** | done residual=0 — **FLEX 값(TOP)으로 오염되지 않음** |

- 동시 실증: **FLAT 상태에선 FLEX 티어 비활성** — 티어 조건은 "결정 시점 posture == `HALF_OPENED_HORIZONTAL`".

### 신규 함정·한계 (15차 발견)

1. **폴드 전환 중 일시 창의 포그라운드 오염** [측정]: 회전 잠금 해제 직후 삼성 월렛(`com.samsung.android.spay`) quick 카드가 event-tracked 포그라운드를 오염 → 게이트 5 통과 → **월렛 대상 자동 세션**이 걸렸다 (07-27 23:20 실측, `ENTRY_STEP_FAILED` 로 자멸). 유튜브 등 실사용 대상은 **active-window 소스가 정상 타게팅**. → v1.5 후보: 포그라운드 안정성 윈도.
2. **재열기 멈칫 한계** [측정 + 설계 결정]: 닫힘→열기 도중 대역 내 ~90°에서 **≥1.4s(디바운스+침묵) 멈칫**하면 정당한 "닫힌 채 → 노트북 자세로 열기"와 **물리적으로 구분 불가** → 발화 수용이 설계 결정. 이어서 즉시 펴면 2층 취소가 정리하지만, **Done 이후 늦게 펴면 완주된 TOP 분할이 잔존**한다(복구 = 버블 메뉴 분할 해제 또는 패널 finish). 키가드 게이트는 정당 사용례(잠긴 채 열어 자세 잡고 얼굴 인식)를 죽여 **기각** — v1.5 재검토.
3. **닫힘 전환 중 `isSplitActive` 오판 1회 관측** [측정, 재현성 미확인]: 닫히는 도중 시작된 세션이 `CheckingSplit` 에서 active=true 로 오판(직전 상태는 전체화면 확인됨). 창 목록 혼란 추정 — 자세-이탈 취소로 무해화됐다. **재현 시도 미실시, 관측 기록만.**
4. **발화 지연 체감치** [측정]: 자세 정착 후 **≈1.05~1.7s** (디바운스 800 + 침묵 600 겹침 + 폴링 250).

### 파일 변경 요약 (15차)

| 파일 | 변경 |
|---|---|
| `platform/FoldStateMonitor.kt` | 후보 체인 = ① 서비스 자신(타 OS 대비 유지) ② **3-인자 display WindowContext(실채택)** ③ `createDisplayContext` 체인(예비). SDK 31 가드 |
| `platform/HingeAngleMonitor.kt` **(신규)** | `TYPE_HINGE_ANGLE` 래퍼. 샘플마다 `Log.d`, 센서 부재 시 `Log.w` 1회 |
| `domain/FlexModePolicy.kt` | `onHingeAngle`/`isAngleStable` 추가(순수 Kotlin 유지). 불안정 시 armed 유지 |
| `service/ArrangerAccessibilityService.kt` | 250ms 조건 폴링(`awaitFlexTrigger`), 자세-이탈 시 FLEX 세션 취소 |
| 테스트 | 220 → **230** (`FlexModePolicyTest` 14 → 24) |

### 설계상 알려진 잔여 (v1.5 후보, 코드 변경 아님)

- 자동 트리거 게이트 2(busy)와 실제 `startArrange()` 재검사 사이에는 `loadProfilesConfig()` 의
  IO 서스펜드 지점(설정 최초 로드시에만 실제 IO, 이후는 캐시)이 끼어 있어 이론상 TOCTOU 레이스가
  있다 — 동시에 수동 트리거가 먼저 세션을 잡으면 자동 트리거는 `startArrange()` 내부의 기존
  "이미 배치 진행 중" 토스트로 조용히 실패한다(FlexModePolicy 는 disarm 되지 않은 채로 남을 수
  있음). 발생 확률이 매우 낮고(설정 캐시 후에는 게이트가 사실상 동기적으로 이어짐) 브리프의
  게이트 순서를 그대로 따른 결과라 v1 범위에서는 손대지 않았다.
- 기존 분할이 활성 상태에서 플렉스로 접는 경우(게이트 3 `split-already-active`)의 재배치는 v1.5
  범위로 명시적으로 보류했다(브리프 지시).

---

## P3 잔여 소규모 실기기 확인 (2026-07-28 16차 — 3항목 완료)

검증 기기 = SM-F966N / One UI 8 / API 36. P3 에 남아 있던 소규모 실기기 확인 **3항목 전부 통과**.
항목 4 는 검증 결과가 아니라 이 세션에서 도출된 **코드 변경**이므로 구분해 기록한다.

### [측정] 항목 1 — 알림 권한 플로우 (P3-1 잔여) — 통과

| 단계 | 조작 | 물증 |
|---|---|---|
| 회수 | `pm revoke POST_NOTIFICATIONS` 후 온보딩 진입 | 알림 카드 **"권한 필요"(적색)** + **"권한 설정으로 이동"** 버튼 정확 렌더. 오버레이·접근성 카드는 **"허용됨"** 유지 (카드별 상태 독립성 확인) |
| 요청 | "권한 설정으로 이동" 탭 | 시스템 다이얼로그 **"FoldWindow에서 알림을 보내도록 허용하시겠습니까?"** 발화 |
| 허용 | 다이얼로그에서 허용 | 카드 **즉시 "허용됨" 전환**, `dumpsys` 권한 상태 **`granted=true USER_SET`** |

- **FGS 자체 알림 실물 확인**: `pkg=dev.dj.foldwindow id=1001 channel=floating_launcher_channel flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE`.
- 부수 관측: **권한 미보유 상태에서도 FGS·버블은 정상 동작**하고 알림만 억제된다 — 온보딩의 "알림 (선택)" 표기가 설계 의도대로 성립.

### [측정] 항목 2 — 온보딩 중지 레이스: 물리 폴드 접기 (P3-3 §26⑤ 잔여 해소)

7차 ⑤ 는 `input tap(중지) + BACK` **근사 검증**이었고 물리 폴드 접기 자체는 [미검증]으로 남아 있었다 — 이번에 실기로 해소.

- 시나리오: 온보딩 **"버블 중지" 탭 직후 1초 내 완전 접기**.
- 결과: NonCancellable 시퀀스 **완주** — 물증 4종.
  1. **FGS 소멸** (dumpsys 에 접근성 서비스만 잔존)
  2. **`enabled=false` 영속** — 재열기 후 "버블 꺼짐" / 버튼 "버블 시작" 상태
  3. **버블 소멸**
  4. **스토어 에러 로그 0건**

### [측정+주관] 항목 3 — 버블 제스처 실사용감: 무불만

- 자유 조작 세션의 탭 배치 **3세션 전부 done, residual=0** (**5.1s / 1.2s / 5.2s**). 2번째 1.2s 는 **분할 유지 상태에서 재탭한 고속 경로**. 재탭 재배치도 정상.
- **탭 / 드래그 / 롱프레스 오분류 0건** (로그 + 사용자 소견 "없음").
- → 제스처 임계값은 **시스템 표준 유지 확정** (`scaledTouchSlop` / `getLongPressTimeout`). 자체 튜닝 상수 도입은 불필요.

### [코드 변경] 버블 숨김 안전 타이머 30s → 90s (`service/FloatingLauncherService`)

`setBubbleHiddenForArrange(true)` 후 복원 신호가 오지 않을 때의 자동 재표시 유예를 **30s → 90s** 로 상향.

- **근거 (이론 최악 세션 [추정])**: MENU 5스텝 × 3시도 × 3s(**45s**) + 디바이더 **4s** + 드래그 **12s**(세션 오버라이드) + verify ≈ **70s > 종전 30s**.
- **조기 복원 = 세션 중 오버레이 재출현 = 함정 #22(피커發 파트너 전체화면 낙착) 자충수.** 워치독은 액추에이터 사망 시 최후 복구만 담당하므로 **비대칭**(늦은 복원 무해 / 이른 복원 유해) → 큰 값이 안전.
- **실측 최장 세션 12s** (15차까지 누적 관측) — 이론 최악과의 간극은 안전 마진으로 흡수.
- 발화 **실측 0회** — 정상 경로에서는 복원 신호(세션 수렴점 cleanupSession)가 항상 선행. **[미검증]: 타이머 실발화 경로.**
- 위 「버블 오버레이 × 분할 피커 상호작용」 절의 "버블 쪽 30s 안전 타이머" 표기는 이 값으로 대체된다 (해당 절은 당시 기록으로 보존).

## Phase 4 구현분 — 실기기 검증 대기 (2026-07-28 구현 세션, 전 항목 [미검증])

P4-2(파트너 위젯)·P4-3(커버 자동 해제)·P4-4(앱 페어 바로가기) 구현 완료 — qa 정적 검증 PASS(257 테스트·빌드·규칙/계약 회귀 0건). P4-1(팝업 freeform)은 `docs/DESIGN_P41_FREEFORM.md` 프로브 F1~F6 선행 필수(구현 미착수 — 이 세션은 기기 미연결로 프로브 미실시). 아래 전 항목이 다음 기기 캠페인(17차) 대상이다.

### P4-3 커버 화면 전환 자동 분할 해제 [미검증]

구현 전제: 트리거 = `FoldPosture.UNKNOWN` 진입(완전 닫힘, 15차 실측 사상) + 600ms 디바운스 후 재검증. 게이트 = lever→posture→session→no-panel. 발화 = `PanelActivity.instance` **직접 `finishAndRemoveTask()`** (`dismissSplit()` 미경유 — 커버 디스플레이에서 `isSplitActive` 창 목록 신뢰 불가 판단).

| # | 항목 | 기대 물증 |
|---|---|---|
| 1 | 분할 완료 상태에서 완전 접기 | `cover auto-dismiss fired` 로그 + 재열기 시 대상 앱 전체화면(분할·패널 잔존 0) |
| 2 | **닫힘 상태(화면 꺼짐/잠금)에서 패널 finish 가 실제로 분할을 해소하는가** | 핵심 미지수 — 해소 실패 시 설계 재검토 |
| 3 | 접기 → 600ms 내 재펴기 | `skipped: reason=posture-bounced` |
| 4 | 배치 세션 진행 중 접기 | `skipped: reason=session-active` (FLEX 세션은 15차 posture-exit 취소가 선행 — 상호작용 관찰) |
| 5 | 패널 없는 상태(무분할/수동 분할)에서 접기 | `skipped: reason=no-panel` |
| 6 | 레버 회귀: `coverAutoDismiss=false` 주입 | `skipped: reason=lever-off` |
| 7 | 닫힌 채 서비스 재기동(콜드스타트 UNKNOWN) | 무발화 (armed 이전 UNKNOWN 무시) |

- 한계(설계 수용): 세션 중 접기로 게이트에 막히면 그 닫힘 에피소드는 소진된다 — 재열기·재닫기 전까지 패널 잔존. 수동 dismissSplit × 자동 해제 레이스는 동일한 패널 finish 로 수렴(이중 finish 무해)해 게이트 미추가.

### P4-2 파트너 위젯 (시계/메모/검정) [미검증]

| # | 항목 |
|---|---|
| 1 | 파트너 페인에서 모드 3종 전환 렌더 + 하단 모드 버튼 3개 상시 표시(BLACK 포함) |
| 2 | 모드·메모 값 재기동 후 유지 (DataStore 왕복) |
| 3 | 메모 입력 → 홈 전환(ON_PAUSE flush) → 프로세스 킬 → 재진입 시 내용 유지 |
| 4 | 좁은 페인에서 다중행·스크롤 동작, 소프트키보드 등장 시 분할 레이아웃 영향 |
| 5 | 모드 전환 체감 지연 (설계상 즉시) |

### P4-4 앱 페어 바로가기 [미검증]

| # | 항목 |
|---|---|
| 1 | 롱프레스 메뉴 → 「앱 페어 바로가기 만들기」 → One UI 런처 고정 다이얼로그(`isRequestPinShortcutSupported` 지원 여부 포함) → 홈 아이콘 생성 |
| 2 | 바로가기 탭(대상 앱 콜드 상태) → 앱 실행 → 5s 폴링 내 전면 → 자동 배치 done E2E |
| 3 | `exportAppPair` 2s 식별 폴링 성공률 — 스크림 제거 직후 a11y 창 목록 비원자 재구축(#25)과 동일 취약 가설 |
| 4 | 접근성 꺼짐 상태 탭 → 토스트+온보딩 유도 / 대상 앱 제거 후 탭 → "앱을 찾을 수 없습니다" |
| 5 | 타임아웃 상수(식별 2s / 전면 대기 5s) 체감 적정성 — 실측치 아닌 브리프 지정값, 조정 시 근거 병기 |

## P4-1 프로브 F1~F6 (2026-07-28 프로브 세션 — adb 단독, 게이트 통과)

측정 환경: SM-F966N · Android 16 · One UI 8.5(`ro.build.version.oneui=80500`) · deviceState=3(펼침) · 일반 adb 셸(root 아님). 물리 조작 없이 전 항목 수행.

| # | 질문 | 결과 |
|---|---|---|
| F1 | One UI 팝업 = `WINDOWING_MODE_FREEFORM`(5)인가 | ✅ [측정] mode 5 로 실행한 창이 One UI 팝업 크롬(상단 핸들·라운드 코너)으로 렌더. 태스크 레코드에 One UI 전용 필드 `isAlwaysOnTopFreeform=true`, `mNonOccludedFreeformAreaRatio=100` |
| F2 | 셸 권한으로 freeform 실행 가능한가 | ✅ [측정] `am start --windowingMode 5 -n <cmp>` → `mode=freeform`. 기존 태스크가 있으면 프런트 이동과 함께 freeform 으로 **전환**됨 (YouTube 재사용 태스크에서 확인) |
| F3 | 실행 후 bounds 제어 수단 | ✅ [측정] `am task resize <taskId> L T R B` — 요청 bounds (200,300,1200,2100)·(300,200,1300,1700) 픽셀 단위 정확 적용, 오차 0 |
| F4 | UNRESIZEABLE 앱(넷플릭스) 팝업 진입 | ✅ [측정] 진입·리사이즈 모두 성공. **`enable_non_resizable_multi_window=0` 상태에서도 성공** — 셸 경로는 One UI 설정 비의존 |
| F5 | DRM 표면이 팝업에서 렌더되는가 | ⏳ [미검증] 팝업 내 넷플릭스 UI(프로필 선택 화면) 렌더 정상. 실제 DRM 재생은 수동 확인 필요 → 17차 캠페인에 편입 |
| F6 | 팝업 창의 a11y 노출 형태 | ✅ [측정] 프로브 B 재실행(팝업 2개 부유 상태): `APPLICATION` 타입 일반 창으로 노출, **bounds = 태스크 bounds 1:1 일치**, layer = z순서 (넷플릭스 layer 2 / YouTube layer 1). 특수 타입 없음 → 기존 창 추적 코드로 검증 폴링 가능 |

부수 실측:
- 런처 컴포넌트: YouTube `com.google.android.youtube/.app.honeycomb.Shell$HomeActivity` · 넷플릭스 `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`. `$` 이스케이프는 원격 명령 통째 인용 + 원격측 작은따옴표 필수 (Git Bash → 기기 sh 이중 확장)
- 태스크 제거: `am stack remove <taskId>` 동작 확인
- `am start --windowingMode 1` 로 기존 freeform 태스크 전체화면 복귀 시도 → 인텐트만 기존 인스턴스에 전달되고 모드 전환 안 됨 [측정 1회] — 복귀 수단으로 부적합, 홈 이동으로 화면 정리

**판정: F1·F2 통과 → P4-1 진행 확정. 후보 A(Shizuku 셸 명령: `am start --windowingMode 5` + `am task resize`) 채택.** 후보 B(binder/HiddenApiBypass) 불필요. 상세 설계 갱신 = `docs/DESIGN_P41_FREEFORM.md`.

### P4-1 구현 세션 추가 실측 (2026-07-28 같은 날 — qa D1·D2 해소 근거)

- **팝업 초기 배치**: mode 5 실행 직후 태스크 bounds `Rect(354, 150 - 1803, 2123)` — 초기 top y=**150** → `PopupPlanner.TOP_MARGIN=150` 의 근거 (좌우 354/165 비대칭은 One UI 자체 배치 — 플래너는 중앙 정렬 정책 사용)
- **`am stack list` 원문 46행 캡처**: 태스크 행은 **단일 물리 행** (taskId·컴포넌트·bounds·visible·topActivity 가 한 줄). RootTask 헤더·configuration 행이 사이에 끼는 다행 구조. 실측 행 원문:
  `  taskId=4971: com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell$HomeActivity bounds=[200,300][1200,2100] userId=0 visible=true topActivity=ComponentInfo{...}`
  freeform 태스크 포함 노출 확인. `StackListParser` 정규식을 원문 46행에 대조 → 태스크 행 10개 전부 정확 추출 (qa D2 해소, 동일 원문이 `StackListParserTest` 픽스처로 들어감)
- freeform 태스크 bounds 는 mode 5 재실행(기존 태스크 프런트 이동) 후에도 유지됨 [측정 1회]
- 기기에 **Shizuku 미설치** (`pm list packages` 0건) — P4-1 E2E 는 Shizuku 앱 설치·활성화 선행 필요

### P4-1 구현분 — 실기기 검증 대기 (전 항목 [미검증], Shizuku 설치 선행)

| # | 항목 |
|---|---|
| 1 | Shizuku 설치→활성(무선 디버깅)→권한 허용 → 온보딩 카드 3분기 (미설치→앱 실행 유도 토스트 / ping→권한 요청 다이얼로그 / granted 표시) |
| 2 | 버블 메뉴 「팝업으로 열기」 노출 조건 — Shizuku 미가용 시 항목 숨김, 가용 전환 시 메뉴 재오픈만으로 출현 |
| 3 | `ShizukuShell.exec` 최초 `bindUserService` 바인드 지연(50ms/3s 폴링 내 완료) · binder 사망 후 재바인드 |
| 4 | E2E: 전면 앱 → 메뉴 탭 → `am start --windowingMode 5`($ 컴포넌트 작은따옴표) → 창 출현 폴링 → `am stack list` taskId → `am task resize` → bounds 검증 ±8px → `popup done` 로그 |
| 5 | UNRESIZEABLE(넷플릭스) 대상 E2E + **F5 잔여분: DRM 팝업 재생** |
| 6 | 버블 오버레이 존재 상태 팝업 낙착 무결성 — adb 프로브에선 무해 관측, Shizuku UserService 경유(shell uid 동일) 재확인 |
| 7 | `boundsMatch` ±8px 허용오차 적정성 — F3 은 오차 0 실측이나 a11y bounds 보고 지연 여지 |

---

## 17차 캠페인 — Phase 4 구현분 검증 (2026-07-28 밤, adb 주도 + device_state 에뮬레이션)

검증 기기 = SM-F966N / One UI 8 / API 36. 최종 빌드(21:12 설치)로 시작, 캠페인 중 레버 검증용 임시 빌드 1회 왕복(원복 완료).
**신규 검증 수단 확립**: `adb shell cmd device_state state 0` / `state reset` 이 `FoldPosture.UNKNOWN` 진입/복원을 재현
(에뮬 CLOSED → 커버 디스플레이 전환, FoldingFeature UNKNOWN 방출 — cover 정책 발화 실증). 물리 접기 없이 P4-3 게이트
검증 가능. 한계: 상태 전환 전파 ~1s (600ms 미만 바운스 재현 불가), 화면 꺼짐/잠금은 미재현.

### P4-2 파트너 위젯 — 5항목 통과

| # | 결과 |
|---|---|
| 1 | ✅ 시계/메모/검정 3모드 렌더 + 하단 모드 버튼 3개 상시 표시(BLACK 포함) |
| 2 | ✅ 모드(BLACK)·메모 값 프로세스 킬+재배치 후 유지 (DataStore 왕복) |
| 3 | ✅ 메모 입력 → 홈(ON_PAUSE flush) → `am force-stop` → 재진입 시 내용 유지 |
| 4 | ✅ 다중행 렌더 정상. IME 등장 시 One UI 가 상단 페인 압축(분할 구조 유지) → 키보드 해제 시 원복. 잔여 [미검증]: 장문 오버플로 스크롤 |
| 5 | ✅ 모드 전환 즉시 체감 (스크린샷 주기 800ms 내 완료) |

### P4-3 커버 자동 해제 — 6/7 통과 (에뮬레이션 기준)

| # | 결과 |
|---|---|
| 1 | ✅ `cover auto-dismiss fired` 2회 (백그라운드 패널 잔존 케이스 + 분할 활성 케이스) |
| 2 | ✅ **핵심 미지수 해소(에뮬)**: 닫힘 중 패널 finish → 재펴기 시 대상 앱 전체화면, 패널·분할 잔존 0 (dumpsys+스크린샷). 물리 접기(화면 꺼짐/잠금 실상태) 확인은 잔여 |
| 3 | 🔶 `posture-bounced` 사유 로그 미발동 — 에뮬 UNKNOWN 방출 지연(~1s) > 600ms 창이라 도달 불가. 단 0.3/0.55s 플랩에서 **오발화 0** (UNKNOWN 미방출 시 무동작) 실증. 물리로도 인간이 600ms 내 재펴기는 사실상 불가 |
| 4 | ✅ `skipped: reason=session-active` — 배치 진행 중 접기 정확 차단. 부수 관측: 에뮬 접기에선 세션이 커버 디스플레이에서 완주(done residual=30) — 물리 접기(화면 꺼짐)와 다른 에뮬 특이 |
| 5 | ✅ `skipped: reason=no-panel` (4회) |
| 6 | ✅ `skipped: reason=lever-off` — `coverAutoDismiss=false` 임시 빌드로 확인 후 **원복 재설치 완료** |
| 7 | ✅ 콜드스타트 UNKNOWN 무시 — 판별식 성립: 동일 상태(닫힘·무패널)에서 armed 인스턴스는 no-panel 스킵 로그를 남기고, 닫힌 채 재기동한 인스턴스는 완전 침묵 |

### P4-4 앱 페어 바로가기 — 4.5/5 통과

| # | 결과 |
|---|---|
| 1 | ✅ 메뉴 → One UI 「홈 화면에 추가」 다이얼로그(YouTube 식별 정확, `isRequestPinShortcutSupported` 지원) → 홈 아이콘 생성 |
| 2 | ✅ 콜드 E2E: force-stop → 아이콘 탭 → `startArrangeWhenForeground: 전면 확인 후 배치 시작` → arrange done (~8s, placement=LAST_SUCCESS·aspect=CACHED 1.778) |
| 3 | ✅ 식별 폴링 1/1 성공 (스크림 제거 직후, 표본 1) |
| 4 | 🔶 접근성 꺼짐 탭 → 토스트 「접근성 서비스가 꺼져 있습니다」 + 온보딩 유도 ✅ / 대상 앱 제거 케이스는 [미검증 — 실앱 제거 침습 회피] |
| 5 | ✅ 타임아웃 체감 적정 (식별 즉시, 전면 대기 ~1s < 5s) |

### P4-1 팝업(freeform) — 7항목 전부 통과 (DRM 육안만 잔여)

Shizuku v13.6.0 을 GitHub 공식 릴리스에서 adb 설치, `libshizuku.so` 스타터 직접 실행으로 활성화
(앱 UI 「명령어 보기」의 명령 그대로 — start.sh 는 앱 실행 전엔 미생성). **재부팅 시 스타터 재실행 필요.**

| # | 결과 |
|---|---|
| 1 | ✅ 온보딩 3분기: 미설치→토스트 「Shizuku 앱을 설치하세요」 / 서버 가동→권한 요청 다이얼로그(항상 허용/거부) / 허용→카드 「허용됨」+버튼 소멸 |
| 2 | ✅ 미가용 시 메뉴 항목 부재 → 가용 전환 후 **메뉴 재오픈만으로** 「팝업으로 열기」 출현 (서비스 재시작 불요) |
| 3 | ✅ 최초 바인드 ~0.7s 내 exec / **서버 재시작(binder 사망) 후 재바인드 성공** (스타터 재실행 → 즉시 팝업 E2E 정상) |
| 4 | ✅ 유튜브 E2E: `startPopup` → `am start result=0` → `popup done bounds=(64,150,1904,1185)` — 1840×1035 = 1.778 정확, One UI 팝업 크롬 렌더 |
| 5 | ✅ 넷플릭스(UNRESIZEABLE) 팝업 E2E 동일 bounds + **DRM 재생 세션이 팝업 내 구동**: PlayerActivity 재생 중(컨트롤·타임라인 렌더), SurfaceFlinger `Layer (Secure) SurfaceView[...PlayerActivity]`, 캡처 검정 = 보안 표면 보호 정상. **잔여: 실화면 육안 확인** (캡처로는 원리상 판별 불가) |
| 6 | ✅ 버블 표시 중 팝업 낙착 무결 2/2 (함정 #22 는 분할 피커 경유에 한정 — 팝업 경로 비발현) |
| 7 | ✅ `boundsMatch` ±8px 통과 2/2 (요청 bounds 그대로 낙착) |

### ⚠ 신규 결함 — step3 패널 소환 경로 부재 (P4-3 부작용, v1.5 수정 필수)

**증상**: 분할 진입 step3 이 `clickCycle: [step3 panel-picker] cycle=0/1/2 node-not-found` 3전멸 → `ENTRY_STEP_FAILED`.
재현 4회 연속 (21:39 / 21:42 / 21:44 / 21:45). **배치 기능 전체 불능 상태가 지속된다.**

**원인 (결정 실험으로 확정)**: step3 의 「FW Panel」 피커 노드 = One UI 분할 피커 최근앱의 **패널 태스크 카드**.
- 패널 태스크 생존 + purge 미개입(수동 분할 진입) → 피커 1번 항목에 FW Panel 출현 (21:47 실증)
- `finishAndRemoveTask()` 가 카드를 recents 에서 제거 → 소환 폴백 없음 (옛 전략2 LAUNCH_ADJACENT 는 10차에 정당하게 삭제된 상태)
- 카드 제거 주체 전원이 자기 코드다: **커버 자동 해제(P4-3)** · dismissSplit · **purgeStalePanelTasks(세션 시작마다)** · 패널 전체화면 자가 가드

**purge 자충 실증**: 패널을 미리 실행해 살려둬도(홈 이탈) 세션 시작 purge 가 그 태스크를 제거 → 3초 뒤 step3 이 그 카드를 못 찾음 (21:45).
**복구 실증**: 수동 피커 탭(런처 경유 실행)만 유효 (21:47) — `am start` 직접 실행은 자가 가드의 finishAndRemoveTask 로 무효 (21:44).
**과거 캠페인이 통과한 이유**: 세션 간 패널 태스크/카드가 상시 잔존(BACK-finish 리셋 관행)했던 우연 의존 — 이번에 커버 해제가 처음으로 "카드 0" 상태를 만들었다.

**수정 방향 후보 (다음 세션 브리프)**:
1. step3 진입 전 패널 프리론치로 카드 보장 (split-select 진입 **전**에 실행 — 진입 후 LAUNCH_ADJACENT 파괴 실측과 무충돌인지 검증 필요)
2. purgeStalePanelTasks 를 세션 시작이 아닌 Done 후/유휴 시로 이동 (자충 제거)
3. 피커 앱 그리드(⋮⋮) 탐색 폴백

→ **설계 확정: `docs/DESIGN_27_PANEL_CARD.md`** — 아래 18차 프로브 결과가 후보 판정을 뒤집었다.

---

## 18차 프로브 — #27 결함 원인 확정 + 수정 방향 판정 (2026-07-28 밤, adb 전용, 코드 무변경)

기기 SM-F966N / One UI 8 / API 36, 펼침 1968×2184 세로. 버블 FGS 미기동(함정 #22 무개입 조건), 접근성 ON.
**전 항목 코드 변경 없이 adb 만으로 판정했다.**

### ① purge 자충 — 단일 로그 시퀀스로 물증화 [확정]

```
카드 확인      dumpsys recents 패널 태스크 = 1
ARRANGE 발사   FWArranger: purgeStalePanelTasks: 잔존 패널 태스크 1 개 제거
               FWArranger: arrange decision: target=com.android.settings …
카드 재확인    dumpsys recents 패널 태스크 = 0
               transition: EnteringSplit(step=3, attempt=3) -> Failed(reason=ENTRY_STEP_FAILED)
               arrange failed: reason=ENTRY_STEP_FAILED
```
**자기 코드가 자기 전제를 지우고 그 부재로 실패**하는 인과가 한 시퀀스로 확정됐다.

### ② G1 통과 — `finish()` 는 분할을 해소하면서 카드를 남긴다 [확정]

- 분할(설정 상단 / FW Panel 하단, 메모 위젯 렌더) 상태에서 패널 페인 BACK
- 결과: 양 stage `visible=false … sz=0` → **분할 해소**
- 패널 태스크: `Recent #1 … Activities=[] autoRemoveRecents=false` → **카드 생존**
- 분할-선택 재진입 시 피커 **1번 항목(MRU)** 으로 「FW Panel」 출현 (`label b=[156,1729][279,1804]`)
- → `finishAndRemoveTask` 의 `removeTask` 부분은 **어떤 실측에도 요구되지 않은 초과 동작**임이 확정

### ③ G3 통과 — 액티비티가 죽은 카드도 정상 낙착한다 [확정 · purge premise 반증]

`Activities=[]`(액티비티 인스턴스 0, 태스크 레코드만) 카드를 피커에서 탭:
```
Task{9d55257 #5022 A=10659:dev.dj.foldwindow.panel} mode=multi-window stage=side/bottom bounds=[0,1099][1968,2184]
Task{6e775d5 #5025 A=1000:com.android.settings.root} mode=multi-window stage=main/top  bounds=[0,0][1968,1085]
```
- 정상 상하 분할, **동일 taskId 재사용**, 전체화면 강탈 0, 자가 가드 로그 침묵
- → **purge 의 원래 근거(2026-07-25 「잔존 카드 탭 → 전체화면 재사용 → 분할 파괴」)가 이 기기·OS 에서 반증됐다.**
  그 실측은 ⓐ `launchMode=singleTask` 시절 ⓑ 버블 숨김(함정 #22) 도입 **이전**이라, 이미 다른 수정으로 해소된 상태였을 가능성이 높다

### ④ 피커 구조 실측 — 후보 ③(앱 그리드) 취약 확정

- 피커 = `com.sec.android.app.launcher/…fromrecent.FromRecentActivity`, 헤더 「앱 선택」 + `list_container` GridView 「최근 앱」 섹션
- 헤더에 **`all_apps_button`**(desc 「모든 앱 버튼」, `b=[1734,1135][1851,1279]`)·**`search_button`** 존재
  → **resource-id 셀렉터라 로케일 무관** (열린 질문 #6 의 다국어 우려는 이 두 노드엔 비적용)
- 그러나 탭해서 연 앱 서랍(`overlay_apps_list`, 아이콘 87개) **1페이지에 FW Panel 부재**, 노출된 `scrollable` 노드 **0**
  → 페이징/검색 없이 도달 불가. **최후 폴백으로만** 유지
- 부수: recents **오버뷰**에는 FW Panel 미노출인데 **피커**에는 노출 — 두 UI 의 소스가 다르다 (`mHasBeenVisible=false` 태스크 취급 차이 추정)

### ⑤ 카드 복구 수단 실측 (개발 편의)

`adb shell "am start -n dev.dj.foldwindow/.ui.PanelActivity > /dev/null; input keyevent 3"`
→ HOME 이 `onPause` 를 불러 자가 가드 job 을 취소 → 카드 생성 + 액티비티 STOPPED 생존 (가드 로그 침묵 확인).
**구현에는 쓰지 말 것** — 타이밍 의존이라 ADR-2 위반. 캠페인 중 카드 0 상태 복구용 도구로만 사용한다.

### ⑥ AOSP 사실관계 [확정 — 소스 라인 확인]

| 사실 | 함의 |
|---|---|
| `autoRemoveFromRecents` 기본 = 일반 액티비티 **false**, document 액티비티 true. `Task#cleanUpResourcesForDestroy` → `shouldAutoRemoveFromRecents()` false 면 `mRecentTasks.remove` 미실행 | ② 의 근거 |
| **함정**: `shouldAutoRemoveFromRecents()` 는 `!hasChild() && !getHasBeenVisible()` 이면 **강제 제거** | **한 번도 보인 적 없는 태스크는 finish 시 카드가 사라진다.** 소환 카드는 1회 가시화되거나 액티비티를 아예 안 만드는 수단을 써야 함 |
| 죽은 카드 탭 → `startActivityFromRecents` → `task.intent` 재실행, `Task#setIntent` 은 **extras 보존** | 열린 질문 #28(base intent 오염)이 [이론]→**[확정]**. 재부팅 후 디스크 복원 경로는 [불명] |
| `ActivityOptions.makeTaskLaunchBehind()` 공개(API 21). NEW_DOCUMENT 동반 필요, singleInstance/singleTask 미지원(패널은 standard), **`onResume` 미호출**(`mDoResume=false`) → STOPPED 정착. 완료 시 `handleLaunchTaskBehindCompleteLocked` 가 `mRecentTasks.add` | 소환 수단 B1. 자가 가드 구조적 무발화 |
| `moveTaskToBack(true)` = 제거 경로 미트리거 → 카드 잔존·액티비티 STOPPED 생존 | 소환 수단 B2 성립 근거 |
| **`ActivityManager.addAppTask()`**(API 21) = *"a new recents entry … will exist **without an activity**"*. 명시 ComponentName + NEW_DOCUMENT + **RETAIN_IN_RECENTS** 필요, 썸네일 인자, 실패 -1(앱당 상한) | **액티비티 미시작 = 포그라운드 무접촉.** 소환 1순위(B0) |

### 잔여 프로브 (구현 중/후)

| # | 확인 | 통과 기준 |
|---|---|---|
| G2 | B0 `addAppTask()` One UI 8 수용 | 반환 ≥0 ∧ 포그라운드 유지 ∧ 피커 출현 ∧ 탭 시 분할 낙착 (실패 시 B1→B2 강등) |
| G4 | 결함 재현 E2E | 커버 자동 해제로 카드 0 → 즉시 배치 → 3연속 done |
| G5 | prune × 소환 무자충 | `pruneExtraPanelTasks: 보존 1` ∧ 카드 생존 ∧ step3 성공 |
| G6 | 재설치 스테일 회귀 | 태스크 생성 → 재설치 → 배치 1회 성공 (③ 으로 premise 반증됐으므로 확인 성격) |
| G7 | 위 「함정」 회귀 | 소환 카드가 미가시 상태로 finish 되는 경로 부재 (B0 는 액티비티 미생성이라 비대상) |

### 기기 잔여 상태

패널 태스크 #5029 복구 완료(액티비티 STOPPED 생존). 회전 설정 무변경. 분할 미활성. 임시 빌드 없음(코드 무변경 캠페인).

**심각도**: 높음 — P4-3 커버 자동 해제가 이 상태를 일상 사용에서 재생산한다. P4-3 활성 배포 전 수정 필수.

### 부수 실증·관측

- **12차 캐시 폴백 실전 발동 2회**: 오염 pre(1.6 / 0.917) → confirm 합치 `RAW_DISAGREE` → `source=CACHED` 1.778 낙착, done residual=0 — 설계 의도대로 오염 차단
- 오염 pre 단독 결정 한계 재확인: 유튜브 피드(비영상)에서 pre=PURE_BLACK 0.917 conf 0.95~0.99 → dividerCenterY=1780(HIT_MAX_PANE_CEILING) — confirm 게이트 전 단계라 종전 동작(#12 v1.5 범위)
- 메모 IME: 분할 상태에서 키보드가 상단 페인을 압축(One UI 동작)하고 해제 시 원복 — 레이아웃 파괴 없음
- 기기 잔여 상태: Shizuku 설치·서버 가동(재부팅 시 재실행 필요), 버블 store enabled=true(다음 부팅 자동 시작), 홈에 YouTube 앱페어 바로가기 잔존, 회전 설정 원복(free)

### Phase B — 물리 조작 잔여 (사용자 확인 필요)

1. P4-3 항목 1·2 **물리 접기** — 화면 꺼짐/잠금 실상태에서 발화·해소 (에뮬은 커버 디스플레이 활성 상태라 등가 아님)
2. P4-1 항목 5 **DRM 육안** — 넷플릭스 팝업 재생 화면이 실제로 보이는지 (캡처 불가 원리)
3. (참고) P4-3 항목 3 물리 600ms 재펴기 — 인간 조작으로 사실상 도달 불가, 미검증 수용 권고

---

## 19차 — #27 v1 실기기 캠페인 (2026-07-28 밤)

> 대상 빌드 `c252a01` (축 A + 축 B + #28). 절차 = `docs/CAMPAIGN_19_PANEL_CARD.md`
> 기기 SM-F966N(R3CY8029XBF), 내부 화면 1968×2184, 회전 free(잠금 미사용)

### 한 줄 결론

**축 A(파괴 제거)는 전부 통과했고, 축 B(소환)는 실패했다 — 그것도 "안 되는" 실패가 아니라
소환이 만든 카드가 step3 를 깨뜨리는 유해 실패다. 그리고 축 B 의 존재 이유였던
「카드 0 = 배치 불능」 전제 자체가 5/5 로 반증됐다.**

### 게이트 결과

| 게이트 | 판정 | 근거 |
|---|---|---|
| G2 소환(B1) | ❌ **실패(유해)** | 소환 자체는 성공(`panel-card: summoned(mode=launch-behind)`, 14ms)하나 그 카드를 step3 가 탭하면 **전체화면 낙착** → 자가 가드 3회 발화 → `ENTRY_STEP_FAILED` |
| G4 결함 재현 | ✅ 통과 | `cover auto-dismiss fired` → 분할 해소 → **카드 생존**(17차엔 여기서 소멸) → 이어서 **3/3 done**, 전부 `already-present`, `node-not-found` 0 |
| G5 prune 무자충 | ✅ 통과 | 패널 태스크 2개 상태에서 `pruneExtraPanelTasks: 보존 1 / 제거 1` → 남은 카드로 step3 성공 → `done verified=true residual=0` |
| G6 재설치 스테일 | ✅ 통과 | 재설치로 `Activities=[]` 죽은 카드 → 배치 1회 `done`, 자가 가드 침묵, `already-present` |
| G7 AOSP 함정 | ✅ 미발동 | 소환 카드(`mHasBeenVisible=false`)가 가드 finish 된 뒤에도 **카드 잔존** — `FLAG_ACTIVITY_RETAIN_IN_RECENTS` 가 `shouldAutoRemoveFromRecents` 강제 제거를 상쇄한 것으로 추정 |
| 레버 회귀 | ✅ 통과 | `panelCardPreflight=false` → `panel-card: lever-off` ∧ 소환 시도 0 ∧ 축 A 정상 |

### [확정] G2 실패의 원인 — 소환이 base intent 를 오염시킨다

동일 세션·동일 기기에서 **카드 종류만 바꾼 대조 실험**:

| 카드 출처 | base intent flags | step3 결과 |
|---|---|---|
| `makeTaskLaunchBehind()` 소환 (taskId 5036) | `flg=0x18182000` = NEW_TASK\|**MULTIPLE_TASK**\|**NEW_DOCUMENT**\|RETAIN_IN_RECENTS | **전체화면 낙착** → `fullscreen 상태 감지` ×3 → `ENTRY_STEP_FAILED` |
| 런처 형태 실행 (taskId 5038) | `flg=0x10000000` = NEW_TASK only | **분할 페인 정상 낙착** → `arrange done verified=true residual=0` |

죽은 카드 탭은 `startActivityFromRecents` → **base intent 재실행**이다(18차 Q2). 소환이 실은
`NEW_DOCUMENT|MULTIPLE_TASK` 는 그대로 보존되어, 피커 탭 시 분할 스테이지가 아니라
**새 문서 태스크(전체화면)** 로 라우팅된다. **#28 과 동일한 결함 클래스이며 대상만 extras → flags 다.**

로그 원문(소환 카드 세션):
```
23:48:28.096 panel-card: summoned(mode=launch-behind)
23:48:30.451 EnteringSplit(step=3, attempt=1)
23:48:31.917 PanelActivity: fullscreen 상태 감지 — 파트너 전용 액티비티이므로 종료
23:48:33.054 EnteringSplit(step=3, attempt=2)   (이하 attempt=3 동형)
23:48:38.265 arrange failed: reason=ENTRY_STEP_FAILED
```

부수 [확정]: 소환 시점에는 가드가 **발화하지 않았다**(첫 가드 로그가 step3 시도 이후) —
Q3 의 "`makeTaskLaunchBehind` 는 `onResume` 미호출" 예측은 **맞다**. 실패는 소환 순간이 아니라
**그 카드를 탭할 때** 발생한다.

### [확정] 축 B 의 전제 반증 — 카드 0 에서도 step3 는 정상 동작한다 (5/5)

레버 off(소환 없음) + 패널 카드 **0개** 상태에서 배치를 시도한 5회 전부 `arrange done`,
step3 는 **attempt 1 에서 성공**(`node-not-found` 0건).

| 카드 0 유도 방법 | 시도 | 결과 |
|---|---|---|
| `adb uninstall` 후 재설치 (신규 설치) | 3 | 3/3 done |
| `pm clear` (설치 시점 유지 — 신규 설치 교란 배제) | 2 | 2/2 done |

`pm clear` 표본이 **"신규 설치 앱이라 피커에 노출된 것"이라는 교란을 배제**한다.
즉 피커는 recents 카드가 아닌 **앱 목록**에서도 「FW Panel」을 제공한다
(`PanelActivity` 의 MAIN/LAUNCHER 노출이 근거로 추정). 18차 후보 ③ 조사는 `all_apps_button`
으로 연 **앱 서랍**을 봤을 뿐, 피커 기본 화면의 앱 목록은 확인 대상이 아니었다.

⇒ DESIGN_27 §1.3 의 구조 진단 「카드를 만드는 경로 0개」는 **부정확**하다. 카드가 없어도
피커에 노드가 존재하므로 소환은 **불필요**하다.

### [미해결] 그렇다면 17차 3전멸의 진짜 원인은?

17차 `node-not-found` 4회 연속(카드 0)은 이 캠페인에서 **재현되지 않았다**(카드 0 에서 5/5 성공).
따라서 「카드 0 → step3 불능」 인과는 **불완전한 설명**이다. 17차엔 다른 요인(피커 화면 상태,
purge 가 세션 내에서 카드를 지운 타이밍, 스크롤/레이아웃 차이)이 함께 작용했을 가능성이 크다.
축 A 는 이 미해결과 무관하게 정당하다(자기 코드가 자기 전제를 지우는 초과 동작 제거).

### 부수 관측

- `RecentTaskInfo` 는 dumpsys 에 `lastActiveTime` 을 노출한다(예: 276753305). 공개 API 접근 가능
  여부는 별개 — 현재 구현은 0 전달 + `appTasks` MRU-first 순서 타이브레이크이며, G5 에서
  **의도대로 최신 카드가 보존**됐다(제거된 것은 오염 카드 5036)
- 카드 0 세션의 pre 측정이 `aspect=0.972332 conf=0.575` → `dividerCenterY=1780
  (HIT_MAX_PANE_CEILING)` 로 낙착 후 보정으로 수렴(`residual=114`) — #12 v1.5 범위, 이번 캠페인 무관
- `cmd device_state` 원복 명령은 `reset` 이 아니라 **`cmd device_state state reset`**
  (17차 기록의 표기 정정)

### 기기 잔여 상태

레버 실험용 JSON 은 원복 완료(`config/window_profiles.json` 무변경). 기기에는 **레버 off 빌드가
설치된 상태**이며 DataStore 는 `pm clear` 로 초기화됐다(버블 설정·placement 기록 소실). 회전 free.
분할 미활성. 패널 카드 1개(정상 형태).

---

## 개선 웨이브 W1 (보안 차단) — 실기기 재검증 대기 [미검증]

**2026-07-29 · 코드 구현 완료, 실기기 미실시.** 계획 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W1.
정적 DoD 는 전부 통과했다(테스트 286 · assembleDebug · lintDebug 신규 0 · assembleRelease ·
릴리스 병합 매니페스트에서 4컴포넌트 + FileProvider **0건**).

| # | 항목 | 확인해야 할 것 | 상태 |
|---|---|---|---|
| W1-1 | **S1 debug 소스셋 분리** | 디버그 빌드에서 `adb shell am broadcast -a dev.dj.foldwindow.ARRANGE -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver --es placement top` 이 **종전과 동일하게** 배치를 트리거하는가 (선언이 debug 매니페스트로 이동했으므로 병합 결과 확인이 목적) | [미검증] |
| W1-2 | **S1 프로브 생존** | 디버그 빌드에서 접근성 설정에 프로브 서비스가 그대로 보이고 `ProbeActivity` 런처 아이콘이 있는가 (F2 2단계 측정에 계속 필요) | [미검증] |
| W1-3 | **S4 토큰 정상 경로** | 버블 롱프레스 메뉴 → 「분할 해제」 1회. 1차 경로는 `PanelActivity.instance.finish()` 라 **토큰을 쓰지 않는다** — 회귀 없음이 기대값 | [미검증] |
| W1-4 | **S4 토큰 폴백 경로** | `instance==null ∧ hasPanelTask()` 인 희귀 경로에서 토큰이 실제로 소비되어 finish 되는가. **PROGRESS.md 「남은 작업」 B-1/B-2 와 동일 조건**이므로 같은 세션에서 함께 유도한다 | [미검증] |

**정적으로 확정된 사실 (실기기 없이 검증 완료):**
- 릴리스 병합 매니페스트(`packaged_manifests/release/`)에 `ArrangeTriggerReceiver` ·
  `ProbeTriggerReceiver` · `ProbeActivity` · `ProbeAccessibilityService` · `FileProvider` **0건**.
  디버그 병합 매니페스트에는 5종 전부 존재
- **AGP 매니페스트 머저는 XML 주석을 병합 결과에 그대로 보존한다** — main 매니페스트 주석에
  남아 있던 `probe.ProbeActivity` / `probe.ProbeTriggerReceiver` 리터럴이 릴리스 매니페스트까지
  따라 들어갔다. 그래서 해당 주석 2곳의 클래스명 리터럴을 debug 매니페스트 경로 표기로 바꿨다
  (정보 손실 없음 — 오히려 "S1 이후 debug 전용" 이라는 갱신된 사실을 반영)
- **lint `ForegroundServiceType` 오탐** — debug 변형(매니페스트 2개 병합)에서만 발생하고
  `lintRelease`(매니페스트 1개)는 동일 코드로 통과한다는 **대조 실험으로 오탐 확정**.
  병합 debug 매니페스트에 `FloatingLauncherService android:foregroundServiceType="specialUse"` 가
  정상 존재함을 육안 확인. `FloatingLauncherService.startForegroundCompat()` 에 `@SuppressLint` +
  근거 주석으로 억제 (baseline 미사용 — 억제 근거를 코드 옆에 남기는 쪽을 택함)

---

## 개선 웨이브 W2 (Shizuku 셸 하드닝) — 실기기 재검증 대기 [미검증]

**2026-07-29 · 코드 구현 완료, 실기기 미실시.** 계획 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W2.
해소 대상 = F3(타임아웃 무효) · F4(파이프 데드락) · F5(바인드 래치 고착) · S2(허용 목록 부재) ·
S3(셸 문자열 보간). 정적 DoD 통과(테스트 299 · assembleDebug · lintDebug 신규 0 · assembleRelease).

**⚠ 이 웨이브는 P4-1 팝업 경로 전체를 관통하는 변경이다.** AIDL 시그니처가 바뀌었으므로
`versionCode` 를 2→3 으로 올렸다(`Shizuku.UserServiceArgs.version()` 이 UserService 프로세스
재생성을 결정 — 안 올리면 구 바이너리 재사용으로 `AbstractMethodError`). 재검증 전까지
**팝업 모드는 전부 [미검증] 로 간주**한다.

| # | 항목 | 확인해야 할 것 | 유도 방법 | 상태 |
|---|---|---|---|---|
| W2-1 | **AIDL 재생성** | 재설치 후 첫 팝업에서 `AbstractMethodError` 없이 UserService 가 바인드되는가. 기대 로그 = `FWArranger.Shizuku: ShellExecUserService 연결됨` | 자연 발생 (첫 팝업 시도) | [미검증] |
| W2-2 | **P4-1 E2E 유튜브** | argv 전환 후 팝업 창이 뜨고 실제 bounds 가 `PopupPlanner` 계산값과 일치하는가. **17차 절차 그대로 재사용** | 버블 메뉴 → 「팝업으로 열기」 | [미검증] |
| W2-3 | **P4-1 E2E 넷플릭스** | 동일 + DRM 콘텐츠 재생 | 동일 | [미검증] |
| W2-4 | **S3 회귀 없음 (`$` 클래스명)** | `am start --windowingMode 5 -n <component>` 가 `Shell$HomeActivity` 같은 `$` 포함 클래스명에서 정상 동작. 구 방식의 작은따옴표 인용을 제거했으므로 **이것이 argv 전환의 직접 실증**이다 | W2-2 유튜브가 곧 이 케이스 | [미검증] |
| W2-5 | **S2 허용 목록 오차단 없음** | 실사용 3종(`am start`/`am stack list`/`am task resize`)이 전부 통과. `blocked by policy` 로그가 **0건**이어야 한다 | W2-2·W2-3 로그 확인 | [미검증] |
| W2-6 | **F5 재바인드 복구** | 바인드 실패 후 다음 `exec` 이 **재바인드를 시도**하는가 (구 코드는 `binding=true` 고착으로 영구 불능) | `adb shell am force-stop moe.shizuku.privileged.api` → 팝업 시도(실패 관찰) → Shizuku 재실행 → 팝업 재시도(성공해야 함) | [미검증] |
| W2-7 | **F3 타임아웃 실효** | `am` 이 걸렸을 때 원격이 `-1 / timeout after 5000ms` 를 돌려주고 `popupInFlight` 가 풀리는가 | **인위 유도 곤란** — 자연 발생 대기. `logcat -s FWArranger.Shizuku` 상시 계측 | [미검증] |

**정적으로 확정된 사실 (실기기 없이 검증 완료):**
- **argv 토큰 순서가 구 셸 문자열과 정확히 일치** — `am start --windowingMode 5 -n <component>` /
  `am stack list` / `am task resize <id> <l> <t> <r> <b>`. 토큰 단위 대조 완료.
  `performStartPopup` 의 제어 흐름·폴링 조건·토스트 문구는 무변경(17차 검증분 보존)
- **허용 목록 강제 지점은 원격**(`ShellExecUserService.run`)이다. 클라이언트(`ShizukuShell.exec`)의
  동일 검사는 fail-fast 용 중복이며, 우회 가능한 클라 검사만으로는 보안 통제가 성립하지 않는다
- **타임아웃 예산 배치**: 클라 `withTimeoutOrNull` = `timeoutMs + BINDER_OVERHEAD_MS(2s)` >
  원격 `waitFor(timeoutMs)`. 코루틴 취소는 중단점에서만 작동하는데 바인더 호출은 중단점이
  아니므로 **실효 타임아웃은 원격에만 존재한다** — 클라 예산을 더 크게 잡아야 원격이 먼저
  포기하고 진단 가능한 timeout 문자열이 돌아온다. `ensureBound()` 는 자체 데드라인
  (`BIND_TIMEOUT_MS` 3s)을 가지므로 이 창 **밖**에 둬 예산 이중 소비를 막았다
- **소스 위생 함정 [신규]** — Kotlin 소스에 NUL 문자 이스케이프를 쓰려다 파일 기록 과정에서
  **raw NUL 바이트(0x00)가 소스에 그대로 박히는** 현상 발생. Read 계열 도구가 NUL 을 공백처럼
  렌더해 오타로 오인하기 쉽다. `c.code == 0` / `0.toChar()` 형태로 재작성해 이스케이프 자체를
  소스에서 제거함. **이 도구 체인에서 파일 내용에 유니코드 이스케이프를 넣을 때 상시 주의**

---

## 개선 웨이브 W3 (기하 정합성 · 도메인 불변식) — 실기기 재검증 대기 [미검증]

**2026-07-29 · 코드 구현 완료, 실기기 미실시.** 계획 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W3.
해소 대상 = F1(`WindowGeometry` 필드 간 불변식 부재) · F2 1단계(기하 하드코딩 / 실화면 불일치 —
명시적 실패 가드). 정적 DoD 통과(테스트 311(304+7) · assembleDebug · lintDebug 신규 0).

| # | 항목 | 확인 방법 / 유도 | 상태 |
|---|---|---|---|
| W3-1 | **가로 배치 1회 정상** | 유튜브 등에서 평소대로 버블 탭 → 배치 성공. 가드가 가로 경로에 회귀를 주지 않았음을 확인 (기대: 새 로그 없음, 배치 정상) | [미검증] |
| W3-2 | **세로 트리거 시 명시적 실패** | 기기를 세로로 두고 버블 탭 → 토스트 「이 화면 방향/디스플레이는 아직 지원하지 않습니다」 + `logcat -s FWArranger` 에 `startArrange: 화면 기하 불일치` 1건 | [미검증] |

**F2 2단계(v1.5, 이번 웨이브 범위 밖) 참고:** 실화면 기반 `WindowGeometry` 생성은 **세로 분할의
디바이더 두께·최소 페인 높이 실측**이 선행 조건이며, 코드 작업이 아니라 프로브 측정 작업이라
v1 범위 밖이다.

---

## 개선 웨이브 W5 (구동부 안정화) — 실기기 재검증 대기 [미검증]

**2026-07-29 · 코드 구현 완료, 실기기 미실시.** 계획 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W5.
해소 대상 = F6(`dispatch()` 재진입 → 이벤트 큐 + 단일 드레인 루프) · F9 v1 대응(터미널 메시지에
허용치 초과 구분). 정적 DoD 통과(테스트 312(311+1) · assembleDebug · lintDebug 신규 0).

**이 웨이브가 실기기 세션을 요구하는 이유:** F6 은 배치 오케스트레이션의 **구동부 전체를 지나는
공통 경로**를 바꾼다. 동작 등가는 정적으로 논증됐지만(터미널 전이 12곳 전부 effects 없음 —
qa 전수 확인), 그 논증이 실제 이벤트 순서에서도 성립하는지는 logcat 대조로만 확인된다.

| # | 항목 | 확인 방법 / 유도 | 상태 |
|---|---|---|---|
| W5-1 | **DRAG 레시피 무회귀** | 유튜브 등에서 평소대로 버블 탭 → 배치 성공. 잔여 px·보정 횟수가 종전과 같은 수준 | [미검증] |
| W5-2 | **MENU 레시피 무회귀** | 메뉴 진입 레시피가 쓰이는 앱에서 배치 성공 | [미검증] |
| W5-3 | **전이 로그 순서 동일** | `logcat -s FWArranger` 의 `transition: A -> B (event=...)` 줄 순서를 종전 세션 로그와 대조. 로그 포맷 문자열은 문자 단위 무변경(변수명 `event`→`e` 치환뿐)이므로 **줄 내용까지 동일**해야 한다 | [미검증] |
| W5-4 | **F9 「(허용치 초과)」 발화** | 개발 빌드에서 `config/window_profiles.json` 의 `defaults.residualTolerancePx` 를 **0 으로 임시 변경** → 잔여 1px 이상이면 토스트가 `배치 완료 · 잔여 Npx (허용치 초과)` 로 뜬다. `logcat` 의 `arrange done: ... tolerance=0` 로 판정 근거 대조. **확인 후 반드시 8 로 원복** (CLAUDE.md 함정 #7 — 이건 값 변경이 아니라 일시 실험이다) | [미검증] |

**W5-4 가 특히 중요한 이유 — JVM 테스트 사각지대 [확정, qa 변조 실험]:** F9 는 서비스 레이어 코드이고
`ArrangerAccessibilityService` 를 인스턴스화하는 JVM 테스트가 **0개**다. qa 검증자가 부등호를
`>` → `>=` 로 변조했을 때 **테스트 29개 전부 통과**했다. 즉 이 3줄은 **실기기 육안이 유일한 검증 수단**이다.
(W4 에서 Robolectric 배선이 생겼으므로 v1.5 에서 서비스 레이어 테스트를 넓힐 여지는 있다.)

**부수 — 터미널 전이 불변식 [확정, 소스 전수]:** `ArrangeStateMachine.kt` 에서 `Done`/`Failed` 를 만드는
Transition 은 **12곳이고 전부 `emptyList()`** (174/218/251/256/298/328/332/348/353/377/383/391행).
F6 의 동작 등가는 이 불변식에 의존한다 — 터미널 + effect 를 동시에 내는 전이가 생기면 신규 코드는
`cleanupSession()`(machineState→Idle) **이후에** 큐를 드레인해 구 코드와 순서가 갈린다.
이 불변식은 이제 `ArrangeStateMachineTest` 의 전수 조합 테스트(대표 상태 10 × 이벤트 15 × config 2 =
300 조합, 그중 터미널 103건)가 기계 강제한다. **W6/W7 이 이 불변식에 기댄다.**

---

## 개선 웨이브 W6 (세션 상태 캡슐화) — 실기기 재검증 대기 [미검증]

**2026-07-29 · 코드 구현 완료, 실기기 미실시.** 계획 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W6 · §M1.
해소 대상 = M1 1단계(세션 가변 필드 17개 → `private class Session` 1개, `cleanupSession()` = `session = null`).
정적 DoD 통과(테스트 312 무변경 · assembleDebug · lintDebug 신규 0 · 프로덕션 1파일 185+/124−).

**이 웨이브가 실기기 세션을 요구하는 이유:** 계획 전체에서 **회귀 위험 최대**로 지정된 항목이고,
세션 상태를 읽는 **모든 경로**(진입·드래그·측정·합치·터미널 보고)를 동시에 지나간다.
정적으로는 읽기 폴백 36곳 전수 대조로 등가가 확인됐지만, 그 등가가 실제 이벤트 순서에서 성립하는지는
logcat 대조로만 확인된다.

| # | 항목 | 확인 방법 / 유도 | 상태 |
|---|---|---|---|
| W6-1 | **DRAG 레시피 무회귀** | 유튜브 등 버블 탭 → 배치 성공. `arrange decision:` / `verify:` / `arrange done:` 3줄이 W5 세션 로그와 **문자 단위 동일 포맷** | [미검증] |
| W6-2 | **MENU 레시피 무회귀** | UNRESIZEABLE 앱(넷플릭스류) 배치 성공. `resize-mode detection: … recipe=MENU` + 진입 5단계 완주 (= `Session.entryRecipe` / `config.entryStepCount` 배선 실증) | [미검증] |
| W6-3 | **취소 경로 + 재배치** | 배치 진행 중 취소 → `배치 실패: 사용자 취소` 토스트 + **버블 복원**. 이어서 **재배치 1회가 정상 성공**해야 한다 — `session = null` 이 세션 상태를 완전히 비웠는지의 직접 증거 | [미검증] |
| W6-4 | **분할 해제** | 배치 완료 후 버블 메뉴 「분할 해제」 정상 동작 (`dismissSplit` 이 `machineState == Idle` 가드를 통과) | [미검증] |
| W6-5 | **연속 2세션 상태 누수 없음 (M1 의 존재 이유 직격)** | 앱 A 배치 → 완료 → **앱 B 배치**. B 의 `arrange decision:` 에서 `label=` `cachedAspect=` `placementSource=` 가 전부 **B 기준**이고, `consensus:` 게이트가 B 에서 **다시 발동**(`aspectConfirmed` 누수 없음), `aspect cache save:` 의 `pkg=` 가 B 여야 한다 | [미검증] |

**W6-5 는 계획서 원문의 4경로에 없던 항목**이다(qa 검증자 권고로 추가). 이 웨이브의 목적 자체를
검증하는 유일한 항목이므로 반드시 함께 확인한다.

**JVM 테스트 사각지대 [확정, qa 변조 실험 7종]:** `ArrangerAccessibilityService` 를 인스턴스화하는
JVM 테스트가 **0개**다(테스트 소스에는 주석 2건만 언급). qa 검증자가 **7종을 동시에 변조**했는데도
**312개 전부 통과**했다 — 폴백 6종 오염(`Placement.TOP`→`BOTTOM`, `"FALLBACK"`→`"FLEX"`,
`requireAgreement`/`cacheAspectEnabled` `true`→`false`, `EntryRecipe.DRAG`→`MENU`,
`DEFAULT_ASPECT`→`1.0f`)에 더해 **`session = Session(...)` 을 `dispatch(Start)` 뒤로 옮기는
심각한 회귀**까지 포함해서다. 즉 **「테스트 312 통과」는 이 웨이브의 안전 근거가 아니며,
실기기 스모크가 유일한 실효 검증 수단이다**(W5-4 와 동일 결론).

---

## 개선 웨이브 W7 (성능 · 중복 정리) — 실기기 재검증 대기 [미검증]

**2026-07-30 · 코드 구현 완료, 실기기 미실시.** 계획 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W7 · §P1+M3 · §P2 · §P4.
해소 대상 = P1(셀렉터별 다중 DFS → 단일 DFS + 노드/깊이 상한) · M3(`NodeActions`/`Polling` 추출) ·
P2(부팅 경로 `runBlocking` 제거) · P4(`toPillarboxScan` 세로 읽기 범위 축소 + 1px 크래시 수정).
정적 DoD 통과(테스트 322 · assembleDebug · lintDebug 신규 0 · baseline 15 무변경).

**이 웨이브가 실기기 세션을 요구하는 이유:** P1 은 **어떤 노드를 고르는가**를, P2 는 **부팅 시퀀스**를
바꾼다. 둘 다 JVM 에서 관측 불가능한 표면이다. 계획서는 3세션을 요구했으나 qa 검증 결과
**6항목으로 확장**한다 — 계획서의 "MENU 세션 1" 이 리사이저블 앱으로 수행되면 이 웨이브에서 가장 크게
재작성된 `DividerPopupRotator`(−115줄)가 **한 줄도 실행되지 않기 때문**이다.

준비:
```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
# ⚠ 앱 업데이트 시 접근성 서비스가 꺼진다 (CLAUDE.md 함정 #6) — 재활성화 필수
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService
adb logcat -c
adb logcat -s FWSplitEntry:V FWDividerRotator:V FWNodeActions:V FWPaneSwapper:V FWFloatingLauncher:V FWArranger:V
```

| # | 항목 | 확인 방법 / 유도 | 상태 |
|---|---|---|---|
| W7-1 | **P1 노드 선택 등가 (DRAG)** | 리사이저블 앱(유튜브) 버블 탭 → 배치 1회. `FWSplitEntry: step2 card-icon matched via selector [...]` 의 **셀렉터 이름이 W6 세션 로그와 동일**해야 한다(한국어 Fold 7 = `ko-content-desc` 기대). `structural-clickable-label` 로 바뀌면 **P1 회귀 확정** — 대형 카드 오매치로 Recents 세션이 파괴된다(2026-07-25 3차 실측 재발) | [미검증] |
| W7-2 | **P1 신규 상한 미발동** | W7-1·W7-3 세션 전체에서 `FWSplitEntry: … 노드 예산 4000 소진` 과 `FWNodeActions: walk: 깊이 상한 50 초과` 가 **단 한 줄도 나오지 않아야** 한다. 한 줄이라도 나오면 구코드에 없던 절단이 주 경로에서 발동한 것 → 상한을 올리고 **실측 트리 규모를 이 문서에 기록** | [미검증] |
| W7-3 | **M3 회전 클릭 (MENU · UNRESIZEABLE 앱 필수)** | 리사이저블 앱으로는 `DividerPopupRotator` 가 **호출조차 안 된다**. 넷플릭스류로 1세션. ① `menuStep2/3 split-menu matched via selector [ko-split-menu]` ② `FWDividerRotator: clickWhenFound: [rotateOnce rotate-node] clicked-self (text=…/desc=…)` — **`clicked` 가 아니라 `clicked-self`/`clicked-ancestor` + 괄호 라벨**이어야 통합본이 실행된 것 ③ 회전 후 상하 분할 성립 | [미검증] |
| W7-4 | **PaneSwapper 탭 duration 해소** | 분할 성립 후 페인 스왑 1회. `FWPaneSwapper: swap:` 이 W6 와 동일 형태로 나오고 스왑 성립. `TAP_DURATION_MS`(50L) 소유권이 `NodeActions` 로 옮겨간 뒤에도 런타임이 맞는지의 유일한 실동작 경로 | [미검증] |
| W7-5 | **P2 부팅 후 버블 위치 복원** | 버블을 기본 위치가 아닌 곳(좌하단 등)으로 옮겨 스냅 완료 → **재부팅**. ① 버블이 **기본 위치(우측 가장자리, 화면 높이 1/3)에 먼저 떴다가 저장 위치로 이동** — 이 한두 프레임 점프가 P2 의 **의도된** 체감 변화다. 점프가 안 보이면 복원이 아예 안 온 것일 수 있으니 ②로 구분 ② 최종 위치가 재부팅 전과 동일 ③ `applyCachedBubblePosition: 버블 위치 반영 실패` 미출현 ④ 부팅 직후 ANR·버벅임 없음 | [미검증] |
| W7-6 | **P2 경합 가드 (best-effort)** | 재부팅 후 버블이 뜨자마자(1초 내) 즉시 잡고 드래그. 드래그 중 버블이 저장 위치로 튀지 않아야 한다. `FWFloatingLauncher: restoreBubblePositionAsync: 복원 전 사용자가 버블을 이동 — 저장값 적용 생략` 이 나오면 가드 동작. **창이 매우 좁아 미재현이 정상 — 미재현은 무결의 증거가 아니다** | [미검증] |

**W7-2 · W7-4 는 계획서 원문(DRAG 1 + MENU 1 + 부팅 1)에 없던 항목**이고, W7-3 의 「UNRESIZEABLE 앱 필수」
제약도 계획서에 없다. 전부 qa 검증자 권고로 추가했다.

**[확정] 새로 도입된 상한 2개는 실측값이 아니다:** `MAX_TREE_DEPTH = 50`, `MAX_NODES_VISITED_TREE = 4000`
(`platform/NodeActions.kt`). 구 순회에는 상한이 **아예 없었고**, Fold 7 Recents 런처 트리의 실제 깊이·노드 수는
측정된 바 없다. 초과 시 조용히 다른 노드가 선택되거나 미발견된다 — 그래서 두 경우 모두 `Log.w` 를 남기고
**W7-2 가 그 로그의 부재를 확인**한다. 참고로 `PaneSwapper.MAX_NODES_VISITED = 500` 은 **통합하지 않고 그대로 뒀다**
(팝업 탐색용 실측값을 런처 전체 트리에 확대하면 진입 경로가 죽는다 — CLAUDE.md 함정 #7).

**[확정] 로그 델타 2건 (실기기 대조 시 혼동 방지):** `DividerPopupRotator` 의
① `clickWhenFound: [$what] clicked` → `clicked-self`/`clicked-ancestor (text=…/desc=…)`
② `clickWhenFound: [$what] gesture-tap-fallback` → `gesture-tap-fallback (text=…/desc=…)`
둘 다 `SplitEntry` 판 통합의 결과이며 **정보 증가 방향, 동작 변화 0**이다.
`matched via selector [...]` / `transition:` / `arrange done:` / `arrange decision:` / `clickCycle:` /
`swap:` / `resize-mode detection:` / `verify:` 는 **문자 단위 무변경**(qa 가 `Log.*` 리터럴 68→69개 전수 대조).

**JVM 테스트 사각지대 [확정, qa 변조 실험 9종]:** `SplitEntry`/`DividerPopupRotator`/`PaneSwapper`/
`FloatingLauncherService`/`NodeActions`/`Polling` 을 인스턴스화하는 JVM 테스트가 **0개**다.
변조 9종 중 **4종만 잡혔고 그 4종은 전부 `BestMatchTracker`·`ScreenshotSampler` 라는 순수 함수 표면**이었다.
**무검출 5종:** ① `walk` 자식 순회를 역순으로 ② `firstMatch` 의 `tracker.accepts(i)` 게이팅 삭제
③ `MAX_NODES_VISITED_TREE` 를 **1** 로 ④ 버블 클램프를 `params.width` → `view.width`(= `addView` 직후 0 이라
`maxX` 가 화면 폭 전체가 되어 버블이 화면 밖으로 나갈 수 있다) ⑤ 경합 가드 무력화 — **전부 322개 통과**.
즉 **「테스트 322 통과」는 W7 의 안전 근거가 아니다**(W5-4·W6 에 이은 **세 번째 확정**). 위 표 6항목이 유일한 실효 검증이다.

