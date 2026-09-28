package com.example.freedom

import com.example.freedom.domain.rag.LocalRagRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalRagRetrieverTest {

    private lateinit var retriever: LocalRagRetriever

    @Before
    fun setUp() {
        retriever = LocalRagRetriever()
    }

    @Test
    fun `search finds payment completion policy with high confidence`() {
        val result = retriever.search("When is payment considered complete?")
        assertTrue(result.hasEvidence)
        assertNotNull(result.topDocument)
        assertEquals("KNOW-PAY-01", result.topDocument!!.id)
        assertEquals("Clause 4.2 (Page 14)", result.topDocument!!.clauseOrPage)
        assertTrue(result.topDocument!!.content.contains("Payment is considered complete only after"))
    }

    @Test
    fun `search finds milk quality standards`() {
        val result = retriever.search("What are the minimum fat and SNF quality standards?")
        assertTrue(result.hasEvidence)
        assertNotNull(result.topDocument)
        assertEquals("KNOW-QUAL-01", result.topDocument!!.id)
        assertTrue(result.topDocument!!.content.contains("Fat of 3.2%"))
    }

    @Test
    fun `search finds rejected milk protocol`() {
        val result = retriever.search("What is the procedure for rejected or spoiled milk?")
        assertTrue(result.hasEvidence)
        assertNotNull(result.topDocument)
        assertEquals("KNOW-REJ-01", result.topDocument!!.id)
        assertTrue(result.topDocument!!.content.contains("rejection slip"))
    }

    @Test
    fun `search returns no evidence for completely irrelevant or unanswerable queries`() {
        val result = retriever.search("What is the capital of France and who won the World Cup?")
        assertFalse("Irrelevant query must yield no evidence to prevent hallucination", result.hasEvidence)
        assertNull(result.topDocument)
    }

    @Test
    fun `search handles empty or blank queries safely`() {
        val emptyResult = retriever.search("")
        assertFalse(emptyResult.hasEvidence)
        assertNull(emptyResult.topDocument)

        val blankResult = retriever.search("   ")
        assertFalse(blankResult.hasEvidence)
        assertNull(blankResult.topDocument)
    }
}
