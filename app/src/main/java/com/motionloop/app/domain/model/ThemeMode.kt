package com.motionloop.app.domain.model

/** App appearance. SYSTEM follows the phone's dark mode setting. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}
