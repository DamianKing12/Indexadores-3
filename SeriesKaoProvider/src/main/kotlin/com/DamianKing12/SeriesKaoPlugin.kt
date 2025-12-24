package com.DamianKing12

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.APIHolder.Companion.registerMainAPI
import android.content.Context

@CloudstreamPlugin
class SeriesKaoPlugin: Plugin() {
    override fun load(context: Context) {
        // Esto registra tu provider automáticamente al abrir la app
        registerMainAPI(SeriesKaoProvider())
    }
}
