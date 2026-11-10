
package com.mirawuse.sportspot.domain.model.review

import com.mirawuse.sportspot.domain.model.spot.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `отзыв создается с корректными данными`() {
        val review =
            Review(
                id = "review-123",
                spotId = spotId,
                authorId = "user-123",
                rating = 5,
                text = "Отличная площадка",
                photoUrls = listOf("photo1.jpg"),
            )

        assertEquals("review-123", review.id)
        assertEquals(spotId, review.spotId)
        assertEquals("user-123", review.authorId)
        assertEquals(5, review.rating)
        assertEquals("Отличная площадка", review.text)
        assertEquals(1, review.photoUrls.size)
    }

    @Test
    fun `оценки 1 и 5 допустимы`() {
        val first = createReview(rating = 1)
        val second = createReview(rating = 5)

        assertEquals(1, first.rating)
        assertEquals(5, second.rating)
    }

    @Test
    fun `оценка меньше 1 вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createReview(rating = 0)
        }
    }

    @Test
    fun `оценка больше 5 вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createReview(rating = 6)
        }
    }

    @Test
    fun `пустой текст вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createReview(text = "")
        }
    }

    @Test
    fun `пустой автор вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createReview(authorId = "")
        }
    }

    @Test
    fun `пустой идентификатор отзыва вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            createReview(id = "")
        }
    }

    @Test
    fun `отзыв может быть без фотографий`() {
        val review = createReview()

        assertTrue(review.photoUrls.isEmpty())
    }

    private fun createReview(
        id: String = "review-123",
        authorId: String = "user-123",
        rating: Int = 5,
        text: String = "Хорошая площадка",
    ): Review =
        Review(
            id = id,
            spotId = spotId,
            authorId = authorId,
            rating = rating,
            text = text,
        )
}
