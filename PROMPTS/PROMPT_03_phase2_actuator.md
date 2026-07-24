# PROMPT 03 — Phase 2 액추에이터

> 전제: Phase 1 완료. `ArrangeStateMachine` 이 테스트로 검증됨.
> ⚠ 이 Phase는 실기기 검증이 필수다. 각 작업 뒤에 사용자에게 실기기 확인을 요청하라.

---

## 브리프에 반드시 담을 컨텍스트

- 상태 머신은 이미 `domain/ArrangeStateMachine` 에 있다. **로직을 다시 짜지 마라.**
  이 Phase의 일은 상태 머신에 **이벤트를 공급하고 액션을 수행하는 어댑터**를 만드는 것이다.
- 디바이더 좌표 확보 전략은 `docs/DEVICE_FACTS.md` 의 #7 판정에 따른다.
- 분할 진입 전략은 #6 판정에 따른다.

## P2-1 → android-implementer
`service/ArrangerAccessibilityService` — 상태 머신 구동 루프.
포그라운드 앱 추적은 `TYPE_WINDOW_STATE_CHANGED` 로 하되 SystemUI/자기 자신은 제외한다.

## P2-2 → android-implementer
`platform/DividerLocator`
- 1순위: `windows.first { it.type == TYPE_SPLIT_SCREEN_DIVIDER }`
- 폴백: 화면 세로 중앙 ±40% 밴드 안에서 높이가 화면의 5% 미만이고 가로 폭이 90% 이상인 SYSTEM 타입 창
- 둘 다 실패 시 `DividerNotFound` 이벤트를 상태 머신에 넣는다

## P2-3 → android-implementer
`platform/SplitEntry` — #6 판정에 따라 분기.
`performGlobalAction` 경로와 Recents 폴백 경로를 **둘 다 구현**하고 전략 인터페이스로 감싸라.
DEVICE_FACTS 결과로 기본값을 정하되 런타임 전환이 가능해야 한다.

## P2-4 → android-implementer
`platform/DividerDragger` — `dispatchGesture` 로 목표까지 드래그.
- 시작점은 디바이더 bounds 의 중심
- 한 번에 크게 움직이지 말고 `GestureDescription.StrokeDescription` 의 `continueStroke` 로
  누르고 → 이동 → 떼기를 분리하면 One UI 가 더 안정적으로 따라온다. 실기기에서 비교 측정하라
- 완료 판정은 "제스처 콜백 성공" 이 아니라 **디바이더 bounds 재조회**로 한다

## P2-5 → android-implementer
`ui/PanelActivity` — 파트너 창 (ADR-3).
검정 배경 + 하단에 최소 컨트롤(위/아래 전환, 해제). `resizeableActivity="true"` 필수.

## P2-6 → android-implementer
폐루프 보정 (ADR-5).
배치 완료 후 `takeScreenshot` → `LetterboxDetector` 로 잔여 띠 측정.
`residual > 임계(예: 8px)` 면 1회만 미세 조정. 무한 루프 방지를 위해 재시도는 1회로 제한.
⚠ takeScreenshot 레이트 리밋(약 1초) 준수.

## 실기기 완료 기준
- [ ] 유튜브: 원터치 → 검은 띠 0px 상단 배치
- [ ] 유튜브: 하단 배치
- [ ] 넷플릭스: 상단 배치
- [ ] 티빙: 상단 배치
- [ ] 실패 시 사용자에게 이유가 보임 (조용한 실패 없음)
