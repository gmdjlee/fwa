package dev.dj.foldwindow.domain

import kotlin.math.abs

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * ADR-2: 고정 지연(postDelayed/delay) 금지. 분할 진입 → 파트너 앱 실행 → 디바이더 이동은
 * 상태 머신 + 조건 폴링으로 구현한다. 이 파일은 "무엇을 언제 할지"만 결정하는 순수 리듀서다.
 * 실제 접근성 액션(제스처 디스패치, 스크린샷 촬영 등)은 platform/service 레이어가
 * ArrangeEffect 를 받아 수행하고, 그 결과를 ArrangeEvent 로 다시 넣어준다.
 *
 * 시간은 절대 이 파일 안에서 구하지 않는다. 모든 이벤트가 nowMs 를 들고 온다.
 * 그래야 이 파일이 100% 결정적이고 JVM 단위 테스트로 전부 검증 가능하다.
 *
 * 실기기 확정 사실:
 * - GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN 미지원 → 분할 진입은 Recents 레시피 4단계
 *   (Recents 열기 → 앱 카드 아이콘 탭 → "분할 화면으로 열기" 탭 → 파트너 앱 탭).
 *   각 단계의 구체적 내용은 platform 레이어가 알고, 이 머신은 "N단계 중 k번째" 만 안다.
 * - AccessibilityService.takeScreenshot() 은 초당 ~1회 레이트 리밋. MeasureLetterbox 효과에
 *   notBeforeMs 를 실어 서비스 레이어가 그 시각 이전에 촬영하지 않도록 강제한다.
 * - ADR-5 폐루프: 배치 후 재측정해 잔여 띠가 임계 초과면 정확히 1회만 미세 조정한다.
 *   무한 보정 루프는 금지.
 */

/** 오케스트레이션 타이밍/허용치 설정. 전부 실기기 튜닝 대상이며 근거 없이 바꾸지 않는다. */
data class ArrangeConfig(
    val splitCheckTimeoutMs: Long = 2000,
    val entryStepTimeoutMs: Long = 3000,      // 단계별
    val entryStepCount: Int = 4,              // Recents 레시피 단계 수
    val entryStepMaxAttempts: Int = 3,        // 단계별 재시도 상한
    val dividerTimeoutMs: Long = 4000,
    val dragTimeoutMs: Long = 3000,
    val verifyTimeoutMs: Long = 5000,
    val screenshotMinIntervalMs: Long = 1100, // takeScreenshot 레이트 리밋 백오프
    val dividerTolerancePx: Int = 4,          // 이 이내면 드래그 생략
    val residualTolerancePx: Int = 8,         // 잔여 띠 허용치
    val closedLoopCorrection: Boolean = true, // ADR-5 1회 보정 활성화 여부. false면 잔여값만 정직 보고
)

/** 서비스가 머신에 밀어넣는 사건. 전부 발생 시각(nowMs)을 들고 온다 — 절대시간은 머신 밖에서 구한다. */
sealed interface ArrangeEvent {
    val nowMs: Long

    /** 사용자가 배치를 트리거. targetDividerCenterY 는 사전 계산된 SplitPlan 의 목표 디바이더 중심 Y */
    data class Start(override val nowMs: Long, val targetDividerCenterY: Int) : ArrangeEvent

    /** QuerySplitState 효과에 대한 응답 */
    data class SplitStateResult(override val nowMs: Long, val active: Boolean) : ArrangeEvent

    /** PerformEntryStep 효과에 대한 응답 */
    data class EntryStepResult(override val nowMs: Long, val success: Boolean) : ArrangeEvent

    /** QueryDivider 효과에 대한 응답. centerY=null 이면 이번 폴에서 미발견 */
    data class DividerResult(override val nowMs: Long, val centerY: Int?) : ArrangeEvent

    /** dispatchGesture 콜백. completed=false 는 취소됨을 뜻한다 */
    data class DragResult(override val nowMs: Long, val completed: Boolean) : ArrangeEvent

    /**
     * 재측정 결과.
     * @param residualPx 잔여 letterbox px. null 이면 측정 실패
     * @param correctedTargetY 서비스가 SplitPlanner 로 재계산한 보정 목표. 측정 성공 시 제공
     */
    data class MeasureResult(
        override val nowMs: Long,
        val residualPx: Int?,
        val correctedTargetY: Int?,
    ) : ArrangeEvent

    /** 타임아웃 판정용. 서비스가 주기적으로 보낸다 */
    data class Tick(override val nowMs: Long) : ArrangeEvent

    /** 사용자 취소 */
    data class Cancel(override val nowMs: Long) : ArrangeEvent
}

/** 실패 사유. 조용한 실패 금지 — 모든 실패는 이 값으로 드러난다. */
enum class FailureReason {
    SPLIT_CHECK_TIMEOUT,
    ENTRY_STEP_FAILED,
    ENTRY_TIMEOUT,
    DIVIDER_NOT_FOUND,
    DRAG_FAILED,
    DRAG_TIMEOUT,
    CANCELLED,
}

/** 배치 오케스트레이션 상태. Done/Failed 는 터미널이며 이후 모든 이벤트를 무시한다. */
sealed interface ArrangeState {

    object Idle : ArrangeState

    data class CheckingSplit(val since: Long, val targetY: Int) : ArrangeState

    /** step 은 1부터 시작 */
    data class EnteringSplit(
        val step: Int,
        val attempt: Int,
        val stepSince: Long,
        val targetY: Int,
    ) : ArrangeState

    data class WaitingDivider(val since: Long, val targetY: Int) : ArrangeState

    data class Dragging(
        val since: Long,
        val targetY: Int,
        val adjustedOnce: Boolean,
        val lastShotAt: Long?,
    ) : ArrangeState

    /** lastShotAt = 이번 측정 요청 시각 (레이트 리밋 백오프 계산의 기준점) */
    data class Verifying(
        val since: Long,
        val targetY: Int,
        val adjustedOnce: Boolean,
        val lastShotAt: Long,
    ) : ArrangeState

    data class Done(
        val verified: Boolean,
        val finalResidualPx: Int?,
        val adjusted: Boolean,
    ) : ArrangeState

    data class Failed(val reason: FailureReason) : ArrangeState
}

/** 서비스 레이어가 실행해야 할 명령. 머신은 실행 결과를 모르고 다음 이벤트로만 안다. */
sealed interface ArrangeEffect {

    object QuerySplitState : ArrangeEffect

    data class PerformEntryStep(val step: Int) : ArrangeEffect

    /** 서비스가 자체 폴 간격으로 재시도한다. 머신은 결과 이벤트만 받는다 */
    object QueryDivider : ArrangeEffect

    data class DragDividerTo(val targetY: Int) : ArrangeEffect

    /** 레이트 리밋: notBeforeMs 이전에 takeScreenshot 을 호출하지 않는다 (함정 #3) */
    data class MeasureLetterbox(val notBeforeMs: Long) : ArrangeEffect
}

/**
 * 분할 배치 오케스트레이션 상태 머신.
 *
 * 순수 리듀서: (현재 상태, 이벤트) → (다음 상태, 실행할 효과 목록).
 * 기대하지 않는 이벤트(스테일 응답, 터미널 상태에서의 모든 이벤트)는 상태를 그대로 두고
 * 효과 없이 무시한다 — 늦게 도착한 응답이 머신을 오동작시키면 안 된다.
 */
object ArrangeStateMachine {

    data class Transition(val state: ArrangeState, val effects: List<ArrangeEffect>)

    fun reduce(
        state: ArrangeState,
        event: ArrangeEvent,
        config: ArrangeConfig = ArrangeConfig(),
    ): Transition {
        // 취소는 모든 비터미널 상태에서 최우선으로 처리한다.
        if (event is ArrangeEvent.Cancel && state !is ArrangeState.Done && state !is ArrangeState.Failed) {
            return Transition(ArrangeState.Failed(FailureReason.CANCELLED), emptyList())
        }

        return when (state) {
            ArrangeState.Idle -> reduceIdle(event)
            is ArrangeState.CheckingSplit -> reduceCheckingSplit(state, event, config)
            is ArrangeState.EnteringSplit -> reduceEnteringSplit(state, event, config)
            is ArrangeState.WaitingDivider -> reduceWaitingDivider(state, event, config)
            is ArrangeState.Dragging -> reduceDragging(state, event, config)
            is ArrangeState.Verifying -> reduceVerifying(state, event, config)
            is ArrangeState.Done -> stay(state)
            is ArrangeState.Failed -> stay(state)
        }
    }

    private fun stay(state: ArrangeState): Transition = Transition(state, emptyList())

    private fun reduceIdle(event: ArrangeEvent): Transition = when (event) {
        is ArrangeEvent.Start -> Transition(
            ArrangeState.CheckingSplit(since = event.nowMs, targetY = event.targetDividerCenterY),
            listOf(ArrangeEffect.QuerySplitState),
        )

        else -> stay(ArrangeState.Idle)
    }

    private fun reduceCheckingSplit(
        state: ArrangeState.CheckingSplit,
        event: ArrangeEvent,
        config: ArrangeConfig,
    ): Transition = when (event) {
        is ArrangeEvent.SplitStateResult -> if (event.active) {
            Transition(
                ArrangeState.WaitingDivider(since = event.nowMs, targetY = state.targetY),
                listOf(ArrangeEffect.QueryDivider),
            )
        } else {
            Transition(
                ArrangeState.EnteringSplit(step = 1, attempt = 1, stepSince = event.nowMs, targetY = state.targetY),
                listOf(ArrangeEffect.PerformEntryStep(1)),
            )
        }

        is ArrangeEvent.Tick -> if (event.nowMs - state.since > config.splitCheckTimeoutMs) {
            Transition(ArrangeState.Failed(FailureReason.SPLIT_CHECK_TIMEOUT), emptyList())
        } else {
            stay(state)
        }

        else -> stay(state)
    }

    private fun reduceEnteringSplit(
        state: ArrangeState.EnteringSplit,
        event: ArrangeEvent,
        config: ArrangeConfig,
    ): Transition = when (event) {
        is ArrangeEvent.EntryStepResult -> if (event.success) {
            if (state.step < config.entryStepCount) {
                val nextStep = state.step + 1
                Transition(
                    state.copy(step = nextStep, attempt = 1, stepSince = event.nowMs),
                    listOf(ArrangeEffect.PerformEntryStep(nextStep)),
                )
            } else {
                Transition(
                    ArrangeState.WaitingDivider(since = event.nowMs, targetY = state.targetY),
                    listOf(ArrangeEffect.QueryDivider),
                )
            }
        } else {
            if (state.attempt < config.entryStepMaxAttempts) {
                Transition(
                    state.copy(attempt = state.attempt + 1, stepSince = event.nowMs),
                    listOf(ArrangeEffect.PerformEntryStep(state.step)),
                )
            } else {
                Transition(ArrangeState.Failed(FailureReason.ENTRY_STEP_FAILED), emptyList())
            }
        }

        is ArrangeEvent.Tick -> if (event.nowMs - state.stepSince > config.entryStepTimeoutMs) {
            Transition(ArrangeState.Failed(FailureReason.ENTRY_TIMEOUT), emptyList())
        } else {
            stay(state)
        }

        else -> stay(state)
    }

    private fun reduceWaitingDivider(
        state: ArrangeState.WaitingDivider,
        event: ArrangeEvent,
        config: ArrangeConfig,
    ): Transition = when (event) {
        is ArrangeEvent.DividerResult -> {
            val centerY = event.centerY
            if (centerY == null) {
                // 아직 못 찾음 — since 보존한 채 재폴 요청
                Transition(state, listOf(ArrangeEffect.QueryDivider))
            } else if (abs(centerY - state.targetY) <= config.dividerTolerancePx) {
                Transition(
                    ArrangeState.Verifying(
                        since = event.nowMs,
                        targetY = state.targetY,
                        adjustedOnce = false,
                        lastShotAt = event.nowMs,
                    ),
                    listOf(ArrangeEffect.MeasureLetterbox(notBeforeMs = event.nowMs)),
                )
            } else {
                Transition(
                    ArrangeState.Dragging(
                        since = event.nowMs,
                        targetY = state.targetY,
                        adjustedOnce = false,
                        lastShotAt = null,
                    ),
                    listOf(ArrangeEffect.DragDividerTo(state.targetY)),
                )
            }
        }

        is ArrangeEvent.Tick -> if (event.nowMs - state.since > config.dividerTimeoutMs) {
            Transition(ArrangeState.Failed(FailureReason.DIVIDER_NOT_FOUND), emptyList())
        } else {
            stay(state)
        }

        else -> stay(state)
    }

    private fun reduceDragging(
        state: ArrangeState.Dragging,
        event: ArrangeEvent,
        config: ArrangeConfig,
    ): Transition = when (event) {
        is ArrangeEvent.DragResult -> if (event.completed) {
            val previousShot = state.lastShotAt
            val notBefore = if (previousShot == null) {
                event.nowMs
            } else {
                maxOf(event.nowMs, previousShot + config.screenshotMinIntervalMs)
            }
            Transition(
                ArrangeState.Verifying(
                    since = event.nowMs,
                    targetY = state.targetY,
                    adjustedOnce = state.adjustedOnce,
                    lastShotAt = event.nowMs,
                ),
                listOf(ArrangeEffect.MeasureLetterbox(notBeforeMs = notBefore)),
            )
        } else {
            Transition(ArrangeState.Failed(FailureReason.DRAG_FAILED), emptyList())
        }

        is ArrangeEvent.Tick -> if (event.nowMs - state.since > config.dragTimeoutMs) {
            Transition(ArrangeState.Failed(FailureReason.DRAG_TIMEOUT), emptyList())
        } else {
            stay(state)
        }

        else -> stay(state)
    }

    private fun reduceVerifying(
        state: ArrangeState.Verifying,
        event: ArrangeEvent,
        config: ArrangeConfig,
    ): Transition = when (event) {
        is ArrangeEvent.MeasureResult -> {
            val residual = event.residualPx
            when {
                residual == null -> Transition(
                    ArrangeState.Done(verified = false, finalResidualPx = null, adjusted = state.adjustedOnce),
                    emptyList(),
                )

                residual <= config.residualTolerancePx -> Transition(
                    ArrangeState.Done(verified = true, finalResidualPx = residual, adjusted = state.adjustedOnce),
                    emptyList(),
                )

                !state.adjustedOnce && config.closedLoopCorrection -> {
                    // ADR-5: 잔여 초과 시 정확히 1회 미세 조정
                    val correctedTarget = event.correctedTargetY ?: state.targetY
                    Transition(
                        ArrangeState.Dragging(
                            since = event.nowMs,
                            targetY = correctedTarget,
                            adjustedOnce = true,
                            lastShotAt = state.lastShotAt,
                        ),
                        listOf(ArrangeEffect.DragDividerTo(correctedTarget)),
                    )
                }

                !state.adjustedOnce -> {
                    // [측정 2026-07-25] PROFILE 종횡비에서 오염된 재측정(컨트롤 오버레이 residual=224)이
                    // 정확한 배치를 과축소 — 보정은 신뢰 가능한 측정 경로(MEASURED/PRESET)에서만.
                    // closedLoopCorrection=false 인 경우 보정하지 않고 잔여값을 정직하게 보고한다
                    // (조용한 실패 금지 — Done(verified=true)로 끝나되 residual/adjusted 는 사실 그대로).
                    Transition(
                        ArrangeState.Done(verified = true, finalResidualPx = residual, adjusted = false),
                        emptyList(),
                    )
                }

                else -> Transition(
                    // 이미 1회 보정했다 — 추가 보정 금지, 잔여값만 보고
                    ArrangeState.Done(verified = true, finalResidualPx = residual, adjusted = true),
                    emptyList(),
                )
            }
        }

        is ArrangeEvent.Tick -> if (event.nowMs - state.since > config.verifyTimeoutMs) {
            Transition(
                ArrangeState.Done(verified = false, finalResidualPx = null, adjusted = state.adjustedOnce),
                emptyList(),
            )
        } else {
            stay(state)
        }

        else -> stay(state)
    }
}
