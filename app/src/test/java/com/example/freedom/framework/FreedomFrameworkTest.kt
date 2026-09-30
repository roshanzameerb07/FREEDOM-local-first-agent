package com.example.freedom.framework

import com.example.freedom.domain.query.AggregationSpec
import com.example.freedom.domain.query.AggregationType
import com.example.freedom.domain.query.FreedomQuery
import com.example.freedom.domain.query.QueryTarget
import com.example.freedom.domain.query.RequestType
import com.example.freedom.domain.query.SchemaField
import com.example.freedom.framework.model.ModelInfo
import com.example.freedom.framework.model.ModelProvider
import com.example.freedom.framework.model.ModelProviderRegistry
import com.example.freedom.framework.organization.ActivationState
import com.example.freedom.framework.organization.ActivationStatus
import com.example.freedom.framework.organization.MilkCollectionProfile
import com.example.freedom.framework.organization.ModelProviderType
import com.example.freedom.framework.organization.OrganizationProfile
import com.example.freedom.framework.organization.OrganizationRegistry
import com.example.freedom.framework.organization.OrganizationType
import com.example.freedom.framework.organization.QueryCapability
import com.example.freedom.framework.security.AuthorizationPolicy
import com.example.freedom.framework.security.OrganizationUser
import com.example.freedom.framework.security.Permission
import com.example.freedom.framework.security.UserRole
import com.example.freedom.framework.session.SessionContext
import com.example.freedom.framework.session.SessionManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for the FREEDOM Framework.
 *
 * Tests organization identity, profiles, user roles, authorization policy,
 * model provider abstraction, session management, and data isolation.
 */
class FreedomFrameworkTest {

    @Before
    fun setUp() {
        // Clean state for each test
        OrganizationRegistry.clearAll()
        ModelProviderRegistry.clearAll()
        SessionManager.clearForTesting()
        FreedomFramework.resetForTesting()
    }

    @After
    fun tearDown() {
        OrganizationRegistry.clearAll()
        ModelProviderRegistry.clearAll()
        SessionManager.clearForTesting()
        FreedomFramework.resetForTesting()
    }

    // ═══════════════════════════════════════════════════════════════
    // Organization Identity Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `organization profile creation with valid data`() {
        val profile = MilkCollectionProfile.createDefault()
        assertEquals("ORG001", profile.organizationId)
        assertEquals("milk_collection", profile.domain)
        assertEquals(OrganizationType.COOPERATIVE, profile.organizationType)
        assertTrue(profile.enabledEntities.contains("FARMER"))
        assertTrue(profile.enabledEntities.contains("MILK_RECORD"))
        assertTrue(profile.enabledFields.contains("QUANTITY"))
        assertTrue(profile.enabledFields.contains("FAT"))
        assertTrue(profile.isActive)
    }

    @Test
    fun `organization registry register and retrieve`() {
        val profile = MilkCollectionProfile.createDefault()
        OrganizationRegistry.register(profile)
        OrganizationRegistry.setActiveProfile(profile.organizationId)

        val retrieved = OrganizationRegistry.getActiveProfile()
        assertEquals(profile.organizationId, retrieved.organizationId)
        assertEquals(profile.organizationName, retrieved.organizationName)
    }

    @Test(expected = IllegalStateException::class)
    fun `organization registry throws when no active profile`() {
        OrganizationRegistry.getActiveProfile()
    }

    @Test(expected = IllegalStateException::class)
    fun `organization registry throws for unregistered profile activation`() {
        OrganizationRegistry.setActiveProfile("NONEXISTENT")
    }

    @Test
    fun `multiple organizations can be registered`() {
        val profile1 = MilkCollectionProfile.createDefault()
        val profile2 = OrganizationProfile(
            organizationId = "ORG002",
            organizationName = "Test Warehouse Corp",
            domain = "warehouse",
            organizationType = OrganizationType.ENTERPRISE,
            enabledEntities = setOf("INVENTORY", "SHIPMENT")
        )

        OrganizationRegistry.register(profile1)
        OrganizationRegistry.register(profile2)

        assertEquals(2, OrganizationRegistry.listRegisteredIds().size)
        assertNotNull(OrganizationRegistry.getProfile("ORG001"))
        assertNotNull(OrganizationRegistry.getProfile("ORG002"))
    }

    // ═══════════════════════════════════════════════════════════════
    // Organization Profile Validation Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `milk collection profile has correct entity set`() {
        val profile = MilkCollectionProfile.createDefault()
        assertEquals(setOf("FARMER", "MILK_RECORD"), profile.enabledEntities)
    }

    @Test
    fun `milk collection profile has correct field set`() {
        val profile = MilkCollectionProfile.createDefault()
        assertTrue(profile.enabledFields.containsAll(
            setOf("QUANTITY", "FAT", "SNF", "PAYMENT_STATUS", "PAYMENT_METHOD",
                  "PAYMENT_REFERENCE", "PAYABLE_AMOUNT", "AMOUNT_PAID")
        ))
    }

    @Test
    fun `query capabilities default includes all capabilities`() {
        val profile = MilkCollectionProfile.createDefault()
        assertEquals(QueryCapability.entries.toSet(), profile.enabledQueryCapabilities)
    }

    // ═══════════════════════════════════════════════════════════════
    // Activation State Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `activation state valid when active and not expired`() {
        val activation = MilkCollectionProfile.createDefaultActivation()
        assertTrue(activation.isValid())
        assertEquals(ActivationStatus.ACTIVE, activation.status)
    }

    @Test
    fun `activation state invalid when expired`() {
        val activation = ActivationState(
            organizationId = "ORG001",
            deviceId = "DEVICE-1",
            status = ActivationStatus.ACTIVE,
            expiresAt = System.currentTimeMillis() - 1000 // Already expired
        )
        assertFalse(activation.isValid())
    }

    @Test
    fun `activation state invalid when revoked`() {
        val activation = ActivationState(
            organizationId = "ORG001",
            deviceId = "DEVICE-1",
            status = ActivationStatus.REVOKED
        )
        assertFalse(activation.isValid())
    }

    // ═══════════════════════════════════════════════════════════════
    // User Role Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `field worker has correct default permissions`() {
        val perms = UserRole.FIELD_WORKER.defaultPermissions
        assertTrue(perms.contains(Permission.READ_RECORDS))
        assertTrue(perms.contains(Permission.WRITE_RECORDS))
        assertTrue(perms.contains(Permission.VIEW_OWN_PROFILE))
        assertFalse(perms.contains(Permission.MANAGE_USERS))
        assertFalse(perms.contains(Permission.MANAGE_ORGANIZATION))
    }

    @Test
    fun `manager has aggregate viewing permission`() {
        val perms = UserRole.MANAGER.defaultPermissions
        assertTrue(perms.contains(Permission.VIEW_AGGREGATES))
        assertTrue(perms.contains(Permission.MANAGE_PAYMENTS))
    }

    @Test
    fun `org admin has all permissions`() {
        val perms = UserRole.ORG_ADMIN.defaultPermissions
        assertEquals(Permission.entries.toSet(), perms)
    }

    // ═══════════════════════════════════════════════════════════════
    // Capability / Permission Check Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `user effective permissions include role defaults plus additional`() {
        val user = OrganizationUser(
            userId = "U001",
            organizationId = "ORG001",
            displayName = "Test Worker",
            role = UserRole.FIELD_WORKER,
            additionalPermissions = setOf(Permission.VIEW_AGGREGATES)
        )
        val effective = user.effectivePermissions()
        assertTrue(effective.contains(Permission.READ_RECORDS))
        assertTrue(effective.contains(Permission.VIEW_AGGREGATES)) // Additional
    }

    @Test
    fun `user effective permissions exclude revoked permissions`() {
        val user = OrganizationUser(
            userId = "U001",
            organizationId = "ORG001",
            displayName = "Restricted Worker",
            role = UserRole.FIELD_WORKER,
            revokedPermissions = setOf(Permission.WRITE_RECORDS)
        )
        assertFalse(user.hasPermission(Permission.WRITE_RECORDS))
        assertTrue(user.hasPermission(Permission.READ_RECORDS))
    }

    // ═══════════════════════════════════════════════════════════════
    // Authorization Policy Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `CASE A - authorized user with permitted query succeeds`() {
        val user = OrganizationUser(
            userId = "WORKER001",
            organizationId = "ORG001",
            displayName = "Ramesh K.",
            role = UserRole.FIELD_WORKER
        )
        val session = SessionContext(
            organizationId = "ORG001",
            currentUser = user
        )
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            target = QueryTarget.MILK_RECORDS
        )

        val result = AuthorizationPolicy.authorize(query, session)
        assertTrue("Authorized user should pass", result.isAuthorized)
    }

    @Test
    fun `CASE B - unauthorized user with same query rejected`() {
        // Field worker trying to view aggregates (requires VIEW_AGGREGATES permission)
        val user = OrganizationUser(
            userId = "WORKER002",
            organizationId = "ORG001",
            displayName = "Junior Worker",
            role = UserRole.FIELD_WORKER,
            revokedPermissions = setOf(Permission.VIEW_AGGREGATES)
        )
        val session = SessionContext(
            organizationId = "ORG001",
            currentUser = user
        )
        val aggregateQuery = FreedomQuery(
            requestType = RequestType.QUERY,
            target = QueryTarget.MILK_RECORDS,
            aggregations = listOf(AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY)),
            groupBy = listOf(SchemaField.FARMER_NAME)
        )

        val result = AuthorizationPolicy.authorize(aggregateQuery, session)
        assertFalse("Unauthorized user should be rejected", result.isAuthorized)
        assertNotNull(result.deniedReason)
    }

    @Test
    fun `unauthenticated session is denied`() {
        val session = SessionContext(
            organizationId = "ORG001",
            currentUser = null
        )
        val query = FreedomQuery(requestType = RequestType.QUERY, target = QueryTarget.MILK_RECORDS)
        val result = AuthorizationPolicy.authorize(query, session)
        assertFalse(result.isAuthorized)
        assertTrue(result.deniedReason!!.contains("No authenticated user"))
    }

    @Test
    fun `inactive user is denied`() {
        val user = OrganizationUser(
            userId = "U001",
            organizationId = "ORG001",
            displayName = "Deactivated",
            role = UserRole.FIELD_WORKER,
            isActive = false
        )
        val session = SessionContext(organizationId = "ORG001", currentUser = user)
        val query = FreedomQuery(requestType = RequestType.QUERY, target = QueryTarget.MILK_RECORDS)

        val result = AuthorizationPolicy.authorize(query, session)
        assertFalse(result.isAuthorized)
        assertTrue(result.deniedReason!!.contains("not active"))
    }

    @Test
    fun `organization mismatch is denied`() {
        val user = OrganizationUser(
            userId = "U001",
            organizationId = "ORG002", // Different org
            displayName = "Cross Org User",
            role = UserRole.FIELD_WORKER
        )
        val session = SessionContext(organizationId = "ORG001", currentUser = user)
        val query = FreedomQuery(requestType = RequestType.QUERY, target = QueryTarget.MILK_RECORDS)

        val result = AuthorizationPolicy.authorize(query, session)
        assertFalse(result.isAuthorized)
        assertTrue(result.deniedReason!!.contains("mismatch"))
    }

    @Test
    fun `write query requires WRITE_RECORDS permission`() {
        val user = OrganizationUser(
            userId = "U001",
            organizationId = "ORG001",
            displayName = "Read Only Worker",
            role = UserRole.FIELD_WORKER,
            revokedPermissions = setOf(Permission.WRITE_RECORDS)
        )
        val session = SessionContext(organizationId = "ORG001", currentUser = user)
        val writeQuery = FreedomQuery(
            requestType = RequestType.WRITE,
            writeArgs = mapOf("farmerName" to "Test", "quantity" to "10.0")
        )

        val result = AuthorizationPolicy.authorize(writeQuery, session)
        assertFalse(result.isAuthorized)
        assertTrue(result.deniedReason!!.contains("WRITE_RECORDS"))
    }

    @Test
    fun `manager can view aggregates`() {
        val user = OrganizationUser(
            userId = "MGR001",
            organizationId = "ORG001",
            displayName = "Manager",
            role = UserRole.MANAGER
        )
        val session = SessionContext(organizationId = "ORG001", currentUser = user)
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            target = QueryTarget.MILK_RECORDS,
            aggregations = listOf(AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY)),
            groupBy = listOf(SchemaField.FARMER_NAME)
        )

        val result = AuthorizationPolicy.authorize(query, session)
        assertTrue(result.isAuthorized)
    }

    // ═══════════════════════════════════════════════════════════════
    // Model Provider Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `model provider registration and activation`() {
        val mockProvider = createMockProvider("mock-model", "Mock Model")
        ModelProviderRegistry.register(mockProvider)
        ModelProviderRegistry.activate("mock-model")

        val active = ModelProviderRegistry.getActiveProvider()
        assertNotNull(active)
        assertEquals("mock-model", active!!.providerId)
    }

    @Test
    fun `model provider switching`() {
        val provider1 = createMockProvider("model-a", "Model A")
        val provider2 = createMockProvider("model-b", "Model B")

        ModelProviderRegistry.register(provider1)
        ModelProviderRegistry.register(provider2)
        ModelProviderRegistry.activate("model-a")
        assertEquals("model-a", ModelProviderRegistry.getActiveProvider()!!.providerId)

        ModelProviderRegistry.activate("model-b")
        assertEquals("model-b", ModelProviderRegistry.getActiveProvider()!!.providerId)
    }

    @Test
    fun `model provider disable returns null active`() {
        val provider = createMockProvider("test-model", "Test")
        ModelProviderRegistry.register(provider)
        ModelProviderRegistry.activate("test-model")
        assertNotNull(ModelProviderRegistry.getActiveProvider())

        ModelProviderRegistry.disableActiveProvider()
        assertNull(ModelProviderRegistry.getActiveProvider())
    }

    @Test(expected = IllegalStateException::class)
    fun `activating unregistered provider throws`() {
        ModelProviderRegistry.activate("nonexistent")
    }

    @Test
    fun `model info correctly reports status`() {
        val provider = createMockProvider("info-test", "Info Test Model")
        ModelProviderRegistry.register(provider)
        ModelProviderRegistry.activate("info-test")

        val info = ModelProviderRegistry.getActiveModelInfo()
        assertNotNull(info)
        assertEquals("info-test", info!!.modelId)
        assertEquals(ModelProviderType.FREEDOM_PROVIDED, info.providerType)
    }

    // ═══════════════════════════════════════════════════════════════
    // Session Management Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `session creation and retrieval`() {
        val session = SessionManager.createSession(
            organizationId = "ORG001",
            userId = "WORKER001",
            displayName = "Ramesh K."
        )
        assertTrue(SessionManager.hasActiveSession())
        assertEquals("ORG001", session.organizationId)
        assertEquals("WORKER001", session.currentUser!!.userId)
        assertTrue(session.isAuthenticated)
    }

    @Test
    fun `session end clears state`() {
        SessionManager.createSession("ORG001", "W001", "Test")
        assertTrue(SessionManager.hasActiveSession())

        SessionManager.endSession()
        assertFalse(SessionManager.hasActiveSession())
    }

    @Test(expected = IllegalStateException::class)
    fun `getting session without login throws`() {
        SessionManager.getCurrentSession()
    }

    // ═══════════════════════════════════════════════════════════════
    // Organization Isolation Tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `session context preserves organization isolation`() {
        val sessionA = SessionManager.createSession("ORG-A", "USER-1", "User A")
        assertEquals("ORG-A", sessionA.organizationId)

        // Simulate re-login as different org
        val sessionB = SessionManager.createSession("ORG-B", "USER-2", "User B")
        assertEquals("ORG-B", SessionManager.getCurrentSession().organizationId)
        assertNotEquals("ORG-A", SessionManager.getCurrentSession().organizationId)
    }

    @Test
    fun `authorization rejects cross-organization query`() {
        val user = OrganizationUser(
            userId = "U001",
            organizationId = "ORG-ALPHA",
            displayName = "Alpha User",
            role = UserRole.FIELD_WORKER
        )
        // Session says ORG-BETA but user belongs to ORG-ALPHA
        val session = SessionContext(
            organizationId = "ORG-BETA",
            currentUser = user
        )
        val query = FreedomQuery(requestType = RequestType.QUERY, target = QueryTarget.MILK_RECORDS)
        val result = AuthorizationPolicy.authorize(query, session)
        assertFalse("Cross-org query must be rejected", result.isAuthorized)
    }

    // ═══════════════════════════════════════════════════════════════
    // Framework Bootstrap Test
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `framework initialization registers default profile and model`() {
        FreedomFramework.initialize()

        assertTrue(FreedomFramework.isReady())
        assertTrue(OrganizationRegistry.hasActiveProfile())
        assertEquals("ORG001", OrganizationRegistry.getActiveProfile().organizationId)
        assertTrue(ModelProviderRegistry.listRegisteredIds().contains("freedom-qwen3-1.7b"))
    }

    @Test
    fun `framework double initialization is safe`() {
        FreedomFramework.initialize()
        FreedomFramework.initialize() // Should not throw
        assertTrue(FreedomFramework.isReady())
    }

    @Test
    fun `framework status summary contains key information`() {
        FreedomFramework.initialize()
        val summary = FreedomFramework.getStatusSummary()
        assertTrue(summary.contains("FREEDOM Framework Status"))
        assertTrue(summary.contains("Organization:"))
        assertTrue(summary.contains("Model:"))
        assertTrue(summary.contains("Initialized: true"))
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun createMockProvider(id: String, name: String): ModelProvider {
        return object : ModelProvider {
            override val providerId = id
            override val displayName = name
            override val providerType = ModelProviderType.FREEDOM_PROVIDED
            override fun isAvailable() = true
            override suspend fun generateText(prompt: String) = "{\"type\": \"QUERY\"}"
            override fun getModelInfo() = ModelInfo(
                modelId = id,
                displayName = name,
                providerType = ModelProviderType.FREEDOM_PROVIDED,
                isActive = true
            )
        }
    }
}
