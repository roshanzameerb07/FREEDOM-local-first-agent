package com.example.freedom.domain.ai

import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.data.local.entity.MilkRecordEntity
import kotlinx.coroutines.flow.first

/**
 * Executes a validated [ToolRequest] against the local Room database.
 * Returns a summary string and optional list of records for display.
 */
class ToolExecutor(private val repository: MilkRecordRepository) {
    data class ExecutionResult(val summary: String, val records: List<MilkRecordEntity> = emptyList())

    suspend fun execute(request: ToolRequest): ExecutionResult {
        return when (request.intent) {
            ToolIntent.CREATE_MILK_RECORD -> {
                // Expect args: farmerName, quantity, fat, snf, paymentStatus
                val farmer = request.args["farmerName"] ?: ""
                val quantity = request.args["quantity"]?.toDoubleOrNull() ?: 0.0
                val fat = request.args["fat"]?.toDoubleOrNull() ?: 0.0
                val snf = request.args["snf"]?.toDoubleOrNull() ?: 0.0
                val payment = request.args["paymentStatus"] ?: MilkRecordEntity.PAYMENT_PENDING
                val entity = MilkRecordEntity(
                    farmerName = farmer,
                    quantity = quantity,
                    fat = fat,
                    snf = snf,
                    paymentStatus = payment
                )
                repository.insertRecord(entity)
                ExecutionResult(summary = "Created milk record for $farmer ( ${"%.1f".format(quantity)} L, fat ${"%.1f".format(fat)}, SNF ${"%.1f".format(snf)} ).")
            }
            ToolIntent.SEARCH_FARMERS -> {
                val query = request.args["query"] ?: ""
                val records = repository.searchAndFilterRecords(query = query, paymentStatus = null, uploadStatus = null).first()
                ExecutionResult(summary = "Found ${records.size} farmer(s) matching '$query'.", records = records)
            }
            ToolIntent.GET_FARMER_HISTORY -> {
                val farmer = request.args["farmerName"] ?: ""
                val records = repository.getRecordsByFarmerName(farmer)
                ExecutionResult(summary = "History for $farmer: ${records.size} record(s).", records = records)
            }
            ToolIntent.GET_TODAY_SUMMARY -> {
                val count = repository.getTodayRecordsCount().first()
                val litres = repository.getTodayTotalQuantity().first()
                ExecutionResult(summary = "Today: $count record(s) totalling ${"%.1f".format(litres)} L.")
            }
            ToolIntent.GET_PENDING_PAYMENTS -> {
                val records = repository.getPendingPaymentRecords()
                val total = records.sumOf { it.quantity }
                ExecutionResult(summary = "Pending payments: ${records.size} record(s), ${"%.1f".format(total)} L.", records = records)
            }
            ToolIntent.GET_PENDING_UPLOADS -> {
                val records = repository.getPendingRecords()
                ExecutionResult(summary = "Pending uploads: ${records.size} record(s).", records = records)
            }
        }
    }
}
