# FoldWindowArranger

갤럭시 Z Fold 7에서 **플로팅 아이콘 원터치**로 현재 앱을 영상 종횡비에 맞는 분할 창으로
재배치하고, 남는 공간을 **위 또는 아래 한쪽으로 몰아주는** 안드로이드 앱.

```
        기본                    이동(상단)              이동(하단)
  ┌──────────────┐         ┌──────────────┐        ┌──────────────┐
  │▓▓▓▓▓▓▓▓▓▓▓▓▓▓│ 370px   │              │        │▒▒▒▒▒▒▒▒▒▒▒▒▒▒│ 740px
  ├──────────────┤         │    영상      │ 1229px ├──────────────┤
  │              │         │              │        │              │
  │    영상      │ 1228px  │              │        │    영상      │ 1229px
  │              │         ├──────────────┤        │              │
  ├──────────────┤         │▒▒▒▒▒▒▒▒▒▒▒▒▒▒│ 740px  │              │
  │▓▓▓▓▓▓▓▓▓▓▓▓▓▓│ 370px   └──────────────┘        └──────────────┘
  └──────────────┘
   ▓ 앱이 만든 검은 띠        ▒ 파트너 창 (콘텐츠 슬롯)
```

**핵심:** 영상 창의 높이를 `화면폭 / 영상비율` 로 잡으면 그 창 안의 letterbox가 0이 되고
남는 공간 전체가 반대쪽으로 몰린다. 2184px 폭에서 16:9는 1229px → 화면의 62.4%.

## 왜 다른 방법이 아닌가

| 방법 | 기각 사유 |
|---|---|
| MediaProjection 캡처 후 재렌더링 | DRM 콘텐츠 검은 화면, 지연 50~150ms, 발열 |
| 삼성 「앱별 화면 비율」 | 앱마다 개별 설정 + 상시 고정. 시청할 때만 쓸 수 없음 |
| Android per-app 레터박스 위치 override | Android 16(API 36) 타겟 앱에는 동작하지 않음 |
| root / LSPosed 후킹 | Knox 영구 트립 |

이 앱은 **창 기하학만 바꾼다.** 픽셀을 건드리지 않으므로 DRM과 무관하다.

## 시작하기

프로젝트 루트에서 `claude` 실행. 세션 간 상태는 `PROGRESS.md` 가 단일 출처다.
빌드·검증 명령과 알려진 함정은 `CLAUDE.md`, 실기기 검증 절차는 `docs/DEVICE_VERIFICATION_RUNBOOK.md`.

## 구조

```
CLAUDE.md              프로젝트 규칙 (Claude Code 자동 로드)
TASK.md                Phase별 작업과 완료 기준
PROGRESS.md            현재 상태 — 세션 간 단일 출처
docs/ADR.md            설계 결정 6건
docs/DEVICE_FACTS.md   실기기 측정값
docs/DEVICE_VERIFICATION_RUNBOOK.md  실기기 검증 절차
.claude/agents/        Worker 정의 4종
config/                앱별 프로파일 SSOT
app/src/main/.../domain/    순수 Kotlin. android import 금지
app/src/main/.../platform/  Android SDK 경계
app/src/main/.../probe/     Phase 0 진단 (debug 빌드 전용으로 격리)
app/src/test/               JVM 단위 테스트 322개
```

## 라이선스 / 배포

사이드로딩 전제 (ADR-6). Play 배포 계획 없음.
