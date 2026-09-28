package com.example.freedom.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.freedom.data.local.FreedomDatabase
import com.example.freedom.data.repository.AuthRepository
import com.example.freedom.data.repository.MilkRecordRepositoryImpl
import com.example.freedom.theme.CardBackground
import com.example.freedom.theme.PrimaryGreen
import com.example.freedom.theme.TextSecondary
import com.example.freedom.ui.screens.ask.AskFreedomScreen
import com.example.freedom.ui.screens.ask.AskFreedomViewModel
import com.example.freedom.ui.screens.collection.RecordCollectionScreen
import com.example.freedom.ui.screens.collection.RecordCollectionViewModel
import com.example.freedom.ui.screens.home.HomeScreen
import com.example.freedom.ui.screens.home.HomeViewModel
import com.example.freedom.ui.screens.login.LoginScreen
import com.example.freedom.ui.screens.login.LoginViewModel
import com.example.freedom.ui.screens.records.LocalRecordsScreen
import com.example.freedom.ui.screens.records.LocalRecordsViewModel
import com.example.freedom.ui.screens.sync.SyncScreen
import com.example.freedom.ui.screens.sync.SyncViewModel

@Composable
fun FreedomApp() {
    val context = LocalContext.current

    // Initialize Room Database & Repositories as single source of truth
    val database = remember { FreedomDatabase.getInstance(context) }
    val milkRecordRepository = remember { MilkRecordRepositoryImpl(database.milkRecordDao()) }
    val authRepository = remember { AuthRepository() }

    // Navigation Stack State
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    val backStack = remember { mutableStateListOf<Screen>(Screen.Login) }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(screen)
            currentScreen = screen
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeLast()
            currentScreen = backStack.last()
        }
    }

    // Handle Hardware Back Button
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    val showBottomBar = currentScreen != Screen.Login && currentScreen != Screen.Sync

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CardBackground
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Home,
                        onClick = { navigateTo(Screen.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.RecordCollection,
                        onClick = { navigateTo(Screen.RecordCollection) },
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = "Collect") },
                        label = { Text("Collect") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.LocalRecords,
                        onClick = { navigateTo(Screen.LocalRecords) },
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Records") },
                        label = { Text("Records") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == Screen.AskFreedom,
                        onClick = { navigateTo(Screen.AskFreedom) },
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Ask") },
                        label = { Text("Ask") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentScreen) {
                is Screen.Login -> {
                    val loginViewModel: LoginViewModel = viewModel(
                        factory = LoginViewModel.Factory(authRepository)
                    )
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = {
                            backStack.clear()
                            navigateTo(Screen.Home)
                        }
                    )
                }

                is Screen.Home -> {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(milkRecordRepository, authRepository)
                    )
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToRecordCollection = { navigateTo(Screen.RecordCollection) },
                        onNavigateToLocalRecords = { navigateTo(Screen.LocalRecords) },
                        onNavigateToAskFreedom = { navigateTo(Screen.AskFreedom) },
                        onNavigateToSync = { navigateTo(Screen.Sync) },
                        onLogout = {
                            backStack.clear()
                            navigateTo(Screen.Login)
                        }
                    )
                }

                is Screen.RecordCollection -> {
                    val collectionViewModel: RecordCollectionViewModel = viewModel(
                        factory = RecordCollectionViewModel.Factory(milkRecordRepository)
                    )
                    RecordCollectionScreen(
                        viewModel = collectionViewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToRecords = {
                            backStack.removeLastOrNull()
                            navigateTo(Screen.LocalRecords)
                        }
                    )
                }

                is Screen.LocalRecords -> {
                    val recordsViewModel: LocalRecordsViewModel = viewModel(
                        factory = LocalRecordsViewModel.Factory(milkRecordRepository)
                    )
                    LocalRecordsScreen(
                        viewModel = recordsViewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.AskFreedom -> {
                    val askViewModel: AskFreedomViewModel = viewModel(
                        factory = AskFreedomViewModel.Factory(milkRecordRepository)
                    )
                    AskFreedomScreen(
                        viewModel = askViewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.Sync -> {
                    val syncViewModel: SyncViewModel = viewModel(
                        factory = SyncViewModel.Factory(milkRecordRepository)
                    )
                    SyncScreen(
                        viewModel = syncViewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }
            }
        }
    }
}
