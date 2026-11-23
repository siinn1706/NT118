package vn.uit.healthos.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyContactInputTest {

    private val valid = EmergencyContactInput(
        name = "Nguyễn Văn An",
        relationship = "Bố",
        phone = "0901 234 567",
        email = "an@example.com",
    )

    @Test
    fun completeContactIsValid() {
        assertTrue(valid.validate().isValid)
        assertTrue(valid.copy(email = "").validate().isValid)
        assertTrue(valid.copy(phone = "+84 (90) 123-4567").validate().isValid)
    }

    @Test
    fun nameRelationshipAndPhoneAreRequired() {
        val errors = EmergencyContactInput(email = "a@b.vn").validate()
        assertEquals(ContactFieldError.REQUIRED, errors.name)
        assertEquals(ContactFieldError.REQUIRED, errors.relationship)
        assertEquals(ContactFieldError.REQUIRED, errors.phone)
        assertNull(errors.email)
    }

    @Test
    fun phoneMustBeADialableNumber() {
        assertEquals(ContactFieldError.INVALID, valid.copy(phone = "0901abc567").validate().phone)
        assertEquals(ContactFieldError.INVALID, valid.copy(phone = "115").validate().phone)
        assertEquals(ContactFieldError.INVALID, valid.copy(phone = "1234567890123456").validate().phone)
        assertEquals(ContactFieldError.INVALID, valid.copy(phone = "09+01234567").validate().phone)
        assertEquals(ContactFieldError.TOO_LONG, valid.copy(phone = "0".repeat(33)).validate().phone)
    }

    @Test
    fun emailIsOptionalButMustBeWellFormed() {
        assertEquals(ContactFieldError.INVALID, valid.copy(email = "an@example").validate().email)
        assertEquals(ContactFieldError.INVALID, valid.copy(email = "an example@x.com").validate().email)
        assertNull(valid.copy(email = "  ").validate().email)
    }

    @Test
    fun lengthLimitsFollowTheBackend() {
        assertEquals(ContactFieldError.TOO_LONG, valid.copy(name = "a".repeat(129)).validate().name)
        assertEquals(ContactFieldError.TOO_LONG, valid.copy(relationship = "a".repeat(65)).validate().relationship)
        assertNull(valid.copy(name = "a".repeat(128)).validate().name)
    }

    @Test
    fun blankRowsAreDroppedAndValuesNormalised() {
        val contacts = listOf(
            EmergencyContactInput(),
            valid.copy(name = "  An  ", phone = " +84 90-123.4567 ", email = " "),
        ).toContactsOrNull()

        assertEquals(
            listOf(EmergencyContactDto(name = "An", phone = "+84901234567", relationship = "Bố", email = null)),
            contacts,
        )
    }

    @Test
    fun anyInvalidFilledRowBlocksSaving() {
        assertNull(listOf(valid, valid.copy(phone = "")).toContactsOrNull())
    }

    @Test
    fun removingEveryContactSavesAnEmptyList() {
        assertEquals(emptyList<EmergencyContactDto>(), listOf(EmergencyContactInput()).toContactsOrNull())
    }

    @Test
    fun dtoRoundTripsThroughTheForm() {
        val dto = EmergencyContactDto(name = "Mẹ", phone = "0912345678", relationship = "Mẹ", email = null)
        assertEquals(listOf(dto), listOf(dto.toInput()).toContactsOrNull())
    }
}
