# DEVICE_VERIFICATION_RUNBOOK — 실기기 검증 절차 (W1~W7 + 잔여)

> 작성 2026-07-31 · Advisor
> **목적:** 코드 구현은 전부 끝났고(W0~W7 / 23항목), 남은 유일한 작업인 **실기기 검증 31항목**을
> 최소 세션 수로 소화하기 위한 실행 절차서.
> **판정 근거의 출처(SSOT):** 각 항목의 정의는 `docs/DEVICE_FACTS.md` 의 W1~W7 절과
> `PROGRESS.md` 「남은 작업」 A·B 절이다. 이 문서는 **순서·묶음·명령**만 정한다.
> 판정 결과는 이 문서가 아니라 **`docs/DEVICE_FACTS.md` 원 항목의 `[미검증]` 을 갱신**해서 기록한다.

---

## 0. 이 절차의 전제 — 왜 실기기가 유일한 수단인가

W5·W6·W7 세 웨이브에서 qa 검증자가 **변조 실험을 3회 반복**했고 결론이 동일했다:

| 웨이브 | 변조 | 테스트 결과 | 결론 |
|---|---|---|---|
| W5 | `>` → `>=` (F9 부등호) | 29개 **전부 통과** | 무검출 |
| W6 | 7종 동시(폴백 6종 + `session = Session(...)` 을 `dispatch(Start)` 뒤로) | 312개 **전부 통과** | 무검출 |
| W7 | 9종 중 5종(`walk` 역순 · `accepts` 삭제 · 노드 예산 1 · 버블 클램프 · 경합 가드) | 322개 **전부 통과** | 무검출 |

`ArrangerAccessibilityService` / `SplitEntry` / `DividerPopupRotator` / `PaneSwapper` /
`FloatingLauncherService` / `NodeActions` / `Polling` 을 인스턴스화하는 JVM 테스트가 **0개**다.
**「테스트 322 통과」는 이 세 웨이브의 안전 근거가 아니다.** 아래 세션이 유일한 실효 검증이다.

---

## 1. ⚠ 착수 전 결정 1건 — 베이스라인 로그 확보 (권고: 채택)

W5-3 / W6-1 / W7-1 세 항목은 판정 기준이 **「종전 세션 로그와 대조」** 인데,
**대조 대상이 리포지토리에 없다.** `docs/DEVICE_FACTS.md` 에 남은 것은 발췌 2~3줄뿐이고
(472~475행), 전체 transcript 는 저장된 적이 없다. W5·W6 은 실기기에서 한 번도 안 돌았으므로
「W5 세션 로그」·「W6 세션 로그」라는 대조군 자체가 존재하지 않는다.

**권고 = S-B 세션을 먼저 1회 수행해 베이스라인을 만든다.**

- 베이스라인 커밋 = **`1965a72`** (W3 완료 직후 = W0~W4 포함, **W5·W6·W7 미포함**).
  구동부 재작성 3종이 전부 빠진 마지막 지점이라 정확히 옳은 대조군이다.
- `versionCode` 는 이미 3 (W2 에서 2→3). HEAD 와 동일하므로 설치 다운그레이드 문제 없음.
- 비용 ≈ 15분(빌드 1회 + 배치 1회). 이득 = W5-3 이 육안 판정에서 **기계 diff** 로 바뀌고,
  W7-1 의 셀렉터 이름이 「`ko-content-desc` 일 것으로 기대」에서 **실측 대조**로 바뀐다.
- **DRAG 세션(S1)에만 적용한다.** MENU 경로(W6-2·W7-3)와 부팅(W7-5)의 판정 기준은
  비교가 아니라 절대값(`recipe=MENU`, `clicked-self`, 위치 일치)이라 베이스라인이 필요 없다.

**채택하지 않을 경우:** W5-3 은 「전이 시퀀스가 상태 머신상 적법한 순서인가」의 육안 판정으로
격하되고, 그 사실을 `DEVICE_FACTS.md` W5-3 항목에 명시해야 한다(약한 근거임을 숨기지 않는다).

---

## 2. 사전 조건 (기기 연결 전)

### 2-1. 정적 DoD 재확인 — 설치 전 필수

HEAD(`20dd987`)는 문서 커밋이라 마지막 코드 검증은 `a8a3522` 시점이다. 설치할 바이너리를
그 자리에서 다시 통과시킨다.

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest    # 322 통과
./gradlew :app:assembleDebug
./gradlew :app:lintDebug            # 신규 0 (baseline 15)
```

### 2-2. adb 인증 — **현재 막혀 있음**

```
$ adb devices -l
R3CY8029XBF   unauthorized   transport_id:1
```

기기 화면의 **「USB 디버깅 허용」 대화상자에서 「항상 허용」 체크 후 확인**.
`adb devices` 가 `device` 로 바뀌어야 착수 가능하다.

### 2-3. 준비물

| 항목 | 이유 |
|---|---|
| 유튜브 (리사이저블 앱) | DRAG 레시피 경로. W3-1·W5-1·W6-1·W7-1·W7-4 |
| **넷플릭스 등 UNRESIZEABLE 앱** | MENU 레시피 + `DividerPopupRotator`. **리사이저블 앱으로는 W7-3 코드가 한 줄도 안 돈다** |
| Shizuku v13.6.0+ 설치·실행 | S3 전체(W2 7항목) |
| 넷플릭스 DRM 재생 가능 계정 | W2-3 · A-2 육안 |
| 재부팅 가능 상태 | S4 |
| 로그 저장 디렉터리 | `mkdir -p logs/` (git 미추적 권장) |

### 2-4. 로그 캡처 — 세션마다 별도 파일

**별도 터미널**에서 상시 구동한다. 태그를 빠뜨리면 판정 근거가 사라지므로 전체 목록을 쓴다.

```bash
# 세션 시작 직전마다: 버퍼 비우고 새 파일로
adb logcat -c
adb logcat -v threadtime \
  -s FWArranger:V FWArranger.Shizuku:V FWArrangeTrigger:V FWBootReceiver:V \
     FWDividerDragger:V FWDividerRotator:V FWFloatingLauncher:V FWFoldStateMonitor:V \
     FWGestureDrags:V FWHingeAngleMonitor:V FWNodeActions:V FWPairShortcut:V \
     FWPaneSwapper:V FWPanelActivity:V FWProbe:V FWProfileStore:V \
     FWResizeModeDetector:V FWSplitEntry:V \
  | tee logs/S1_drag.txt
```

---

## 3. 세션 지도

**핵심 제약이 순서를 결정한다:**

1. **앱 설치(`installDebug`)는 접근성 서비스를 끈다** (CLAUDE.md 함정 #6). → 설치 횟수를 최소화하고, 설치 직후엔 **항상** 재활성화한다.
2. **S1 → S2 는 재설치·프로세스 재시작 없이 연속으로** 해야 한다. W6-5(연속 2세션 상태 누수)가 「앱 A 배치 → 앱 B 배치」를 같은 프로세스 수명 안에서 요구한다.
3. **S7(허용치 실험)은 동작을 바꾼다.** `residualTolerancePx` 는 메시지 문구만이 아니라 `ArrangeStateMachine.kt:353` 에서 **폐루프 보정 발동 여부를 가르는 게이트**다. 0 으로 두면 잔여 1px 에도 보정이 1회 더 돈다 → 다른 세션의 「잔여 px·보정 횟수」 판정을 오염시킨다. **반드시 마지막에 격리 수행하고 원복한다.**
4. `config/` 가 assets 소스 디렉터리다(`app/build.gradle.kts:50`). JSON 수정은 **재빌드·재설치**를 요구한다.

| 세션 | 내용 | 항목 | 설치 | 예상 |
|---|---|---|---|---|
| **S-B** | (권고) 베이스라인 로그 — `1965a72` DRAG 1회 | — | 1회 | 15분 |
| **S0** | HEAD 설치 · 권한 · 스모크 | W1-1 W1-2 | 1회 | 10분 |
| **S1** | DRAG 주경로 (유튜브) + 취소·해제 | W3-1 W5-1 W5-3 W6-1 W6-3 W6-4 W7-1 W7-4 W7-2 | — | 20분 |
| **S2** | MENU 경로 (넷플릭스) — **S1 직후 연속** | W5-2 W6-2 W6-5 W7-3 W7-2 | — | 20분 |
| **S3** | 팝업 / Shizuku | W2-1~W2-7 A-2 | — | 30분 |
| **S4** | 부팅 경로 (재부팅) | W7-5 W7-6 | — | 15분 |
| **S5** | 실패·희귀 경로 | W3-2 W1-3 W1-4 B-1 B-2 | — | 20분 |
| **S6** | 물리 접기 | A-1 | — | 10분 |
| **S7** | 허용치 실험 → **원복** | W5-4 | 2회 | 20분 |

합계 ≈ 2시간 40분 / 설치 4~5회. **S1~S2 는 반드시 붙여서, S7 은 반드시 마지막.**

---

## 4. 세션 상세

### S-B — 베이스라인 (권고, 1절에서 채택한 경우)

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
git status                       # clean 확인 (더러우면 중단)
git checkout 1965a72             # detached HEAD — W0~W4, W5·W6·W7 미포함
./gradlew :app:installDebug
```

설치 후 **접근성 재활성화**(§4 공통 블록 A) → 로그를 `logs/S0_baseline.txt` 로 캡처 →
**S1 과 동일한 조작**(유튜브 재생 중 버블 탭 → 배치 1회 → 스왑 1회)을 수행.

```bash
git checkout main                # 반드시 원복. detached 상태로 다음 세션 진입 금지
```

**남길 것:** `logs/S0_baseline.txt`. S1·S5 의 대조군이다.

---

### 공통 블록 A — 설치 직후 항상 실행

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug

# ⚠ 앱 업데이트는 접근성 서비스를 끈다 (CLAUDE.md 함정 #6).
#   프로브 서비스도 함께 유지해야 W1-2 를 확인할 수 있으므로 콜론으로 이어 쓴다.
adb shell settings put secure enabled_accessibility_services \
  "dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService:dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService"
adb shell settings put secure accessibility_enabled 1

# 확인
adb shell settings get secure enabled_accessibility_services
```

**오버레이 권한**(버블)은 설치로 사라지지 않지만, 최초 1회는 온보딩에서 부여해야 한다.

---

### S0 — 설치 · 권한 · W1 스모크

공통 블록 A 실행 후:

| # | 조작 | 판정 |
|---|---|---|
| **W1-1** | `adb shell am broadcast -a dev.dj.foldwindow.ARRANGE -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver --es placement top` | **배치가 트리거된다.** 목적은 배치 성공이 아니라 **debug 매니페스트로 옮긴 선언이 병합되어 살아 있음**의 확인이다. `Broadcast completed: result=0` + `FWArrangeTrigger` 로그가 나오면 통과 |
| **W1-2** | 설정 → 접근성 목록 확인 + 런처 아이콘 확인 | 프로브 서비스가 **목록에 보이고**, `ProbeActivity` 런처 아이콘이 **존재**한다 (F2 2단계 측정에 계속 필요) |

W1-1 이 실패하면 **릴리스 분리(S1 항목)가 debug 까지 지운 것**이므로 즉시 중단하고 매니페스트 병합 결과를 본다.

---

### S1 — DRAG 주경로 (유튜브, 리사이저블) — 8항목

로그를 `logs/S1_drag.txt` 로 캡처. **조작은 아래 순서 그대로.**

**① 배치 1회** — 유튜브 16:9 영상 재생 중 버블 탭 (또는 W1-1 브로드캐스트)

| # | 판정 기준 |
|---|---|
| **W3-1** | 가로에서 배치 **성공**. `startArrange: 화면 기하 불일치` 로그가 **없어야** 한다 (가드가 가로 경로에 회귀를 주지 않았음) |
| **W5-1** | 검은 띠 0px 상단 배치. **잔여 px·보정 횟수가 S-B 베이스라인과 같은 수준** |
| **W5-3** | `transition: A -> B (event=…)` 줄 **순서·내용이 S-B 와 동일**. `diff <(grep transition: logs/S0_baseline.txt) <(grep transition: logs/S1_drag.txt)` 로 기계 대조. 포맷 문자열은 정적으로 문자 단위 무변경 확인됨(변수명 `event`→`e` 치환뿐) → **줄 내용까지 같아야 한다** |
| **W6-1** | `arrange decision:` / `verify:` / `arrange done:` 3줄이 S-B 와 **문자 단위 동일 포맷** |
| **W7-1** | `FWSplitEntry: step2 card-icon matched via selector [...]` 의 **셀렉터 이름이 S-B 와 동일**(한국어 Fold 7 = `ko-content-desc` 기대). ⚠ `structural-clickable-label` 로 바뀌면 **P1 회귀 확정** — 대형 카드 오매치로 Recents 세션이 파괴된다(2026-07-25 3차 실측 재발). 즉시 중단 |
| **W7-2** | 세션 전체에서 `FWSplitEntry: … 노드 예산 4000 소진` 과 `FWNodeActions: walk: 깊이 상한 50 초과` 가 **단 한 줄도 없어야** 한다. 한 줄이라도 나오면 구코드에 없던 절단이 주 경로에서 발동한 것 → 상한을 올리고 **실측 트리 규모를 `DEVICE_FACTS.md` 에 기록** |

**② 스왑 1회** — 분할 성립 후 페인 스왑

| # | 판정 기준 |
|---|---|
| **W7-4** | `FWPaneSwapper: swap:` 이 나오고 **스왑 성립**. `TAP_DURATION_MS`(50L) 소유권이 `NodeActions` 로 옮겨간 뒤에도 런타임이 맞는지의 **유일한 실동작 경로** |

**③ 취소 + 재배치** — 배치 진행 중 취소

| # | 판정 기준 |
|---|---|
| **W6-3** | `배치 실패: 사용자 취소` 토스트 + **버블 복원**. 이어서 **재배치 1회가 정상 성공**해야 한다 — `session = null` 이 세션 상태를 완전히 비웠는지의 **직접 증거** |

**④ 분할 해제** — 버블 롱프레스 메뉴 → 「분할 해제」

| # | 판정 기준 |
|---|---|
| **W6-4** | 정상 해제 (`dismissSplit` 이 `machineState == Idle` 가드 통과) |
| **W1-3** | 회귀 없음이 **기대값**. 1차 경로는 `PanelActivity.instance.finish()` 라 **토큰을 쓰지 않는다** |

---

### S2 — MENU 경로 (넷플릭스, UNRESIZEABLE) — **S1 직후, 재설치·재부팅 없이** — 5항목

> **⚠ 리사이저블 앱으로 수행하면 이 세션은 무효다.** W7 에서 가장 크게 재작성된
> `DividerPopupRotator`(−115줄)가 **호출조차 되지 않는다.**
> **⚠ S1 과 같은 프로세스 수명 안에서** 해야 W6-5 가 성립한다 (A=유튜브 → B=넷플릭스).

로그를 `logs/S2_menu.txt` 로 캡처(버퍼는 비우지 말 것 — S1 꼬리가 W6-5 의 A 쪽 근거다.
또는 S1 로그를 그대로 이어 받는다).

| # | 판정 기준 |
|---|---|
| **W6-2** | `resize-mode detection: … recipe=MENU` + **진입 5단계 완주**. 배치 성공 (= `Session.entryRecipe` / `config.entryStepCount` 배선 실증) |
| **W5-2** | MENU 레시피 배치 성공 (W6-2 와 같은 관측으로 동시 충족) |
| **W7-3** | ① `menuStep2/3 split-menu matched via selector [ko-split-menu]` ② `FWDividerRotator: clickWhenFound: [rotateOnce rotate-node] clicked-self (text=…/desc=…)` — **`clicked` 가 아니라 `clicked-self`/`clicked-ancestor` + 괄호 라벨**이어야 M3 통합본이 실행된 것 ③ 회전 후 상하 분할 성립 |
| **W7-2** | (S1 과 동일) 상한 로그 0줄 |
| **W6-5** | **M1 의 존재 이유 직격.** B(넷플릭스)의 `arrange decision:` 에서 `label=` `cachedAspect=` `placementSource=` 가 전부 **B 기준**이고, `consensus:` 게이트가 B 에서 **다시 발동**(`aspectConfirmed` 누수 없음), `aspect cache save:` 의 `pkg=` 가 **B** 여야 한다 |

**로그 델타 2건 — 회귀로 오인하지 말 것.** `DividerPopupRotator` 의
`clickWhenFound: [$what] clicked` → `clicked-self`/`clicked-ancestor (text=…/desc=…)` 와
`gesture-tap-fallback` → `gesture-tap-fallback (text=…/desc=…)` 는 **의도된 정보 증가, 동작 변화 0**이다.

---

### S3 — 팝업 / Shizuku — 7+1항목

> **⚠ AIDL 시그니처가 바뀌었고 `versionCode` 2→3 이다.** 재설치 후 첫 팝업에서
> UserService 프로세스가 재생성되지 않으면 `AbstractMethodError` 가 난다.
> **재검증 전까지 팝업 모드 전체가 [미검증]** 이다.

준비: Shizuku 실행·인증 확인. 로그를 `logs/S3_popup.txt` 로 캡처.

| # | 조작 | 판정 |
|---|---|---|
| **W2-1** | 첫 팝업 시도 | `AbstractMethodError` **없이** 바인드. `FWArranger.Shizuku: ShellExecUserService 연결됨` |
| **W2-2** | 유튜브 → 버블 메뉴 「팝업으로 열기」 | 팝업 창 출현 + **실제 bounds 가 `PopupPlanner` 계산값과 일치** (17차 절차 그대로) |
| **W2-4** | (W2-2 가 곧 이 케이스) | `am start --windowingMode 5 -n <component>` 가 `Shell$HomeActivity` 류 **`$` 포함 클래스명**에서 정상. 구 방식의 작은따옴표 인용을 제거했으므로 **argv 전환의 직접 실증** |
| **W2-3** | 넷플릭스 팝업 + DRM 재생 | 동일 + DRM 정상 |
| **A-2** | (W2-3 과 동시) | 팝업 창 안에서 DRM 영상이 **눈으로** 정상 렌더 (17차엔 로그로만 확인) |
| **W2-5** | W2-2·W2-3 로그 확인 | `blocked by policy` 가 **0건**. 실사용 3종(`am start`/`am stack list`/`am task resize`) 전부 통과 |
| **W2-6** | `adb shell am force-stop moe.shizuku.privileged.api` → 팝업 시도(**실패 관찰**) → Shizuku 재실행 → 팝업 재시도 | **재시도가 성공**해야 한다. 구 코드는 `binding=true` 고착으로 영구 불능이었다 |
| **W2-7** | **인위 유도 곤란** | `am` 이 걸렸을 때 `-1 / timeout after 5000ms` + `popupInFlight` 해제. **자연 발생 대기** — 로그만 상시 계측하고 미발동이면 `[미검증]` 유지 |

---

### S4 — 부팅 경로 — 2항목

> 재부팅은 접근성 서비스를 끄지 **않는다**(secure setting 유지). 앱 업데이트만 끈다.

**준비:** 버블을 **기본 위치가 아닌 곳**(좌하단 등)으로 옮기고 **스냅 완료**까지 기다린다.
그 뒤 `adb reboot`. 로그는 `logs/S4_boot.txt`.

| # | 판정 기준 |
|---|---|
| **W7-5** | ① 버블이 **기본 위치(우측 가장자리, 화면 높이 1/3)에 먼저 떴다가 저장 위치로 이동** — 이 한두 프레임 점프가 P2 의 **의도된** 체감 변화다. **점프가 안 보이면 복원이 아예 안 온 것일 수 있으니 ②로 구분** ② 최종 위치가 재부팅 전과 동일 ③ `applyCachedBubblePosition: 버블 위치 반영 실패` **미출현** ④ 부팅 직후 ANR·버벅임 없음 |
| **W7-6** | (best-effort) 재부팅 후 버블이 뜨자마자 **1초 내에 잡고 드래그**. 드래그 중 저장 위치로 튀지 않아야 한다. `FWFloatingLauncher: restoreBubblePositionAsync: 복원 전 사용자가 버블을 이동 — 저장값 적용 생략` 이 나오면 가드 동작. **창이 매우 좁아 미재현이 정상 — 미재현은 무결의 증거가 아니다**(그렇게 기록할 것) |

---

### S5 — 실패 · 희귀 경로 — 4항목

**① 세로 방향 명시적 실패** — 기기를 세로로 두고 버블 탭

| # | 판정 기준 |
|---|---|
| **W3-2** | 토스트 「이 화면 방향/디스플레이는 아직 지원하지 않습니다」 + `logcat -s FWArranger` 에 `startArrange: 화면 기하 불일치` **1건**. 조용히 틀린 위치로 디바이더를 옮기지 **않아야** 한다 |

**② 희귀 경로 — 세 항목이 같은 조건이므로 한 번에 유도한다**

조건 = `PanelActivity.instance == null` ∧ 패널 태스크 존재 ∧ 분할 활성.
`instance` 는 접근성 서비스와 **같은 프로세스의 static** 이라 `force-stop` 은 서비스까지 죽인다.
유도 후보를 순서대로 시도한다:

1. **개발자 옵션 → 「활동 유지 안 함」 ON** (가장 유망) — 배치로 분할 성립 → 홈 → 최근앱으로 복귀.
   `PanelActivity` 만 destroy 되고 태스크 레코드는 남는다 → `instance==null ∧ hasPanelTask()==true`.
2. `adb shell am kill dev.dj.foldwindow` (백그라운드 프로세스만 대상 — 포그라운드 서비스 보유 시 무시될 수 있음)
3. `am force-stop` + 태스크 스와이프 조합 (`PROGRESS.md` B-1 원안)

| # | 판정 기준 |
|---|---|
| **W1-4** | 토큰이 실제로 소비되어 finish 된다 (S4 토큰 폴백 경로) |
| **B-1 (#28)** | `performDismissSplit` 3분기 중 **「패널 태스크 부재」 분기** — `instance==null` ∧ **태스크 없음** ∧ 분할 활성 판정 통과. 위 유도에서 태스크까지 지우면 이 분기 |
| **B-2 (P3-2)** | dismissSplit 인텐트 폴백 (`instance==null`, 프로세스 사망 후 분할 잔존) |

**3후보 전부 실패하면 `[미검증]` 유지가 정답이다.** 무리한 유도로 다른 항목의 판정을 오염시키지 않는다.
**끝나면 「활동 유지 안 함」을 반드시 OFF.**

---

### S6 — 물리 조작 — 1항목

| # | 판정 기준 |
|---|---|
| **A-1** | 실제로 기기를 접어 **화면 꺼짐 실상태**에서 커버 자동 해제 발화. 17차엔 `cmd device_state state 0/reset` **에뮬레이션으로만** 확인했다 |

> **A-3(600ms 재펴기)은 조치 불필요** — 15차 결정으로 미검증 수용 확정.

---

### S7 — 허용치 실험 (**반드시 마지막 · 반드시 원복**) — 1항목

> **⚠ 이건 값 변경이 아니라 일시 실험이다** (CLAUDE.md 함정 #7).
> `residualTolerancePx` 는 `ArrangeStateMachine.kt:353` 에서 **폐루프 보정 발동 게이트**이기도 하다.
> 0 으로 두면 잔여 1px 에도 보정이 1회 더 돈다 → **다른 세션의 판정을 오염시킨다.**

```bash
# 1) config/window_profiles.json 의 defaults.residualTolerancePx 를 8 → 0 으로 변경
#    (config/ 가 assets 소스 디렉터리이므로 재빌드·재설치 필수)
./gradlew :app:installDebug
# → 공통 블록 A 의 접근성 재활성화 실행
```

| # | 판정 기준 |
|---|---|
| **W5-4** | 잔여 1px 이상이면 토스트가 `배치 완료 · 잔여 Npx (허용치 초과)` 로 뜬다. `logcat` 의 `arrange done: … tolerance=0` 으로 판정 근거 대조. **F9 는 JVM 사각지대 — 부등호를 `>`→`>=` 로 바꿔도 29개 전부 통과했다. 육안이 유일한 검증 수단** |

```bash
# 2) 원복 — 생략 금지
git checkout config/window_profiles.json     # 8 로 복귀
git diff --stat                              # 빈 출력이어야 한다
./gradlew :app:installDebug
# → 공통 블록 A 의 접근성 재활성화 실행 (기기를 정상 빌드 상태로 남긴다)
```

---

## 5. 공통 함정 (세션 중 실패 시 1차 원인)

| 증상 | 먼저 볼 것 |
|---|---|
| **아무 반응 없음** | 접근성 서비스 꺼짐. **앱 업데이트하면 항상 꺼진다** (함정 #6). 발생 빈도 1위 |
| 첫 팝업에서 `AbstractMethodError` | `versionCode` 미반영 = 구 UserService 바이너리 재사용. 앱 삭제 후 재설치 |
| 배치는 되는데 띠가 남음 | 인셋/디바이더 두께가 `WindowGeometry` 에 반영 안 됨 |
| **`가끔만` 성공** | ADR-2 위반 — 고정 지연이 어딘가 들어갔다 |
| 스크린샷 실패 반복 | `takeScreenshot()` 초당 1회 레이트 리밋 (함정 #3). 백오프 확인 |
| MENU 항목이 안 나옴 | 리사이저블 앱으로 테스트 중일 가능성. **W7-3 은 UNRESIZEABLE 앱 필수** |
| 세로에서 이상 동작 | W3 가드가 안 걸린 것 → W3-2 실패로 기록 |

---

## 6. 완료 처리

각 세션 종료 시:

1. **`docs/DEVICE_FACTS.md`** 의 해당 항목 `[미검증]` → `[측정]` / `[실패]` 로 갱신하고
   **판정 근거 로그 줄을 인용**한다. 이 문서(RUNBOOK)에는 결과를 쓰지 않는다.
2. **미재현 항목은 「미재현」으로 기록**한다 — W2-7·W7-6·B-1~B-2 는 미재현이 정상이며,
   **미재현은 무결의 증거가 아니다**. 그렇게 명시한다.
3. **`PROGRESS.md`** 의 웨이브 표 「실기기」 열과 「남은 작업」 A·B 절을 Advisor 가 갱신한다.
4. 새 실측 상수가 나오면(W7-2 의 트리 규모 등) **`DEVICE_FACTS.md` 에 측정 근거와 함께** 기록한다 (함정 #7).
5. 회귀를 발견하면 **즉시 중단**하고 해당 웨이브 커밋을 지목해 보고한다.
   특히 **W7-1 의 `structural-clickable-label` 매치**는 진입 경로 전체를 파괴하므로 최우선 중단 사유다.

## 7. 커버리지 확인

| 웨이브 | 항목 수 | 배정 세션 |
|---|---|---|
| W0 · W4 | — | **실기기 불필요** |
| W1 | 4 | S0(1·2) · S1(3) · S5(4) |
| W2 | 7 | S3 |
| W3 | 2 | S1(1) · S5(2) |
| W5 | 4 | S1(1·3) · S2(2) · **S7(4)** |
| W6 | 5 | S1(1·3·4) · S2(2·5) |
| W7 | 6 | S1(1·2·4) · S2(2·3) · S4(5·6) |
| A(물리) | 2 | S3(A-2) · S6(A-1) |
| B(희귀) | 2 | S5 |

**총 32항목 / 미배정 0.**
