package com.conferbot.sdk.utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Comprehensive validation utilities for the Conferbot Android SDK.
 * These validation methods match the validation logic used in the web widget
 * to ensure consistent behavior across platforms.
 */
object ValidationUtils {

    // ============================================
    // EMAIL VALIDATION
    // ============================================

    /**
     * Email validation regex pattern.
     * Matches the pattern used in the web widget:
     * - Local part: allows letters, numbers, and special characters
     * - Domain: allows letters, numbers, hyphens, and IP addresses in brackets
     * - TLD: requires at least 2 characters
     */
    private val EMAIL_PATTERN = Pattern.compile(
        "^(([^<>()\\[\\]\\\\.,;:\\s@\"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@\"]+)*)|(\".+\"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Validates an email address.
     * Matches the _validateEmailHelper function from the web widget.
     *
     * @param email The email address to validate
     * @return true if the email is valid, false otherwise
     */
    @JvmStatic
    fun isValidEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_PATTERN.matcher(email.trim().lowercase()).matches()
    }

    /**
     * Validates an email address with detailed result.
     *
     * @param email The email address to validate
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateEmail(email: String?): ValidationResult {
        return when {
            email.isNullOrBlank() -> ValidationResult(false, "Email address is required")
            !EMAIL_PATTERN.matcher(email.trim().lowercase()).matches() ->
                ValidationResult(false, "Please enter a valid email address")
            else -> ValidationResult(true)
        }
    }

    // ============================================
    // PHONE NUMBER VALIDATION
    // ============================================

    /**
     * Phone number validation regex pattern.
     * Matches the pattern used in the web widget:
     * - Requires 6-15 digits only
     */
    private val PHONE_PATTERN_BASIC = Pattern.compile("^\\d{6,15}$")

    /**
     * International phone number pattern with optional country code.
     * More flexible pattern for international formats:
     * - Optional + prefix for country code
     * - Allows spaces, dashes, parentheses for formatting
     * - Requires minimum 6 digits, maximum 15 digits (E.164 standard)
     */
    private val PHONE_PATTERN_INTERNATIONAL = Pattern.compile(
        "^\\+?[0-9]{1,4}?[-.\\s]?\\(?[0-9]{1,3}?\\)?[-.\\s]?[0-9]{1,4}[-.\\s]?[0-9]{1,4}[-.\\s]?[0-9]{1,9}$"
    )

    /**
     * Validates a phone number using basic validation (digits only).
     * Matches the _validatePhoneNumberHelper function from the web widget.
     *
     * @param phoneNumber The phone number to validate
     * @return true if the phone number is valid, false otherwise
     */
    @JvmStatic
    fun isValidPhoneNumber(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        // Remove all non-digit characters for basic validation (matching web widget behavior)
        val digitsOnly = phoneNumber.trim().replace(Regex("[^0-9]"), "")
        return PHONE_PATTERN_BASIC.matcher(digitsOnly).matches()
    }

    /**
     * Validates a phone number with international format support.
     *
     * @param phoneNumber The phone number to validate
     * @param allowInternational Whether to allow international format with + prefix
     * @return true if the phone number is valid, false otherwise
     */
    @JvmStatic
    fun isValidPhoneNumber(phoneNumber: String?, allowInternational: Boolean): Boolean {
        if (phoneNumber.isNullOrBlank()) return false

        return if (allowInternational) {
            // Check international format first
            if (PHONE_PATTERN_INTERNATIONAL.matcher(phoneNumber.trim()).matches()) {
                // Also verify digit count is within E.164 limits
                val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
                digitsOnly.length in 6..15
            } else {
                false
            }
        } else {
            isValidPhoneNumber(phoneNumber)
        }
    }

    /**
     * Validates a phone number with detailed result.
     *
     * @param phoneNumber The phone number to validate
     * @param allowInternational Whether to allow international format
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validatePhoneNumber(phoneNumber: String?, allowInternational: Boolean = true): ValidationResult {
        return when {
            phoneNumber.isNullOrBlank() -> ValidationResult(false, "Phone number is required")
            !isValidPhoneNumber(phoneNumber, allowInternational) ->
                ValidationResult(false, "Please enter a valid phone number")
            else -> ValidationResult(true)
        }
    }

    // ============================================
    // URL VALIDATION
    // ============================================

    /**
     * URL validation regex pattern.
     * Matches the pattern used in the web widget:
     * - Optional http:// or https:// protocol
     * - Domain with TLD (2-6 characters)
     * - Optional path
     */
    private val URL_PATTERN = Pattern.compile(
        "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * More comprehensive URL pattern for stricter validation.
     */
    private val URL_PATTERN_STRICT = Pattern.compile(
        "^(https?://)?([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(/[\\w\\-.~:/?#\\[\\]@!$&'()*+,;=]*)?$",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Validates a URL.
     * Matches the _validateUrlHelper function from the web widget.
     *
     * @param url The URL to validate
     * @return true if the URL is valid, false otherwise
     */
    @JvmStatic
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return URL_PATTERN.matcher(url.trim().lowercase()).matches()
    }

    /**
     * Validates a URL with strict mode option.
     *
     * @param url The URL to validate
     * @param strictMode Whether to use stricter validation
     * @return true if the URL is valid, false otherwise
     */
    @JvmStatic
    fun isValidUrl(url: String?, strictMode: Boolean): Boolean {
        if (url.isNullOrBlank()) return false

        return if (strictMode) {
            URL_PATTERN_STRICT.matcher(url.trim()).matches()
        } else {
            isValidUrl(url)
        }
    }

    /**
     * Validates a URL with detailed result.
     *
     * @param url The URL to validate
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateUrl(url: String?): ValidationResult {
        return when {
            url.isNullOrBlank() -> ValidationResult(false, "URL is required")
            !isValidUrl(url) -> ValidationResult(false, "Please enter a valid URL")
            else -> ValidationResult(true)
        }
    }

    // ============================================
    // NUMBER VALIDATION
    // ============================================

    /**
     * Validates if a string is a valid number.
     *
     * @param value The string to validate
     * @return true if the string is a valid number, false otherwise
     */
    @JvmStatic
    fun isValidNumber(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.trim().toDoubleOrNull() != null
    }

    /**
     * Validates if a string is a valid integer.
     *
     * @param value The string to validate
     * @return true if the string is a valid integer, false otherwise
     */
    @JvmStatic
    fun isValidInteger(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.trim().toIntOrNull() != null
    }

    /**
     * Validates if a number is within a specified range.
     *
     * @param value The string value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return true if the number is valid and within range, false otherwise
     */
    @JvmStatic
    fun isValidNumberInRange(value: String?, min: Double, max: Double): Boolean {
        if (value.isNullOrBlank()) return false
        val number = value.trim().toDoubleOrNull() ?: return false
        return number in min..max
    }

    /**
     * Validates if an integer is within a specified range.
     *
     * @param value The string value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @return true if the integer is valid and within range, false otherwise
     */
    @JvmStatic
    fun isValidIntegerInRange(value: String?, min: Int, max: Int): Boolean {
        if (value.isNullOrBlank()) return false
        val number = value.trim().toIntOrNull() ?: return false
        return number in min..max
    }

    /**
     * Validates a number with detailed result.
     *
     * @param value The string value to validate
     * @param min Optional minimum value
     * @param max Optional maximum value
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateNumber(value: String?, min: Double? = null, max: Double? = null): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult(false, "Number is required")
        }

        val number = value.trim().toDoubleOrNull()
            ?: return ValidationResult(false, "Please enter a valid number")

        if (min != null && number < min) {
            return ValidationResult(false, "Number must be at least $min")
        }

        if (max != null && number > max) {
            return ValidationResult(false, "Number must be at most $max")
        }

        return ValidationResult(true)
    }

    // ============================================
    // DATE/TIME VALIDATION
    // ============================================

    /**
     * Common date format patterns.
     */
    object DateFormats {
        const val ISO_DATE = "yyyy-MM-dd"
        const val ISO_DATETIME = "yyyy-MM-dd'T'HH:mm:ss"
        const val ISO_DATETIME_WITH_TIMEZONE = "yyyy-MM-dd'T'HH:mm:ssZ"
        const val US_DATE = "MM/dd/yyyy"
        const val EU_DATE = "dd/MM/yyyy"
        const val DISPLAY_DATE = "MMM dd, yyyy"
        const val DISPLAY_DATETIME = "MMM dd, yyyy HH:mm"
        const val TIME_12H = "hh:mm a"
        const val TIME_24H = "HH:mm"
    }

    /**
     * Validates if a string is a valid date in the specified format.
     *
     * @param dateString The date string to validate
     * @param format The expected date format (default: ISO date format)
     * @return true if the date string is valid, false otherwise
     */
    @JvmStatic
    fun isValidDate(dateString: String?, format: String = DateFormats.ISO_DATE): Boolean {
        if (dateString.isNullOrBlank()) return false

        return try {
            val sdf = SimpleDateFormat(format, Locale.US)
            sdf.isLenient = false
            sdf.parse(dateString.trim())
            true
        } catch (e: ParseException) {
            false
        }
    }

    /**
     * Validates if a string is a valid time in the specified format.
     *
     * @param timeString The time string to validate
     * @param format The expected time format (default: 24-hour format)
     * @return true if the time string is valid, false otherwise
     */
    @JvmStatic
    fun isValidTime(timeString: String?, format: String = DateFormats.TIME_24H): Boolean {
        if (timeString.isNullOrBlank()) return false

        return try {
            val sdf = SimpleDateFormat(format, Locale.US)
            sdf.isLenient = false
            sdf.parse(timeString.trim())
            true
        } catch (e: ParseException) {
            false
        }
    }

    /**
     * Validates if a date is within a specified range.
     *
     * @param dateString The date string to validate
     * @param format The expected date format
     * @param minDate The minimum allowed date (inclusive)
     * @param maxDate The maximum allowed date (inclusive)
     * @return true if the date is valid and within range, false otherwise
     */
    @JvmStatic
    fun isValidDateInRange(
        dateString: String?,
        format: String = DateFormats.ISO_DATE,
        minDate: Date? = null,
        maxDate: Date? = null
    ): Boolean {
        if (dateString.isNullOrBlank()) return false

        return try {
            val sdf = SimpleDateFormat(format, Locale.US)
            sdf.isLenient = false
            val date = sdf.parse(dateString.trim()) ?: return false

            if (minDate != null && date.before(minDate)) return false
            if (maxDate != null && date.after(maxDate)) return false

            true
        } catch (e: ParseException) {
            false
        }
    }

    /**
     * Validates a date with detailed result.
     *
     * @param dateString The date string to validate
     * @param format The expected date format
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateDate(dateString: String?, format: String = DateFormats.ISO_DATE): ValidationResult {
        return when {
            dateString.isNullOrBlank() -> ValidationResult(false, "Date is required")
            !isValidDate(dateString, format) ->
                ValidationResult(false, "Please enter a valid date")
            else -> ValidationResult(true)
        }
    }

    /**
     * Parses a date string to Date object.
     *
     * @param dateString The date string to parse
     * @param format The expected date format
     * @return Date object if parsing succeeds, null otherwise
     */
    @JvmStatic
    fun parseDate(dateString: String?, format: String = DateFormats.ISO_DATE): Date? {
        if (dateString.isNullOrBlank()) return null

        return try {
            val sdf = SimpleDateFormat(format, Locale.US)
            sdf.isLenient = false
            sdf.parse(dateString.trim())
        } catch (e: ParseException) {
            null
        }
    }

    /**
     * Formats a Date object to string.
     *
     * @param date The Date object to format
     * @param format The desired date format
     * @return Formatted date string
     */
    @JvmStatic
    fun formatDate(date: Date, format: String = DateFormats.ISO_DATE): String {
        val sdf = SimpleDateFormat(format, Locale.US)
        return sdf.format(date)
    }

    // ============================================
    // REQUIRED FIELD VALIDATION
    // ============================================

    /**
     * Validates that a field is not empty or blank.
     *
     * @param value The value to validate
     * @return true if the value is not empty or blank, false otherwise
     */
    @JvmStatic
    fun isNotEmpty(value: String?): Boolean {
        return !value.isNullOrBlank()
    }

    /**
     * Validates that a field is not null.
     *
     * @param value The value to validate
     * @return true if the value is not null, false otherwise
     */
    @JvmStatic
    fun isNotNull(value: Any?): Boolean {
        return value != null
    }

    /**
     * Validates a required field with detailed result.
     *
     * @param value The value to validate
     * @param fieldName The name of the field for the error message
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateRequired(value: String?, fieldName: String = "This field"): ValidationResult {
        return if (value.isNullOrBlank()) {
            ValidationResult(false, "$fieldName is required")
        } else {
            ValidationResult(true)
        }
    }

    /**
     * Validates field length.
     *
     * @param value The value to validate
     * @param minLength Minimum length (inclusive)
     * @param maxLength Maximum length (inclusive)
     * @return true if the length is within range, false otherwise
     */
    @JvmStatic
    fun isValidLength(value: String?, minLength: Int = 0, maxLength: Int = Int.MAX_VALUE): Boolean {
        if (value == null) return minLength == 0
        return value.length in minLength..maxLength
    }

    /**
     * Validates field length with detailed result.
     *
     * @param value The value to validate
     * @param minLength Minimum length (inclusive)
     * @param maxLength Maximum length (inclusive)
     * @param fieldName The name of the field for the error message
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateLength(
        value: String?,
        minLength: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
        fieldName: String = "This field"
    ): ValidationResult {
        if (value == null) {
            return if (minLength == 0) {
                ValidationResult(true)
            } else {
                ValidationResult(false, "$fieldName must be at least $minLength characters")
            }
        }

        return when {
            value.length < minLength ->
                ValidationResult(false, "$fieldName must be at least $minLength characters")
            value.length > maxLength ->
                ValidationResult(false, "$fieldName must be at most $maxLength characters")
            else -> ValidationResult(true)
        }
    }

    // ============================================
    // CUSTOM PATTERN VALIDATION
    // ============================================

    /**
     * Validates a value against a custom regex pattern.
     *
     * @param value The value to validate
     * @param pattern The regex pattern to match
     * @return true if the value matches the pattern, false otherwise
     */
    @JvmStatic
    fun matchesPattern(value: String?, pattern: String): Boolean {
        if (value.isNullOrBlank()) return false
        return Pattern.compile(pattern).matcher(value).matches()
    }

    /**
     * Validates a value against a custom regex pattern.
     *
     * @param value The value to validate
     * @param pattern The compiled Pattern to match
     * @return true if the value matches the pattern, false otherwise
     */
    @JvmStatic
    fun matchesPattern(value: String?, pattern: Pattern): Boolean {
        if (value.isNullOrBlank()) return false
        return pattern.matcher(value).matches()
    }

    // ============================================
    // VALIDATION RESULT CLASS
    // ============================================

    /**
     * Data class representing the result of a validation operation.
     *
     * @property isValid Whether the validation passed
     * @property errorMessage The error message if validation failed, null otherwise
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        /**
         * Returns the error message if invalid, or the default value if valid.
         */
        fun getErrorOrDefault(default: String = ""): String {
            return errorMessage ?: default
        }
    }

    // ============================================
    // COMPOSITE VALIDATION
    // ============================================

    /**
     * Validates multiple conditions and returns the first error.
     *
     * @param validations Vararg of ValidationResult objects
     * @return The first failed ValidationResult, or a success result if all pass
     */
    @JvmStatic
    fun validateAll(vararg validations: ValidationResult): ValidationResult {
        for (validation in validations) {
            if (!validation.isValid) {
                return validation
            }
        }
        return ValidationResult(true)
    }

    /**
     * Validates a value based on its type/purpose.
     *
     * @param value The value to validate
     * @param fieldType The type of field (email, phone, url, number, date, text)
     * @param required Whether the field is required
     * @return ValidationResult containing validity and error message if invalid
     */
    @JvmStatic
    fun validateByType(
        value: String?,
        fieldType: String,
        required: Boolean = true
    ): ValidationResult {
        // Check required first
        if (required && value.isNullOrBlank()) {
            return ValidationResult(false, "This field is required")
        }

        // If not required and empty, it's valid
        if (!required && value.isNullOrBlank()) {
            return ValidationResult(true)
        }

        // Validate based on type
        return when (fieldType.lowercase()) {
            "email" -> validateEmail(value)
            "phone", "mobile", "telephone" -> validatePhoneNumber(value)
            "url", "website", "link" -> validateUrl(value)
            "number", "numeric", "integer" -> validateNumber(value)
            "date" -> validateDate(value)
            "text", "name" -> validateRequired(value)
            else -> validateRequired(value)
        }
    }
}
