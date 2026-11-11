package com.monito.expense.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.YearMonth

data class CurrencySettings(
    val symbol: String = "₹",
    val symbolOnLeft: Boolean = true,
    /** '.' or ',' */
    val decimalSeparator: Char = '.',
    /** ',', '.', ' ' or null for none */
    val thousandSeparator: Char? = ',',
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val currency: CurrencySettings = CurrencySettings(),
    val showCarryForward: Boolean = true,
    val startWithGraphs: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dailyReminder: Boolean = false,
    /** HH:mm */
    val reminderTime: String = "22:00",
    val autoBackup: Boolean = true,
    val autoDeleteTrash: Boolean = false,
    val hideContent: Boolean = false,
    val screenLock: Boolean = false,
    val timePeriodCode: Int = TimePeriod.THIS_MONTH.code,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    /** Calendar month shown when [timePeriodCode] is [TimePeriod.MONTH]. */
    val monthAnchor: YearMonth? = null,
    val hasRunBefore: Boolean = false,
    /** Google account e-mail once Drive is linked. */
    val driveAccount: String? = null,
    val driveAutoUpload: Boolean = false,
    /** Whether an encryption passphrase for Drive copies is stored (the passphrase itself lives in SecretStore). */
    val drivePassphraseSet: Boolean = false,
) {
    val timePeriod: TimePeriod get() = TimePeriod.fromCode(timePeriodCode)
}

/** Simple SharedPreferences backed settings store exposed as a StateFlow for Compose. */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("monito_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings

    val current: Settings get() = _settings.value

    fun update(block: (Settings) -> Settings) {
        val new = block(_settings.value)
        save(new)
        _settings.value = new
    }

    private fun load(): Settings = Settings(
        currency = CurrencySettings(
            symbol = sp.getString(K_SYMBOL, "₹") ?: "₹",
            symbolOnLeft = sp.getBoolean(K_SYMBOL_LEFT, true),
            decimalSeparator = sp.getString(K_DEC_SEP, ".")?.firstOrNull() ?: '.',
            thousandSeparator = sp.getString(K_THOU_SEP, ",")?.firstOrNull(),
        ),
        showCarryForward = sp.getBoolean(K_CARRY_FORWARD, true),
        startWithGraphs = sp.getBoolean(K_START_GRAPHS, false),
        theme = ThemeMode.entries.getOrElse(sp.getInt(K_THEME, 0)) { ThemeMode.SYSTEM },
        dailyReminder = sp.getBoolean(K_REMIND, false),
        reminderTime = sp.getString(K_REMIND_TIME, "22:00") ?: "22:00",
        autoBackup = sp.getBoolean(K_AUTO_BACKUP, true),
        autoDeleteTrash = sp.getBoolean(K_AUTO_DELETE_TRASH, false),
        hideContent = sp.getBoolean(K_HIDE_CONTENT, false),
        screenLock = sp.getBoolean(K_SCREEN_LOCK, false),
        timePeriodCode = sp.getInt(K_PERIOD, TimePeriod.THIS_MONTH.code),
        customStart = sp.getString(K_CUSTOM_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        customEnd = sp.getString(K_CUSTOM_END, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        monthAnchor = sp.getString(K_MONTH, null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
        hasRunBefore = sp.getBoolean(K_HAS_RUN, false),
        driveAccount = sp.getString(K_DRIVE_ACCOUNT, null),
        driveAutoUpload = sp.getBoolean(K_DRIVE_AUTO, false),
        drivePassphraseSet = sp.getBoolean(K_DRIVE_PASS_SET, false),
    )

    private fun save(s: Settings) {
        sp.edit()
            .putString(K_SYMBOL, s.currency.symbol)
            .putBoolean(K_SYMBOL_LEFT, s.currency.symbolOnLeft)
            .putString(K_DEC_SEP, s.currency.decimalSeparator.toString())
            .putString(K_THOU_SEP, s.currency.thousandSeparator?.toString() ?: "")
            .putBoolean(K_CARRY_FORWARD, s.showCarryForward)
            .putBoolean(K_START_GRAPHS, s.startWithGraphs)
            .putInt(K_THEME, s.theme.ordinal)
            .putBoolean(K_REMIND, s.dailyReminder)
            .putString(K_REMIND_TIME, s.reminderTime)
            .putBoolean(K_AUTO_BACKUP, s.autoBackup)
            .putBoolean(K_AUTO_DELETE_TRASH, s.autoDeleteTrash)
            .putBoolean(K_HIDE_CONTENT, s.hideContent)
            .putBoolean(K_SCREEN_LOCK, s.screenLock)
            .putInt(K_PERIOD, s.timePeriodCode)
            .putString(K_CUSTOM_START, s.customStart?.toString())
            .putString(K_CUSTOM_END, s.customEnd?.toString())
            .putString(K_MONTH, s.monthAnchor?.toString())
            .putBoolean(K_HAS_RUN, s.hasRunBefore)
            .putString(K_DRIVE_ACCOUNT, s.driveAccount)
            .putBoolean(K_DRIVE_AUTO, s.driveAutoUpload)
            .putBoolean(K_DRIVE_PASS_SET, s.drivePassphraseSet)
            .apply()
    }

    companion object {
        private const val K_SYMBOL = "currencySymbol"
        private const val K_SYMBOL_LEFT = "symbolOnLeft"
        private const val K_DEC_SEP = "decimalSeparator"
        private const val K_THOU_SEP = "thousandSeparator"
        private const val K_CARRY_FORWARD = "isShowCarryForward"
        private const val K_START_GRAPHS = "startWithGraphs"
        private const val K_THEME = "currentTheme"
        private const val K_REMIND = "isNotify"
        private const val K_REMIND_TIME = "notifTime"
        private const val K_AUTO_BACKUP = "isAutoBackup"
        private const val K_AUTO_DELETE_TRASH = "isAutoDelete"
        private const val K_HIDE_CONTENT = "isHideContent"
        private const val K_SCREEN_LOCK = "isScreenLockSet"
        private const val K_PERIOD = "timePeriodCode"
        private const val K_CUSTOM_START = "startDate"
        private const val K_CUSTOM_END = "endDate"
        private const val K_MONTH = "monthAnchor"
        private const val K_HAS_RUN = "hasRunBefore"
        private const val K_DRIVE_ACCOUNT = "driveAccount"
        private const val K_DRIVE_AUTO = "driveAutoUpload"
        private const val K_DRIVE_PASS_SET = "drivePassphraseSet"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
