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
| #20 잔여 미발동 경로 (10차 Gate 통과 후) | 주 경로 전부 [측정] 해소 (위 10차 절). 잔여: 피커 cycle-2 a11y·스왑 cycle-1/2 제스처·팝업 재오픈 분기·involution 가드·budget-exhausted·오버레이 가드 발동·회전×2 폴백 | 자연 발생 대기 (mech 로그가 상시 계측) — 3시도 전멸 재발 시 FORENSIC viewId 로 원인 특정 후 스텝 되감기 재검토 |
| 가로(상하 분할) 디바이더 기하 | 세로값(14px/68×221) 대칭 가정 | 가로 분할 상태 dumpsys 실측 |
| One UI 정확 버전 | 설정값 비어 있음 | 다른 조회 경로 필요 |
| Recents 셀렉터 다국어 | 한국어만 | 영어 등 로케일에서 content-desc/text 확인 |
| wavve 등 국내 OTT 패키지명 | 미확인 | 대상 앱 실행 후 foreground 패키지 조회 |
| E 종횡비 역산 실측 | 미검출로 0건 | 순흑 플레이어 또는 Detector v2 로 재측정 |
| #12 §6 측정 캐싱 v1 (12차 구현) | JVM 204 테스트·qa PASS. 실기기 0회 | ① 클린 MEASURED 합치 세션 Done(verified=true) 후 `aspect cache save` 로그 + DataStore `measured_aspect.<pkg>` 키 확인 (P3-3 pb 디코딩 방식) ② 캐시 존재 상태에서 pre 실패/불합치 유도 재트리거 → `arrange decision` 로그 `cachedAspect=` 채움·`source=CACHED` 낙착·residual 수렴 ③ CACHED 세션이 confirm 미실행(자기 갱신 없음) 로그 확인 ④ `cacheMeasuredAspect=false` 레버 — 시드 임시 수정으로 종전 동작 회귀 확인 |

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
