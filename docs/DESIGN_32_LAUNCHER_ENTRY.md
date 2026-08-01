# DESIGN_32 — 런처 진입점 통합 (앱 서랍 아이콘 3 → 1)

**상태:** 검토 완료 · **코드 변경 0** · v1.5 후보로 등재
**작성:** 2026-08-01
**요청:** "3개의 앱으로 나누어진 것을 1개의 앱으로 통합할 수 있는지 검토"

---

## 0. 한 줄 결론

**3 → 2 는 무위험이고 지금 당장 가능하다. 2 → 1 도 가능하지만 「합치기」로는 불가능하고,
「진입점 소유권 이전」으로만 성립한다.** 그리고 그 방향의 전제(피커 노출이 MAIN/LAUNCHER
때문이라는 것)는 리포가 스스로 **「추정」이라고 적어 둔 미검증 명제**라서, 착수 전에 실기기
A/B 로 인과를 확정해야 한다.

---

## 1. 배경 — 이 항목은 한 번 인지되고 미뤄졌다가 유실됐다

`PanelActivity` 의 런처 노출은 처음부터 **의도된 부채**였고, 재검토 시점까지 못 박혀 있었다.

`PROGRESS.md:369` (2026-07-25 결정 로그):
> `PanelActivity` MAIN/LAUNCHER 노출 수용 (라벨 "FW Panel") | 분할 파트너 피커 목록에 뜨려면
> 필요. **앱 서랍 오염은 Phase 3 재검토**

`PROGRESS.md:307` (파일 인벤토리):
> 라벨 "FW Panel" = SplitEntry 피커 셀렉터 계약 … **MAIN/LAUNCHER 노출은 Phase 3 재검토**

`app/src/main/AndroidManifest.xml:30-33`:
> P2-5 / ADR-3: 파트너(비영상) 창. MAIN/LAUNCHER 필터가 필요한 이유: One UI 분할 화면 파트너
> 피커(SplitEntry step4 폴백, "FromRecentActivity")가 런처에 노출된 앱만 후보로 나열한다.
> **앱 서랍에 아이콘이 노출되는 부작용은 Phase 2에서는 수용하고, Phase 3에서 별도 태스크/숨김
> 아이콘 처리로 재검토한다.**

**그 재검토는 수행되지 않았다.** 현재 Phase 4 이고(`PROGRESS.md:7`) Phase 3 는 완료 확정인데,
결정 로그에 후속 기록이 0건이며 열린 질문 목록에도 없다. 이 문서가 그 유실된 항목을 근거와 함께
복구한다.

부수로 발견된 오기 하나 — `docs/IMPROVEMENT_PLAN_2026-07-29.md:51` 은 S1(프로브 debug 격리)의
부수 효과를 "릴리스에서 접근성 서비스 1개·**런처 아이콘 1개**로 정상화" 라고 적었으나, 실제
릴리스는 그때도 지금도 **2개**다(`PanelActivity` + `OnboardingActivity`). 정정 부기 완료.

---

## 2. 현재 상태 — 「3개의 앱」의 정체

MAIN/LAUNCHER intent-filter 를 가진 액티비티가 3개이고, 그것이 앱 서랍의 아이콘 3개다.

| 컴포넌트 | 서랍 라벨 | 라벨 출처 | 소스셋 |
|---|---|---|---|
| `.ui.OnboardingActivity` | FoldWindow | `android:label` **미지정** → application 라벨 `app_name` 상속 (`AndroidManifest.xml:89-98`, `strings.xml:3`) | main |
| `.ui.PanelActivity` | FW Panel | `android:label="@string/panel_title"` (`AndroidManifest.xml:36-47`, `strings.xml:10`) | main |
| `.probe.ProbeActivity` | FoldWindow Probe | `android:label="@string/probe_title"` (`app/src/debug/AndroidManifest.xml:6-16`, `strings.xml:4`) | **debug 전용** |

- **릴리스 = 2개.** 병합 매니페스트(`app/build/intermediates/packaged_manifests/release/…`) 실측:
  LAUNCHER 카테고리 3회 = 액티비티 필터 2 + `<queries>` 1. probe 4종·`ArrangeTriggerReceiver`·
  `FileProvider` 는 0건(W1/S1 격리 결과).
- **아이콘은 셋 다 같다.** 액티비티별 `android:icon` 지정이 0건이라 전부
  `<application android:icon="@mipmap/ic_launcher">` (`AndroidManifest.xml:25`)를 상속하고
  **라벨로만 구분**된다. 사용자 눈에는 "같은 아이콘 3개"로 보인다.
- 예외 선례: `.ui.PairShortcutActivity` 는 `<intent-filter>` 가 **아예 없고** `exported="true"`
  단독이다(`AndroidManifest.xml:105-111`). 아이콘도 recents 흔적도 없다.

**주의 — probe 는 매니페스트만 격리됐다.** Kotlin 소스는 `main` 에 그대로 있고 릴리스 APK 에도
컴파일돼 들어간다(`isMinifyEnabled = false`, `app/build.gradle.kts:27`). 릴리스에서 사라진 것은
**도달 가능성**이지 바이트코드가 아니다. `probe/` 완전 삭제는 v1.5 이월 확정
(`docs/IMPROVEMENT_PLAN_2026-07-29.md:526` — F2 2단계 측정에 필요).

---

## 3. 「합치기」는 불가능하다 — 깨지는 것 4가지 (전부 코드 사실)

`PanelActivity` 를 `OnboardingActivity` 와 한 컴포넌트로 합치면:

### 3.1 사용자의 온보딩 태스크가 지워진다

`pruneExtraPanelTasks` 는 `ActivityManager.appTasks` 를 `PanelActivity::class.java.name` 으로
필터해 대상만 `finishAndRemoveTask()` 한다
(`ArrangerAccessibilityService.kt:1695-1708`, 특히 `:1698` `:1705`).
합치면 클래스명이 하나가 되어 **사용자가 열어 둔 온보딩 태스크가 매 세션 시작마다 제거된다**
(호출점 = `beginSession` 선두, `:1533`).

### 3.2 자동 트리거의 유일한 「분할 해제 관측점」이 사라진다

`PanelActivity.onDestroy` → `ArrangerAccessibilityService.onPanelDestroyed()` →
`AutoTriggerLedger.onSplitDismissed()` (`PanelActivity.kt:153`,
`ArrangerAccessibilityService.kt:1195-1196`).

이 지점이 **(a) 메뉴 「분할 해제」 (b) 커버 자동 해제 (c) 자가 가드 finish (d) 사용자 BACK/디바이더
드래그 — 4경로를 모두 덮는 유일한 포착점**이다(`PanelActivity.kt:148-152`). 여기서 거는 래치는
**sticky** 라 홈 런처로 풀리지 않으며, 이게 없으면 해제 직후 대상 앱의 전체화면 복귀가 새 진입
엣지가 되어 **#30 이 22차에 고친 P-1 재발화 루프로 되돌아간다**(`DEVICE_FACTS.md` 22차 절).

### 3.3 finish 경로 5개가 전부 `PanelActivity` 인스턴스/태스크를 전제한다

토큰 소비 finish(`PanelActivity.kt:77-99`), 전체화면 자가 가드(`:126-137`), 서비스의 인스턴스
직접 finish(`ArrangerAccessibilityService.kt:593-597`), 인텐트 폴백(`:611-620`), 커버 자동
해제(`:1150-1160`).

### 3.4 finish 토큰 DoS 방어의 전제가 흔들린다

`EXTRA_FINISH_TOKEN` + `@Volatile finishToken` 1회용 소비(`PanelActivity.kt:200-215`)는
**exported + MAIN/LAUNCHER 이기 때문에** 생긴 방어다(W1/S4). 컴포넌트를 합치면 이 방어의
적용 대상과 범위를 다시 설계해야 한다.

---

## 4. 성립 가능한 유일한 방향 — 진입점 소유권 이전

두 액티비티를 **합치지 않고**, `OnboardingActivity` 의 LAUNCHER 필터를 제거해
**`PanelActivity` 를 유일한 런처 진입점으로 남긴다.**

§3 의 4가지에 **전부 무접촉**이다. 그리고 부수 이득이 하나 있다 — 2026-07-25 의 피커 오클릭
사고(`SplitEntry.kt:728-730`)는 *우리 패키지의 런처 항목이 2개였고 셀렉터가 틀린 쪽에 매치한*
사고였다. 항목이 1개가 되면 **오클릭 대상이 존재하지 않는다.** 회피가 아니라 원인 제거다.

---

## 5. 급소 — PanelActivity 의 LAUNCHER 는 하중을 받는다 (단 인과는 미검증)

### 5.1 셀렉터 계약

`SplitEntry.kt:731`:
```kotlin
val PANEL_LABEL_CANDIDATES: List<String> = listOf("FW Panel")
```
이 후보가 **DRAG step3(`SplitEntry.kt:261`)와 MENU step4(`:332`) 양쪽의 유일한 셀렉터**이며
(공용 함수 `findPanelPickerNode` `:511-529`), 이 단계가 **실제로 분할 쌍을 만든다.**
`strings.xml:8-9` 에 `panel_title` **값 변경 금지** 주석이 붙어 있는 이유다.

바로 위 주석이 실측 근거다(`SplitEntry.kt:728-730`):
> [실측 2026-07-25] "FoldWindow" 후보가 P3-4 OnboardingActivity 라벨(`@string/app_name`)과
> 충돌 … "FW Panel" 만 남긴다. **후보 추가 시 앱 서랍에 노출되는 다른 액티비티 라벨과 겹치지
> 않는지 반드시 확인할 것.**

**매칭은 `contains` 부분 문자열이다** (`SplitEntry.kt:518`):
```kotlin
if (text.contains(label) || desc.contains(label)) { … }
```
이 한 줄이 §7 의 L1 선행 의존을 만든다.

### 5.2 인과가 미검증이라는 사실

`docs/DEVICE_FACTS.md:864-877` (19차):
> 레버 off + 패널 카드 **0개** 상태에서 배치 5회 전부 `arrange done` … `pm clear` 표본이
> "신규 설치 앱이라 피커에 노출된 것"이라는 교란을 배제한다. 즉 피커는 recents 카드가 아닌
> **앱 목록**에서도 「FW Panel」을 제공한다 (**`PanelActivity` 의 MAIN/LAUNCHER 노출이 근거로
> 추정**).

19차는 "카드 0 에서도 성공"만 관측했고 **원인 변수를 조작하지 않았다.** 대체 경로는 전멸했다 —
「소환」(축 B)은 19차 기각·코드 제거(`docs/DESIGN_27_PANEL_CARD.md:12-17`), 앱 서랍 폴백은
도달 불가 확정(`docs/DEVICE_FACTS.md:759-765`).

⇒ **인과 확정 없이 L3 착수 금지.** 그리고 A/B 가 "LAUNCHER 때문이 **아니다**"를 내면 훨씬 싼
경로가 열린다(§6 의 ALT).

---

## 6. 로드맵 — L1 / L2 / L3 (각 단계 독립 중단 가능)

| 단계 | 내용 | 위험 | 실기기 |
|---|---|---|---|
| **L1** | `ProbeActivity` 의 LAUNCHER 필터 제거 (디버그 3→2) | **없음** | 6항목(형식적) |
| **L2** | 실기기 A/B — 피커 노출의 LAUNCHER 의존성 확정. **코드 영구 변경 0** | 낮음 | 16세션 |
| **L3** | `OnboardingActivity` LAUNCHER 제거(3a) + 아이콘 탭→온보딩 라우팅(3b) + 라벨 개명(3c) | 중간 | 12항목 |

의존: L3c 는 **L1 + L3a 선행 필수**(§7). L3a 단독은 UX 파손이라 **L3b 와 같은 세션**에 넣어야 한다.

### 6.1 L1 — 디버그 3 → 2 (무위험)

`app/src/debug/AndroidManifest.xml:12-15` 의 `<intent-filter>` 블록만 삭제.
`exported="true"`·`label`·`resizeableActivity`·`configChanges` 는 전부 유지.

**무위험인 이유:**
1. 릴리스에 `ProbeActivity` 선언 **자체가 없다** — 존재하지 않는 선언의 자식 요소를 지우는 변경은
   릴리스 변형에 도달할 경로가 없다.
2. **워크플로 손실 0.** 리포의 프로브 실행 절차는 3종이고 **어느 것도 LAUNCHER 에 의존하지 않는다**:
   - `adb shell am start -n dev.dj.foldwindow/.probe.ProbeActivity` (`CLAUDE.md:151`)
   - `adb shell am broadcast -a dev.dj.foldwindow.probe.RUN_PROBE` (`docs/DEVICE_FACTS.md:306-310`) —
     **주 트리거**다. 핵심 프로브 E(레터박스 실측)는 ProbeActivity UI 로는 **실행 불가**하다
     (`probe/ProbeTriggerReceiver.kt:11-14`: "실행 버튼이 ProbeActivity 안에 있어 전체화면을
     벗어나야만 누를 수 있는 설계 공백")
   - `adb shell cmd package query-activities` — W1-2 의 실제 판정 수단(`docs/DEVICE_FACTS.md:1155`)
3. **리포 내 선례**: `PairShortcutActivity` 가 필터 없이 exported 만으로 명시 실행되며 lint 를
   통과하고 있다(`AndroidManifest.xml:105-111`).

**함께 고칠 문서:** `docs/DEVICE_VERIFICATION_RUNBOOK.md:177`(W1-2 판정 문구에서 "런처 아이콘이
존재한다" 삭제 → 접근성 목록 + `am start -n` `Status: ok` 로 교체) · `docs/DEVICE_FACTS.md:918`
및 `:1155`(과거 판정은 **고쳐 쓰지 말고** 포인터만 부기 — append-only) · `CLAUDE.md:151` 주석 ·
`app/src/main/AndroidManifest.xml:86-88` 주석(**릴리스 매니페스트까지 실려 나가는 주석**이라
사실과 어긋난 채 두면 안 된다).

**L1 검증표**

| # | 항목 | 절차 | 판정 기준 |
|---|---|---|---|
| L1-1 | 진입점 인벤토리 | `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \| grep dev.dj.foldwindow` | 정확히 2행(Panel·Onboarding), `.probe.ProbeActivity` 0행 |
| L1-2 | 서랍 육안 | 앱 서랍 검색 | 아이콘 2개, "FoldWindow Probe" 부재 |
| L1-3 | 프로브 명시 실행 생존 | `adb shell am start -n dev.dj.foldwindow/.probe.ProbeActivity` | `Status: ok` + 프로브 화면 |
| L1-4 | 프로브 서비스 불변 | 설정 → 접근성 | `FoldWindow Probe` 항목 존재 |
| L1-5 | 주 트리거 불변 | `am broadcast -a dev.dj.foldwindow.probe.RUN_PROBE` | `result=0` + 리포트 생성 |
| L1-6 | 주 경로 무회귀 | 유튜브 가로 전체화면 → 배치 1회 | `arrange done` · step3 매치 · `ENTRY_STEP_FAILED` 0 |

L1-6 은 형식적 확인이다("FoldWindow Probe" 는 `"FW Panel"` 과 겹치지 않는다). 다만 **L3c 를 할
예정이라면 L1-6 의 로그를 대조군으로 보관**한다.

### 6.2 L2 — 인과 확정 (측정, 코드 영구 변경 0)

**명제 H1:** 카드 0 상태에서 피커 앱 목록에 「FW Panel」이 나타나는 것은 `PanelActivity` 의
MAIN/LAUNCHER 선언 **때문이다.**

| 암 | 빌드 |
|---|---|
| A (대조군) | `HEAD` 그대로 |
| B (실험군) | `HEAD` + `app/src/main/AndroidManifest.xml` 의 **PanelActivity `<intent-filter>` 만 삭제** (label·exported·taskAffinity 등 전부 유지) |

암 B 는 안전하다 — Onboarding 이 아직 LAUNCHER 를 갖고 있어 앱이 계속 실행된다.

**조건:** C0 카드 0(`pm clear`, 19차와 동일 수단) / C1 카드 존재
(`am start -n …/.ui.PanelActivity` 후 홈, `DEVICE_FACTS.md:768-771`).
**버블은 끈다** — 버블 ON 이 피커 탭을 전체화면으로 낙착시키는 별개 교란이 실측돼 있다
(`DEVICE_FACTS.md:171-173`). 트리거는 전 구간 `ArrangeTriggerReceiver` 브로드캐스트.

**판정 수단은 프로덕션 매칭 코드 자신이다** — `step3 panel-picker matched via selector
[panel-label:FW Panel]` → `arrange done` (성공) / `node-not-found` → `ENTRY_STEP_FAILED` (실패).
별도 판정 도구를 만들면 그 도구의 정확성이 새 미검증 항목이 된다.
보조로 `uiautomator dump` 로 피커 트리를 직접 관측하되, 기존 덤프에 U+FEFF 가 섞여 있으므로
`FW` 와 `Panel` 을 따로 grep 하고 최종 판정은 로그에 맡긴다.

| 셀 | n | 판정 |
|---|---|---|
| A / C0 | 5 | **5/5 done** 이어야 실험 유효(19차 재현). 미달 = 환경 오염 → 중단 |
| B / C0 | 5 | **0/5 done + 5/5 `node-not-found`** ⇒ H1 참 |
| B / C1 | 3 | 카드 경로가 LAUNCHER 없이도 사는지 분리 |
| A / C1 | 3 | 대조 |

**결과 3분기**

| 결과 | 결론 | 방향 |
|---|---|---|
| B/C0 전멸 | **H1 참** | §4 본안(진입점 소유권 이전) |
| B/C0 성공 | **H1 거짓** | **ALT: PanelActivity 쪽 LAUNCHER 만 제거.** Onboarding 이 유일 진입점으로 남으므로 라벨·자가가드·장부 문제가 **전부 소멸**한다. 훨씬 싸다 |
| B/C0 전멸 + B/C1 성공 | LAUNCHER 는 앱 목록 경로에만 필요 | H1 참 취급 — 카드 0 은 실제로 발생한다(재설치·커버 자동 해제·prune) |

**부수 측정(M4):** 암 A 에 로그 1줄을 임시 추가해
`intent.action`/`categories`/`flags`/`isTaskRoot`/`isInMultiWindowMode` 를 찍고,
**(i) 서랍 아이콘 탭 (ii) 피커에서 FW Panel 선택** 을 각 5회 관측한다.
답할 것 = **"피커 경로에만 있고 서랍 경로에는 없는 동기 판별자가 있는가"**
(1순위 후보 `FLAG_ACTIVITY_LAUNCH_ADJACENT` = 0x00001000). 있으면 L3b 가 지연 0 으로 분기할 수
있고, 없으면 기존 600ms 가드 재사용이 확정된다.

**위생:** 암 B 는 **커밋하지 않는다**(워킹트리 편집 후 `git checkout --` 복구 + `git status` 빈
출력 확인 — 20차 W5-4 절차). 매 설치 후 **함정 #6**(접근성 서비스 꺼짐) 재활성화 필수이며 이 설정
키는 **덮어쓰기**라 콜론 목록에서 빠진 서비스는 꺼진다. 암 전환마다 APK 타임스탬프 + `versionCode`
3중 확인(21차의 「구버전 설치본으로 40분」 재발 방지).

### 6.3 L3 — 2 → 1 (H1 참일 때)

**L3a** — `app/src/main/AndroidManifest.xml:94-97` 의 OnboardingActivity `<intent-filter>` 삭제.
`exported` 는 이번 단계에서 **건드리지 않는다**(변수 1개 유지).

온보딩 진입 경로는 **4곳 전부 살아남는다**(전부 명시 컴포넌트 또는 PendingIntent):

| # | 경로 | 코드 |
|---|---|---|
| 1 | 버블 탭(a11y 꺼짐)·메뉴 「설정」 등 5개 호출부 | `FloatingLauncherService.launchOnboarding()` `:602-607` |
| 2 | 페어 바로가기 폴백 | `PairShortcutActivity.kt:47-52` |
| 3 | 최초 설치 후 사용자 | 아이콘 → PanelActivity → **L3b 라우팅** |
| 4 | **포그라운드 알림 탭** | `FloatingLauncherService.startForegroundCompat()` `:369-375` — `PendingIntent` 는 생성자 신원으로 실행되므로 필터 무관 |

부수 영향 2건: `getLaunchIntentForPackage` 가 **모호(2개) → 결정적(Panel)** 으로 바뀐다 ·
고정 바로가기(`requestPinShortcut` `:971-1002`)는 `setActivity()` 를 주지 않아 프레임워크가 기본
메인 액티비티를 채우므로 **귀속이 Onboarding→Panel 로 바뀐다**(검증 항목 L3-7).

**L3b** — 아이콘 탭 → 온보딩 라우팅. **급소 2개:**

1. **`onCreate` 에서 `isInMultiWindowMode` 로 분기하면 모든 분할이 파괴된다.** 서랍 아이콘·피커
   앱 목록·recents 카드가 **전부 동일한 `ACTION_MAIN`+`CATEGORY_LAUNCHER` 인텐트**로 도착하고,
   600ms 유예가 존재하는 이유가 정확히 "분할 배치 전환 중 일시적으로 멀티윈도우 아님으로 보고될
   수 있어 즉시 종료하면 정상 배치를 죽인다" 이다(`PanelActivity.kt:105-107`).
   ⇒ 판정은 **기존 자가 가드(`scheduleFullscreenCheck`)의 확정 시점을 재사용**하고
   **새 고정 지연을 추가하지 않는다**(ADR-2). 600ms 값도 바꾸지 않는다(함정 #7).
   조건 3개: `launchedAsAppEntry`(onCreate 1회 계산, `isTaskRoot` ∧ MAIN/LAUNCHER ∧ 복원 아님) ∧
   `!everInMultiWindow`(분할에 들어갔다 나온 인스턴스 배제) ∧ `!arrangeSessionActive()`(세션 실패
   위에 온보딩까지 얹지 않음).
2. **장부 오염 — 반드시 함께 넣어야 한다.** 아이콘 탭으로 뜬 인스턴스의 `onDestroy` 가
   `onPanelDestroyed()` 를 부르면 **sticky 래치**가 걸린다. sticky 는 홈으로 안 풀리므로
   *자동 배치가 한 번 발화한 뒤 사용자가 홈에서 아이콘을 탭하면 다음번 자동 배치가 조용히 죽는다.*
   ⇒ `routedToOnboarding` 일 때 `onPanelDestroyed()` 를 **건너뛴다**. 정당화: 그 인스턴스는
   `!everInMultiWindow` 로 **분할에 들어간 적이 없음이 확정**됐으므로 "분할 해제" 신호가 의미상
   거짓이다. §3.2 의 4경로 커버리지는 손상되지 않는다.

**최초 설치 UX(있는 그대로):** 흰 번쩍(`Theme.FoldWindow` = Material Light) → 검정 패널 0.6초 →
온보딩(별도 태스크). 기능 손실 0. recents 에 종료된 패널 카드가 남는데 **막으려 하지 말 것** —
19차가 무해함을 실증했고 오히려 step3 소환원이며 `pruneExtraPanelTasks` 가 MRU 1개로 유지한다.
`setContent` 를 판정까지 미루는 것도 금지(진짜 분할에서 패널이 0.6초 빈 화면이 된다).

**L3 검증표**

| # | 항목 | 판정 기준 |
|---|---|---|
| L3-1 | 진입점 1개 | `query-activities` 정확히 1행 = `.ui.PanelActivity` |
| L3-2 | 서랍 육안 | 아이콘 1개. L3c 후 "FoldWindow" 검색 히트 |
| L3-3 | **주 경로 무회귀(최우선)** | `pm clear` 카드 0 → 배치 ×5 = **5/5 done**, `node-not-found` 0. L1-6/L2-A0 로그와 대조 |
| L3-4 | MENU 경로 무회귀 | UNRESIZEABLE 앱 ×3 = 3/3 done, `menuStep4 panel-picker matched` |
| L3-5 | 아이콘 탭 → 온보딩 | 온보딩 표시 + 로그 1회 + 패널 잔존 0 (a11y 켜짐/꺼짐 각 3회) |
| L3-6 | **장부 오염 없음(회귀 급소)** | 자동 발화 → 해제 → 래치 해제 → 홈 → **아이콘 탭** → BACK → 유튜브 전체화면 = `fullscreen auto-arrange trigger` **1회 발화**. 0 이면 오염 확정 → 즉시 되돌림 |
| L3-7 | 고정 바로가기 | 생성 성공 + 탭 시 대상 앱 실행 + 배치 트리거 |
| L3-8 | 온보딩 4경로 | 4/4 표시, 각 별도 태스크(패널 태스크에 안 얹힘) |
| L3-9 | 분할 중 아이콘 탭 | **온보딩이 분할 위로 튀어나오지 않을 것**(`!everInMultiWindow` 실동작) |
| L3-10 | 세션 중 낙착 방어 | 배치 실패 유도 시 **온보딩이 뜨지 않을 것**. 유도 실패 시 `[미검증]` 으로 남긴다 |
| L3-11 | 최초 설치 UX | `pm uninstall` → 설치 → 아이콘 탭 → 온보딩 도달. 흰 번쩍 지속 육안 기록 |
| L3-12 | 재부팅 생존 | L3-1·L3-3·L3-5 재확인 |

---

## 7. 라벨 결정 — "FoldWindow" 로 개명 (L1 선행 필수)

L3a 만 하면 서랍의 유일한 아이콘 이름이 **"FW Panel"** 이 된다. One UI 서랍 검색은 액티비티
라벨을 매칭하므로 사용자가 "FoldWindow" 를 검색하면 아무것도 안 나온다 — 출시 제품으로는 결함이다.

**채택 = `panel_title` 과 `PANEL_LABEL_CANDIDATES` 를 동시에 `"FoldWindow"` 로 교체**(추가가 아니라 교체).

**과거 충돌 실측이 무효화되는 논거.** 2026-07-25 실측(`SplitEntry.kt:728-730`,
`DEVICE_FACTS.md:176`)이 보인 것은 *"FoldWindow" 라는 문자열이 본질적으로 모호하다*가 **아니다.**
보인 것은 *우리 패키지의 런처 항목이 **2개**였고 후보가 그중 틀린 쪽에 매치했다*는 것이다.
L3a 이후 항목은 **정확히 1개**이므로 우리 패키지를 식별하는 임의의 문자열이 곧 그 유일 항목을
식별한다. **오클릭 대상이 존재하지 않는다.**

**단, 잔여 위험 R1 이 L1 선행을 강제한다.** 매칭이 `contains` **부분 문자열**이므로
(`SplitEntry.kt:518`) 디버그 빌드의 `probe_title` = **"FoldWindow Probe"** 가 부분 문자열로
걸린다. **L1 이 프로브를 피커/서랍에서 빼지 않으면 L3c 는 2026-07-25 충돌을 디버그 빌드에서
그대로 재현한다.** `EntrySelectors` 주석에 "후보는 부분 문자열이다 — 서랍/피커에 노출되는 **모든**
라벨(디버그 포함)의 부분 문자열이 되지 않는지 확인" 을 명문화한다.

기타 잔여: 패널 recents 카드 제목도 "FoldWindow" 가 되지만 **그게 우리다**(카드 경로와 앱 목록
경로를 후보 1개로 동시에 덮어 오히려 단순화) · 타 앱 라벨 충돌 위험은 현재 "FW Panel" 과 동일
성질이라 회귀 아님 · `panel_title` 은 기본 `values/` 에만 있어 로케일 드리프트 없음.

**함정 #7 준수:** 검증된 상수(셀렉터 계약) 변경이므로 **새 실측 근거를 `docs/DEVICE_FACTS.md` 에
기록하지 않으면 변경 금지.** 순서는 L3a+L3b 착지 → 실기기 검증 → **그 다음** L3c → 재검증.
그래야 회귀 시 원인이 라벨인지 진입점 이전인지 즉시 갈린다.

`android:icon` 을 PanelActivity 에 명시할 필요는 **없다** — 이미 앱 아이콘을 상속하며, 명시할
유일한 이유는 "앱 아이콘과 다른 아이콘을 주고 싶을 때"인데 그건 원하지 않는 것이다.

---

## 8. 하지 말아야 할 것

1. **`PackageManager.setComponentEnabledSetting` 으로 런처 필터 동적 토글** — 최악.
   리포 선례 0건 · 런처 인벤토리가 **런타임 이력의 함수**가 되어 `cmd package query-activities`
   기반 검증표 전부가 재현 불가능해진다(이 리포의 방법론 자체를 공격) · 캐시 반영이 비동기라
   "인식할 때까지 대기"가 필요해지고 그건 **ADR-2 정면 위반** · `DONT_KILL_APP` 없이 호출하면
   프로세스가 죽어 접근성 서비스가 세션 중 사망 · 상태가 **재부팅을 넘어 영속**해 disabled 상태에서
   크래시하면 **진입점 0개 = 실행 불가능한 앱**.
2. **PanelActivity 를 다른 컴포넌트와 합치기** — §3 의 4가지. 재검토 대상 아님.
3. **600ms 를 바꾸거나 새 고정 지연 추가** — 함정 #7 / ADR-2.
4. **`PANEL_LABEL_CANDIDATES` 에 후보 「추가」** — 2개가 되는 순간 2026-07-25 모호성 클래스가
   부활한다. L3c 는 **교체**다.
5. **서랍에서만 숨기는 매니페스트 트릭** — 존재하지 않는다. 필터에 카테고리를 더 넣어도
   `MAIN+LAUNCHER` 질의는 그대로 매치한다(질의 카테고리가 필터의 부분집합이면 매치).
   `<activity-alias>` 로 라벨을 분리하는 안도 **후보가 2개로 늘어** 목표의 정반대다.
6. **L1 에서 `probe/` 삭제** — v1.5 이월 확정. L1 은 **필터만** 제거한다.
7. **DEVICE_FACTS 의 과거 판정을 고쳐 쓰기** — 새 절을 추가하고 옛 줄에는 포인터만 부기(append-only).
8. **L2 없이 L3 착수** — 방향 전체가 리포 스스로 「추정」이라 명시한 추론 위에 서 있다.

---

## 9. 미해결 / 낙관 금지

- **H1(피커 노출의 LAUNCHER 의존) 미확정.** L2 전까지 §4 방향은 가설이다.
- **M4 가 동기 판별자를 못 찾을 확률이 낮지 않다.** 그 경우 0.6초 깜빡임은 L3 의 확정 비용이며,
  이를 없애려 새 타이밍 장치를 도입하는 것은 금지다.
- **L3-9(분할 중 아이콘 탭)의 One UI 거동은 예측이다.** 표준 launchMode + 동일 affinity 태스크
  존재 시 "태스크 전면 복귀, `onCreate` 미호출"을 기대하지만 실측 대상이다. `launchedAsAppEntry`
  를 `onCreate` 1회 계산으로 **고정**하고 `onNewIntent` 에서 갱신하지 않는 이유가 이 불확실성이다.
- **L3-10 의 유도 가능성 불명.** 버블 ON 실패 모드가 `setBubbleHiddenForArrange` 도입 이후에도
  유도되는지 불명. 유도 실패 시 `[미검증]` 으로 정직하게 남긴다(B-1/B-2 선례와 동급).

---

## 10. 되돌리기

L1/L3a/L3b/L3c 전부 **DataStore·스키마·영속 상태를 건드리지 않는다.**
되돌리기는 `git revert` + `installDebug` + 접근성 재활성화(함정 #6)가 전부이며 마이그레이션이 없다.

**중단 기준:** 카드 0 상태에서 step3/menuStep4 `node-not-found` 로 인한 `ENTRY_STEP_FAILED` 가
1건이라도 나오면 → **L3c 를 먼저 되돌리고** 재검증 → 그래도 재현되면 L3a 되돌림.
순서가 정해져 있어야 원인이 갈린다.
