# DESIGN_P41 — 팝업(freeform) 모드: 설계 스케치 + 선행 프로브 계획

> 상태: **구현 완료 — 실기기 E2E 대기** (2026-07-28 프로브+구현 세션). F1·F2·F3·F4·F6 ✅ / F5(DRM 재생)만 수동 확인 잔류 → 17차 캠페인 편입. 잔여 검증 = DEVICE_FACTS 「P4-1 구현분」 절 7항목 (Shizuku 설치 선행).
> **후보 A(Shizuku 셸 명령) 채택 확정** — `am start --windowingMode 5` + `am task resize` 실측 통과. 측정 SSOT = `docs/DEVICE_FACTS.md` 「P4-1 프로브 F1~F6」 절.
> 작성: 2026-07-28 (Phase 4 착수 세션).

## 1. 목적

분할 화면 대신 **One UI 팝업(멀티윈도우) 창**으로 대상 앱을 영상 종횡비에 맞는 크기로 띄우는 선택 모드.

분할 모드 대비 차별점:
- 파트너 창(PanelActivity)이 필요 없다 — 피커 진입 레시피 전체가 생략된다
- 임의 위치·크기 — 상/하 몰기가 아니라 자유 배치
- 대가: Shizuku 필요 (일반 접근성 API 로는 freeform 실행/리사이즈 불가)

## 2. 확정 근거 (Phase 0 프로브 A, docs/DEVICE_FACTS.md)

| 항목 | 값 |
|---|---|
| `FEATURE_FREEFORM_WINDOW_MANAGEMENT` | ✅ true |
| `force_resizable_activities` | ✅ 1 |
| One UI 팝업 = AOSP freeform 기반 | ✅ [측정 2026-07-28] mode 5 → One UI 팝업 크롬 렌더 확인 |

부수 실측: 드래그 진입 레시피가 UNRESIZEABLE 앱에서 "팝업(프리폼)으로 라우팅"된 사례 다수 — One UI 가 freeform 계열 윈도잉을 일상적으로 사용한다는 방증.

## 3. 미지수 — 구현 전 반드시 프로브로 해소

| # | 질문 | 확인 방법 (기기 연결 시 adb, 물리 조작 불요) |
|---|---|---|
| F1 | One UI "팝업 화면"이 실제 `WINDOWING_MODE_FREEFORM`(5)인가 | 수동으로 아무 앱을 팝업으로 띄운 뒤 `adb shell dumpsys window windows \| grep -iE "windowingMode\|mode=5"` 및 `adb shell dumpsys activity activities \| grep -i windowingmode` |
| F2 | 셸 권한으로 freeform 실행이 되는가 | `adb shell am help \| grep -i windowing` 으로 `--windowingMode` 플래그 존재 확인 → `adb shell am start -n com.google.android.youtube/.app.honeycomb.Shell\$HomeActivity --windowingMode 5` 실행 후 창 상태 관찰 |
| F3 | 실행 후 bounds 제어 수단 | `adb shell am help` 에서 `task resize` 지원 확인 → 지원 시 `am task resize <taskId> L T R B` 실측. 미지원이면 후보 B(binder) 로 강등 |
| F4 | UNRESIZEABLE 앱(넷플릭스)이 팝업 진입을 허용하는가 | F2 명령을 넷플릭스로 반복. One UI "모든 앱 멀티윈도우" 설정 ON/OFF 각각 |
| F5 | DRM 표면이 팝업에서 렌더되는가 | 넷플릭스 팝업 상태에서 재생 — 화면 캡처 방식이 아니므로 가능성 높음, 확인만 |
| F6 | 팝업 창의 a11y 노출 형태 | 팝업 상태에서 기존 프로브 B(창 덤프) 재실행 — type/bounds/layer 기록 (후속 보정·추적용) |

**F1·F2 중 하나라도 ❌ 면 P4-1 전체를 기각하고 이 문서에 사유를 기록한다.**

> **프로브 결과 (2026-07-28)**: F1 ✅ · F2 ✅ · F3 ✅ (`am task resize` 픽셀 정확) · F4 ✅ (`enable_non_resizable_multi_window=0` 에서도 성공 — 설정 비의존) · F5 ⏳ 수동 잔류 · F6 ✅ (`APPLICATION` 타입, bounds 1:1, layer=z순서). 게이트 통과 — 기각 조건 미충족. 상세 = DEVICE_FACTS 「P4-1 프로브 F1~F6」 절.

## 4. 아키텍처 스케치 (프로브 통과 가정)

### 의존성·권한
- `dev.rikka.shizuku:api` + `dev.rikka.shizuku:provider` (13.x), 매니페스트 `ShizukuProvider` 등록
- 온보딩에 4번째 권한 카드 "Shizuku" (설치 → 활성 → 권한 허용). Shizuku 미가용이면 팝업 기능 자체를 숨긴다 (기존 권한 카드 감지 패턴 재사용)
- binder 사망/권한 회수 런타임 처리: 토스트 + 기능 숨김 (조용한 실패 금지)

### 실행 경로 후보 (프로브 결과로 택1)
- **후보 A — Shizuku 셸 명령**: `am start --windowingMode 5` (+ 필요 시 `am task resize`). 가장 단순, hidden API 무접촉. **← 채택 (F2·F3 통과, 2026-07-28)**
- **후보 B — Shizuku binder**: `IActivityTaskManager` + `ActivityOptions.setLaunchBounds()`(+hidden `setLaunchWindowingMode`). HiddenApiBypass 필요 — 기각 (A 통과로 불필요)

### 도메인 (순수 Kotlin, ADR-4)
- `PopupPlanner`: (aspect, 화면 기하, 여백 정책) → 팝업 bounds. v1 정책 = 상단 중앙, 폭 = min(화면폭 − 마진, ⌊높이×aspect⌋). SplitPlanner 와 동형 테스트 커버리지
- 상태 머신: **비사용 확정** (2026-07-28 구현) — 진입 스텝이 셸 명령 1개라 머신 과잉. `startPopup()` = 단순 명령 + 검증 폴링 5단(창 출현→taskId→resize→bounds 검증 ±8px), 각 단계 타임아웃+명시적 실패 (머신 무변경 원칙 유지)

### UX
- 확장 메뉴 항목 "팝업으로 열기" — Shizuku 가용 시에만 노출
- 함정 #22(오버레이 존재 시 낙착 교란)가 팝업 실행에도 적용되는지 프로브 F2 에서 버블 ON/OFF 로 관찰

## 5. 명시적 비목표
- 팝업 위치 기억/드래그 추종 (v1 은 고정 정책 배치)
- 분할 모드와의 자동 전환
- Shizuku 없이 동작하는 폴백 경로 (없음 — 기능 숨김이 폴백)
