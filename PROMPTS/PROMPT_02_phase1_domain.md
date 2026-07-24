# PROMPT 02 — Phase 1 도메인 확정

> 전제: `docs/DEVICE_FACTS.md` 존재. 미지수 #5·#6·#7 확정됨.

---

너는 Advisor다. 아래 4개를 **병렬로** Worker에게 위임하라 (한 메시지에 다중 Agent 호출).

## P1-1 → android-implementer
`docs/DEVICE_FACTS.md` 의 실측값을 `WindowGeometry.foldSevenLandscapePlaceholder()` 에 반영하고
이름을 `foldSevenLandscape()` 로 바꿔라. `dividerThickness`, `minPaneHeight`, `usableTop` 을 실측값으로 채운다.
완료 기준: 16:9 입력 시 `residualLetterboxPx == 0` 이고 `exact == true` 인 테스트가 새로 통과한다.

## P1-2 → android-implementer
`config/window_profiles.json` 을 읽는 로더와 스키마 검증기를 `domain/` 에 만들어라.
JSON 파싱은 kotlinx.serialization 대신 **인터페이스로 추상화**하고 도메인은 파서를 모르게 하라.
완료 기준: 잘못된 스키마(누락 필드, 범위 밖 aspect, 중복 package)를 각각 구분되는 예외로 거부하는 테스트.

## P1-3 → android-implementer
`domain/AspectResolver` — 3단 폴백 (ADR-1).
```
resolve(pkg, measurement, userPreset) =
    프로파일에 있으면 프로파일
    없고 measurement.confidence >= 임계면 measurement.value
    아니면 userPreset
    그것도 없으면 16:9
```
각 결정에 `AspectSource` 를 함께 반환해 UI가 근거를 표시할 수 있게 하라.
완료 기준: 4가지 경로가 각각 테스트로 커버됨.

## P1-4 → android-implementer
`domain/ArrangeStateMachine` — **순수 도메인**. Android API를 부르지 않는다.
상태: `Idle → EnteringSplit → WaitingForDivider → LaunchingPanel → MovingDivider → Verifying → Done`
실패: `Failed(reason)`. reason 은 sealed class 로 구체화.
입력은 이벤트(`DividerAppeared`, `Timeout`, `PanelLaunched`, `MeasuredResidual(px)` …).
**ADR-2 준수: 시간은 외부에서 주입되는 이벤트다. 상태 머신 안에서 delay 하지 않는다.**
완료 기준: 모든 전이 + 각 단계 타임아웃 + 재시도 소진 경로가 테스트로 커버됨.

## 검증
4개가 끝나면 `qa-verifier` 에게 통합 검증을 위임하라.
