package com.mirawuse.sportspot.domain.validation

import com.mirawuse.sportspot.domain.model.spot.SportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormValidationTest {
    @Test
    fun `required values reject whitespace`() {
        assertEquals(FormError.REQUIRED, FormValidation.required(" \n\t "))
    }

    @Test
    fun `required values accept text with surrounding whitespace`() {
        assertNull(FormValidation.required("  Комментарий  "))
    }

    @Test
    fun `empty email is reported as required`() {
        assertEquals(FormError.REQUIRED, FormValidation.email("  "))
    }

    @Test
    fun `malformed emails receive a format error`() {
        listOf("user", "user@example", "user@@example.com", "user@ex..ample.com", "name @example.com").forEach {
            assertEquals(FormError.EMAIL_FORMAT, FormValidation.email(it))
        }
    }

    @Test
    fun `email accepts a subdomain and surrounding whitespace`() {
        assertNull(FormValidation.email("  Name+spot@sport.example.org  "))
    }

    @Test
    fun `rating accepts inclusive boundaries and requires a selection`() {
        assertNull(FormValidation.rating(1))
        assertNull(FormValidation.rating(5))
        assertEquals(FormError.RATING_REQUIRED, FormValidation.rating(0))
        assertEquals(FormError.RATING_REQUIRED, FormValidation.rating(6))
    }

    @Test
    fun `proposal requires a real sport selection`() {
        assertEquals(FormError.SPORT_REQUIRED, FormValidation.sport(null))
        assertNull(FormValidation.sport(SportType.FOOTBALL))
    }

    @Test
    fun `coordinates accept decimal points commas and inclusive boundaries`() {
        assertNull(FormValidation.latitude(" 56,3269 "))
        assertNull(FormValidation.longitude("44.0059"))
        assertNull(FormValidation.latitude("-90"))
        assertNull(FormValidation.latitude("90"))
        assertNull(FormValidation.longitude("-180"))
        assertNull(FormValidation.longitude("180"))
    }

    @Test
    fun `coordinates report missing malformed and non-finite values`() {
        assertEquals(FormError.REQUIRED, FormValidation.latitude(""))
        assertEquals(FormError.COORDINATE_FORMAT, FormValidation.latitude("Нижний Новгород"))
        assertEquals(FormError.COORDINATE_FORMAT, FormValidation.longitude("NaN"))
        assertEquals(FormError.COORDINATE_FORMAT, FormValidation.longitude("Infinity"))
    }

    @Test
    fun `coordinates reject values beyond their axis limits`() {
        assertEquals(FormError.COORDINATE_RANGE, FormValidation.latitude("90,1"))
        assertEquals(FormError.COORDINATE_RANGE, FormValidation.longitude("-180.1"))
    }
}
