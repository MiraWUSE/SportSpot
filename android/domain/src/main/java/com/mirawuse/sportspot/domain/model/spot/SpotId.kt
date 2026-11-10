package com.mirawuse.sportspot.domain.model.spot

data class SpotId(
    val source: String,
    val value: String,
) {
    init {
        require(source.isNotBlank()) {
            "Источник площадки не может быть пустым"
        }

        require(value.isNotBlank()) {
            "Идентификатор площадки не может быть пустым"
        }
    }
}
