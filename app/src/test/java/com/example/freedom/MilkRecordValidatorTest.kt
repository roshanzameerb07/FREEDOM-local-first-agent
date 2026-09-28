package com.example.freedom

import com.example.freedom.domain.validation.MilkRecordValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MilkRecordValidatorTest {

    private lateinit var validator: MilkRecordValidator

    @Before
    fun setUp() {
        validator = MilkRecordValidator()
    }

    @Test
    fun `validate valid record succeeds`() {
        val result = validator.validate(
            farmerName = "Ramesh",
            quantityStr = "18.0",
            fatStr = "4.2",
            snfStr = "8.6",
            paymentStatus = "PENDING"
        )

        assertTrue(result.isValid)
        assertNull(result.farmerNameError)
        assertNull(result.quantityError)
        assertNull(result.fatError)
        assertNull(result.snfError)
        assertNull(result.paymentStatusError)
    }

    @Test
    fun `validate empty farmer name fails`() {
        val result = validator.validate(
            farmerName = "   ",
            quantityStr = "18.0",
            fatStr = "4.2",
            snfStr = "8.6",
            paymentStatus = "PAID"
        )

        assertFalse(result.isValid)
        assertNotNull(result.farmerNameError)
        assertEquals("Farmer name cannot be empty", result.farmerNameError)
    }

    @Test
    fun `validate non-numeric or non-positive quantity fails`() {
        val zeroQtyResult = validator.validate(
            farmerName = "Suresh",
            quantityStr = "0.0",
            fatStr = "4.0",
            snfStr = "8.5",
            paymentStatus = "PAID"
        )
        assertFalse(zeroQtyResult.isValid)
        assertEquals("Quantity must be greater than 0 L", zeroQtyResult.quantityError)

        val invalidQtyResult = validator.validate(
            farmerName = "Suresh",
            quantityStr = "abc",
            fatStr = "4.0",
            snfStr = "8.5",
            paymentStatus = "PAID"
        )
        assertFalse(invalidQtyResult.isValid)
        assertEquals("Quantity must be a valid number", invalidQtyResult.quantityError)
    }

    @Test
    fun `validate out-of-range fat and snf fails`() {
        val highFatResult = validator.validate(
            farmerName = "Mahesh",
            quantityStr = "20.0",
            fatStr = "25.0",
            snfStr = "8.7",
            paymentStatus = "PENDING"
        )
        assertFalse(highFatResult.isValid)
        assertEquals("Fat must be between 0.0% and 15.0%", highFatResult.fatError)

        val negativeSnfResult = validator.validate(
            farmerName = "Mahesh",
            quantityStr = "20.0",
            fatStr = "4.3",
            snfStr = "-1.0",
            paymentStatus = "PENDING"
        )
        assertFalse(negativeSnfResult.isValid)
        assertEquals("SNF must be between 0.0% and 15.0%", negativeSnfResult.snfError)
    }

    @Test
    fun `validate invalid payment status fails`() {
        val result = validator.validate(
            farmerName = "Ramesh",
            quantityStr = "18.0",
            fatStr = "4.2",
            snfStr = "8.6",
            paymentStatus = "UNKNOWN"
        )

        assertFalse(result.isValid)
        assertNotNull(result.paymentStatusError)
    }
}
