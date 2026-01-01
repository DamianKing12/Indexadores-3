package com.DamianKing12

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class CuevanaProvider : MainAPI() {
    override var name = "Cuevana"
    override var mainUrl = "https://www.cuevana.biz"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "es"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val response = app.get(url)
        val document = response.document

        return document.select("div.item, article.item").mapNotNull {
            val title = it.selectFirst(".title, h2")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: ""
            val poster = it.selectFirst("img")?.attr("src")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("iframe, source").amap {
            var iframeUrl = it.attr("src")
            if (iframeUrl.isEmpty()) iframeUrl = it.attr("data-src")

            if (iframeUrl.isNotEmpty()) {
                if (iframeUrl.startsWith("//")) {
                    iframeUrl = "https:$iframeUrl"
                }
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
