# DESIGN #27 — 패널 카드 소환: step3 의 암묵 전제를 자산 관리로 전환

> 대상 결함: PROGRESS.md 열린 질문 #27 / DEVICE_FACTS 17차 「신규 결함」 절
> 상태: **축 A 채택·구현·실기기 검증 완료 / 축 B 기각·제거** (2026-07-28 19차 캠페인)
>
> ⚠ **이 문서는 18차 설계 시점의 기록이다. 19차 실기기 결과가 §1.3 의 구조 진단과 §3.2 축 B 를
> 뒤집었다** — 아래 「19차 판정」 절과 `docs/DEVICE_FACTS.md` 19차 절이 최신 사실이다.
> 본문의 축 B 서술은 **기각된 설계의 이력**으로만 읽어라.

---

## 19차 판정 (2026-07-28 실기기 — 이 문서의 결론을 덮어쓴다)

| 축 | 판정 | 근거 |
|---|---|---|
| **축 A (파괴 제거)** | ✅ **채택 확정** | G4 커버 해제 후 카드 생존 + 3/3 done · G5 `보존 1 / 제거 1` 후 step3 성공 · G6 재설치 죽은 카드로 done · G7 함정 미발동 |
| **축 B (소환)** | ❌ **기각·코드 제거** | ① 소환 카드의 base intent 가 `NEW_DOCUMENT\|MULTIPLE_TASK` 로 오염돼 **step3 를 깨뜨린다**(전체화면 낙착 → 가드 3회 → `ENTRY_STEP_FAILED`). 대조군인 런처 형태 카드(`flg=0x10000000`)는 정상 낙착 ② **전제 자체가 반증**: 카드 0 상태에서 배치 5/5 성공(`pm clear` 표본 2건으로 신규 설치 교란 배제) — 피커는 앱 목록에서도 「FW Panel」을 제공한다 |

**§1.3 정정**: 「카드를 만드는 경로 0개」는 부정확했다. 카드가 없어도 피커에 노드가 존재한다.
따라서 **소환은 불필요**하며, 결함의 본체는 「우리 코드가 카드를 지우는 초과 동작」(축 A)뿐이다.

**[미해결]** 17차의 `node-not-found` 4연속(카드 0)은 19차에 재현되지 않았다. 「카드 0 → step3 불능」은
불완전한 설명이며 다른 요인이 함께 작용했을 가능성이 크다. 축 A 의 정당성은 이와 무관하게 유지된다
(자기 전제를 지우는 근거 없는 초과 동작을 걷어낸 것).

**소환을 다시 검토한다면** — `makeTaskLaunchBehind`/`addAppTask` 계열은 전부 base intent 에 문서
플래그를 남기므로 **step3 가 탭할 카드의 base intent 는 런처 형태(`NEW_TASK` 단독)여야 한다**는
제약이 1순위 요구사항이다(19차 대조 실험이 유일한 판정 기준).
> 선례: `DESIGN_20_CLICK_CYCLE.md`(설계→프로브→구현), `DESIGN_P41_FREEFORM.md`(후보 A/B 프로브 판정)

---

## 0. 한 줄 요약

step3(분할 파트너 피커 탭)는 **「FW Panel 최근 태스크 카드가 존재한다」는 암묵 전제** 위에 서 있는데,
그 카드를 **지우는 코드 경로는 4개, 만드는 경로는 0개**다.
18차 실측으로 **① 카드 파괴는 전부 불필요한 초과 동작이고 ② purge 는 premise 가 반증됐다**는 것이 확정됐다.
파괴를 걷어내는 것이 주 수정, 소환은 카드 0 상태(재설치·수동 스와이프)를 위한 안전망이다.

---

## 1. 문제 정의

### 1.1 증상 (17차)

`clickCycle: [step3 panel-picker] cycle=0/1/2 node-not-found` 3전멸 → `ENTRY_STEP_FAILED`.
21:39 / 21:42 / 21:44 / 21:45 4회 연속 재현. **배치 기능 전체 불능이 지속**된다.

### 1.2 18차 결정 실험 — purge 자충 직접 재현 [확정]

```
카드 존재 확인            → dumpsys recents 패널 태스크 1개
adb ARRANGE 브로드캐스트  → FWArranger: purgeStalePanelTasks: 잔존 패널 태스크 1 개 제거
                          → arrange decision: target=com.android.settings …
카드 재확인               → dumpsys recents 패널 태스크 0개
                          → transition: EnteringSplit(step=3, attempt=3) -> Failed(ENTRY_STEP_FAILED)
                          → arrange failed: reason=ENTRY_STEP_FAILED
```

**자기 코드가 자기 전제를 지우고 그 부재로 실패한다**는 인과가 단일 로그 시퀀스로 물증화됐다.

### 1.3 구조 진단 — 생산 없는 소비

| 카드를 **지우는** 경로 | 위치 | 트리거 빈도 |
|---|---|---|
| 커버 자동 해제 (P4-3) | `ArrangerAccessibilityService.evaluateCoverAutoDismiss` | 접을 때마다 (일상) |
| `dismissSplit` | `performDismissSplit` (instance 경로) | 사용자 해제 시 |
| `purgeStalePanelTasks` | `beginSession` 선두 | **모든 세션 시작마다** |
| 전체화면 자가 가드 | `PanelActivity.scheduleFullscreenCheck` | 분할 이탈 시 |

카드를 **만드는** 경로: **없음** (사용자가 런처에서 직접 실행하는 것 외 수단 0).

**과거 캠페인이 통과한 이유** = 세션 간 카드가 상시 잔존(BACK-finish 리셋 관행)한 **우연**.
P4-3(커버 자동 해제)이 처음으로 "카드 0" 을 **일상적으로 재생산**하면서 전제가 무너졌다.

---

## 2. 실측·사실관계 (18차)

### 2.1 실기기 실측 — 결정적 2건

**[G1 통과]** `finish()` 는 분할을 해소하면서 **카드를 남긴다**
- 분할(설정 상단 / FW Panel 하단) 상태에서 패널 페인 BACK → 양 stage `visible=false sz=0` (분할 해소)
- 패널 태스크 `Recent #1 … Activities=[] autoRemoveRecents=false` → **카드 생존**
- 재진입한 분할 피커에서 「FW Panel」이 **1번 항목(MRU)** 으로 출현

**[G3 통과]** **액티비티가 죽은 카드**(`Activities=[]`)를 피커에서 탭하면 정상 낙착한다
- `Task #5022 … mode=multi-window stage=side/bottom bounds=[0,1099][1968,2184]`
- 대상(설정) = `stage=main/top bounds=[0,0][1968,1085]` — 정상 상하 분할
- **동일 taskId 재사용**, 전체화면 강탈 0, 자가 가드 로그 침묵
- → **purge 의 원래 premise(「잔존 카드를 탭하면 전체화면 재사용으로 분할 파괴」)가 이 기기·OS 에서 반증됐다.**
  그 실측(2026-07-25)은 ⓐ `launchMode=singleTask` 시절 ⓑ 버블 숨김(함정 #22) 도입 **이전**이라
  이미 다른 수정으로 해소된 상태였을 가능성이 높다.

**[후보 ③ 취약 확정]** 피커 앱 그리드
- 헤더에 `all_apps_button`(desc「모든 앱 버튼」)·`search_button` 존재 — **resource-id 라 로케일 무관**(#6 우려 해소)
- 그러나 탭해서 연 앱 서랍 1페이지(`overlay_apps_list`, 아이콘 87개)에 **FW Panel 부재**, 노출된 `scrollable` 노드도 **0**
- → 페이징/검색 없이는 도달 불가. **최후 폴백으로만** 유지

### 2.2 AOSP 사실관계 [확정 — 소스 라인 확인]

| # | 사실 | 함의 |
|---|---|---|
| Q1 | `autoRemoveFromRecents` 기본값 = 일반 액티비티 **false** (document 액티비티만 true). `Task#cleanUpResourcesForDestroy` → `shouldAutoRemoveFromRecents()` false 면 `mRecentTasks.remove` 미실행 | `finish()` 가 카드를 남기는 근거. G1 실측과 일치 |
| Q1-함정 | **`shouldAutoRemoveFromRecents()` 는 `!hasChild() && !getHasBeenVisible()` 이면 강제 제거** | **한 번도 보인 적 없는 태스크는 finish 시 카드가 사라진다.** 소환한 카드는 최소 1회 가시화되거나, 애초에 액티비티를 만들지 않는 수단을 써야 한다 |
| Q2 | 죽은 카드 탭 → `startActivityFromRecents` → `task.intent`(base intent) 재실행. `Task#setIntent` 은 **extras 를 그대로 보존** | **§4 인접 결함이 [이론]→[확정]**. 재부팅 후 디스크 복원 경로는 [불명] |
| Q3 | `ActivityOptions.makeTaskLaunchBehind()` = 공개 API(21). `FLAG_ACTIVITY_NEW_DOCUMENT` 동반 필요, `singleInstance`/`singleTask` 미지원(패널은 standard 라 무관). **`onResume` 미호출 확정**(`mDoResume=false`, `shouldMakeActive()` false) → STOPPED 정착. 완료 시 `handleLaunchTaskBehindCompleteLocked` 가 `mRecentTasks.add` | 자가 가드가 구조적으로 발화 불가. 단 NEW_DOCUMENT 는 `autoRemoveRecents` 를 true 로 만든다(§3.2 주의) |
| Q4 | `moveTaskToBack(true)` = 제거 경로 미트리거 → 카드 잔존, 액티비티 STOPPED 생존 | B2 성립 근거 |
| Q5 | **`ActivityManager.addAppTask()`**(21) = *"a new recents entry … will exist **without an activity**"*. 명시 ComponentName + `FLAG_ACTIVITY_NEW_DOCUMENT` + `FLAG_ACTIVITY_RETAIN_IN_RECENTS` 필요, 썸네일 Bitmap 인자, 실패 시 -1(앱당 개수 상한) | **액티비티를 아예 시작하지 않고** 카드만 만든다 — 포그라운드 무접촉. 소환 1순위 |

---

## 3. 확정 설계

### 3.1 축 A — 파괴 제거 (주 수정, 18차 실측 근거)

#### A1. `finishAndRemoveTask()` → `finish()` 격하

**논거**: P3-2 가 확정한 분할 해소 트리거는 **BACK = `finish()`** 다
(`PanelActivity` companion KDoc: "BACK 으로 finish 하면 분할이 즉시 해소되고 상대 앱이 전체화면 복귀").
`removeTask` 부분은 **어떤 실측에도 요구되지 않은 초과 동작**이고, 그 초과분이 결함의 직접 원인이다.
G1 이 "finish 로도 해소되고 카드는 남는다" 를 직접 실증했다.

| 위치 | 변경 |
|---|---|
| `ArrangerAccessibilityService.evaluateCoverAutoDismiss` | `panel.finishAndRemoveTask()` → `panel.finish()` |
| `ArrangerAccessibilityService.performDismissSplit` (instance 경로) | 동일 |
| `PanelActivity.scheduleFullscreenCheck` | 동일 |
| `PanelActivity` `EXTRA_FINISH_PANEL` 경로 (onCreate / onNewIntent) | 동일 |

#### A2. `purgeStalePanelTasks` → `pruneExtraPanelTasks` (범위 축소)

**논거**: purge 의 premise 가 G3 로 반증됐고(죽은 카드 탭 = 정상 낙착), 비용은 §1.2 로 물증화됐다.
그러나 "패널 태스크가 여러 개 쌓이는 것" 자체는 여전히 무의미하므로 **삭제가 아니라 축소**한다 (함정 #7 준수 — 새 측정 근거를 §2.1 에 기록).

- **가장 최근(MRU) 패널 태스크 1개는 반드시 남긴다** — 그것이 step3 의 소환원이다
- 나머지 패널 태스크만 `finishAndRemoveTask()` 로 제거
- 세션 시작 위치는 유지(순서 의존 없음). 제거/보존 개수를 로그로 명시

### 3.2 축 B — 소환 (안전망) 〔19차 기각·제거됨 — 이하 이력〕

축 A 만으로는 **앱이 통제할 수 없는 카드 소멸**이 남는다: 앱 재설치 · 프로세스 강제 종료 ·
사용자가 recents 에서 손으로 스와이프 · 시스템 태스크 트리밍(`isTrimmable=true` 실측).
따라서 소환 경로는 여전히 필요하다 — 다만 **주 수정이 아니라 보험**이다.

**배치**: `beginSession` 말미, `dispatch(ArrangeEvent.Start)` **직전**. 상태 머신 **밖**의 사전 조건 폴링
(P4-4 `startArrangeWhenForeground` 선례). **머신 무변경.**
**순서**: `pruneExtraPanelTasks()` → (기존 결정 체인) → `ensurePanelCard()` → `dispatch(Start)`
**진입 시점 제약**: 소환은 분할-선택 진입 **전**에만. `beginSession` 은 step1 보다 앞이라 **구조적으로 위반 불가**.

**동작**: 패널 태스크 존재 시 `already-present` 즉시 반환(비용 0) → 없으면 소환 → 태스크 출현 조건 폴링(타임아웃, ADR-2)
→ 실패해도 **세션은 계속**(피커에 다른 경로로 있을 수 있음). 사유는 항상 로그, step3 실패 시 토스트에 반영.

| 순위 | 수단 | 근거 / 주의 |
|---|---|---|
| ~~**B0**~~ | ~~`ActivityManager.addAppTask()`~~ | **[구현 시 배제 확정 — 컴파일 판정]** Q5 의 능력 서술("액티비티 없이 recents 항목 생성")은 맞으나 **시그니처 1번 인자가 `Activity` 인스턴스**다. 호출부는 `AccessibilityService`(Activity 아님)이고, **소환이 필요한 상황은 정의상 `PanelActivity.instance` 가 null** 이라 빌려올 Activity 도 없다 — 구조적 불가. 실제 작성 후 `:app:compileDebugKotlin` 결과: `Argument type mismatch: actual type is ArrangerAccessibilityService, but 'android.app.Activity' was expected`. 리플렉션/hidden API 우회는 유지비용 원칙상 기각. **프로브 G2 대상에서 제외** |
| **B1** *(채택·구현됨)* | `ActivityOptions.makeTaskLaunchBehind()` | Q3 — `onResume` 미호출이라 자가 가드 구조적 무발화. NEW_DOCUMENT 가 `autoRemoveRecents=true` 를 유발하므로 **`RETAIN_IN_RECENTS` 병기 확정**. 최종 플래그 = `NEW_TASK \| NEW_DOCUMENT \| RETAIN_IN_RECENTS`, extras 무탑재(§4 계약). 출현 폴링 150ms/2s |
| **B2** | 일반 실행 + `EXTRA_PRELAUNCH_CARD` **1회성 소비 플래그** + 즉시 `moveTaskToBack(true)` | Q4 + 18차 실측(런치 직후 HOME → 가드 미발화·카드 생성 확인). 순간 전체화면 깜빡임 비용 |
| **B3** | 피커 `all_apps_button` / `search_button` 경유 | §2.1 — 로케일 무관 셀렉터는 확보됐으나 1페이지 부재·scrollable 미노출. **최후 폴백** |

**B2 의 가드 처리**: 세션 스코프 억제(세션 중 가드 전면 무력화)는 **기각** — 세션이 실패로 끝나면
패널이 전체화면에 남아 화면을 강탈한다. `EXTRA_PRELAUNCH_CARD` 를 **첫 `onResume` 에서만 소비**하는
1회성 플래그로 좁힌다(가드 스케줄 생략 + `moveTaskToBack`). 두 번째 `onResume`(피커 탭)부터 기존 동작 그대로.
**`onPause` 가 가드 job 을 취소하는 성질에 기대는 타이밍 의존 구현은 금지** — ADR-2 위반.

### 3.3 도메인 표면 (ADR-4)

```
domain/PanelTaskPolicy.kt   (신규, 순수 Kotlin)
  data class PanelTaskSnapshot(val taskId: Int, val componentClassName: String?, val lastActiveMs: Long)
  fun needsSummon(tasks: List<PanelTaskSnapshot>, panelClassName: String): Boolean
  fun pruneTargets(tasks: List<PanelTaskSnapshot>, panelClassName: String): List<Int>   // MRU 1개 보존
```
platform/service 는 스냅샷 변환과 실행만. 경계값: 빈 목록 / 타 컴포넌트만 / 패널 다중 / 컴포넌트 null(조회 실패) / 동률 lastActive.

### 3.4 롤백 레버 (#20·#12 관례) 〔19차 기각 — 축 B 와 함께 제거됨〕

~~`config/window_profiles.json` `defaults.panelCardPreflight` (키 부재 = true) — 소환만 끈다.~~
`WindowProfilesParser` 에 `coverAutoDismiss`/`flexAutoTopPlacement` 와 **동형** 추가.
축 A(파괴 제거)는 레버 대상이 아니다 — 되돌릴 이유가 결함 재생산뿐이다.

### 3.5 조용한 실패 금지

- `panel-card: already-present | summoned(mode=add-app-task|launch-behind|move-to-back) | summon-failed(reason=…) | lever-off`
- `pruneExtraPanelTasks: 보존 1 / 제거 N`
- step3 3전멸 시 토스트에 사유 병기
- `FailureReason` enum **확장하지 않음** (머신 무변경 — 소환은 머신 밖 사전 조건)

---

## 4. 인접 결함 — base intent 오염 [확정, Q2]

`performDismissSplit` 인텐트 폴백 = `FLAG_ACTIVITY_NEW_TASK` + `EXTRA_FINISH_PANEL`.
패널 태스크가 **없는** 상태에서 이 경로를 타면 `EXTRA_FINISH_PANEL` 이 **base intent 에 보존**되고
(Q2: `Task#setIntent` 은 extras 를 그대로 유지), 이후 피커 탭이 그 카드를 재실행하면
`onCreate` 가 즉시 종료 → **step3 영구 실패 루프**.

**수정**: 폴백 진입 전 패널 태스크 존재를 확인하고, 없으면 폴백 자체를 생략한다
(태스크가 없다 = 해제할 우리 분할이 없다). 소환 인텐트도 **extras 무탑재**를 계약으로 고정한다.

---

## 5. 기각한 대안

| 대안 | 기각 사유 |
|---|---|
| LAUNCH_ADJACENT 부활 | 10차에 구조적 근거로 삭제(분할-선택 파괴, 성공 0회, 예산 오염원). 원인이 다른데 폐기 수단을 되살릴 이유 없음 |
| purge 완전 삭제 | 다중 패널 태스크 누적을 방치. 축소(A2)로 충분 |
| 축 B 단독 (파괴는 그대로 두고 매번 소환) | 매 세션 소환 비용 + 커버 해제마다 카드 파괴 → 불필요한 왕복. 파괴가 근거 없는 초과 동작임이 밝혀진 이상 그걸 남길 이유 없음 |
| 축 A 단독 (소환 없음) | 재설치·수동 스와이프·시스템 트리밍(`isTrimmable=true`)으로 카드 0 이 여전히 발생 |
| Shizuku 셸로 패널을 인접 페인에 직접 투입 | P4-1 이 능력을 실증했으나 Shizuku 는 **선택 설치** — 주 경로 불가. v1.5 후보 |
| 고정 지연으로 가드 회피 (`onPause` 취소 성질 이용) | ADR-2 정면 위반. 타이밍 도박 |

---

## 6. 프로브 결과 (18차 G1·G3 통과 → 19차 나머지 판정 완료)

**전 게이트 종료.** G2 만 실패했고, 그 실패가 축 B 기각의 직접 근거다 (상세 = DEVICE_FACTS 19차 절).

| # | 19차 판정 |
|---|---|
| G2 | ❌ 소환 카드가 step3 에서 전체화면 낙착 → 축 B 기각 |
| G4 | ✅ 커버 해제 후 카드 생존 + 3/3 done |
| G5 | ✅ `보존 1 / 제거 1` + step3 성공 |
| G6 | ✅ 재설치 죽은 카드로 done |
| G7 | ✅ 함정 미발동 (`RETAIN_IN_RECENTS` 상쇄 추정) |

이하 18차 시점의 원 계획:

| # | 확인 | 통과 기준 |
|---|---|---|
| G2 | ~~B0~~ → **B1 `makeTaskLaunchBehind()`** One UI 8 수용 (B0 는 컴파일 판정으로 배제, §3.2) | `panel-card: summoned(mode=launch-behind)` 로그 ∧ 포그라운드 유지(깜빡임 0) ∧ 피커에 「FW Panel」 출현 ∧ 탭 시 분할 페인 낙착. 실패 시 B2(`moveTaskToBack`)로 강등 — v1 미구현이라 설계 복귀 필요 |
| G4 | 결함 재현 시나리오 E2E | 커버 자동 해제로 카드 0 → 즉시 배치 → **3연속 done** |
| G5 | prune × 소환 무자충 | `pruneExtraPanelTasks: 보존 1` 로그 ∧ 카드 생존 ∧ step3 성공 |
| G6 | 재설치 스테일 회귀 | 패널 태스크 생성 → 재설치 → 배치 1회 성공 (G3 로 premise 는 이미 반증됐으므로 확인 성격) |
| G7 | Q1-함정 회귀 | 소환된 카드가 한 번도 가시화되지 않은 채 finish 되는 경로가 없는지 (B0 는 액티비티 미생성이라 비대상) |

---

## 7. v1 범위 / 이월

**최종 v1 (19차 후 확정)**: §3.1 축 A(A1·A2) · §3.3 도메인(`PanelTaskPolicy`) · §4 인접 결함(#28) · §3.5 로깅.
**축 B·레버는 제거**됐다 (19차 기각).

이하 18차 시점의 원 범위:

~~**v1**: §3.1 축 A(A1·A2) · §3.2 축 B(B0, 실패 시 B1) · §3.3 도메인 · §3.4 인접 결함 · §3.5 로깅 · 레버~~
**이월(v1.5)**: B3 앱그리드/검색 폴백 · Shizuku 인접 투입 · 재부팅 후 base intent 디스크 복원 시 extras 보존 여부([불명], Q2)

---

## 8. 완료 기준 (DoD)

1. `./gradlew :app:testDebugUnitTest` 통과 — `PanelTaskPolicy` 테스트 신규 포함
2. `./gradlew :app:assembleDebug` 통과
3. G2·G4·G5·G6·G7 결과를 `docs/DEVICE_FACTS.md` 에 기록 (미통과는 [미검증] 명시)
4. `PROGRESS.md` 열린 질문 #27·#28 갱신
5. 레버 `panelCardPreflight=false` 회귀 1회 (소환만 꺼지고 축 A 는 유지되는지)
