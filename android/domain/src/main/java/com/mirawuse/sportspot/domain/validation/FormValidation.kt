package com.mirawuse.sportspot.domain.validation

import com.mirawuse.sportspot.domain.model.spot.SportType

enum class FormError {
    REQUIRED,
    PROFILE_NAME_TOO_LONG,
    EMAIL_FORMAT,
    RATING_REQUIRED,
    SPORT_REQUIRED,
    COORDINATE_FORMAT,
    COORDINATE_RANGE,
}

object FormValidation {
    const val MAX_PROFILE_NAME_LENGTH = 80

    private val emailPattern = Regex("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")

    fun required(value: String): FormError? = if (value.isBlank()) FormError.REQUIRED else null

    fun profileName(value: String): FormError? =
        required(value) ?: if (value.trim().length > MAX_PROFILE_NAME_LENGTH) FormError.PROFILE_NAME_TOO_LONG else null

    fun email(value: String): FormError? = required(value) ?: if (emailPattern.matches(value.trim())) null else FormError.EMAIL_FORMAT

    fun rating(value: Int): FormError? = if (value in 1..5) null else FormError.RATING_REQUIRED

    fun sport(value: SportType?): FormError? = if (value == null) FormError.SPORT_REQUIRED else null

    fun latitude(value: String): FormError? = coordinate(value, -90.0..90.0)

    fun longitude(value: String): FormError? = coordinate(value, -180.0..180.0)

    private fun coordinate(
        value: String,
        range: ClosedFloatingPointRange<Double>,
    ): FormError? {
        if (value.isBlank()) return FormError.REQUIRED
        val coordinate = value.trim().replace(',', '.').toDoubleOrNull()
        return when {
            coordinate == null || !coordinate.isFinite() -> FormError.COORDINATE_FORMAT
            coordinate !in range -> FormError.COORDINATE_RANGE
            else -> null
        }
    }
}
