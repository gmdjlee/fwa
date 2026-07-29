# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.

**최종 갱신:** 2026-07-29 **개선 계획 W3 완료 — 기하 정합성·도메인 불변식(F2 1단계·F1)**. `startArrange` 최상단에 **화면 기하 정합성 가드** 추가 — 종전엔 세로/커버/외부 디스플레이에서 캐시된 `WindowGeometry`(2184×1968)로 계산해 **디바이더를 조용히 틀린 곳으로 옮겼다**. 이제 명시적 미지원(토스트+`Log.w`)으로 떨어진다. 자동 트리거(`evaluateFlexAutoTrigger`)에도 동일 게이트를 넣되 **토스트 없이 로그만**(센서 발화는 "조용한 실패 금지" 원칙 비대상 — 기존 게이트 선례). 판정 자체는 신규 **순수 도메인 `WindowGeometry.matchesScreen()`**(±1% 상대 오차)로 올려 JVM 테스트 대상화 = 계획 대비 편차 1건. F1 = `WindowGeometry.init` 에 `allocatableHeight >= 2 * minPaneHeight` require(클램프 아님 — 계획 불가는 계획 불가로 드러낸다). **DoD 4종 PASS: 테스트 311(+7) · assembleDebug · lintDebug(신규 0, baseline 15 무변경) · 기존 테스트 편집 0(70 삽입/0 삭제)**. 독립 검증 **CONDITIONAL PASS**(조건 = 이 `PROGRESS.md` 갱신 자체, 코드 결함 0) — 변조 3종 전부 사전 예측과 일치, 특히 `&&`→`||` 변조가 1건 포착돼 **테스트 충분성 실증**. **실기기 2항목 [미검증]** = `DEVICE_FACTS.md` W3 절. 상세 = 아래 「개선 웨이브 진행」 절. 직전 = 2026-07-29 **개선 계획 W4 완료 — 테스트 안전망(T1)**. Robolectric 배선(`@Config(sdk=[34])` 필수 — 4.14.1 은 API 36 jar 부재) + 신규 `ScreenshotSamplerTest` **5종**으로 `Bitmap.getPixels`·stride·margin `coerceIn` 경계를 처음으로 커버. 종전 도메인 테스트는 **이미 만들어진** `LetterboxScan` 만 봤고 그 앞단(모든 측정의 입력)은 무방비였다. **W7/P4(`getPixels` stride 최적화)의 선행 조건** — 계획서 P-2 원칙. **DoD 4종 PASS: 테스트 304(+5) · assembleDebug · lintDebug(신규 0, baseline 15 무변경) · 프로덕션 diff 0줄**. 독립 검증 **PASS**(5개 기댓값 전부 수기 재도출 + 미사용 변조로 비-공허성 재확인 6건 FAILED). **실기기 불필요.** 편차 1건 = 계획서의 「리터럴 전치 쌍이 같은 AR」 요구가 기하학적 모순(`raw_pillarbox ≡ 1/raw_letterbox`) → 치수만 전치 + 밴드 독립 배치로 재설계, 검증자 CONFIRMED. 상세 = 아래 「개선 웨이브 진행」 절. 직전 = 2026-07-29 **개선 계획 W2 완료 — Shizuku 셸 하드닝(F3·F4·F5·S2·S3)**. AIDL 을 `String run(String command)` → **`String run(in String[] argv, long timeoutMs)`** 로 전환해 `sh -c` 를 제거(셸 파싱 소멸 = S3 원천 해소) + 신규 순수 도메인 `ShellCommandPolicy` 허용 목록을 **원격 UserService(권한 있는 쪽)에서 강제**(S2) + 원격 `waitFor(timeoutMs)` + 읽기 스레드 분리로 **실효 타임아웃 확보**(F3) + `redirectErrorStream` 단일 스트림(F4) + `ensureBound` 타임아웃 시 `binding=false` 로 래치 해제(F5). **versionCode 2→3**(AIDL 변경 시 UserService 재생성 강제 — 안 올리면 `AbstractMethodError`). **DoD 4종 PASS: 테스트 299(+13) · assembleDebug · lintDebug(신규 0, baseline 15 무변경) · assembleRelease**. 독립 검증 **CONDITIONAL PASS**(조건 = 실기기 세션, 코드 결함 0). **실기기 7항목 [미검증]** = `DEVICE_FACTS.md` W2 절. 상세 = 아래 「개선 웨이브 진행」 절. 직전 = 2026-07-29 **개선 계획 W1 완료 — 보안 차단(S1·S4·F7·F8)**. `probe/`·`ArrangeTriggerReceiver`·`FileProvider` 선언을 `app/src/debug/AndroidManifest.xml` 로 이관해 **릴리스에서 완전 소멸**(병합 릴리스 매니페스트 0건 확인) + `PanelActivity` finish 를 **프로세스 로컬 1회용 토큰**으로 교체(자체 DoS 차단, #28 결함 클래스 구조적 소멸) + 취소 시 Bitmap recycle + 리듀서 `Idle`+`Cancel` 무시. **DoD 4종 PASS: 테스트 286 · assembleDebug · lintDebug(신규 0, baseline 17→15) · assembleRelease**. 독립 검증 PASS. **실기기 4항목 [미검증]** = `DEVICE_FACTS.md` W1 절. 상세 = 아래 「개선 웨이브 진행」 절. 직전 = 2026-07-29 **개선 계획 W0 완료 — 무위험 정리·lint 게이트 도입·아키텍처 테스트** (M7·M4·M2·M6·T2·스타일 4건. `docs/IMPROVEMENT_PLAN_2026-07-29.md` §2 W0). **DoD 3종 PASS: 테스트 285(282+T2 3) · assembleDebug · `lintDebug`(신규 게이트, baseline 17건 필터)**. 실기기 불필요. 상세 = 아래 「개선 웨이브 진행」 절. 직전 = 2026-07-29 **잔여 작업 재편 (코드 무변경, 문서 정리만)** — 착수 목록을 A(사용자 물리 확인) · B(미발동·희귀 경로) · C(v1.5 후보) 로 3분류, Phase 4 행 갱신(19차 종결 반영), 번호 중복 해소. **차단 작업 0 — v1 기능은 전부 구현·검증 완료 상태.** 직전 = 2026-07-28 밤 **#27 종결 — 19차 실기기 캠페인 완료 + 축 B 기각·제거** (테스트 282 PASS · assembleDebug PASS · 제거 빌드 실기기 3/3 done 무회귀). **채택 = 축 A(파괴 제거) + #28**, **기각 = 축 B(소환)**. 근거: ① 소환 카드의 base intent 가 `NEW_DOCUMENT|MULTIPLE_TASK` 로 오염돼 **step3 를 깨뜨림**(전체화면 낙착 → 가드 3회 → `ENTRY_STEP_FAILED`) — 대조군 런처 형태 카드(`flg=0x10000000`)는 정상 낙착 ② **전제 반증**: 카드 0 에서도 배치 **5/5 성공**(`pm clear` 표본 2건으로 신규 설치 교란 배제) — 피커는 앱 목록에서도 「FW Panel」 제공. G4·G5·G6·G7·레버 전부 통과, G2 만 실패. 상세 = `docs/DEVICE_FACTS.md` 19차 절 · 절차 = `docs/CAMPAIGN_19_PANEL_CARD.md`. **[미해결]** 17차 3전멸의 진짜 원인 — 카드 0 은 재현 안 됨. 직전 = **#27 v1 구현(축 A+축 B+#28)** — A1 `finishAndRemoveTask()`→`finish()` 격하 4경로 5곳(커버 해제·dismissSplit instance·자가 가드·EXTRA_FINISH_PANEL onCreate/onNewIntent) + A2 `purgeStalePanelTasks`→`pruneExtraPanelTasks`(판정은 신규 순수 도메인 `PanelTaskPolicy`, MRU 1개 보존) + 반증된 premise 서술 KDoc 정리. **테스트 282 PASS(신규 `PanelTaskPolicyTest` 11개 포함) · assembleDebug PASS**. 실기기 G4~G7 [미검증]. 잔여 = 축 B 소환(B0) · #28. 직전 = **18차 프로브 — #27 원인 확정·설계 확정** (adb 전용·코드 무변경: purge 자충 재현 + G1·G3 통과 + AOSP 사실관계 확정 → 주 수정을 「소환 신설」에서 **「파괴 제거」**로 전환. `docs/DESIGN_27_PANEL_CARD.md` · DEVICE_FACTS 18차 절). 그 직전 = **17차 실기기 캠페인** — Phase 4 구현분 4종 검증 **실질 완료** (adb 주도, DEVICE_FACTS 17차 절): **P4-2 5/5** · **P4-4 4.5/5** · **P4-3 6/7** (신규 수단 `cmd device_state state 0/reset` 에뮬레이션으로 커버 게이트 전 경로 발화 — 핵심 미지수였던 "닫힘 중 패널 finish 의 분할 해소" 에뮬 기준 해소) · **P4-1 7/7** (Shizuku v13.6.0 adb 설치·스타터 활성 — 온보딩 3분기·메뉴 출현·재바인드·유튜브/넷플릭스 팝업 E2E bounds 정확·DRM 재생 Secure layer 팝업 내 구동). 부수: 12차 캐시 폴백 실전 2회 발동 실증. **⚠ 신규 중대 결함 발견**: step3 패널 소환 경로 부재 — 피커 「FW Panel」 노드 = recents 태스크 카드인데 `finishAndRemoveTask`(커버 해제·dismissSplit·purge·자가 가드)가 카드를 제거하면 배치 전체 불능 (node-not-found 3전멸, 재현 4회 + 원인 결정 실험 완료). purge 는 세션 시작마다 자충. 과거 캠페인은 카드 상시 잔존 우연에 의존했음. **P4-3 실배포 전 수정 필수** — DEVICE_FACTS 17차 「신규 결함」 절에 수정 방향 3후보
**직전(P4-1 프로브+구현):** 프로브 F1~F6 게이트 통과·후보 A(Shizuku 셸) 채택(`7e1d912`) → 구현 완료(UserService AIDL + PopupPlanner·StackListParser + startPopup 5단 폴링 + 메뉴 조건부 노출 + 온보딩 카드). 테스트 271·빌드 PASS. 그 전 Phase 4 구현(`9c36905`)·16차·15차 = DEVICE_FACTS 각 절 참조
**현재 Phase:** Phase 4 — **전 항목 구현 + 17차·19차 검증 완료, #27 종결**. 잔여 = 아래 「남은 작업」 A(물리 2건) · B(희귀 경로) · C(v1.5) 뿐. Phase 0~3 완료 확정

---

## 🔖 남은 작업 (2026-07-29 정리 — 재개 지점)

**전제:** Phase 0~4 전 항목 구현 완료. 17차·19차 실기기 캠페인으로 주 경로 검증 완료. **차단 작업 없음.**
아래는 전부 "v1 을 더 단단하게" 하는 잔여이며, 어느 것도 다른 것을 막지 않는다. 착수 순서는 자유.

### A. 사용자 물리 조작 필요 — adb 대체 불가 (실질 유일한 잔여)

| # | 항목 | 현재 상태 | 필요한 조작 |
|---|---|---|---|
| A-1 | **P4-3 항목1·2 물리 접기** | 17차에 `cmd device_state state 0/reset` **에뮬레이션으로만** 커버 게이트 전 경로 발화 확인 | 실제로 기기를 접어 **화면 꺼짐 실상태**에서 커버 자동 해제 발화 확인 |
| A-2 | **P4-1 DRM 육안** | 팝업 창 내 Secure layer 구동은 로그로 확인 (17차 7/7) | 넷플릭스 등 DRM 재생을 팝업 창에서 **눈으로** 정상 렌더 확인 |
| A-3 | (참고) 600ms 재펴기 | **조치 불필요** — 15차 결정으로 미검증 수용 확정 | — |

### B. 미발동·희귀 경로 [미검증] — 자연 발생 대기 또는 인위적 유도

| # | 경로 | 유도 방법 / 비고 |
|---|---|---|
| B-1 | **#28** `performDismissSplit` 3분기 중 "패널 태스크 부재" 분기 | 조건 = `instance==null` ∧ 패널 태스크 부재 ∧ 분할 활성 판정 통과. 희귀 — `am force-stop` + 태스크 스와이프 조합으로 인위 유도 필요 |
| B-2 | **P3-2** dismissSplit 인텐트 폴백 | 조건 = `instance==null` (프로세스 사망 후 분할 잔존). B-1 과 인접 조건 — 같은 세션에서 함께 유도 가능. **W1/S4 토큰 소비 경로와 동일 조건**(`DEVICE_FACTS.md` W1-4)이므로 셋을 한 세션에서 함께 확인한다 |
| B-3 | **#20** 클릭 사이클 cycle-2(a11y) · 스왑 제스처 사이클(cycle-1/2) | 10차 이후 미발동. `mech` 로그 상시 계측 중 — 자연 발생 대기. 3시도 전멸 재발 시 FORENSIC viewId 로 특정 후 스텝 되감기 재검토 |
| B-4 | **#19** 스왑 실패 시 회전×2 폴백 | 누적 12+ 세션 미발동 (PaneSwapper 가 항상 수렴). 검증 기회 확보 대기 |
| B-5 | **`PanelTaskPolicy`** `ActivityManager.appTasks` MRU-first 순서 계약 | 공개 API 로 `lastActiveTime` 조회 불가 → 플랫폼이 전부 0 전달, 순서에 위임. 이 계약 자체가 [미검증] — prune 오제거 시 재조사 |

### C. v1.5 후보 (v1 범위 밖, 결정 완료 — 근거는 각 항목 참조)

- **#12 축적분**: BOTH_AXES_BARS 시 보정 생략(G1 드리프트 실측) · 적응형 residual(글로우 필러박스 블라인드 G5 실측) · 비-16:9 콘텐츠 합치·캐시 실측 · flex 게이트2↔startArrange TOCTOU(이론상, DEVICE_FACTS 기록)
- **P3-5 축적분**: 포그라운드 안정성 윈도(폴드 전환 중 월렛 quick 카드가 event-tracked 오염 → 게이트5 통과 실측) · 재열기 멈칫 Done-후 분할 잔존(수동 해제로 복구, 키가드 게이트는 정당 사용례 훼손으로 기각) · 각도 대역 경계값 실측
- **`ScreenshotSampler` 1px 입력 크래시** [W4 발견, 미수정]: `toLetterboxScan:25` / `toPillarboxScan:113,115` 의 `coerceIn(0, w/2 - 1)` 은 `w==1`(또는 `h==1`) 이면 `coerceIn(0, -1)` = **빈 범위 → `IllegalArgumentException`**. 전면 스크린샷·분할 페인에서 1px 은 도달 불가라 v1 차단 아님. W7/P4 가 이 함수를 손댈 때 `coerceAtMost` 등으로 함께 정리한다. 테스트 ⑤ 는 의도적으로 4×4·2×2 까지만 커버(`w/2-1 ≥ 0` 경계)
- **F2 2단계 — 실화면 기반 `WindowGeometry` 생성** [W3 에서 v1.5 로 이관 확정]: W3 은 **가드만** 넣었다(불일치 시 명시적 미지원). 근본 수정은 `WindowGeometry` 를 `screenRect()` 에서 만들고 `dividerThickness`/`minPaneHeight` 만 실측 상수로 남기는 것인데, **선행 조건 = 세로 분할의 디바이더 두께·최소 페인 높이 실측**이다(현재 값은 「세로 좌우분할 측정 → 가로 대칭 가정 `[미검증]`」). 이는 코드 작업이 아니라 **프로브 측정 작업**이라 v1 범위 밖 — 프로브는 W1/S1 이후에도 개발 빌드에 남아 있으므로 그대로 쓸 수 있다
- **열린 질문 잔여**: #1 `rowStride` 축소의 역산 정밀도 영향 · #3 wavve 패키지명 · #5 `ADAPTIVE_*` 상수 실기기 재검증 · #6 셀렉터 다국어(EN) 안정성 · #14 BOTTOM 배치 시 스왑 생략 가능성

### D. 미해결 (원인 미상 — 재발 시 재조사)

- **17차 step3 3전멸의 진짜 원인**. 19차에 「카드 0」 가설은 **5/5 배치 성공으로 반증·폐기**(`pm clear` 표본 2건이 신규 설치 교란 배제). 재발 시 후보 = 피커 화면 상태 · purge 타이밍 · 오염 카드 잔존. 관련 기록 = 열린 질문 #27, DEVICE_FACTS 17차·19차 절

---

## 🌊 개선 웨이브 진행 (2026-07-29 리뷰 대응)

계획서 = `docs/IMPROVEMENT_PLAN_2026-07-29.md` · 원 리뷰 = `docs/CODE_REVIEW_2026-07-29.md`
전 8웨이브(W0~W7) / 23항목. 각 웨이브 종료 = CLAUDE.md DoD 충족 = 언제든 중단 가능한 지점.

| 웨이브 | 상태 | 실기기 |
|---|---|---|
| **W0** 무위험 정리·게이트·아키텍처 테스트 | ✅ **완료** | 불필요 |
| **W1** 보안 차단 (S1·S4·F7·F8) | ✅ **완료** | 0.5회 **[미검증]** |
| **W2** Shizuku 셸 하드닝 (F3·F4·F5·S2·S3) | ✅ **완료** (`1d1e0bd`) | 1회 **[미검증]** |
| **W3** 기하 정합성·도메인 불변식 (F2 1단계·F1) | ✅ **완료** | 0.5회 **[미검증]** |
| **W4** 테스트 안전망 (T1) | ✅ **완료** (`0f53af2`) | 불필요 |
| W5 구동부 안정화 (F6·F9) | ⬜ 미착수 | 1회 |
| W6 세션 상태 캡슐화 (M1 1단계) ⚠ 최대 위험·단독 커밋 | ⬜ 미착수 | 1회 |
| W7 성능·중복 정리 (P1·M3·P2·P4) | ⬜ 미착수 | 1회 |

### W3 완료 내역 (기하 정합성·도메인 불변식 · 실기기 2항목 [미검증])

- **F2 1단계 가드** — 서비스가 들고 있는 `geometry` 는 `WindowGeometry.foldSevenLandscape()`(2184×1968) **상수**인데, 실화면이 그와 다르면(세로 방향 · 커버 디스플레이 · 외부 디스플레이) 그 기하로 계산한 `dividerCenterY` 를 실화면에 그대로 적용해 **조용히 틀린 곳으로 디바이더를 옮겼다**. `startArrange` **첫 문장**(세션 상태 `machineState`/`sessionInFlight` 를 건드리기 전, busy 가드보다도 앞)에 `geometry.matchesScreen(screenRect())` 검사를 넣어 토스트 「이 화면 방향/디스플레이는 아직 지원하지 않습니다」 + `Log.w` 로 떨어뜨린다. **가로 경로 영향 0** — 가로에서는 항상 통과한다
- **자동 트리거는 로그만** — `evaluateFlexAutoTrigger` 게이트 체인의 **busy 게이트와 split-active 게이트 사이**에 동일 검사를 넣되 토스트 없이 `reason=geometry-mismatch` 로그 + `flexPolicy.disarm()`. 「조용한 실패 금지」 는 *사용자가 시작한 행위*의 원칙이고 센서 발화 자동 트리거는 비대상이라는 기존 5개 게이트의 선례를 그대로 따른다. 부수로 바로 아래 `isSplitActive(safeWindows(), screenRect())` 가 새 `screen` 지역변수를 재사용하도록 정리(중복 호출 제거, 동작 등가 — 두 지점 사이에 suspend 지점 없음 + `Dispatchers.Main.immediate` 단일 스레드라 끼어들 경로 없음을 검증자가 확인)
- **`startPopup` 은 대상 아님** — `PopupPlanner.plan(screen.width, screen.height, ...)` 이 이미 실화면을 직접 읽으므로 애초에 이 결함에 노출되지 않는다. KDoc 에 근거를 명시해 "왜 여기만 빠졌나" 를 나중에 다시 묻지 않게 했다
- **F1 불변식** — `WindowGeometry.init` 에 `require(allocatableHeight >= 2 * minPaneHeight)`. **클램프가 아니라 require 인 이유**: 음수 `panelH` 를 0으로 클램프하면 "말이 안 되는 계획"을 조용히 만들어낸다 — 계획 불가는 계획 불가로 드러내야 한다. 기존 `SplitPlannerTest` 의 모든 기하 조합(`minPane=800`, `divider=24,minPane=200` 등)과 Fold 7 실측값(`1954 >= 362`)이 여유롭게 통과해 **기존 테스트 편집 0건**
- **신규 테스트 7종** — F1 3종(경계 정확히 통과 `986 == 2×493` · 1px 부족 시 예외 · Fold 7 실측 통과) + F2 4종(정확 일치 · 톨러런스 안 `2205×1987` · **폭 단독 위반과 높이 단독 위반을 분리 단언** · 세로 전치 `1968×2184`)
- **DoD 4종 PASS: 테스트 311(304+7) · assembleDebug · lintDebug(신규 0, baseline 15 무변경) · 기존 테스트 순수 추가(70 삽입/0 삭제)**

**계획 대비 편차 1건 — 판정 함수를 서비스 private 이 아니라 `domain/` 으로:**
계획서는 `private fun geometryMatches(screen: IntRect)` 를 서비스 안에 두라고 했으나, `WindowGeometry.matchesScreen(screen, toleranceFraction = GEOMETRY_TOLERANCE_FRACTION)` 멤버로 올렸다.
서비스 private 이면 **톨러런스 경계가 실기기 없이는 검증 불가**한데, 도메인에 두면 JVM 단위 테스트 대상이 되고 `ArchitectureTest` 가 순수성을 기계 강제한다(ADR-4). W2 편차 1번(`ShellCommandPolicy` 를 `domain/` 으로)과 같은 취지이며, 검증자도 "더 나은 설계, 문제 삼을 이유 없음" 판정.

**독립 검증(qa-verifier) 판정 = CONDITIONAL PASS** — 유일한 조건이 **이 `PROGRESS.md` 갱신 자체**였고(계획서 W3 DoD 가 §C 등재를 명시) 코드 결함은 0건. 검증 핵심 3건:
① **변조 3종 전부 사전 예측과 정확히 일치** — (a) `matchesScreen` 무조건 `true` → 2건 FAILED (b) **`&&` → `||`** → 1건 FAILED (c) `require` 무력화 → 1건 FAILED. 특히 (b) 는 "게으른 테스트 세트면 아무것도 안 잡힌다" 를 겨냥한 변조인데 실제로 포착됐다 — **폭 단독/높이 단독 분리 단언이 실효를 갖는다는 실증**. portrait 테스트가 (b) 를 못 잡는 것(두 축 동시 위반이라 `||` 로도 false 유지)까지 사전 예측대로였다
② **가드 배치를 문자 그대로 확인** — F2 가드가 `startArrange` 의 첫 statement 이고 `sessionInFlight = true`·`scope.launch`·busy 체크 **전부보다 앞**, fall-through 없음. flex 게이트는 busy 와 split-active **사이**, `toast()` 호출 0, `disarm()` 이웃과 동일
③ **NUL 오탐을 스스로 폐기** — bash `grep -c $'\x00'` 가 NUL 을 인자에서 잘라 빈 패턴이 되며 전체 라인 수와 우연히 일치하는 **허위 신호**를 냈고, `wc -l` 대조로 이를 탐지한 뒤 Python 바이트 스캔으로 대체해 4개 파일 NUL 0건·제어문자 0건 확정 (W2 에서 발견된 소스 위생 함정의 재발 없음)

### W4 완료 내역 (테스트 안전망 · 실기기 불필요 · 프로덕션 로직 0줄)

- **T1 Robolectric 배선** — `testImplementation(libs.robolectric)`(4.14.1, 카탈로그에 이미 선언돼 있었으나 미사용이던 것을 사용 전환 = M4 「robolectric 미사용」 동시 해소) + `testOptions.unitTests.isIncludeAndroidResources = true`. **`@Config(sdk = [34])` 필수** — Robolectric 이 `targetSdk` 로 android-all jar 를 고르는데 4.14.1 은 API 36 jar 가 없다. 34 가 1차 시도에 통과해 4.16.1 상향은 불필요했다
- **신규 `app/src/test/.../platform/ScreenshotSamplerTest.kt` 5종** — 종전 도메인 테스트는 **이미 만들어진** `LetterboxScan` 을 입력으로 순수 로직만 봤다. 실제 `Bitmap.getPixels`/stride/margin `coerceIn` 경계는 **커버 범위 밖**이었고, 여기가 모든 측정의 입력이다. ① 행축 letterbox → `resolveAspect` 16:9 ② 열축 pillarbox → `resolveAspectPillarbox` 4:3 ③ 전치 쌍 대응 ④ `rowStride ∈ {1,2,4}` raw 불변 ⑤ 초소형(4×4·2×2) 경계
- **이 웨이브의 목적은 W7/P4 의 선행 조건** — P4(`getPixels` 의 `stride` 인자 활용)는 **모든 측정의 입력 함수**를 건드린다. 계획서 P-2 원칙대로 안전망을 먼저 깐다. 테스트 ④ 가 그 직접적 게이트다(stride 를 바꿔도 raw 가 불변임을 기계 보장)
- **DoD 4종 PASS: 테스트 304(299+5) · assembleDebug · lintDebug(신규 0, baseline 15 무변경) · 프로덕션 diff 0줄**

**계획 대비 편차 1건 — 테스트 ③ 은 「리터럴 픽셀 전치」로 만들 수 없다 (계획서 요구가 모순):**
계획서는 "동일 콘텐츠의 전치 쌍이 양축에서 같은 AR" 을 요구했으나, 이는 기하학적으로 불가능하다.
`resolveAspect` 의 raw = (frame 폭)/(content 높이), `resolveAspectPillarbox` 의 raw = (content 폭)/(frame 높이) 인데
이미지를 90도 돌리면 두 항이 서로 자리를 바꾸므로 **`raw_pillarbox ≡ 1/raw_letterbox`** 가 강제된다
(A: 144×270, content 81 → 144/81 vs 81/144 로 실수 대입 확인). 따라서 **프레임 치수만 전치**(144×270 → 270×144)하고
콘텐츠 밴드는 B 안에서 독립 배치해 같은 16:9 를 재현했다. 대신 stride 를 명시적으로 교차 배정(entries 축 1, 교차축 8)하고
**`scanA.width == scanB.width == 144` 를 직접 단언**해 원래 겨냥한 회귀군(`scaledWidth=w/rowStride` ↔ `scaledHeight=h/colStride` 대응 붕괴)을
그대로 포착한다. 검증자 판정 **CONFIRMED**(커버리지 약화 아님)

**독립 검증(qa-verifier) 판정 = PASS** — 워커 주장 전부 재현. 검증 핵심 3건:
① **5개 테스트 기댓값 전부 수기 재도출** — 잘못된 기댓값도 통과한다는 위험을 겨냥. 특히 테스트 ③ 의 `band.height = 256` 은
`sideMarginPct=0.005` 로 `x0=1` 이 되어 x=0 열이 빠지는 것까지 반영된 값이며 off-by-one 우연이 아님을 확인.
테스트 ④ 의 content fraction = 64/192 = 0.333 이 `MIN_CONTENT_FRACTION`(0.25) 을 반올림 의존 없이 상회함도 확인
② **워커가 쓰지 않은 변조로 비-공허성 재확인** — `impliedAspect` 의 분자·분모를 뒤집으니 **6건 FAILED**(신규 3 + 기존 `LetterboxDetectorTest` 3),
사전 손예측과 정확히 일치. 원복 후 `git diff` 공백 + 304/0 재확인
③ **lint 15건 무변경의 이유 확정** — robolectric 의존성 추가가 신규 경고를 안 만든 것은, `NewerVersionAvailable`(4.14.1→4.16.1) 이
**W0 시점부터 이미 baseline 에 있었기** 때문이다(`lint-baseline.xml:148-150`, 위치가 `libs.versions.toml` 버전 선언 라인 — lint 의 카탈로그
신선도 검사는 실제 사용 여부와 무관하게 카탈로그 항목 자체를 스캔한다). baseline 수동 편집 불필요, `updateLintBaseline` 미실행

### W2 완료 내역 (Shizuku 셸 하드닝 · 실기기 7항목 [미검증])

- **S3 + S2 argv 전환** — `IShellExec.aidl` 을 `String run(in String[] argv, long timeoutMs)` 로 바꾸고 `sh -c` 를 폐기했다. **셸 파싱 자체가 사라져** 문자열 보간·작은따옴표 인용 의존(YouTube `Shell$HomeActivity` 의 `$`)이 원천 소멸한다. 그 위에 신규 **순수 도메인 `domain/ShellCommandPolicy.kt`** 허용 목록 — `am` → `{start, stack, task}` 정확 일치(대소문자 구분), argv 크기 `2..16`, NUL 문자 포함 인자 거부(execve 인자 절단 방어). **강제 지점은 원격 `ShellExecUserService.run`** 이고 클라 `ShizukuShell.exec` 의 동일 검사는 fail-fast 용 중복 — 우회 가능한 클라 검사만으로는 보안 통제가 아니다. **목적은 인젝션 방어가 아니라 shell UID(2000) 권한 최소화**(인젝션할 셸이 이미 없다)
- **F3 실효 타임아웃** — 근본 원인은 `binder.run()` 이 **블로킹 바인더 호출**이라 클라 `withTimeoutOrNull` 이 취소를 걸 수 없다는 것(코루틴 취소는 중단점에서만 작동). 따라서 실효 타임아웃은 **원격에만 만들 수 있다**: `process.waitFor(timeoutMs, TimeUnit)` + 초과 시 `destroyForcibly()`. 클라 예산은 `timeoutMs + BINDER_OVERHEAD_MS(2s)` 로 **원격보다 크게** 잡아 원격이 먼저 포기하고 진단 가능한 `-1` + timeout 문자열을 돌려주게 했다. `ensureBound()`(자체 데드라인 3s)는 이 창 **밖**으로 빼 예산 이중 소비를 제거 — 종전엔 3초 바인드가 5초 exec 예산을 잠식했다
- **F3 부수 — 읽기 스레드 분리가 타임아웃의 전제** — 자식이 stdout 을 닫지 않으면 같은 스레드의 읽기가 영원히 블록돼 `waitFor(timeout)` 에 **도달조차 못 한다**. 데몬 스레드(`fw-shell-reader`)로 분리해야 타임아웃이 실효를 갖는다
- **F4 파이프 데드락** — `ProcessBuilder(argv).redirectErrorStream(true)` 로 단일 스트림화 + `outputStream.close()`. 구 코드는 stdout 을 EOF 까지 소진한 뒤에야 stderr 를 읽어, 자식이 stderr 파이프(64KB)를 채우면 상호 대기에 빠졌다. 버퍼는 **읽기·쓰기 양쪽 다** `synchronized`
- **F5 바인드 래치 해제** — `ensureBound()` 타임아웃 경로에 `if (bound != true) binding = false`. 구 코드는 `bindUserService` 가 예외 없이 반환됐는데 `onServiceConnected` 가 끝내 안 오면 `binding` 이 `true` 로 고착돼 이후 모든 호출이 `if (!binding)` 에 걸려 **재바인드를 시도조차 안 했다**(복구 경로 없음). `unbindUserService` 는 부르지 않는다 — 도착 직전 연결을 죽이면 새 실패 모드가 생긴다
- **versionCode 2→3 (필수 부수)** — `Shizuku.UserServiceArgs.version(BuildConfig.VERSION_CODE)` 이 UserService 프로세스 재생성을 결정한다. AIDL 이 바뀌었는데 버전이 같으면 **구 바이너리 재사용으로 `AbstractMethodError`**. W0 에서 이미 1→2 로 올렸고 그 빌드가 기기에 설치돼 있을 수 있으므로, AIDL 이 실제로 바뀌는 이 커밋에서 다시 올렸다
- **부수** `proguard-rules.pro` 에 `ShellExecUserService`·`IShellExec`(+Stub) keep 규칙 — Shizuku 가 리플렉션으로 로드하고 `destroy()` 를 이름으로 호출한다. `isMinifyEnabled=false` 라 현재는 무효, M6 과 같은 취지의 v1.5 대비

**계획 대비 편차 3건 (전부 Advisor 판단으로 계획서에 추가한 것):**
1. **허용 목록을 `domain/` 순수 Kotlin 으로** — 계획서는 위치를 지정하지 않았다. 도메인에 두면 보안 판정이 JVM 단위 테스트 대상이 되고 `ArchitectureTest` 가 순수성을 기계 강제한다. `Array<String>` 대신 `List<String>` 을 받는 것도 도메인 관례(배열 `==` 는 참조 동등성)
2. **`ensureBound()` 를 타임아웃 예산 밖으로 이동** — 계획서에 없던 항목. F3 수정만으로는 3초 바인드가 5초 exec 예산을 잠식하는 문제가 남는다
3. **versionCode 를 다시 올림(2→3)** — 계획서는 "M4 의 1→2 가 이 목적을 겸한다" 고 봤으나, W0 빌드가 이미 기기에 설치됐을 가능성이 있어 AIDL 이 실제로 바뀌는 커밋에서 재상향하는 쪽이 `AbstractMethodError` 를 확정 차단한다

**독립 검증(qa-verifier) 판정 = CONDITIONAL PASS** — 유일한 조건은 계획서가 W2 에 지정한 **실기기 E2E 세션**이며 코드 결함은 0건. 검증 핵심 3건:
① **허용 목록 대조 실험** — `isAllowed` 를 `return true` 로 임시 무력화하니 `ShellCommandPolicyTest` **13중 9 FAILED**(허용 목록 밖 케이스 전부), 원복 후 MD5 일치 확인. 테스트가 장식이 아님을 실증
② **`ensureBound` 이동을 diff 로 확인** — 개정 전엔 `withTimeoutOrNull(timeoutMs)` **안**에 있었고 개정 후 밖으로 나왔음을 실제 diff 로 대조
③ **S2 우회 벡터 3종 차단 확인** — `/system/bin/am`·`./am` 경로 변형은 `ALLOWED[argv[0]]==null` 로 거부(더 엄격), NUL 검사가 argv 전 원소·전 문자 순회, 대소문자/호모글리프는 `equals` 정확 일치로 자연 거부. 추가로 `ArchitectureTest` 가 `walkTopDown()` 디렉터리 순회라 신규 도메인 파일이 자동 포함됨(vacuous-pass 아님)도 확인

**소스 위생 함정 [신규 발견]** — Kotlin 소스에 NUL 문자 유니코드 이스케이프를 쓰려다 파일 기록 과정에서 **raw NUL 바이트(0x00)가 소스에 그대로 박히는** 현상이 발생했다. Read 계열 도구가 NUL 을 공백처럼 렌더해 "오타로 스페이스가 들어갔다" 로 오인하기 쉽다. `c.code == 0` / `0.toChar()` 형태로 재작성해 이스케이프를 소스에서 제거했고, qa 가 변경 파일 8종 전부를 바이트 수준으로 스캔해 잔여 0건 확인. **이 도구 체인에서 파일 내용에 유니코드 이스케이프를 넣을 때 상시 주의** (`DEVICE_FACTS.md` W2 절에도 기록)

### W1 완료 내역 (보안 차단 · 실기기 4항목 [미검증])

- **S1 debug 소스셋 분리** — 신규 `app/src/debug/AndroidManifest.xml` 로 **선언 5종 이관**(`probe.ProbeActivity`·`probe.ProbeAccessibilityService`·`probe.ProbeTriggerReceiver`·`service.ArrangeTriggerReceiver`·`FileProvider`). **Kotlin 소스는 이동하지 않았다** — 매니페스트만. 릴리스 병합 매니페스트에서 5종 **0건**, 디버그에는 5종 전부 확인. adb 트리거 편의 100% 보존. **M5(프로브 릴리스 탑재) 동시 해소** — 릴리스는 접근성 서비스 1개·런처 아이콘 1개로 정상화, 프로브는 개발 빌드에 남아 F2 2단계 측정에 계속 쓸 수 있다
- **S4 finish 토큰** — `EXTRA_FINISH_PANEL: Boolean` → `EXTRA_FINISH_TOKEN` + companion `@Volatile finishToken` / `issueFinishToken()` / `consumeFinishToken()`(1회용). `PanelActivity` 는 파트너 피커 노출 때문에 `exported="true"` 가 불가피한데, 발신자 신원 검증 수단이 없다(`callingActivity` 는 `startActivityForResult` 전용, `referrer` 는 Service 발 `startActivity` 에서 신뢰 불가) → **비밀값으로 검증**. **정상 경로 회귀 0**: 1차 경로는 `PanelActivity.instance.finish()` 라 토큰을 아예 쓰지 않고, 토큰 경로(`instance==null ∧ hasPanelTask()`)는 서비스 인스턴스 위에서 호출되므로 발급·소비가 항상 동일 프로세스. **보너스**: base intent 에 토큰이 박혀도 프로세스가 바뀌면 불일치로 무시 → **#28 「영구 실패 루프」 결함 클래스가 구조적으로 소멸**(단 `hasPanelTask()` 사전 가드는 불필요한 태스크 생성 방지라는 별개 목적으로 유지)
- **F7 취소 시 Bitmap 회수** — `ArrangerAccessibilityService.captureScreen()` · `ProbeAccessibilityService` 2곳에 `else bmp?.recycle()`. 전면 스크린샷 1장 ≈17MB(2184×1968×4B)라 GC 압박이 무시할 수준이 아니다
- **F8 리듀서 `Idle`+`Cancel`** — Cancel 최우선 가드에 `&& state !is ArrangeState.Idle` 추가 + 테스트 1개. 서비스 `cancelArrange()` 가 Idle 을 먼저 걸러 실제 도달은 없었으나, **가드가 리듀서 밖에 있는 것 자체**가 순수 리듀서의 자기완결성 위반이었다
- **부수 개명** `Intent?.requestsFinish()` → `consumesFinishRequest()` + 부작용 KDoc — 토큰을 **소비**하는데 순수 질의처럼 읽혀 이중 호출 함정이 있었다

**계획 대비 편차 3건 (후속 웨이브 참고):**
1. **`ExportedReceiver` 2건은 자동 해소되지 않았다** (W0 편차 2번의 연장). `lintDebug` 는 debug 병합 매니페스트를 보므로 리시버는 여전히 존재하고 **위치만 `src/debug/` 로 바뀌어 신규 경고**가 된다 → debug 매니페스트의 두 `<receiver>` 에 `tools:ignore="ExportedReceiver"` + 근거 주석. baseline 은 계획대로 **17 → 15**(해당 2건만 수동 삭제, `updateLintBaseline` 미실행)
2. **lint `ForegroundServiceType` 오탐 신규 발생** — debug 소스셋에 `foregroundServiceType` 없는 `<service>`(=`ProbeAccessibilityService`, AccessibilityService 라 없는 게 정상)가 **별도 매니페스트 파일**로 생기자 검사기가 `FloatingLauncherService.startForeground()` 호출을 잘못 연결. **대조 실험으로 오탐 확정**: 동일 코드에서 `lintRelease`(매니페스트 1개)는 통과, `lintDebug`(2개 병합)만 실패. 병합 debug 매니페스트에 `foregroundServiceType="specialUse"` 정상 존재 확인. `startForegroundCompat()` 에 `@SuppressLint` + 진단 근거 주석(baseline 에 숨기지 않음)
3. **AGP 머저가 XML 주석을 병합 결과에 보존한다** — main 매니페스트 주석에 남아 있던 `probe.ProbeActivity` / `probe.ProbeTriggerReceiver` 리터럴이 **릴리스 매니페스트까지 따라 들어갔다.** 해당 주석 2곳을 debug 매니페스트 경로 표기로 대체(내용 정확도는 오히려 개선)

**독립 검증(qa-verifier) 판정 = PASS.** 억제 정당성을 대조 실험으로 실증하고 `@SuppressLint` 제거 후 재현·원복까지 확인. S4 토큰 로직은 (a)정상 경로 통과 (b)`onCreate`/`onNewIntent` 이중 소비 없음(플랫폼이 둘 중 하나로만 라우팅) (c)config change 재생성 시 오발화 없음(`PanelActivity` 는 `configChanges` 로 재생성 자체가 없고, 낡은 토큰은 불일치로 무시) 전부 확인.

### W0 완료 내역 (실기기 불필요 · 런타임 동작 변경 0줄)

- **M7 lint 게이트 도입** — 실제 수정 7종(`PropertyEscape` 2곳·`MissingApplicationIcon`·`ObsoleteSdkInt`×2·`ClickableViewAccessibility`×2·`DataExtractionRules`·`RedundantLabel`) + 코드 억제 2종(`ResizeModeDetector` 리플렉션 `@Suppress`, 폴백 비트가 이미 대응) → **그 다음에** baseline 생성. `app/lint-baseline.xml` **17건 = `AndroidGradlePluginVersion`×2·`GradleDependency`×6·`NewerVersionAvailable`×7·`ExportedReceiver`×2**. 앞 15건은 의존성 상향(P-5, v1.5 일괄), `ExportedReceiver` 는 **W1/S1 에서 해소 예정**(W0 시점엔 `app/src/debug/` 부재라 해소 불가 — 검증자 확인). **금지 ID(고치기로 한 8종) baseline 진입 0건** 감사 완료. baseline 경로는 전부 상대경로 — 머신 종속 없음
- **M4** versionCode 1→2 · versionName `0.4.0`(**W2 Shizuku AIDL 변경의 선행 조건** — `UserServiceArgs.version` 이 프로세스 재생성을 결정) · `android:icon` · `EntryContext.panelIntent` 필드+생성부 삭제(재사용처 0 확인) · `tapPoint` KDoc 정정
- **M2** CLAUDE.md 정정 — 「의존성」에서 **Hilt 삭제**(실제 미사용, 컴포넌트가 직접 생성하는 것이 이 규모에서 옳음) + 「검증 명령」에 `JAVA_HOME` 전제 명시(런처는 `org.gradle.java.home` 과 별개로 요구) + DoD 3번에 `lintDebug` 편입
- **M6** `app/proguard-rules.pro` 신규 — `ApplicationInfo.privateFlags` keep 규칙 사전 작성 + release 배선. `isMinifyEnabled=false` 유지라 현재는 무효, v1.5 대비
- **T2** `app/src/test/.../ArchitectureTest.kt` 3개 — `domain/` 순수성 + `ProfileStoreMapping` 순수성 + **vacuous-pass 가드**(경로 오타로 항상 통과하는 것이 이 테스트의 유일한 실패 모드). **실효성 실증**: `domain/Profiles.kt` 에 `import android.util.Log` 임시 주입 → 실제 FAILED 확인 후 원복. CLAUDE.md 「철칙」이 사람 리뷰 의존에서 기계 강제로 전환됨
- **스타일** dp 리터럴 4종 상수화(값 불변 14/4/14/10) · `MENU_ITEM_TEXT_COLOR = -1` → `Color.WHITE`(동일 값, `const`→`val` 은 외부 정적 필드라 불가피) · 로그 태그 `PanelActivity`→`FWPanelActivity`(유일하게 `FW` 접두사 이탈 → `logcat -s` 누락 원인)
- **부수 추가** `res/xml/backup_rules.xml` + `android:fullBackupContent` — minSdk 30(<31) 이라 `dataExtractionRules` 단독으로는 lint 갭이 안 닫힘. `allowBackup="false"` 와 정합하게 전부 제외
- **KTX 치환 2건** `Bitmap.createBitmap`→KTX `createBitmap` · `Uri.parse`→`toUri()`. core-ktx 1.15.0 sources jar 실물 확인으로 **둘 다 순수 pass-through inline** 판정 (baseline 로 숨기지 않고 실수정한 근거)

**독립 검증(qa-verifier) 판정 = CONDITIONAL PASS → 권고 2건 반영 후 종료.** 검증 핵심 2건:
① **`BubbleImageView` 이중 발화 없음** — `BubbleTouchListener.onTouch` 4분기 전부 `return true` → 프레임워크 `onTouchEvent` 미호출 → `performClick()` 호출 지점은 탭 확정 분기 단 하나. 탭/드래그/롱프레스 임계값·분기 구조 무변경(16차 오분류 0 검증분 보존)
② `SDK_INT < R` 죽은 분기 제거 2곳(minSdk=30=R) 후 제어 흐름 동일, `ArrangerAccessibilityService` 의 `import android.os.Build` 는 미사용 확정 제거 / `ProbeAccessibilityService` 는 `Build.MANUFACTURER` 등 실사용이라 유지 — 판단 정확

---

**개발 환경 주의 (실측 누적)**: Git Bash 에서 gradlew 는 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` 프리픽스 필수 — 없으면 installDebug 가 조용히 실패해 구버전 APK 로 검증하게 됨 (실제 발생, 40분 소모). screencap 은 `-d 4630946449689556883` (멀티 디스플레이). adb 롱프레스 시뮬레이션은 `input swipe x y x y 1200` (700ms 는 경계 실패). **10차 추가**: Git Bash 가 `/sdcard/...` 인자를 로컬 경로로 변환해 파괴 — 원격 명령은 `adb shell "cat /sdcard/x"` 처럼 통째 인용, `adb pull` 은 `MSYS_NO_PATHCONV=1` 프리픽스. E2E 리셋 = 패널 페인 탭+`keyevent 4` (PanelActivity finish → 상대 앱 전체화면 복귀). 유튜브 상태 셋업 = `am force-stop` 후 `am start -a VIEW -d '...watch?v=aqz-KE-bpKQ&t=120'` (기존 태스크는 딥링크 미라우팅) → 컨트롤 탭 → 전체화면 버튼 (2184×1968 가로에서 ≈1466,858). 회전 강제 = `accelerometer_rotation 0` + `user_rotation 1` (외부 요인으로 리셋되니 캠페인 중 재확인). **13차 추가**: Shorts 진입이 포트레이트 강제 + 잠금 무시하며, 복귀 후 settings put 만으론 WM 재평가 안 됨 — `adb shell cmd window user-rotation lock 1` 이 즉시 적용 (`free` 로 해제). pre-null(측정 실패) 상태 유도 = 세로 직캠(MPD직캠 검색) immersive 전체화면 — 다크 UI 는 상태바/엣지 순흑 행 때문에 pre 가 항상 후보 생성(한쪽 밴드 0 허용). 종료 대기 = `adb logcat -s FWArranger -e "arrange (done|failed)" -m 1`. **15차 추가**: 13차 `user-rotation lock` 잔재가 남아 있으면 FoldingFeature orientation 이 물리 자세와 무관하게 VERTICAL — 폴드 검증 전 `cmd window user-rotation free` 확인 필수. 재설치 후 `settings put secure enabled_accessibility_services` 를 **동일 값**으로 put 하면 no-op (서비스 재기동 로그 안 나옴) — 기동 로그 관찰하려면 `none` 으로 토글 후 재설정.
**실기기 검증 절차 (참고):**

```bash
./gradlew :app:installDebug
# 재설치 후 접근성 재활성화 필수 (함정 #6). probe 병행 시 콜론으로 연결
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService
# 유튜브 가로 전체화면 재생 상태에서 (⚠ -n 필수 — 액션만으로는 implicit broadcast 제한으로 수신 안 됨, 7차 실측):
adb shell am broadcast -a dev.dj.foldwindow.ARRANGE -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver --es placement top
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
| Phase 3 UI | ✅ 완료 | DoD 3항목(콜드부팅 버블 복귀 · 프로파일 유지 · 권한 미부여 무크래시 안내) 실기기 검증 완료. P3-1 버블 ✅ + P3-4 온보딩 ✅ (실기기 E2E: 버블 탭→배치 4.1초, 버블 숨김/복원 검증). P3-2 확장 메뉴 ✅ **실기기 E2E 완료** (메뉴發 배치 verified·분할 해제 성공·재탭 닫기만·프리셋 렌더·가로/세로 클램프 — 결함 3건 발견·수정·재검증, DEVICE_FACTS P3-2 절). P3-3 DataStore ✅ **실기기 검증 완료** (141 테스트 + 7차 실기기 #26 5항목 전부 — 이관·goAsync 부팅·placement 복원 E2E·corruption 복구·중지 레이스). P3-5 FoldingFeature ✅ **15차 실기기 검증 5항목 전부 통과** (결함 2건 현장 수정: UiContext 3-인자 채택·닫기 오발화 2층 방어. qa PASS 230/230, DEVICE_FACTS 15차 절) |
| Phase 4 확장 | ✅ 구현·검증 완료 (물리 확인 2건 이월) | P4-1·2·3·4 구현 + **17차 실기기 검증** (P4-2 5/5 · P4-3 6/7 에뮬 · P4-4 4.5/5 · P4-1 7/7, DEVICE_FACTS 17차 절) + **19차 #27 종결** (축 A 채택·축 B 기각 제거·#28 수정. G4~G7 통과, G2 실패가 축 B 기각 근거. DEVICE_FACTS 19차 절). 잔여 = 「남은 작업」 A-1 물리 접기 · A-2 DRM 육안 + B 희귀 경로 |

## 작성된 코드

| 파일 | 상태 |
|---|---|
| `domain/SplitPlanner.kt` | ✅ P1-1 반영. `foldSevenLandscape()` = divider 14px / minPane 181px (실측). 테스트 22개 |
| `domain/LetterboxDetector.kt` | ✅ v2 하이브리드. 순흑(0.97) 우선 → luma 통계 기반 적응 폴백(`ADAPTIVE_*` 상수 4종). `resolveAspect` 진입점 불변. 11차 #12: `resolveAspectPillarbox` 열축 역산(band.height/scan.width) 추가 — 기존 함수 무변경. 테스트 37개 |
| `domain/MeasurementConsensus.kt` | ✅ 11차 #12 신규 + **실기기 검증** (G1~G5, 판정 6경로 발동 실증). 순수 Kotlin — 2-샷 합치 판정 (`classifyAxis`/`classifyConfirm`/`agree`, verdict 9종, DESIGN_12 §3.3 코드화). classifyAxis minConfidence(0.25) — 크롬 그라디언트 conf 0.08 유사 밴드 오승격 차단 (실측 대응). 테스트 25개 |
| `domain/FlexModePolicy.kt` | ✅ 14차 신규 → **15차 각도 게이트 확장 + 실기기 검증**. 순수 Kotlin — 800ms 디바운스·진입당 1회 arm·이탈 disarm 유지 + `onHingeAngle`/`isAngleStable`(대역 45~135 ∧ 침묵≥600ms ∨ 600ms 윈도 스프레드≤8°, 센서 무가용 시 통과 격하, 불안정 시 armed 유지). 근거: 닫기 체류 실측 ~2s(3표본) — 시간 단독 판별 불가. 테스트 24개 |
| `domain/PanelTaskPolicy.kt` | ✅ #27/A2 신규. 순수 Kotlin — `PanelTaskSnapshot`(taskId/componentClassName/lastActiveMs) + `pruneTargets`(**MRU 패널 태스크 1개 반드시 보존** = step3 소환원, 나머지 taskId 만 입력 순서로 반환. `componentClassName==null` = 비패널 취급으로 오제거 차단, `taskId<0` = 식별 불가라 반환 제외하되 보존 선정엔 참여) + `hasPanelTask`(19차에 `needsSummon` 에서 개명·반전 — 용도는 #28 폴백 가드 하나). 동률 `lastActiveMs` → 입력 순서 타이브레이크 = 실전 주 경로(공개 API 로 lastActiveTime 조회 불가 → 플랫폼이 전부 0 전달, `ActivityManager.appTasks` 의 MRU-first 순서에 위임 — 이 계약은 [미검증]). 테스트 11개 |
| `domain/CoverDismissPolicy.kt` | ✅ P4-3 신규. 순수 Kotlin — armed(비UNKNOWN 관측 후) 상태에서 UNKNOWN 진입 시 600ms 디바운스 예약 → 발화 시점 `shouldDismissNow` 재검증 → 에피소드당 1회 래치, 콜드스타트 UNKNOWN 보호, 재열림 시 재-arm. 테스트 14개. 실기기 미검증 |
| `platform/FoldStateMonitor.kt` | ✅ 14차 신규 → **15차 실기기 판정 반영**. 후보 체인 = ①서비스 자신(One UI 8 거부 — assertUiContext, 타 OS 대비 유지) ②**3-인자 `createWindowContext(display,...)` 실채택**(방출 수신 확인, SDK 31 가드) ③createDisplayContext 체인(예비). 구 2-인자 후보는 생성 불가 판명·삭제. 커버 디스플레이 기하(1080×2520)로도 방출 지속 실측 |
| `platform/HingeAngleMonitor.kt` | ✅ 15차 신규 **실기기 검증**. `Sensor.TYPE_HINGE_ANGLE` 래퍼 (Fold 7 노출 확정 — 도 단위, 노트북 ≈90.0, 닫힘 0.0, on-change 방출). start/stop 멱등, arm 수명주기 연동(배터리 위생), 센서 부재 시 Log.w 1회 후 무동작 |
| `domain/ArrangeStateMachine.kt` | ✅ P1-4 + `closedLoopCorrection` 플래그 (false 면 ADR-5 보정 생략, 잔여 정직 보고 — PROFILE 소스 오보정 실측 대응). 순수 리듀서, 시간은 이벤트 nowMs 만(ADR-2). 테스트 24개 |
| `domain/ClickCyclePlan.kt` | ✅ #20 신규 (9차) **10차 실기기 검증**. 순수 Kotlin — 클릭 메커니즘 사이클 계획. `PICKER`(gesture-first)/`POPUP_SWITCH`(a11y-first) 프로파일 = 데이터(1줄 롤백 레버), `mechanismFor` 클램프, verifySlice≥400ms 불변식. 테스트 12개. 실기기: 피커 cycle-0 14/15·cycle-1 회복 1회, 스왑 cycle-0 4/4 (cycle-2/스왑 제스처 사이클 미발동 [미검증]) |
| `domain/Profiles.kt` | ✅ P1-2 신규. `AspectSource`/`PartnerMode`/모델 4종 + `validate()` 위치 특정 에러. 순수 Kotlin. 12차 §6: `AspectSource.CACHED`(리졸버 출력 전용, JSON 금지)·`defaults.cacheMeasuredAspect` 레버·MIN/MAX_ASPECT 공개 승격(값 불변, 캐시 오염 검증 공유). 14차 P3-5: `defaults.flexAutoTopPlacement` 레버(부재=true). P4-3: `defaults.coverAutoDismiss` 레버(부재=true). (#27/B `panelCardPreflight` 는 19차 축 B 기각과 함께 제거 — 시드 JSON 무변경) |
| `domain/AspectResolver.kt` | ✅ P1-3 신규 + 12차 §6 확장. ADR-1 폴백 4단(PROFILE→MEASURED→**CACHED**→PRESET) — `cachedAspect` 기본 null 파라미터라 기존 호출부 무영향, 유효 측정이 캐시를 절대 못 이김. `DEFAULT_MIN_MEASUREMENT_CONFIDENCE=0.25f`. 테스트 15개 |
| `data/WindowProfilesParser.kt` | ✅ P1-2 신규. kotlinx-serialization DTO→domain 매핑, 예외 누출 없이 `ProfilesParseResult`. 11차 #12: `requireMeasurementAgreement` 토글 (키 부재=true, 1줄 롤백 레버 — 시드 JSON 무수정). 12차 §6: `cacheMeasuredAspect` 토글 동형 추가. 14차 P3-5: `flexAutoTopPlacement` 토글 동형 추가. P4-3: `coverAutoDismiss` 토글 동형. (#27/B `panelCardPreflight` 토글은 19차 기각으로 제거 — 테스트도 함께 원복) |
| `data/ProfileStore.kt` | ✅ P3-3 신규. Preferences DataStore(`fwa_store`) 래퍼 — 버블 enabled/x/y + 앱별 마지막 성공 placement. `SharedPreferencesMigration("bubble_prefs")` 무손실 이관(레거시 키 이름 동결 계약), `ReplaceFileCorruptionHandler`→emptyPreferences(부팅 크래시 루프 방지), 쓰기 = `safeWrite` NonCancellable(레거시 apply() 의 종료 후 반영 보장 대체), 읽기 = safeRead 예외 방어. **7차 실기기 검증**: 이관 무손실·corruption 복구·NonCancellable 완주 (DEVICE_FACTS P3-3 절). 12차 §6: `measuredAspect`/`saveMeasuredAspect` (키 `measured_aspect.<pkg>`, 저장측도 범위 검증 후 거부 — 이중 방어) → **13차 실기기 검증** (pb 키 실물 판독, 레버 OFF 중 보존 확인). P4-2: `panelWidgetMode`/`panelMemo` (저장측 허용집합 재검증·절단, safeWrite 경유) |
| `data/ProfileStoreMapping.kt` | ✅ P3-3 신규. 순수 Kotlin 매핑 — placement 직렬화 왕복(오염값→null, 크래시 금지)·키 네임스페이스·레거시 키 상수. 12차 §6: `measuredAspectKeyFor`·`aspectFromStorage`(NaN/∞/1.0..4.0 밖 → null, domain 공개 상수 공유). JVM 테스트 18개 (키 이름 동결 회귀 테스트 포함). P4-2: 위젯 모드 허용집합 검증·`PANEL_MEMO_MAX_CHARS=4000` 절단 (테스트 29개) |
| `platform/ScreenshotSampler.kt` | ✅ v2 확장. 행별 luma 평균/분산 산출 추가. 11차 #12: `toPillarboxScan` 열축 전치판 (entries=열, width 자리=height/colStride — 역산 좌표계 계약) + **실기기 검증·현장 튜닝 2건** — sideMarginPct 0.005(최외곽 열 var 404~501: 코너 누출+엣지 렌더링 물증), edgeMarginPct 0.12(플레이어 크롬 y-대역 제외). 수정 후 글로우 밴드 214/214 대칭 3/3 재현. 기존 `toLetterboxScan` 무변경 |
| `domain/PaneGeometry.kt` | ✅ P2-2 + 확장. 가시 교집합·간격 휴리스틱·상하/좌우 분할 판정·분할선택 페인 판정·`pickPaneLike`(최소화 플레이어 팝업 오염 필터, 실기기 근거). 순수 Kotlin. 테스트 30개 |
| `platform/DividerLocator.kt` | ✅ P2-2. `TYPE_SPLIT_SCREEN_DIVIDER` 핸들 중심 1차 → PaneGeometry 간격 휴리스틱 폴백. 실기기 미검증 |
| `platform/DividerDragger.kt` | ✅ P2-4 **실기기 검증**. SINGLE_STROKE 기본(확정). HOLD_THEN_MOVE 는 GestureDrags 위임 |
| `platform/PaneSwapper.kt` | ✅ 9차 #20 재구성 → **10차 실기기 검증** (회전 여파 컨텍스트 4/4 수렴 — 과거 무효 2회 실측 컨텍스트 동형 전승): 정착 게이트(`dividerSettled` 주입, 핸들 bounds 2연속 동일, 실측 ~154ms ok) + 스위치 클릭 = POPUP_SWITCH 사이클(a11y→gesture→gesture, involution 가드·팝업 소멸 시 재탭 전 isSwapped 재확인·검증 슬라이스 800ms — cycle-0 a11y 로 전승, cycle-1/2·재탭 분기 [미검증]). 핸들탭/팝업탐색 루프·더블탭 폴백 무변경. 실패 시 서비스 회전×2 폴백 유지(미발동) |
| `platform/SplitEntry.kt` | ✅ P2-3 **실기기 검증** (DRAG·MENU 양 경로). `EntryRecipe` 분기: DRAG 3단계(유튜브 회귀 E2E 통과) / MENU 5단계(UNRESIZEABLE 전용, E2E 6회 성공). 2026-07-25 오후 2차: step2 유령 매치 재폴링 + 목표 상태 선체크 수정. 9차 #20 → **10차 실기기 검증**: step3/menuStep4 = `clickUntilCondition` PICKER 사이클(gesture→gesture→a11y) 재작성 — 15세션 cycle-0 gesture 14회(176~362ms)·cycle-1 회복 1회(오착지 FORENSIC 특정), ENTRY_STEP_FAILED 0 (과거 무override ~50% 실패 → 5연속 무실패). **LAUNCH_ADJACENT 전략2 삭제** 무회귀 확인. cycle-2 a11y·오버레이 가드 발동 [미검증]. menuStep2/3/5·step1/2·셀렉터 무변경 |
| `platform/ResizeModeDetector.kt` | ✅ 신규 **실기기 검증**. `privateFlags` 필드 리플렉션 allowed / 상수 리플렉션 denied(max-target-o) → 폴백 비트 **1<<11** (0x8c000910 교차 검증). 실패 시 null → DRAG 폴백 |
| `platform/DividerPopupRotator.kt` | ✅ 신규. 핸들 탭→"시계 방향으로 회전" 클릭 공용화 (MENU step5 + 서비스 회전×2 폴백). step5 경로 실기기 검증, 회전×2 폴백은 미발동 **미검증** |
| `platform/GestureDrags.kt` | ✅ 신규·실기기 검증. 2-페이즈 홀드드래그(1px 드리프트 홀드 + continueStroke 이동, 타이밍 가드). API 함정 4종 문서화 |
| `service/ArrangerAccessibilityService.kt` | ✅ **실기기 검증** (유튜브+넷플릭스). 상태 머신 구동, 레시피 선택 배선, pre-measure **페인 크롭**, `pickPaneLike` 위치 판정, `purgeStalePanelTasks`, 스왑 실패 시 회전×2 폴백, `closedLoopCorrection` 배선(PROFILE 시 off), 세션 `dragTimeoutMs=12s` 오버라이드. P3-2 `dismissSplit()` **실기기 E2E 검증**: 디바이더 드래그 아님(반증) — `PanelActivity.instance.finishAndRemoveTask()` + 인텐트 폴백[미검증], 진입 체크 = `isSplitActive` 자체 2s 조건 폴링(스크림發 a11y 목록 비원자 재구축 대응), 150ms/3s 해소 폴링, 실패 전부 토스트. `awaitWindowsSettled()` = beginSession freshness 게이트. P3-3: placement 결정 체인에 `ProfileStore.lastSuccessfulPlacement` 2순위 삽입(override>last-success>profile>defaults>TOP, placementSource 로그), Done ∧ effective==desired 시에만 저장(지역 캡처 후 launch) — **7차 실기기 E2E 검증** (OVERRIDE 저장 → LAST_SUCCESS 복원, residual=0). 9차 #20 → **10차 실기기 검증**: `awaitDividerSettled` 공급(4/4 ~154ms ok), `TYPE_VIEW_CLICKED` 포렌식 수신 검증(성공 클릭=FrameLayout/null·오착지=icon_container·카드=task_icon 판별 실증), 오버레이 가드 람다 주입(발동 [미검증]). 11차 #12: `confirmMeasuredAspect` 합치 게이트 (handleDragDividerTo 선두, 세션 1회, MEASURED 만 발동, 레이트리밋 조건부 대기, realTargetY 덮어쓰기 선례 재사용 — 머신 무변경), aspectOverride tier 0 (측정·보정 전부 생략), verify residualCols 로그 병기(MeasureResult 인자 불변), `logMeasurement` 밴드 기하 상시 기록 — **실기기 G1~G5 통과** (9/9 done, DEVICE_FACTS 11차 절). 12차 §6 캐싱 배선: beginSession 캐시 조회(override 세션 생략)+decision 로그 `cachedAspect=`, finishConfirm 불합치 폴백 cached→preset, reportTerminal Done 에서 admission(합치∧verified∧레버) 저장, cleanupSession 3필드 리셋 → **13차 실기기 4항목 전부 검증** (저장·폴백/decision 양 지점 CACHED 낙착·confirm 미실행·레버 회귀, DEVICE_FACTS 13차 절). 14차 P3-5: `FoldStateMonitor` 구동(onServiceConnected, 기본 런처 해석 포함) + `onFoldPosture` 디바운스 예약(delay 후 shouldTriggerNow 재검증 — ADR-2 예외 요건 충족) + `evaluateFlexAutoTrigger` 5단 게이트(거부 시 disarm·토스트 없음) + placement 체인 FLEX 티어 + `sessionPlacementSource` 세션 필드(cleanupSession 리셋) + FLEX 세션 last-success 저장 억제 — 15차 실기기 검증 통과. P4-3: `coverPolicy` 배선(onFoldPosture 의 flex 조기 return 앞 삽입, UNKNOWN 디바운스→`evaluateCoverAutoDismiss` 게이트 4종→패널 직접 finish, dismissSplit 미경유·토스트 없음). P4-4: `foregroundPackageForExport`/`startArrangeWhenForeground`(150ms/5s 사전 조건 폴링 — 머신 밖, placement 는 기존 체인) — P4 분 실기기 미검증. **#27/A1·A2**: `performDismissSplit` instance 경로·`evaluateCoverAutoDismiss` 를 `panel.finish()` 로 격하(18차 G1 — removeTask 는 step3 소환원까지 지우는 초과 동작), `purgeStalePanelTasks`→`pruneExtraPanelTasks` 재구현(판정 `PanelTaskPolicy` 위임, `lastActiveMs` 는 전부 0 전달, 로그 `보존 1 / 제거 N`, 호출 위치 `beginSession` 선두 불변) + 반증된 premise KDoc 정리 — 실기기 [미검증]. **#28**: 폴백 3분기화(패널 태스크 부재 시 인텐트 폴백 생략) + 헬퍼 `panelTaskSnapshots`/`hasPanelTask`. **#27/B 소환은 19차 실기기 기각으로 전량 제거** (`ensurePanelCard`·`setPanelCardOutcome`·`lastPanelCardOutcome`·폴링 상수 2종·토스트 병기 삭제, `reportTerminal` 원복). 축 A·#28 경로는 **19차 실기기 검증 완료** (커버 해제 후 카드 생존 3/3 · prune 보존1/제거1 · 재설치 죽은 카드 done). **W1/S4**: `performDismissSplit` 인텐트 폴백이 `PanelActivity.issueFinishToken()` 발급값을 실어 보낸다(`hasPanelTask()` 사전 가드 유지). **W1/F7**: `captureScreen()` 취소 시 `bmp?.recycle()` |
| `service/ArrangeTriggerReceiver.kt` | ✅ adb 디버그 트리거 (`dev.dj.foldwindow.ARRANGE`). 버블 도입 후에도 회귀용으로 유지 |
| `service/FloatingLauncherService.kt` | ✅ P3-1 + P3-2 확장 메뉴 **실기기 E2E 검증**. 탭=배치, 드래그=이동+스냅, **롱프레스=메뉴 열기**(위/아래 배치·분할 해제·프리셋(JSON SSOT 파싱 캐시)·설정). 메뉴 = **풀스크린 투명 스크림** FrameLayout 창 (ACTION_OUTSIDE 방식은 디스패치 순서 경합 실측으로 폐기 — 스크림이 모든 터치 선점, 재탭=닫기만 구조 보장). 함정 #22: 모든 트리거 직전 + `setBubbleHiddenForArrange(true)` 시 메뉴 제거. 위치/켬 상태 P3-3 에서 `ProfileStore`(DataStore) 이관 완료 — 초기 위치는 onCreate 1회 runBlocking 스냅샷(runCatching 폴백), 쓰기는 serviceScope.launch(store 계층 NonCancellable). 9차 #20: `bubbleAttached`/`menuView` @Volatile + companion `hasAttachedOverlayWindow()` (제스처 탭 오버레이 가드 판정원). 16차: `HIDE_SAFETY_TIMEOUT_MS` 30s→90s (이론 최악 ≈70s 근거) + 제스처 실사용감 검증 (오분류 0). P4-4: 메뉴 「앱 페어 바로가기 만들기」+`exportAppPair`(dismissMenu 선행·2s 식별 폴링·`requestPinShortcut` 지원 체크) — 실기기 미검증 |
| `service/BootReceiver.kt` | ✅ P3-1 신규 + P3-3 개편. BOOT_COMPLETED 시 bubble_enabled+오버레이 권한 확인 후 FGS 재기동. 실부팅 검증은 구 동기 prefs 코드로 통과(5차) — P3-3 에서 goAsync+IO 코루틴(finally finish 보장)으로 재작성 → **7차 실부팅 재검증 통과** (로그·FGS 기동·버블 가시, 회귀 해소) |
| `ui/OnboardingActivity.kt` | ✅ P3-4 신규 **실기기 검증** (권한 감지·버블 토글·안내 렌더 확인). 권한 카드 3종 + 버블 시작/중지 + 사용 안내 (넷플릭스 "배치 후 재생" 포함). MAIN/LAUNCHER 진입점. P3-3: 중지 = NonCancellable(enabled=false 쓰기→stopService, 액티비티 파괴에도 완주) + stopInProgress 재진입 가드 — **7차 근사 + 16차 물리 폴드 접기 실기 검증** (양 경로 시퀀스 완주). 16차: 알림 권한 플로우 전 구간 검증 (회수 감지·다이얼로그·허용 즉시 반영) |
| `ui/PanelActivity.kt` | ✅ P2-5 + P3-2. 검정 배경+시계 파트너 창. 라벨 "FW Panel" = SplitEntry 피커 셀렉터 계약. P3-2: `instance` 정적 참조 + `EXTRA_FINISH_PANEL` (dismissSplit 의 패널 finish 경로 — finish 실기기 검증, 인텐트 폴백 [미검증]). MAIN/LAUNCHER 노출은 Phase 3 재검토. P4-2: 위젯 3종(시계/메모/검정)+하단 모드 버튼 상시 표시, 메모 500ms 디바운스+ON_PAUSE flush(DisposableEffect — Activity 메서드 무접촉) — 기존 자가 가드·라벨 계약 무변경 (실기기 미검증). **#27/A1**: `EXTRA_FINISH_PANEL` 경로(onCreate·onNewIntent)와 `scheduleFullscreenCheck` 자가 가드 3곳을 `finish()` 로 격하 — 분할은 그대로 해소되고 최근 태스크 카드는 잔존(18차 G1). companion KDoc·잔존 청소 주석 갱신. **W1/S4**: `EXTRA_FINISH_PANEL: Boolean` → `EXTRA_FINISH_TOKEN` + companion `@Volatile finishToken`/`issueFinishToken()`/`consumeFinishToken()`(1회용) — exported 상태에서의 자체 DoS 차단. 확장함수는 `consumesFinishRequest()` 로 개명(부작용 명시). 실기기 [미검증](W1-3·W1-4) |
| `ui/PanelWidgetMode.kt` | ✅ P4-2 신규. UI 표시 선호 enum(CLOCK/MEMO/BLACK)+`fromStorage` CLOCK 폴백 — 도메인 `PartnerMode{BLACK}`(JSON 스키마 소속)과 의도적 분리 |
| `ui/PairShortcutActivity.kt` | ✅ P4-4 신규. 트램펄린 — extra 검증→접근성 확인(꺼짐 시 온보딩 유도)→대상 앱 실행(NEW_TASK)→`startArrangeWhenForeground`→finish, 전 구간 runCatching. exported+excludeFromRecents+noHistory+전용 taskAffinity+반투명 테마. 실기기 미검증 |
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
7. ~~`AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE = 0.25f` 튜닝~~ → **접근 자체 기각** (11차 #12 검토): 오염 conf 0.60~0.97 ≥ 정상 ADAPTIVE 상한 0.6 — 어떤 임계도 오염/정상 분리 불가. 0.25 는 "후보 자격" 게이트로 역할 격하·값 유지, 채택은 합치 게이트가 결정 (DESIGN_12 §3.3)
8. ~~PaneSwapper 셀렉터~~ → **"창 전환" 실측 확정** (팝업 노드 3종: "App pair 추가 위치"/"창 전환"/"시계 방향으로 회전"). 하단 배치 E2E 성공
9. ~~step 파트너 경로~~ → **피커 노드 탭이 1차 확정**. LAUNCH_ADJACENT 는 분할 선택 상태를 파괴(전체화면 강탈)해 최후 폴백으로 강등
10. ~~`defaults.closedLoopCorrection` JSON 토글 미배선~~ → **배선 완료** (2026-07-25 오후). 추가 규칙: aspectSource=PROFILE 이면 무조건 보정 생략 (오염 측정이 프로파일을 덮어쓰는 실측 사고 2회 대응)
11. 대상 앱 라벨 조회 — `<queries>` 블록으로 실기기 정상 동작 확인 (label=YouTube 조회 성공)
12. 사전 실측 오염: 플레이어 컨트롤 오버레이/앰비언트 글로우가 떠 있으면 종횡비 오측 (1.333/1.12 관측. **추가 실측 2026-07-25 오후 2차: 영상 시작 직후 탭 → 추천화면/인트로 오염 1.6 오측(conf 0.60), 어두운 장면이라 verify residual=0 오판**). → **11차 해소 완결**: 검토→구현(qa PASS)→**실기기 G1~G5 전부 통과** (DEVICE_FACTS 11차 절). 2-샷 합치 게이트 (`docs/DESIGN_12_MEASUREMENT_CONSENSUS.md`) — 사고 클래스 3종(엔드스크린 conf 0.70·컨트롤 snap 1.5·과거 1.6) 전부 차단 실증, 클린 경로는 SNAP_AGREE→MEASURED 3/3. 잔여 = v1.5 후보(다음 행동 2번)와 좁은 갭(재트리거 시 디바이더 이미 목표 4px 이내면 Dragging 스킵 → confirm 미발동 — pre 단독 = 종전 동작, 회귀 아님)
13. 필러박스 맹점: 과소 이동 시 `residualBars=0` 으로 verified 오판 가능. → 열축 도메인 로직이 #12 confirm 측정과 동일 기반 — v1 에서 `residualColumns` **로그 보고까지** 동봉, `verified` 의미론 반영은 v1.5 (DESIGN_12 §3.4·§7). **11차 실측 보강**: 순흑 residual 은 글로우 필러박스에 블라인드 확정 (G5: 16:9-in-21:9 실재 필러박스에서 residualCols=0) — v1.5 는 적응형 residual 필요
14. BOTTOM 배치 최적화: 현재 상단 도킹 후 "창 전환" 스왑. step2 드롭 지점을 하단 가장자리로 바꾸면 스왑 생략 가능한지 실기기 확인 — Phase 3
15. ~~step2 성공 조건 폴링 예산~~ → **실질 해소** (2026-07-25 오후 2차). 실측: 잔여 폴링 ~370ms 로 애니메이션 정착 불가 → 실패 판정. 예산 분리 대신 **다음 시도의 목표 상태 선체크**가 늦은 정착을 흡수하는 설계 채택 — 회귀 E2E 에서 시도1 실패→시도2 선체크 즉시 성공 실증. 타임아웃 값 무변경
16. ~~넷플릭스(UNRESIZEABLE) 분기 자동화~~ → **완료 + 실기기 E2E 검증** (2026-07-25 오후, 6회). 감지 = privateFlags 필드 리플렉션 + 폴백 비트 1<<11 (실측 교차 검증)
17. ~~넷플릭스 재생-분할 관계~~ → **특성 규명 완료**: 분할 페인 안에서 재생 시작 = 유지 / 재생 중 메뉴 진입 = 재생 세션이 "최소화된 플레이어" 팝업으로 분리 (3회+ 재현, One UI 동작). v1 지침 = "배치 후 재생". Phase 3 온보딩/토스트에 안내 반영
18. ~~step2/3 성공 조건 오탐~~ → **완결** (2026-07-25 오후 2차). 유튜브 DRAG 회귀 E2E 통과. `isSplitSelectTopPane` 임계(전폭≥90%, `EDGE_DOCK_TOLERANCE_PX=40`) 는 ground truth(대상 페인 `[0,0][2184,977]`, 도킹 0px)로 **[검증]**. 회귀에서 발견된 실버그 2종(유령 매치/성공 미인지)은 SplitEntry 수정 완료
19. 회전 결과 페인 위치 비결정 — 원인 미상. 10차 표본 보강: 이 세션 TOP 7/7 (누적 TOP 10 / BOTTOM 2 — 상단 편향). 하단 낙착 시 교정 체인: PaneSwapper(10차 4/4 수렴) → 회전×2 폴백(**미발동·미검증**) → 실패 시 하단 유지+토스트
20. **[10차 실기기 Gate 1~3 통과 — 주 경로 완결]** 15/15 done·ENTRY_STEP_FAILED 0. 피커 cycle-1 회복 실증 + FORENSIC 이 무효 클릭 = **오착지(icon_container) 클래스**임을 물증화 (실행 자체 없음 아님). 회전 여파 스왑 4/4 수렴 (정착 게이트 ~154ms). 잔여 = 미발동 경로 [미검증] 목록 (DEVICE_FACTS 10차 절)과 3시도 전멸 클래스 재발 시 스텝 되감기 재검토만. — (이하 이력) "창 전환" ACTION_CLICK 무효 (회전 직후 컨텍스트 2회 실측, 유튜브 세션에선 성공) — 원인 탐구 필요. 좌표 탭 제스처로 대체 시도 검토. **2026-07-25 오후 4차 보강: step3 피커 탭도 동일 계열 변동성 실측** — 메뉴發 배치 4회 중 2회 step3 3연속 실패(클릭 무효 2회 = 액티비티 생성 이벤트 없음 / `startActivityFromRecents` 오라우팅 1회), 직후 재시도는 성공. 성공 시그니처 = `startActivityAsUser:launcher` (DEVICE_FACTS P3-2 절). → **2026-07-25 저녁 8차 검토 완결**: AOSP 소스 검증 — ACTION_CLICK=true 는 isClickable 만 보장(performClick 결과 폐기, 히트테스트·터치 파이프라인 우회) → 무효·오라우팅 두 클래스 모두 설명. 기존 제스처 폴백은 true 반환 시 도달 불가 = 죽은 코드 실증. "창 전환" 무효는 팝업이 아니라 회전-여파 컨텍스트가 독(회전 노드 6/6 대조) → 정착 게이트 + 재시도가 1차 해법. 설계 확정 = 클릭-사이클 에스컬레이션(`docs/DESIGN_20_CLICK_CYCLE.md`), 구현·실기기 [미검증]. 잔여 미지(3시도 전멸 시 세션 레벨 원인)는 TYPE_VIEW_CLICKED 포렌식으로 특정 후 스텝 되감기 재검토
21. PROFILE 보정 생략으로 verify 측정값(residual 122~224)은 보고 전용 — 컨트롤 오버레이 오염이라 신뢰 낮음. → #12 설계에서 verify = "최저 신뢰 컴포넌트" 판정 (은폐·오염 양방향 실측), 사후 보정 단독 방어 기각 근거 (DESIGN_12 §2). 적응형 residual(글로우 은폐 대응)은 §5 측정 로깅 데이터 수집 후 v1.5
22. 버블 오버레이 존재 시 피커發 파트너가 전체화면 낙착하는 **메커니즘 불명** (One UI WM 라우팅 추정) — 현재 경험 법칙(세션 중 버블 숨김)으로 해소. One UI 업데이트 시 재검증 필요
23. P3-1 잔여: ~~BootReceiver 실부팅 복귀, specialUse FGS 의 BOOT_COMPLETED 시작 허용~~ → **2026-07-25 오후 5차 실부팅 검증 통과** (BOOT_COMPLETED 수신 로그·FGS 자동 기동·접근성 유지·버블 가시 전부 확인). 잔여 → **16차 전부 해소**: 제스처 무불만·오분류 0(시스템 표준 임계 확정), 타이머는 이론 최악 ≈70s 산정으로 90s 상향 (DEVICE_FACTS 16차)
24. ~~P3-2 [미검증] 전체~~ → **2026-07-25 오후 4차 실기기 검증으로 전부 해소**: ① 드래그 해제 가정 **반증** (dispatchGesture 스냅백 2/2 vs 동일 기하 input swipe 성공 3/3) → **패널 finish 방식으로 재구현·E2E 성공** ② 클램프 가로/세로 실기기 정상 ③ DOWN 스냅샷 방어 **실패 실측** (OUTSIDE 선행 디스패치 → 재탭이 배치 오발화) → **풀스크린 스크림으로 구조 해결·E2E 확인** ④ 프리셋 6종 최초 롱프레스에 정상 렌더. 잔여 [미검증]: dismissSplit 인텐트 폴백(instance null 희귀 경로), "분할 없음" 시 2s 대기 후 토스트 체감
25. 스크림 부산물 (해결·기록): 풀스크린 터치 가능 오버레이가 떠 있는 동안 하위 창이 a11y `getWindows()` 에서 가림-제외되고, 제거 직후 재구축이 **비원자적** (앱 창 먼저, 디바이더 나중) — `isSplitActive` false-negative 2/2 실측. dismiss 는 목표 조건 자체 폴링으로 해결. `beginSession` 의 `awaitWindowsSettled`(APPLICATION≥1 약한 게이트)도 같은 원리에 취약할 수 있음 — 배치 경로에서 유사 증상 재현 시 동일 패턴 적용. **10차 부수 관측**: broadcast 트리거 15세션(버블/스크림 창 미개입 경로)에서 창 목록發 증상 0건 — 취약 가설은 스크림·버블 창 존재 시나리오에 한정된 채 유지
26. ~~P3-3 실기기 [미검증] 목록~~ → **2026-07-25 오후 7차 실기기 검증으로 전부 해소** (DEVICE_FACTS P3-3 절): ① 이관 — 구버전에 x/y 주입 후 업데이트 설치, 3키 무손실 이관+원본 삭제+버블 위치 복원 ② goAsync 실부팅 — 로그·FGS 기동·접근성 유지 (회귀 해소) ③ placement — OVERRIDE bottom 저장 → 무override 3회 전부 LAST_SUCCESS/BOTTOM 결정, 3회차 done residual=0 ④ corruption — 가비지 주입 후 서비스 기동, 손상 감지 로그+emptyPreferences 복구+무크래시, enabled 재기록 ⑤ 중지 레이스 — 탭+즉시 back(finish) 근사, 쓰기·stopService 완주 → **16차 물리 폴드 접기 실기 통과** (잔여 0). 부차 미해결: 손상 1차 감지가 주체 미상 초기 store 접근에서 발생 (결과는 동일한 정상 복구)

27. **[17차 신규 → 18차 원인·설계 확정 → 19차 실기기 종결 (2026-07-28 밤). ✅ 해결]** 최종 채택 = **축 A(파괴 제거) + #28**. **축 B(소환)는 기각·코드 제거.** 축 A 구현분: A1 `finish()` 격하 5곳 + A2 `pruneExtraPanelTasks`(MRU 1개 보존) + `domain/PanelTaskPolicy.kt`. 19차 실기기: **G4** 커버 해제 후 카드 생존 + 3/3 done · **G5** `보존 1 / 제거 1` 후 step3 성공 · **G6** 재설치 죽은 카드로 done · **G7** AOSP 강제 제거 함정 미발동(`RETAIN_IN_RECENTS` 상쇄 추정) · **G2 ❌** 소환 카드가 step3 를 깨뜨림. 축 B 기각 근거 2건 [확정]: ① base intent 오염(`NEW_DOCUMENT|MULTIPLE_TASK` 보존 → 피커 탭이 새 문서=전체화면으로 라우팅. 대조군 런처 카드 `flg=0x10000000` 은 정상) ② 전제 반증(카드 0 에서 5/5 done — `pm clear` 표본이 신규 설치 교란 배제. 피커는 앱 목록에서도 노드 제공 ⇒ DESIGN_27 §1.3 「만드는 경로 0개」 부정확). 제거 후 282 PASS + 실기기 3/3 무회귀. `PanelTaskPolicy.needsSummon` → **`hasPanelTask`** 개명·반전(용도 = #28 폴백 가드). **[미해결]** 17차 3전멸 원인 — 카드 0 가설 폐기, 재발 시 재조사. 이하 원 기록: step3 패널 소환 경로 부재: 피커 「FW Panel」 노드 = recents 태스크 카드. `finishAndRemoveTask`(커버 자동 해제 P4-3·dismissSplit·purgeStalePanelTasks·패널 자가 가드)가 카드 제거 시 배치 전체 불능 (node-not-found 3전멸 ×4 재현). purge 는 세션 시작마다 자충(살려둔 패널 태스크도 제거). 복구는 런처 경유 실행(수동 피커 탭)만 유효 — `am start` 직접 실행은 자가 가드가 다시 제거. → **설계 확정 `docs/DESIGN_27_PANEL_CARD.md`**: 구조 진단 = 카드를 지우는 경로 4개 / 만드는 경로 0개(생산 없는 소비). 축 A(파괴 억제) 단독은 **불충분** — 재설치·강제종료·수동 스와이프 등 앱이 통제 못 하는 소멸 경로가 남아 결함 클래스 존속. → **18차 adb 프로브(코드 무변경)로 판정 완료**: ① purge 자충 단일 로그 시퀀스 재현(카드 1→purge→0→`ENTRY_STEP_FAILED`) ② **G1 통과** — `finish()`(BACK)는 분할 해소하면서 카드 잔존, 피커 MRU 1번 재출현 ③ **G3 통과** — 액티비티 죽은 카드(`Activities=[]`) 탭도 `stage=side/bottom` 정상 낙착·taskId 재사용·전체화면 강탈 0 ⇒ **purge premise 반증**(그 근거는 singleTask 시절·버블 숨김 이전 실측) ④ 피커 `all_apps_button`/`search_button` 은 resource-id 라 로케일 무관이나 앱서랍 1페이지 부재·scrollable 0 → 후보 ③ 취약 확정. **주 수정 = 축 A(파괴 제거)** 로 전환, 축 B 는 카드 0 안전망(B0 `addAppTask()` 1순위). 잔여 프로브 G2·G4·G5·G6·G7

28. **[17차 신규 — 18차 AOSP 확인 [확정] → 수정 완료(2026-07-28 밤)]** 수정분: `performDismissSplit` 폴백을 3분기로 확장 — `instance!=null → finish()` / `instance==null ∧ hasPanelTask() → 기존 인텐트 폴백` / `instance==null ∧ 패널 태스크 부재 → **폴백 생략**`(로그 + 토스트 "해제할 FoldWindow 패널을 찾지 못했습니다", settle 폴링 중복 토스트 방지 위해 조기 return — try/finally 안이라 버블 복원 정상). 판정은 `PanelTaskPolicy.needsSummon` 재사용(신규 도메인 함수 0). 부수로 헬퍼 2종 추출 — `panelTaskSnapshots(rawTasks: List<AppTask>? = null)` / `hasPanelTask()` (축 B 소환이 재사용). `EXTRA_FINISH_PANEL` KDoc 에 계약 명시(태스크 신설 가능 인텐트에 탑재 금지 · 소환 인텐트는 extras 무탑재). `pruneExtraPanelTasks` 동작·로그·호출 위치 불변 확인. 282 테스트 PASS. **희귀 경로라 실기기 재현은 인위적 유도 필요 [미검증]**. 이하 원 기록: `performDismissSplit` 인텐트 폴백의 base intent 오염: `FLAG_ACTIVITY_NEW_TASK` + `EXTRA_FINISH_PANEL` 조합이라 패널 태스크 **부재** 상태에서 이 경로를 타면 `EXTRA_FINISH_PANEL` 을 base intent 에 담은 태스크가 새로 생기고, 이후 피커 탭이 그 카드를 재실행하면 `onCreate` 가 즉시 종료 → **step3 영구 실패 루프**. 발생 조건은 희귀(`instance==null` ∧ 태스크 부재 ∧ 분할 활성 판정 통과)하나 자기 코드다. **AOSP 확인**: 죽은 카드 탭 → `startActivityFromRecents` → `task.intent` 재실행이고 `Task#setIntent` 은 **extras 를 그대로 보존** → 실재 확정. 수정 = 폴백 진입 전 패널 태스크 존재 확인, 없으면 폴백 생략(태스크 부재 = 해제할 우리 분할 없음) + 소환 인텐트는 extras 무탑재 계약 — DESIGN_27 §4. 재부팅 후 디스크 복원 경로의 extras 보존 여부는 [불명]

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
| 2026-07-25 오후 6차 | P3-3 이관 = `SharedPreferencesMigration("bubble_prefs")`, 레거시 키 이름(`bubble_enabled`/`bubble_x`/`bubble_y`) **동결 계약** — 회귀 테스트로 고정 | 마이그레이션이 키 이름 그대로 이관 + 원본 파일 삭제. 이름이 어긋나면 기존 사용자 설정 유실. 직접 `getSharedPreferences` 호출은 리포지토리에서 0건으로 소거 (잔존 사용처 = 삭제된 파일 부활 버그) |
| 2026-07-25 오후 6차 | placement 복원 우선순위 = override > **last-success** > profile > defaults > TOP. 저장은 Done ∧ effective==desired 만 | 마지막 실사용 선택이 정적 JSON 보다 사용자 의도에 가까움. 스왑 실패로 낙착한 위치(effective≠desired)를 저장하면 사용자가 고르지 않은 값이 기본값을 오염 (#19/#20 실측 존재). placementSource 로그로 어느 티어가 이겼는지 노출 |
| 2026-07-25 오후 6차 | ProfileStore 쓰기 = `NonCancellable` + `ReplaceFileCorruptionHandler`→emptyPreferences. 측정 종횡비 캐싱은 P3-3 에서 **제외 유지** | 리뷰 실지적: 레거시 apply() 는 컴포넌트 종료 후에도 QueuedWork 로 반영 보장 — 코루틴 취소가 쓰기 유실/stopService 미호출 회귀를 만듦. corruptionHandler 부재는 부팅 크래시 루프 위험. 캐싱은 #12 신뢰도 필터 전엔 오염 고착 위험 (실측 사고 2회) |
| 2026-07-25 오후 6차 | BootReceiver = goAsync + IO 코루틴, `finally { pendingResult.finish() }` | 메인 스레드 동기 IO 제거. 부팅 시점 조용한 실패 금지 의미론(로그만·크래시/토스트 금지)과 검증된 로그 시그니처는 불변. 실부팅 재검증 필요 (#26) |
| 2026-07-25 저녁 8차 | #20 대응 = **클릭-사이클 에스컬레이션** (매 디스패치 직전 성공조건 선체크 + 검증 슬라이스 800ms 폴링 + 사이클별 메커니즘 전환: 피커 gesture→gesture→a11y / 스왑 a11y→gesture→gesture) + **step3 LAUNCH_ADJACENT 폴백 삭제** + PaneSwapper 정착 게이트(디바이더 bounds 2연속 동일). 구현 전 [미검증] | AOSP 소스: ACTION_CLICK true 는 isClickable 만 보장·performClick 결과 폐기·히트테스트 우회 (View.java 검증). 실측: 무효 클릭 전부 true 반환 → 기존 폴백 도달 불가. LAUNCH_ADJACENT 는 분할-선택 파괴 실측·구조 성공 0회·예산 ~0ms 발화·재시도 오염원. 검증 상수 무변경, 메커니즘 순서 = 데이터(1줄 롤백 레버). 상세 `docs/DESIGN_20_CLICK_CYCLE.md` |
| 2026-07-25 저녁 10차 | **LAUNCH_ADJACENT 삭제 확정** + 클릭-사이클 설계 실기기 채택 확정 (피커 gesture-first·스왑 a11y-first 프로파일 유지, 롤백 레버 미사용) | Gate 1~3 실측: 15/15 done·ENTRY_STEP_FAILED 0·전략2 부재 회귀 0. cycle-1 회복 1회 실증 + FORENSIC 오착지 특정. 회전 여파 스왑 4/4 (과거 무효 컨텍스트 동형 전승). DEVICE_FACTS 10차 절 = SSOT |
| 2026-07-25 저녁 11차 | #12 = **2-샷 합치 게이트** (pre 행축 × 진입 후 confirm 열축·페인 크롭, relΔ≤3% 합치 시만 MEASURED, 불합치·확인불가 = PRESET 폴백 + verify 보정 수렴). 임계 상향·대칭성 휴리스틱·verify 강화 단독·pre 2연속 전부 기각. 머신 무변경 (handleDragDividerTo 선두, realTargetY 선례). 토글 `defaults.requireMeasurementAgreement` 기본 true. aspectOverride 는 tier 0 승격 (측정이 사용자 "강제"를 이기는 인접 결함 수정, 보정도 생략). 캐싱 admission = 합치∧verified. **구현 완료 (qa PASS 182/182·머신 diff 0)** |
| 2026-07-25 밤 11차 | #12 실기기 G1~G5 **전부 통과 — 합치 게이트 채택 확정**. 현장 수정 2건: `toPillarboxScan` sideMarginPct 0.005 + edgeMarginPct 0.12, `classifyAxis` minConfidence — 전부 **스캔 입력 범위** 수정이고 도메인 판정 상수(ADAPTIVE_* 4종)는 무변경 | 오염 = 상수가 아니라 입력 문제 (픽셀 물증: 최외곽 열 var 404~501 / 크롬 아이콘 luma 176~188·raw 소수 7자리 동일 재현 = 정적 물증). 수정 후 클린 3/3 SNAP_AGREE·사고 클래스 3종 차단·tier 0 정상. 함정 #7 준수 — 상수 대신 입력 정화. DEVICE_FACTS 11차 절 = SSOT | 오염 프레임 conf 0.60~0.97 실측 — 단일 프레임 점수는 "프레임이 영상인가"를 측정하지 않음. 오염원은 전부 일시적 ↔ 띠는 지속 → 시간·축·컨텍스트 분리가 유일하게 견고한 신호. 오탐 비용(보정 1회) ≪ 미탐 비용(오배치 고착, 1.6 사고 실증). 상세 `docs/DESIGN_12_MEASUREMENT_CONSENSUS.md` |
| 2026-07-26 12차 | #12 §6 측정 캐싱 v1 = `AspectSource.CACHED` 티어 (MEASURED 아래·PRESET 위) + admission "합치∧verified∧레버" + 불합치 폴백도 cached 우선 + 무 TTL last-write-wins + 레버 `defaults.cacheMeasuredAspect`(부재=true). CACHED 세션은 confirm 미실행·자기 갱신 없음 | 캐시 = 같은 앱의 이중 검증된 사전값 — §3.5 의 PRESET "사전확률" 논증을 그대로 승계하되 정보량 우위, 오류 비용은 보정 1회로 동일(CACHED 도 closedLoopCorrection ON). requireAgreement=false 포함 confirm 미실행 세션은 구조적으로 저장 불가 — 단일 프레임 값 유입 차단(오염 고착 사고 2회 재발 방지). pre×캐시 합치로 confirm 샷 생략(+0.3s)은 G1~G5 검증 직후 새 채택 경로 부담으로 기각(v1.5 재고). qa PASS 204/204 결함 0 |
| 2026-07-26 13차 | #12 §6 측정 캐싱 **실기기 검증 4항목 전부 통과 — v1 채택 확정** (코드·상수 무변경, 현장 수정 0건) | 10 세션 10/10 done. 저장 admission·CACHED 낙착(폴백/decision 양 지점)·자기 갱신 차단·레버 회귀 전부 로그+pb 물증. 원복 세션에서 사고 클래스 1.333 오염이 캐시 폴백으로 무해화 — §6 기대 효용 실전 재현. pre-null 유도법(세로 영상 immersive)·`cmd window user-rotation` 함정은 DEVICE_FACTS 13차 절 기록 |
| 2026-07-26 14차 | P3-5 = placement 체인 **FLEX 티어**(OVERRIDE 다음·LAST_SUCCESS 앞) + 자동 트리거는 `startArrange(null,null)` 로 동일 티어 경유(자동·수동 단일 메커니즘) + FLEX 세션 last-success 저장 억제(종횡비 캐시는 직교라 유지) + 레버 `flexAutoTopPlacement`(부재=true) + 게이트 거부 시 disarm·로그만(토스트 없음) + 디바운스 800ms·진입당 1회 arm | 노트북 자세에선 하단 페인이 책상에 눕는 물리 — TOP 강제가 옳고 명시 override 만 예외. 자동화 결정값이 사용자 선호(last-success)를 오염 금지(P3-3 저장 조건 원칙 연장). 자동 스킵은 사용자 시작 행위가 아니라 조용한 실패 금지 비대상. 닫기 동작의 HALF_OPENED 일시 통과 오발화는 800ms 디바운스+display-off 게이트 2중 방어. UiContext 수용 불확실은 폴백 체인+기능 무력 폴백으로 흡수(크래시 금지) — 전부 실기기 검증 대상 |
| 2026-07-28 15차 | UiContext = **3-인자 `createWindowContext(display, TYPE_ACCESSIBILITY_OVERLAY, null)` 채택** (서비스 자신·2-인자 전멸 실측). 구 2-인자 후보 삭제 | 에러 메시지가 해법 명시 — display 명시 연결만 UI 컨텍스트 성립. 실기기: 후보 ② 방출 수신, 격하 경로(전멸 시 무크래시)도 실증 |
| 2026-07-28 15차 | 닫기 오발화 방어 = **힌지 각도 안정성 게이트** (시간 상수 증액 기각) + **FLEX 세션 자세-이탈 취소** 2층 | 닫기 체류 실측 ~2s(3표본) — 800ms 디바운스 반증, display-off 게이트는 닫는 중 화면 켜져 무력. 상수 증액은 느린 닫기 꼬리에 재차 뚫리는 타이밍 도박 → ADR-2 정신대로 조건 기반(각도 정지 = 속도 무관 신호). 멈칫 동반 느린 닫기는 게이트 통과 불가피 → 자세 이탈 = 의도 번복 신호로 진행 중 세션 취소 (기존 cancel 경로 재사용, 머신 무변경). 정상 닫기 2/2 차단 + 취소 1/1 + Done-후 유지 전부 실증 |
| 2026-07-28 15차 | FLEX 라벨 의미론 확정 (qa CONDITIONAL 회신): FLEX = "위치를 자세가 자동 결정" — **트리거 출처 무관** (버블 탭 무override 포함). 취소·저장억제 대상 기준 = 이 라벨. OVERRIDE(명시 위치)만 불가침 | 위치 결정 근거가 물리 자세라면 근거 소멸(이탈) 시 취소가 정합. 사용자 "명시 선택"과 "알아서" 위임의 경계가 원칙적 보호선 — 14차 단일 메커니즘 결정의 연장 |
| 2026-07-28 15차 | 재열기 멈칫(대역 내 ≥1.4s 정지) 오발화는 **수용**, 키가드 게이트 기각. Done 후 자동 되돌리기 없음 | 물리 신호만으론 정당한 "닫힌 채→노트북 자세로 열기"와 구분 불가. 키가드 게이트는 그 정당 사용례(잠긴 채 자세 잡고 얼굴 인식)를 죽임. 늦은 펴기 시 완주 분할 잔존은 수동 해제로 복구 — v1.5 재검토 |
| 2026-07-28 P4 | Phase 4 스코프 = P4-2·P4-3·P4-4 즉시 구현, **P4-1 은 설계 문서+프로브(F1~F6) 선행 후 구현** (`docs/DESIGN_P41_FREEFORM.md`) | freeform 실행 메커니즘(One UI 8 이 셸 `--windowingMode` 를 수용하는지 등)이 전부 미측정 — 맹목 구현은 #12/#20 선례(설계→프로브→구현) 위반. 기기 미연결로 프로브는 다음 세션 이월 |
| 2026-07-28 P4 | P4-3 발화 = `dismissSplit()` 미경유, `PanelActivity.instance` 직접 `finishAndRemoveTask()`. 자동 스킵/실패 = 로그만(토스트 금지) | dismissSplit 의 isSplitActive 2s 폴링은 커버 디스플레이 a11y 창 목록 상태가 미지수라 신뢰 불가. 패널 finish = 분할 해소 트리거는 실측 확정 사실(P3-2). 자동 트리거 스킵은 조용한 실패 금지 비대상(P3-5 flex 선례) |
| 2026-07-28 P4 | P4-3 수동 dismissSplit × 커버 자동 해제 레이스 = 게이트 미추가 수용 | 양 경로가 동일한 패널 finish 로 수렴 — 이중 finish 무해. 발생 창도 "해제 메뉴 탭+600ms 내 완전 접기"로 극소. 세션 중 접기로 게이트에 막힌 닫힘 에피소드는 소진(재열기 전 패널 잔존) — v1 한계로 수용, 17차 관찰 |
| 2026-07-28 P4 | P4-2 위젯 모드 = ui 전용 enum, 도메인 `PartnerMode{BLACK}` 비확장. 자막 위젯 v1 제외 | PartnerMode 는 JSON 프로파일 스키마 소속 — UI 표시 선호와 결합하면 시드 의미론 오염. 자막은 미디어 세션 의존 투기 구현이라 범위 밖 |
| 2026-07-28 P4 | P4-4 = 자체 트램펄린 pinned shortcut(삼성 App Pair 포맷 비의존) + 전면 대기 = **머신 밖 사전 조건 폴링**(150ms/5s) + placement 는 기존 체인 재사용 | 삼성 App Pair 포맷은 비공개 런처 내부라 재현 불가. 사전 폴링을 머신 밖에 둬 상태 머신 무변경 원칙 유지. 새 placement 티어 금지(자동화 값이 사용자 선호 오염 금지 원칙 연장) |
| 2026-07-28 P4-1 | 실행 경로 = **후보 A(Shizuku 셸 명령) 채택, 후보 B(binder/HiddenApiBypass) 기각** | 프로브 F2·F3 통과 — `am start --windowingMode 5` + `am task resize` 픽셀 정확 실측. hidden API 무접촉이 유지비용 최소 |
| 2026-07-28 P4-1 | Shizuku 실행 = **UserService(AIDL) 방식**, `Shizuku.newProcess` 사용 금지 | `newProcess` 는 비공개 API — 버전 간 파손 위험. UserService 는 공개 지원 경로, shell uid 프로세스에서 `sh -c` 실행으로 동일 능력 |
| 2026-07-28 P4-1 | `StackListParser` 를 **domain/ 에 배치** (셸 출력 파싱) | 문자열 파싱은 android 비의존 순수 로직 — JVM 테스트 표면 확대 원칙. 실기기 원문 46행 대조 10/10 + 원문 픽스처 테스트로 회귀 방어 |
| 2026-07-28 P4-1 | 팝업 경로는 `ArrangeStateMachine` **비사용** — 단순 명령 + 검증 폴링 5단(창 출현→taskId→resize→bounds 검증) | 진입 스텝이 셸 명령 1개라 머신이 과잉(설계 문서 §4 미결 → 확정). 머신 무변경 원칙 유지. 각 단계 타임아웃 + 명시적 실패(ADR-2) |
| 2026-07-28 18차 | #27 주 수정 = **축 A(파괴 제거)**. A1 `finishAndRemoveTask()` → **`finish()` 격하** 4곳(커버 해제·dismissSplit instance 경로·자가 가드·EXTRA_FINISH_PANEL). A2 `purgeStalePanelTasks` → **`pruneExtraPanelTasks`**(MRU 패널 태스크 1개 반드시 보존, 나머지만 제거) | **G1 실측**: BACK(=finish)가 분할을 해소하면서 카드를 남기고 피커 MRU 1번으로 재출현 — P3-2 가 확정한 해소 트리거는 애초에 finish 였고 `removeTask` 는 **어떤 실측에도 요구되지 않은 초과 동작**. **G3 실측**: 액티비티 죽은 카드(`Activities=[]`) 탭도 `stage=side/bottom` 정상 낙착·taskId 재사용·전체화면 강탈 0 ⇒ **purge premise 반증**(그 근거는 singleTask 시절·버블 숨김 이전). purge 비용은 단일 로그 시퀀스로 물증화(카드 1→purge→0→ENTRY_STEP_FAILED). 새 측정 근거를 DEVICE_FACTS 18차에 기록해 함정 #7 준수 |
| 2026-07-28 18차 | #27 축 B(소환)는 **주 수정이 아니라 카드 0 안전망**으로 격하. 배치 = `beginSession` 말미 `dispatch(Start)` 직전, 머신 밖 사전 조건 폴링(P4-4 선례). 수단 순위 **B0 `ActivityManager.addAppTask()`** → B1 `makeTaskLaunchBehind` → B2 `EXTRA_PRELAUNCH_CARD` 1회성 소비 + `moveTaskToBack` → B3 피커 앱그리드. 레버 `defaults.panelCardPreflight`(부재=true, 소환만 제어) | 축 A 만으로는 앱이 통제 못 하는 소멸(재설치·강제종료·수동 스와이프·`isTrimmable=true` 시스템 트리밍)이 남아 결함 클래스 존속. B0 = AOSP javadoc *"recents entry … will exist without an activity"* — 액티비티 미시작이라 포그라운드 무접촉·자가 가드 무관, NEW_DOCUMENT+RETAIN_IN_RECENTS 필수. 소환 시점은 분할-선택 진입 **전**이어야 하며(LAUNCH_ADJACENT 파괴와 동일 클래스 위험) beginSession 배치가 구조적으로 위반 불가. 세션 스코프 가드 억제는 실패 시 전체화면 강탈이라 1회성 소비가 더 좁음. `onPause` 가 가드를 취소하는 성질에 기대는 구현은 ADR-2 위반이라 금지 |
| 2026-07-28 18차 | #27 후보 ②(purge 를 Done 후로 이동) → **A2 로 대체 흡수**, ③(피커 앱그리드) **최후 폴백 유지** | ② 의 문제의식(자충)은 옳았으나 이동이 아니라 **범위 축소**가 정답 — G3 로 premise 자체가 반증됐으므로 "언제 지우나"가 아니라 "무엇을 남기나"가 축. ③ 은 `all_apps_button`·`search_button` 이 resource-id 라 로케일 무관(#6 우려 해소)이지만 앱서랍 1페이지에 FW Panel 부재·`scrollable` 노드 0 실측 → 페이징/검색 없이 도달 불가 |
| 2026-07-28 18차 | 신규 함정 기록: **`!hasChild() && !getHasBeenVisible()` 이면 `autoRemoveFromRecents=false` 라도 recents 에서 강제 제거** (AOSP `Task#shouldAutoRemoveFromRecents`) | 소환 카드가 "한 번도 보인 적 없는" 상태로 finish 되면 manifest 설정과 무관하게 사라진다. B0(액티비티 미생성)는 비대상이지만 B1/B2 설계 시 필수 고려. recents 오버뷰엔 안 보이는데 분할 피커엔 보이는 실측 불일치도 이 플래그 취급 차이로 추정 |
| 2026-07-28 18차(구현) | #27 축 B 소환 수단 = **B0 `addAppTask()` 배제, B1 `makeTaskLaunchBehind()` 채택**. 최종 플래그 `NEW_TASK\|NEW_DOCUMENT\|RETAIN_IN_RECENTS` + extras 무탑재. G2 프로브 대상도 B1 로 교체 | **컴파일 판정**: `addAppTask` 1번 인자가 `Activity` 인스턴스 요구 — `Argument type mismatch: actual type is ArrangerAccessibilityService, but 'android.app.Activity' was expected`. 호출부는 AccessibilityService 이고 **소환이 필요한 상황은 정의상 `PanelActivity.instance` 도 null** 이라 빌려올 Activity 가 존재하지 않음 = 구조적 불가. 리플렉션/hidden API 우회는 P4-1 의 "hidden API 무접촉이 유지비용 최소" 원칙과 충돌해 기각. B1 은 Q3(AOSP 확정)로 `onResume` 미호출 → 자가 가드 구조적 무발화, `NEW_DOCUMENT` 의 `autoRemoveRecents=true` 는 `RETAIN_IN_RECENTS` 로 상쇄. 설계 §2.2 Q5 가 능력만 보고 시그니처 제약을 누락한 사례 — 설계 문서 §3.2·§6 갱신 완료 |
| 2026-07-28 18차(구현) | #27 v1 = 소환 실패해도 **세션 계속**, 사유는 `lastPanelCardOutcome` 으로 ENTRY_STEP_FAILED 토스트에만 병기. G7(소환 카드 미가시 finish → AOSP 강제 제거) 은 **가드 없이 재소환 자기치유에 위임** | 소환은 보험이지 전제가 아니다 — 카드가 다른 경로로 있을 수 있는데 소환 실패로 배치를 죽이면 안 된다(`FailureReason` 무확장 = 머신 밖 사전 조건이라는 설계 의미론 유지). G7 가드는 커버 해제·자가 가드와의 상호작용을 세션 스코프로 넓히게 되는데, 그 확장은 실패 시 전체화면 강탈 위험(§3.2 B2 논의와 동일 클래스) — 비용 대비 이득이 없다. 재소환은 다음 세션 `beginSession` 에서 비용 0(카드 존재 시 `already-present` 즉시 반환) |
| 2026-07-28 19차 | #27 **축 B(소환) 기각·코드 제거**. 최종 v1 = 축 A + #28 + `PanelTaskPolicy`. 레버 `panelCardPreflight` 도 함께 제거, `needsSummon`→`hasPanelTask` 개명·반전 | **① 유해 실증(대조 실험)**: `makeTaskLaunchBehind` 카드는 base intent 가 `flg=0x18182000`(NEW_TASK\|MULTIPLE_TASK\|NEW_DOCUMENT\|RETAIN)이라 피커 탭 시 **새 문서=전체화면**으로 라우팅 → 자가 가드 3회 → `ENTRY_STEP_FAILED`. 동일 세션 대조군인 런처 형태 카드(`flg=0x10000000`)는 정상 분할 낙착·`done residual=0`. **#28 과 동일 결함 클래스(대상만 extras→flags)** — 소환은 그 함정을 스스로 밟았다 **② 전제 반증**: 카드 0 에서 배치 **5/5 done**(uninstall 재설치 3 + `pm clear` 2 — 후자가 "신규 설치라 피커에 노출" 교란 배제). 피커는 recents 카드가 아닌 **앱 목록**에서도 「FW Panel」을 제공 ⇒ DESIGN_27 §1.3 「만드는 경로 0개」가 부정확. 전제가 죽고 구현이 해로운 안전망은 유지 비용만 남는다. 제거 후 282 PASS + 실기기 3/3 무회귀. DEVICE_FACTS 19차 절 = SSOT |
| 2026-07-28 19차 | #27 축 A **채택 확정** (G4·G5·G6·G7 전부 통과) | 17차 결함 시나리오가 재현되지 않음 — 커버 자동 해제 후 **카드 생존** ∧ 이어진 3연속 배치 3/3 done ∧ 전부 `already-present`(소환 불필요) ∧ `node-not-found` 0. prune 은 오염 카드만 제거하고 MRU 보존(`보존 1 / 제거 1`), 재설치 죽은 카드(`Activities=[]`)도 정상 낙착 — 18차 G3 premise 반증을 실사용 경로에서 재확인. G7 함정은 `RETAIN_IN_RECENTS` 로 미발동 관측 |
| 2026-07-28 16차 | 버블 숨김 안전 타이머 30s→**90s** | 이론 최악 세션(MENU 5스텝×3시도×3s + 디바이더 4s + 드래그 12s + verify) ≈70s > 30s. 조기 복원 = 세션 중 오버레이 재출현 = 함정 #22 자충수. 워치독 비대칭(늦은 복원 무해/이른 복원 유해) + 실측 최장 12s. 제스처 임계는 시스템 표준 유지 확정 (16차 무불만·오분류 0) |
