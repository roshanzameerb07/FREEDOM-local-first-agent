package com.example.freedom.ui.screens.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRecordsScreen(
    viewModel: LocalRecordsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var paymentDialogRecord by remember { mutableStateOf<MilkRecordEntity?>(null) }
    var selectedMethod by remember { mutableStateOf(MilkRecordEntity.METHOD_CASH) }
    var referenceInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Records & Payments",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${uiState.totalCount} entries • ${String.format("%.1f", uiState.totalQuantityLitres)} L total",
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
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Search by farmer name...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGreen)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextTertiary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = OutlineColor,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = uiState.paymentFilter == null,
                        onClick = { viewModel.onPaymentFilterChange(null) },
                        label = { Text("All Records") }
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.paymentFilter == MilkRecordEntity.PAYMENT_PENDING,
                        onClick = { viewModel.onPaymentFilterChange(MilkRecordEntity.PAYMENT_PENDING) },
                        label = { Text("Pending Payment") },
                        leadingIcon = if (uiState.paymentFilter == MilkRecordEntity.PAYMENT_PENDING) {
                            { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }

                item {
                    FilterChip(
                        selected = uiState.paymentFilter == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY || uiState.paymentFilter == MilkRecordEntity.PAYMENT_PAID,
                        onClick = { viewModel.onPaymentFilterChange(MilkRecordEntity.PAYMENT_RECORDED_LOCALLY) },
                        label = { Text("Payment Recorded") },
                        leadingIcon = if (uiState.paymentFilter == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Records List
            if (uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No collection records found",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.records, key = { it.id }) { record ->
                        RecordCardItem(
                            record = record,
                            onRecordPaymentClick = {
                                paymentDialogRecord = record
                                selectedMethod = MilkRecordEntity.METHOD_CASH
                                referenceInput = ""
                            }
                        )
                    }
                }
            }
        }
    }

    // Payment Recording Dialog
    paymentDialogRecord?.let { record ->
        val amount = record.payableAmount ?: MilkRecordEntity.calculatePayableAmount(record.quantity, record.fat, record.snf)
        val isRefRequired = selectedMethod == MilkRecordEntity.METHOD_UPI || selectedMethod == MilkRecordEntity.METHOD_BANK_TRANSFER
        val isRefMissing = isRefRequired && referenceInput.isBlank()

        AlertDialog(
            onDismissRequest = { paymentDialogRecord = null },
            title = {
                Text("Record Payment", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Farmer: ${record.farmerName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Amount: ₹${"%.2f".format(amount)} (${record.quantity} L)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryGreenDark,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select Payment Method:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMethod == MilkRecordEntity.METHOD_CASH,
                            onClick = { selectedMethod = MilkRecordEntity.METHOD_CASH },
                            label = { Text("Cash") }
                        )
                        FilterChip(
                            selected = selectedMethod == MilkRecordEntity.METHOD_UPI,
                            onClick = { selectedMethod = MilkRecordEntity.METHOD_UPI },
                            label = { Text("UPI") }
                        )
                        FilterChip(
                            selected = selectedMethod == MilkRecordEntity.METHOD_BANK_TRANSFER,
                            onClick = { selectedMethod = MilkRecordEntity.METHOD_BANK_TRANSFER },
                            label = { Text("Bank") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = referenceInput,
                        onValueChange = { referenceInput = it },
                        label = { Text(if (isRefRequired) "Reference / Transaction ID (Mandatory)" else "Receipt / Reference (Optional)") },
                        placeholder = { Text(if (isRefRequired) "e.g. UPI-98421 or Bank Txn ID" else "e.g. REC-102") },
                        isError = isRefMissing,
                        supportingText = {
                            if (isRefMissing) {
                                Text("Transaction / Reference ID is required for $selectedMethod", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recorded locally • Awaiting bank reconciliation upon sync.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.recordPayment(record.id, selectedMethod, referenceInput)
                        paymentDialogRecord = null
                    },
                    enabled = !isRefMissing,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save Payment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { paymentDialogRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecordCardItem(
    record: MilkRecordEntity,
    onRecordPaymentClick: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(record.createdAt) { dateFormat.format(Date(record.createdAt)) }
    val isPending = record.paymentStatus == MilkRecordEntity.PAYMENT_PENDING
    val amount = record.payableAmount ?: MilkRecordEntity.calculatePayableAmount(record.quantity, record.fat, record.snf)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.farmerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${record.quantity} L",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = PrimaryGreen
                    )
                    Text(
                        text = "₹${"%.2f".format(amount)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Gray100)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Fat: ${record.fat}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = "SNF: ${record.snf}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isPending) PendingAmber.copy(alpha = 0.12f) else OfflineGreenBg
                ) {
                    Text(
                        text = if (isPending) "Pending Payment" else "Recorded (${record.paymentMethod ?: "Cash"})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPending) PendingAmber else OfflineGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isPending) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRecordPaymentClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record Payment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            } else if (!record.paymentReference.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ref: ${record.paymentReference}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
