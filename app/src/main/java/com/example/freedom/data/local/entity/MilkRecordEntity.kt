package com.example.freedom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "milk_records")
data class MilkRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val farmerName: String,
    val quantity: Double,
    val fat: Double,
    val snf: Double,
    val paymentStatus: String, // "PENDING" or "PAID"
    val createdAt: Long = System.currentTimeMillis(),
    val uploadStatus: String = UPLOAD_STATUS_PENDING // "PENDING" or "UPLOADED"
) {
    companion object {
        const val PAYMENT_PENDING = "PENDING"
        const val PAYMENT_PAID = "PAID"

        const val UPLOAD_STATUS_PENDING = "PENDING"
        const val UPLOAD_STATUS_UPLOADED = "UPLOADED"
    }
}
