package vn.uit.healthos.feature.profile

/** One editable emergency-contact row, holding the raw text the user typed. */
data class EmergencyContactInput(
    val name: String = "",
    val relationship: String = "",
    val phone: String = "",
    val email: String = "",
)

enum class ContactFieldError { REQUIRED, TOO_LONG, INVALID }

data class EmergencyContactErrors(
    val name: ContactFieldError? = null,
    val relationship: ContactFieldError? = null,
    val phone: ContactFieldError? = null,
    val email: ContactFieldError? = null,
) {
    val isValid: Boolean get() = name == null && relationship == null && phone == null && email == null
}

/** Rows beyond this are not offered: every contact gets its own SOS SMS. */
const val MAX_EMERGENCY_CONTACTS = 5

// Limits mirror the backend `EmergencyContact` schema (name 128, relationship 64, phone 32).
private const val NAME_MAX = 128
private const val RELATIONSHIP_MAX = 64
private const val PHONE_MAX = 32
private const val EMAIL_MAX = 254
private val PHONE_DIGITS = 8..15
private val PHONE_PATTERN = Regex("""^\+?[0-9 .()\-]+$""")
private val EMAIL_PATTERN = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$""")

/** A row with nothing typed in it; such rows are ignored on save. */
val EmergencyContactInput.isBlank: Boolean
    get() = name.isBlank() && relationship.isBlank() && phone.isBlank() && email.isBlank()

fun EmergencyContactInput.validate(): EmergencyContactErrors {
    val phoneText = phone.trim()
    val emailText = email.trim()
    return EmergencyContactErrors(
        name = requiredText(name, NAME_MAX),
        relationship = requiredText(relationship, RELATIONSHIP_MAX),
        phone = when {
            phoneText.isEmpty() -> ContactFieldError.REQUIRED
            phoneText.length > PHONE_MAX -> ContactFieldError.TOO_LONG
            !PHONE_PATTERN.matches(phoneText) || phoneText.count { it.isDigit() } !in PHONE_DIGITS ->
                ContactFieldError.INVALID
            else -> null
        },
        email = when {
            emailText.isEmpty() -> null
            emailText.length > EMAIL_MAX -> ContactFieldError.TOO_LONG
            !EMAIL_PATTERN.matches(emailText) -> ContactFieldError.INVALID
            else -> null
        },
    )
}

/**
 * The contacts to send to `PATCH /users/me`, or null when a filled row is invalid. Blank rows are dropped and the
 * phone keeps only digits and a leading `+`, so SMS gets a dialable number.
 */
fun List<EmergencyContactInput>.toContactsOrNull(): List<EmergencyContactDto>? {
    val filled = filterNot { it.isBlank }
    if (filled.any { !it.validate().isValid }) return null
    return filled.map { input ->
        EmergencyContactDto(
            name = input.name.trim(),
            phone = input.phone.trim().filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '+') },
            relationship = input.relationship.trim(),
            email = input.email.trim().ifEmpty { null },
        )
    }
}

fun EmergencyContactDto.toInput(): EmergencyContactInput = EmergencyContactInput(
    name = name,
    relationship = relationship,
    phone = phone,
    email = email.orEmpty(),
)

private fun requiredText(value: String, max: Int): ContactFieldError? {
    val text = value.trim()
    return when {
        text.isEmpty() -> ContactFieldError.REQUIRED
        text.length > max -> ContactFieldError.TOO_LONG
        else -> null
    }
}
