package com.example.freedom

import com.example.freedom.domain.ai.PatternBasedVoiceExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatternBasedVoiceExtractorTest {

    private lateinit var extractor: PatternBasedVoiceExtractor

    @Before
    fun setUp() {
        extractor = PatternBasedVoiceExtractor()
    }

    @Test
    fun `extract standard milk collection prompt successfully`() = runTest {
        val transcript = "Ramesh gave 18 litres, fat 4.2 and SNF 8.6. Payment is pending."
        val draft = extractor.extractRecordFromTranscript(transcript)

        assertEquals("Ramesh", draft.farmerName)
        assertEquals("18", draft.quantity)
        assertEquals("4.2", draft.fat)
        assertEquals("8.6", draft.snf)
        assertEquals("PENDING", draft.paymentStatus)
    }

    @Test
    fun `extract paid prompt successfully`() = runTest {
        val transcript = "Suresh delivered 12 L, fat 4.0 and SNF 8.5. Payment is paid."
        val draft = extractor.extractRecordFromTranscript(transcript)

        assertEquals("Suresh", draft.farmerName)
        assertEquals("12", draft.quantity)
        assertEquals("4.0", draft.fat)
        assertEquals("8.5", draft.snf)
        assertEquals("PAID", draft.paymentStatus)
    }
}
