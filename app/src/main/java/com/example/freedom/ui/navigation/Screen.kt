package com.example.freedom.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Login : Screen("login", "Login")
    object Home : Screen("home", "FREEDOM")
    object RecordCollection : Screen("record_collection", "Record Collection")
    object LocalRecords : Screen("local_records", "Local Records")
    object AskFreedom : Screen("ask_freedom", "Ask FREEDOM")
    object Sync : Screen("sync", "Batch Sync (Simulation)")
}
