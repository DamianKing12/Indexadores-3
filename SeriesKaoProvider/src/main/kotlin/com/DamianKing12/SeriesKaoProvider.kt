package com.DamianKing12

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element

class SeriesKaoProvider : MainAPI() { // Esto es lo que faltaba: la CLASE
    override var mainUrl = "https://serieskao.tv" // Ajusta si la URL cambió
    override var name = "SeriesKao"
    override val hasMainPage = true
    override var lang = "es"
    override val hasQuickSearch = false

    // Aquí dentro va la función que te dio Kimi
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document // Eliminé 'headers' porque a veces da error si no están definidos

        // 1️⃣ SUBTÍTULOS
        doc.select("track[kind=subtitles]").forEach { track ->
            val src = track.attr("src")
            if (src.isNotBlank()) {
                subtitleCallback(
                    newSubtitleFile(
                        track.attr("srclang") ?: "es",
                        src
                    )
                )
            }
        }

        // 2️⃣ IFRAMES (donde está el reproductor real)
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                callback(
                    newExtractorLink(
                        name = "Enlace Externo",
                        source = "SeriesKao",
                        url = src
                    ).apply {
                        this.referer = mainUrl
                    }
                )
            }
        }

        // 3️⃣ MASTER.TXT (índice HLS)
        val masterScript = doc.select("script").map { it.data() }.firstOrNull { it.contains("master.txt") }
        if (masterScript != null) {
            val masterUrl = Regex("""(https?://[^"'\s]+master\.txt)""").find(masterScript)?.value
            if (masterUrl != null) {
                callback(
                    newExtractorLink(
                        name = "HLS (Directo)",
                        source = "SeriesKao",
                        url = masterUrl
                    ).apply {
                        this.referer = mainUrl
                        this.isM3u8 = true
                    }
                )
            }
        }

        // 4️⃣ SERVIDORES EN SCRIPT
        val scriptElement = doc.selectFirst("script:containsData(var servers =)")
        if (scriptElement != null) {
            val serversJson = scriptElement.data().substringAfter("var servers = ").substringBefore(";").trim()
            try {
                val servers = parseJson<List<ServerData>>(serversJson)
                servers.forEach { server ->
                    val cleanUrl = server.url.replace("\\/", "/")
                    callback(
                        newExtractorLink(
                            name = server.title,
                            source = server.title,
                            url = cleanUrl
                        ).apply {
                            this.isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true)
                            this.referer = mainUrl
                        }
                    )
                }
            } catch (e: Exception) {
                // Silencioso para que no rompa la carga
            }
        }

        return true
    }

    // Clase auxiliar para el JSON de servidores
    data class ServerData(
        val title: String,
        val url: String
    )
}
