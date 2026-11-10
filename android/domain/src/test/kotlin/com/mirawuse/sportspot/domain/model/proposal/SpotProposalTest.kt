package com.mirawuse.sportspot.domain.model.proposal

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SpotProposalTest {
    @Test
    fun `новое предложение ожидает модерации`() {
        val proposal = createProposal()

        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNull(proposal.rejectReason)
    }

    @Test
    fun `предложение сохраняет данные площадки`() {
        val proposal = createProposal()

        assertEquals("proposal-123", proposal.id)
        assertEquals("user-123", proposal.authorId)
        assertEquals("Футбольное поле", proposal.name)
        assertEquals(SportType.FOOTBALL, proposal.sportType)
        assertEquals(GeoPoint(56.3269, 44.0059), proposal.location)
    }

    @Test
    fun `пустой идентификатор вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createProposal(id = "")
        }
    }

    @Test
    fun `пустой автор вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createProposal(authorId = "")
        }
    }

    @Test
    fun `пустое название вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createProposal(name = "")
        }
    }

    @Test
    fun `ожидающее предложение можно одобрить`() {
        val proposal = createProposal()

        val approved = proposal.approve()

        assertEquals(ProposalStatus.APPROVED, approved.status)
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNull(approved.rejectReason)
    }

    @Test
    fun `ожидающее предложение можно отклонить`() {
        val proposal = createProposal()

        val rejected = proposal.reject("Неверные координаты")

        assertEquals(ProposalStatus.REJECTED, rejected.status)
        assertEquals("Неверные координаты", rejected.rejectReason)
    }

    @Test
    fun `отклонение без причины вызывает ошибку`() {
        val proposal = createProposal()

        assertThrows(IllegalArgumentException::class.java) {
            proposal.reject("   ")
        }
    }

    @Test
    fun `одобренное предложение нельзя одобрить повторно`() {
        val approved = createProposal().approve()

        assertThrows(IllegalArgumentException::class.java) {
            approved.approve()
        }
    }

    @Test
    fun `отклоненное предложение нельзя отклонить повторно`() {
        val rejected = createProposal().reject("Дубликат")

        assertThrows(IllegalArgumentException::class.java) {
            rejected.reject("Другая причина")
        }
    }

    private fun createProposal(
        id: String = "proposal-123",
        authorId: String = "user-123",
        name: String = "Футбольное поле",
    ): SpotProposal =
        SpotProposal(
            id = id,
            authorId = authorId,
            name = name,
            sportType = SportType.FOOTBALL,
            location = GeoPoint(56.3269, 44.0059),
        )
}
