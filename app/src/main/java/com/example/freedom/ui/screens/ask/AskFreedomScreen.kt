package com.example.freedom.ui.screens.ask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.domain.ai.ToolExecutor
import com.example.freedom.domain.ai.ToolIntent
import com.example.freedom.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskFreedomScreen(
    viewModel: AskFreedomViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ask FREEDOM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Field Assistant • Works Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryGreen
                )
            )
        },
        containerColor = BackgroundLight
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {

            // Suggestion Chips Header
            Text(
                text = "Try an example",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ASK Section
            Text(
                text = "ASK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreenDark,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickQueryChip("How much did Ramesh give this week?") {
                    viewModel.submitQuery(it)
                }
                QuickQueryChip("Who has pending payments?") {
                    viewModel.submitQuery(it)
                }
                QuickQueryChip("How many farmers did I cover today?") {
                    viewModel.submitQuery(it)
                }
                QuickQueryChip("When is payment considered complete?") {
                    viewModel.submitQuery(it)
                }
                QuickQueryChip("What is my ID?") {
                    viewModel.submitQuery(it)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // RECORD Section
            Text(
                text = "RECORD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = InfoBlue,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickQueryChip("Ramesh gave 10 litres, fat 4.2 and SNF 8.6.") {
                    viewModel.submitQuery(it)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.queryInput,
                        onValueChange = { viewModel.onQueryInputChange(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Ask anything or type a collection...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            focusManager.clearFocus()
                            viewModel.submitQuery()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.submitQuery()
                        },
                        enabled = !uiState.isLoading && uiState.queryInput.isNotBlank()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (uiState.queryInput.isNotBlank()) PrimaryGreen else TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Confirmation Dialog for Record Creation
            uiState.confirmationRequest?.let { req ->
                CollectionReviewCard(
                    args = req.args,
                    onConfirm = { viewModel.confirmToolExecution() },
                    onCancel = { viewModel.cancelToolExecution() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Adaptive Result Presentation
            uiState.executionResult?.let { result ->
                AdaptiveResultCard(result)
            }

            // Summary result (Fallback)
            uiState.lastResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = result.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickQueryChip(
    text: String,
    onClick: (String) -> Unit
) {
    Surface(
        onClick = { onClick(text) },
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Gray100),
        shadowElevation = 1.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/**
 * Human-centered collection review card prior to saving into SQLite.
 */
@Composable
fun CollectionReviewCard(
    args: Map<String, String>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val farmer = args["farmerName"] ?: "Farmer"
    val qty = args["quantity"] ?: "0.0"
    val fat = args["fat"] ?: "0.0"
    val snf = args["snf"] ?: "0.0"
    val payment = args["paymentStatus"] ?: "PENDING"
    val paymentMethod = args["paymentMethod"] ?: "CASH"

    val qVal = qty.toDoubleOrNull() ?: 0.0
    val fVal = fat.toDoubleOrNull() ?: 0.0
    val sVal = snf.toDoubleOrNull() ?: 0.0
    val estAmount = MilkRecordEntity.calculatePayableAmount(qVal, fVal, sVal)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.5.dp, PrimaryGreen.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PrimaryGreen.copy(alpha = 0.12f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Review Collection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Verify values before saving to device records",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Gray100)
            Spacer(modifier = Modifier.height(14.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Farmer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(farmer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Quantity", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("$qty L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Fat", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("$fat%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SNF", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("$snf%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Est. Amount", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("₹${"%.2f".format(estAmount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = PrimaryGreenDark)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Payment Status", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (payment == "PENDING") PendingAmber.copy(alpha = 0.15f) else OfflineGreenBg
                ) {
                    Text(
                        text = if (payment == "PENDING") "Pending" else "Recorded locally ($paymentMethod)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (payment == "PENDING") PendingAmber else OfflineGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save Collection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Adaptive Result Presentation based on the capability executed.
 */
@Composable
fun AdaptiveResultCard(result: ToolExecutor.ExecutionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            when {
                // 1. Cooperative Policy Question (RAG)
                result.ragDocument != null -> {
                    RagResultContent(result)
                }

                result.capabilityUsed == "SEARCH_LOCAL_KNOWLEDGE" -> {
                    RagRefusalContent(result)
                }

                // 2. Numeric / Summary Capability
                result.capabilityUsed in listOf("COUNT_FARMERS_COVERED", "GET_WEEKLY_WORKER_SUMMARY", "GET_TODAY_SUMMARY", "GET_TODAY_WORKER_SUMMARY") -> {
                    NumericSummaryContent(result)
                }

                // 3. Farmer History / Deliveries List
                result.capabilityUsed == "GET_FARMER_HISTORY" -> {
                    FarmerHistoryContent(result)
                }

                // 4. Pending Payments
                result.capabilityUsed == "GET_PENDING_PAYMENTS" -> {
                    PendingPaymentsContent(result)
                }

                // 5. Worker Profile
                result.capabilityUsed in listOf("GET_WORKER_PROFILE", "GET_ORGANIZATION_INFO") -> {
                    ProfileContent(result)
                }

                // 6. Record Creation Result
                result.capabilityUsed == "CREATE_MILK_RECORD" -> {
                    RecordCreationResultContent(result)
                }

                // 7. Clarification / Generic
                else -> {
                    GenericResultContent(result)
                }
            }
        }
    }
}

@Composable
fun RagResultContent(result: ToolExecutor.ExecutionResult) {
    val doc = result.ragDocument ?: return
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Cooperative Policy",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = PrimaryGreenDark
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = TextPrimary,
        lineHeight = 22.sp
    )

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = Gray100)
    Spacer(modifier = Modifier.height(8.dp))

    // Expandable Policy Evidence
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Reference: ${doc.clauseOrPage}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(Gray100.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "${doc.documentTitle} — ${doc.sectionTitle}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = doc.content,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun RagRefusalContent(result: ToolExecutor.ExecutionResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = PendingAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Policy Not Found",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = PendingAmber
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        lineHeight = 22.sp
    )
}

@Composable
fun NumericSummaryContent(result: ToolExecutor.ExecutionResult) {
    Text(
        text = result.summary,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    result.calculationDetails?.let { details ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun FarmerHistoryContent(result: ToolExecutor.ExecutionResult) {
    Text(
        text = result.summary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    if (result.records.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            result.records.take(5).forEach { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Gray100.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = dateFormat.format(Date(record.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Fat ${record.fat}% • SNF ${record.snf}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Text(
                            text = "${record.quantity} L",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingPaymentsContent(result: ToolExecutor.ExecutionResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Payment,
            contentDescription = null,
            tint = PendingAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Pending Payments",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )

    if (result.records.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            result.records.take(5).forEach { record ->
                val amount = record.payableAmount ?: MilkRecordEntity.calculatePayableAmount(record.quantity, record.fat, record.snf)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = PendingAmber.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = record.farmerName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${record.quantity} L • ${dateFormat.format(Date(record.createdAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "₹${"%.2f".format(amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PendingAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileContent(result: ToolExecutor.ExecutionResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = PrimaryGreen,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Profile Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        lineHeight = 22.sp
    )
}

@Composable
fun RecordCreationResultContent(result: ToolExecutor.ExecutionResult) {
    val isSuccess = result.success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isSuccess) OfflineGreen else ErrorRed,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isSuccess) "Collection Saved" else "Incomplete Collection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSuccess) OfflineGreen else ErrorRed
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary
    )
}

@Composable
fun GenericResultContent(result: ToolExecutor.ExecutionResult) {
    Text(
        text = result.summary,
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        lineHeight = 22.sp
    )
}
