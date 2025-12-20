package com.DamianKing12

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class SeriesKaoProvider : MainAPI() {
    override var mainUrl = "https://serieskao.top"
    override var name = "SeriesKao"
    override val lang = "es"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false
    override val usesWebView = true
    override val useTrackerLoading = true
    override val rateLimit = RateLimit(2, 1000)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    override suspend fun search(query: String): List<SearchResponse> = safeApiCall {
        val url = "$mainUrl/search?s=${query.urlEncode()}"
        val doc = app.get(url, headers = headers).document

        doc.select("a").filter { element ->
            val href = element.attr("href")
            href.contains("/pelicula/", ignoreCase = true) || href.contains("/serie/", ignoreCase = true)
        }.mapNotNull { el ->
            val href = el.attr("href")
            val title = el.selectFirst(".poster-card__title")?.text()?.trim() ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src") ?: ""
            val year = el.selectFirst(".poster-card__year")?.text()?.toIntOrNull()

            if (href.contains("/pelicula/", ignoreCase = true)) {
                MovieSearchResponse(title, href, name, TvType.Movie, poster, year)
            } else {
                TvSeriesSearchResponse(title, href, name, TvType.TvSeries, poster, year)
            }
        }.distinctBy { it.url }
    }.unwrapOr { emptyList() }

    override suspend fun load(url: String): LoadResponse = safeApiCall {
        val doc = app.get(url, headers = headers).document
        val isMovie = url.contains("/pelicula/", ignoreCase = true)
        val isSerie = url.contains("/serie/", ignoreCase = true)

        if (!isMovie && !isSerie) {
            throw ErrorLoadingException("URL no válida: $url")
        }

        val title = doc.selectFirst("h1")?.text()?.trim() 
            ?: doc.selectFirst(".original-title")?.text()?.trim() 
            ?: throw ErrorLoadingException("No se pudo obtener el título")
            
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content") ?: ""
        val description = doc.selectFirst(".synopsis, .description, .plot")?.text()?.trim()
            ?: doc.select("p").firstOrNull { it.text().length > 50 }?.text()?.trim()

        if (isMovie) {
            MovieLoadResponse(name = title, url = url, apiName = name, type = TvType.Movie, dataUrl = url, posterUrl = poster, plot = description)
        } else {
            val episodes = mutableListOf<Episode>()
            
            doc.select("#season-tabs li a[data-tab]").forEach { seasonLink ->
                val seasonId = seasonLink.attr("data-tab").substringAfter("season-").toIntOrNull() ?: return@forEach
                
                val seasonContent = doc.selectFirst("div.tab-content #season-${seasonId}")
                
                seasonContent?.select("a.episode-item")?.forEach { episodeLink ->
                    val href = episodeLink.attr("href").trim()
                    val epNumText = episodeLink.selectFirst(".episode-number")?.text() ?: ""
                    val epNum = epNumText.removePrefix("E").toIntOrNull() ?: 0
                    val epTitle = episodeLink.selectFirst(".episode-title")?.text()?.trim()
                    
                    if (href.isNotBlank() && epNum > 0) {
                        episodes.add(Episode(data = href, name = epTitle, season = seasonId, episode = epNum))
                    }
                }
            }

            TvSeriesLoadResponse(name = title, url = url, apiName = name, type = TvType.TvSeries, episodes = episodes, posterUrl = poster, plot = description)
        }
    }.unwrapOr { throw it }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = headers).document

        try {
            doc.select("track[kind=subtitles]").forEach { track ->
                val src = track.attr("src")
                if (src.isNotBlank()) {
                    subtitleCallback(SubtitleFile(
                        lang = track.attr("srclang") ?: "es",
                        url = src,
                        name = track.attr("label") ?: "Español"
                    ))
                }
            }
        } catch (e: Exception) {
            logError("Subtítulos no encontrados: ${e.message}")
        }

        val scriptElement = doc.selectFirst("script:containsData(var servers =)")
        if (scriptElement == null) {
            logError("❌ NO se encontró el script de servidores")
            return false
        }

        val serversJson = try {
            scriptElement.data().substringAfter("var servers = ").substringBefore(";").trim()
        } catch (e: Exception) {
            logError("Error extrayendo JSON: ${e.message}")
            return false
        }

        return try {
            val servers = parseJson<List<ServerData>>(serversJson)
            servers.forEach { server ->
                val cleanUrl = server.url.replace("\\/", "/")
                callback(ExtractorLink(
                    source = server.title,
                    name = server.title,
                    url = cleanUrl,
                    referer = mainUrl,
                    quality = getQuality(server.title),
                    isM3u8 = cleanUrl.contains(".m3u8", ignoreCase = true),
                    headers = headers
                ))
            }
            servers.isNotEmpty()
        } catch (e: Exception) {
            logError("Error parseando servidores: ${e.message}")
            false
        }
    }

    private fun getQuality(name: String): Int {
        return when {
            "1080" in name || "fullhd" in name -> Qualities.P1080.value
            "720" in name || "hd" in name -> Qualities.P720.value
            "480" in name || "sd" in name -> Qualities.P480.value
            else -> Qualities.Unknown.value
        }
    }

    data class ServerData(val id: Int, val title: String, val url: String)
}
