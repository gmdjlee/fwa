# PROMPT 00 — 부트스트랩

> Claude Code 세션을 처음 열었을 때 **이 프롬프트를 그대로 붙여넣는다.**

---

너는 이 프로젝트의 **Advisor**다. `CLAUDE.md` 의 역할 분담 규칙을 따른다.
구현 노동은 직접 하지 말고 `.claude/agents/` 의 Worker에게 위임한다.

## 1단계 — 컨텍스트 흡수
`CLAUDE.md`, `TASK.md`, `PROGRESS.md`, `docs/ADR.md` 를 읽어라.
읽은 뒤 **현재 상태를 3문장으로 요약**하고, 다음에 할 일을 제시하라.

## 2단계 — 빌드 환경 정합성 (Worker 위임)
`android-implementer` 에게 아래를 위임하라:

> `gradle/libs.versions.toml` 의 버전들이 2026-07 기준 잠정값이다.
> 실제 설치된 AGP/Kotlin/JDK 로 빌드가 통과하도록 갱신하라.
> Gradle Wrapper 가 없으면 생성하라.
> 완료 기준: `./gradlew :app:assembleDebug` 와 `./gradlew :app:testDebugUnitTest` 가 모두 통과한다.
> 도메인 코드의 로직은 건드리지 마라. 빌드 설정만 손댄다.

## 3단계 — 도메인 테스트 확인
`./gradlew :app:testDebugUnitTest` 결과를 보고하라.
`SplitPlannerTest`, `LetterboxDetectorTest` 가 모두 통과해야 한다.
실패하면 **테스트가 아니라 구현을 의심**하라. 테스트의 기대값은 손계산으로 검증된 값이다.

## 4단계 — 사용자 보고
한국어로 보고하라:
- 빌드/테스트 상태
- Phase 0 실행 준비 완료 여부
- 사용자가 실기기에서 해야 할 다음 행동
