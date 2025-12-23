package com.DamianKing12

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SeriesKaoPlugin: Plugin() {
    override fun load(context: Context) {
        // Al ser explícitos evitamos que el compilador falle si hay micro-cambios en la API
        registerMainAPI(SeriesKaoProvider())
    }
}
