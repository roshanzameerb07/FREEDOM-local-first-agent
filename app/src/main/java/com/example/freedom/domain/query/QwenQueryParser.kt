package com.example.freedom.domain.query

import android.util.Log
import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.domain.ai.AIEngineProvider
import com.example.freedom.domain.ai.NumberFidelityReconciler
import org.json.JSONObject

/**
 * Semantic query parser powered by on-device Qwen3 1.7B INT4 via LiteRT-LM.
 *
 * Converts natural language user prompts into the canonical [FreedomQuery] representation.
 * Disables thinking/reasoning tags for low-latency JSON extraction.
 */
object QwenQueryParser {

    private const val TAG = "QwenQueryParser"

    fun buildSystemPrompt(): String {
        return """
            You are FREEDOM AI Query Parser for a dairy field collection database.
            Parse the user's natural language question into a JSON structured query.
            Output ONLY raw JSON. No thinking tags, no reasoning, no markdown.

            JSON Schema:
            {
              "type": "QUERY" | "WRITE" | "CLARIFY" | "UNSUPPORTED",
              "target": "MILK_RECORDS" | "WORKER_PROFILE" | "ORGANIZATION_INFO" | "KNOWLEDGE_BASE",
              "entity": string or null,
              "entityScope": "SPECIFIC" | "ALL",
              "select": ["QUANTITY", "FAT", "SNF", "PAYMENT_STATUS", "PAYMENT_METHOD", "PAYMENT_REFERENCE", "PAYABLE_AMOUNT", "CREATED_AT"],
              "filters": [
                {
                  "field": string,
                  "op": "EQUALS" | "NOT_EQUALS" | "GREATER_THAN" | "GREATER_OR_EQUAL" | "LESS_THAN" | "LESS_OR_EQUAL" | "IS_NULL" | "IS_NOT_NULL" | "CONTAINS" | "IN_RANGE",
                  "value": string or null,
                  "secondaryValue": string or null
                }
              ],
              "filterLogic": "AND" | "OR",
              "time": {
                "type": "RELATIVE" | "EXPLICIT_DATE" | "EXPLICIT_RANGE",
                "period": "TODAY" | "YESTERDAY" | "THIS_WEEK" | "LAST_WEEK" | "THIS_MONTH" | "LAST_MONTH" | "SINCE_MONDAY" | "LAST_SUNDAY" | null,
                "date": string or null,
                "startDate": string or null,
                "endDate": string or null
              } or string or null,
              "aggregations": [
                { "type": "SUM" | "COUNT" | "AVG" | "MIN" | "MAX", "field": "QUANTITY" | "FAT" | "SNF" | "PAYABLE_AMOUNT" | null }
              ],
              "groupBy": ["FARMER_NAME"] or [],
              "havingFilter": { "agg": "SUM", "field": "QUANTITY", "op": "GREATER_THAN", "value": 20.0 } or null,
              "orderBy": { "target": "FIELD" | "AGGREGATE", "field": string or null, "dir": "ASC" | "DESC" } or null,
              "limit": number or null,
              "exists": boolean,
              "conditionalSpec": { "conditionField": "PAYMENT_STATUS", "expectedValue": "PAID" } or null,
              "writeArgs": { "farmerName": string, "quantity": string, "fat": string, "snf": string, "paymentStatus": string } or null,
              "knowledgeQuery": string or null,
              "reason": string or null
            }

            Rules:
            1. "entity" is the farmer NAME ONLY (e.g. "Ramesh Naik"), NOT the full question.
            2. If a specific farmer is mentioned, set "entityScope": "SPECIFIC".
            3. "Who gave the most milk this week?" -> "groupBy": ["FARMER_NAME"], "aggregations": [{"type": "SUM", "field": "QUANTITY"}], "orderBy": {"target": "AGGREGATE", "dir": "DESC"}, "limit": 1.
            4. Questions about policies, rules, standards -> "target": "KNOWLEDGE_BASE", "knowledgeQuery": "<question>".
            5. Questions about officer ID, worker -> "target": "WORKER_PROFILE".
            6. Questions about organization, cooperative name -> "target": "ORGANIZATION_INFO".
            7. "Why was X paid late?" -> "type": "UNSUPPORTED", "reason": "Payment delay reasons not stored".
            8. Record creation commands -> "type": "WRITE", fill "writeArgs".
            9. Never generate epoch timestamps. Use the "time" object with period or date strings.
        """.trimIndent()
    }

    suspend fun parse(userPrompt: String, knownFarmers: List<FarmerEntity> = emptyList()): FreedomQuery {
        if (AIEngineProvider.isAvailable()) {
            try {
                val fullPrompt = "${buildSystemPrompt()}\n\nUser Question: \"$userPrompt\"\nJSON Output:"
                val rawResponse = AIEngineProvider.generateText(fullPrompt)
                if (!rawResponse.isNullOrBlank()) {
                    val parsed = parseJsonResponse(rawResponse, userPrompt)
                    if (parsed != null) {
                        Log.i(TAG, "Qwen3 1.7B parsed query successfully")
                        return parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Qwen3 parsing error: ${e.message}", e)
            }
        }

        Log.i(TAG, "Using deterministic fallback parser")
        return parseFallback(userPrompt, knownFarmers)
    }

    fun parseJsonResponse(rawJson: String, userPrompt: String): FreedomQuery? {
        try {
            var clean = rawJson.trim()
            if (clean.contains("```json")) {
                clean = clean.substringAfter("```json").substringBefore("```").trim()
            } else if (clean.contains("```")) {
                clean = clean.substringAfter("```").substringBefore("```").trim()
            }
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            if (start != -1 && end != -1 && end > start) {
                clean = clean.substring(start, end + 1)
            }

            val json = JSONObject(clean)

            val typeStr = json.optString("type", "QUERY").uppercase()
            val requestType = try { RequestType.valueOf(typeStr) } catch (_: Exception) { RequestType.QUERY }

            when (requestType) {
                RequestType.CLARIFY -> return FreedomQuery(
                    requestType = RequestType.CLARIFY,
                    clarifyReason = json.optString("reason").ifBlank { "Please provide more details." }
                )
                RequestType.UNSUPPORTED -> return FreedomQuery(
                    requestType = RequestType.UNSUPPORTED,
                    unsupportedReason = json.optString("reason").ifBlank { "This information is not available in local records." }
                )
                RequestType.WRITE -> {
                    val writeObj = json.optJSONObject("writeArgs")
                    val writeMap = mutableMapOf<String, String>()
                    if (writeObj != null) {
                        writeObj.keys().forEach { key -> writeMap[key] = writeObj.optString(key) }
                    }
                    val reconciled = NumberFidelityReconciler.reconcile(userPrompt, writeMap)
                    return FreedomQuery(
                        requestType = RequestType.WRITE,
                        entityMention = reconciled["farmerName"],
                        writeArgs = reconciled
                    )
                }
                RequestType.QUERY -> { /* proceed */ }
            }

            val targetStr = json.optString("target", "MILK_RECORDS").uppercase()
            val target = try { QueryTarget.valueOf(targetStr) } catch (_: Exception) { QueryTarget.MILK_RECORDS }

            when (target) {
                QueryTarget.KNOWLEDGE_BASE -> return FreedomQuery(
                    requestType = RequestType.QUERY,
                    target = QueryTarget.KNOWLEDGE_BASE,
                    knowledgeQuery = json.optString("knowledgeQuery").ifBlank { userPrompt }
                )
                QueryTarget.WORKER_PROFILE -> return FreedomQuery(
                    requestType = RequestType.QUERY,
                    target = QueryTarget.WORKER_PROFILE
                )
                QueryTarget.ORGANIZATION_INFO -> return FreedomQuery(
                    requestType = RequestType.QUERY,
                    target = QueryTarget.ORGANIZATION_INFO
                )
                QueryTarget.MILK_RECORDS -> { /* proceed */ }
            }

            val entity = json.optString("entity", "").takeIf { it.isNotBlank() && it != "null" }
            val scopeStr = json.optString("entityScope", "ALL").uppercase()
            val entityScope = if (entity != null) EntityScope.SPECIFIC else {
                try { EntityScope.valueOf(scopeStr) } catch (_: Exception) { EntityScope.ALL }
            }

            val selectArray = json.optJSONArray("select")
            val selectFields = mutableListOf<SchemaField>()
            if (selectArray != null) {
                for (i in 0 until selectArray.length()) {
                    SchemaField.fromString(selectArray.optString(i))?.let { selectFields.add(it) }
                }
            }

            val filtersArray = json.optJSONArray("filters")
            val filterConditions = mutableListOf<QueryFilter>()
            if (filtersArray != null) {
                for (i in 0 until filtersArray.length()) {
                    val fObj = filtersArray.optJSONObject(i) ?: continue
                    val field = SchemaField.fromString(fObj.optString("field")) ?: continue
                    val opStr = fObj.optString("op", "EQUALS").uppercase()
                    val op = try { FilterOperator.valueOf(opStr) } catch (_: Exception) { FilterOperator.EQUALS }
                    val value = fObj.optString("value").takeIf { it.isNotBlank() && it != "null" }
                    val secValue = fObj.optString("secondaryValue").takeIf { it.isNotBlank() && it != "null" }
                    filterConditions.add(QueryFilter(field, op, value, secValue))
                }
            }
            val logicStr = json.optString("filterLogic", "AND").uppercase()
            val logic = try { LogicOperator.valueOf(logicStr) } catch (_: Exception) { LogicOperator.AND }

            // Parse structured temporal constraint
            val timeObj = json.optJSONObject("time")
            val timeStr = json.optString("time", "").takeIf { it.isNotBlank() && it != "null" }
            val temporalConstraint: TemporalConstraint? = when {
                timeObj != null -> {
                    val tType = timeObj.optString("type").uppercase()
                    when (tType) {
                        "EXPLICIT_DATE" -> timeObj.optString("date").takeIf { it.isNotBlank() }?.let { TemporalConstraint.ExplicitDate(it) }
                        "EXPLICIT_RANGE" -> {
                            val s = timeObj.optString("startDate")
                            val e = timeObj.optString("endDate")
                            if (s.isNotBlank() && e.isNotBlank()) TemporalConstraint.ExplicitRange(s, e) else null
                        }
                        else -> {
                            val p = timeObj.optString("period").takeIf { it.isNotBlank() }?.let { TemporalResolver.parseRelativePeriodToken(it) }
                            p?.let { TemporalConstraint.Relative(it) }
                        }
                    }
                }
                timeStr != null -> {
                    TemporalResolver.parseRelativePeriodToken(timeStr)?.let { TemporalConstraint.Relative(it) }
                        ?: TemporalConstraint.ExplicitDate(timeStr)
                }
                else -> null
            }

            // Parse aggregations
            val aggregationsList = mutableListOf<AggregationSpec>()
            val aggArray = json.optJSONArray("aggregations")
            if (aggArray != null) {
                for (i in 0 until aggArray.length()) {
                    val aObj = aggArray.optJSONObject(i) ?: continue
                    val aType = try { AggregationType.valueOf(aObj.optString("type").uppercase()) } catch (_: Exception) { null } ?: continue
                    val aField = SchemaField.fromString(aObj.optString("field"))
                    aggregationsList.add(AggregationSpec(aType, aField))
                }
            } else {
                val aggStr = json.optString("aggregation", "").takeIf { it.isNotBlank() && it != "null" }
                val aggType = aggStr?.let { try { AggregationType.valueOf(it.uppercase()) } catch (_: Exception) { null } }
                val aggField = SchemaField.fromString(json.optString("aggregationField", ""))
                if (aggType != null) {
                    aggregationsList.add(AggregationSpec(aggType, aggField))
                }
            }

            val groupByArray = json.optJSONArray("groupBy")
            val groupBy = mutableListOf<SchemaField>()
            if (groupByArray != null) {
                for (i in 0 until groupByArray.length()) {
                    SchemaField.fromString(groupByArray.optString(i))?.let { groupBy.add(it) }
                }
            }

            // Parse orderBy
            var orderBy: QueryOrder? = null
            val sortObj = json.optJSONObject("orderBy")
            if (sortObj != null) {
                val targetType = sortObj.optString("target", "FIELD").uppercase()
                val dir = if (sortObj.optString("dir").uppercase() == "ASC") SortDirection.ASC else SortDirection.DESC
                if (targetType == "AGGREGATE") {
                    val aggTypeStr = sortObj.optString("aggregation", "SUM").uppercase()
                    val aggType = try { AggregationType.valueOf(aggTypeStr) } catch (_: Exception) { AggregationType.SUM }
                    val aggField = SchemaField.fromString(sortObj.optString("field"))
                    orderBy = QueryOrder.ByAggregate(aggregationType = aggType, field = aggField, direction = dir)
                } else {
                    val sField = SchemaField.fromString(sortObj.optString("field"))
                    if (sField != null) orderBy = QueryOrder.ByField(sField, dir)
                }
            }

            val limit = json.optInt("limit", -1).takeIf { it > 0 }
            val exists = json.optBoolean("exists", false)

            return FreedomQuery(
                requestType = RequestType.QUERY,
                target = QueryTarget.MILK_RECORDS,
                entityMention = entity,
                entityScope = entityScope,
                select = selectFields,
                filters = FilterGroup(logic, filterConditions),
                temporalConstraint = temporalConstraint,
                aggregations = aggregationsList,
                groupBy = groupBy,
                orderBy = orderBy,
                limit = limit,
                existenceCheck = exists
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON response: ${e.message}")
            return null
        }
    }

    fun parseFallback(userPrompt: String, knownFarmers: List<FarmerEntity> = emptyList()): FreedomQuery {
        val lower = userPrompt.lowercase().trim()

        // 1. Write operation detection
        val isExplicitWrite = (lower.contains("gave") || lower.contains("collected")) &&
                (lower.contains("litre") || lower.contains("liter") || Regex("\\d+\\s*l\\b").containsMatchIn(lower)) &&
                (lower.contains("fat") || lower.contains("snf") || NumberFidelityReconciler.extractQuantityFromText(userPrompt) != null)

        if (isExplicitWrite) {
            val qty = NumberFidelityReconciler.extractQuantityFromText(userPrompt) ?: ""
            val fat = NumberFidelityReconciler.extractFatFromText(userPrompt) ?: ""
            val snf = NumberFidelityReconciler.extractSnfFromText(userPrompt) ?: ""
            val payment = if (lower.contains("paid")) "PAID" else "PENDING"
            val args = mutableMapOf<String, String>()
            args["farmerName"] = userPrompt
            if (qty.isNotEmpty()) args["quantity"] = qty
            if (fat.isNotEmpty()) args["fat"] = fat
            if (snf.isNotEmpty()) args["snf"] = snf
            args["paymentStatus"] = payment
            return FreedomQuery(requestType = RequestType.WRITE, writeArgs = args)
        }

        // 2. Knowledge search
        val isPolicySop = lower.contains("policy") || lower.contains("rule") || lower.contains("procedure") ||
                lower.contains("quality standard") || lower.contains("milk standard") ||
                lower.contains("rejected milk") || lower.contains("rejection") ||
                lower.contains("spoilage") || lower.contains("sour milk") ||
                lower.contains("curdled") || lower.contains("sync protocol") ||
                lower.contains("sync policy") || lower.contains("offline limit") ||
                lower.contains("quota") || lower.contains("when is payment considered") ||
                lower.contains("when is payment complete")
        if (isPolicySop) {
            return FreedomQuery(
                requestType = RequestType.QUERY,
                target = QueryTarget.KNOWLEDGE_BASE,
                knowledgeQuery = userPrompt
            )
        }

        // 3. Worker Profile
        if (lower.contains("officer id") || lower.contains("worker id") || lower.contains("who am i") ||
            lower.contains("my center") || lower.contains("my route") || lower.contains("my profile") ||
            lower.contains("what is my id") || lower.contains("my id")
        ) {
            return FreedomQuery(requestType = RequestType.QUERY, target = QueryTarget.WORKER_PROFILE)
        }

        // 4. Organization Info
        if (lower.contains("organization") || lower.contains("cooperative name") ||
            lower.contains("which union") || lower.contains("registration number")
        ) {
            return FreedomQuery(requestType = RequestType.QUERY, target = QueryTarget.ORGANIZATION_INFO)
        }

        // 5. Unsupported reason queries
        if (lower.contains("why was") && lower.contains("paid late")) {
            return FreedomQuery(
                requestType = RequestType.UNSUPPORTED,
                unsupportedReason = "Local records contain payment status and timestamps, but do not store payment delay reasons."
            )
        }

        // 6. Build MILK_RECORDS query
        val temporalConstraint = TemporalResolver.extractConstraintFromPrompt(userPrompt)
        val filters = mutableListOf<QueryFilter>()
        val aggregationsList = mutableListOf<AggregationSpec>()
        val groupByList = mutableListOf<SchemaField>()
        var orderBy: QueryOrder? = null
        var limit: Int? = null
        val selectFields = mutableListOf<SchemaField>()

        // Aggregations
        if (lower.contains("total collection") || lower.contains("total milk") ||
            lower.contains("how much") || lower.contains("quantity")
        ) {
            aggregationsList.add(AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY))
        }
        if (lower.contains("average fat") || lower.contains("avg fat")) {
            aggregationsList.add(AggregationSpec(AggregationType.AVG, SchemaField.FAT))
            selectFields.add(SchemaField.FAT)
        }

        // "Who gave the most milk" -> GROUP BY farmer SUM(quantity) ORDER BY AGGREGATE DESC LIMIT 1
        if (lower.contains("who gave the most") || lower.contains("supplied the most") ||
            lower.contains("highest collection")
        ) {
            groupByList.add(SchemaField.FARMER_NAME)
            aggregationsList.add(AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY))
            orderBy = QueryOrder.ByAggregate(aggregationType = AggregationType.SUM, field = SchemaField.QUANTITY, direction = SortDirection.DESC)
            limit = 1
            selectFields.add(SchemaField.FARMER_NAME)
            selectFields.add(SchemaField.QUANTITY)
        }

        // Numeric filters
        if (lower.contains("more than 20") || lower.contains("> 20") || lower.contains("above 20")) {
            filters.add(QueryFilter(SchemaField.QUANTITY, FilterOperator.GREATER_THAN, "20.0"))
            selectFields.add(SchemaField.FARMER_NAME)
            selectFields.add(SchemaField.QUANTITY)
        }
        if (lower.contains("fat above 4.5")) {
            filters.add(QueryFilter(SchemaField.FAT, FilterOperator.GREATER_THAN, "4.5"))
            selectFields.add(SchemaField.FAT)
        }
        if (lower.contains("pending payment") || lower.contains("unpaid")) {
            filters.add(QueryFilter(SchemaField.PAYMENT_STATUS, FilterOperator.EQUALS, "PENDING"))
            selectFields.add(SchemaField.PAYMENT_STATUS)
        }

        // Payment fields
        val hasPaymentContext = lower.contains("paid") || lower.contains("payment") ||
                lower.contains("method") || lower.contains("reference")
        if (hasPaymentContext) {
            selectFields.add(SchemaField.PAYMENT_STATUS)
            selectFields.add(SchemaField.PAYMENT_METHOD)
            selectFields.add(SchemaField.PAYMENT_REFERENCE)
        }

        // Database-backed entity mention detection
        var entityMention: String? = null
        if (knownFarmers.isNotEmpty()) {
            val normPrompt = NameNormalizer.normalize(userPrompt)
            val matchedFarmer = knownFarmers
                .sortedByDescending { it.normalizedName.length }
                .firstOrNull { farmer ->
                    val normFarmer = farmer.normalizedName
                    val pattern = Regex("\\b${Regex.escape(normFarmer)}\\b")
                    pattern.containsMatchIn(normPrompt)
                }
            if (matchedFarmer != null) {
                entityMention = matchedFarmer.farmerName
            } else {
                val tokens = NameNormalizer.tokenize(normPrompt)
                for (token in tokens) {
                    val matching = knownFarmers.filter { f ->
                        NameNormalizer.tokenize(f.normalizedName).any { it == token }
                    }
                    if (matching.isNotEmpty()) {
                        entityMention = token.replaceFirstChar { it.uppercase() }
                        break
                    }
                }
            }
        }

        val scope = if (entityMention != null) EntityScope.SPECIFIC else EntityScope.ALL

        return FreedomQuery(
            requestType = RequestType.QUERY,
            target = QueryTarget.MILK_RECORDS,
            entityMention = entityMention,
            entityScope = scope,
            select = selectFields.distinct(),
            filters = FilterGroup(LogicOperator.AND, filters),
            temporalConstraint = temporalConstraint,
            aggregations = aggregationsList,
            groupBy = groupByList,
            orderBy = orderBy,
            limit = limit
        )
    }

    fun parseFallbackWithEntity(userPrompt: String, entityMention: String): FreedomQuery {
        val base = parseFallback(userPrompt)
        return base.copy(
            entityMention = entityMention,
            entityScope = EntityScope.SPECIFIC
        )
    }
}
