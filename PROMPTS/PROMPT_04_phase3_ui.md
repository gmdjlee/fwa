# PROMPT 04 — Phase 3 플로팅 UI와 프로파일

> 전제: Phase 2 완료. 실기기에서 배치가 재현 가능함.

---

## P3-1 → android-implementer
`service/FloatingLauncherService` — `TYPE_APPLICATION_OVERLAY` 버블.
- Foreground Service. Android 14+ 이므로 `foregroundServiceType="specialUse"` + 사유 명시
- 드래그 이동, 화면 가장자리 스냅, 위치 영속화
- 탭 = 기본 동작(마지막 placement), 롱프레스 = 메뉴 확장
- ⚠ 버블 자신이 포그라운드 앱으로 잡히면 안 된다. 오버레이는 앱 전환을 일으키지 않는다

## P3-2 → android-implementer `[병렬]`
버블 확장 메뉴: 위 / 아래 / 해제 / 비율 프리셋(16:9, 2:1, 21:9, 자동감지).
"자동감지" 는 `AspectResolver` 의 measurement 경로를 강제한다.

## P3-3 → android-implementer `[병렬]`
DataStore 앱별 프로파일 저장/복원. `config/window_profiles.json` 은 초기 시드로만 쓴다.
사용자가 특정 앱에서 조정하면 그 값이 프로파일로 승격된다.

## P3-4 → android-implementer
온보딩: 접근성 권한 → 오버레이 권한 → 사용법 3장.
권한이 없는 상태에서 버튼을 눌러도 **크래시 없이** 해당 설정 화면으로 유도한다.

## P3-5 → android-implementer `[병렬]`
`androidx.window` `FoldingFeature` 연동.
`HALF_OPENED` + `HORIZONTAL` 이면 자동으로 상단 배치를 제안(자동 실행 아님, 버블 배지로 힌트).

## 완료 기준
- [ ] 재부팅 후 버블 자동 복귀
- [ ] 앱별 프로파일 유지
- [ ] 권한 미부여 상태에서 크래시 없음
- [ ] `qa-verifier` PASS
