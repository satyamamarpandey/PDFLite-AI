package com.pdfliteai.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdfliteai.ui.PdfScreen
import com.pdfliteai.ui.SettingsScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "pdf") {
        composable("pdf") { PdfScreen(onOpenSettings = { nav.navigate("settings") }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
