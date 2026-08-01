# TASK.md — FoldWindowArranger

> Phase 0~4 는 전부 완료됐다(구현 + 실기기 검증). 이 파일은 앞으로도 참조되는 확정 사실과
> 범위 밖 결정만 남긴다. 진행 중 작업·재개 지점은 `PROGRESS.md`, 실측값은 `docs/DEVICE_FACTS.md`,
> 설계 결정은 `docs/ADR.md`/`docs/DESIGN_*.md` 가 SSOT다.

---

## 확정된 사실 (Day 0 + Phase 0 실기기 검증)

| # | 항목 | 결과 |
|---|---|---|
| 1 | 디바이더를 계산 위치에 맞추면 검은 띠가 제거되는가 | ✅ 가능 |
| 2 | One UI 디바이더가 임의 비율을 허용하는가 | ✅ 임의 비율 자유 조절(프리셋 스냅 없음) |
| 3 | 넷플릭스·티빙 등이 분할 화면을 허용하는가 | ✅ 허용 |
| 4 | 삼성 기본 「앱별 화면 비율」로 대체 가능한가 | ❌ 불가 — 앱별 개별 설정 + 상시 고정 |
| 5 | 팝업 화면이 AOSP freeform 기반인가 | ✅ freeform 지원(`FEATURE_FREEFORM_WINDOW_MANAGEMENT=true`) |
| 6 | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 이 One UI 8에서 동작하는가 | ❌ 미지원(FAILS) → Recents 카드 드래그 레시피로 대체 |
| 7 | `TYPE_SPLIT_SCREEN_DIVIDER` 창이 접근성에 노출되는가 | ✅ 노출(단, 분할 활성 중에만·핸들 영역만) |

상세 측정값·근거 = `docs/DEVICE_FACTS.md`.

---

## Phase 완료 요약

| Phase | 내용 | 상태 |
|---|---|---|
| Day 0 | 수동 검증(위 표 #1~4) | ✅ 완료 |
| Phase 0 | 진단 프로브, Fold 7 실측값 확보(#5~7) | ✅ 완료 |
| Phase 1 | 도메인 확정 — SplitPlanner/LetterboxDetector/AspectResolver/ArrangeStateMachine | ✅ 완료 |
| Phase 2 | 액추에이터 — DRAG/MENU 진입 레시피, DividerDragger, 폐루프 보정 | ✅ 완료 |
| Phase 3 | 플로팅 버블 UI, 온보딩, DataStore 프로파일, FoldingFeature 자동 배치 | ✅ 완료 |
| Phase 4 | 팝업(Shizuku freeform)·파트너 위젯·커버 자동 해제·앱 페어 바로가기 | ✅ 완료 |
| 개선 웨이브 W0~W7 | 보안 차단·Shizuku 하드닝·기하 정합성·구동부 안정화·성능 정리(23항목) | ✅ 완료 |
| #30 | 전체화면 재생 자동 트리거 | ✅ 완료 |

설계 결정 6건(ADR-1~6) 요약은 `docs/ADR.md`. **v1 에 수행이 필요한 잔여 작업은 없다**(`PROGRESS.md` 상태 헤더). 자연 발생 대기 중인 희귀 경로는 `docs/DEVICE_VERIFICATION_RUNBOOK.md` §5.

---

## 범위 밖 (명시적으로 하지 않는 것)

- MediaProjection 화면 캡처 기반 재렌더링 (DRM 불가, 지연/발열)
- root / LSPosed 후킹 (Knox 영구 트립)
- Google Play 배포 (접근성 서비스 심사 부담. 사이드로딩 전제 — ADR-6)
- **스크린리더(TalkBack) 지원** — ① 이 앱의 가치가 시각적이다("검은 띠를 한쪽으로 몰기"는 화면을 보는 것이 전제. 저시력 사용자의 주 도구인 대비·확대·터치 타깃은 이미 확보) ② ADR-6 으로 배포 강제가 없다 ③ **TalkBack 활성 시 앱이 분할을 생성하지 못한다**(Recents 카드 드래그 3/3 실패, 플랫폼 제약 추정) — 메뉴 접근성을 완성해도 그 메뉴가 하는 일이 완주하지 못한다. 접근성 관련 코드(라벨·`isCheckable`·`clearAndSetSemantics` 등)는 저시력/확대 사용자 이득이므로 **유지**한다. 근거 = `docs/DEVICE_FACTS.md` 24차 「F 항목(TalkBack) 종결」 절
- 가로/세로 자동 회전 대응 (v1은 가로 시청 시나리오만)
