# PHASE_RUNBOOK — 실행 순서

## 준비

```bash
git init && git add -A && git commit -m "chore: FoldWindowArranger scaffold"
claude          # 프로젝트 루트에서
```

첫 메시지로 `PROMPTS/PROMPT_00_bootstrap.md` 내용을 붙여넣는다.

## 순서

| 단계 | 프롬프트 | 산출물 | 실기기 필요 |
|---|---|---|---|
| 부트스트랩 | `PROMPT_00` | 빌드/테스트 통과 | ✗ |
| Phase 0 | `PROMPT_01` | `docs/DEVICE_FACTS.md` 확정 | ✅ |
| Phase 1 | `PROMPT_02` | 도메인 확정, 상태 머신 | ✗ |
| Phase 2 | `PROMPT_03` | 실제 창 배치 동작 | ✅ |
| Phase 3 | `PROMPT_04` | 플로팅 UI, 프로파일 | ✅ |
| 루프 | `PROMPT_99` | 잔여 작업 소화 | 상황별 |

## 자주 쓰는 명령

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# 접근성 서비스 켜기 (개발 편의. 앱 업데이트마다 꺼진다)
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 리포트 회수
adb pull /sdcard/Android/data/dev.dj.foldwindow/files/probe_report.md ./docs/

# 창 상태 확인 (디바이더 두께 실측 교차검증)
adb shell dumpsys activity activities | grep -i -E "windowingMode|Task.*bounds"

# 로그
adb logcat -s FWProbe:V
```

## 디버깅 체크리스트

증상별 1차 원인:

| 증상 | 먼저 볼 것 |
|---|---|
| 아무 반응 없음 | 접근성 서비스 꺼짐 (앱 업데이트하면 항상 꺼진다) |
| 디바이더를 못 찾음 | `DEVICE_FACTS` #7. 폴백 휴리스틱 동작 여부 |
| 분할 진입 실패 | `DEVICE_FACTS` #6. Recents 폴백으로 전환 |
| 배치는 되는데 띠가 남음 | 인셋/디바이더 두께가 `WindowGeometry` 에 안 들어감 |
| 앱마다 결과가 다름 | 앱 자체 상단바 높이. ADR-5 폐루프 보정 확인 |
| 가끔만 성공 | ADR-2 위반. 고정 지연이 어딘가 들어갔다 |
