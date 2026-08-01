# PROGRESS.md

> Advisor가 매 작업 완료 시 갱신한다. 이 파일이 세션 간 상태의 단일 출처다.
> 상세 서술은 두지 않는다 — 측정값은 `docs/DEVICE_FACTS.md`, 계획 이력은 git log, 설계는 `docs/DESIGN_*`/`docs/ADR.md` 가 SSOT.

**상태 (2026-08-01):** Phase 0~4 전 항목 구현 + 실기기 검증 완료. **✅ AAA UI/UX 품질 캠페인 완료**
(5표면 비평 PASS, 커밋 `24afc4a`~`da11fed`) — **24차 실기기 검증에서 10항목 중 8항목 소진, 전부 PASS.**

그 과정에서 **기존 결함 2건 신규 발견 — 둘 다 수정·실기기 재검증 완료**(캠페인 이전 코드에도 존재, 회귀 아님).
**①** 메뉴 경유 배치의 `isSplitActive` 오판정 → 비대칭 정착 술어.
**②** 가로 최소 페인이 563px 인데 상수가 181 → 실측 교체 + 클램프 고지(「4:3 구형」 무고지 해소). 아래 **A'''** 참조.
잔여는 선택 항목(B/C/E) 또는 사용자 손이 필요한 항목(F), 원인 미상 재조사 대기(D).

---

## ✅ AAA 품질 캠페인 — 완료 (2026-08-01)

**목표:** 5개 사용자 대면 표면(버블/메뉴·파트너 패널·온보딩/테마·메시지·문서)을 상용 최고 앱 기준으로.
**방법:** Worker 전면 개편 → 엄격 비평가(읽기 전용, **모든 수치 주장 재계산**) → 조준 수정 → 재비평, PASS까지.

| 라운드 | 내용 | 결과 |
|---|---|---|
| R1 전면 개편 (5 워커) | 5표면 병렬 | 빌드 ✅ |
| R2 비평 (3) | 상용 기준 판정 | 3/3 FAIL — 결함 49건 |
| R2.5 수정 (3) | 49건 + lint/deprecation 3건 | 빌드 ✅ |
| R3 재비평 (3) | 수정 성립 검증 + 회귀 사냥 | 3/3 FAIL — 잔여 좁혀짐 |
| R3.5 수정 (3) | 표면별 외과적 수정 | 워커 3명 도중 정지(사용자 지시 중단) |
| **R3.5 재개 (3+1+3)** | 자체 grep 감사 → OPEN 만 수정 → 비평 | **3/3 PASS** — 심각 0, minor 4 |
| R3.6 정리 (1) | minor 4건 + 파생 1건 | **잔여 0** |

**최종 트리 상태: 단위 테스트 372/372 ✅ · assembleDebug ✅ · lintDebug 신규 이슈 0 ✅**
(`--rerun-tasks` 로 캐시 없이 Advisor 가 직접 재검증. JUnit XML 합산 failures=0 errors=0)

### 이 캠페인의 지배적 결함 클래스 — **주석에 적힌 수치가 거짓**

49건 중 다수, 그리고 마지막까지 남은 5건이 전부 이것이었다. 색·치수·대비를 바꿀 때
주석의 숫자를 **인용**하고 재계산하지 않은 것이 누적된 결과다. 재발 방지 규칙:

- 대비값을 적거나 고칠 때는 WCAG 공식으로 **실행 계산**한다(눈대중·기존 주석 인용 금지).
- **알파 합성 주의**: 순검정 배경에서는 합성 채널 = `alpha × 원본채널` 을 sRGB 공간에서
  곱한 **뒤** 선형화한다. 반올림도 오차원이다 — `0xE6` 은 0.90 이 아니라 0.901961 이다
  (이 반올림 하나가 5.126 vs 5.143 을 만들었다).
- 실측 상수를 인용할 때는 `docs/DEVICE_FACTS.md` 를 대조한다(내부 화면은 840dp 가 아니라 **875dp**).

### 표면별 착지 결과

- **① 패널** (`PanelActivity.kt`): 휘도 사다리 12행 전부 재계산 검증(캐럿 7.41 / MEMO 본문 6.60
  최상위 / 날짜 1.83 …)하고 **수치와 모순되던 계약 문장**을 사실에 맞게 수정 — "콘텐츠 > 크롬" →
  "그 모드의 **주역** 콘텐츠 > 크롬 상한", BLACK 모드는 시계가 없으므로 칩이 유일 발광체.
  `PaneSelectionColors` 의 허위 1.9:1 → 3.71:1 정정. 저장 표시 접근성은 조건부 컴포지션으로
  이미 닫혀 있었고(근거를 KDoc 에 명문화), 좌측 빈 `Text` 의 TalkBack 무음 정지는
  `clearAndSetSemantics` 로 제거(레이아웃 무영향).
- **② 온보딩** (`OnboardingActivity.kt`·`Theme.kt`): F1~F11 전항 착지 확인. F8 은 래핑 `Column` +
  AV 내부 패딩으로 접힘/펼침 양쪽 20dp 수렴(exit 애니메이션 유지), F11 죽은 축소 분기는
  도달 불가 증명(W≥2.6h vs 필요폭 2.46h) 후 삭제. F10 다크 히어로 outline 대비 3.15:1 확보.
  후행 화살표는 `Icon` 대신 글리프 + `clearAndSetSemantics` 유지 — 벡터 에셋/의존성 추가 없이
  같은 결과(프로젝트 전체에 `Icons.` 사용 0건, material-icons 의존성 미도입).
- **③ 버블/메뉴** (`FloatingLauncherService.kt`): 이번 재개의 주 작업(이전 워커가 편집 전 정지).
  **F1 = `isLongClickable` + `setOnLongClickListener` — TalkBack 사용자가 메뉴에 도달할 유일한 경로**
  (터치 리스너가 DOWN 을 소비하므로 터치 경로 무변화). F2 섹션 라벨 전용 색 `#DCEAE3`
  (불투명 표면 6.111:1 / 스크림 최악 5.143:1 — 종전 액센트는 4.162:1 로 AA 미달).
  F4 = **커밋 액션 3결과 모델**(즉시 / `deferredMenuAction` 으로 복원까지 이연 / onDestroy 폐기),
  정상 복원과 안전 타이머가 같은 경로로 수렴해 어느 쪽에서도 액션이 잊히지 않는다.
- **④ 메시지**: 사용자 문자열 전량 `strings.xml` 이관 + 제품 카피 재작성. 성공 토스트 억제
  (배치·분할해제 — 화면이 증거), 실패 메시지는 원인+행동 지시형.
- **⑤ 문서**: ~6,300줄 → ~2,900줄. 죽은 문서 5종 삭제, 측정값은 `DEVICE_FACTS.md` 로 이관(MISSING 0).

**HOLDS(비평가가 "성립한다" 판정 — 재구조화 금지)**: `panelSaveScope`·정직한 저장 표시 ·
`pendingMenuAction` 액션 원장 · `removeViewImmediate` 스윕(D20) · `menuAttached` 술어 ·
아이콘 22dp 프레임 통일 · 온보딩 히어로 대비/색 체계 · `Theme.kt` 팔레트 슬롯.

### ✅ 실기기 검증 — 24차에서 8/10 소진 (2026-08-01)

상세·근거 로그 = `docs/DEVICE_FACTS.md` 「24차」 절.

| 항목 | 결과 |
|---|---|
| 메뉴 행 탭→배치 1회(exit 애니메이션 개입) | ✅ `startArrange` 1회·busy 경고 0·residual 0 |
| 애니메이터 배율 0 | ✅ 액션 유실/중복 0 |
| **F4 이연 실행** | ✅ 이연→복원 실행, 액션 정확히 1회(세션 실패 경로에서도) |
| 아이콘 22dp 실렌더 | ✅ 프레임 49px, 라벨 좌단 예측 714 vs 실측 715 |
| 시계 계수 0.26 | ✅ 3점 스윕, 글리프/em 0.772~0.782 일치 + 96dp 상한 클램프 실증 |
| 패널 IME(하단 배치 MEMO) | ✅ 창 팬, 필드 273dp 가시(하한 120dp), 입력·복귀 정상 |
| 온보딩 커버 화면 폭 | ✅ 411.43dp×960dp 전 섹션 무결 |
| 버블 링 55% 시인성 | ⚠ 조건부 — 아래 남은 작업 E |
| **TalkBack 롱클릭 메뉴 진입**(F1) + 토글 낭독 | ⬜ [미검증] — 아래 남은 작업 F |
| 패널 메모 좌상단 무음 정지 소멸(F6c) | ⬜ [미검증] — 아래 남은 작업 F |

> 「시계 계수 0.26/**3.4**」의 `3.4` 는 코드에 대응 상수가 없다(`git log -S` 0건). 실재 계수는
> 0.26(밴드)·0.19(날짜)·0.86/1.386(heightFit)·클램프 64/96 — 종전 항목명이 오기였다.

---

## Phase 상태

| Phase | 상태 | 비고 |
|---|---|---|
| Day 0 수동 검증 | ✅ 완료 | #1~#3 통과, #4 대체 불가 판정 |
| Phase 0 프로브 | ✅ 완료 | 실기기 측정값 `docs/DEVICE_FACTS.md` 확정 |
| Phase 1 도메인 | ✅ 완료 | SplitPlanner/LetterboxDetector/AspectResolver/ArrangeStateMachine 등 확정 |
| Phase 2 액추에이터 | ✅ 완료 | DRAG·MENU 레시피 E2E 검증 완료 |
| Phase 3 UI | ✅ 완료 | 버블·온보딩·프로파일 저장·FoldingFeature 자동 배치 전부 실기기 검증 |
| Phase 4 확장 | ✅ 완료 | P4-1(팝업/Shizuku)·P4-2(위젯)·P4-3(커버 자동 해제)·P4-4(앱 페어 바로가기) 구현+검증, #27/#28/#29 결함 종결 |
| 개선 웨이브 W0~W7 | ✅ 완료 | 23항목, 20차 캠페인으로 28/31 실기기 검증(3건은 계획된 자연 대기). 상세 = 아래 표 + `docs/DEVICE_FACTS.md` |
| #30 전체화면 자동 트리거 | ✅ 완료 | 21·22·23차로 W0 7/8·W1 11/11 검증, 결함 #31 발견·수정·재검증 |
| #32 런처 진입점 통합 검토 | ✅ 검토 완료(코드 변경 0) | 결론은 아래 남은 작업 C 참조 |

**개선 웨이브 커밋**: W2=`1d1e0bd` W3=`8edcce3` W4=`0f53af2` W5=`70e53fa` W6=`836efe0` W7=`a8a3522`(+`42f0c4a`=#29 수정). 계획·원 리뷰 문서(2026-07-29)는 23항목 전부 반영 완료 후 폐기했다 — 각 수정의 이유는 해당 `.kt` KDoc 에 인라인으로 옮겼다. 실기기 검증 결과(항목별 로그 근거) = `docs/DEVICE_FACTS.md` 「개선 웨이브 W1~W7」·「20차」절.

---

## 남은 작업 (재개 지점)

### A — 사용자 물리 조작·#30 실기기 캠페인: ✅ 전량 소진, 잔여 0

물리 접기(A-1)·DRM 육안(A-2)·#30 W0(7/8, 1건 유도 불가)·W1(11/11) 전부 완료. 상세 = `docs/DEVICE_FACTS.md` 20·21·22·23차 절.

### A'' — 결함 #31(홈 경유 래치 미해제): ✅ 22차에서 수정·재검증 완료

최종 해법 = 래치 2종 구분(`AutoTriggerLedger.latchSticky`, 순수 도메인) — 분할 해제가 건 래치는 sticky(홈으로 안 풀림), 자동 발화가 건 래치는 non-sticky(홈으로 풀림). 시간창 방식은 채택 안 함(ADR-2 위반). 실기기 4행 전부 통과, 상세 = `docs/DEVICE_FACTS.md` 22차 절.

### A''' — 24차 신규 발견 결함 2건 (기존 결함, 회귀 아님) — **미수정**

상세·재현 로그 = `docs/DEVICE_FACTS.md` 24차 절. 둘 다 캠페인 이전(`24afc4a^`) 코드에도 존재함을 확인했다.

**① 메뉴 경유 배치가 「기존 분할 위」에서 실패 — `isSplitActive` 오판정 → ✅ 24차에서 수정·재검증 완료**

> **해법 = 비대칭 정착 술어**(`awaitSettledSplitState`, `ArrangerAccessibilityService`). `handleQuerySplitState()`
> 의 단발 판독을 조건 폴링으로 바꾸되, **true 는 즉시 신뢰하고 false 만 확인**한다 — 스크림은 창을
> *제거*만 하므로 이 메커니즘에서 false-positive 가 없고, 위험한 방향은 false 이기 때문이다(기존 분할을
> 파괴한다). APPLICATION 창 0 인 판독은 **무효 표본**이라 확인 횟수로 세지 않는다.
> `performDismissSplit` 의 폴링을 그대로 복사하지 **않은** 이유 = 배치 경로에서 "분할 없음"은 정상적인
> 다수 경로라, true 를 기다리는 방식이면 그 경로마다 타임아웃이 붙는다(회귀).
> 실기기 3행 전부 통과, 상세 = `docs/DEVICE_FACTS.md` 24차 「결함 ① 수정」 절.
>
> 아래는 발견 당시 기록(원인 규명 근거로 보존).

분할이 이미 활성인 상태에서 버블 메뉴 → 「위로 배치」를 탭하면 `SplitStateResult(active=false)` 오판정 →
진입 스텝 3회 실패 → `ENTRY_STEP_FAILED`. 같은 순간 `dumpsys accessibility` 에는
`TYPE_SPLIT_SCREEN_DIVIDER` 창이 정상 존재한다. 원인은 `performDismissSplit` KDoc
(`ArrangerAccessibilityService.kt:553-567`)이 **이미 문서화한** 스크림 제거 후 a11y 창 목록 비원자적 재구축.

**그 대응이 dismiss 경로에만 있다** — `performDismissSplit` 은 `isSplitActive` 를 `SPLIT_STATE_SETTLE_TIMEOUT_MS`
까지 폴링하는데, 배치 경로의 `handleQuerySplitState()`(`:1934-1940`)는 단발 판독이다. 같은 세션 대조가
진단을 확정: **같은 스크림·같은 시점에 「분할 해제」는 `dismissSplit: 성공`, 「위로 배치」는 `active=false`.**

- 영향: 「비율 바꿔 재배치」·「위/아래 뒤집기」 = 분할 유지 중 재배치가 메뉴의 주 용도인데 그 경로가 깨진다.
- 최소 수정 = `handleQuerySplitState()` 에 `performDismissSplit` 과 동일한 폴링을 다는 것.
- 무영향 경로: 분할 없는 상태의 메뉴 배치(정상), `am broadcast` 트리거(스크림 없음, 정상).

**② 가로 상하분할 최소 페인 = 563px — `minPaneHeight=181` 이 가로에서 틀림 → ✅ 24차에서 수정·재검증 완료**

> **해법 = 실측 후 상수 교체 + 클램프 고지.** 상수를 바꾸기 전에 가로 값을 새로 측정했다(함정 #7) —
> 단일 대이동으로 하한 563px 확정, 상한 1391px(= 1954−563)로 **양쪽 페인 대칭** 확인. **종전의 181 은
> 「최소 페인」이 아니라 접힘 슬라이버의 가시 폭**이었고, 181~563 은 존재하지 않는 대역이었다.
> 상수 교체만으로는 4:3 의 물리적 결과가 그대로이므로(이 기기 가로에서 4:3 은 원리상 불가능),
> **`clampReason` 을 토스트로 고지**하는 것을 함께 넣었다 — 결함 표제가 "조용히 미달 배치"였고
> 실제 개선 지점이 거기다. 자동 트리거는 침묵한다. 실기기 재검증: 4:3 → `HIT_MAX_PANE_CEILING`
> + 토스트, 16:9 → `clamp=null` + 무토스트(회귀 없음). 상세 = `docs/DEVICE_FACTS.md` 24차 「결함 ② 수정」 절.
>
> 아래는 발견 당시 기록(원인 규명 근거로 보존).

`SplitPlanner.kt:79` 의 `minPaneHeight = 181` 은 주석이 스스로 「세로 좌우분할. 가로 상하분할 [미검증]」
이라 밝힌 값이다. 24차 스윕 실측: 가로는 **정상 리사이즈 최소 페인 563px**, 그 아래 요청은 563 으로
되돌아가고(1.60/1.55/1.40/1.333 전부 563), 훨씬 더 밀면 181px 접힘 슬라이버로 점프한다.
**181~563px 는 사각지대** — planner 가 clamp 하지 않고(`clamp=null`) 도달 실패도 감지 못한 채
`arrange done: verified=true residual=0` 을 남긴다(verify 는 잔여 띠만 보지 디바이더 도달은 안 본다).

- 영향: 메뉴 **「4:3 구형」 프리셋이 이 기기 가로에서 구조적으로 성립 불가**(필요 패널 316px < 563px).
  성공으로 보이고 로그도 성공이다. 16:9·2:1·21:9·2.35:1 은 여유가 있어 무영향.
- 이로써 열린 질문 #4 의 「가로 상하분할 [미검증]」 절반이 해소된다(아래 #4 갱신).

### B — 미발동·희귀 경로 [미검증] — 자연 발생 대기 또는 인위적 유도

| # | 경로 | 상태 |
|---|---|---|
| B-1 | `performDismissSplit` "패널 태스크 부재" 분기(#28) | 유도 3후보 소진(20차) — 자연 발생 대기 확정 |
| B-2 | dismissSplit 인텐트 폴백(P3-2, `instance==null`) | B-1 과 인접 조건, 같은 세션에서 함께 유도 대상 |
| B-3 | 클릭 사이클 cycle-2(a11y)·스왑 제스처 사이클(#20) | 10차 이후 미발동. `mech` 로그 상시 계측 중 |
| B-4 | 스왑 실패 시 회전×2 폴백(#19) | 누적 12+ 세션 미발동(PaneSwapper 가 항상 수렴) |
| B-5 | `PanelTaskPolicy` MRU-first 순서 계약 | 공개 API 로 `lastActiveTime` 조회 불가 → 플랫폼 순서에 위임, 계약 자체가 [미검증] |
| W2-7 | Shizuku 셸 타임아웃 실효(F3) | 인위 유도 곤란, 자연 발생 대기 |
| W1-4 | 자동 트리거 실패 복구(BACK 주입 후 대상 앱 전면 복귀) | 유도 방법이 HOME 주입이라 복귀 스택 없음, 복귀 성공 사례 미관측 |

### C — v1.5 후보 (v1 범위 밖, 결정 완료)

- **#12 축적분**: BOTH_AXES_BARS 시 보정 생략(G1 드리프트 실측) · 적응형 residual(글로우 필러박스 블라인드 G5) · 비-16:9 콘텐츠 합치·캐시 실측 · flex 게이트2↔startArrange TOCTOU(이론상)
- **P3-5 축적분**: 포그라운드 안정성 윈도(월렛 quick 카드 오염) · 재열기 멈칫 시 Done-후 분할 잔존(수동 해제로 복구, 키가드 게이트는 정당 사용례 훼손으로 기각) · 각도 대역 경계값 실측
- **D17 열축(필러박스) 게이트** — 23차에서 「미구현」이 아니라 「미연결」로 판명: `LetterboxDetector.detectHybrid()` 는 이미 순흑 실패 시 적응형 폴백을 쓰지만, verify 가 부르는 `residualBars()`(`LetterboxDetector.kt:157`) 에만 그 폴백이 없다. 최소 수정 = 거기에 같은 폴백을 다는 것. v1 은 로그 보고만 한다는 DESIGN_12 §3.4/§7 결정은 유지. 상세 = `docs/DEVICE_FACTS.md` 23차 절
- **#32 런처 아이콘 2→1 — 「진입점 소유권 이전」**: 앱 서랍 아이콘이 릴리스 2개(`OnboardingActivity` "FoldWindow" + `PanelActivity` "FW Panel") · 디버그 3개. **「합치기」는 불가능** — `pruneExtraPanelTasks` 가 `PanelActivity::class.java.name` 으로 태스크를 골라 `finishAndRemoveTask` 하고, `PanelActivity.onDestroy` 가 분할 해제 4경로를 덮는 유일한 관측점(sticky 래치의 근원)이며, finish 경로 5개 + finish 토큰 DoS 방어가 전부 이 컴포넌트를 전제한다. **성립 가능한 유일한 방향 = `OnboardingActivity` 의 LAUNCHER 를 제거해 `PanelActivity` 를 유일 진입점으로 남기는 것**(부수 이득: 2026-07-25 피커 오클릭 사고 원인이 구조적으로 소멸). **선행 조건 = 인과 확정** — 「카드 0 에서도 피커 앱 목록에 FW Panel 이 있다」의 근거가 `DEVICE_FACTS.md` 19차 절에 "MAIN/LAUNCHER 노출로 추정"이라고만 적혀 있다(원인 변수 미조작). 실기기 A/B 가 "LAUNCHER 때문이 아니다"를 내면 훨씬 싼 경로(Panel 쪽 LAUNCHER 만 제거)가 열린다. 라벨은 "FoldWindow" 개명 방향이며 셀렉터가 `contains` 부분 문자열이라(`SplitEntry.kt:518`) **디버그의 `ProbeActivity` LAUNCHER 필터 제거가 선행 필수**(무위험, 언제든 착수 가능 — 릴리스엔 그 선언 자체가 없고 프로브 실행 3종 모두 LAUNCHER 비의존)
- **`probe/` 완전 삭제**: 매니페스트 격리(W1/S1)는 프로브 3종 선언을 디버그 소스셋으로 옮겼을 뿐이라 **`probe/` Kotlin 소스는 `main` 에 남아 릴리스 APK 에도 컴파일돼 들어간다**(`isMinifyEnabled = false`). 사라진 것은 도달 가능성이지 바이트코드가 아니다. 완전 삭제는 실화면 기반 `WindowGeometry` 측정(가로 분할 디바이더 두께·최소 페인 높이)이 프로브를 요구하므로 그 이후로 이월
- **열린 질문 잔여**: #1 `rowStride` 축소의 역산 정밀도 영향 · #3 wavve 패키지명 · #5 `ADAPTIVE_*` 상수 실기기 재검증 · #6 셀렉터 다국어(EN) 안정성 · #14 BOTTOM 배치 시 스왑 생략 가능성 (번호는 아래 「열린 질문」 목록과 대응)

### D — 미해결 (원인 미상, 재발 시 재조사)

- **17차 step3 3전멸의 진짜 원인.** 19차에 「카드 0」 가설은 5/5 배치 성공으로 반증·폐기(`pm clear` 표본이 신규 설치 교란 배제). 재발 시 후보 = 피커 화면 상태·purge 타이밍·오염 카드 잔존. #29(유령 매치, 20차)는 시그니처가 다르므로(node-not-found ≠ 매치 후 dispatched=false) 동일시 금지 — 단 재조사 시 #29 의 bounds 필터가 증상을 node-not-found 로 바꿀 수 있음을 감안.

- **소폭 디바이더 이동(≤30px) 미반영** (24차 관측). 650→620·600→580·600→570 요청이 디바이더를 전혀 못 움직였다(40px 이상은 전부 반영). `dividerTolerancePx=4` 보다 훨씬 크므로 드래그 생략 경로가 아니고 제스처는 디스패치됐다. One UI 디바이더 데드존 추정, 원인 미상. 실사용 영향 작음 — 프리셋 간 이동은 전부 40px 초과, 미세 보정은 tolerance 안쪽이라 시도 자체를 안 한다. 재조사 후보 = 거리 비례로 짧아지는 `scaledDuration` 스트로크

### E — 버블 유휴 알파 대비 (24차 실측, 한 눈금 미달)

`BUBBLE_IDLE_ALPHA=0.65` × 링 스트로크 알파 `#8C`(55%) = 화면 위 0.358. 순검정 위 링 대비 **2.96:1** 로
WCAG 1.4.11(비텍스트 3:1)에 **0.04 모자란다**. **뷰 알파 0.66 이면 3:1 을 넘는다**(합성식 역산).
D16 의 0.55→0.65 는 방향이 옳았고 실측으로 개선이 확인됐다(어두운 영상 위 링 2.61:1 중앙값,
설계 근거인 "어두운 배경=링 / 밝은 배경=원" 상보성도 실증) — 다만 경계를 한 눈금 못 넘었다.

- 한 줄 수정: `BUBBLE_IDLE_ALPHA` 0.65 → 0.66. 유휴 시각적 인상 변화는 무시 가능(1.5%).
- 별건(v1.5): **중간 휘도대(0.06~0.35)는 알파 1.0 에서도 1.63/1.37** 로 낮다. 반투명 세이지 톤의
  구조적 한계라 알파로는 해소 불가 — 대비 윤곽(예: 어두운 아웃라인 1px 동반)이 필요하다.

### F — 사용자 손이 필요한 잔여 2항목 (TalkBack 낭독)

**adb 로 유도 불가 확정** — TalkBack 을 켜도 `input tap`/`input swipe` 주입분이 터치 탐색을 우회해
버블의 터치 리스너로 직행한다(로그가 `bubble long-click(접근성 경로)` 이 아니라 `bubble long-press` 를 냄).

| # | 항목 | 판정 방법 |
|---|---|---|
| F-1 | **F1 = TalkBack 롱클릭 메뉴 진입**(신규 경로라 우선) | TalkBack 켜고 버블에 포커스 → **두 번 탭 후 두 번째를 누른 채 유지**. `adb logcat -s FWFloatingLauncher:V` 에 **`bubble long-click(접근성 경로)`** 가 뜨면 PASS(터치 경로면 `bubble long-press` 가 뜬다 — 두 줄이 달라 혼동 불가) |
| F-2 | 토글 행 낭독(D12) | 메뉴에서 「전체화면 자동 배치」 행에 포커스 → "켜짐/꺼짐 + 체크 상태"가 읽히고 **안내가 두 번 나가지 않는지**(`isCheckable`/`isChecked` + ACTION_CLICK 라벨로 중복 제거한 것이 의도대로인지) |
| F-3 | F6c 패널 메모 좌상단 무음 정지 소멸 | 패널 MEMO 모드에서 **오른쪽으로 스와이프 선형 탐색** → 좌상단에서 아무 말 없이 멈추는 지점이 없어야 PASS |

TalkBack 켜기: `adb shell settings put secure enabled_accessibility_services "dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService:dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService:com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService"`
(끄기 = 뒤의 `:com.samsung...TalkBackService` 만 뺀 문자열로 다시 실행)

---

## 열린 질문 (번호는 코드 주석이 인용한다 — 재번호·삭제 금지)

`.kt` 소스가 `PROGRESS.md 열린 질문 #N` 형태로 이 번호를 직접 인용한다(`DividerDragger.kt`#2, `SplitEntry.kt`#6·#18). 전부 해소됐어도 번호는 유지한다.

1. `rowStride` 축소가 종횡비 역산 정밀도에 미치는 영향 — [미해결]
2. 드래그 전략 → **SINGLE_STROKE 확정**(실기기: 단일 스와이프로 984→1236 정확 이동). `HOLD_THEN_MOVE` 는 `GestureDrags` 로 재구현돼 카드 드래그 진입 전용으로 잔존
3. wavve 등 국내 OTT 패키지명 확인 필요 — [미해결]
4. `minPaneHeight` 실측 → **24차에 해소·정정.** 가로 상하분할 최소 페인 = **563px**(= 250dp), 양쪽 페인 대칭(상한 1391 = 1954−563). **종전의 181 은 「최소 페인」이 아니라 접힘 슬라이버의 가시 폭**이었고 181~563 은 존재하지 않는 대역이다 — 그 대역을 요청하면 플랫폼이 조용히 563 으로 되돌린다. `SplitPlanner` 상수는 563 으로 교체 완료(위 A''' ②). 세로 좌우분할 값은 이 앱이 쓰지 않으므로(기하는 `foldSevenLandscape()` 하나뿐) 재측정하지 않았다
5. `LetterboxDetector` v2 설계 → 구현 완료. `ADAPTIVE_*` 상수 실기기 재검증·순흑 조건 E 실측은 잔여
6. Recents 분할 진입 셀렉터의 다국어 안정성 — 한국어만 검증. `SPLIT_MENU_TEXT_EN`·`ROTATE_DESC_EN` [미검증]
7. `DEFAULT_MIN_MEASUREMENT_CONFIDENCE=0.25f` 튜닝 → 접근 자체 기각(11차 검토: 오염 conf 가 정상 ADAPTIVE 상한과 겹쳐 임계로 분리 불가) — "후보 자격" 게이트로 역할 격하, 채택은 합치 게이트가 결정
8. `PaneSwapper` 셀렉터 → "창 전환" 실측 확정. 하단 배치 E2E 성공
9. step 파트너 경로 → 피커 노드 탭이 1차 확정. `LAUNCH_ADJACENT` 는 분할 선택 상태를 파괴해 최후 폴백으로 강등
10. `defaults.closedLoopCorrection` 토글 → 배선 완료. `aspectSource=PROFILE` 이면 무조건 보정 생략(오염 측정이 프로파일을 덮어쓰는 사고 대응)
11. 대상 앱 라벨 조회 → `<queries>` 블록으로 실기기 정상 동작 확인
12. 사전 실측 오염(컨트롤 오버레이/앰비언트 글로우) → **#12 해소 완결**: 2-샷 합치 게이트(`docs/DESIGN_12_MEASUREMENT_CONSENSUS.md`), 실기기 G1~G5 전부 통과
13. 필러박스 맹점(과소 이동 시 `residualBars=0` 오판) → 열축 로직은 confirm 측정과 동일 기반, v1 은 `residualColumns` 로그 보고까지만(`verified` 의미론 반영은 v1.5). 23차: 오탐 원인은 "미구현"이 아니라 "미연결"로 판명(위 남은 작업 C 참조)
14. BOTTOM 배치 최적화(상단 도킹 후 스왑 대신 하단 직접 드롭) — [미해결, v1.5 후보]
15. step2 성공 조건 폴링 예산 → 실질 해소: 예산 분리 대신 "다음 시도의 목표 상태 선체크"로 늦은 정착을 흡수(회귀 E2E 실증). 타임아웃 값 무변경
16. 넷플릭스(UNRESIZEABLE) 분기 자동화 → 완료 + 실기기 E2E 검증(6회). 감지 = privateFlags 필드 리플렉션 + 폴백 비트 1<<11
17. 넷플릭스 재생-분할 관계 → 특성 규명 완료: 분할 페인 안에서 재생 시작=유지 / 재생 중 메뉴 진입=최소화 플레이어 팝업으로 분리. v1 지침 = "배치 후 재생"
18. step2/3 성공 조건 오탐 → **완결.** 유튜브 DRAG 회귀 E2E 통과. `isSplitSelectTopPane` 임계(전폭≥90%, `EDGE_DOCK_TOLERANCE_PX=40`) 는 ground truth(`[0,0][2184,977]`, 도킹 0px)로 [검증]. 발견된 실버그 2종(유령 매치/성공 미인지)은 `SplitEntry` 수정 완료
19. 회전 결과 페인 위치 비결정 — 원인 미상. 누적 TOP 10 / BOTTOM 2(상단 편향). 교정 체인 = `PaneSwapper`(수렴) → 회전×2 폴백([미발동], 위 B-4) → 실패 시 하단 유지+토스트
20. 클릭-사이클 에스컬레이션 → 10차 Gate 1~3 통과로 주 경로 완결(15/15 done, `ENTRY_STEP_FAILED` 0). 잔여 = [미발동] 목록(위 B-3)
21. PROFILE 보정 생략 시 verify 잔여값(122~224)은 보고 전용 — #12 설계에서 verify="최저 신뢰 컴포넌트" 판정, 사후 보정 단독 방어는 기각 근거로 확정
22. 버블 오버레이 존재 시 피커發 파트너가 전체화면 낙착하는 메커니즘 불명(One UI WM 라우팅 추정) — 경험 법칙(세션 중 버블 숨김)으로 해소. One UI 업데이트 시 재검증 필요
23. `BootReceiver` 실부팅 복귀 → 실부팅 검증 통과(BOOT_COMPLETED·FGS 자동 기동·접근성 유지·버블 가시)
24. P3-2 확장 메뉴 [미검증] 전체 → 4차 실기기 검증으로 전부 해소(드래그 해제 반증→패널 finish 재구현, 클램프, 스크림 재탭=닫기만, 프리셋 렌더)
25. 스크림 부산물(풀스크린 오버레이 존재 시 a11y 창 목록 가림-제외, 재구축 비원자적) — 해결·기록. dismiss 는 목표 조건 자체 폴링으로 해결
26. P3-3 DataStore [미검증] 목록 → 7차+16차 실기기 검증으로 전부 해소(이관·goAsync 부팅·placement 복원·corruption 복구·중지 레이스)
27. `#27` 패널 카드 결함 → 19차 종결. 채택 = 축 A(파괴 제거, `finish()` 격하 + `pruneExtraPanelTasks`) + `#28`. 기각 = 축 B(소환, base intent 오염으로 유해 판정). 상세 = `docs/DESIGN_27_PANEL_CARD.md`, `docs/DEVICE_FACTS.md` 17~19차 절
28. `#28` base intent 오염(패널 태스크 부재 시 인텐트 폴백이 새 태스크에 extras 를 실어 영구 실패 루프 생성) → 수정 완료: 폴백 3분기화, 패널 태스크 부재 시 폴백 생략

---

## 결정 로그 (핵심만 — 세부는 git log / DEVICE_FACTS / DESIGN_* 참조)

| 날짜 | 결정 | 근거 |
|---|---|---|
| Day 0 | Tier 1(접근성+오버레이) 확정, Shizuku 는 Phase 4 선택 | 디바이더 임의 비율 허용 확인 |
| Day 0 | MediaProjection 재렌더링 · 삼성 「앱별 화면 비율」 대체 모두 폐기 | DRM 차단/발열, 상시 고정이라 시청 시에만 못 씀 |
| 2026-07-25 | 진입 레시피 = Recents 카드 **드래그** 3단계(메뉴 경로는 좌우 분할만 생성해 폐기) | 실측 반증 |
| 2026-07-25 | `DividerDragger` = SINGLE_STROKE, 페인 대상 크롭 pre-measure, `aspectSource=PROFILE` 시 보정 생략 | 실측: 단일 스와이프 정확 이동 / 전체 화면 스캔 오염 / 컨트롤 오버레이가 재측정을 과축소 |
| 2026-07-25 | UNRESIZEABLE(넷플릭스) = MENU 레시피(좌우 분할 후 "시계 방향 회전") 분기, 감지 = privateFlags 리플렉션 + 폴백 비트 1<<11 | 드래그 레시피가 팝업으로 라우팅됨(실측) |
| 2026-07-25 | `PanelActivity` = launchMode 기본 + 멀티윈도우 이탈 시 자가 가드 finish, MAIN/LAUNCHER 노출 수용(라벨 "FW Panel") | 잔존 태스크가 피커 탭에서 전체화면 재사용(실측). 피커 노출 필요조건 |
| 2026-07-25 오후 | 세션 시작 시 `purgeStalePanelTasks()`(후에 `pruneExtraPanelTasks` 로 대체, #27) | 강제종료 후 잔존 카드가 피커를 무력화(실측) |
| 2026-07-25 오후2차 | 진입 스텝 재시도 = "목표 상태 선체크 우선"(타임아웃 증액 대신) | 늦은 정착이 실패로 오판되는 것을 다음 시도가 흡수 |
| 2026-07-25 오후2차 | 버블 = 독립 specialUse FGS + `TYPE_APPLICATION_OVERLAY`, 배치 세션 중 제거·종료 시 복원 | 오버레이 존재 시 피커發 파트너가 전체화면 낙착(A/B 실측) |
| 2026-07-25 오후3차 | 분할 해제 = 패널 finish(디바이더 드래그 아님) + `isSplitActive` 자체 폴링, 메뉴 = 풀스크린 스크림 | dispatchGesture 는 dismiss 깊이에서 스냅백(실측) / ACTION_OUTSIDE 디스패치 순서 경합(실측) |
| 2026-07-25 오후6차 | placement 복원 = override > last-success > profile > defaults > TOP, 저장은 Done ∧ effective==desired 만 | 마지막 실사용 선택이 정적 JSON 보다 사용자 의도에 가까움 |
| 2026-07-25 저녁8차 | `#20` 클릭-사이클 에스컬레이션(선체크+800ms 검증 슬라이스+사이클별 메커니즘 전환) + `LAUNCH_ADJACENT` 폴백 삭제 | AOSP 소스: `ACTION_CLICK=true` 는 히트테스트 우회(실측 무효 클릭 전부 true 반환) |
| 2026-07-25 밤11차 | `#12` 측정 합치 게이트(pre 행축 × confirm 열축, 불합치 시 PRESET 폴백) | 오염 프레임 단일 샷 conf 로는 "띠"와 "일시적 UI" 구분 불가. 오탐 비용≪미탐 비용 |
| 2026-07-26 12차 | `#12` §6 측정 캐싱(`AspectSource.CACHED`, admission=합치∧verified∧레버) | 같은 앱의 이중 검증된 사전값. 단일 프레임 유입은 캐시 오염 사고 재발 방지 위해 구조적 차단 |
| 2026-07-27~28 15차 | FoldingFeature 자동 배치 = 힌지 각도 안정성 게이트 1층 + FLEX 세션 자세-이탈 취소 2층(시간 상수 증액 기각) | 완전 닫기 체류 시간 실측 2.1/1.95/1.2s 가 800ms 디바운스를 초과(설계 가정 반증) |
| 2026-07-28 P4 | P4-3 발화 = `dismissSplit()` 미경유, 패널 직접 `finishAndRemoveTask()` | 커버 디스플레이에서 `isSplitActive` 창 목록 신뢰 불가 |
| 2026-07-28 P4-1 | 팝업 실행 = Shizuku UserService(AIDL), `Shizuku.newProcess` 금지, 상태 머신 비사용(단순 명령+검증 폴링) | `newProcess` 는 비공개 API. 진입 스텝 1개라 머신 과잉 |
| 2026-07-28 18~19차 | `#27` 주 수정 = 축 A(`finish()` 격하 + `pruneExtraPanelTasks` MRU 1개 보존). 축 B(소환)는 기각·제거 | G1/G3 가 purge 의 원 근거를 반증, 소환 카드는 base intent 오염으로 유해(G2) |
| 2026-07-29 | 개선 웨이브 W0~W7(23항목) 채택·완주 | 전체 코드 리뷰 대응. 계획·리뷰 문서는 반영 후 폐기(사유는 각 `.kt` KDoc 인라인) |
| 2026-07-31 20차 | `#29` MENU 피커 유령(0-bounds) 매치 수정(`findPanelPickerNode` bounds 필터) | 잔존 카드의 0-bounds 유령 노드가 DFS 앞에서 매치됨(베이스라인 대조로 기존 결함 확정) |
| 2026-07-31 | `#30` 전체화면 자동 트리거 = 긍정 술어(`FullscreenWindowJudge`, "APP 창이 화면≥99% 덮고 상단 스트립 가리는 비-APP 창 0") + 패키지 단위 에피소드 래치(`AutoTriggerLedger`, 시간창 아님) | 부정 술어는 자기 분할을 몰입으로 오판정(D8). 해제→재진입 간격은 사용자 페이스라 시간창 크기를 원리상 정할 수 없음(D2) |
| 2026-08-01 22차 | `#31` 최종 해법 = 래치 2종 구분(sticky/non-sticky) | 1차 수정(홈을 해제 신호로 재허용)이 분할-해제 전환 중 노출되는 런처 이벤트로 P-1 루프를 회귀시킴 — 시간창은 D2 로 이미 기각 |
| 2026-08-01 23차 | D17 열축 게이트는 신규 판별자 불요, `residualBars()` 에 적응형 폴백만 연결하면 됨(v1.5) | 앰비언트 조명이 띠 휘도를 52~60 으로 올려 순흑 임계(darkLuma≤24) 미달 — `detectHybrid()`/`ADAPTIVE_MAX_BAR_LUMA=90` 는 이미 이 범위를 덮음 |
| 2026-08-01 | `#32` 런처 진입점 통합 = 「합치기」 불가, 「소유권 이전」만 가능. L2(A/B 인과 확정) 선행 후 착수 | `PanelActivity` 가 finish 경로 5개·sticky 래치·prune 판정 전부의 유일 전제. 위 남은 작업 C 참조 |
| 2026-07-31 | 문서 정리: `PROMPTS/`·`docs/PHASE_RUNBOOK.md`·`docs/CAMPAIGN_19_PANEL_CARD.md` 제거(git 이력으로 복원 가능) | Phase 0~4 완료로 목적 소진 |
| 2026-08-01 | AAA 품질 캠페인 = 개편 → **읽기 전용 비평가에 "모든 수치 주장 재계산" 의무** → 조준 수정 루프 | 지배적 결함 클래스가 "주석의 수치가 거짓"이었다. 코드를 읽는 비평만으로는 안 잡히고, 실행 계산을 강제해야 드러난다(위 캠페인 절) |
| 2026-08-01 | 메뉴 커밋 액션 = **3결과 모델**(즉시 / 복원까지 이연 / onDestroy 폐기). 버리지도 즉시 재생도 하지 않는다 | 자동 배치 세션 중 재생되면 busy 토스트가 세션을 오염(함정 #22 계열). "정확히 1회 실행" 계약은 세 경우 모두에서 유지 |
| 2026-08-01 24차 | AAA 캠페인 실기기 검증 8/10 PASS. 검증 단계는 **코드 변경 0** 으로 마치고, 발견한 결함 2건은 별도 판단 대상으로 분리(A''') | 회귀가 아니면 검증 캠페인 안에서 고치지 않는다(원인 커밋 지목 원칙, 런북 §7-5) |
| 2026-08-01 24차 | 결함 ① 해법 = **비대칭 정착 술어**(true 즉시 신뢰 / false 만 2회 확인 / APPLICATION 0 은 무효 표본). `performDismissSplit` 폴링 복사는 **기각** | 배치 경로에서 "분할 없음"은 정상적인 다수 경로다 — true 를 기다리는 방식이면 그 경로마다 타임아웃이 붙어 새 회귀가 된다. 비대칭이면 양쪽 다 1틱(80ms) |
| 2026-08-01 24차 | 결함 ② = 상수 교체 **전에 가로 값을 새로 실측**(함정 #7). 교체만으로 그치지 않고 `clampReason` **고지**를 함께 넣음 | 181 의 출처가 세로 좌우분할이라, 재측정 없이 바꾸면 틀린 값을 다른 틀린 값으로 바꾸는 것. 그리고 4:3 의 물리적 결과는 상수를 고쳐도 동일하므로(원리상 불가능) 실제 개선 지점은 "앱이 알고 말하는 것" 뿐이다 — 결함 표제가 "**조용히** 미달 배치"였다 |
| 2026-08-01 24차 | 클램프 고지 문구는 **원인 + 눈에 보일 결과**까지만, 행동 지시는 넣지 않음. 자동 트리거는 침묵 | 되돌릴 행동이 실제로 없는데 억지 지시를 넣으면 거짓말이 된다. 자동 발화는 사용자가 시작한 행위가 아니라 매번 토스트하면 방해(`evaluateFullscreenAutoTrigger` 원칙 승계) |
| 2026-08-01 24차 | "비율 맞으면 토스트 / 아니면 재배치" 제안 **반려** | 재배치는 이미 구현·동작(오판정 때문에 메뉴에서만 못 닿았을 뿐)이고, 토스트 쪽은 (a) 「위로/아래로 배치」가 **위치**를 바꾸는 행이라 비율로 게이팅하면 두 행이 죽고 (b) 완전 중복 배치는 `dividerTolerancePx=4` 로 이미 조용한 무동작(실측 2.0s, 드래그 0)이며 (c) 화면에 보이는 사실을 말하는 토스트는 카피 정책 위반 |
| 2026-08-01 24차 | 픽셀 계측을 판정 근거로 채택(대비·글리프 박스·링 프로파일) | 「주석의 수치가 거짓」이 지배적 결함 클래스였던 캠페인의 후속 검증이므로, 육안이 아니라 **실렌더 픽셀에서 재계산**해야 의미가 있다. PIL 이 이 머신에 있음이 확인돼 비용도 낮다 |
| 2026-08-01 | 버블 메뉴에 `setOnLongClickListener` 추가(a11y 전용 경로) | 커스텀 터치 리스너의 롱프레스 검출은 접근성 서비스가 합성하지 못한다 — TalkBack 사용자에게 메뉴 도달 경로가 **아예 없었다**. 터치 리스너가 DOWN 을 소비하므로 터치 경로는 무변화 |

---

## 개발 환경 함정 (실측 누적)

- Git Bash 에서 `gradlew` 는 `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` 프리픽스 필수 — 없으면 `installDebug` 가 조용히 실패해 구버전 APK 로 검증하게 됨(실제 40분 소모 사례).
- `screencap` 은 폴드에서 `-d <display-id>` 필수(생략 시 경고 2줄이 PNG 에 섞임). 내부 화면 id·커버 id 는 `dumpsys SurfaceFlinger --display-id`.
- adb 롱프레스 시뮬레이션은 `input swipe x y x y 1200`(700ms 는 경계 실패).
- Git Bash 가 `/sdcard/...` 인자를 로컬 경로로 변환해 파괴 — 원격 명령은 통째 인용, `adb pull` 은 `MSYS_NO_PATHCONV=1` 프리픽스.
- 재설치 후 접근성 서비스 재활성화 필수(CLAUDE.md 함정 #6). 동일 값으로 재설정하면 no-op — 서비스 재기동 로그를 보려면 `none` 으로 토글 후 재설정.
- 회전은 `adb shell cmd window user-rotation lock 1(가로)/0(세로)` 로 고정(물리 회전과 WM 관점 동일, 재현성 높음) — `user-rotation lock` 잔재가 남으면 FoldingFeature 판정이 물리 자세와 무관하게 나오므로 검증 전 `cmd window user-rotation free` 확인.

## 실기기 검증 절차 (상시 사용 명령)

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug
# 재설치 후 접근성 재활성화 필수 (함정 #6). probe 병행 시 콜론으로 연결
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService
# 유튜브 가로 전체화면 재생 상태에서 (⚠ -n 필수 — 액션만으로는 implicit broadcast 제한으로 수신 안 됨):
adb shell am broadcast -a dev.dj.foldwindow.ARRANGE -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver --es placement top
# 하단 배치: --es placement bottom / 종횡비 강제: --ef aspect 1.7778 / 취소: --ez cancel true
```

상세 절차(세션 구성·로그 캡처·공통 함정)는 `docs/DEVICE_VERIFICATION_RUNBOOK.md`.
