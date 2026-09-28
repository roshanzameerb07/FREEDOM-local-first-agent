package com.example.freedom

import com.example.freedom.domain.ai.NumberFidelityReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberFidelityReconcilerTest {

    @Test
    fun `reconcile restores exact decimals when model output is rounded or truncated`() {
        val userInput = "Ramesh gave 18.5 litres, fat 4.2 and SNF 8.6. Payment is pending."
        // Model truncated 4.2 to 4, 8.6 to 8, 18.5 to 18
        val modelExtractedArgs = mapOf(
            "farmerName" to "Ramesh",
            "quantity" to "18",
            "fat" to "4",
            "snf" to "8",
            "paymentStatus" to "pending"
        )

        val reconciled = NumberFidelityReconciler.reconcile(userInput, modelExtractedArgs)

        assertEquals("18.5", reconciled["quantity"])
        assertEquals("4.2", reconciled["fat"])
        assertEquals("8.6", reconciled["snf"])
        assertEquals("Ramesh", reconciled["farmerName"])
        assertEquals("PENDING", reconciled["paymentStatus"]?.uppercase())
    }

    @Test
    fun `reconcile distinguishes missing fields from 0 or 0_0`() {
        val userInput = "Ramesh gave 80 litres."
        val modelExtractedArgs = mapOf(
            "farmerName" to "Ramesh",
            "quantity" to "80"
        )

        val reconciled = NumberFidelityReconciler.reconcile(userInput, modelExtractedArgs)

        assertEquals("80", reconciled["quantity"])
        assertEquals("Ramesh", reconciled["farmerName"])
        assertNull("Missing fat must remain null/absent, not 0.0", reconciled["fat"])
        assertNull("Missing snf must remain null/absent, not 0.0", reconciled["snf"])
    }

    @Test
    fun `reconcile removes fabricated 0 or 0_0 when field is absent from user input`() {
        val userInput = "Ramesh gave 80 litres."
        // Malicious or confused model hallucinated 0 or 0.0 for missing fields
        val modelExtractedArgs = mapOf(
            "farmerName" to "Ramesh",
            "quantity" to "80",
            "fat" to "0.0",
            "snf" to "0"
        )

        val reconciled = NumberFidelityReconciler.reconcile(userInput, modelExtractedArgs)

        assertEquals("80", reconciled["quantity"])
        assertFalse("Fabricated fat=0.0 must be purged", reconciled.containsKey("fat"))
        assertFalse("Fabricated snf=0 must be purged", reconciled.containsKey("snf"))
    }

    @Test
    fun `extractQuantityFromText handles multiple formats`() {
        assertEquals("18.5", NumberFidelityReconciler.extractQuantityFromText("Ramesh gave 18.5 litres"))
        assertEquals("22", NumberFidelityReconciler.extractQuantityFromText("Suresh gave 22 liters"))
        assertEquals("5.75", NumberFidelityReconciler.extractQuantityFromText("Mahesh 5.75 L"))
        assertEquals("14", NumberFidelityReconciler.extractQuantityFromText("14 ltr collected"))
    }

    @Test
    fun `extractFatFromText and extractSnfFromText capture percentages correctly`() {
        val text = "Ramesh gave 18 litres, fat: 4.25 and snf: 8.65"
        assertEquals("4.25", NumberFidelityReconciler.extractFatFromText(text))
        assertEquals("8.65", NumberFidelityReconciler.extractSnfFromText(text))
    }
}
