# 19차 실기기 캠페인 — #27 패널 카드 v1 검증

> 대상 커밋: `c252a01` (feat: #27 패널 카드 결함 v1 — 축 A + 축 B + #28)
> 설계: `docs/DESIGN_27_PANEL_CARD.md` §6 / 미결 목록: `PROGRESS.md` 「다음 행동」 1번
> 결과 기록처: `docs/DEVICE_FACTS.md` **19차 절 신설** (기존 절 서식 준수) → `PROGRESS.md` 갱신은 Advisor

---

## 0. 이 캠페인이 판정하려는 것

v1 은 **파괴를 걷어내고(축 A) 카드 0 을 위한 보험을 달았다(축 B)**. 실기기에서 확인할 명제는 3개다.

| 명제 | 게이트 |
|---|---|
| ① 우리 코드는 더 이상 카드를 죽이지 않는다 | G4 · G5 |
| ② 카드가 0이어도 스스로 만들어 낸다 | G2 |
| ③ 그 보험이 새 결함을 만들지 않는다 | G7 · G6 · 레버 회귀 |

**게이트 순서는 상태 의존성에 묶여 있다.** 특히 G2 는 「카드 0」에서 출발해야 하는데,
**A1 이후로는 커버 자동 해제가 더 이상 카드 0 을 만들지 않는다**(그게 수정의 핵심이다).
따라서 카드 0 은 **클린 설치로만** 인위적으로 만든다. 아래 순서를 지켜라.

---

## 1. 사전 조건 (매 세션 시작 시)

```bash
# 빌드 = 검증 대상 커밋인지 확인
git log --oneline -1          # c252a01 이어야 한다

# Git Bash 필수 프리픽스 (없으면 installDebug 가 조용히 실패해 구버전 APK 로 검증하게 된다 — 실측 40분 소모)
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug

# 함정 #6: 재설치하면 접근성 서비스가 꺼진다. 동일 값 put 은 no-op 이므로 none 토글 후 재설정
adb shell settings put secure enabled_accessibility_services none
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService

# 15차 함정: user-rotation 잔재가 있으면 FoldingFeature 가 물리 자세와 무관하게 VERTICAL
adb shell cmd window user-rotation free
adb shell settings get secure enabled_accessibility_services   # 값 확인
```

**Git Bash 경로 변환 함정 (10차)**: 원격 경로가 든 명령은 **통째로 인용**한다
(`adb shell "dumpsys ... | grep ..."`), `adb pull` 은 `MSYS_NO_PATHCONV=1` 프리픽스.

### 상시 계측 (별도 터미널 2개)

```bash
# 터미널 A — 이번 캠페인 핵심 로그
adb logcat -s FWArranger PanelActivity | grep --line-buffered -E "panel-card|pruneExtraPanelTasks|arrange (decision|done|failed)|clickCycle|dismissSplit|cover auto-dismiss|transition"

# 터미널 B — 세션 종료 대기용 (게이트마다 재실행)
adb logcat -s FWArranger -e "arrange (done|failed)" -m 1
```

### 카드 상태 조회 (모든 게이트에서 반복 사용 — `CARD?` 로 표기)

```bash
adb shell "dumpsys activity recents | grep -n -i -B2 -A6 PanelActivity"
```
확인 항목: 패널 태스크 **개수**, `taskId`, `Activities=[...]`(비었으면 액티비티 죽은 카드),
`autoRemoveRecents`, `isTrimmable`.

> **18차 기록 주의**: 패널 카드는 **recents 오버뷰(제스처 UI)에는 안 보이는데 분할 피커에는 보이는**
> 불일치가 관측됐다(`!hasChild() && !getHasBeenVisible()` 취급 차이 추정). **오버뷰 육안 = 판정 근거로 쓰지 마라.**
> 판정은 항상 `dumpsys` 와 피커 실물이다.

### 배치 트리거 (⚠ `-n` 필수 — 액션만으로는 implicit broadcast 제한으로 수신 안 됨)

```bash
adb shell am broadcast -a dev.dj.foldwindow.ARRANGE \
  -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver --es placement top
```

### 대상 앱 상태 셋업 (유튜브 가로 전체화면)

```bash
adb shell am force-stop com.google.android.youtube
adb shell am start -a android.intent.action.VIEW \
  -d 'https://www.youtube.com/watch?v=aqz-KE-bpKQ&t=120'
# 컨트롤 탭 → 전체화면 버튼 (2184×1968 가로에서 ≈1466,858)
adb shell input tap 1466 858
```
(기존 태스크에는 딥링크가 라우팅되지 않으므로 `force-stop` 선행이 필수다.)

### E2E 리셋 (게이트 사이)

패널 페인 탭 + `adb shell input keyevent 4` → `PanelActivity.finish()` → 상대 앱 전체화면 복귀.
**v1 부터는 이 리셋이 카드를 남긴다** — 그게 G1 이 실증한 A1 의 효과다. 리셋 직후 `CARD?` 로 확인하면
그 자체가 A1 의 상시 회귀 감시가 된다.

---

## 2. 게이트

### G2 — B1 `makeTaskLaunchBehind()` 소환이 One UI 8 에서 성립하는가 【최우선·가장 불확실】

축 B 전체가 이 한 API 의 수용 여부에 걸려 있다. **실패하면 v1 미구현인 B2(`moveTaskToBack`)로 설계 복귀**가 필요하다.

**카드 0 만들기 (유일하게 확실한 수단)**
```bash
adb uninstall dev.dj.foldwindow     # ⚠ DataStore(버블 위치·placement 기록) 함께 소실 — 수용
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
# 접근성 재설정 (§1) → CARD? 로 패널 태스크 0 확인
```

**실행**: 유튜브 전체화면 셋업 → 배치 트리거 1회.

| # | 통과 기준 | 확인 수단 |
|---|---|---|
| 2-1 | `panel-card: summoned(mode=launch-behind)` | 터미널 A |
| 2-2 | 소환 직후 카드 1개 출현 | `CARD?` |
| 2-3 | **포그라운드 무접촉** — 소환 순간 유튜브가 계속 resumed, 화면 깜빡임 0 | `adb shell "dumpsys activity activities \| grep -i ResumedActivity"` 소환 전후 비교 + 육안 |
| 2-4 | 자가 가드 침묵 — `fullscreen 상태 감지` 로그 **0건** (Q3: `onResume` 미호출 예측의 실증) | 터미널 A |
| 2-5 | 피커에 「FW Panel」 출현 → step3 성공 → `arrange done` | 터미널 A·B |

**실패 시 분기**
- `summon-failed(reason=start-exception)` → 예외 스택을 그대로 기록(`adb logcat -s FWArranger:W`). NEW_DOCUMENT/launch-behind 조합 거부 가능성
- `summon-failed(reason=timeout)` → 카드가 2s 안에 안 뜬 것인지, 아예 안 뜨는 것인지 `CARD?` 로 구분
- 2-3 위반(전체화면 깜빡임) → launch-behind 가 One UI 에서 무시되고 일반 실행된 것 → **B1 기각·B2 필요**
- 어느 실패든 **다음 게이트로 진행하지 말고 보고하라** — 축 B 설계 복귀 판단이 먼저다

---

### G7 — 소환 카드가 「한 번도 보인 적 없는」 상태로 finish 되면 사라지는가 【G2 직후에만 가능】

AOSP `Task#shouldAutoRemoveFromRecents()` 는 `!hasChild() && !getHasBeenVisible()` 이면
`autoRemoveRecents=false` 여도 **강제 제거**한다. 소환 카드는 정확히 그 상태다.

**G2 직후(소환됐고 아직 한 번도 분할에 쓰이지 않은 카드)** 상태에서:
```bash
adb shell cmd device_state state 0     # 커버(닫힘) 에뮬레이션 — 17차 신규 수단
# 터미널 A 에서 cover auto-dismiss 발화 여부 확인
adb shell cmd device_state reset
```

| 결과 | 판정 | 후속 |
|---|---|---|
| 카드 생존 | 함정 **미발동** — v1 무위험 | 기록만 |
| 카드 소멸 | 함정 **실재 확정** | v1 은 재소환 자기치유로 수용(설계 결정). 다음 배치가 `summoned` 로 복구되는지 **1회 실증**하고 기록 |
| `cover auto-dismiss skipped: reason=no-panel` | 소환 패널이 `instance` 에 안 잡힌 것 | 그 자체가 유의미한 사실 — 기록 |

> 이 게이트는 **결과가 무엇이든 v1 을 막지 않는다.** 목적은 자기치유 가정의 실증이다.

---

### G4 — 결함 재현 시나리오가 더 이상 재현되지 않는가 【본 게이트】

17차에 배치 전체를 불능으로 만든 그 시나리오다. **A1 이후 커버 해제는 카드를 남겨야 한다.**

```bash
# 준비: 정상 배치 1회 완료 상태(패널이 분할 페인에 있음)
adb shell cmd device_state state 0     # 커버 자동 해제 발화
adb shell cmd device_state reset
```

| # | 통과 기준 |
|---|---|
| 4-1 | `cover auto-dismiss fired` **∧** 분할 해소 **∧** `CARD?` 카드 **생존** (17차엔 여기서 카드가 사라졌다) |
| 4-2 | 이어서 배치 트리거 **3연속** → **3/3 `arrange done`**, `ENTRY_STEP_FAILED` 0건 |
| 4-3 | 3회 모두 `panel-card: already-present` (소환 불필요 = 축 A 가 제 역할) |
| 4-4 | `clickCycle: [step3 panel-picker] ... node-not-found` **0건** |

4-2 가 하나라도 실패하면 **원인이 카드 부재인지 다른 것인지**를 `CARD?` 와 step3 로그로 반드시 분리해 기록하라.

---

### G5 — prune × 소환 무자충

```bash
# 카드 1개 정상 상태에서 배치 트리거
```

| # | 통과 기준 |
|---|---|
| 5-1 | `pruneExtraPanelTasks` 로그 **침묵**(제거 0이면 로그 없음이 정상) **∧** 카드 생존 **∧** step3 성공 |
| 5-2 | 18차에 물증화된 자충 시퀀스(`카드 1 → purge → 0 → ENTRY_STEP_FAILED`)가 **재현되지 않음** |

**패널 태스크 2개 이상 만들기가 가능하면** 추가로 `pruneExtraPanelTasks: 보존 1 / 제거 N` 로그와
제거 후 카드 1개 생존을 확인한다. `NEW_DOCUMENT` 는 `MULTIPLE_TASK` 없이 동일 컴포넌트 태스크를
재사용하므로 **2개 상태를 못 만들 수 있다** — 못 만들면 "유도 불가"로 정직하게 기록하고 5-1 로 마감한다.
(억지로 만들려고 코드를 고치지 마라.)

---

### G6 — 재설치 스테일 회귀

18차 G3(죽은 카드 탭도 정상 낙착)로 premise 는 이미 반증됐으므로 **확인 성격**이다.

```bash
# 1) 패널 태스크가 있는 상태를 만든다 (배치 1회 후 BACK 리셋)
# 2) uninstall 없이 재설치 → 프로세스 강제 종료로 액티비티만 죽은 카드가 남는다
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
# 3) 접근성 재설정 (§1, none 토글 포함)
# 4) CARD? 로 Activities=[] 인 죽은 카드 확인
# 5) 배치 트리거 1회
```

| # | 통과 기준 |
|---|---|
| 6-1 | 죽은 카드 탭으로 **분할 페인 낙착**(전체화면 강탈 0) → `arrange done` |
| 6-2 | `fullscreen 상태 감지` 자가 가드 로그 0건 |
| 6-3 | 소환 불필요(`already-present`) |

---

### 레버 회귀 — `panelCardPreflight=false` 에서 소환만 꺼지는가

`config/window_profiles.json` 은 APK 에셋이라 **JSON 수정 후 재빌드·재설치**가 필요하다.

```bash
# config/window_profiles.json 의 defaults 에 "panelCardPreflight": false 추가
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
# 접근성 재설정 → 카드 0 상태(uninstall 후 재설치)에서 배치 트리거
```

| # | 통과 기준 |
|---|---|
| L-1 | `panel-card: lever-off` **∧** 소환 시도 없음(카드 계속 0) |
| L-2 | **축 A 는 그대로 산다** — BACK 리셋 후 카드 잔존, `pruneExtraPanelTasks` 가 MRU 를 안 지움 |
| L-3 | 레버 원복(키 삭제) 후 재설치 → `summoned` 정상 복귀 |

**끝나면 반드시 JSON 을 원복하고 커밋에 섞이지 않았는지 `git status` 로 확인하라.**

---

## 3. 기록 규칙

- 결과는 `docs/DEVICE_FACTS.md` **19차 절**에 게이트별로: **명령 → 로그 원문 → 판정**. 로그는 요약하지 말고 원문을 붙인다
- **미통과·미유도 항목은 `[미검증]` 으로 명시**한다. 통과로 뭉뚱그리지 않는다 (조용한 실패 금지)
- 새 실측 수치·기기 사실이 나오면 함정 #7 준수 — 근거를 DEVICE_FACTS 에 함께 남긴다
- 캠페인 중 코드를 고치고 싶어지면 **먼저 보고하라.** 현장 수정은 Advisor 승인 후 워커 위임 (11차·15차 선례)

## 4. 중단 조건

아래는 즉시 중단하고 보고한다 — 계속 두들기면 판정이 오염된다.

1. **G2 전 항목 실패** (소환 자체가 성립 안 함) → 축 B 설계 복귀(B2) 판단이 선행
2. 동일 게이트 **3연속 실패** → 원인 미상 클래스. `TYPE_VIEW_CLICKED` 포렌식·`dumpsys` 스냅샷을 남기고 중단 (#20 선례)
3. 배치가 **카드와 무관한 사유**로 실패 (측정·드래그·스왑 계열) → 이 캠페인 범위 밖. 별건으로 기록
4. `installDebug` 후 접근성 재설정을 빠뜨린 채 검증한 정황 → 그 구간 결과 **전부 폐기**하고 재실행
