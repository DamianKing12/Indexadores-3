package com.DamianKing12
//Hola
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class SeriesKaoProvider : MainAPI() {
    override var name = "SeriesKao"
    override var mainUrl = "https://serieskao.top"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // El buscador que ya sabemos que funciona
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("div.title a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("div.title a")?.attr("href") ?: ""
            val poster = it.selectFirst("img")?.attr("src")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    // NUEVA ESTRATEGIA: Copiando a Pelispedia
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1. Cargamos la página de la película
        val doc = app.get(data).document

        // 2. Buscamos todos los iframes (donde suelen estar los servidores)
        // Usamos .apmap para que busque en todos los servidores al mismo tiempo
        doc.select("iframe").amap {
            var iframeUrl = it.attr("src")

            // Si el link empieza por // lo arreglamos
            if (iframeUrl.startsWith("//")) {
                iframeUrl = "https:$iframeUrl"
            }

            // 3. LA MAGIA: Le pasamos el link al sistema de Cloudstream
            // loadExtractor intentará reconocer si es Fastream, Voe, Streamwish, etc.
            loadExtractor(iframeUrl, data, subtitleCallback, callback)
        }

        return true
    }
}
