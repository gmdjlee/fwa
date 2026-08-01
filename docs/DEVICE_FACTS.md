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
| 측정 방법 | Phase 0 프로브 3런 (런 ① 세로 비몰입 / 런 ② 분할 활성 / 런 ③ 가로 몰입 — 원본 덤프는 아래 「Phase 0 프로브 원본 측정」) + Advisor의 `adb dumpsys window` · `input swipe` · `screencap` 실측 |
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
| One UI 버전 | `one_ui_version` 설정값 비어 있음(조회 실패). One UI 8 로 추정 | 사양 | [추정] |

> 폐기: `density 2.0(추정) → 984×1092 dp`. 교정: 실측 2.25 / 875×971 dp.

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
| 6 | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 동작하는가 | ❌ **미지원 (FAILS)** | 3개 프로브 런 전부 `performGlobalAction` 반환값 **false** + Advisor adb 확인: 분할 진입 전과 분할 중 모두 false. One UI 8 미지원 판정 → Recents 폴백을 Phase 2 기본 전략으로 승격 |
| 7 | `TYPE_SPLIT_SCREEN_DIVIDER` 노출되는가 | ✅ **노출됨 (단, 분할 활성 중에만 / 핸들 영역만)** | 분할 활성 런(②)에서만 노출: bounds `950,981,1018,1202`. 1·3차 런의 "미노출"은 분할 비활성 상태 측정이라 무효. 노출 bounds는 디바이더 전체가 아니라 드래그 핸들 68×221px |

**분기 판정:** `dividerExposed=true` → 핸들 좌표로 확보(진입 이후에만 유효) · `splitAction=FAILS` → Recents 폴백을 P2-3 기본 전략으로 · `hasFreeformFeature=true` → P4-1(Shizuku 팝업) 유지.

---

## WindowGeometry 실측값

전부 분할 활성 런(②, 세로 좌우 분할)과 Advisor의 `dumpsys window` 실측 기준. px 단위.

| 항목 | 세로(좌우 분할) | 가로(상하 분할) | 상태 |
|---|---|---|---|
| usable 크기 | 1968 × 2184 | 2184 × 1968 | [측정] |
| density | 2.25 (360 dpi) | 2.25 (360 dpi) | [측정] |
| **dividerThickness (시각 간격)** | **14 px** | 14 px (대칭 가정) | 세로 [측정] / 가로 [미검증] |
| 드래그 핸들 크기 | 68 × 221 px | 221 × 68 px (회전, 대칭 가정) | 세로 [측정] / 가로 [미검증] |
| SurfaceFlinger 디바이더 창 폭 | 154 px | 154 px (대칭 가정) | 세로 [측정] / 가로 [미검증] |
| 최소 페인 (가시) | 181 px | 미측정 | 세로 [측정] / 가로 [미검증] |

### 디바이더 기하 상세 (세로 좌우 분할, [측정])

같은 "디바이더"가 세 가지 값으로 조회된다 — 혼동 주의:

| 무엇 | 값 | 용도 |
|---|---|---|
| 접근성 `TYPE_SPLIT_SCREEN_DIVIDER` 창 bounds | `950,981,1018,1202` = 68×221px 핸들, 중심 `(984, 1092)` | DividerLocator 드래그 기준점(핸들 중심) |
| SurfaceFlinger `StageCoordinatorSplitDivider` frame | `Rect(907,0-1061,2184)` = 154px 폭 × 전체 높이, 중심 X 984 | 물리 디바이더 서피스(접근성엔 안 보임) |
| 두 앱 페인 사이 시각 간격 | 좌 977 ↔ 우 991 = **14px** | `SplitPlanner.dividerThickness` |

좌 페인(ProbeActivity) frame `Rect(0,0-977,2184)`, 우 페인(YouTube) frame `Rect(991,0-1968,2184)`. **교정:** 분할 활성 런(②)이 제안한 `dividerThickness=221` 은 핸들의 세로 길이를 두께로 오독한 것 — 폐기하고 **14** 로 교체.

### 최소 페인 실측 (세로 좌우 분할, [측정])

`input swipe` 로 디바이더를 좌측 끝(목표 x=60)까지 드래그 → 최소 스냅 위치(좌 페인 가시 폭 **181px**, 디바이더 창 frame `Rect(111,0-265,2184)`)에서 정지. **함정:** 이때 앱 창은 리사이즈되지 않고 화면 밖으로 슬라이드된다(ProbeActivity frame `Rect(-1592,0-181,2184)`, 창 폭 ≈1773px 유지). 폐루프 보정(ADR-5)에서 창 bounds 를 그대로 종횡비 역산에 넣으면 음수 left 에 속으므로 **가시 교집합(화면 ∩ 창)** 으로 클램프한다. 가로(상하 분할) 최소값은 **미측정**(세로 181px 은 참고값).

---

## 분할 진입 전략 (#6 FAILS → 드래그 레시피 확정, 2026-07-25 실기기 E2E 검증)

`GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 미지원 + 메뉴 레시피 반증 → **P2-3 기본 = Recents 카드 드래그 레시피**.

### [반증] 구 메뉴 레시피 ("분할 화면으로 열기")

메뉴 탭 경로는 가로 화면에서 **좌우 분할**을 생성한다(실측). 레터박스 제거는 상하 분할이 필수이므로 이 경로는 폐기. "가로 = 상하 분할" 가정은 이 진입 경로에 한해 반증됨.

### [측정] 드래그 레시피 (3단계 — E2E 4회 성공: 상단 배치 2회 · 하단 배치 1회 · BBB 재검증 1회)

| 단계 | 동작 | 근거 / 셀렉터 |
|---|---|---|
| 1 | Recents 열기 (`GLOBAL_ACTION_RECENTS`) | 성공 조건: 카드 아이콘 노드 출현. `content-desc="고급 옵션, <앱이름>, 버튼"` |
| 2 | 카드 아이콘을 상단 가장자리로 홀드 드래그(홀드 500ms + 이동 600ms, 드롭 y≈150) | `input draganddrop 592 322 1092 150 800` 상당. 성공 조건: 대상 창 가시 높이 15~75% |
| 3 | 피커에서 파트너("FW Panel") 노드 탭 | launcher `FromRecentActivity`. LAUNCH_ADJACENT 는 분할 선택 상태를 파괴함(전체화면 강탈) — 최후 폴백 전용 |

### [측정] 디바이더 조작 (상하 분할, 가로)

초기 디바이더 중심 Y ≈ **984**(반반 분할). 단일 스와이프로 이동 가능 — `input swipe 1092 984 1092 1235 500` 으로 16:9 완전 배치 달성(`DividerDragger` 기본 = `SINGLE_STROKE`). 핸들 탭 → 팝업 메뉴 노드(content-desc, [측정]): **"App pair 추가 위치" / "창 전환" / "시계 방향으로 회전"**(상하 전환은 "창 전환" — `PaneSwapper` 전략 1 로 실증). 페인 창 핸들 desc = `"<앱이름> 창 핸들"`, 기타 `"최소화된 플레이어"`.

### [측정] 접근성 제스처 API 함정 (GestureDrags 구현 근거)

1. `willContinue=true` 스트로크의 `onCompleted` 는 "주입 큐 수락" 의미로 ~4ms 만에 도착 — 완료 아님.
2. 제로 길이 경로(moveTo만/같은 점 lineTo)의 continueStroke 는 +6ms 취소됨 — 홀드 스트로크는 1px 드리프트 경로 필수.
3. 원 스트로크와 continueStroke 를 한 GestureDescription 에 넣으면 8ms 가짜 완료 — 반드시 두 번의 `dispatchGesture` 로 분리.
4. 롱프레스 인식엔 실제 홀드 시간 확보 필요 — 완료 콜백 직후 이동을 잇지 말고 holdMs 만큼 타이밍 가드.

### [측정] 개발 환경 함정

- **삼성 Freecess**: 포그라운드 서비스 없는 앱 프로세스를 백그라운드 동결 → 브로드캐스트 미달. 우회: `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow` + deviceidle whitelist.
- **암시적 브로드캐스트(API 26+)**: `am broadcast -a <action>` 만으로는 매니페스트 리시버에 미배달(`result=0` 인데 무실행) — 반드시 `-n <pkg>/<receiver>` 명시.
- **파트너 액티비티 잔존 태스크**: singleTask/잔존 태스크가 피커 탭 시 전체화면으로 재사용돼 분할 파괴 → `PanelActivity` 는 launchMode 기본 + "멀티윈도우 아님 감지 시 `finishAndRemoveTask`" 자가 가드.

### [측정] 비리사이저블 앱 분기 (넷플릭스, 2026-07-25)

넷플릭스 = `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE` 선언(기기 `force_resizable_activities=1` 로 분할 자체는 가능). 드래그 레시피는 **팝업(프리폼)으로 라우팅**된다 — One UI 가 선언 플래그를 보고 팝업으로 보냄. **우회 실측 성공**: Recents 카드 메뉴 "분할 화면으로 열기" → 좌우 분할 선택 → 피커에서 파트너 탭 → 디바이더 핸들 탭 → 팝업 "시계 방향으로 회전" → **상하 분할로 전환**. 팝업 버튼 시각 배치(좌→우): 회전 / 창 전환(⇆) / App pair(☆). **함정**: 분할 상태에서 재생을 먼저 시작하면 재생 화면이 팝업/전체화면으로 이탈(재현 조건 [미검증], 순서가 변수 — 아래 MENU 절 참조). step2/3 성공 조건 허점: 팝업(프리폼) 창도 "가시 높이 15~75%" 를 통과(오탐) — 전폭(≥90%)·상단 도킹 조건 보강 필요.

### [측정] MENU 레시피 자동화 실기기 검증 (2026-07-25 오후, E2E 6회)

MENU 5단계 자동화 성공 — 6회 중 5회 1차 시도 통과, 트리거→완료 ~4.7초. 실패 1회는 잔존 태스크 함정(아래). **육안 확인**: 상단 페인 16:9 영상 검은 띠 0, 하단 파트너 정상 — 넷플릭스 DoD ① 성립.

- **UNRESIZEABLE 감지 확정**: `privateFlags` 필드 리플렉션 = allowed. 상수 `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE` = denied(NoSuchFieldException) → 폴백 비트 **1<<11 = 0x800**(실측 `privateFlags=0x8c000910` 비트 분해로 dumpsys 명칭 교차 검증. 최초 가정 1<<12 는 오답).
- **회전 결과 페인 위치는 비결정** — 같은 절차로 상단 3회/하단 2회, 원인 미상.
- **"창 전환" ACTION_CLICK 무효 2회 실측**(회전 직후 컨텍스트, result=true 인데 3초 대기에도 실배치 불변) — 대응: PaneSwapper 탭 재시도(3회) + 회전×2 폴백(`DividerPopupRotator`) 구현(이후 런에서 미발동, [미검증]).
- **재생 중 메뉴 진입 → 재생 세션이 "최소화된 플레이어" 팝업으로 분리**(3회+ 재현). 반대로 분할 페인 안에서 재생 시작 → 분할 유지 — **순서가 결정 변수**. v1 넷플릭스 사용법 = 원터치 배치 → 페인에서 재생.
- **잔존 패널 태스크 함정**: 프로세스 강제 종료로 자가 가드 미실행 시 죽은 FW Panel 카드가 셀렉터를 오탭 → 자가 가드 즉시 종료 → 3회 소진 `ENTRY_STEP_FAILED`. 해소: `purgeStalePanelTasks()` 세션 시작 시 청소(1개 제거·1차 성공 실측).
- **측정 오염 2종**: ① pre-measure 전체 화면 스캔이 분할/홈 UI 를 띠로 오인(aspect 1.14 conf 0.91 / 2.95 conf 0.97 고신뢰 오측) → 페인 크롭 + 넷플릭스 PROFILE 1.7778 고정으로 수정. ② 드래그 직후 재측정이 컨트롤 오버레이 오염(residual 122~224) → PROFILE 소스는 ADR-5 보정 생략(`defaults.closedLoopCorrection` 토글 배선).

### [측정] DRAG 레시피 유튜브 회귀 (2026-07-25 오후 2차, E2E 2회)

1차 실행 = `ENTRY_STEP_FAILED`(step2)였으나 드래그는 물리적으로 성공해 있었다. 설계 버그 2종: ① 유령 매치 즉시 실패(bounds 조회 불가 노드 매치 → 시도 수 ms 소진) ② 성공 미인지 재시도(잔여 폴링 예산 ~370ms 안에 정착 미완료 → 실패 판정, 다음 시도는 사라진 카드 재탐색). **수정**(`SplitEntry.kt`): step2 폴링이 매 주기 목표 상태 도달을 먼저 확인 + bounds 빈 매치는 재폴링 + 동일 패턴을 step3·menuStep3~5 에도 적용(타임아웃/기하 상수 무변경). **2차 실행 = 통과**: 트리거→Done 4.2초, `verified=true residual=0px`. **분할-선택 상단 페인 ground truth**(정착 후): 대상 frame `[0,0][2184,977]`(전폭 100%·상단 도킹 0px), 피커 `[0,991][2184,1968]`, 간격 14px → `isSplitSelectTopPane` 임계(전폭≥90%, `EDGE_DOCK_TOLERANCE_PX=40`) **[검증]**. **Detector v2 ADAPTIVE 첫 실증**: 앰비언트 글로우 띠에서 pre-measure 가 1.7778 정확 측정(conf 0.57~0.60, 2회). MEASURED 소스 폐루프 무오염 성공(verify residual=0px). structural 셀렉터 함정 추가: 유효 bounds 의 대형 오매치(카드 본체 중심 1092,833 오인) → 크기 가드(bounds ≤ 화면폭/10 ≈218px, 실측 아이콘 ~90px vs 카드 수백px).

### [측정] 버블 오버레이 × 분할 피커 상호작용 (2026-07-25, Phase 3 P3-1)

오버레이 창(버블)이 떠 있는 동안 분할 피커에서 파트너를 탭하면 `PanelActivity` 가 분할 페인이 아니라 **전체화면으로 launch** 된다. A/B 실측: 버블 ON = 실패 2회 / 버블 OFF 동일 빌드 = 즉시 성공(160ms 수렴). 메커니즘 불명(One UI WM 라우팅 추정). 버블 창은 접근성 창 목록에 **TYPE_SYSTEM**(wm type 2038)으로 보고됨 — `TYPE_APPLICATION` 필터 기반 기하 판정은 오염하지 않는다. **대응(검증 완료)**: `beginSession` 시 `setBubbleHiddenForArrange(true)` 로 버블 창 removeView, `cleanupSession` 에서 복원(적용 후 E2E 4.1초 통과). **파생 함정**: 피커 셀렉터 후보에 앱 서랍 노출 라벨("FoldWindow"=OnboardingActivity)이 있으면 재시도가 온보딩을 오클릭(실측 1회) → `PANEL_LABEL_CANDIDATES` 는 "FW Panel" 단독. 셀렉터 문자열은 한국어 로케일 실측값(다국어 [미검증]).

### [측정] P3-2 확장 메뉴·분할 해제 실기기 검증 (2026-07-25 오후 4차)

1. **분할 해제는 디바이더 드래그로 불가** — `dispatchGesture` SINGLE_STROKE 는 스냅백(2/2), 동일 기하·시간의 `adb input swipe` 는 3/3 해제 성공(One UI 가 dismiss 깊이의 디바이더 드래그만 접근성 주입 거부, 경험 법칙).
2. **패널 finish → 분할 해소**: BACK 으로 finish 시 분할 즉시 해소 + 상대 앱 전체화면 복귀. `dismissSplit v2` = `PanelActivity.instance.finishAndRemoveTask()` + `isSplitActive` 폴링, E2E 성공. 인텐트 폴백(instance null)은 [미검증].
3. **ACTION_OUTSIDE 디스패치 순서**: 메뉴 창의 ACTION_OUTSIDE 가 버블 창 ACTION_DOWN 보다 먼저 디스패치(재탭 시 오발화/재열림). 대응: 풀스크린 투명 스크림으로 재구성 — 재탭=닫기만 E2E 확인.
4. **풀스크린 터치 가능 오버레이 = a11y 창 목록 가림-제외**: 스크림이 떠 있는 동안 하위 창이 `getWindows()` 에서 제외되고, 재구축은 비원자적(APPLICATION 먼저, DIVIDER 나중) — "APPLICATION≥1" 게이트 통과 직후에도 `isSplitActive` false-negative 2/2. 대응: dismiss 진입 체크를 `isSplitActive` 자체의 2s 조건 폴링으로 교체 후 E2E 통과.
5. **step3 피커 탭 변동성(#20 확장)**: 메뉴發 배치 4회 중 2회 step3 3연속 실패(직후 재시도 성공). 실패 시 클릭 무효(2회, 액티비티 생성 이벤트 없음) 또는 `startActivityFromRecents` 오라우팅(1회), 성공 시 `startActivityAsUser:com.sec.android.app.launcher`.
6. `adb input swipe x y x y 700` 롱프레스 시뮬레이션은 경계(1/3 발화) — **1200ms 권장**. 세로 방향도 파이프라인 정상(verified=true, residual=0).
7. **실부팅 복귀**: `adb reboot` 후 BOOT_COMPLETED 수신·specialUse FGS 자동 기동·접근성 유지·버블 가시 전부 확인. 분할 없는 상태의 해제 시도는 2.0s 폴링 후 "분할 화면이 아닙니다" 토스트.

### [측정] P3-3 DataStore 이관·placement 복원 실기기 검증 (2026-07-25 오후 7차)

1. **SharedPreferencesMigration 무손실**: 구버전 `bubble_prefs.xml`(enabled=true/x=1500/y=300) 주입 → 업데이트 설치 → 3키 무손실 이관 + 원본 XML 삭제 확인.
2. **goAsync 부팅 복귀**: `adb reboot` 실측 — 버블 자동 복귀 로그·FGS 자동 기동·접근성 유지·홈 버블 가시. 5차(구 동기 코드)와 동일 결과.
3. **마지막 성공 placement 저장→복원 E2E**: OVERRIDE bottom 배치 성공(5.1s, residual=0) → pb 기록 확인 → 무override 트리거 3회 전부 `placementSource=LAST_SUCCESS placement=BOTTOM`, 3회차 `residual=0` 완결.
4. **corruptionHandler**: `fwa_store.preferences_pb` 가비지 주입 → 버블 시작 탭 → CorruptionException 감지 로그 → emptyPreferences 재시작 → FGS 정상 기동(무크래시), enabled=true 재기록.
5. **온보딩 중지 취소 레이스(근사)**: 탭+즉시 BACK — enabled=false 쓰기 완료 + stopService 완주 + 무크래시 + 여타 키 보존.
6. **운영 함정**: ① adb 배치 트리거는 `-n dev.dj.foldwindow/.service.ArrangeTriggerReceiver` 컴포넌트 지정 필수(액션만은 implicit broadcast 제한으로 미수신) ② `am force-stop` 후 접근성 서비스 재바인드 안 됨(settings put 재설정 필요) ③ 온보딩의 `accessibilityGranted` 는 onResume 스냅샷이라 백그라운드 재바인드 즉시 미반영.

### [측정] #20 클릭-사이클 에스컬레이션 실기기 검증 (2026-07-25 저녁 10차 — Gate 1~3 통과)

빌드 = `9985b99`. 총 15 arrange 세션, **15/15 done, `ENTRY_STEP_FAILED` 0건**. LAUNCH_ADJACENT 삭제 후 회귀 없음.

| Gate | 구성 | 결과 |
|---|---|---|
| 1 회귀 | 유튜브 DRAG(OVERRIDE top) ×3 | 3/3 residual=0, 피커 cycle-0 gesture 327~362ms |
| 1 회귀 | 넷플릭스 MENU(top) ×3 | 3/3 전 단계 1차 통과, rotateOnce 1회 TOP 착지, residual=122(보고 전용) |
| 2① 독 컨텍스트 | 유튜브 무override 연속 ×5 | 실패 0(과거 ~50%→우연 확률 ~3%). 4회 cycle-0(176~333ms), 1회 cycle-1 회복(1157ms) |
| 2② 회전 여파 스왑 | 넷플릭스 bottom ×4 | 스왑 4/4 수렴, settleGate 153~154ms, mech=a11y, 800ms 검증 슬라이스 내 수렴 |

**핵심 실증**: ① 사이클 회복 실작동 — FORENSIC `TYPE_VIEW_CLICKED` 가 오착지(`icon_container`)를 특정 → cycle-1 재탭 수렴(무효 클릭="다른 뷰 착지" 클래스, "실행 자체 없음" 아님. 성공 착지점은 `FrameLayout viewId=null`, menuStep2 카드는 `task_icon`). ② 회전 여파 독 컨텍스트가 정착 게이트+검증 슬라이스로 4/4 수렴. 회전 착지 이 세션 TOP 7/7(누적 TOP 10/BOTTOM 2). 유튜브 MEASURED 경로 conf 0.53~0.59 로 1.7778 정확 측정 8/8.

**미발동 [미검증]**: 피커 cycle-2 a11y 폴백 · 스왑 cycle-1/2 제스처 · 팝업 소멸→재탭 분기 · involution 가드 · budget-exhausted tail · 오버레이 가드 발동 · 회전×2 폴백.

### [측정] #12 측정 합치 게이트 실기기 검증 (2026-07-25 밤 11차 — G1~G5 통과)

빌드 = `2474ff3` + 현장 수정 2건(아래). 총 9 arrange 세션 9/9 done. 트리거 = broadcast(OVERRIDE top), 유튜브 BBB 앰비언트.

| Gate | 시나리오 | 결과 |
|---|---|---|
| G1 사고 재현 | 추천 엔드스크린에서 트리거 | pre = conf 0.70 쓰레기(PURE_BLACK 1.197) → confirm 양축 띠 → **BOTH_AXES_BARS** → PRESET 1236 |
| G2 컨트롤 오염 | 플레이어 탭 직후 트리거 | pre band 370/160 비대칭(스크럽 바 잠식) → raw 1.519 → snap 1.5 conf 0.566(사고 클래스 동형) → confirm NoBars → 페인 AR 2.23 vs 1.5 = 48% 괴리 → **NO_BARS_INCONSISTENT** → PRESET → residual=0 |
| G3 클린 회귀 ×3 | 전체화면 재생 중 | **3/3 SNAP_AGREE → MEASURED**, residual=0, 4.2~4.3s |
| G4 앰비언트 | G1~G3 전체 | ADAPTIVE pre conf 0.51~0.59 ×5 전부 게이트 통과, 클린 3회는 합치 채택 |
| G5 오버라이드 | `--ef aspect 2.3704` | pre 생략·confirm/consensus 로그 0건·`aspectSource=PRESET aspectOverride=2.3704`·divider **928**(21:9 정확) — tier 0 완전 작동 |

**confirm 크롭 오염원 2종 → 현장 수정(픽셀 물증, 도메인 상수는 무변경)**: ① 최외곽 열 오염(라운드 코너 배경 누출 + 엣지 렌더링) — `toPillarboxScan` **sideMarginPct 0.005** 로 수정. ② 플레이어 크롬 오염(축소 아이콘·타이틀 그라디언트가 BOTH_AXES_BARS 오판정) — **edgeMarginPct 0.05→0.12** + `classifyAxis` **minConfidence 0.25** 로 수정(수정 후 cols band 214/214 완벽 대칭 3/3 재현).

**부수**: ADAPTIVE_MAX_VARIANCE(400)·MAX_BAR_LUMA(90) 등 도메인 상수 무변경(오염은 입력 문제, 함정 #7 준수). **글로우 필러박스에서 residualCols 순흑 블라인드**(G5: 16:9-in-21:9 페인에서 verify residualCols=0 — 열린 질문 #13 v1.5 근거). BOTH_AXES_BARS 세션의 ADR-5 보정이 비영상 콘텐츠를 쫓음(G1: verify residual 118 → drift 1회).

**[미검증] 잔여**: 비-16:9 콘텐츠(영화 2.35 등) 합치 채택 실측 0건 · `requireMeasurementAgreement=false` 롤백 레버 미실사용 · confirm 레이트리밋 백오프 분기 · 메뉴 프리셋 UI發 tier 0.

### [측정] #12 §6 측정 캐싱 v1 실기기 검증 (2026-07-26 오전 13차 — 4항목 전부 통과)

빌드 = `2e5028b`(레버 세션만 시드 JSON `cacheMeasuredAspect:false` 임시 빌드, 원복 완료). 총 10 arrange 세션 10/10 done. 유튜브만 사용(BBB 앰비언트 + MPD직캠 세로 영상).

| # | 항목 | 시나리오 | 물증 |
|---|---|---|---|
| ① | 합치 세션 → 저장 | 클린 BBB 전체화면 | SNAP_AGREE→MEASURED, done residual=0 → `aspect cache save: pkg=com.google.android.youtube aspect=1.7777778` + pb 실물(float LE `39 8e e3 3f`=1.7777778) |
| ② | 불합치 폴백 CACHED 낙착 | 일시정지+컨트롤/홈 피드/Shorts 3연 | 3세션 전부 `cachedAspect=1.7777778` → confirm 불합치 → `aspect=1.7777778 source=CACHED`. residual 0/46/38 |
| ③ | CACHED 세션 confirm 미실행 | 세로 직캠 immersive 전체화면 | pre **null**(`밴드 없음`) → `aspectSource=CACHED preMeasure=none`(divider 1236 즉시) → confirm/consensus/cache save 로그 **0건**. done verified=true residual=0 |
| ④ | 레버 false 회귀 | 시드 `cacheMeasuredAspect:false` 로 ③·② 동형 3세션 | read: `cachedAspect=none`·`aspectSource=PRESET` / write: SNAP_AGREE+verified=true 인데 save 로그 0건 + pb 불변(기존 캐시 보존, 레버=read/write 차단만) |

원복 세션: `cachedAspect=1.7777778` 재등장 + confirm 이 구름 장면을 4:3 오측(ADAPTIVE 1.333, 사고 클래스 재현) → RAW_DISAGREE → CACHED 폴백 → residual=0(과거 오염 고착 사고가 캐시로 무해화).

**운영 함정**: pre-null 유도 조건 = "필러박스 전용 콘텐츠의 immersive 전체화면"(비-immersive 다크 UI 는 순흑 행이 항상 후보를 만들어 pre 가 null 이 안 됨). **Shorts 진입은 포트레이트 강제 + `user_rotation` 잠금 무시** — 복귀 후 `settings put system user_rotation` 만으론 WM 미재평가, **`adb shell cmd window user-rotation lock 1`** 이 즉시 적용(`free` 로 해제).

---

## 검은 띠 실측 (프로브 E) — 실패 + 근본 원인

세 런 모두 검은 띠 미검출(스냅 안 됨). 근본 원인은 검출기 버그가 아니라 입력 가정 위배.

### 근본 원인: YouTube 앰비언트 모드 ([측정])

가로 전체화면 16:9(Big Buck Bunny)에서 상하 띠가 순흑이 아니라 영상 색이 번진 어두운 글로우("앰비언트 모드")로 채워짐. `screencap` 실측: 상단 띠 darkRatio(luma≤24) = **0.000**, 하단 최대 **0.66**. `LetterboxDetector` 순흑 임계 **0.97** 도달 불가 → 미검출. 검출기 코드 자체는 정상 — 입력 가정(띠=순흑)이 YouTube 기본 설정에서 깨진 것. 넷플릭스 등 대부분 플레이어는 순흑 띠 유지 — 순흑 경로는 유지한다.

### E 재검증 조건

① 순흑 띠 플레이어(넷플릭스/티빙 등) 또는 앰비언트 모드 OFF 재측정, 또는 ② LetterboxDetector v2(하이브리드) 구현 후 앰비언트 영상 재측정.

---

## 참고 계산표 (2184 × 1968 px 가로, 인셋/디바이더 0 가정 — 유지)

`SplitPlannerTest` 기대값과 일치해야 한다. 실배치 시에는 dividerThickness=14 + 시스템바 인셋을 usableHeight 에서 차감한 뒤 계산할 것.

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

## Phase 0 프로브 원본 측정 (2026-07-25, 3런 — 원본 덤프 흡수)

> 이 절은 Phase 0 프로브가 기기에서 뽑아낸 원본 덤프 3개를 **가공 없이** 옮긴 것이다.
> `FullscreenWindowJudge` 의 상수와 `FullscreenWindowJudgeTest` 14~18번의 앵커 좌표가 전부
> 여기서 나온다 — 임의로 고치지 마라 (함정 #7). 세 런은 서로 **다른 화면 상태**에서 찍혔다:
> ① 세로 비몰입 ② 분할 활성 ③ 가로 몰입. 판정 술어의 3표본이 곧 이 셋이다.
> 원본 덤프 파일은 이 절로 흡수한 뒤 삭제했다(이 파일이 기기 측정값의 SSOT).
> 재실행 방법은 아래 「프로브 재실행 절차」.

### 공통 — A. 기기 (3런 전부 동일)

| 항목 | 값 |
|---|---|
| 제조사/모델 | samsung SM-F966N |
| Android | 16 (API 36) |
| One UI | - |
| FEATURE_FREEFORM_WINDOW_MANAGEMENT | true |
| FEATURE_PICTURE_IN_PICTURE | true |
| enable_freeform_support | - |
| enable_non_resizable_multi_window | 0 |
| force_resizable_activities | 1 |

3런 전부 C절(분할 진입) 판정이 **FAILS** 이고, E절(검은 띠)은 미검출 — 상세는 각 런에.

---

### 런 ① 세로 비몰입(엣지투엣지)

- 생성: **2026-07-25 00:08:57**
- 측정 시점 포그라운드 앱: `com.android.settings`
- 미지수 해소: #5 ✅ freeform 지원 / #6 FAILS / #7 ❌ 미노출 *(분할 비활성 상태 측정이라 무효 — 런 ② 참조)*

**B. 창 (7개)**

| type | layer | bounds | package | active |
|---|---|---|---|---|
| UNKNOWN(-1) | 6 | 1917,1134,1968,1646 | com.samsung.android.sidegesturepad | false |
| UNKNOWN(-1) | 5 | 0,1208,51,1701 | com.samsung.android.sidegesturepad | false |
| SYSTEM | 4 | 0,2150,1968,2184 | com.sec.android.app.launcher | false |
| UNKNOWN(-1) | 3 | 1230,2104,1968,2184 | com.sec.android.app.launcher | false |
| SYSTEM | 2 | 1915,235,1968,564 | com.sec.android.app.launcher | false |
| SYSTEM | 1 | 0,0,1968,89 | com.android.systemui | false |
| APPLICATION | 0 | 0,0,1968,2184 | dev.dj.foldwindow | true |

디바이더 bounds: `(없음)`

**C. 분할 진입** — 호출 전 디바이더 존재 `false` · `performGlobalAction` 반환값 `false` ·
디바이더 상태 변화 감지 `false (3003ms)` · **판정: FAILS — Recents 폴백 전략으로 전환 필요**

**D. 메트릭**

| 항목 | 값 |
|---|---|
| 해상도 | 1968 × 2184 px |
| density | 2.25 (360 dpi) |
| dp 크기 | 875 × 971 dp |
| smallestScreenWidthDp | 875 |
| 방향 | PORTRAIT |
| rootWindowBounds | 0,0,1968,2184 |

**E. 검은 띠** — 프레임 `984 × 1092 px`. 상단 띠 / 하단 띠 / 콘텐츠 높이 / 역산 종횡비 / 신뢰도 전부 `-`,
스냅 결과 `(스냅 안 됨)`. 비고: "검은 띠를 찾지 못함. 영상을 가로 전체화면으로 재생한 뒤 다시 실행할 것".

**SplitPlanner 반영 제안값(원본 그대로, 당시)** — `usableLeft=0`, `usableTop=0`,
`usableWidth=1968`, `usableHeight=2184`, `dividerThickness=TODO`, `minPaneHeight=0`.

---

### 런 ② 분할 활성(세로 좌우)

- 생성: **2026-07-25 00:14:50**
- 측정 시점 포그라운드 앱: `(없음)`
- 미지수 해소: #5 ✅ freeform 지원 / #6 FAILS / #7 ✅ **노출됨**

**B. 창 (7개)**

| type | layer | bounds | package | active |
|---|---|---|---|---|
| UNKNOWN(-1) | 6 | 1917,1134,1968,1646 | com.samsung.android.sidegesturepad | false |
| UNKNOWN(-1) | 5 | 0,1208,51,1701 | com.samsung.android.sidegesturepad | false |
| SYSTEM | 4 | 0,2150,1968,2184 | com.sec.android.app.launcher | false |
| SPLIT_SCREEN_DIVIDER | 3 | 950,981,1018,1202 | com.android.systemui | false |
| APPLICATION | 2 | 991,0,1968,2184 | com.google.android.youtube | true |
| APPLICATION | 1 | 381,89,595,145 | com.android.systemui | false |
| APPLICATION | 0 | 0,0,977,2184 | dev.dj.foldwindow | false |

디바이더 bounds: `950,981,1018,1202`

> `381,89,595,145`(com.android.systemui 소형 창)의 type 열이 **APPLICATION** 이라는 점에 주의 —
> 판정 술어에서 이 창이 어느 절에 걸리는지가 달라진다(위 「#30 …」절의 정정 참조).

**C. 분할 진입** — 호출 전 디바이더 존재 `true` · `performGlobalAction` 반환값 `false` ·
디바이더 상태 변화 감지 `false (3001ms)` · **판정: FAILS — Recents 폴백 전략으로 전환 필요**

**D. 메트릭**

| 항목 | 값 |
|---|---|
| 해상도 | 1968 × 2184 px |
| density | 2.25 (360 dpi) |
| dp 크기 | 875 × 971 dp |
| smallestScreenWidthDp | 875 |
| 방향 | PORTRAIT |
| rootWindowBounds | 0,0,977,2184 |

**E. 검은 띠** — 프레임 `984 × 1092 px`, 나머지 전부 `-`, 스냅 결과 `(스냅 안 됨)`. 비고 동일.

**SplitPlanner 반영 제안값(원본 그대로, 당시)** — `usableLeft=0`, `usableTop=0`,
`usableWidth=1968`, `usableHeight=2184`, `dividerThickness=221`, `minPaneHeight=0`.
※ 이 `dividerThickness=221` 은 **폐기값**이다(핸들 세로 길이를 두께로 오독) — 실효값 **14px**,
위 「디바이더 기하 상세」 참조.

---

### 런 ③ 가로 몰입 재생(유튜브)

- 생성: **2026-07-25 00:23:39**
- 측정 시점 포그라운드 앱: `(없음)`
- 미지수 해소: #5 ✅ freeform 지원 / #6 FAILS / #7 ❌ 미노출 *(분할 비활성 상태 측정이라 무효)*

**B. 창 (3개)**

| type | layer | bounds | package | active |
|---|---|---|---|---|
| UNKNOWN(-1) | 2 | 2117,507,2184,1530 | com.samsung.android.sidegesturepad | false |
| UNKNOWN(-1) | 1 | 0,460,67,1530 | com.samsung.android.sidegesturepad | false |
| APPLICATION | 0 | 0,0,2184,1968 | com.google.android.youtube | true |

디바이더 bounds: `(없음)`

**C. 분할 진입** — 호출 전 디바이더 존재 `false` · `performGlobalAction` 반환값 `false` ·
디바이더 상태 변화 감지 `false (3002ms)` · **판정: FAILS — Recents 폴백 전략으로 전환 필요**

**D. 메트릭**

| 항목 | 값 |
|---|---|
| 해상도 | 2184 × 1968 px |
| density | 2.25 (360 dpi) |
| dp 크기 | 971 × 875 dp |
| smallestScreenWidthDp | 875 |
| 방향 | LANDSCAPE |
| rootWindowBounds | 0,0,2184,1968 |

**E. 검은 띠** — 프레임 `1092 × 984 px`, 나머지 전부 `-`, 스냅 결과 `(스냅 안 됨)`. 비고 동일.

**SplitPlanner 반영 제안값(원본 그대로, 당시)** — `usableLeft=0`, `usableTop=0`,
`usableWidth=2184`, `usableHeight=1968`, `dividerThickness=TODO`, `minPaneHeight=0`.

---

## 프로브 재실행 절차

- **트리거**: `ProbeTriggerReceiver`(액션 `dev.dj.foldwindow.probe.RUN_PROBE`, exported).
  ```bash
  adb shell am broadcast -a dev.dj.foldwindow.probe.RUN_PROBE
  adb pull /sdcard/Android/data/dev.dj.foldwindow/files/probe_report.md ./docs/
  ```
- **함정 #6**: 재설치(`installDebug`)하면 접근성 서비스가 꺼진다 — 재실행 전 재활성화:
  ```bash
  adb shell settings put secure enabled_accessibility_services \
    dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService
  ```
- 분할/앰비언트 관련 측정은 상태 의존이다: #7은 분할 활성 중에, E는 순흑 띠 조건에서 찍어야 유효.

---

## 남은 미검증 항목 [미검증]

| 항목 | 현재 상태 | 확정 방법 |
|---|---|---|
| 가로(상하 분할) 최소 페인 높이 | 세로 좌우 181px만 측정 | 가로 분할에서 디바이더를 끝까지 드래그해 실측 |
| #20 잔여 미발동 경로 | 주 경로 전부 해소(10차). 잔여: 피커 cycle-2 a11y·스왑 cycle-1/2 제스처·팝업 재오픈 분기·involution 가드·budget-exhausted·오버레이 가드 발동·회전×2 폴백 | 자연 발생 대기(mech 로그 상시 계측) — 3시도 전멸 재발 시 FORENSIC viewId 로 원인 특정 |
| 가로(상하 분할) 디바이더 기하 | 세로값(14px/68×221) 대칭 가정 | 가로 분할 상태 dumpsys 실측 |
| One UI 정확 버전 | 설정값 비어 있음 | 다른 조회 경로 필요 |
| Recents 셀렉터 다국어 | 한국어만 | 영어 등 로케일에서 content-desc/text 확인 |
| wavve 등 국내 OTT 패키지명 | 미확인 | 대상 앱 실행 후 foreground 패키지 조회 |
| E 종횡비 역산 실측 | 미검출로 0건 | 순흑 플레이어 또는 Detector v2 로 재측정 |
| #12 §6 측정 캐싱 v1 | **13차 4항목 전부 해소.** 잔여: 캐시 값이 1.7778 이외인 앱(비-16:9 콘텐츠 캐시) 실측 | — |

---

## Phase 1·2 권고

### P1-1 — SplitPlanner 기본값 반영 — ✅ 2026-07-25 반영 완료 (`foldSevenLandscape()`)

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

`dividerThickness=14` 는 세로 실측값의 가로 전용([미검증] 유지). `minPaneHeight=181` 은 16:9(1229px)·파트너(739px) 모두 초과라 정상 종횡비에서 클램프를 유발하지 않는다(초광각 >≈12:1 에서만 작동). `usableTop`(시스템바 인셋)은 아직 실측 없음(0 유지).

### P2-2 — DividerLocator

`TYPE_SPLIT_SCREEN_DIVIDER` 조회를 1차 경로로(분할 활성 이후에만 창 존재 — 진입 완료를 조건 폴링). 핸들 중심(세로 예: `(984,1092)`)을 드래그 기준점으로. 휴리스틱 폴백은 후순위(화면 중앙 가로 밴드에서 APPLICATION 페인 경계를 비율로 추정).

### P2-3 — SplitEntry (기본 = Recents 폴백)

`performGlobalAction(TOGGLE_SPLIT_SCREEN)` 제거 또는 즉시 실패 처리. 기본 경로 = Recents 레시피(위 3단계). 상태 머신(ADR-2): 각 단계는 다음 노드 출현을 조건 폴링으로 확인(`postDelayed` 금지). 셀렉터는 한국어 문자열 + 패키지/역할 폴백 병행.

### P2-4 — DividerDragger

`dispatchGesture` 로 핸들 중심 → `plan.dividerCenterY` 이동. **함정**: 최소 스냅에 걸리면 창이 오프스크린으로 슬라이드 — 목표가 `minPaneHeight` 아래로 내려가지 않게 클램프(SplitPlanner 처리). 폐루프 재측정 시 창 bounds 는 화면과의 가시 교집합으로 해석.

### LetterboxDetector v2 설계 분기 (E 재검증 근거)

| 방안 | 아이디어 | 장점 | 약점 |
|---|---|---|---|
| (a) 행 균일도/저디테일 | 띠 후보 행의 색 분산·엣지 밀도가 낮음을 검출 | 앰비언트 글로우도 잡음 | 저디테일 콘텐츠 오검출 |
| (b) 모서리 색 적응 임계 | 네 모서리 색을 배경 기준으로 임계 동적화 | 비순흑 배경 대응 | 배경 그라디언트에 약함 |
| (c) 하이브리드 | 순흑(0.97) 우선 → 실패 시 (a)/(b) | 넷플릭스 등 안전 + 폴백 | 복잡도 |

**채택 = (c) 하이브리드.** 순흑 경로는 그대로, 미검출 시에만 (a)+(b) 폴백. 입력은 행별 통계(밝기/분산)로 추상화해 `domain/` 순수성 유지.

---

## P3-5 FoldingFeature 실기기 검증 (2026-07-27~28 15차 — 5항목 전부 통과)

검증 기기 = SM-F966N / One UI 8 / API 36. 항목 1·3 은 수정 전 빌드에서 실패가 실측됐고 그 물증이 설계를 바꿨다(항목 3 은 "완전 닫기는 800ms 미만"이라는 설계 가정 자체가 반증된 경우).

**컴파일타임 확인 사항(참고)**: androidx.window 1.3.0 소스 확인 — `WindowInfoTracker.windowLayoutInfo(Context)` 의 `@UiContext` 는 `@RequiresOptIn` 아님(opt-in 불요). `FoldingFeature.bounds` 는 `android.graphics.Rect` — `FoldStateMonitor` 힌지 좌표 로그를 그대로 실좌표로 사용 가능.

### [측정] 항목 1 — `@UiContext` 수용 컨텍스트 (3-인자 WindowContext 채택)

| 후보 | 결과 | 물증 |
|---|---|---|
| ① 서비스 자신 | ❌ 구독 거부 | `assertUiContext` → `IllegalArgumentException` |
| (구) 2-인자 `createWindowContext` | ❌ 생성 자체 불가 | `UnsupportedOperationException`(display 미연결) → 죽은 코드로 삭제 |
| ② **3-인자 `createWindowContext(display, TYPE_ACCESSIBILITY_OVERLAY, null)`** | ✅ **채택**(방출 수신 확인) | display=`DisplayManager.getDisplay(DEFAULT_DISPLAY)`, API 31+ 가드 |

전멸 시 조용한 격하 실증(수정 전 빌드): 크래시 없음·`Log.w` 만·폴드 감지만 격하·서비스 정상.

### [측정] 항목 2 — 노트북 자세 방출 + `orientation` 의미론

| 상태 | posture | hingeBounds |
|---|---|---|
| 노트북 자세 + 가로 창 | `HALF_OPENED_HORIZONTAL` | `Rect(0, 984 - 2184, 984)`(y=984) |
| 노트북 자세 + 포트레이트 고정 | `HALF_OPENED_VERTICAL` | `Rect(984, 0 - 984, 2184)` |
| 닫힘/전환 중(커버 기하 1080×2520) | HALF_OPENED_* | `Rect(540, 0 - 540, 2520)` / `Rect(0, 540 - 2520, 540)` |
| 완전 닫힘 | `UNKNOWN` | null |

`orientation` 은 물리 힌지 방향이 아니라 **창 상대 좌표 의미론**이다 — 회전 잠금으로 포트레이트 고정 시 물리적 노트북 자세여도 `HALF_OPENED_VERTICAL`. **⚠ 검증/사용 전 `adb shell cmd window user-rotation free` 확인 필수.** 닫는 도중에도 방출이 끊기지 않고 좌표계가 내부 화면 → 커버 디스플레이로 갈아탄다.

### [측정] 항목 3 — 완전 닫기 오발화: 설계 가정 반증 → 2층 방어

**반증**: 닫기 HALF_OPENED 대역 체류 3표본 = **2.1s / 1.95s / 1.2s**, 전부 `DEFAULT_STABILITY_MS`(800ms) 초과. **오발화 물증**(07-27 23:31): 대역 진입 +803ms 트리거 → 1.15s 뒤 완전 닫힘 → Recents 진입 3회 → `ENTRY_STEP_FAILED`(`display-off` 게이트는 +800ms 시점 화면 아직 켜져 있어 무력). 디바운스 증액은 타이밍 도박(ADR-2 위반)이라 기각, 조건 신호 2층 채택.

**방어 1층 — 힌지 각도 안정성 게이트 [측정]**: `Sensor.TYPE_HINGE_ANGLE` Fold 7 실노출(도 단위, 노트북 ≈90.0, 닫힘 0.0, on-change 방출). 게이트 = 대역 [45°,135°] ∧ (침묵 ≥600ms ∨ 600ms 윈도 스프레드 ≤8°). 정상 속도 닫기 **2/2 차단**. 센서 무가용 시 통과로 격하.

**방어 2층 — FLEX 세션 자세-이탈 취소 [측정]**: 멈칫 동반 느린 닫기(대역 내 정지 ≥850ms)는 1층 통과 → 자세가 `HALF_OPENED_HORIZONTAL` 이탈 시 기존 cancel 경로로 세션 취소. 실증: `flex session cancelled: posture-exit`(07-28 00:04:42). 수동 세션(OVERRIDE)은 비대상. Done 이후엔 취소 안 함(Done 3.2s 후 펴기, 분할 유지, 07-28 00:05).

### [측정] 항목 4 — E2E 자동 상단 배치 (2회 재현)

유튜브 가로 전체화면 → 노트북 자세 → **접기~완료 7.1s**(트리거 지연 ≈1.05s = 디바운스 800 + 침묵 600 중첩 + 폴링 250).

```
fold posture changed: FLAT -> HALF_OPENED_HORIZONTAL
hinge angle=90.0
flex auto-arrange trigger: target=com.google.android.youtube (source=active-window)
arrange decision: aspectSource=CACHED placement=TOP placementSource=FLEX dividerCenterY=1236
  → DRAG 3스텝 전부 1시도 통과 → 드래그 → verify residualRows=84 → ADR-5 보정 1회(target 1153) → residual 0
arrange done: verified=true residual=0 adjusted=true desired=TOP effective=TOP
```

픽셀 물증: 상단 페인 영상 풀블리드(검은 띠 0), 하단 FW Panel.

### [측정] 항목 5 — FLEX last-success 비오염

| 단계 | 트리거 | 결정 | 결과 |
|---|---|---|---|
| 기준선 | 수동 `--es placement bottom` | `placementSource=OVERRIDE placement=BOTTOM` | done → BOTTOM 저장 |
| 검증 | (항목4 FLEX TOP done 직후) FLAT 에서 무override 트리거 | **`placementSource=LAST_SUCCESS placement=BOTTOM`** | done residual=0 — FLEX 값(TOP)에 오염되지 않음 |

FLAT 상태에선 FLEX 티어 비활성(조건 = 결정 시점 posture==`HALF_OPENED_HORIZONTAL`).

### 신규 함정·한계 (15차)

1. **폴드 전환 중 일시 창 포그라운드 오염** [측정]: 회전 잠금 해제 직후 삼성 월렛 quick 카드가 event-tracked 포그라운드를 오염 → 월렛 대상 자동 세션 발화(07-27 23:20, `ENTRY_STEP_FAILED` 자멸). 실사용 대상(유튜브 등)은 active-window 소스가 정상 타게팅 → v1.5 후보: 포그라운드 안정성 윈도.
2. **재열기 멈칫 한계** [측정+결정]: 닫힘→열기 도중 대역 내 ~90°에서 ≥1.4s 멈칫하면 정당한 재열기와 물리적으로 구분 불가 → 발화 수용이 설계 결정. Done 이후 늦게 펴면 완주된 TOP 분할이 잔존(복구=수동 해제). 키가드 게이트는 정당 사용례를 죽여 기각(v1.5 재검토).
3. **닫힘 전환 중 `isSplitActive` 오판 1회 관측** [재현성 미확인] — 자세-이탈 취소로 무해화.
4. **발화 지연 체감치**: ≈1.05~1.7s(디바운스800+침묵600 겹침+폴링250).

### 파일 변경 요약 (15차)

| 파일 | 변경 |
|---|---|
| `platform/FoldStateMonitor.kt` | 후보 체인: ①서비스 자신(유지) ②3-인자 display WindowContext(채택) ③createDisplayContext(예비). SDK 31 가드 |
| `platform/HingeAngleMonitor.kt`(신규) | `TYPE_HINGE_ANGLE` 래퍼, 샘플마다 Log.d, 센서 부재 시 Log.w 1회 |
| `domain/FlexModePolicy.kt` | `onHingeAngle`/`isAngleStable` 추가(순수 유지), 불안정 시 armed 유지 |
| `service/ArrangerAccessibilityService.kt` | 250ms 조건 폴링(`awaitFlexTrigger`), 자세-이탈 시 FLEX 세션 취소 |
| 테스트 | 220 → 230(`FlexModePolicyTest` 14→24) |

### 설계상 알려진 잔여 (v1.5 후보, 코드 무변경)

- 자동 트리거 게이트2(busy)와 `startArrange()` 재검사 사이 `loadProfilesConfig()` IO 서스펜드 지점에 이론상 TOCTOU 레이스(발생 확률 매우 낮음, v1 미대응).
- 기존 분할 활성 상태에서 플렉스로 접는 경우(게이트3 `split-already-active`)의 재배치는 v1.5 로 명시 보류.

---

## P3 잔여 소규모 실기기 확인 (2026-07-28 16차 — 3항목 완료)

검증 기기 = SM-F966N / One UI 8 / API 36.

### [측정] 항목 1 — 알림 권한 플로우 (P3-1 잔여) — 통과

`pm revoke POST_NOTIFICATIONS` 후 온보딩 진입 → 알림 카드 "권한 필요"(적색)+"권한 설정으로 이동" 정확 렌더(오버레이·접근성 카드는 "허용됨" 유지) → 탭 → 시스템 다이얼로그 발화 → 허용 → 카드 즉시 "허용됨" 전환, `dumpsys` `granted=true USER_SET`. FGS 자체 알림 확인: `pkg=dev.dj.foldwindow id=1001 channel=floating_launcher_channel flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE`. 권한 미보유 상태에서도 FGS·버블은 정상 동작(알림만 억제).

### [측정] 항목 2 — 온보딩 중지 레이스: 물리 폴드 접기 (P3-3 §26⑤ 잔여 해소)

"버블 중지" 탭 직후 1초 내 완전 접기 → NonCancellable 시퀀스 완주: FGS 소멸·`enabled=false` 영속·버블 소멸·스토어 에러 로그 0건.

### [측정+주관] 항목 3 — 버블 제스처 실사용감: 무불만

자유 조작 3세션 전부 done, residual=0(5.1s/1.2s/5.2s, 2번째는 분할 유지 상태 재탭 고속 경로). 탭/드래그/롱프레스 오분류 0건 → 제스처 임계값은 시스템 표준(`scaledTouchSlop`/`getLongPressTimeout`) 유지 확정.

### [코드 변경] 버블 숨김 안전 타이머 30s → 90s (`service/FloatingLauncherService`)

**근거([추정])**: 이론 최악 세션 = MENU 5스텝×3시도×3s(45s) + 디바이더 4s + 드래그 12s(세션 오버라이드) + verify ≈ 70s > 종전 30s. 조기 복원은 함정 #22 자충수(비대칭: 늦은 복원 무해/이른 복원 유해). **실측 최장 세션 12s**(15차까지 누적). 발화 실측 0회([미검증]: 타이머 실발화 경로).

---

## Phase 4 구현분 — 실기기 검증 대기 (2026-07-28 구현 세션, 전 항목 [미검증])

P4-2(파트너 위젯)·P4-3(커버 자동 해제)·P4-4(앱 페어 바로가기) 구현 완료(qa 정적 PASS, 257 테스트). P4-1(팝업 freeform)은 `docs/DESIGN_P41_FREEFORM.md` 프로브 F1~F6 선행 필수. 아래 전 항목이 다음 기기 캠페인(17차) 대상.

### P4-3 커버 화면 전환 자동 분할 해제 [미검증]

전제: 트리거 = `FoldPosture.UNKNOWN` 진입(완전 닫힘) + 600ms 디바운스 후 재검증. 게이트 = lever→posture→session→no-panel. 발화 = `PanelActivity.instance` 직접 `finishAndRemoveTask()`(`dismissSplit()` 미경유).

| # | 항목 | 기대 물증 |
|---|---|---|
| 1 | 분할 완료 상태에서 완전 접기 | `cover auto-dismiss fired` + 재열기 시 대상 앱 전체화면 |
| 2 | 닫힘(화면 꺼짐/잠금) 상태에서 패널 finish 가 실제로 분할을 해소하는가 | 핵심 미지수 |
| 3 | 접기 → 600ms 내 재펴기 | `skipped: reason=posture-bounced` |
| 4 | 배치 세션 진행 중 접기 | `skipped: reason=session-active` |
| 5 | 패널 없는 상태에서 접기 | `skipped: reason=no-panel` |
| 6 | 레버 회귀(`coverAutoDismiss=false`) | `skipped: reason=lever-off` |
| 7 | 닫힌 채 서비스 재기동(콜드스타트 UNKNOWN) | 무발화 |

한계(설계 수용): 세션 중 접기로 게이트에 막히면 그 닫힘 에피소드는 소진(재열기·재닫기 전까지 패널 잔존). 수동 dismissSplit × 자동 해제 레이스는 동일 패널 finish 로 수렴(이중 finish 무해).

### P4-2 파트너 위젯 (시계/메모/검정) [미검증]

모드 3종 렌더+하단 버튼 상시 표시 · 재기동 후 유지(DataStore) · ON_PAUSE flush 유지 · 좁은 페인 다중행/IME 영향 · 모드 전환 지연.

### P4-4 앱 페어 바로가기 [미검증]

롱프레스 메뉴 → 고정 다이얼로그 → 홈 아이콘 · 콜드탭→5s 폴링 내 전면→자동 배치 · `exportAppPair` 2s 식별 폴링 · 접근성 꺼짐/대상 앱 제거 처리 · 타임아웃 상수(2s/5s) 적정성.

---

## P4-1 프로브 F1~F6 (2026-07-28 프로브 세션 — adb 단독, 게이트 통과)

측정 환경: SM-F966N · Android 16 · One UI 8.5(`ro.build.version.oneui=80500`) · deviceState=3(펼침) · 일반 adb 셸(root 아님).

| # | 질문 | 결과 |
|---|---|---|
| F1 | One UI 팝업 = `WINDOWING_MODE_FREEFORM`(5)인가 | ✅ [측정] mode 5 창이 One UI 팝업 크롬으로 렌더. `isAlwaysOnTopFreeform=true`, `mNonOccludedFreeformAreaRatio=100` |
| F2 | 셸 권한으로 freeform 실행 가능한가 | ✅ [측정] `am start --windowingMode 5 -n <cmp>` → `mode=freeform`. 기존 태스크는 프런트 이동과 함께 전환 |
| F3 | 실행 후 bounds 제어 수단 | ✅ [측정] `am task resize <taskId> L T R B` — (200,300,1200,2100)·(300,200,1300,1700) **오차 0** 정확 적용 |
| F4 | UNRESIZEABLE(넷플릭스) 팝업 진입 | ✅ [측정] 진입·리사이즈 성공. `enable_non_resizable_multi_window=0` 상태에서도 성공(셸 경로는 One UI 설정 비의존) |
| F5 | DRM 표면이 팝업에서 렌더되는가 | ⏳ [미검증] UI(프로필 선택) 렌더 정상, DRM 재생 육안은 17차 캠페인으로 |
| F6 | 팝업 창의 a11y 노출 형태 | ✅ [측정] `APPLICATION` 타입 일반 창, bounds=태스크 bounds 1:1, layer=z순서. 특수 타입 없음 |

**부수 실측**: 런처 컴포넌트 YouTube `com.google.android.youtube/.app.honeycomb.Shell$HomeActivity` · 넷플릭스 `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`(`$` 이스케이프는 원격 명령 통째 인용 필요). `am stack remove <taskId>` 동작 확인. `am start --windowingMode 1` 로 기존 freeform 태스크 전체화면 복귀 시도 → 모드 전환 안 됨[측정 1회](부적합, 홈 이동으로 정리).

**판정: F1·F2 통과 → 후보 A(Shizuku 셸 명령: `am start --windowingMode 5` + `am task resize`) 채택.** 후보 B(binder/HiddenApiBypass) 불필요.

### P4-1 구현 세션 추가 실측 (2026-07-28 같은 날 — qa D1·D2 해소 근거)

- **팝업 초기 배치**: mode 5 실행 직후 태스크 bounds `Rect(354, 150 - 1803, 2123)` — top y=**150** → `PopupPlanner.TOP_MARGIN=150` 근거(좌우 354/165 비대칭은 One UI 자체 배치).
- **`am stack list` 원문 46행 캡처**: 태스크 행은 단일 물리 행(taskId·컴포넌트·bounds·visible·topActivity 한 줄). 실측 행 원문:
  `  taskId=4971: com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell$HomeActivity bounds=[200,300][1200,2100] userId=0 visible=true topActivity=ComponentInfo{...}`
  `StackListParser` 정규식을 이 원문 46행에 대조 → 태스크 행 10개 전부 정확 추출(같은 원문이 `StackListParserTest` 픽스처로 사용).
- freeform 태스크 bounds 는 mode 5 재실행(프런트 이동) 후에도 유지됨[측정 1회]. 기기에 Shizuku 미설치(`pm list packages` 0건) — E2E 는 설치 선행 필요.

### P4-1 구현분 — 실기기 검증 대기 (전 항목 [미검증], Shizuku 설치 선행)

Shizuku 설치→활성→권한 온보딩 3분기 · 메뉴 노출 조건 · 바인드 지연/재바인드 · E2E bounds 검증(±8px) · UNRESIZEABLE E2E+DRM 재생 · 버블 오버레이 낙착 무결성 · `boundsMatch` 허용오차 적정성.

---

## 17차 캠페인 — Phase 4 구현분 검증 (2026-07-28 밤, adb 주도 + device_state 에뮬레이션)

검증 기기 = SM-F966N / One UI 8 / API 36. 최종 빌드(21:12 설치). **신규 검증 수단**: `adb shell cmd device_state state 0` / `state reset` 이 `FoldPosture.UNKNOWN` 진입/복원을 재현(물리 접기 없이 P4-3 게이트 검증 가능). 한계: 상태 전환 전파 ~1s(600ms 미만 바운스 재현 불가), 화면 꺼짐/잠금 미재현.

### P4-2 파트너 위젯 — 5항목 통과

3모드 렌더+버튼 상시 표시 ✅ · 모드/메모 값 프로세스 킬 후 유지 ✅ · 메모 ON_PAUSE flush + force-stop 후 유지 ✅ · IME 등장 시 One UI 가 상단 페인 압축(구조 유지, 장문 스크롤은 [미검증]) ✅ · 모드 전환 즉시 체감(800ms 내) ✅.

### P4-3 커버 자동 해제 — 6/7 통과 (에뮬레이션 기준)

`cover auto-dismiss fired` 2회 ✅ · **핵심 미지수 해소(에뮬)**: 닫힘 중 패널 finish → 재펴기 시 대상 앱 전체화면, 잔존 0 ✅(물리 접기는 잔여) · `posture-bounced` 미발동(에뮬 지연 ~1s > 600ms 창) 🔶(단 오발화 0 실증) · `session-active` 정확 차단 ✅ · `no-panel`(4회) ✅ · `lever-off` ✅ · 콜드스타트 UNKNOWN 무시 ✅.

### P4-4 앱 페어 바로가기 — 4.5/5 통과

메뉴→다이얼로그→아이콘 생성 ✅ · 콜드 E2E(force-stop→탭→~8s→arrange done) ✅ · 식별 폴링 1/1 ✅ · 접근성 꺼짐 처리 ✅/대상 앱 제거 [미검증-침습 회피] 🔶 · 타임아웃 체감 적정 ✅.

### P4-1 팝업(freeform) — 7항목 전부 통과 (DRM 육안만 잔여)

Shizuku v13.6.0 을 GitHub 공식 릴리스에서 adb 설치, `libshizuku.so` 스타터 직접 실행으로 활성화(재부팅 시 스타터 재실행 필요). 온보딩 3분기 ✅ · 메뉴 재오픈만으로 항목 출현 ✅ · 최초 바인드 ~0.7s/서버 재시작 후 재바인드 성공 ✅ · 유튜브 E2E `popup done bounds=(64,150,1904,1185)`=1840×1035=**1.778 정확** ✅ · 넷플릭스 동일 bounds + DRM 재생 세션이 팝업 내 구동(PlayerActivity 렌더, SurfaceFlinger `Layer (Secure)`, 캡처 검정=보안 표면 정상) — **잔여: 실화면 육안** ✅ · 버블 표시 중 낙착 무결 2/2 ✅ · `boundsMatch` ±8px 통과 2/2 ✅.

### ⚠ 신규 결함 — step3 패널 소환 경로 부재 (P4-3 부작용) → 18·19차에서 원인 확정·해소

증상: 분할 진입 step3 `node-not-found` 3전멸 재현 4회 연속(21:39/21:42/21:44/21:45). 원인: step3 「FW Panel」 피커 노드 = 패널 태스크 카드인데 `finishAndRemoveTask()` 가 카드를 지워 소환 폴백이 없다(커버 자동 해제·dismissSplit·purgeStalePanelTasks·자가 가드가 전부 자기 코드로 카드를 제거). purge 자충 실증(21:45), 수동 피커 탭만 복구(21:47). → **설계 확정 `docs/DESIGN_27_PANEL_CARD.md`**, 18차 프로브가 후보 판정을 뒤집음(아래).

---

## 18차 프로브 — #27 결함 원인 확정 + 수정 방향 판정 (2026-07-28 밤, adb 전용, 코드 무변경)

기기 SM-F966N / One UI 8 / API 36, 펼침 1968×2184 세로. 버블 FGS 미기동(함정 #22 무개입), 접근성 ON.

### ① purge 자충 — 단일 로그 시퀀스로 물증화 [확정]

`purgeStalePanelTasks: 잔존 패널 태스크 1개 제거` → `arrange decision` → `dumpsys recents` 패널 태스크 0 → `EnteringSplit(step=3) -> Failed(reason=ENTRY_STEP_FAILED)`. 자기 코드가 자기 전제를 지우고 그 부재로 실패.

### ② G1 통과 — `finish()` 는 분할을 해소하면서 카드를 남긴다 [확정]

패널 페인 BACK → 분할 해소, 패널 태스크 `Activities=[] autoRemoveRecents=false` → 카드 생존, 피커 1번(MRU)으로 재출현. `finishAndRemoveTask` 의 `removeTask` 는 어떤 실측에도 요구되지 않은 초과 동작임이 확정.

### ③ G3 통과 — 액티비티가 죽은 카드도 정상 낙착한다 [확정 · purge premise 반증]

`Activities=[]` 카드를 피커에서 탭해도 정상 상하 분할·동일 taskId 재사용·전체화면 강탈 0. purge 의 원래 근거(2026-07-25 실측)는 launchMode=singleTask 시절·버블 숨김 도입 이전 것이라 이미 다른 수정으로 해소됐을 가능성이 높다.

### ④ 피커 구조 실측 — 후보 ③(앱 그리드) 취약 확정

`all_apps_button`/`search_button` 은 resource-id 라 로케일 무관이나, 연 앱 서랍 1페이지에 FW Panel 부재 + `scrollable` 노드 0 → 페이징/검색 없이 도달 불가(최후 폴백으로만 유지). recents 오버뷰엔 미노출인데 피커엔 노출(`mHasBeenVisible=false` 취급 차이 추정).

### ⑤ 카드 복구 수단 (개발 편의, 구현엔 미사용)

`am start -n .../.ui.PanelActivity > /dev/null; input keyevent 3` → HOME 이 `onPause` 로 자가 가드 job 취소 → 카드 생성. 타이밍 의존(ADR-2 위반)이라 캠페인 중 복구 도구로만 사용.

### ⑥ AOSP 사실관계 [확정 — 소스 라인 확인]

| 사실 | 함의 |
|---|---|
| `autoRemoveFromRecents` 기본=false(일반), true(document). `shouldAutoRemoveFromRecents()` false 면 미제거 | ②의 근거 |
| **함정**: `!hasChild() && !getHasBeenVisible()` 이면 `autoRemoveFromRecents=false` 라도 **강제 제거** | 한 번도 안 보인 태스크는 finish 시 카드가 사라진다 |
| 죽은 카드 탭 → `startActivityFromRecents` → `task.intent` 재실행, `Task#setIntent` 은 **extras 보존** | 열린 질문 #28(base intent 오염)이 [확정] |
| `makeTaskLaunchBehind()`(API 21, NEW_DOCUMENT 동반, standard 런치모드) — `onResume` 미호출(`mDoResume=false`) → STOPPED 정착 | 소환 수단 B1, 자가 가드 구조적 무발화 |
| `moveTaskToBack(true)` = 제거 경로 미트리거 | 소환 수단 B2 성립 근거 |
| **`ActivityManager.addAppTask()`**(API 21) = "recents entry … will exist **without an activity**". 명시 ComponentName+NEW_DOCUMENT+**RETAIN_IN_RECENTS** 필요 | 액티비티 미시작=포그라운드 무접촉. 소환 1순위(B0) |

### 잔여 프로브 (구현 중/후)

G2 B0 `addAppTask()` 수용 여부 · G4 결함 재현 E2E · G5 prune×소환 무자충 · G6 재설치 스테일 회귀 · G7 「함정」 회귀.

### 부수 실증

12차 캐시 폴백 실전 발동 2회(오염 pre → confirm 합치 `RAW_DISAGREE` → CACHED 1.778, residual=0). 메모 IME 는 분할 상단 페인을 압축·해제 시 원복.

### Phase B — 물리 조작 잔여 (사용자 확인 필요, 당시 기준)

P4-3 항목 1·2 물리 접기(에뮬은 커버 디스플레이 활성 상태라 등가 아님) · P4-1 항목 5 DRM 육안 · P4-3 항목 3 물리 600ms 재펴기(인간 조작으로 사실상 도달 불가, 미검증 수용 권고).

---

## 19차 — #27 v1 실기기 캠페인 (2026-07-28 밤)

대상 빌드 `c252a01`(축 A + 축 B + #28). 기기 SM-F966N(`R3CY8029XBF`), 내부 화면 1968×2184, 회전 free.

### 한 줄 결론

**축 A(파괴 제거)는 전부 통과, 축 B(소환)는 실패**(소환이 만든 카드가 step3 를 깨뜨리는 유해 실패). 축 B 의 존재 이유였던 「카드 0 = 배치 불능」 전제 자체가 5/5 로 반증됐다.

### 게이트 결과

| 게이트 | 판정 | 근거 |
|---|---|---|
| G2 소환(B1) | ❌ **실패(유해)** | 소환 자체는 성공(`summoned(mode=launch-behind)`, 14ms)하나 그 카드를 step3 가 탭하면 전체화면 낙착 → 가드 3회 → `ENTRY_STEP_FAILED` |
| G4 결함 재현 | ✅ | `cover auto-dismiss fired` → 카드 생존(17차엔 여기서 소멸) → 이어서 **3/3 done**, 전부 `already-present`, `node-not-found` 0 |
| G5 prune 무자충 | ✅ | 패널 태스크 2개 상태에서 `pruneExtraPanelTasks: 보존 1 / 제거 1` → 남은 카드로 step3 성공 → `done verified=true residual=0` |
| G6 재설치 스테일 | ✅ | 재설치 `Activities=[]` 죽은 카드 → 배치 1회 done, `already-present` |
| G7 AOSP 함정 | ✅ 미발동 | `RETAIN_IN_RECENTS` 가 `shouldAutoRemoveFromRecents` 강제 제거를 상쇄한 것으로 추정 |
| 레버 회귀 | ✅ | `panelCardPreflight=false` → `lever-off` ∧ 소환 시도 0 ∧ 축 A 정상 |

### [확정] G2 실패의 원인 — 소환이 base intent 를 오염시킨다

| 카드 출처 | base intent flags | step3 결과 |
|---|---|---|
| `makeTaskLaunchBehind()` 소환(taskId 5036) | `flg=0x18182000`=NEW_TASK\|MULTIPLE_TASK\|NEW_DOCUMENT\|RETAIN_IN_RECENTS | **전체화면 낙착** → `fullscreen 상태 감지`×3 → `ENTRY_STEP_FAILED` |
| 런처 형태 실행(taskId 5038) | `flg=0x10000000`=NEW_TASK only | **분할 페인 정상 낙착** → `arrange done verified=true residual=0` |

죽은 카드 탭은 `startActivityFromRecents` → base intent 재실행(18차 Q2). 소환의 `NEW_DOCUMENT|MULTIPLE_TASK` 가 보존돼 피커 탭이 새 문서(전체화면)로 라우팅됨 — #28 과 동일 결함 클래스(대상만 extras→flags). 소환 시점엔 가드가 발화하지 않았다(Q3 "`onResume` 미호출" 예측 확인) — 실패는 카드를 탭할 때 발생.

### [확정] 축 B 의 전제 반증 — 카드 0 에서도 step3 는 정상 동작한다 (5/5)

레버 off + 패널 카드 0개 상태에서 배치 5회 전부 `arrange done`, step3 attempt 1 성공.

| 카드 0 유도 방법 | 시도 | 결과 |
|---|---|---|
| `adb uninstall` 후 재설치 | 3 | 3/3 done |
| `pm clear`(신규 설치 교란 배제) | 2 | 2/2 done |

`pm clear` 표본이 "신규 설치라 노출"이라는 교란을 배제한다 → 피커는 recents 카드가 아닌 **앱 목록**에서도 「FW Panel」을 제공한다(`PanelActivity` 의 MAIN/LAUNCHER 노출이 근거로 추정 — 원인 변수는 미조작). ⇒ DESIGN_27 §1.3 「카드를 만드는 경로 0개」는 부정확. 카드가 없어도 소환은 불필요.

### [미해결] 17차 3전멸의 진짜 원인은?

17차 `node-not-found` 4회 연속(카드 0)은 이 캠페인에서 재현되지 않았다(카드 0 에서 5/5 성공). 「카드 0 → step3 불능」 인과는 불완전한 설명 — 다른 요인(피커 화면 상태·purge 타이밍·레이아웃 차이) 가능성. 축 A 는 이 미해결과 무관하게 정당하다.

### 부수 관측

`RecentTaskInfo` 는 dumpsys 에 `lastActiveTime` 노출(예: 276753305)하나 공개 API 접근 불가 — 현재 구현은 0 전달 + MRU-first 타이브레이크(G5 에서 의도대로 최신 카드 보존). `cmd device_state` 원복은 `state reset`(17차 표기 정정).

---

## 개선 웨이브 W1~W7 — ✅ 20차 실기기 검증 (2026-07-31)

계획·원 리뷰 문서(2026-07-29)는 전 항목 반영 완료 후 폐기 — 각 수정의 이유는 해당 `.kt` KDoc 에 인라인으로 남아 있다. 절차 = `docs/DEVICE_VERIFICATION_RUNBOOK.md`(S-B~S7). 원 로그 = `logs/S0_baseline.txt`(베이스라인 `1965a72`=W0~W4, 구동부 재작성 3종 미포함) `logs/S1_drag.txt` `logs/S2_menu_FAIL_head.txt`(#29 재현) `logs/S2_menu_fixed.txt`(#29 수정 검증). HEAD=`20dd987`+#29 수정.

**JVM 테스트 사각지대 [확정, qa 변조 실험 3회 반복]**: `ArrangerAccessibilityService`/`SplitEntry`/`DividerPopupRotator`/`PaneSwapper`/`FloatingLauncherService`/`NodeActions`/`Polling` 을 인스턴스화하는 JVM 테스트가 **0개**다. W5(`>`→`>=` 변조, 29개 전부 통과) · W6(7종 동시 변조, 312개 전부 통과) · W7(9종 중 5종 변조, 322개 전부 통과) — **「테스트 통과」는 이 3웨이브의 안전 근거가 아니며 실기기가 유일한 실효 검증**이다.

### W1 — 보안 차단 (S1·S4·F7·F8)

정적 DoD: 테스트 286 · assembleDebug · lintDebug 신규 0 · assembleRelease · 릴리스 병합 매니페스트에서 4컴포넌트+FileProvider **0건**.

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W1-1 | S1 debug 소스셋 분리 — 트리거 생존 | ✅ | `adb broadcast ARRANGE` 정상 배치 |
| W1-2 | S1 프로브 생존 | ✅ | `query-activities` 에 `probe.ProbeActivity` 존재 + 접근성 Enabled 등재 |
| W1-3 | S4 토큰 정상 경로(1차=`instance.finish()`, 토큰 미사용) | ✅ | `dismissSplit: 성공` + 토큰 로그 0건 |
| W1-4 | S4 토큰 폴백 경로(`instance==null ∧ hasPanelTask()`) | [미검증 유지] | 유도 3후보 소진(아래) |

정적 확인: AGP 매니페스트 머저가 XML 주석을 병합 결과에 보존(main 주석의 probe 리터럴이 릴리스까지 유입 → debug 경로 표기로 정정) · lint `ForegroundServiceType` 은 debug 변형(매니페스트 2개 병합)에서만 발생하는 대조 실험 확정 오탐(`@SuppressLint`+근거 주석으로 억제, baseline 미사용).

### W2 — Shizuku 셸 하드닝 (F3·F4·F5·S2·S3)

정적 DoD: 테스트 299 · assembleDebug · lintDebug 신규 0 · assembleRelease. AIDL 시그니처 변경으로 `versionCode` 2→3(`UserServiceArgs.version()` 미상향 시 `AbstractMethodError`).

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W2-1 | AIDL 재생성 무오류 | ✅ | `ShellExecUserService 연결됨`, `AbstractMethodError` 0건 |
| W2-2 | E2E 유튜브 팝업 bounds 일치 | ✅ | `popup done bounds=(64,150,2120,1306)`=2056×1156=**1.778 정확** |
| W2-3 | E2E 넷플릭스 + DRM | ✅ | 팝업 성립 + 동일 bounds |
| W2-4 | `$` 클래스명 argv 정상(S3 실증) | ✅ | `Shell$HomeActivity` 인용 없이 argv 로 정상 |
| W2-5 | S2 허용 목록 오차단 없음 | ✅ | `blocked by policy` 전 세션 0건 |
| W2-6 | F5 재바인드 복구 | ✅ | 서버 kill(`kill <pid>`, force-stop 은 무효) → 메뉴 소멸 → 재실행 → 재시도 성공 |
| W2-7 | F3 타임아웃 실효 | [미검증 유지] | 인위 유도 곤란, 자연 발생 대기 |

정적 확인: argv 토큰 순서가 구 셸 문자열과 토큰 단위 일치 · 허용 목록 강제 지점은 원격(`ShellExecUserService.run`), 클라 검사는 fail-fast 중복 · 타임아웃 예산: 클라 `timeoutMs+BINDER_OVERHEAD_MS(2s)` > 원격 `waitFor(timeoutMs)`(중단점 없는 바인더 호출이라 실효 타임아웃은 원격에만 존재) · **소스 위생 함정**: Kotlin 소스에 NUL 문자 이스케이프 작성 시 raw NUL 바이트(0x00)가 그대로 박히는 현상 발견 — `c.code==0`/`0.toChar()` 로 재작성해 제거.

### W3 — 기하 정합성·도메인 불변식 (F2 1단계·F1)

정적 DoD: 테스트 311(304+7) · assembleDebug · lintDebug 신규 0.

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W3-1 | 가로 배치 무회귀 | ✅ | `기하 불일치` 로그 0건, 배치 정상 |
| W3-2 | 세로 트리거 시 명시적 실패 | ✅ | `startArrange: 화면 기하 불일치 — screen=1968x2184 expected=2184x1968(v1 미지원)` 1건 + 토스트 육안, 배치 미시도 |

F2 2단계(v1.5 범위 밖) 참고: 실화면 기반 `WindowGeometry` 생성은 세로 분할 디바이더 두께·최소 페인 높이 실측이 선행 조건.

### W5 — 구동부 안정화 (F6·F9)

정적 DoD: 테스트 312(311+1) · assembleDebug · lintDebug 신규 0. **터미널 전이 불변식[확정, 소스 전수]**: `ArrangeStateMachine.kt` 에서 `Done`/`Failed` 를 만드는 Transition 은 12곳이고 전부 `emptyList()`(174/218/251/256/298/328/332/348/353/377/383/391행) — F6 등가는 이 불변식에 의존, `ArrangeStateMachineTest` 전수 조합(대표 상태10×이벤트15×config2=300, 터미널103건)이 기계 강제(W6/W7 이 이 불변식에 기댐).

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W5-1 | DRAG 무회귀 | ✅ | residual=0·보정 0회, 베이스라인과 동수준 |
| W5-2 | MENU 무회귀 | ✅ | (W6-2 와 동시 충족) |
| W5-3 | 전이 로그 순서 동일 | ✅ | 전이 시퀀스 문자 단위 동일(양쪽 경로). 유일 델타 = `arrange done` 의 ` tolerance=8` 부기(F9 의도된 추가) |
| W5-4 | F9 「허용치 초과」 발화 | ✅ | tolerance=0 실험: 토스트 「배치 완료 · 잔여 30px (허용치 초과)」 정확 일치 + `residual=30 … tolerance=0` 로그. 실험 후 8 원복 + `git diff` 빈 출력 확인 |

### W6 — 세션 상태 캡슐화 (M1 1단계)

정적 DoD: 테스트 312(무변경) · assembleDebug · lintDebug 신규 0 · 프로덕션 1파일 185+/124−.

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W6-1 | DRAG 무회귀, 로그 포맷 동일 | ✅ | `arrange decision:`/`verify:`/`arrange done:` 베이스라인 동일 |
| W6-2 | MENU 무회귀 | ✅ | `resize-mode detection: … recipe=MENU` + 5단 완주 |
| W6-3 | 취소 경로 + 재배치 | ✅ | Dragging 중 Cancel → `Failed(reason=CANCELLED)` → 재배치 즉시 성공(세션 완전 초기화 증거). 부수: 늦게 도착한 스왑 코루틴이 `mech=none converged` 후 후속 전이 0(종료 후 쓰기 no-op 실발화) |
| W6-4 | 분할 해제 | ✅ | `dismissSplit` 이 `machineState==Idle` 가드 통과 |
| W6-5 | 연속 2세션 상태 누수 없음 | 조건부 ✅ | B(넷플릭스) `label=` `aspectSource=PROFILE` 전부 B 기준, A 잔재 0. B=PROFILE 은 합치 게이트·캐시 저장을 정당 스킵하므로 `consensus:` 재발동·`aspect cache save:` 는 이 세션에서 관측 불가(B=MEASURED 후보 세션 자연 관측 대기) |

### W7 — 성능·중복 정리 (P1·M3·P2·P4)

정적 DoD: 테스트 322 · assembleDebug · lintDebug 신규 0. 계획서 3세션에서 6항목으로 확장(qa 권고 — MENU 세션이 리사이저블 앱이면 `DividerPopupRotator` 가 한 줄도 실행 안 됨).

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W7-1 | P1 노드 선택 등가(DRAG) | ✅ | `step2 card-icon matched via selector [ko-content-desc]` — 베이스라인 동일(`structural-clickable-label` 회귀 아님) |
| W7-2 | P1 신규 상한 미발동 | ✅ | `노드 예산 4000 소진`·`깊이 상한 50 초과` 전 세션 0건 |
| W7-3 | M3 회전 클릭(MENU·UNRESIZEABLE 필수) | ✅ | ① `menuStep2/3 split-menu matched via selector [ko-split-menu]` ② `clickWhenFound: [rotateOnce rotate-node] clicked-self (text=null/desc=시계 방향으로 회전)`(통합본 라벨 부기 정확, 베이스라인은 `clicked` 만) ③ 회전 후 상하 분할·완료 |
| W7-4 | PaneSwapper 탭 duration 해소 | ✅ | `settleGate ok in 153ms` → `swap: converged cycle=0 mech=a11y` — 베이스라인 동형 |
| W7-5 | P2 부팅 후 버블 위치 복원 | ✅ | 재부팅 후 최종 위치=저장 위치, `적용 실패` 0건, ANR 0(기본 위치 점프는 육안 미관측이나 목적인 「복원 도착」은 확정) |
| W7-6 | P2 경합 가드(best-effort) | 미재현·무이상 | 부팅 직후 1초 내 드래그에서 튐 없음, 가드 로그 미발화(경합 창 자체가 안 열림). 미재현은 무결의 증거가 아님 |

정적 확인: 신규 상한 `MAX_TREE_DEPTH=50`, `MAX_NODES_VISITED_TREE=4000`(`platform/NodeActions.kt`) 은 실측값이 아니다(구 순회는 상한 없음, 초과 시 `Log.w`, W7-2 가 그 로그 부재를 확인). `PaneSwapper.MAX_NODES_VISITED=500` 은 통합하지 않음(팝업 탐색용 실측값을 런처 전체 트리에 확대하면 진입 경로가 죽는다, 함정 #7). 로그 델타 2건(정보 증가, 동작 변화 0): `clickWhenFound: [$what] clicked`→`clicked-self`/`clicked-ancestor(text=…/desc=…)`, `gesture-tap-fallback`→`gesture-tap-fallback(text=…/desc=…)`.

---

## 20차 — 개선 웨이브 실기기 검증 캠페인 + #29 발견·수정 (2026-07-31)

### 한 줄 결론

**W1~W7 실기기 31항목 중 28 통과 · 3 [미검증] 유지(W1-4·W2-7·B계열 — 전부 계획된 자연 대기).** 부수로 신규 결함 #29(피커 유령 매치) 발견·수정·재검증 완료. A-1(물리 접기)·A-2(DRM 육안)도 종결.

### 세션 지도 실행 결과

| 세션 | 내용 | 결과 |
|---|---|---|
| S-B | 베이스라인 로그(`1965a72`) | ✅ 대조군 확보 |
| S0 | HEAD 설치·W1-1·W1-2 | ✅ |
| S1 | DRAG 주경로(top→bottom→취소→재배치→해제) | ✅ 8항목 |
| S2 | MENU 경로(넷플릭스) | ⚠ #29 발견 → 수정 → ✅ 재검증 |
| S3 | 팝업/Shizuku + A-2 | ✅(W2-7 만 자연 대기) |
| S4 | 재부팅×2 | ✅ / 미재현·무이상 |
| S5 | 세로 명시 실패 + 희귀 경로 유도 | W3-2 ✅ · W1-4/B-1/B-2 유도 불가 확정 |
| S6 | 물리 접기(A-1) | ✅ |
| S7 | 허용치 0 실험(W5-4) → 8 원복 | ✅ |

**A-1** ✅ 실물리 접기: `fold posture: FLAT → HALF_OPENED_HORIZONTAL` → `hinge angle 90.0 → 0.0` → `UNKNOWN` → `cover auto-dismiss fired` → 육안 확정(17차 에뮬 → 실물리 완결). **A-2** ✅ 넷플릭스 팝업 창 내 DRM 영상 육안 정상 재생(17차 Secure layer 로그 + 금회 육안 = 완결).

**W1-4·B-1·B-2** [미검증 유지] — 유도 3후보 소진: ①「활동 유지 안 함」은 분할 중 패널이 가시 상태라 destroy 안 됨(조건 논리적 유도 불가) ② `am kill` 은 FGS 보유로 거부 실증(pid 생존) ③ `force-stop` 은 접근성까지 죽여 조건 파괴.

### ⚠ 신규 결함 #29 — MENU 피커 유령(0-bounds) 매치 → 패널 전체화면 낙착 [발견·수정·재검증 완료]

**증상**: FW Panel 리센츠 카드 **잔존** 상태에서 MENU 레시피 menuStep4 가 3attempt 전멸 → `ENTRY_STEP_FAILED`(육안: 검은 시계 화면이 두 번 떴다 사라짐).

**재현 시그니처** (`logs/S2_menu_FAIL_head.txt`):
```
clickCycle: [menuStep4 panel-picker] cycle=0 mech=gesture dispatched=false   ← 5~30ms 간격 즉발
clickCycle: [menuStep4 panel-picker] cycle=1 mech=gesture dispatched=false
clickCycle: [menuStep4 panel-picker] cycle=2 mech=a11y dispatched=true
FORENSIC viewClicked … viewId=…:id/icon_container                            ← 오착지
FWPanelActivity: fullscreen 상태 감지 — 파트너 전용 액티비티이므로 종료
```

**원인 체인 [확정]**: 카드 잔존 시 그 카드의 0-bounds 유령 노드가 가시 노드보다 DFS 앞에 출현 → `findPanelPickerNode` 가 라벨 포함만 보고 bounds 를 안 봐 유령 우선 매치 → gesture 는 `tapNodeCenter` 의 `bounds.isEmpty` 가드로 무디스패치 → a11y 폴백이 유령의 clickable 조상(icon_container)을 클릭 → 런처가 일반 실행으로 해석 → 패널 전체화면 낙착.

**판별 실험 [확정]**: (a) 베이스라인 `1965a72` 도 문자 단위 동일 실패 → W0~W7 무관, 기존 결함(07-25 MENU 6/6 성공은 카드 부재의 우연) (b) 카드 스와이프 제거 후 동일 조작 즉시 성공(`dispatched=true → converged 340ms`) → 유령 소스=잔존 카드 확정. **#27 과의 관계**: 거울상(#27=카드 부재 시 소환 불능 / #29=카드 존재 시 유령 매치). 17차 3전멸(node-not-found)과는 시그니처가 달라 별개 유지.

**수정**: `findPanelPickerNode` 셀렉터 predicate 에 bounds 필터 추가(라벨 매치여도 빈 rect 면 비매치 → 재폴링). DoD: 테스트 322 무변경 · assembleDebug · lintDebug 신규 0 · domain diff 0.

**재검증 [확정]**: 카드=1 상태에서 `cycle=0 mech=gesture dispatched=true → converged 333ms` + `fullscreen 감지` 0건 + 회전·완료. 카드-부재 경로·DRAG step3 도 정상.

### 신규 사실 (20차 부수 관측)

- **넷플릭스는 재생 중 분할 진입 시 자체 팝업 플레이어(PiP)로 전환**하고, 재생 중 팝업 전환 시엔 진입 액티비티 재실행으로 재생 세션이 종료된다(앱 고유 동작. 팝업 창 안에서 새로 재생하면 정상). v1.5 후보: 팝업 대상 컴포넌트 선택 개선.
- **Shizuku 기동**: `<apk-dir>/lib/arm64/libshizuku.so` 직접 실행이 유일 수단 재확인(`app_process` 직접 기동은 SIGABRT). 재부팅 시 스타터 재실행 필요.
- **`am force-stop <shizuku pkg>` 는 서버를 못 죽인다**(shell uid 부모라 범위 밖) — `kill <pid>` 직접이 정답.
- **`am kill dev.dj.foldwindow` 는 FGS 보유로 무시**(pid 생존 실증) — B-1/B-2 유도 후보에서 제외 확정.
- 베이스라인 대조법 유효성 실증: `1965a72` 재설치 대조가 #29 를 「W7 회귀」 오판에서 구했다(향후 구동부 회귀 의심 시 표준 판별 절차로 재사용 가치).

### 기기 잔여 상태 (20차 종료)

프로파일 tolerance=8 원복(`git diff` 빈 출력) · HEAD+#29 빌드 설치 · 접근성 2종 활성 · Shizuku 서버 가동 중(재부팅 시 재실행 필요) · FW Panel 카드 잔존(정상, #29 수정으로 무해).

---

## #30 전체화면 재생 자동 트리거 — 설계·판정 술어 (2026-07-31 구현, 실측 = 21·22·23차)

구현 근거·설계 = `docs/DESIGN_30_FULLSCREEN_AUTO.md`. 기능은 사용자 토글 **기본 OFF**(`ProfileStore.isFullscreenAutoEnabled`)이며 꺼진 동안은 `onWindowsChangedEvent` 최전방 선차단에서 끝나 코드가 한 줄도 실행되지 않는다.

### 판정 술어(`FullscreenWindowJudge`)의 실측 앵커

표본 원본 = 위 「Phase 0 프로브 원본 측정」.

| 표본 | 출처 | 화면 | 판정 |
|---|---|---|---|
| 가로 몰입 재생(유튜브) | 런 ③ B절 | 2184×1968 | FULLSCREEN |
| 세로 비몰입(엣지투엣지) | 런 ① B절 | 1968×2184 | NOT_FULLSCREEN |
| 분할 활성(세로 좌우) | 런 ② B절 | 1968×2184 | NOT_FULLSCREEN |

**정정**: 런 ② B절의 `381,89,595,145 com.android.systemui` 는 type 열이 APPLICATION 이다. 설계서 §2.1 은 (b)절(비-APP 상단 전폭)에서 배제된다고 썼으나 실제로는 (a)절("전체 덮음 아님")에서 걸린다 — 최종 판정값·D10 결론은 동일해 코드 변경 불필요(근거는 `FullscreenWindowJudgeTest` 17번 KDoc).

### 로깅 (별도 프로브 빌드 불요, 토글 ON 시에만 발생)

`FWArranger` 태그: `fullscreen signal: <prev> -> <next> screen=WxH appFull=n topBars=m`(전이 시에만) · `fullscreen media probe: playing=<bool> usages=[...]`(판정 변화 틱에만).

### W0/W1 항목 정의 (실측 결과는 21·22·23차 절)

W0 = 판정 술어·상수 실측(가로 몰입/컨트롤 자동 숨김/일시정지/우리 분할/셰이드/상단 스와이프/재생 상태 4종/Shorts 세로 게이트) 8항목. W1 = 구현 후 검증(자동 발화 E2E/P-1 루프 부재/래치 해제/실패 복구/서킷브레이커/bubble-off/토글+재부팅/콜드스타트/세로 영상/넷플릭스 비대상/메인 스레드 A·B) 11항목. 절차 상세 = `docs/DESIGN_30_FULLSCREEN_AUTO.md` §6.

**미검증 상수(설계 당시)**: `DEFAULT_EXIT_HOLD_MS`(1200) · `FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS`(5000) · `AUTO_RECOVERY_TIMEOUT_MS`(2500) · `AutoTriggerLedger.DEFAULT_MAX_FAIL_STREAK`(2) — 21차에서 3종 실측 근거 확보(아래).

---

## 21차 — #30 전체화면 자동 트리거 실기기 캠페인 (2026-08-01, adb 주도)

**환경**: SM-F966N(`R3CY8029XBF`) One UI 8/Android 16. HEAD `42860c6` 재설치(직전 설치본은 `#30` 미포함이었음을 확인 후 진행). 회전은 `adb shell wm user-rotation lock 1`(가로 2184×1968)/`lock 0`(세로 1968×2184) 로 고정(물리 회전과 WM 관점 동일, 재현성 높음). 재생 소스 = 유튜브 `dQw4w9WgXcQ` + 자동재생 ON.

### 한 줄 결론

**W0 8항목 중 7 측정 완료(1 유도 불가) · W1 11항목 중 9 완료 · 신규 결함 #31 1건.** 설계의 두 핵심 판단 — 긍정 술어(D8)와 패키지 단위 에피소드 래치(D1·D2) — 는 실측으로 정당성이 확인됐다. 래치 해제 경로(W1-3)는 명세대로 동작하지 않는다(#31).

### 신규 도구 사실

`performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)` 은 이 기기에서 false 반환+무동작(프로브 C절이 화면을 훼손하지 않아 몰입 재생 중 반복 실행 안전) · `dumpsys accessibility` 에는 창 목록 없음(프로브만 수단) · 재생/일시정지는 `input keyevent 126/127`(MEDIA_PLAY/PAUSE) 이 확실(탭 2회는 더블탭=10초 탐색 오인) · 미디어 볼륨은 `adb shell cmd media_session volume --stream 3 --set N` · 밀리초 해상도 전이는 `adb exec-out screencap` 원시 버퍼 픽셀 1점 샘플링(왕복 ≈1.05s, ±0.1s 해상도).

### W0 — 판정 술어·상수 실측

| # | 결과 | 판정 |
|---|---|---|
| W0-1 | 가로 몰입 재생 4창 → `appFull=1 topBars=0` | **FULLSCREEN** ✅ |
| W0-2 | 컨트롤 표시 중 창 목록 동일. 자동 숨김 탭 후 **2.1s 가시/2.2s 소멸**(2회 재현) | ✅ |
| W0-3 | 일시정지+컨트롤 표시 창 목록 동일 → FULLSCREEN 유지. D9 미성립 | ✅ |
| W0-4 | 우리 분할 8창 → 전체 덮음 APP 0개 → (a)절 NOT_FULLSCREEN | ✅ |
| W0-5 | 셰이드 개방 5창, APP 0개+systemui 전면 → `appFull=0 topBars=1` | NOT_FULLSCREEN ✅ |
| W0-6 | 상단 스와이프 8창, 전폭 상단 바 출현 → `topBars=1`. 자동 숨김 **1.5s 가시/2.2s 소멸** | NOT_FULLSCREEN ✅ |
| W0-7 | 재생 `usages=[1]` / 일시정지 `usages=[]` / 스트림 볼륨0 `usages=[1]` / 앱내 음소거 = 유도 불가(유튜브 표면 없음) | ✅ 3/4 |
| W0-8 | Shorts 세로 8창 `SYSTEM 0,0,1968,89` 전폭 상태바 → 술어 단계 차단 + 세로 몰입은 게이트5 `reason=geometry-mismatch` **이중 차단** | ✅ |

**W0-1 창 목록(screen 2184×1968)**: UNKNOWN×2(sidegesturepad) · SYSTEM `1842,1607,1968,1733` **dev.dj.foldwindow(자기 버블)** · APPLICATION `0,0,2184,1968` youtube(active). 동결 표본(런 ③ 가로 몰입, 3창)에 없던 자기 버블(TYPE_SYSTEM 126×126)이 실제 목록에 있으나 `TOP_BAR_MIN_WIDTH_FRACTION`(0.80) 필터가 이를 죽인다(폭 126/2184=5.8%, 실측으로 확인). 앱 로그 동일: `fullscreen signal: UNKNOWN -> FULLSCREEN screen=2184x1968 appFull=1 topBars=0`.

**W0-4 창 목록(screen 2184×1968) — D8 의 결정적 증거**: UNKNOWN×2 · SYSTEM `0,1934,2184,1968`(런처 제스처 바) · SYSTEM `1842,1607,1968,1733`(버블) · SPLIT_SCREEN_DIVIDER `981,1209,1202,1263` · APPLICATION `985,1254,1199,1310`(디바이더 팝업) · APPLICATION `0,1243,2184,1968`(하단 페인) · APPLICATION `0,0,2184,1229`(상단 페인, youtube). **이 구성에 상단 전폭 시스템 바가 존재하지 않는다** — 부정 술어("상단 바 부재=몰입")였다면 자기 분할을 몰입으로 오판정했을 것. 긍정 술어는 (a)절(전체 덮음 APP 부재)에서 정확히 걸러낸다: `fullscreen signal: FULLSCREEN -> NOT_FULLSCREEN appFull=0 topBars=0`. 부수: 가로 상하 분할의 디바이더 창은 전폭 바가 아니라 핸들만(`981,1209,1202,1263`=221×54, centerY 1236). 페인 간극 14px(1229↔1243).

**상수 판정**: `DEFAULT_ENTRY_DEBOUNCE_MS`(3000) — 근거 확보(컨트롤 자동 숨김≈2.2s<3.0s, pre-measure 는 항상 컨트롤 소멸 후) · `DEFAULT_EXIT_HOLD_MS`(1200) — 상향 불필요하나 의미 확정(셰이드·transient bar 둘 다 1200ms 를 넘겨 disarm→재무장 → exit hold 로는 자가유발 재진입을 막을 수 없고 래치가 유일한 방어) · `FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS`(5000) — 동작 확인(일시정지 진입 시 정확히 5.05s 후 `reason=trigger-poll-timeout`) · `DEFAULT_MAX_FAIL_STREAK`(2) — 동작 확인(W1-5) · `AUTO_RECOVERY_TIMEOUT_MS`(2500) — 경로는 실행되나 복귀 성공 사례 미관측(W1-4).

### W1 — 구현 검증

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| W1-1 | 자동 발화 E2E | ✅ | 엣지+3.007s `media probe playing=true` → 트리거 → `startArrange … trigger=FULLSCREEN_AUTO` → 3.9s 후 `arrange done: verified=true residual=0 trigger=FULLSCREEN_AUTO`(토스트 억제 D19) |
| W1-2 | **P-1 루프 부재** | ✅ | 해제 직후 복귀 엣지+셰이드3회+상단스와이프3회 = 진입 엣지 6회 전부 `reason=latched`, 발화 0. 일시정지/재생 3회는 신호 전이 자체가 없었음 |
| W1-3 | 래치 해제(홈 왕복) | ❌ 결함 #31 → 22차 수정·재검증 ✅ | 아래 |
| W1-4 | 실패 복구 | △ 부분 | `ENTRY_STEP_FAILED` → `auto recovery: 2500ms 내 대상 앱 전면 복귀 미확인 — 추가 주입 없이 종료`. 가드·주입·데드라인은 동작, 복귀 성공은 미관측 |
| W1-5 | 서킷브레이커 | ✅ | 2연속 실패 후 `reason=auto-disabled streak=2`. 수동 버블 탭 1회로 스트릭 해제 확인 |
| W1-6 | bubble-off | ✅(문구 델타) | 로그 0줄·발화 0. `reason=bubble-off` 자체는 안 나옴(`onWindowsChangedEvent` 최전방 선차단이 게이트3 보다 먼저 끊김) |
| W1-7 | 재부팅 후 유지 | ✅ | `arranger service connected` + `fullscreen auto snapshot: lever=true userToggle=true enabled=true`(DataStore 유지) + FGS 자동 기동. 부팅~첫 조작 사이 발화 0, 이어 가로 몰입 진입 → `arrange done: verified=true residual=0` E2E 재확인 |
| W1-8 | 콜드스타트 | ✅ | 몰입 재생 중 접근성 off→on → 스냅샷 로그만, 신호 전이·발화 0(20s 관측) |
| W1-9 | 세로 영상(직캠) | ✅ D17 예측 재현 | 「23차」 절 |
| W1-10 | 넷플릭스 술어 | ✅ | 재생 중 FULLSCREEN 판정 후 `reason=not-auto-target pkg=com.netflix.mediaclient`. `usages=[1,1]` 다중 재생 관측 |
| W1-11 | 메인 스레드 A/B | ✅ | 「23차」 절 |

### ⚠ 신규 결함 #31 — 홈 경유로는 자동 트리거 래치가 풀리지 않는다

**증상**: 배치→해제→**홈**→유튜브 복귀→가로 몰입 진입 엣지에서 `reason=latched`(설계서 「홈→복귀→재발화 1회」 명세 불성립). **원인(소스 확정)**: `ArrangerAccessibilityService.onAccessibilityEvent` 는 `pkg !in EXCLUDED_FOREGROUND_PACKAGES` 로 거른 뒤에야 `autoLedger.onForeground(pkg)` 호출(:338-346)하는데 `EXCLUDED_FOREGROUND_PACKAGES` 에 `com.sec.android.app.launcher` 가 포함(:2675-2681) — 홈 이동에서 래치 해제 조건이 호출되지 않는다.

**분리 실험**: 배치→해제→홈→복귀→몰입 = `reason=latched`(재발화 0) / 배치→해제→**Chrome**(비제외)→복귀→몰입 = **재발화 1회**. 동일 조건 2회 반복 동일 결과(캠페인 초반 1회는 우발적 비-제외 패키지 이벤트로 발화 — 현재 동작은 비결정적).

**영향**: fail-safe 방향(오발화 아닌 미발화). 탈출구(버블 탭)는 살아 있어 기능 불능 아님. **수정 방향**: `EXCLUDED_FOREGROUND_PACKAGES` 는 세션 중 Recents/런처 전환의 `lastForegroundPkg` 오염을 막는 목록이라 제거 금지(2026-07-25 근거, 함정 #7) — 래치용 포그라운드 신호를 `lastForegroundPkg` 추적과 **분리**해야 한다. → **22차에서 수정 완료**(아래).

### 기기 잔여 상태 (21차 종료)

사용자 토글 켜짐(기본값 꺼짐, 필요시 버블 롱프레스 메뉴로 끌 것) · 회전 `wm user-rotation lock 0`+`user_rotation 0`(세로)로 복원 · 미디어 볼륨 `STREAM_MUSIC=7` 복원 · 유튜브 자동재생 켜짐(측정 편의) · 재부팅 1회 수행(W1-7, 직후 USB 재열거 실패 → 케이블 재연결로 복구).

---

## 22차 — 결함 #31 수정 + P-1 회귀 발견·재수정 (2026-08-01, 같은 세션)

### 한 줄 결론

**#31 은 「런처를 래치 해제 신호로 재허용」 하나로는 못 고친다** — 1차 수정이 곧바로 P-1 루프를 회귀시켰다. 최종 해법 = **래치의 종류를 구분**(`AutoTriggerLedger.latchSticky`). 시간창은 쓰지 않았다(D2 가 이미 기각).

### 1차 수정과 그 회귀 (실측)

1차 = `ForegroundSignalPolicy` 신설(추적/해제 신호 분리)+홈 런처를 해제 신호로 재허용+세션 가드. `#31` 자체는 고쳐졌으나(`auto latch released: foreground=launcher`→재발화 1회), **분할 해제에서 P-1 루프가 되살아났다**:

```
07:02:56.698 fullscreen signal: FULLSCREEN -> NOT_FULLSCREEN appFull=1 topBars=1
07:02:57.004 auto latch released: foreground=com.sec.android.app.launcher   ← 방금 건 재래치가 풀림
07:02:59.662 fullscreen signal: NOT_FULLSCREEN -> FULLSCREEN appFull=1 topBars=0
07:03:02.675 fullscreen auto-arrange trigger: target=com.google.android.youtube   ← 즉시 재발화
```

원인: 분할 해제 시 One UI 가 전환 중 홈 런처를 잠깐 노출 — `PanelActivity.onDestroy` → `onSplitDismissed()` 가 재래치를 건 0.3초 뒤 그 런처 이벤트가 도착해 재래치를 푼다(`dismissInFlight` 는 그 시점 이미 false 라 세션 가드가 못 덮음). **시간창(grace period)은 해법이 될 수 없다**(D2 가 「해제→재진입 간격은 사용자 페이스라 창 크기를 정할 근거가 없다」로 이미 기각, ADR-2 위반).

### 최종 해법 — 래치 2종 (`AutoTriggerLedger.latchSticky`, 순수 도메인)

`onSplitDismissed()` 가 거는 래치는 **sticky**(홈 런처로 안 풀림). `onAutoFired()` 가 거는 래치는 non-sticky(홈으로 풀림). 판정 전부가 도메인 안 → JVM 테스트로 동결(서비스 신규 상태 플래그 0개).

### 실기기 재검증 — 4행 전부 통과

| # | 경로 | 래치 | 기대 | 실측 |
|---|---|---|---|---|
| 1 | 자동 배치 → **홈** → 복귀 → 몰입 | non-sticky | 재발화 1회 | ✅ `latch released: foreground=launcher` → `arrange done … trigger=FULLSCREEN_AUTO` |
| 2 | 자동 배치 → **분할 해제** → 전환 중 런처 블립 | sticky | 재발화 0 | ✅ `latch released` 0건, `reason=latched`, 발화 0건 |
| 3 | 해제 → **Chrome** → 복귀 → 몰입 | sticky | 재발화 1회 | ✅ `latch released: foreground=chrome` → `arrange done …` |
| 4 | 해제 → **홈** → **같은 앱** 복귀 → 몰입 | sticky | 재발화 0 | ✅ 홈 왕복 2회, `latch released` 0건·발화 0건·`reason=latched` 2회 |
| — | 4행의 탈출구: 버블 탭 | — | 수동 배치 성공 | ✅ `trigger=MANUAL` → `arrange done … trigger=MANUAL`(R7 생존) |

4행은 **의도된 비대칭**이다 — 분할 해제는 "이 배치를 원하지 않는다"는 명시적 신호(D2)이므로 같은 앱 복귀만으로 자동화를 재신뢰하지 않는다. 탈출구는 버블 탭.

### 부수 관측 (22차)

자동 배치 성공 직후에도 진입 경로(Recents)의 런처 잔여 이벤트가 1.7초 뒤 도착해 non-sticky 래치를 풀지만, 분할 활성 중엔 진입 엣지가 없어 무해(해제 시 sticky 로 재래치). 자동 세션 실패 시엔 같은 잔여 이벤트가 서킷브레이커를 갉을 수 있어 `autoRecoveryInFlight`(2.5s) 가드가 그 창을 덮는다(관측된 지연 1.7s < 2.5s). `verified=true residual=212 adjusted=true` 1회 관측(재생 중 콘텐츠 변화로 보정 후에도 허용치 초과, F9 부기 경로).

`verified=false residual=null` 로 끝난 자동 세션 3회 — 짧은 간격 연속 세션 구간에 몰려 `takeScreenshot()` 레이트 리밋(함정 #3)으로 확증 측정 실패로 추정(배치 결과는 육안상 정상). **합치(consensus) 게이트가 오염된 pre-measure 를 실제로 구제**: 4:3 필러박스 프레임에서 `preMeasure conf=0.54, aspect=1.2013` 시작 세션이 진입 후 `RAW_DISAGREE → source=CACHED aspect=1.7777778` 채택, 상단 페인이 정확히 `0,0,2184,1229` 로 떨어짐(DESIGN_12 실동작 확인). 유튜브 세로 전체화면(회전 잠금 세로 시 "전체화면" 버튼이 1968×2184 몰입으로 진입)은 술어 FULLSCREEN 이며 게이트5 가 차단. 넷플릭스는 로그인 상태 재생 진입 시 `PlayerActivity` 가 전면·몰입(FULLSCREEN).

### 기기 잔여 상태 (22차 종료)

분할 해제 · 홈 화면 · 세로 회전. 사용자 토글은 계속 켜짐(기본값은 꺼짐).

---

## 23차 — W1-9·W1-11 소화 + 열축 판별자 오탐 발견 (2026-08-01)

### 한 줄 결론

W1 잔여 2항목을 닫았다. **W1-11 은 무해(0:0)** 로 통과, **W1-9 는 D17 이 예측한 「세로 영상 61% 축소」를 정확히 재현**했다. 그 과정에서 **v1.5 D17 게이트가 쓰려던 열축 판별자가 앰비언트 조명 앞에서 0 을 낸다**는 것을 픽셀 단위로 확인했다 — 이 차수의 가장 값어치 있는 산출물. **코드 변경 0.**

### 전제

앱 = HEAD `debdb8a`. APK 07:11 빌드·설치 07:13·`assembleDebug` UP-TO-DATE·워킹트리 clean → 설치본=HEAD 소스 확정(21차의 「구버전 설치본」 함정 재발 방지). 기기 = `OPENED`(내부 1968×2184 ON), 가로=`wm user-rotation lock 1`. 사용자 토글 켜짐(22차 종료 상태). DoD 3종 비차단 재확인: 테스트 **372**(`--rerun` 강제, failures 0·errors 0·skipped 0)·assembleDebug·lintDebug(신규 0, baseline 무변경).

### W1-9 세로 영상(직캠) 자동 발화 — ✅ D17 예측 재현

대상 = 9:16 세로 업로드 영상, 가로 전체화면 2184×1968.

```
fullscreen signal: NOT_FULLSCREEN -> FULLSCREEN screen=2184x1968 appFull=1 topBars=0
fullscreen media probe: playing=true usages=[1]
fullscreen auto-arrange trigger: target=com.google.android.youtube
startArrange: target=com.google.android.youtube trigger=FULLSCREEN_AUTO source=active-window
measure[pre/rows]: 밴드 없음 residual=0px
arrange decision: aspectSource=CACHED aspect=1.7777778 placement=BOTTOM
                  placementSource=LAST_SUCCESS dividerCenterY=732 preMeasure=none
verify: residualRows=0px residualCols=0px
arrange done: verified=true residual=0 adjusted=false trigger=FULLSCREEN_AUTO
auto arrange result (토스트 억제): 배치 완료 · 잔여 0px
```

행축 pre-measure 가 세로 영상에서 밴드를 못 찾고(`밴드 없음`) 캐시된 16:9 로 계획 → 하단 페인이 16:9 기준으로 잡혀 그 안에서 9:16 영상이 다시 필러박스된다.

| 구간 | 영상 표시 영역 | 면적 |
|---|---|---|
| 발화 전(가로 전체화면) | 1107 × 1968 | 2,178,576 px² |
| 발화 후(하단 페인 안) | 689 × 1228(육안 실측) | 846,092 px² |

**면적비 0.388 = 61.2% 축소**(설계서 R3 「61% 작아진다」와 일치). 앱은 `verified=true residual=0` 으로 성공을 보고한다. 되돌리기 경로 = 버블 롱프레스→「분할 해제」(D20 안내 카드). 토스트 문구 = `배치 완료 · 잔여 0px`(화면엔 안 뜸, D19 자동 세션 억제 — logcat 에만 `(토스트 억제)`).

**유도 절차**: 유튜브 검색의 세로 썸네일은 Shorts 플레이어로 열려 전체화면 버튼이 없어 유도 불가 — 「세로 직캠」 검색 → 재생목록 진입 → 칩 필터 「동영상」(Shorts 배제) → 컨트롤 표시 탭 → 전체화면 버튼. 컨트롤은 ≈2.2s 만에 숨으므로 표시 탭과 전체화면 탭을 **같은 `adb shell` 호출 안에서 연속 실행**해야 한다.

### ⚠ 신규 사실 — 열축 필러박스 판별자가 앰비언트 조명에서 0 을 낸다

W1-9 세션의 `verify: residualCols=0px` 는 오탐이다(같은 화면 좌우 띠 육안·측정 모두 각 ≈745px). 원인을 픽셀로 특정(`adb exec-out screencap -d <inner-id>` raw):

| 지점 | RGB | 휘도 | `darkLuma ≤ 24` |
|---|---|---|---|
| 좌측 띠(200,1000) | 57,49,52 | ≈52 | ❌ |
| 좌측 띠(400,1600) | 57,49,52 | ≈52 | ❌ |
| 우측 띠(1750,300) | 66,55,59 | ≈60 | ❌ |
| 영상 내부(1100,1000) | 129,138,165 | ≈139 | — |

유튜브 앰비언트 조명이 띠를 영상 색으로 물들여 휘도 52~60 — `ScreenshotSampler` 기본 `darkLuma=24` 를 넘어 colDarkRatio≈0, `DEFAULT_DARK_ROW_THRESHOLD=0.97` 미달 → `residualBars()` 가 `(0,0)` 반환.

**v1.5 D17 게이트 함의 — 새 판별자를 만들 필요가 없다.** `LetterboxDetector.detectHybrid()` 는 이미 순흑 실패 시 `adaptiveDetect()` 로 폴백하고 `ADAPTIVE_MAX_BAR_LUMA=90` 이 실측 52~60 을 덮는다. `Bitmap.toPillarboxScan()` 은 `rowMeanLuma`/`rowLumaVariance` 를 이미 채운다(`ScreenshotSampler.kt:200-205`). 빠진 곳은 하나 — verify 가 부르는 **`residualBars()`(`LetterboxDetector.kt:157`) 에만 적응형 폴백이 없다.** 최소 수정 = 여기에 `detectHybrid` 와 같은 폴백을 다는 것(PROGRESS §C 가 「미구현」이라 적어 둔 항목은 실은 「미연결」). v1 은 로그 보고만 한다는 DESIGN_12 §3.4/§7 결정은 유지.

### W1-11 메인 스레드 A/B — ✅ 증가 없음

절차: 유튜브 가로 전체화면 + 자동 래치가 걸린 상태(자동 발화 0)에서 버블 탭 수동 배치→종료 대기→롱프레스 메뉴 「분할 해제」를 1사이클(≈10s)로 자동 토글만 바꿔 각 20회.

| 암 | 세션 | `ENTRY_STEP_FAILED` | 그 외 `Failed` | `verified=true residual=0` | trigger |
|---|---|---|---|---|---|
| 자동 **ON** | 20 | **0** | 0 | 20/20 | `MANUAL`×20 |
| 자동 **OFF**(대조군) | 20 | **0** | 0 | 20/20 | `MANUAL`×20 |

**양방향 증거**: OFF 상태에서 창 churn(`APP_SWITCH`→`BACK`)을 일으키면 `fullscreen signal` 0건, ON 으로 되돌리고 같은 churn 을 주면 2건 — OFF 암은 판정 코드가 한 줄도 안 돈다(W1-6 「최전방 선차단」과 같은 경로). → **R6(메인 스레드 IPC 증가)는 40세션 표본에서 관측 가능한 열화를 만들지 않는다**(표본은 전부 `EntryRecipe.DRAG`, `DividerPopupRotator` 를 타는 UNRESIZEABLE 경로는 미포함).

### 부수 관측 (23차)

분할 해제 후 유튜브는 스스로 가로 전체화면으로 복귀하고(`dismissSplit: 성공`→재래치→0.2s 뒤 전이) 이어지는 진입 엣지는 sticky 래치에 막혀 `reason=latched`(22차 4행 실동작 재확인, 40사이클 내내 자동 발화 0). 버블 롱프레스 메뉴는 분할 활성/비활성에서 항목·좌표 동일. **`adb exec-out screencap` 은 폴드에서 `-d <display-id>` 필수**(생략 시 `[Warning] Multiple displays were found…` 2줄이 stdout 에 섞여 PNG 가 깨진다. 내부 화면 id=`4630946449689556883`, 커버=`4630946872173396372`, `dumpsys SurfaceFlinger --display-id` 로 조회).

### 기기 잔여 상태 (23차 종료)

사용자 토글 켜짐(캠페인 전 상태로 복원 완료, 기본값은 꺼짐) · 분할 해제됨, 유튜브 가로 전체화면 재생목록 재생 중, 자동 래치 sticky · 회전 `wm user-rotation lock 1`(가로 고정) **미복원**(세로로 되돌리려면 `adb shell wm user-rotation lock 0` 또는 `wm user-rotation free`).

---

## 24차 — AAA 품질 캠페인 실기기 검증 (2026-08-01, adb 주도)

### 한 줄 결론

AAA 캠페인이 남긴 실기기 확인 10항목 중 **8항목 검증 완료(전부 PASS, 단 #5 는 조건부)**, 2항목(TalkBack 낭독계)은 **adb 로 유도 불가**로 [미검증] 유지. 그 과정에서 **기존 결함 2건을 새로 발견**했다 — 메뉴 경유 배치가 기존 분할 위에서 `isSplitActive` 오판정으로 실패(재현 2/2), 가로 상하분할의 실제 최소 페인이 `SplitPlanner` 가정(181px)과 다름(563px). 둘 다 캠페인 이전 코드에도 있던 것으로 확인 — **회귀 아님.**

**검증 단계는 코드 변경 0 으로 마쳤고**, 이후 사용자 승인 아래 **결함 ① 을 같은 세션에서 수정·재검증했다**(아래 「결함 ① 수정」 절, 실기기 3행 통과). 결함 ② 는 미수정.

### 전제

앱 = HEAD `da11fed`, 워킹트리 clean. `testDebugUnitTest`·`assembleDebug`·`lintDebug` 전부 UP-TO-DATE(커밋 상태 그대로) 후 `installDebug`. 기기 = SM-F966N, Android 16, `OPENED`(내부 1968×2184), **density override 360(2.25) → 875×971dp**(기본 제원 절과 일치). 접근성 재활성화 완료(함정 #6).

### 결과 요약

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | 메뉴 행 탭 → 배치 1회(exit 애니메이션 개입) | ✅ PASS | `startArrange` 1회·busy 경고 0·`arrange done residual=0` |
| 2 | 애니메이터 배율 0 | ✅ PASS | 동일. 액션 유실/중복 경고 0 |
| 3 | TalkBack 롱클릭 메뉴 진입(F1) + 토글 낭독 | ⬜ [미검증] | adb `input` 이 TalkBack 터치 탐색을 우회 — 유도 불가 |
| 4 | 패널 메모 좌상단 무음 정지 소멸(F6c) | ⬜ [미검증] | 위와 같음(선형 탐색 낭독 청취 필요) |
| 5 | 버블 링 55% 밝은/어두운 영상 위 시인성 | ⚠ 조건부 | D16 개선 실증. 단 유휴 알파에서 3:1 에 0.04 미달 |
| 6 | 패널 IME(하단 배치 MEMO) | ✅ PASS | 창 팬 후 필드 273dp 가시, 입력·복귀 정상 |
| 7 | 시계 계수 0.26 실측 | ✅ PASS | 3점 스윕, 글리프/em 비 0.772~0.782 일치 |
| 8 | 온보딩 커버 화면 폭 | ✅ PASS | 411.43dp×960dp 전 섹션 무결 |
| 9 | 아이콘 22dp 실렌더 | ✅ PASS | 프레임 49px, 라벨 좌단 예측 714 vs 실측 715 |
| 10 | F4 이연 실행 | ✅ PASS | 이연→복원 실행, 액션 정확히 1회 |

### #9 아이콘 22dp 프레임 — 실렌더 픽셀 검증

메뉴 스크린샷에서 행별 잉크 런을 뽑아 측정(density 2.25 → 22dp=49px, 간격 14dp=31px).

```
행 밴드 12개(구분선·섹션 라벨 제외) 글리프 폭 31 / 35 / 36 / 37px  → 전부 49px 프레임 내
글리프 좌단 641 / 642 / 644   (49px 프레임 좌단 635 기준 중앙 정렬과 일치:
                               37px→635+6=641, 31px→635+9=644)
라벨 좌단 715 / 716 / 719     (예측 635+49+31 = 714, 오차 ±1 = 반올림)
```

**판정**: 22dp 프레임과 14dp 간격이 픽셀 단위로 성립. 글리프는 프레임을 넘지 않고 중앙 정렬된다.

### #7 시계 계수 0.26 — 창 높이 스윕 3점

`ClockWidget`: `band = paneDp × 0.26` clamp[64,96] → `timeDp = min(band, widthFit, heightFit)`.
강제 종횡비로 패널 높이를 바꿔 가며 렌더 글리프를 측정했다.

| 강제 종횡비 | 패널 px | paneDp | 계산 band | 예측 em(px) | 실측 시각 글리프 | 글리프/em |
|---|---|---|---|---|---|---|
| 16:9 (자동) | 725 | 322.2 | 83.78 (선형역) | 188.5 | 146px | 0.7745 |
| 2.35:1 | 1025 | 455.6 | **96.00 (상한 클램프)** | 216.0 | 169px | 0.7824 |
| 1.57 실착지 | 563 | 250.2 | 65.06 (선형역) | 146.4 | 113px | 0.7720 |

글리프/em 비가 세 점에서 0.772~0.782(편차 1.3%)로 일치 → **0.26 계수와 96dp 상한 클램프가 실렌더에서 성립**. 날짜줄도 `dateDp = timeDp × 0.19` 를 따랐다(글리프/em 0.899~0.921, 한글 자형 기준 일관).

**부수 확인**: `CLOCK_BAND_MIN_DP=64` 는 paneDp<246dp(=553px)에서만 구속되는데, 아래 「최소 페인」 발견으로 **가로 상하분할에서는 도달 불가**하다. KDoc 이 예로 든 「147dp(4:3) 창」은 이 기기 가로에서 만들어지지 않는다.

> PROGRESS 의 항목명은 「시계 계수 **0.26/3.4**」였으나 `3.4` 에 대응하는 상수는 `PanelActivity.kt` 에 **없다**(`git log -S"3.4"` 도 0건). 실재하는 시계 계수는 0.26(밴드)·0.19(날짜)·0.86/1.386(heightFit)·클램프 64/96 이다. 항목명 표기 오류로 판단.

### #5 버블 링 — 「링 55%」는 스트로크 알파(`#8C`)이지 유휴 알파가 아니다

`bubble_background.xml`: 원 `#CC3E5A4B`(80%), **링 `#8CA9C7B5`(55%)**. 유휴 뷰 알파 `BUBBLE_IDLE_ALPHA=0.65` 와 곱해져 화면 위 링 알파 = 0.358.

유휴 상태 실측(영상 위 고정 위치 16프레임, 링 r=61~62 / 배경 r=70~78, 각도 360°):

| 배경 휘도대 | n | 링 대 배경(중앙값) | 원 대 배경(중앙값) | 둘 중 최선 |
|---|---|---|---|---|
| very dark [0,0.02) | 348 | **2.61** | 1.56 | 2.62 |
| dark [0.02,0.06) | 1085 | **2.22** | 1.34 | 2.22 |
| mid-dark [0.06,0.15) | 1096 | 1.69 | 1.11 | 1.71 |
| mid-bright [0.15,0.35) | 495 | 1.24 | 1.49 | 1.56 |
| bright [0.35,1] | 1568 | 1.25 | **2.19** | 2.19 |

**D4 설계 근거("어두운 영상 위에서는 어두운 원보다 밝은 테두리가 더 잘 읽힌다")는 실증됐다** — 어두운 쪽은 링이(2.61 vs 원 1.56), 밝은 쪽은 원이(2.19 vs 링 1.25) 분리를 담당한다. 유휴 알파가 실제로 0.65임도 합성 역산으로 확인(링 실측 (80,108,91) vs 0.65 예측 (78,105,90), 알파 1.0 예측은 (116,145,128)).

합성식 해석 결과:

| 배경 | 링 A=1.00 | 링 A=0.65 | 원 A=1.00 | 원 A=0.65 |
|---|---|---|---|---|
| 순검정 | 5.89 | **2.96** | 2.13 | 1.49 |
| 밝음(200) | 1.69 | 1.39 | 3.19 | 2.03 |

**결론**: 조작 직후(알파 1.0)에는 링이 순검정 위 5.89:1 로 충분하다. 유휴(0.65)에서는 **2.96:1 로 WCAG 1.4.11(비텍스트 3:1)에 0.04 모자란다.** 링이 순검정 위 3:1 을 내는 최소 뷰 알파는 **0.66** — 즉 `BUBBLE_IDLE_ALPHA` 를 0.65→0.66 으로 올리면 경계를 넘는다. D16 의 0.55→0.65 변경은 방향이 옳았고 실제로 개선됐으나 한 눈금 못 미쳤다. 중간 휘도대(0.06~0.35)는 알파 1.0 에서도 1.63/1.37 로 낮은데, 이는 반투명 세이지 톤의 구조적 한계라 알파만으로는 해소되지 않는다(별도 대비 윤곽이 필요 — v1.5 후보).

### #6 패널 IME(하단 배치 MEMO) — PASS

분할(패널 하단 725px) → MEMO 전환 → 필드 탭.

```
mInputShown=true
PanelActivity frame: Rect(0,0-725,2184) -> Rect(835,0-1560,2184)   (창 크기 불변, 팬만 발생)
가로 화면 좌표: 패널 y 1243..1968 -> 408..1133  (필드가 IME 위로 완전히 노출, 가시 273dp >> 120dp 하한)
```

`input text` 입력분이 필드에 표시되고, BACK 으로 IME 를 닫으면 `mInputShown=false` + 창이 원위치(`Rect(0,0-725,2184)`)로 복귀하며 입력 내용이 유지됐다. IME 표시 중 모드 칩이 숨는 것도 KDoc 표(「MEMO/열림/강제 숨김」)와 일치.

### #8 온보딩 커버 화면 폭 — PASS

커버 화면 논리 기하 = `displayId=1, 2520×1080, densityDpi=420` → 세로로 들었을 때 **411.43dp × 960dp**. 내부 화면에 `wm size 1080x2520` + `wm density 420` 으로 동일 dp 기하를 재현해 검증(물리 커버 패널의 컷아웃·라운딩은 미포함).

5단 스크롤 전 구간에서 히어로·필수 설정(진행바)·선택 설정·사용 방법 3단계·경고 2종 전부 정상 렌더. 제목 1줄 유지, 한글 본문 정상 줄바꿈, 상태 칩(「허용됨」/「권한 필요」)이 제목과 같은 줄에 수용, 가로 오버플로·클리핑 0.

### #10 F4 이연 실행 — PASS (3결과 모델 완주)

애니메이터 배율 10배로 퇴장 창을 1300ms 로 늘려 경합을 인위 유도(메뉴 행 탭 직후 `am broadcast ARRANGE`).

```
startArrange: target=com.google.android.youtube trigger=MANUAL source=active-window
removeMenuNow: 배치 세션 진행 중 — 커밋된 메뉴 액션을 복원 시점까지 이연     <- 결과 2
arrange failed: reason=ENTRY_STEP_FAILED trigger=MANUAL
restoreBubbleAfterArrange: 배치 세션 중 커밋됐던 메뉴 액션을 지금 실행(이연 실행)
fullscreen auto toggle: true -> false
```

`fullscreen auto toggle` 발생 횟수 = **1**. 세션이 실패로 끝난 경로에서도 이연 액션이 정확히 1회 실행됨을 확인(버려지지도, 두 번 실행되지도 않음).

### 신규 발견 ① — 메뉴 경유 배치가 기존 분할 위에서 실패 (`isSplitActive` 오판정, 재현 2/2)

분할이 **이미 활성인 상태**에서 버블 메뉴 → 「위로 배치」를 탭하면:

```
startArrange: target=com.google.android.youtube trigger=MANUAL
CheckingSplit -> EnteringSplit(step=1) (event=SplitStateResult(active=false))   <- 오판정
EnteringSplit step=2 attempt 1,2,3 전부 success=false
arrange failed: reason=ENTRY_STEP_FAILED
```

같은 순간 `dumpsys accessibility` 에는 `TYPE_SPLIT_SCREEN_DIVIDER`(title=화면 분할기) 창이 **정상 존재**한다. 원인은 `performDismissSplit` KDoc(`ArrangerAccessibilityService.kt:553-567`)이 이미 문서화한 바로 그 현상 — 풀스크린 스크림 제거 직후 a11y 창 목록 재구축이 비원자적이라 디바이더 창이 늦게 돌아온다.

**그 대응이 `dismissSplit` 에만 적용돼 있다.** `performDismissSplit` 은 `isSplitActive` 자체를 `SPLIT_STATE_SETTLE_TIMEOUT_MS` 까지 폴링하지만, 배치 경로의 `handleQuerySplitState()`(`:1934-1940`)는 **단발 판독**이다.

같은 세션 내 대조 실험이 진단을 확정한다 — **동일한 스크림·동일한 시점에 「분할 해제」 행은 `dismissSplit: 성공`, 「위로 배치」 행은 `active=false`**.

- 분할이 없는 상태에서 같은 메뉴 탭 → `active=false` 가 정상 판정이므로 정상 배치 성공(`residual=0`).
- `am broadcast` 경로(스크림 없음)는 같은 분할 상태에서 `active=true` 로 정상.
- **회귀 아님**: 캠페인 이전(`24afc4a^`) 코드도 `dismissMenu()` 직후 `startArrange()` 를 즉시 호출한다(정착 게이트 없음). AAA 캠페인의 D1/D20 변경과 무관한 기존 결함.

**영향**: 「비율을 바꿔 재배치」·「위/아래 뒤집기」 등 분할 유지 중 재배치가 주 경로인데, 메뉴에서 하면 실패하고 진입 재시도까지 돌아 분할이 깨진다. **최소 수정 = `handleQuerySplitState()` 에 `performDismissSplit` 과 같은 폴링을 다는 것.**

### 신규 발견 ② — 가로 상하분할 최소 페인 = 563px (`minPaneHeight=181` 은 가로에서 틀림)

`SplitPlanner.kt:79` 는 `minPaneHeight = 181, // [측정] 세로 좌우분할. 가로 상하분할 [미검증]`. 그 [미검증] 절반을 실측했다(강제 종횡비 스윕, 가로 2184×1968, 디바이더 14px).

| 요청 종횡비 | 요청 패널 px | 실착지 패널 px | planner clamp |
|---|---|---|---|
| 2.35:1 | 1025 | 1025 (정확) | null |
| 1.60 | 589 | **563** | null |
| 1.55 | 544 | **563** | null |
| 1.40 | 394 | **563** | null |
| 4:3 (1.333) | 316 | **563** (2/2 재현) | null |
| 1.0 | — | 181(접힘 슬라이버, 창은 `Rect(-1592,0-181,2184)`) | HIT_MAX_PANE_CEILING |

즉 One UI 가로 상하분할은 **정상 리사이즈 최소 페인 563px**, 그 아래로 밀면 563 으로 되돌리고, 훨씬 더 밀면 181px 접힘 슬라이버로 점프한다. **181~563px 사이는 사각지대**다. planner 는 181만 알고 있어 이 대역을 clamp 하지 않고(`clamp=null`) 요청을 그대로 내보내며, 도달 실패를 감지하지 못한 채 `arrange done: verified=true residual=0` 을 남긴다(디바이더 도달 여부는 verify 대상이 아니다 — verify 는 잔여 띠만 본다).

**영향**: 메뉴의 **「4:3 구형」 프리셋이 이 기기 가로에서 구조적으로 성립하지 않는다**(필요 패널 316px < 563px). 사용자에겐 성공으로 보이고 로그도 성공이다. 16:9(725px)·2:1(862px)·21:9·2.35:1(1025px)은 전부 여유가 있어 무영향.

### 잔여 [미검증] — TalkBack 낭독계 2항목

`#3`(F1 롱클릭 메뉴 진입 + 토글 낭독)·`#4`(F6c 무음 정지 소멸)는 **adb 로 유도 불가**로 확정. TalkBack 을 켜고(`touchExplorationEnabled=true`, `serviceHandlesDoubleTap=true` 확인) 두 번 탭+홀드를 `input tap`+`input swipe` 로 합성했으나, **adb 주입 이벤트가 TalkBack 의 터치 탐색을 우회해 버블의 터치 리스너로 직행**했다(로그가 접근성 경로 `bubble long-click` 이 아니라 터치 경로 `bubble long-press` 를 냄). a11y 포커스도 버블 창(id=1467, type 2038)이 목록에 있음에도 유튜브 창(1450)에 머물렀다.

단, 판정에 쓸 로그 신호는 준비돼 있다 — **터치 경로는 `bubble long-press`, 접근성 경로는 `bubble long-click(접근성 경로)` 로 서로 다른 줄을 남기므로**, 사용자가 실제 손가락으로 두 번 탭+홀드하면 `adb logcat -s FWFloatingLauncher:V` 한 줄로 F1 이 즉시 판정된다.

### 도구 함정 (이 차수 실측)

- **PIL(Pillow 10.4.0)은 이 머신에 있다** — 종전 메모의 「PIL 없음」은 오기. 픽셀 계측(대비·글리프 박스·링 프로파일)이 전부 파이썬으로 가능하다.
- `input swipe` 로 화면을 가로지르는 긴 드래그는 **시스템 뒤로 제스처로 오인**돼 홈으로 빠진다. 버블 이동은 짧은 구간으로 나눠서 할 것.
- `wm density reset` 은 **물리 밀도(420)로 되돌려 이 기기의 360 설정을 날린다.** 커버 화면 에뮬레이션 후에는 `wm density 360` 으로 명시 복원해야 875dp 기하가 유지된다(`wm density` 는 복원 전까지 override 줄이 없어 정상처럼 보인다 — 버블이 126px 이 아니라 147px 로 렌더되는 것이 검출 신호).
- 패널 모드 칩은 3초 뒤 자동으로 숨는다 — 「탭해서 컨트롤 노출 → 칩 탭」은 **하나의 `adb shell` 안에서** `input tap ...; sleep 0.6; input tap ...` 로 이어붙여야 한다.
- `am force-stop <우리 패키지>` 는 접근성 서비스까지 끈다 — 이후 반드시 `enabled_accessibility_services` 재설정.

### 기기 잔여 상태 (검증 단계 종료 시점)

`wm size` 물리(1968×2184)·`wm density` override 360 복원 완료 · 회전 `user-rotation free` · 애니메이터 배율 3종 1.0 복원 · 접근성 서비스 2종 재활성화 · TalkBack **해제** · 전체화면 자동 토글 **켜짐**(F4 테스트로 꺼졌던 것 복원 확인: `fullscreen auto toggle: false -> true`) · 분할 해제됨 · 버블 실행 중(126px = 56dp×2.25 확인).

### 결함 ① 수정 — 비대칭 정착 술어 (`awaitSettledSplitState`, 같은 세션에서 수정·재검증)

**채택안**: `handleQuerySplitState()` 의 단발 판독을 조건 폴링으로 교체하되, **판정 방향에 따라 비대칭**으로 신뢰한다.

| 판독 | 처리 | 근거 |
|---|---|---|
| `isSplitActive == true` | **즉시 신뢰** | 스크림은 창을 *제거*만 하므로 이 메커니즘에서 false-positive 가 원리상 없다 |
| `false` + APPLICATION 창 ≥ 1 | 확인 1회로 계상, **2회 연속**이어야 false 결론 | 목록 부분 복구(앱 창은 왔고 디바이더는 아직) 구간을 한 틱 흘려보낸다 |
| `false` + APPLICATION 창 == 0 | **무효 표본** — 확인으로 세지 않고 계속 폴링 | 전면 앱이 있는 한 0 은 물리적으로 불가능 = "분할 없음"이 아니라 "판독 불가" |

**`performDismissSplit` 의 폴링을 그대로 복사하지 않은 이유**: 그쪽은 "true 가 될 때까지" 최대 2초를 돈다. 배치 경로에서 **"분할 없음"은 정상적인 다수 경로**(전체화면 영상 → 위로 배치)이므로 같은 방식이면 그 경로마다 타임아웃만큼 지연이 붙는 **새 회귀**가 된다. 비대칭 술어는 양쪽 다 1틱으로 끝난다.

상수(`SPLIT_STATE_READ_*`): 폴링 80ms / 타임아웃 1,200ms / 음성 확인 2회. 80ms 는 실측 복구 시간(~150~170ms)의 절반 이하라 2틱 안에 잡히고, 정상 경로가 **항상** 1틱을 물기 때문에 다른 폴링(150ms)보다 촘촘하게 잡았다. 타임아웃 1,200ms 는 머신 자체의 `splitCheckTimeoutMs=2,000ms` 보다 800ms 짧아 **머신이 먼저 죽는 일이 없다**(설계 시 확인).

**신규 파일 없음.** `DividerLocator.applicationWindowCount()`(기존 `applicationPaneRects` 재사용) 1개 + 서비스 함수 1개 + 상수 3개. `performDismissSplit`·`awaitWindowsSettled`·기존 상수는 무변경. `domain/` 무변경.

#### 실기기 재검증 — 3행 전부 통과 (설치본 pid 30548)

| 행 | 시나리오 | `SplitStateResult` | 판정 소요 | 결과 |
|---|---|---|---|---|
| 1 | **분할 활성** + 메뉴 「위로 배치」 | `active=true` | **81ms** | `arrange done: verified=true residual=0 effective=TOP` (패널 862→725px 재배치) |
| 2 | **분할 활성** + 메뉴 「아래로 배치」(위치 반전) | `active=true` | **83ms** | `swap: converged` → `pane swap 성공` → `effective=BOTTOM`, 창 실제 반전 |
| 3 | **분할 없음** + 메뉴 「위로 배치」 | `active=false` | **82ms** | `EnteringSplit` 정상 진행 → `arrange done: verified=true residual=0` |

행 1·2 는 수정 전 `ENTRY_STEP_FAILED` 로 죽던 바로 그 조작이다. 행 3 은 회귀 확인 — 지연 82ms(1틱)는 사용자 체감 불가 수준이며, 이어지는 진입 시퀀스(수 초)에 비하면 무시할 수 있다.

```
전(24차 발견 시):  CheckingSplit -> EnteringSplit (SplitStateResult(active=false))  → ENTRY_STEP_FAILED
후(수정 후):       CheckingSplit(27388841) -> WaitingDivider(27388922) (SplitStateResult(active=true))
                   arrange done: verified=true residual=0 adjusted=false effective=TOP
```

#### 부수 발견 — 실제로 일한 경로는 「가려진 판독」이 아니라 「미정착 판독」이었다

행 1·2 모두 `blindReads == 0` 이었다. 즉 스크림이 걷힌 뒤 APPLICATION 창은 **이미 돌아와 있었고**, 그럼에도 `isSplitActive` 가 1회차에 false 를 냈다 — `awaitWindowsSettled` KDoc 이 2026-07-25 에 기록한 「앱 창이 먼저, 디바이더가 나중」 부분 복구가 **정확히 재현된 것**이다. 이 구간을 흡수하는 것이 음성 확인 2회 규칙이므로, 설계 근거가 실측으로 확인된 셈이다.

다만 최초 구현은 `blindReads > 0` 일 때만 로그를 남겨, **정작 이 수정이 일한 순간이 로그에 안 남는** 관측성 공백이 있었다("조용한 실패 금지"의 뒷면 = 조용한 성공). 같은 세션에서 로깅 조건을 `blindReads > 0 || negativeConfirmations > 0` 으로 넓히고 두 경로를 구분해 남기도록 보완했다. 보완본 재설치 후 같은 조작에서:

```
awaitSettledSplitState: 판독 정착 후 분할 활성 확인 — 오판정 회피 (가려진 판독 0회 / 미정착 판독 1회, 81ms)
CheckingSplit(27893018) -> WaitingDivider(27893100) (event=SplitStateResult(nowMs=27893100, active=true))
arrange done: verified=true adjusted=true desired=TOP effective=TOP trigger=MANUAL
```

「가려진 판독 0회 / 미정착 판독 1회」가 위 부수 발견을 로그 한 줄로 확증한다 — 앞으로 이 결함의 재발·변형은 이 줄의 두 카운터로 즉시 구분된다(스크림 가림이면 앞자리가, 부분 복구면 뒷자리가 오른다).

#### 이 수정이 건드리지 **않은** 것

`verified=true` 인데 `residual` 이 tolerance(8)를 넘는 관측(이 세션 92·118·120px)은 이 결함과 무관한 별건이며 이미 열린 질문 #21 에 「PROFILE 보정 생략 시 verify 잔여값은 보고 전용」으로 기록돼 있다. `verified` 는 "잔여가 허용 오차 이내"가 아니라 #12 설계의 "최저 신뢰 컴포넌트 판정"이다 — 이번 변경은 그 의미론을 바꾸지 않았다.

### 결함 ② 수정 — 가로 최소 페인 실측 확정 후 `minPaneHeight` 181 → 563

**상수를 바꾸기 전에 측정부터 했다**(CLAUDE.md 함정 #7). 종전 181 의 출처가 「세로 좌우분할」이었고 가로는 주석 스스로 [미검증]이라 밝히고 있었으므로, 가로 값을 새로 재지 않고 바꾸면 틀린 값을 다른 틀린 값으로 바꾸는 셈이 된다.

#### 측정 1 — 강제 종횡비 스윕(패널 목표 px 를 종횡비로 역산해 요청)

| 요청 패널 | 실착지 | | 요청 패널 | 실착지 |
|---|---|---|---|---|
| 800 | 800 | | 580 | 600 |
| 700 | 700 | | 570 | 600 |
| 650 | 650 | | 560 | **563** |
| 620 | 650 | | 520 | **563** |
| 600 | 600 | | 400 | **563** |

**주의 — 이 표에는 두 현상이 섞여 있다.** 620→650, 580/570→600 은 하한이 아니라 **소폭 이동 미반영**이다(각 행이 직전 착지에서 출발하는 연속 스윕이라, 650→620 은 30px, 600→580 은 20px 이동 요청이었고 디바이더가 아예 안 움직였다). 40px 이상 이동은 전부 반영됐다. 그래서 하한은 이 표가 아니라 아래 단일 대이동으로 확정했다.

#### 측정 2 — 단일 대이동으로 하한·상한 확정 (결정적)

| 방향 | 조작 | 결과 | 해석 |
|---|---|---|---|
| 하한 | 패널 900 → **300 요청** | **563** | 한 번의 큰 이동에서도 563 에서 멎는다 = 진짜 하한 |
| 상한 | 1200 요청 | 1200 | 여유 |
| 상한 | **1500 요청** | **1391** | = 1968 − 14 − **563** → **상대 페인도 같은 하한**(대칭) |
| 상한 | 1700 요청 | 접힘 슬라이버 | 창이 `Rect(-1592,0-181,2184)` 로 화면 밖으로 나가고 가시 181px 만 남는다 |

**결론: 가로 상하분할 최소 페인 = 563px(= 250dp @ 2.25), 양쪽 페인 대칭.** 그리고 **종전의 181 은 「최소 페인」이 아니라 접힘 슬라이버의 가시 폭이었다** — 181~563 은 애초에 존재하지 않는 대역이고, 그 대역을 요청하면 플랫폼이 조용히 563 으로 되돌린다. planner 는 181 만 알아 이 대역을 clamp 하지 않았고(`clamp=null`), 도달 실패를 감지하지 못한 채 성공으로 보고했다.

`dividerThickness = 14` 도 가로에서 반복 실측돼 「가로 대칭 가정 [미검증]」을 [측정]으로 승격했다 — 726/740 · 862/876 · 725/739 · **563/577**(아래 수정 후 검증에서 확보).

#### 수정 내용

1. `WindowGeometry.foldSevenLandscape()`: `minPaneHeight` 181 → **563**, 근거를 KDoc 에 인라인 기록.
2. **클램프를 사용자에게 알린다** — 종전 `clampReason` 은 로그에만 남아, 이 기기에서 성립 불가능한 선택이 성공처럼 보였다. `beginSession` 의 결정 지점에서 `!triggerSource.isAuto` 일 때만 토스트한다(자동 트리거는 사용자가 시작한 행위가 아니므로 침묵 — `evaluateFullscreenAutoTrigger` 원칙 승계). 문자열 2종은 **원인 + 눈에 보일 결과**만 말한다(되돌릴 행동이 없을 때 억지 지시를 넣지 않는다):
   - `arrange_clamped_pillarbox` = "이 비율은 이 화면에서 끝까지 맞출 수 없어 좌우에 여백이 남습니다"
   - `arrange_clamped_letterbox` = "이 비율은 이 화면에서 끝까지 맞출 수 없어 위아래에 띠가 남습니다"
3. 도메인 테스트 갱신 + 회귀 방어선 1개 신규(**372 → 373개**).

`SplitPlanner.plan()` 계산 로직은 무변경 — 클램프 의미론(`HIT_MAX_PANE_CEILING` = "영상 창을 못 키워 좌우 여백")은 원래 옳았고 **상수만 틀렸다.**

#### 실기기 재검증

| 프리셋 | clamp | 패널/유튜브 | 토스트 | 판정 |
|---|---|---|---|---|
| **4:3 구형**(1.3333) | **HIT_MAX_PANE_CEILING** | 563 / 577 (디바이더 14) | **표출됨**(좌우 여백) | 수정 전 `clamp=null` + 무고지 |
| 16:9(1.7778) | `null` | 726 / 740 | 없음 | 회귀 없음, 잔여 0 |

```
4:3  → aspectSource=PRESET aspectOverride=1.3333 clamp=HIT_MAX_PANE_CEILING
        arrange done: verified=true residual=164   (계획 예측 좌우 총 329px ≒ 편측 164)
16:9 → clamp=null  arrange done: verified=true residual=0
```

「4:3 구형」의 **물리적 결과 자체는 수정 전후 동일**하다(이 기기 가로에서 4:3 은 원리상 불가능하므로 여전히 좌우 여백이 남는다). 달라진 것은 **앱이 그 사실을 알고, 사용자에게 말한다**는 점이다 — 결함의 표제가 "조용히 미달 배치"였으므로 여기가 실제 개선 지점이다.

#### 부수 관측 [미해결] — 소폭 디바이더 이동(≤30px) 미반영

측정 1 에서 650→620(30px), 600→580(20px), 600→570(30px) 요청이 **디바이더를 전혀 움직이지 못했다**(40px 이상은 전부 반영). `dividerTolerancePx=4` 보다 훨씬 큰 값이라 드래그 생략 경로는 아니고, 제스처는 디스패치됐다. One UI 디바이더 자체의 데드존으로 추정하나 원인 미상. 실사용 영향은 작다 — 프리셋 간 이동은 전부 40px 을 넘고, 미세 보정은 `dividerTolerancePx` 안쪽이라 애초에 시도하지 않는다. 재조사 시 후보 = 제스처 스트로크 지속시간이 거리에 비례해 짧아지는 `scaledDuration`.

### 항목 #5 후속 — 버블 링 스트로크 알파 `#8C` → `#A0` (AA 경계 해소)

24차 검증에서 「유휴 알파에서 순검정 위 링 2.96:1 = WCAG 1.4.11(비텍스트 3:1)에 0.04 미달」로 판정했던 항목을 닫았다.

#### 레버 선택 — 뷰 알파가 아니라 스트로크 알파

두 후보를 합성식으로 비교했다(유휴 알파 0.65 고정, 링/원 각각 대 배경, 대괄호 = 둘 중 최선):

| 배경 | 현행 `0x8C` | 변경 `0xA0` | 최선 변화 |
|---|---|---|---|
| 순검정 | 링 2.96 원 1.49 **[2.96]** | 링 **3.26** 원 1.49 **[3.26]** | **+0.31** |
| 어두움(45) | 링 2.56 **[2.56]** | 링 **2.79** **[2.79]** | +0.23 |
| 중간(110) | 링 1.38 **[1.38]** | 링 1.48 **[1.48]** | +0.10 |
| 밝음(200) | 링 1.39 원 2.03 **[2.03]** | 링 1.33 원 2.03 **[2.03]** | ±0.00 |
| 흰색 | 링 1.82 원 2.44 **[2.44]** | 링 1.75 원 2.44 **[2.44]** | ±0.00 |

**뷰 알파 0.65 → 0.66 안은 기각했다.** 3.02 로 여유가 0.02뿐이고(반올림 한 단위에 무너진다), 원까지 함께 밝아져(1.49 → 1.51) "영상 옆에서 발광을 억제한다"는 유휴 페이드의 목적 자체를 갉아먹는다. 스트로크만 올리면 **원 휘도는 완전히 불변**이다.

밝은 배경에서 링 자체는 근소하게 나빠지지만(1.39 → 1.33) 그 구간은 **원**이 분리를 담당하므로(2.03) 실질 분리도가 변하지 않는다 — **어떤 배경에서도 나빠지지 않고 어두운 쪽만 개선된다.** 이 드로어블이 이미 `0x66(40%) → 0x8C(55%)` 로 같은 레버를 올린 전례가 있고 그 주석이 근거("어두운 영상 위에서는 어두운 원보다 밝은 테두리가 더 잘 읽힌다")까지 적어 뒀으므로, 성격상 그 연장이다.

#### 실기기 픽셀 재계측 — 모델이 아니라 실렌더로 확인

패널 BLACK 모드(순검정)를 버블 아래로 가져와(placement=BOTTOM + 2.35 로 패널을 상단 1025px 로 키움) 무접촉 8초 후 유휴 상태에서 반경 프로파일을 읽었다.

```
r=52..60  (32, 47, 39)   <- 원 채움
r=61      (81, 98, 88)   <- 링 정점
r=62      (74, 89, 81)
r=63      (69, 81, 74)
r>=64     ( 0,  0,  0)   <- 순검정 배경(BLACK 모드)
```

| 항목 | 예측 | 실측 | 판정 |
|---|---|---|---|
| 링 합성 픽셀 | (81.0, 98.6, 88.4) | **(81, 98, 88)** | 채널당 1 이내 일치 |
| 링 대 순검정 | 3.276 : 1 | **3.238 : 1** | **AA 통과**(≥3:1) |
| 원 합성 픽셀 | (32.2, 46.8, 39.0) | **(32, 47, 39)** | 일치 |
| 원 대 순검정 | 1.49 : 1 | **1.497 : 1** | **불변** — 레버 B 의도대로 |

예측과 실측의 0.04 차이는 초록 채널 반올림 한 단위(98 vs 98.6)에서 온다. **원 휘도가 변경 전과 동일**하다는 것이 실측으로 확인됐으므로, "영상 옆 발광을 늘리지 않고 링만 올린다"는 설계 의도가 성립했다.

**남은 약점은 중간 휘도대**(최선 1.48)다. 반투명 세이지 톤의 구조적 한계라 알파로는 해소되지 않으며, 별도 대비 윤곽이 필요하다 — v1.5 후보로 남긴다(PROGRESS 남은 작업 E).

### F 항목(TalkBack) 종결 — 전제 반증 + 플랫폼 제약 발견 → v1 범위 밖

24차의 잔여 2항목(F-1 롱클릭 메뉴 진입 · F-3 메모 무음 정지)을 사용자 손가락으로 시도하는 과정에서 **F1 의 설계 전제가 반증**됐고, 그보다 무거운 **플랫폼 제약**이 드러났다. 결론은 「스크린리더 지원을 v1 범위 밖으로 명시」다(`TASK.md` 「범위 밖」 갱신).

#### 측정 1 — TalkBack 활성 시 앱이 분할을 **생성하지 못한다** (3/3)

```
step2 card-icon matched via selector [ko-content-desc]     <- 카드 아이콘은 정상 탐색
step2: holdThenDrag icon(593,323) -> (1092,150)
EntryStepResult(success=false)   x3
arrange failed: reason=ENTRY_STEP_FAILED
```

카드 아이콘 탐색은 성공하는데 **드래그 제스처만 먹지 않는다.** 3회 중 1회는 **교란 통제 시행**이다 — 처음 두 번은 「TalkBack 을 방금 켠 직후」라 TalkBack 자체 UI 가 전면에 올라온 영향이 섞였을 수 있어, 홈 왕복으로 TalkBack UI 를 정착시키고(포그라운드 = 런처 확인) 유튜브를 새로 띄운 뒤 다시 시도했으나 **동일하게 실패**했다.

대조군: 같은 조작이 **TalkBack OFF 에서는 성공**한다(`arrange done: verified=true residual=0`).

원인은 플랫폼 쪽으로 추정한다 — 터치 탐색이 켜진 상태에서 One UI Recents 가 접근성 주입 제스처(`dispatchGesture` 기반 `holdThenDrag`)를 거부하는 것으로 보인다. 우리 코드로 우회 가능한지는 불명이며 v1 에서 파지 않는다.

#### 측정 2 — TalkBack 을 켜면 **기존 분할이 해소된다** (2/2)

분할을 먼저 만들고 TalkBack 을 켜는 우회도 성립하지 않는다. `settings put secure enabled_accessibility_services` 로 켜는 순간 포그라운드가 바뀌며 분할이 풀린다(`auto latch released: foreground=com.samsung.android.accessibility.talkback` / 두 번째 시행은 `=com.sec.android.app.launcher`). 앱 프로세스는 재시작되지 않았다(pid 12099 불변) — 우리 앱이 죽은 게 아니라 **전면 태스크가 교체된** 것이다.

⚠ 이는 `settings put` 방식의 부작용일 수 있다. 사용자가 볼륨키 단축키나 설정 화면에서 토글할 때도 같은지는 [미검증].

#### 측정 3 — 우리 오버레이는 TalkBack 터치 탐색 **대상 안**이다 (F1 전제 반증)

사용자 손가락으로 버블을 **한 번 탭**한 결과:

| 관측 | 결과 |
|---|---|
| TalkBack 낭독 | **정상적으로 버블을 읽어 줌** |
| 앱 반응(재배치) | **없음** |
| 앱 로그(`FWFloatingLauncher`/`FWArranger`) | **전무** — 터치가 앱에 도달하지 않음 |

즉 `TYPE_APPLICATION_OVERLAY` 인 버블 창도 터치 탐색이 가로챈다. **그런데 같은 세션에서 확장 메뉴는 열렸고 로그는 `bubble long-press`(터치 경로)였다 — `bubble long-click(접근성 경로)` 은 세 세션 통틀어 한 번도 관측되지 않았다.**

평범한 터치가 앱에 닿지 않는데 터치 경로 롱프레스가 발화했다는 것은, TalkBack 이 어떤 제스처에서 터치를 **통과(pass-through)** 시킨다는 뜻이다(두 번 탭 후 유지가 유력하나 **제스처 종류는 미확정** — 사용자가 수행한 제스처를 로그로 특정하지 못했다).

**따라서 F1 KDoc 의 「TalkBack 사용자에게는 메뉴에 도달할 방법이 아예 없었다」는 성립하지 않는다.** 메뉴는 F1 이전에도 도달 가능했다. 주석을 「유일한 경로」 → 「명시적 경로」로 정정했다(코드는 무변경 — 몇 줄이고, 통과 동작은 TalkBack 구현 세부라 보장이 아니며, 라벨 붙은 `ACTION_LONG_CLICK` 은 읽기 메뉴에 노출되는 명시적 계약이다).

#### 범위 결정

v1 은 **스크린리더 지원을 목표로 하지 않는다**(`TASK.md` 「범위 밖」). 근거:

1. 이 앱의 가치가 **시각적**이다 — "영상 주변 검은 띠를 한쪽으로 몰기"는 화면을 보는 것이 전제다. 저시력 사용자의 주 도구인 대비·확대·터치 타깃은 AAA 캠페인에서 이미 확보했다.
2. **배포 경로에 강제가 없다** — ADR-6 이 Play 배포를 범위 밖으로 두고 사이드로딩을 전제한다.
3. **주 기능이 원리적으로 막힌다** — 위 측정 1. 메뉴 접근성을 완성해도 그 메뉴가 하는 일이 완주하지 못한다.

이에 따라 F-1(롱클릭 진입)·F-2(토글 낭독)·F-3(메모 무음 정지)은 **[미검증]이 아니라 「범위 밖」으로 종결**한다. 접근성 관련 코드(라벨·`isCheckable`/`isChecked`·`clearAndSetSemantics`·`setOnLongClickListener`)는 **전부 유지**한다 — 저시력/확대 사용자에게 이득이고 제거가 오히려 회귀 위험이다.

v1.5 에서 재검토한다면 선행 질문은 하나다: **터치 탐색 활성 시 Recents 카드 드래그가 왜 거부되는가, 우회가 있는가.** 그게 풀리지 않으면 나머지 접근성 작업은 의미가 없다.

### 기기 잔여 상태 (24차 최종)

24차 전 작업(검증 → 결함 ①·② 수정 → 링 알파 → F 종결) 종료 시점. 설치본 = 수정 3건 반영본 · 접근성 서비스 2종 활성 · **TalkBack 해제** · 회전 `user-rotation free` · `wm size` 물리(1968×2184) · `wm density` override **360**(물리 420 으로 되돌리면 875dp 기하가 깨진다 — 도구 함정 참고) · 애니메이터 배율 3종 1.0 · 전체화면 자동 토글 켜짐 · 버블 실행 중(126px) · 분할 없음(유튜브 전체화면).
