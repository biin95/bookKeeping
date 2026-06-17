package com.biin95.bookkeeping.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddTransaction : Screen("add_transaction?transactionId={transactionId}") {
        fun createRoute(transactionId: Long? = null): String =
            if (transactionId != null) "add_transaction?transactionId=$transactionId"
            else "add_transaction"
    }
    data object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: Long): String = "transaction_detail/$transactionId"
    }
    data object Settings : Screen("settings")
    data object NlpInput : Screen("nlp_input")
}
