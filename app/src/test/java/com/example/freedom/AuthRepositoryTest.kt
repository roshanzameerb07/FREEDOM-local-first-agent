package com.example.freedom

import com.example.freedom.data.repository.AuthRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        authRepository = AuthRepository()
    }

    @org.junit.After
    fun tearDown() {
        authRepository.logout()
    }

    @Test
    fun `login with fixed demo credentials succeeds`() {
        val result = authRepository.login("ORG001", "WORKER001", "1234")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("ORG001", user?.organizationId)
        assertEquals("WORKER001", user?.workerId)
        assertTrue(authRepository.isLoggedIn())
    }

    @Test
    fun `login with hackathon demo credentials succeeds and establishes session`() {
        val result = authRepository.login("FREEDOM-DEMO-001", "DEMO-FIELD-01", "FreedomDemo@2026")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("FREEDOM-DEMO-001", user?.organizationId)
        assertEquals("DEMO-FIELD-01", user?.workerId)
        assertTrue(authRepository.isLoggedIn())

        // Verify SessionContext
        val session = com.example.freedom.framework.session.SessionManager.getCurrentSession()
        assertEquals("FREEDOM-DEMO-001", session.organizationId)
        assertEquals("DEMO-FIELD-01", session.currentUser?.userId)
        assertEquals(com.example.freedom.framework.security.UserRole.FIELD_WORKER, session.currentUser?.role)
    }

    @Test
    fun `login with empty credentials fails`() {
        val result = authRepository.login("", "", "")
        assertTrue(result.isFailure)
        assertFalse(authRepository.isLoggedIn())
    }
}
