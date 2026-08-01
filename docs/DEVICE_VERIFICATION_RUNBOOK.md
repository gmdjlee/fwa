# DEVICE_VERIFICATION_RUNBOOK — 실기기 검증 절차

> 원래 W0~W7 개선 웨이브 31항목 검증용으로 작성됐다(2026-07-31). 그 캠페인은 **20차에서 28/31
> 완료**(3건은 계획된 자연 대기)로 종결됐다 — 결과는 `docs/DEVICE_FACTS.md` 「개선 웨이브 W1~W7」·
> 「20차」 절 참조, 항목별 체크리스트는 여기서 제거했다(git 이력에서 복원 가능).
> 이 문서는 이제 **재사용 가능한 절차**(설치·로그 캡처·공통 함정)와 **아직 열려 있는 항목**의
> 유도 방법만 남긴다. 새 실기기 캠페인(v1.5 변경 검증 등)에도 그대로 쓸 수 있다.

---

## 1. 왜 실기기가 필요한가 (JVM 테스트 사각지대)

`ArrangerAccessibilityService`/`SplitEntry`/`DividerPopupRotator`/`PaneSwapper`/
`FloatingLauncherService`/`NodeActions`/`Polling` 을 인스턴스화하는 JVM 테스트는 **0개**다.
qa 검증자가 3개 웨이브에서 변조 실험을 반복했고 결과가 동일했다 — W5(`>`→`>=`, 29개 전부 통과) ·
W6(7종 동시 변조, 312개 전부 통과) · W7(9종 중 5종, 322개 전부 통과). **서비스 레이어 로직은
실기기 육안·logcat 대조가 유일한 실효 검증 수단**이다. 새 변경이 이 파일들을 건드리면 아래
절차로 검증할 것.

---

## 2. 사전 조건

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest    # 정적 DoD 재확인
./gradlew :app:assembleDebug
./gradlew :app:lintDebug            # 신규 경고 0 (baseline 기준)
```

`adb devices` 가 `unauthorized` 면 기기 화면의 "USB 디버깅 허용" 다이얼로그에서 "항상 허용" 필요.

준비물: 유튜브(DRAG 레시피 대상, 리사이저블) · 넷플릭스 등 UNRESIZEABLE 앱(MENU 레시피 +
`DividerPopupRotator` — **리사이저블 앱으로는 이 경로가 한 줄도 안 돈다**) · Shizuku v13.6.0+ ·
재부팅 가능 상태 · 로그 저장 디렉터리(`mkdir -p logs/`, git 미추적 권장).

---

## 3. 설치 직후 항상 실행하는 공통 블록

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:installDebug

# ⚠ 앱 업데이트는 접근성 서비스를 끈다 (CLAUDE.md 함정 #6).
adb shell settings put secure enabled_accessibility_services \
  "dev.dj.foldwindow/dev.dj.foldwindow.service.ArrangerAccessibilityService:dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService"
adb shell settings put secure accessibility_enabled 1
adb shell settings get secure enabled_accessibility_services   # 확인
```

오버레이 권한(버블)은 설치로 사라지지 않지만 최초 1회는 온보딩에서 부여해야 한다.

## 4. 로그 캡처

```bash
adb logcat -c
adb logcat -v threadtime \
  -s FWArranger:V FWArranger.Shizuku:V FWArrangeTrigger:V FWBootReceiver:V \
     FWDividerDragger:V FWDividerRotator:V FWFloatingLauncher:V FWFoldStateMonitor:V \
     FWGestureDrags:V FWHingeAngleMonitor:V FWNodeActions:V FWPairShortcut:V \
     FWPaneSwapper:V FWPanelActivity:V FWProbe:V FWProfileStore:V \
     FWResizeModeDetector:V FWSplitEntry:V \
  | tee logs/<세션이름>.txt
```

베이스라인이 필요한 검증(로그 전이 순서·포맷 대조)은 회귀 의심 커밋 이전으로 `git checkout <hash>`
→ `installDebug` → 접근성 재활성화 → 동일 조작을 먼저 돌려 `logs/S0_baseline.txt` 로 남긴다.
**반드시 `git checkout main` 으로 원복 후 다음 세션 진행** — detached 상태로 넘어가지 않는다.

---

## 5. 아직 열려 있는 항목 — 유도 방법

`PROGRESS.md` 「남은 작업」 B 절과 동일 목록이다. 아래는 그 유도 절차.

### 희귀 경로 3종 — 같은 세션에서 함께 유도 (W1-4 · B-1 · B-2)

조건 = `PanelActivity.instance == null` ∧ 패널 태스크 존재 ∧ 분할 활성. `instance` 는 접근성
서비스와 같은 프로세스의 static 필드라 `force-stop` 은 서비스까지 죽여 조건을 파괴한다.

1. **개발자 옵션 → 「활동 유지 안 함」 ON**(가장 유망) — 배치로 분할 성립 → 홈 → 최근앱으로 복귀.
   `PanelActivity` 만 destroy 되고 태스크 레코드는 남는다.
2. `adb shell am kill dev.dj.foldwindow` — 백그라운드 프로세스만 대상. **20차 실측: FGS 보유 시
   거부됨(pid 생존) — 이 후보는 사실상 소진 확정.**
3. `am force-stop` + 태스크 스와이프 조합.

**20차에서 3후보 전부 유도 실패로 종결**(①은 분할 중 패널이 가시 상태라 destroy 안 됨 — 조건
논리적 유도 불가, ②는 위 실측대로 거부, ③은 접근성까지 죽여 조건 파괴). 재도전 시 새 후보가
필요하다. 전부 실패하면 `[미검증]` 유지가 정답 — 무리한 유도로 다른 항목의 판정을 오염시키지 않는다.
끝나면 「활동 유지 안 함」을 반드시 OFF.

### Shizuku 타임아웃 실효 (W2-7)

인위 유도 곤란 — `logcat -s FWArranger.Shizuku` 상시 계측하며 자연 발생 대기. `am` 명령이 걸렸을
때 `-1 / timeout after 5000ms` + `popupInFlight` 해제를 확인하면 판정.

---

## 6. 공통 함정

| 증상 | 먼저 볼 것 |
|---|---|
| 아무 반응 없음 | 접근성 서비스 꺼짐. **앱 업데이트하면 항상 꺼진다**(함정 #6). 발생 빈도 1위 |
| 첫 팝업에서 `AbstractMethodError` | `versionCode` 미반영 = 구 UserService 바이너리 재사용. 앱 삭제 후 재설치 |
| 배치는 되는데 띠가 남음 | 인셋/디바이더 두께가 `WindowGeometry` 에 반영 안 됨 |
| **`가끔만` 성공** | ADR-2 위반 — 고정 지연이 어딘가 들어갔다 |
| 스크린샷 실패 반복 | `takeScreenshot()` 초당 1회 레이트 리밋(함정 #3). 백오프 확인 |
| MENU 항목이 안 나옴 | 리사이저블 앱으로 테스트 중일 가능성 — MENU 경로는 UNRESIZEABLE 앱 필수 |
| 세로에서 이상 동작 | W3 가드(화면 기하 불일치)가 안 걸린 것 |
| `am force-stop <shizuku pkg>` 로 서버가 안 죽음 | 서버는 shell uid 부모라 범위 밖 — `kill <pid>` 직접 |

기타 실측 함정(adb 좌표 변환, 롱프레스 임계, screencap 멀티 디스플레이 등)은 `PROGRESS.md`
「개발 환경 함정」 절.

---

## 7. 완료 처리

1. **`docs/DEVICE_FACTS.md`** 에 해당 캠페인 절을 새로 추가하고(날짜·기기·결과) 판정 근거 로그를
   인용한다. append-only — 과거 판정을 고쳐 쓰지 않는다(함정 #7).
2. 미재현 항목은 「미재현」으로 기록한다 — **미재현은 무결의 증거가 아니다.**
3. **`PROGRESS.md`** 의 「남은 작업」 절을 Advisor 가 갱신한다.
4. 새 실측 상수가 나오면 측정 방법·날짜와 함께 `DEVICE_FACTS.md` 에 기록한다(함정 #7).
5. 회귀를 발견하면 즉시 중단하고 원인 커밋을 지목해 보고한다.
