package com.example.freedom.domain.ai

import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.validation.MilkRecordValidator
import kotlinx.coroutines.flow.first

/**
 * Executes a validated [ToolRequest] against the local Room database.
 * Returns a summary string, success status, and optional list of records for display.
 */
class ToolExecutor(
    private val repository: MilkRecordRepository,
    private val validator: MilkRecordValidator = MilkRecordValidator()
) {
    data class ExecutionResult(
        val summary: String,
        val records: List<MilkRecordEntity> = emptyList(),
        val success: Boolean = true
    )

    suspend fun execute(request: ToolRequest): ExecutionResult {
        return when (request.intent) {
            ToolIntent.CREATE_MILK_RECORD -> {
                val farmer = request.args["farmerName"]?.trim() ?: ""
                val quantityStr = request.args["quantity"]?.trim() ?: ""
                val fatStr = request.args["fat"]?.trim() ?: ""
                val snfStr = request.args["snf"]?.trim() ?: ""
                val payment = request.args["paymentStatus"]?.trim()?.uppercase() ?: MilkRecordEntity.PAYMENT_PENDING

                val validation = validator.validate(
                    farmerName = farmer,
                    quantityStr = quantityStr,
                    fatStr = fatStr,
                    snfStr = snfStr,
                    paymentStatus = payment
                )

                if (!validation.isValid) {
                    val errors = listOfNotNull(
                        validation.farmerNameError,
                        validation.quantityError,
                        validation.fatError,
                        validation.snfError,
                        validation.paymentStatusError
                    ).joinToString("; ")
                    return ExecutionResult(
                        summary = "Validation Failed: $errors. Database write aborted.",
                        records = emptyList(),
                        success = false
                    )
                }

                val quantity = quantityStr.toDouble()
                val fat = fatStr.toDouble()
                val snf = snfStr.toDouble()
                val paymentStatus = if (payment == "PAID") MilkRecordEntity.PAYMENT_PAID else MilkRecordEntity.PAYMENT_PENDING

                val entity = MilkRecordEntity(
                    farmerName = farmer,
                    quantity = quantity,
                    fat = fat,
                    snf = snf,
                    paymentStatus = paymentStatus,
                    uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                )
                repository.insertRecord(entity)
                ExecutionResult(
                    summary = "Successfully saved milk record for $farmer: ${"%.1f".format(quantity)} L, Fat ${"%.1f".format(fat)}%, SNF ${"%.1f".format(snf)}%, Payment: $paymentStatus (Stored Locally).",
                    records = listOf(entity),
                    success = true
                )
            }
            ToolIntent.SEARCH_FARMERS -> {
                val query = request.args["query"] ?: ""
                val records = repository.searchAndFilterRecords(query = query, paymentStatus = null, uploadStatus = null).first()
                ExecutionResult(
                    summary = "Found ${records.size} local record(s) matching '$query'.",
                    records = records,
                    success = true
                )
            }
            ToolIntent.GET_FARMER_HISTORY -> {
                val farmer = request.args["farmerName"]?.trim() ?: ""
                val records = if (farmer.isNotEmpty()) repository.getRecordsByFarmerName(farmer) else repository.getAllRecords().first()
                val totalQty = records.sumOf { it.quantity }
                ExecutionResult(
                    summary = if (records.isNotEmpty()) {
                        "Found ${records.size} record(s) for $farmer totaling ${"%.1f".format(totalQty)} L."
                    } else {
                        "No local delivery records found for farmer '$farmer'."
                    },
                    records = records,
                    success = true
                )
            }
            ToolIntent.GET_TODAY_SUMMARY -> {
                val count = repository.getTodayRecordsCount().first()
                val litres = repository.getTodayTotalQuantity().first()
                val allToday = repository.getAllRecords().first().take(count)
                ExecutionResult(
                    summary = "Today's summary: $count record(s) collected locally totaling ${"%.1f".format(litres)} L.",
                    records = allToday,
                    success = true
                )
            }
            ToolIntent.GET_PENDING_PAYMENTS -> {
                val records = repository.getPendingPaymentRecords()
                val total = records.sumOf { it.quantity }
                ExecutionResult(
                    summary = "Found ${records.size} pending payment record(s) totaling ${"%.1f".format(total)} L.",
                    records = records,
                    success = true
                )
            }
            ToolIntent.GET_PENDING_UPLOADS -> {
                val records = repository.getPendingRecords()
                ExecutionResult(
                    summary = "Pending uploads: ${records.size} record(s) queued for batch synchronization.",
                    records = records,
                    success = true
                )
            }
        }
    }
}
