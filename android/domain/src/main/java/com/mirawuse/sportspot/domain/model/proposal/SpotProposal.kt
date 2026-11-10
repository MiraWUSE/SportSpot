package com.mirawuse.sportspot.domain.model.proposal

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType

data class SpotProposal(
    val id: String,
    val authorId: String,
    val name: String,
    val sportType: SportType,
    val location: GeoPoint,
    val status: ProposalStatus = ProposalStatus.PENDING,
    val rejectReason: String? = null,
) {
    init {
        require(id.isNotBlank()) {
            "Идентификатор предложения не может быть пустым"
        }

        require(authorId.isNotBlank()) {
            "Идентификатор автора не может быть пустым"
        }

        require(name.isNotBlank()) {
            "Название площадки не может быть пустым"
        }
    }

    fun approve(): SpotProposal {
        require(status == ProposalStatus.PENDING) {
            "Одобрить можно только ожидающее предложение"
        }

        return copy(
            status = ProposalStatus.APPROVED,
            rejectReason = null,
        )
    }

    fun reject(reason: String): SpotProposal {
        require(status == ProposalStatus.PENDING) {
            "Отклонить можно только ожидающее предложение"
        }

        require(reason.isNotBlank()) {
            "Причина отклонения не может быть пустой"
        }

        return copy(
            status = ProposalStatus.REJECTED,
            rejectReason = reason.trim(),
        )
    }
}
