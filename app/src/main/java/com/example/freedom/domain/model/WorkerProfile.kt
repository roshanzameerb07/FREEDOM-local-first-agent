package com.example.freedom.domain.model

data class WorkerProfile(
    val workerId: String = "WORKER001",
    val workerName: String = "Ramesh K.",
    val role: String = "Field Collection Officer",
    val assignedArea: String = "Sector 4 — North Mandya Milk Route",
    val centerName: String = "Dharmapura Center #14",
    val organizationId: String = "ORG001",
    val organizationName: String = "Mandya District Cooperative Milk Producers Union (KMF / Nandini)",
    val shift: String = "Morning (06:00 - 10:30)",
    val permissions: List<String> = listOf("CREATE_MILK_RECORD", "VIEW_PAYMENTS", "BATCH_SYNC")
)

data class OrganizationInfo(
    val organizationId: String = "ORG001",
    val organizationName: String = "Mandya District Cooperative Milk Producers Union (KMF / Nandini)",
    val registrationNumber: String = "COOP-KAR-MND-4412",
    val regionalDistrict: String = "Mandya, Karnataka",
    val activeCentersCount: Int = 18,
    val supportHotline: String = "+91 8232 220011"
)

object WorkerProfileRepository {
    private var currentProfile: WorkerProfile = WorkerProfile()
    private var currentOrgInfo: OrganizationInfo = OrganizationInfo()

    fun getProfile(): WorkerProfile = currentProfile

    fun getOrganizationInfo(): OrganizationInfo = currentOrgInfo

    fun updateProfileFromAuth(orgId: String, workerId: String, workerName: String) {
        currentProfile = currentProfile.copy(
            organizationId = orgId,
            workerId = workerId,
            workerName = workerName
        )
        currentOrgInfo = currentOrgInfo.copy(
            organizationId = orgId
        )
    }
}
