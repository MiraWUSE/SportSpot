package com.mirawuse.sportspot.domain.model.user

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
) {
    init {
        require(id.isNotBlank()) {
            "Идентификатор пользователя не может быть пустым"
        }

        require(name.isNotBlank()) {
            "Имя пользователя не может быть пустым"
        }

        require(email.isNotBlank()) {
            "Электронная почта не может быть пустой"
        }
    }

    fun isModerator(): Boolean = role == UserRole.MODERATOR
}
