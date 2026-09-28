package com.example.freedom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a milk collection record saved locally in Room SQLite.
 * Designed for offline field operation and clean future synchronization to an online database.
 */
@Entity(tableName = "milk_records")
data class MilkRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val orgId: String = "ORG001",
    val workerId: String = "WORKER001",
    val farmerId: String? = null,
    val farmerName: String,
    val quantity: Double,
    val fat: Double,
    val snf: Double,
    val paymentStatus: String = PAYMENT_PENDING,
    val paymentMethod: String? = null,
    val paymentReference: String? = null,
    val paymentTimestamp: Long? = null,
    val payableAmount: Double? = null,
    val amountPaid: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val uploadStatus: String = UPLOAD_STATUS_PENDING
) {
    companion object {
        const val PAYMENT_PENDING = "PENDING"
        const val PAYMENT_RECORDED_LOCALLY = "RECORDED_LOCALLY"
        const val PAYMENT_PAID = "PAID"

        const val METHOD_CASH = "CASH"
        const val METHOD_UPI = "UPI"
        const val METHOD_BANK_TRANSFER = "BANK_TRANSFER"
        const val METHOD_OTHER = "OTHER"

        const val UPLOAD_STATUS_PENDING = "PENDING"
        const val UPLOAD_STATUS_UPLOADED = "UPLOADED"

        // Cooperative baseline rate: ₹37.50 / L base rate
        fun calculatePayableAmount(quantity: Double, fat: Double, snf: Double): Double {
            // Quality-adjusted price: base rate ₹35 + adjustments for fat (>= 3.5) and snf (>= 8.0)
            val fatBonus = (fat - 3.5).coerceAtLeast(0.0) * 4.0
            val snfBonus = (snf - 8.0).coerceAtLeast(0.0) * 2.0
            val ratePerLiter = (35.0 + fatBonus + snfBonus).coerceAtLeast(30.0)
            return Math.round(quantity * ratePerLiter * 100.0) / 100.0
        }
    }
}
