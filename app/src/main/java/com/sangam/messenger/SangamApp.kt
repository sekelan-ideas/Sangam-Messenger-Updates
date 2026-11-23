package com.sangam.messenger

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sangam.messenger.data.Sangam

/** Global UI state. */
object Ui {
    /**
     * Theme mode as chosen in the menu: "auto" follows the device theme,
     * "light" and "dark" force it. Applied live by SangamTheme.
     */
    var themeMode by mutableStateOf("auto")
    var desktopMode by mutableStateOf(false)
}

class SangamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sangam.init(this)
        Ui.themeMode = Sangam.themeMode()
        Ui.desktopMode = Sangam.desktopMode()
    }
}
