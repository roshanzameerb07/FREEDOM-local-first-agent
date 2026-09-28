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
    fun `login with empty credentials fails`() {
        val result = authRepository.login("", "", "")
        assertTrue(result.isFailure)
        assertFalse(authRepository.isLoggedIn())
    }
}
