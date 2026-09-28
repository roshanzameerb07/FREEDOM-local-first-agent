package com.example.freedom.ui.screens.collection

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

    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            showSuccessDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Record Milk Collection",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
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

            // Voice Input Future-Ready Architecture Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(PrimaryGreen, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Voice Entry (AI Pipeline Conduit)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryContainer
                            )
                            Text(
                                text = "Speech -> On-Device SLM -> Structured Draft",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryGreenDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Future input: \"Ramesh gave 18 litres, fat 4.2 and SNF 8.6. Payment is pending.\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.processVoiceSample()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Speak (Simulate Voice)")
                        }

                        OutlinedButton(
                            onClick = { viewModel.fillSampleRecord() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                        ) {
                            Text("Auto-Fill Demo")
                        }
                    }

                    if (uiState.voiceExtractionMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.voiceExtractionMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryGreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Entry Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Milk Collection Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Enter validated metrics for local storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Farmer Name
                    OutlinedTextField(
                        value = uiState.farmerName,
                        onValueChange = { viewModel.onFarmerNameChange(it) },
                        label = { Text("Farmer Name *") },
                        placeholder = { Text("e.g. Ramesh") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryGreen)
                        },
                        isError = uiState.validationResult?.farmerNameError != null,
                        supportingText = {
                            uiState.validationResult?.farmerNameError?.let {
                                Text(it, color = ErrorRed)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = OutlineColor
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Quantity (L)
                    OutlinedTextField(
                        value = uiState.quantity,
                        onValueChange = { viewModel.onQuantityChange(it) },
                        label = { Text("Quantity (Litres) *") },
                        placeholder = { Text("e.g. 18.0") },
                        leadingIcon = {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = PrimaryGreen)
                        },
                        trailingIcon = {
                            Text("L", fontWeight = FontWeight.Bold, color = TextTertiary, modifier = Modifier.padding(end = 12.dp))
                        },
                        isError = uiState.validationResult?.quantityError != null,
                        supportingText = {
                            uiState.validationResult?.quantityError?.let {
                                Text(it, color = ErrorRed)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = OutlineColor
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Fat (%) and 4. SNF (%) in Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.fat,
                            onValueChange = { viewModel.onFatChange(it) },
                            label = { Text("Fat (%) *") },
                            placeholder = { Text("4.2") },
                            isError = uiState.validationResult?.fatError != null,
                            supportingText = {
                                uiState.validationResult?.fatError?.let {
                                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = OutlineColor
                            )
                        )

                        OutlinedTextField(
                            value = uiState.snf,
                            onValueChange = { viewModel.onSnfChange(it) },
                            label = { Text("SNF (%) *") },
                            placeholder = { Text("8.6") },
                            isError = uiState.validationResult?.snfError != null,
                            supportingText = {
                                uiState.validationResult?.snfError?.let {
                                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = OutlineColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Payment Status
                    Text(
                        text = "Payment Status *",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaymentOptionChip(
                            label = "Pending",
                            isSelected = uiState.paymentStatus == "PENDING",
                            selectedColor = PendingAmber,
                            selectedBg = PendingAmberBg,
                            onClick = { viewModel.onPaymentStatusChange("PENDING") },
                            modifier = Modifier.weight(1f)
                        )

                        PaymentOptionChip(
                            label = "Paid",
                            isSelected = uiState.paymentStatus == "PAID",
                            selectedColor = PaidGreen,
                            selectedBg = PaidGreenBg,
                            onClick = { viewModel.onPaymentStatusChange("PAID") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (uiState.validationResult?.paymentStatusError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.validationResult?.paymentStatusError ?: "",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Save Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.saveRecord(onSuccess = {})
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Record (Store Locally)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Success Confirmation Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                viewModel.dismissSuccess()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PaidGreen,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text("Record Saved Offline", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Collection record for '${uiState.lastSavedFarmerName}' has been successfully validated and committed to local SQLite storage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.dismissSuccess()
                        onNavigateToRecords()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("View in Local Records")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.dismissSuccess()
                    }
                ) {
                    Text("Add Another Record")
                }
            }
        )
    }
}

@Composable
fun PaymentOptionChip(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    selectedBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) selectedBg else SurfaceLight,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) selectedColor else OutlineColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = selectedColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedColor else TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
