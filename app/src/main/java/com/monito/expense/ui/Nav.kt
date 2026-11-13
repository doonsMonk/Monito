package com.monito.expense.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monito.expense.data.Prefs
import com.monito.expense.ui.budget.BudgetsScreen
import com.monito.expense.ui.category.AddCategoryScreen
import com.monito.expense.ui.category.CategoriesScreen
import com.monito.expense.ui.main.CategoryRecordsScreen
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.dialog
import com.monito.expense.ui.main.MainScreen
import com.monito.expense.ui.main.SearchScreen
import com.monito.expense.ui.record.AddRecordScreen
import com.monito.expense.ui.record.TemplatesScreen
import com.monito.expense.ui.recurrence.AddRecurrenceScreen
import com.monito.expense.ui.recurrence.RecurrencesScreen
import com.monito.expense.ui.settings.BackupsScreen
import com.monito.expense.ui.settings.DriveScreen
import com.monito.expense.ui.settings.FirstRunScreen
import com.monito.expense.ui.settings.SettingsScreen
import com.monito.expense.ui.trash.TrashScreen
import com.monito.expense.ui.trends.TrendsScreen

object Routes {
    const val MAIN = "main"
    const val FIRST_RUN = "firstRun"
    const val RECORD = "record?id={id}&date={date}&template={template}"
    const val CATEGORIES = "categories"
    const val CATEGORY = "category?id={id}"
    const val RECURRENCES = "recurrences"
    const val RECURRENCE = "recurrence?id={id}"
    const val TRASH = "trash"
    const val SEARCH = "search"
    const val CATEGORY_RECORDS = "catRecords/{catId}"
    const val SETTINGS = "settings"
    const val BACKUPS = "backups"
    const val TEMPLATES = "templates"
    const val BUDGETS = "budgets"
    const val TRENDS = "trends"
    const val DRIVE = "drive"

    fun record(id: Int = -1, date: String? = null, templateId: Int = -1) = "record?id=$id&date=${date ?: ""}&template=$templateId"
    fun category(id: Int = -1) = "category?id=$id"
    fun recurrence(id: Int = -1) = "recurrence?id=$id"
    fun categoryRecords(catId: Int) = "catRecords/$catId"
}

@Composable
fun MonitoNavHost(pendingAddTransaction: MutableState<Boolean>) {
    val nav: NavHostController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val start = if (settings.hasRunBefore) Routes.MAIN else Routes.FIRST_RUN

    LaunchedEffect(pendingAddTransaction.value) {
        if (pendingAddTransaction.value && settings.hasRunBefore) {
            pendingAddTransaction.value = false
            nav.navigate(Routes.record())
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.FIRST_RUN) { FirstRunScreen(nav) }
        composable(Routes.MAIN) { MainScreen(nav) }
        dialog(
            Routes.RECORD,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
            arguments = listOf(
                navArgument("id") { type = NavType.IntType; defaultValue = -1 },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
                navArgument("template") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            AddRecordScreen(
                nav = nav,
                recordId = entry.arguments?.getInt("id") ?: -1,
                presetDate = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() },
                templateId = entry.arguments?.getInt("template") ?: -1,
            )
        }
        composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
        composable(
            Routes.CATEGORY,
            arguments = listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 }),
        ) { entry -> AddCategoryScreen(nav, entry.arguments?.getInt("id") ?: -1) }
        composable(Routes.RECURRENCES) { RecurrencesScreen(nav) }
        composable(
            Routes.RECURRENCE,
            arguments = listOf(navArgument("id") { type = NavType.IntType; defaultValue = -1 }),
        ) { entry -> AddRecurrenceScreen(nav, entry.arguments?.getInt("id") ?: -1) }
        composable(Routes.TRASH) { TrashScreen(nav) }
        composable(Routes.SEARCH) { SearchScreen(nav) }
        composable(
            Routes.CATEGORY_RECORDS,
            arguments = listOf(navArgument("catId") { type = NavType.IntType }),
        ) { entry -> CategoryRecordsScreen(nav, entry.arguments?.getInt("catId") ?: -1) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.BACKUPS) { BackupsScreen(nav) }
        composable(Routes.TEMPLATES) { TemplatesScreen(nav) }
        composable(Routes.BUDGETS) { BudgetsScreen(nav) }
        composable(Routes.TRENDS) { TrendsScreen(nav) }
        composable(Routes.DRIVE) { DriveScreen(nav) }
    }
}
