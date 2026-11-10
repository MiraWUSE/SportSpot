package com.mirawuse.sportspot.domain.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainResultTest {
    @Test
    fun `успешный результат содержит значение`() {
        val result: DomainResult<String> =
            DomainResult.Success("Площадка найдена")

        assertTrue(result is DomainResult.Success)
        assertEquals("Площадка найдена", (result as DomainResult.Success).value)
    }

    @Test
    fun `пустой список считается успешным результатом`() {
        val result: DomainResult<List<String>> =
            DomainResult.Success(emptyList())

        assertTrue(result is DomainResult.Success)
        assertTrue((result as DomainResult.Success).value.isEmpty())
    }

    @Test
    fun `ошибка авторизации возвращается как Failure`() {
        val result: DomainResult<String> =
            DomainResult.Failure(DomainError.Unauthorized)

        assertTrue(result is DomainResult.Failure)
        assertEquals(
            DomainError.Unauthorized,
            (result as DomainResult.Failure).error,
        )
    }

    @Test
    fun `ошибка валидации содержит сообщение`() {
        val error = DomainError.Validation("Название не может быть пустым")
        val result: DomainResult<String> = DomainResult.Failure(error)

        assertEquals(
            error,
            (result as DomainResult.Failure).error,
        )
        assertEquals("Название не может быть пустым", error.message)
    }

    @Test
    fun `ошибки авторизации и доступа различаются`() {
        val unauthorized = DomainError.Unauthorized
        val forbidden = DomainError.Forbidden

        assertTrue(unauthorized != forbidden)
    }

    @Test
    fun `все варианты результата обрабатываются через when`() {
        val success: DomainResult<Int> = DomainResult.Success(5)
        val failure: DomainResult<Int> =
            DomainResult.Failure(DomainError.NotFound)

        fun describe(result: DomainResult<Int>): String =
            when (result) {
                is DomainResult.Success -> "Успех: ${result.value}"
                is DomainResult.Failure -> "Ошибка"
            }

        assertEquals("Успех: 5", describe(success))
        assertEquals("Ошибка", describe(failure))
    }
}
