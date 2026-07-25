package dev.dj.foldwindow.data

import dev.dj.foldwindow.domain.AppProfile
import dev.dj.foldwindow.domain.AspectPreset
import dev.dj.foldwindow.domain.AspectSource
import dev.dj.foldwindow.domain.PartnerMode
import dev.dj.foldwindow.domain.Placement
import dev.dj.foldwindow.domain.ProfileDefaults
import dev.dj.foldwindow.domain.WindowProfilesConfig
import dev.dj.foldwindow.domain.validate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/*
 * kotlinx.serialization 은 이 파일(data/)에만 존재한다. domain/ 은 이 라이브러리를 몰라야 한다
 * (CLAUDE.md 아키텍처 규칙: domain 은 kotlin-stdlib 외 의존성 금지).
 *
 * 역할: JSON 문자열 -> DTO(@Serializable) -> 도메인 모델 매핑 -> domain.validate() 호출.
 * 문법 오류든 의미 검증 오류든 예외를 밖으로 던지지 않고 전부 ProfilesParseResult.Failure 로 감싼다.
 */

/** window_profiles.json 파싱 결과. 문법 오류와 의미 검증 오류를 구분하지 않고 errors 로 통합한다 */
sealed interface ProfilesParseResult {
    data class Success(val config: WindowProfilesConfig) : ProfilesParseResult
    data class Failure(val errors: List<String>) : ProfilesParseResult
}

object WindowProfilesParser {

    /**
     * window_profiles.json 이 assets 에 노출되는 파일명. SSOT — service/ArrangerAccessibilityService
     * 와 service/FloatingLauncherService(P3-2 메뉴 프리셋 로드) 양쪽이 이 상수를 공유한다.
     * 중복 정의 금지.
     */
    const val PROFILES_ASSET_NAME = "window_profiles.json"

    private val json = Json {
        ignoreUnknownKeys = true // note, 향후 추가 필드 등에 관대하게. 필수 필드 누락은 여전히 에러.
    }

    fun parse(jsonText: String): ProfilesParseResult {
        val dto = try {
            json.decodeFromString(ConfigDto.serializer(), jsonText)
        } catch (e: SerializationException) {
            return ProfilesParseResult.Failure(listOf("JSON 파싱 실패: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization 이 일부 형식 오류(예: enum 역직렬화 실패)를
            // IllegalArgumentException 계열로 던지는 경우가 있어 함께 방어한다.
            return ProfilesParseResult.Failure(listOf("JSON 파싱 실패: ${e.message}"))
        }

        val errors = mutableListOf<String>()

        val defaults = mapDefaults(dto.defaults, errors)
        val presets = dto.presets.map { mapPreset(it) }
        val profiles = dto.profiles.mapIndexed { i, p -> mapProfile(p, i, defaults, errors) }

        // 열거형 매핑 단계에서 이미 오류가 났다면 도메인 모델을 구성해봐야 무의미하다.
        if (errors.isNotEmpty()) {
            return ProfilesParseResult.Failure(errors)
        }

        val config = WindowProfilesConfig(
            schema = dto.schema,
            defaults = defaults,
            presets = presets,
            profiles = profiles,
        )

        val validationErrors = config.validate()
        return if (validationErrors.isEmpty()) {
            ProfilesParseResult.Success(config)
        } else {
            ProfilesParseResult.Failure(validationErrors)
        }
    }

    private fun mapDefaults(dto: DefaultsDto, errors: MutableList<String>): ProfileDefaults {
        val placement = mapPlacement(dto.placement, "defaults.placement", errors)
        val partner = mapPartner(dto.partner, "defaults.partner", errors)
        return ProfileDefaults(
            aspect = dto.aspect,
            placement = placement,
            partner = partner,
            closedLoopCorrection = dto.closedLoopCorrection,
            residualTolerancePx = dto.residualTolerancePx,
            requireMeasurementAgreement = dto.requireMeasurementAgreement,
            cacheMeasuredAspect = dto.cacheMeasuredAspect,
        )
    }

    private fun mapPreset(dto: PresetDto): AspectPreset =
        AspectPreset(id = dto.id, aspect = dto.aspect, label = dto.label)

    private fun mapProfile(
        dto: ProfileDto,
        index: Int,
        defaults: ProfileDefaults,
        errors: MutableList<String>,
    ): AppProfile {
        val aspectSource = mapAspectSource(dto.aspectSource, "profiles[$index].aspectSource", errors)
        val placement = dto.placement
            ?.let { mapPlacement(it, "profiles[$index].placement", errors) }
            ?: defaults.placement // 생략 시 defaults 로 병합
        return AppProfile(
            packageName = dto.packageName,
            label = dto.label,
            aspect = dto.aspect,
            aspectSource = aspectSource,
            placement = placement,
            note = dto.note,
        )
    }

    private fun mapPlacement(value: String, location: String, errors: MutableList<String>): Placement =
        when (value) {
            "TOP" -> Placement.TOP
            "BOTTOM" -> Placement.BOTTOM
            else -> {
                errors += "$location: 알 수 없는 placement 값 '$value'"
                Placement.TOP // 에러가 이미 기록됐으므로 이 값은 상위에서 폐기된다
            }
        }

    private fun mapPartner(value: String, location: String, errors: MutableList<String>): PartnerMode =
        when (value) {
            "BLACK" -> PartnerMode.BLACK
            else -> {
                errors += "$location: 알 수 없는 partner 값 '$value'"
                PartnerMode.BLACK
            }
        }

    private fun mapAspectSource(value: String, location: String, errors: MutableList<String>): AspectSource =
        when (value) {
            "PROFILE" -> AspectSource.PROFILE
            "MEASURED" -> AspectSource.MEASURED
            "PRESET" -> AspectSource.PRESET // 문법적으로는 유효한 값. 도메인 검증(validate())에서 금지 처리된다.
            else -> {
                errors += "$location: 알 수 없는 aspectSource 값 '$value'"
                AspectSource.PRESET
            }
        }
}

@Serializable
private data class ConfigDto(
    val schema: String,
    val defaults: DefaultsDto,
    val presets: List<PresetDto> = emptyList(),
    val profiles: List<ProfileDto> = emptyList(),
)

@Serializable
private data class DefaultsDto(
    val aspect: Float,
    val placement: String,
    val partner: String,
    val closedLoopCorrection: Boolean,
    val residualTolerancePx: Int,
    // DESIGN #12: 키 부재 시 기본 true 로 동작해야 한다 — 기존 SSOT 시드(config/window_profiles.json)
    // 에는 이 키가 없다. kotlinx.serialization 은 JSON 키가 없으면 이 기본값을 그대로 쓴다.
    val requireMeasurementAgreement: Boolean = true,
    // DESIGN #12 §6: 키 부재 시 기본 true 로 동작해야 한다 — 기존 SSOT 시드에도 이 키가 없다.
    val cacheMeasuredAspect: Boolean = true,
)

@Serializable
private data class PresetDto(
    val id: String,
    val aspect: Float? = null,
    val label: String,
)

@Serializable
private data class ProfileDto(
    @SerialName("package") val packageName: String,
    val label: String,
    val aspect: Float? = null,
    val aspectSource: String,
    val placement: String? = null,
    val note: String? = null,
)
