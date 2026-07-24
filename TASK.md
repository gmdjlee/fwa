# TASK.md — FoldWindowArranger

> Phase는 순차 의존이다. 이전 Phase의 완료 기준을 통과하지 않고 다음으로 넘어가지 않는다.
> 각 Phase 안의 작업 중 `[병렬]` 표시된 것들은 한 메시지에서 다중 Agent로 동시 위임한다.

---

## 확정된 사실 (Day 0 실기기 검증 완료)

| # | 항목 | 결과 |
|---|---|---|
| 1 | 디바이더를 계산 위치에 맞추면 유튜브 검은 띠가 제거되는가 | ✅ **가능** |
| 2 | One UI 디바이더가 임의 비율을 허용하는가 | ✅ **임의 비율 자유 조절 가능** (프리셋 스냅 없음) |
| 3 | 넷플릭스·티빙 등이 분할 화면을 허용하는가 | ✅ **허용** |
| 4 | 삼성 기본 「앱별 화면 비율」로 대체 가능한가 | ❌ **불가**. 앱별 개별 설정이 필요하고 상시 고정이라 시청 시에만 쓸 수 없음 |
| 5 | 팝업 화면이 AOSP freeform 기반인가 | ⏳ Phase 0에서 자동 확인 |
| 6 | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 이 One UI 8에서 동작하는가 | ⏳ Phase 0에서 자동 확인 |
| 7 | `TYPE_SPLIT_SCREEN_DIVIDER` 창이 접근성에 노출되는가 | ⏳ Phase 0에서 자동 확인 |

**2번이 통과했으므로 Tier 1(접근성 + 오버레이) 경로가 확정되었다. Shizuku는 Phase 4 선택 사항.**

---

## 설계 결정 (ADR 요약 — 상세는 `docs/ADR.md`)

- **ADR-1** 종횡비는 3단 폴백: ① 앱별 저장 프로파일 → ② 스크린샷 검은 띠 실측 역산 → ③ 사용자 프리셋
- **ADR-2** 고정 지연 금지. 상태 머신 + 조건 폴링
- **ADR-3** 파트너 창을 낭비하지 않음. 검정 고정이 아니라 시계/메모/임의 앱 지정 가능
- **ADR-4** `domain/` 은 순수 Kotlin. 실기기 없이 검증 가능한 표면적을 최대화
- **ADR-5** 폐루프 보정: 배치 후 재측정 → 잔여 띠가 임계 이상이면 1회 미세 조정

---

## Phase 0 — 진단 프로브 (선행 필수)

**목적:** 미확인 항목 5·6·7을 코드로 자동 확인하고, Fold 7의 실제 수치를 확보한다.

### 작업
- `P0-1` `domain/` 모델과 `SplitPlanner` 완성 + 단위 테스트 `[병렬]`
- `P0-2` `domain/LetterboxDetector` 완성 + 단위 테스트 `[병렬]`
- `P0-3` `probe/ProbeAccessibilityService` — 5개 프로브 실행 `[병렬]`
- `P0-4` `probe/ProbeActivity` — Compose UI, 실행/공유 버튼
- `P0-5` Gradle/Manifest/접근성 설정 XML 정합성 확인 및 빌드 통과

### 프로브가 수집해야 할 항목
| 프로브 | 내용 | 해소하는 미지수 |
|---|---|---|
| A. Device | `FEATURE_FREEFORM_WINDOW_MANAGEMENT`, `FEATURE_PICTURE_IN_PICTURE`, `enable_freeform_support`, `enable_non_resizable_multi_window` | #5 |
| B. Windows | 전체 `windows` 덤프 (type, bounds, layer, package) | #7 |
| C. SplitAction | `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 호출 후 디바이더 창 출현을 폴링 | #6 |
| D. Metrics | `WindowMetrics` current/maximum, insets, density, 폴딩 상태 | 수치 확보 |
| E. Letterbox | `takeScreenshot()` → 검은 띠 실측 → 역산 종횡비 | ADR-1 ② 실증 |

### 완료 기준
- [ ] `./gradlew :app:testDebugUnitTest` 통과, `SplitPlanner`/`LetterboxDetector` 테스트 각 8케이스 이상
- [ ] 실기기에서 프로브 실행 → `probe_report.md` 생성
- [ ] 리포트를 `docs/DEVICE_FACTS.md` 로 병합
- [ ] 항목 5·6·7에 ✅/❌ 확정 기입

---

## Phase 1 — 도메인 확정

**목적:** Phase 0 실측값을 반영해 계산 로직을 고정한다.

### 작업
- `P1-1` `DEVICE_FACTS.md` 의 divider 두께·최소 창 높이·시스템 바 인셋을 `SplitPlanner` 기본값에 반영
- `P1-2` `config/window_profiles.json` 스키마 확정 + 로더/검증기
- `P1-3` `AspectResolver` — 3단 폴백 조합 로직 (프로파일 → 실측 → 프리셋)
- `P1-4` `ArrangeStateMachine` — 상태/전이/타임아웃 정의. **순수 도메인으로 작성**

### 완료 기준
- [ ] `SplitPlanner.plan()` 이 Fold 7 실측값으로 16:9 → 잔여 띠 0px 를 반환
- [ ] `ArrangeStateMachine` 이 모든 실패 경로(타임아웃, 디바이더 미발견, 분할 진입 실패)에 대해 명시적 상태를 갖고, 테스트로 커버됨
- [ ] JSON 로더가 잘못된 스키마를 명확한 에러로 거부

---

## Phase 2 — 액추에이터 (실제로 창을 움직이는 부분)

**목적:** 상태 머신을 실제 접근성 API에 연결한다.

### 작업
- `P2-1` `service/ArrangerAccessibilityService` — 상태 머신 구동, 포그라운드 앱 추적
- `P2-2` `platform/DividerLocator` — `TYPE_SPLIT_SCREEN_DIVIDER` 조회 + 휴리스틱 폴백
- `P2-3` `platform/SplitEntry` — 분할 진입 전략 (Phase 0 #6 결과에 따라 분기)
  - #6 ✅ → `performGlobalAction`
  - #6 ❌ → Recents 열기 → 앱 아이콘 탭 → "분할 화면으로 열기" 노드 클릭
- `P2-4` `platform/DividerDragger` — `dispatchGesture` 로 목표 위치까지 드래그
- `P2-5` `ui/PanelActivity` — 파트너 창 (ADR-3)
- `P2-6` 폐루프 보정 (ADR-5)

### 완료 기준
- [ ] 유튜브 재생 중 원터치 → 검은 띠 0px 상단 배치 성공 (실기기)
- [ ] 넷플릭스에서 동일 동작 성공
- [ ] 상단/하단 전환 성공
- [ ] 실패 시 사용자에게 이유가 보이는 상태로 종료 (조용한 실패 금지)

---

## Phase 3 — 플로팅 UI + 프로파일

### 작업
- `P3-1` `service/FloatingLauncherService` — 오버레이 버블, 드래그 이동, 가장자리 스냅
- `P3-2` 버블 확장 메뉴 — 위/아래/해제/비율 프리셋 `[병렬]`
- `P3-3` DataStore 앱별 프로파일 저장/복원 `[병렬]`
- `P3-4` 온보딩 — 접근성/오버레이 권한 유도 플로우
- `P3-5` `FoldingFeature` 연동: 플렉스 모드 감지 시 자동 상단 배치

### 완료 기준
- [ ] 콜드 부팅 후 버블이 자동 복귀
- [ ] 앱별 프로파일이 재실행 후에도 유지
- [ ] 권한 미부여 상태에서 크래시 없이 안내

---

## Phase 4 — 선택 확장

- `P4-1` 팝업(freeform) 모드 — Phase 0 #5 가 ✅ 인 경우에만. Shizuku + `setLaunchBounds`
- `P4-2` 파트너 창 위젯 (시계/자막/메모)
- `P4-3` 커버 화면 전환 시 자동 해제
- `P4-4` 앱 페어 바로가기 내보내기

---

## 범위 밖 (명시적으로 하지 않는 것)

- MediaProjection 화면 캡처 기반 재렌더링 (DRM 불가, 지연/발열)
- root / LSPosed 후킹 (Knox 영구 트립)
- Google Play 배포 (접근성 서비스 심사 부담. 사이드로딩 전제)
- 가로/세로 자동 회전 대응 (v1은 가로 시청 시나리오만)
