package com.example.freedom.ui.screens.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.freedom.framework.model.*
import com.example.freedom.framework.session.SessionManager
import com.example.freedom.theme.*

/**
 * Admin screen for local model management.
 *
 * Only visible to users with [com.example.freedom.framework.security.Permission.MANAGE_MODEL_PROVIDER].
 * FIELD_WORKER users never reach this screen (enforced at navigation level).
 *
 * Displays:
 * - Active FREEDOM built-in model
 * - List of imported models with status and actions
 * - Import workflow (SAF file picker → compatibility check → activate)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    viewModel: LocalModelsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val manifestList by viewModel.manifestList.collectAsState()
    val activeManifestId by viewModel.activeManifestId.collectAsState()
    val activeSession = SessionManager.getCurrentSessionOrNull()

    // File picker — accepts any file, we validate extension in ViewModel
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Local Models",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Organization model management",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryGreen)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // === Status message ===
            when (val state = uiState) {
                is LocalModelsViewModel.UiState.Importing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryGreen)
                }
                is LocalModelsViewModel.UiState.Checking -> {
                    Surface(
                        color = InfoBlueBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = InfoBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Running compatibility check...", style = MaterialTheme.typography.bodySmall, color = InfoBlue)
                        }
                    }
                }
                is LocalModelsViewModel.UiState.Info -> {
                    Surface(
                        color = OfflineGreenBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = OfflineGreen, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearMessage() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = OfflineGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                is LocalModelsViewModel.UiState.Error -> {
                    Surface(
                        color = ErrorRedBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = ErrorRed, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearMessage() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = ErrorRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                else -> {}
            }

            // === Active Model Section ===
            Text(
                "Active Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            val isImportedModelActive = activeManifestId != null

            // FREEDOM built-in model card
            ModelCard(
                name = "FREEDOM Built-in Model",
                subtitle = "Qwen3-1.7B • Bundled with FREEDOM",
                statusLabel = if (!isImportedModelActive) "ACTIVE" else "Available",
                statusColor = if (!isImportedModelActive) OfflineGreen else TextSecondary,
                statusBg = if (!isImportedModelActive) OfflineGreenBg else CardBackground,
                icon = Icons.Default.Memory,
                actions = {
                    if (isImportedModelActive) {
                        OutlinedButton(
                            onClick = { viewModel.useFreedomModel() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGreen)
                        ) {
                            Text("Use FREEDOM Model", color = PrimaryGreen, fontSize = 12.sp)
                        }
                    }
                }
            )

            // === Imported Models Section ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Imported Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Local Model", fontSize = 13.sp)
                }
            }

            if (manifestList.isEmpty()) {
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No models imported yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap \"Import Local Model\" to select a compatible .litertlm file from your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                manifestList.forEach { manifest ->
                    ImportedModelCard(
                        manifest = manifest,
                        isActive = manifest.modelId == activeManifestId,
                        onCheckCompatibility = { viewModel.checkCompatibility(manifest.modelId) },
                        onActivate = { viewModel.activateModel(manifest.modelId) },
                        onRemove = { viewModel.removeModel(manifest.modelId) }
                    )
                }
            }

            // === Guide Section ===
            Spacer(Modifier.height(8.dp))
            Surface(
                color = InfoBlueBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("How to import a model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = InfoBlue)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Obtain a compatible .litertlm model file (e.g., from Google AI Edge Gallery)\n" +
                        "2. Share or copy the file to a location accessible by the file picker\n" +
                        "3. Tap \"Import Local Model\" and select the file\n" +
                        "4. Run \"Check Compatibility\" to verify it works with FREEDOM\n" +
                        "5. Tap \"Activate\" to use it for all queries",
                        style = MaterialTheme.typography.bodySmall,
                        color = InfoBlue,
                        lineHeight = 18.sp
                    )
                }
            }

            // Org context footer
            activeSession?.let { session ->
                Text(
                    "Organization: ${session.organizationId}  •  Admin: ${session.currentUser?.displayName ?: "Unknown"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    name: String,
    subtitle: String,
    statusLabel: String,
    statusColor: Color,
    statusBg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(PrimaryGreen.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            val actionsContent: @Composable () -> Unit = actions
            val hasActions = true // always render slot
            Spacer(Modifier.height(8.dp))
            actionsContent()
        }
    }
}

@Composable
private fun ImportedModelCard(
    manifest: LocalModelManifest,
    isActive: Boolean,
    onCheckCompatibility: () -> Unit,
    onActivate: () -> Unit,
    onRemove: () -> Unit
) {
    val statusColor = when {
        isActive -> OfflineGreen
        manifest.status == ModelLifecycleStatus.COMPATIBLE_VERIFIED -> PrimaryGreen
        manifest.status == ModelLifecycleStatus.INCOMPATIBLE -> ErrorRed
        else -> PendingAmber
    }
    val statusBg = when {
        isActive -> OfflineGreenBg
        manifest.status == ModelLifecycleStatus.COMPATIBLE_VERIFIED -> PrimaryContainer
        manifest.status == ModelLifecycleStatus.INCOMPATIBLE -> ErrorRedBg
        else -> PendingAmberBg
    }
    val statusLabel = when {
        isActive -> "ACTIVE"
        manifest.status == ModelLifecycleStatus.COMPATIBLE_VERIFIED -> "COMPATIBLE"
        manifest.status == ModelLifecycleStatus.INCOMPATIBLE -> "INCOMPATIBLE"
        manifest.status == ModelLifecycleStatus.DEACTIVATED -> "DEACTIVATED"
        else -> "NOT CHECKED"
    }

    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) PrimaryGreen.copy(alpha = 0.4f) else OutlineColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(InfoBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(manifest.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "${manifest.runtimeType.name} • ${formatFileSize(manifest.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Metadata row
            Spacer(Modifier.height(8.dp))
            Text(
                "File: ${manifest.fileName}  •  SHA-256: ${manifest.sha256.take(12)}...",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            manifest.compatibilityReason?.let { reason ->
                if (manifest.status == ModelLifecycleStatus.INCOMPATIBLE) {
                    Spacer(Modifier.height(4.dp))
                    Text(reason, style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }

            // Actions
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (manifest.status == ModelLifecycleStatus.REGISTERED) {
                    OutlinedButton(
                        onClick = onCheckCompatibility,
                        border = androidx.compose.foundation.BorderStroke(1.dp, InfoBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Check Compatibility", color = InfoBlue, fontSize = 12.sp)
                    }
                }

                if (manifest.status == ModelLifecycleStatus.COMPATIBLE_VERIFIED && !isActive) {
                    Button(
                        onClick = onActivate,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Activate", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.weight(1f))

                if (!isActive) {
                    OutlinedButton(
                        onClick = onRemove,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Remove", color = ErrorRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
