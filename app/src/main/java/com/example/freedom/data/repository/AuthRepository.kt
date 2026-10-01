package com.example.freedom.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthUser(
    val organizationId: String,
    val workerId: String,
    val workerName: String = "Field Officer #01"
)

class AuthRepository {

    companion object {
        const val DEMO_ORG_ID = "ORG001"
        const val DEMO_WORKER_ID = "WORKER001"
        const val DEMO_PASSWORD = "1234"

        // Dedicated Hackathon Demo Account (DEMO ONLY)
        const val HACKATHON_DEMO_ORG_ID = "FREEDOM-DEMO-001"
        const val HACKATHON_DEMO_WORKER_ID = "DEMO-FIELD-01"
        const val HACKATHON_DEMO_PASSWORD = "FreedomDemo@2026"
    }

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    fun login(orgId: String, workerId: String, password: String):Result<AuthUser> {
        val trimmedOrg = orgId.trim()
        val trimmedWorker = workerId.trim()
        val trimmedPassword = password.trim()

        if (trimmedOrg.isEmpty() || trimmedWorker.isEmpty() || trimmedPassword.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please fill in all credentials."))
        }

        // 1. Dedicated Hackathon Demo Account
        if (trimmedOrg.equals(HACKATHON_DEMO_ORG_ID, ignoreCase = true) &&
            trimmedWorker.equals(HACKATHON_DEMO_WORKER_ID, ignoreCase = true) &&
            trimmedPassword == HACKATHON_DEMO_PASSWORD
        ) {
            val user = AuthUser(organizationId = HACKATHON_DEMO_ORG_ID, workerId = HACKATHON_DEMO_WORKER_ID, workerName = "Demo Field Officer")
            _currentUser.value = user
            com.example.freedom.domain.model.WorkerProfileRepository.updateProfileFromAuth(HACKATHON_DEMO_ORG_ID, HACKATHON_DEMO_WORKER_ID, user.workerName)
            com.example.freedom.framework.FreedomFramework.onUserAuthenticated(HACKATHON_DEMO_ORG_ID, HACKATHON_DEMO_WORKER_ID, user.workerName, com.example.freedom.framework.security.UserRole.FIELD_WORKER)
            return Result.success(user)
        }

        // 2. Validate against fixed legacy demo credentials or accept standard worker IDs for flexibility
        if (trimmedOrg.equals(DEMO_ORG_ID, ignoreCase = true) &&
            trimmedWorker.equals(DEMO_WORKER_ID, ignoreCase = true) &&
            trimmedPassword == DEMO_PASSWORD
        ) {
            val user = AuthUser(organizationId = DEMO_ORG_ID, workerId = DEMO_WORKER_ID, workerName = "Ramesh K. (Field Officer)")
            _currentUser.value = user
            com.example.freedom.domain.model.WorkerProfileRepository.updateProfileFromAuth(DEMO_ORG_ID, DEMO_WORKER_ID, user.workerName)
            com.example.freedom.framework.FreedomFramework.onUserAuthenticated(DEMO_ORG_ID, DEMO_WORKER_ID, user.workerName, com.example.freedom.framework.security.UserRole.FIELD_WORKER)
            return Result.success(user)
        }

        // Allow any valid non-empty input for test/demo flexibility if matching minimum lengths
        if (trimmedOrg.length >= 3 && trimmedWorker.length >= 3 && trimmedPassword.isNotEmpty()) {
            val user = AuthUser(organizationId = trimmedOrg, workerId = trimmedWorker, workerName = "Officer ($trimmedWorker)")
            _currentUser.value = user
            com.example.freedom.domain.model.WorkerProfileRepository.updateProfileFromAuth(trimmedOrg, trimmedWorker, user.workerName)
            com.example.freedom.framework.FreedomFramework.onUserAuthenticated(trimmedOrg, trimmedWorker, user.workerName)
            return Result.success(user)
        }

        return Result.failure(IllegalArgumentException("Invalid credentials. Try demo: ORG001 / WORKER001 / 1234"))
    }

    fun logout() {
        _currentUser.value = null
        com.example.freedom.framework.FreedomFramework.onUserLogout()
    }

    fun isLoggedIn(): Boolean = _currentUser.value != null
}
