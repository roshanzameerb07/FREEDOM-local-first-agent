package com.example.freedom.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freedom.domain.model.WorkerProfileRepository
import com.example.freedom.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToRecordCollection: () -> Unit,
    onNavigateToLocalRecords: () -> Unit,
    onNavigateToAskFreedom: () -> Unit,
    onNavigateToSync: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = com.example.freedom.R.drawable.freedom_logo),
                            contentDescription = "FREEDOM Logo",
                            modifier = Modifier
                                .size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FREEDOM",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Dairy Field Operations",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "My Profile",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Offline Status Notice
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OfflineGreenBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OfflineGreen.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(OfflineGreen, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ready • Working Offline",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = OfflineGreen
                        )
                        Text(
                            text = "All collection and payment records are saved on device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Officer Welcome Header
            uiState.currentUser?.let { user ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome, ${user.workerName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "ID: ${user.workerId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    TextButton(onClick = { showProfileDialog = true }) {
                        Text("My Profile", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Summary Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Today's Volume",
                    value = "${String.format("%.1f", uiState.todayLitres)} L",
                    subtitle = "Collected",
                    icon = Icons.Default.WaterDrop,
                    iconTint = PrimaryGreen,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "Entries Today",
                    value = "${uiState.recordsTodayCount}",
                    subtitle = "Collections",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    iconTint = InfoBlue,
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    title = "To Send",
                    value = "${uiState.pendingUploadCount}",
                    subtitle = "Pending sync",
                    icon = Icons.Default.CloudUpload,
                    iconTint = PendingAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Field Tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action 1: Collect Milk
            FieldActionCard(
                title = "Collect Milk",
                subtitle = "Record farmer deliveries, fat %, SNF and payment",
                icon = Icons.Default.AddCircle,
                badgeText = "Fast Entry",
                containerColor = PrimaryContainer,
                onClick = onNavigateToRecordCollection
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action 2: Records & Payments
            FieldActionCard(
                title = "Records & Payments",
                subtitle = "Browse deliveries, review pending payments and mark paid",
                icon = Icons.Default.FolderOpen,
                badgeText = "${uiState.totalRecordsCount} Records",
                containerColor = CardBackground,
                onClick = onNavigateToLocalRecords
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action 3: Ask Assistant
            FieldActionCard(
                title = "Ask FREEDOM",
                subtitle = "Ask questions about deliveries, payments, or cooperative policies",
                icon = Icons.Default.ChatBubble,
                badgeText = "Assistant",
                containerColor = CardBackground,
                onClick = onNavigateToAskFreedom
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action 4: Send Data (Sync)
            FieldActionCard(
                title = "Send Data",
                subtitle = "Upload pending collections when connected at the center",
                icon = Icons.Default.Sync,
                badgeText = "${uiState.pendingUploadCount} Pending",
                containerColor = CardBackground,
                onClick = onNavigateToSync
            )
        }
    }

    // Profile Dialog
    if (showProfileDialog) {
        val profile = WorkerProfileRepository.getProfile()
        val org = WorkerProfileRepository.getOrganizationInfo()

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text("Officer & Cooperative Profile", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    DetailRow("Officer ID", profile.workerId)
                    DetailRow("Officer Name", profile.workerName)
                    DetailRow("Role", profile.role)
                    DetailRow("Assigned Area", profile.assignedArea)
                    DetailRow("Center", profile.centerName)
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Gray100)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Cooperative", org.organizationName)
                    DetailRow("Org ID", org.organizationId)
                    DetailRow("District", org.regionalDistrict)
                    DetailRow("Registration", org.registrationNumber)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showProfileDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun FieldActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(PrimaryGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = PrimaryGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreenDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
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
