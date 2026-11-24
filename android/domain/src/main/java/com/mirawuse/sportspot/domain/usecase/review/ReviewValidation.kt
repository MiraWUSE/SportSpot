package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.result.DomainError

internal fun reviewValidation(
    rating: Int,
    text: String,
    title: String?,
): DomainError.Validation? =
    when {
        rating !in 1..5 -> DomainError.Validation("Оценка должна быть от 1 до 5")
        text.isBlank() -> DomainError.Validation("Текст отзыва не может быть пустым")
        text.trim().length > 2000 -> DomainError.Validation("Текст отзыва должен содержать до 2000 символов")
        (title?.trim()?.length ?: 0) > 120 -> DomainError.Validation("Заголовок должен содержать до 120 символов")
        else -> null
    }
