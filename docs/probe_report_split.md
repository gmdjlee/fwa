# DEVICE_FACTS — Phase 0 프로브 결과

- 생성: 2026-07-25 00:14:50
- 측정 시점 포그라운드 앱: `(없음)`

## 미지수 해소

| # | 항목 | 결과 |
|---|---|---|
| 5 | 팝업 화면이 AOSP freeform 기반인가 | ✅ freeform 지원 — Phase 4 Shizuku 경로 가능 |
| 6 | GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN 동작하는가 | FAILS — Recents 폴백 전략으로 전환 필요 |
| 7 | TYPE_SPLIT_SCREEN_DIVIDER 노출되는가 | ✅ 노출됨 |

## SplitPlanner 에 반영할 값

```kotlin
WindowGeometry(
    usableLeft = 0,
    usableTop = 0,   // ← rootWindowBounds 로 보정할 것
    usableWidth = 1968,
    usableHeight = 2184,
    dividerThickness = 221,
    minPaneHeight = 0,   // ← 디바이더를 위/아래 끝까지 밀어보고 실측
)
```

## A. 기기

| 항목 | 값 |
|---|---|
| 제조사/모델 | samsung SM-F966N |
| Android | 16 (API 36) |
| One UI | - |
| FEATURE_FREEFORM_WINDOW_MANAGEMENT | true |
| FEATURE_PICTURE_IN_PICTURE | true |
| enable_freeform_support | - |
| enable_non_resizable_multi_window | 0 |
| force_resizable_activities | 1 |

## B. 창 (7개)

| type | layer | bounds | package | active |
|---|---|---|---|---|
| UNKNOWN(-1) | 6 | 1917,1134,1968,1646 | com.samsung.android.sidegesturepad | false |
| UNKNOWN(-1) | 5 | 0,1208,51,1701 | com.samsung.android.sidegesturepad | false |
| SYSTEM | 4 | 0,2150,1968,2184 | com.sec.android.app.launcher | false |
| SPLIT_SCREEN_DIVIDER | 3 | 950,981,1018,1202 | com.android.systemui | false |
| APPLICATION | 2 | 991,0,1968,2184 | com.google.android.youtube | true |
| APPLICATION | 1 | 381,89,595,145 | com.android.systemui | false |
| APPLICATION | 0 | 0,0,977,2184 | dev.dj.foldwindow | false |

디바이더 bounds: `950,981,1018,1202`

## C. 분할 진입

- 호출 전 디바이더 존재: true
- performGlobalAction 반환값: false
- 디바이더 상태 변화 감지: false (3001ms)
- **판정: FAILS — Recents 폴백 전략으로 전환 필요**

## D. 메트릭

| 항목 | 값 |
|---|---|
| 해상도 | 1968 × 2184 px |
| density | 2.25 (360 dpi) |
| dp 크기 | 875 × 971 dp |
| smallestScreenWidthDp | 875 |
| 방향 | PORTRAIT |
| rootWindowBounds | 0,0,977,2184 |

## E. 검은 띠 실측

| 항목 | 값 |
|---|---|
| 프레임 | 984 × 1092 px |
| 상단 띠 | - px |
| 하단 띠 | - px |
| 콘텐츠 높이 | - px |
| 역산 종횡비 | - |
| 스냅 결과 | (스냅 안 됨) |
| 신뢰도 | - |

비고: 검은 띠를 찾지 못함. 영상을 가로 전체화면으로 재생한 뒤 다시 실행할 것
