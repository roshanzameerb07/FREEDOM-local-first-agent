package com.example.freedom.ui.screens.ask

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freedom.theme.CardBackground
import com.example.freedom.theme.Gray100
import com.example.freedom.theme.OfflineGreen
import com.example.freedom.theme.OfflineGreenBg
import com.example.freedom.theme.OnPrimaryContainer
import com.example.freedom.theme.OutlineColor
import com.example.freedom.theme.PrimaryContainer
import com.example.freedom.theme.PrimaryGreen
import com.example.freedom.theme.PrimaryGreenDark
import com.example.freedom.theme.TextPrimary
import com.example.freedom.theme.TextSecondary
import com.example.freedom.theme.TextTertiary
import com.example.freedom.ui.screens.records.RecordCardItem

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
                            text = "FREEDOM Agent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Gemma 3 1B IT • Local-First Architecture",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Pipeline Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local Agent Conduit",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = OnPrimaryContainer
                            )
                        }
                        Surface(
                            color = OfflineGreenBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "100% OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OfflineGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Input → Gemma Intent/Tool Selection → Deterministic Validation & Calculation → Room SQLite / Local RAG.\nGemma never calculates totals or alters SQLite directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPrimaryContainer.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Query Input Box
            OutlinedTextField(
                value = uiState.queryInput,
                onValueChange = { viewModel.onQueryInputChange(it) },
                placeholder = { Text("Ask a question about records, profile, or rules...") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.submitQuery()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Submit query",
                            tint = PrimaryGreen
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.submitQuery()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = OutlineColor,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Curated Evaluation Queries
            Text(
                text = "Practical Evaluation Scenarios:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ExampleQueryChip(
                    category = "RECORD CREATION",
                    text = "Ramesh gave 18.5 litres, fat 4.2 and SNF 8.6. Payment is pending.",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitQuery("Ramesh gave 18.5 litres, fat 4.2 and SNF 8.6. Payment is pending.")
                    }
                )
                ExampleQueryChip(
                    category = "WORKER PROFILE",
                    text = "What is my officer ID and assigned area?",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitQuery("What is my officer ID and assigned area?")
                    }
                )
                ExampleQueryChip(
                    category = "DB AGGREGATION",
                    text = "How many farmers did I cover today?",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitQuery("How many farmers did I cover today?")
                    }
                )
                ExampleQueryChip(
                    category = "LOCAL RAG",
                    text = "When is payment considered complete?",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitQuery("When is payment considered complete?")
                    }
                )
                ExampleQueryChip(
                    category = "WEEKLY SUMMARY",
                    text = "How much did Ramesh give this week?",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitQuery("How much did Ramesh give this week?")
                    }
                )
                ExampleQueryChip(
                    category = "GUARDRAIL CHECK",
                    text = "Ramesh gave 80 litres.",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submitQuery("Ramesh gave 80 litres.")
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirmation Dialog for Tool Requests
            uiState.confirmationRequest?.let { request ->
                AlertDialog(
                    onDismissRequest = { viewModel.cancelToolExecution() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = PrimaryGreenDark,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Confirm Tool Action",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreenDark
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Selected Tool: ${request.intent.name}",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Source: User Prompt → Verified Extraction → Pending Room Insert",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Extracted Arguments (Numeric Precision Guarded):", fontWeight = FontWeight.Medium, color = TextSecondary)
                            if (request.args.isEmpty()) {
                                Text(text = "  (None)", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                            } else {
                                request.args.forEach { (key, value) ->
                                    val displayVal = if (value.isBlank()) "MISSING (Required)" else value
                                    val isMissing = value.isBlank()
                                    Text(
                                        text = "• $key: $displayVal",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (key in listOf("quantity", "fat", "snf")) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isMissing) Color.Red else TextPrimary
                                    )
                                }
                            }

                            if (request.intent.name == "CREATE_MILK_RECORD") {
                                val hasMissing = request.args["fat"].isNullOrBlank() || request.args["snf"].isNullOrBlank() || request.args["quantity"].isNullOrBlank()
                                if (hasMissing) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = Color(0xFFFFEBEE),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Guardrail Active: Incomplete data detected. Deterministic validator will reject missing Fat/SNF to prevent database corruption.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFC62828),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmToolExecution() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Text("Confirm & Execute")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelToolExecution() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                )
            }

            // Loading state
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PrimaryGreen)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Gemma 3 1B processing prompt on-device...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Tool Execution Result UI
            uiState.executionResult?.let { execResult ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when {
                                    execResult.ragDocument != null -> Icons.Default.Description
                                    execResult.capabilityUsed.contains("WORKER") || execResult.capabilityUsed.contains("ORGANIZATION") -> Icons.Default.Person
                                    else -> Icons.Default.Storage
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (execResult.success) PrimaryGreenDark else Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = execResult.capabilityUsed,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (execResult.success) PrimaryGreenDark else Color(0xFFC62828)
                                )
                            }
                            Surface(
                                color = if (execResult.success) OfflineGreenBg else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (execResult.success) "VERIFIED LOCAL" else "BLOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (execResult.success) OfflineGreen else Color(0xFFC62828),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Source of Truth Badge
                        Surface(
                            color = Gray100,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Source: ${execResult.sourceOfTruth}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Summary / Result Text
                        Text(
                            text = execResult.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        // Calculation details badge
                        execResult.calculationDetails?.let { calc ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = PrimaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Calculation: $calc",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryGreenDark,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Local RAG Evidence Box
                        execResult.ragDocument?.let { doc ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF1F8E9),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Local Knowledge Excerpt (${doc.clauseOrPage})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryGreenDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${doc.documentTitle} • ${doc.sectionTitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "\"${doc.content}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        // Extracted Parameters Breakdown
                        if (execResult.extractedParams.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Extracted Parameters:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            execResult.extractedParams.forEach { (k, v) ->
                                Text(
                                    text = "  • $k = $v",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }

                        // Display records if any returned
                        if (execResult.records.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Records (${execResult.records.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                            ) {
                                items(execResult.records, key = { it.id }) { record ->
                                    RecordCardItem(record = record)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Summary result handling
            if (!uiState.isLoading && uiState.lastResult != null) {
                val result = uiState.lastResult!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Local Query Result",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreenDark
                            )
                            Surface(
                                color = OfflineGreenBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "OFFLINE SQLITE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OfflineGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        if (result.records.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Matching Entries (${result.records.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                            ) {
                                items(result.records, key = { it.id }) { record ->
                                    RecordCardItem(record = record)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExampleQueryChip(
    category: String,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = CardBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, OutlineColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Gray100,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
