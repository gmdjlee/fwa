# PROMPT 01 — Phase 0 실행과 해석

> 전제: PROMPT 00 완료. 빌드 통과 상태.

---

## A. 설치와 실행 안내 (Advisor가 직접)

사용자에게 아래 절차를 한국어로 안내하라:

```bash
./gradlew :app:installDebug
```

1. 설정 → 접근성 → 설치된 앱 → **FoldWindow Probe** 켜기
2. Fold 7 을 펼치고 **가로 모드**로 유튜브에서 영상을 **전체화면 재생**
   (프로브 E가 검은 띠를 실측하려면 반드시 필요)
3. FoldWindow 앱으로 전환 → **진단 실행**
4. **리포트 공유** → 파일을 저장하거나

```bash
adb pull /sdcard/Android/data/dev.dj.foldwindow/files/probe_report.md ./docs/
```

## B. 해석 (Worker 위임)

리포트가 `docs/probe_report.md` 에 들어오면 `probe-analyst` 에게 위임하라.
브리프에 반드시 포함할 것:
- 이 프로젝트가 해소하려는 미지수는 #5(freeform), #6(GLOBAL_ACTION), #7(divider 노출) 세 가지다
- Day 0 에서 #1·#2·#3 은 이미 ✅ 로 확정됐다 (`TASK.md` 표 참조)
- 완료 기준: `docs/DEVICE_FACTS.md` 생성, `TASK.md` 미지수 표 갱신, Phase 2 전략 분기 판정

## C. 만약 프로브가 실패하면

| 증상 | 대응 |
|---|---|
| `instance == null` | 접근성 서비스가 꺼짐. 앱 재설치 후엔 항상 꺼진다 |
| 프로브 E 캡처 실패 | `canTakeScreenshot` 확인. 레이트 리밋이면 1초 뒤 재시도 |
| 프로브 E "띠를 찾지 못함" | 영상이 전체화면이 아니거나 이미 꽉 찬 상태. 16:9 영상으로 재시도 |
| 프로브 C 가 화면을 분할해버림 | 정상이다. 되돌리고 다시 실행하면 된다 |

## D. 보고
한국어로 미지수 3개의 판정과 그로 인한 Phase 2 설계 분기를 보고하라.
