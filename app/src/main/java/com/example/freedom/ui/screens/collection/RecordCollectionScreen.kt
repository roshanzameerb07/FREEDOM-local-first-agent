package com.example.freedom.ui.screens.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCollectionScreen(
    viewModel: RecordCollectionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Collection saved for ${uiState.lastSavedFarmerName}!")
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Collect Milk",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New Collection Entry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        TextButton(onClick = { viewModel.fillSampleRecord() }) {
                            Text("Fill Sample", color = PrimaryGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Farmer Name
                    OutlinedTextField(
                        value = uiState.farmerName,
                        onValueChange = { viewModel.onFarmerNameChange(it) },
                        label = { Text("Farmer Name") },
                        placeholder = { Text("e.g. Ramesh") },
                        isError = uiState.validationResult?.farmerNameError != null,
                        supportingText = {
                            uiState.validationResult?.farmerNameError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryGreen)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Quantity (L)
                    OutlinedTextField(
                        value = uiState.quantity,
                        onValueChange = { viewModel.onQuantityChange(it) },
                        label = { Text("Quantity (Litres)") },
                        placeholder = { Text("e.g. 18.5") },
                        isError = uiState.validationResult?.quantityError != null,
                        supportingText = {
                            uiState.validationResult?.quantityError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = PrimaryGreen)
                        },
                        trailingIcon = {
                            Text("L", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(end = 12.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Fat & SNF Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.fat,
                            onValueChange = { viewModel.onFatChange(it) },
                            label = { Text("Fat %") },
                            placeholder = { Text("e.g. 4.2") },
                            isError = uiState.validationResult?.fatError != null,
                            supportingText = {
                                uiState.validationResult?.fatError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            trailingIcon = {
                                Text("%", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(end = 8.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Right) }
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = uiState.snf,
                            onValueChange = { viewModel.onSnfChange(it) },
                            label = { Text("SNF %") },
                            placeholder = { Text("e.g. 8.6") },
                            isError = uiState.validationResult?.snfError != null,
                            supportingText = {
                                uiState.validationResult?.snfError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            trailingIcon = {
                                Text("%", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(end = 8.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // 4. Estimated Payout Display
                    if (uiState.estimatedAmount > 0.0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = PrimaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estimated Amount:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = OnPrimaryContainer
                                )
                                Text(
                                    text = "₹${"%.2f".format(uiState.estimatedAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreenDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Gray100)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Payment Section
                    Text(
                        text = "Payment Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = !uiState.isPaymentRecorded,
                            onClick = { viewModel.onPaymentRecordedToggle(false) },
                            label = { Text("Payment Pending") },
                            leadingIcon = if (!uiState.isPaymentRecorded) {
                                { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = uiState.isPaymentRecorded,
                            onClick = { viewModel.onPaymentRecordedToggle(true) },
                            label = { Text("Record Payment") },
                            leadingIcon = if (uiState.isPaymentRecorded) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    // If Payment is Recorded
                    if (uiState.isPaymentRecorded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Payment Method:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaymentMethodChip(
                                title = "Cash",
                                isSelected = uiState.paymentMethod == MilkRecordEntity.METHOD_CASH,
                                onClick = { viewModel.onPaymentMethodChange(MilkRecordEntity.METHOD_CASH) }
                            )
                            PaymentMethodChip(
                                title = "UPI",
                                isSelected = uiState.paymentMethod == MilkRecordEntity.METHOD_UPI,
                                onClick = { viewModel.onPaymentMethodChange(MilkRecordEntity.METHOD_UPI) }
                            )
                            PaymentMethodChip(
                                title = "Bank",
                                isSelected = uiState.paymentMethod == MilkRecordEntity.METHOD_BANK_TRANSFER,
                                onClick = { viewModel.onPaymentMethodChange(MilkRecordEntity.METHOD_BANK_TRANSFER) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val isRefRequired = uiState.paymentMethod == MilkRecordEntity.METHOD_UPI || uiState.paymentMethod == MilkRecordEntity.METHOD_BANK_TRANSFER
                        OutlinedTextField(
                            value = uiState.paymentReference,
                            onValueChange = { viewModel.onPaymentReferenceChange(it) },
                            label = { Text(if (isRefRequired) "Reference / Transaction ID (Mandatory)" else "Reference / Receipt ID (Optional)") },
                            placeholder = { Text(if (isRefRequired) "e.g. UPI-98421 or Bank Txn ID" else "e.g. CASH-01") },
                            isError = uiState.validationResult?.paymentReferenceError != null,
                            supportingText = {
                                uiState.validationResult?.paymentReferenceError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: Recorded locally on device. Awaiting bank / cooperative reconciliation.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Review & Save Button
                    Button(
                        onClick = { viewModel.onReviewClick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text(
                            text = "Review Collection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Review Dialog
    if (uiState.isReviewing) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissReview() },
            title = {
                Text(
                    text = "Confirm Collection",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("Please confirm the collection values:")
                    Spacer(modifier = Modifier.height(12.dp))

                    DetailRow("Farmer", uiState.farmerName)
                    DetailRow("Quantity", "${uiState.quantity} L")
                    DetailRow("Fat", "${uiState.fat}%")
                    DetailRow("SNF", "${uiState.snf}%")
                    DetailRow("Est. Amount", "₹${"%.2f".format(uiState.estimatedAmount)}")
                    DetailRow(
                        "Payment",
                        if (uiState.isPaymentRecorded) "Recorded locally (${uiState.paymentMethod})" else "Pending"
                    )
                    if (uiState.isPaymentRecorded && uiState.paymentReference.isNotBlank()) {
                        DetailRow("Ref ID", uiState.paymentReference)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmAndSave {
                            onNavigateToRecords()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save Collection")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.onDismissReview() }) {
                    Text("Edit")
                }
            }
        )
    }
}

@Composable
fun PaymentMethodChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) PrimaryGreen else Gray100.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isSelected) PrimaryGreen else Gray100)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
