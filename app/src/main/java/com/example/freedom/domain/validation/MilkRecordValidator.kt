package com.example.freedom.domain.validation

data class MilkRecordValidationResult(
    val isValid: Boolean,
    val farmerNameError: String? = null,
    val quantityError: String? = null,
    val fatError: String? = null,
    val snfError: String? = null,
    val paymentStatusError: String? = null
)

class MilkRecordValidator {

    fun validate(
        farmerName: String,
        quantityStr: String,
        fatStr: String,
        snfStr: String,
        paymentStatus: String
    ): MilkRecordValidationResult {
        var farmerNameError: String? = null
        var quantityError: String? = null
        var fatError: String? = null
        var snfError: String? = null
        var paymentStatusError: String? = null

        // 1. Farmer Name validation
        val trimmedName = farmerName.trim()
        if (trimmedName.isEmpty()) {
            farmerNameError = "Farmer name cannot be empty"
        } else if (trimmedName.length < 2) {
            farmerNameError = "Farmer name must be at least 2 characters"
        }

        // 2. Quantity validation
        val trimmedQuantity = quantityStr.trim()
        if (trimmedQuantity.isEmpty()) {
            quantityError = "Quantity is required"
        } else {
            val qty = trimmedQuantity.toDoubleOrNull()
            if (qty == null) {
                quantityError = "Quantity must be a valid number"
            } else if (qty <= 0.0) {
                quantityError = "Quantity must be greater than 0 L"
            } else if (qty > 1000.0) {
                quantityError = "Quantity exceeds maximum allowable single entry (1000 L)"
            }
        }

        // 3. Fat validation
        val trimmedFat = fatStr.trim()
        if (trimmedFat.isEmpty()) {
            fatError = "Fat percentage is required"
        } else {
            val fat = trimmedFat.toDoubleOrNull()
            if (fat == null) {
                fatError = "Fat must be a valid number"
            } else if (fat < 0.0 || fat > 15.0) {
                fatError = "Fat must be between 0.0% and 15.0%"
            }
        }

        // 4. SNF validation
        val trimmedSnf = snfStr.trim()
        if (trimmedSnf.isEmpty()) {
            snfError = "SNF percentage is required"
        } else {
            val snf = trimmedSnf.toDoubleOrNull()
            if (snf == null) {
                snfError = "SNF must be a valid number"
            } else if (snf < 0.0 || snf > 15.0) {
                snfError = "SNF must be between 0.0% and 15.0%"
            }
        }

        // 5. Payment Status validation
        val validStatuses = listOf("PENDING", "PAID", "RECORDED_LOCALLY", "COMPLETE")
        if (paymentStatus.trim().uppercase() !in validStatuses) {
            paymentStatusError = "Please select a payment status (Pending or Paid)"
        }

        val isValid = farmerNameError == null &&
                quantityError == null &&
                fatError == null &&
                snfError == null &&
                paymentStatusError == null

        return MilkRecordValidationResult(
            isValid = isValid,
            farmerNameError = farmerNameError,
            quantityError = quantityError,
            fatError = fatError,
            snfError = snfError,
            paymentStatusError = paymentStatusError
        )
    }
}
