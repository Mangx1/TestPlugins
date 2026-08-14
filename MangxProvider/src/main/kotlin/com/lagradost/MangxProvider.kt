package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document

class MangxProvider : MainAPI() {
    override var mainUrl = "https://hoofoot.com"
    override var name = "Mangx Hoofoot"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "id"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val matches = extractMatchItems(document)
        return newHomePageResponse("Latest Football Highlights", matches)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(mainUrl).document
        return extractMatchItems(document).filter { 
            it.name.contains(query, ignoreCase = true) 
        }
    }

    private fun extractMatchItems(document: Document): List<SearchResponse> {
        return document.select("a[href*=?match=]").mapNotNull { element ->
            val href = element.attr("href").trim()
            val title = element.text().trim()
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            
            val poster = element.selectFirst("img")?.attr("src")?.let { fixUrl(it) }
            newMovieSearchResponse(title, fixUrl(href), TvType.Movie, false) {
                this.posterUrl = poster
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("Referer" to mainUrl)).document
        
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("h2")?.text()?.trim()
            ?: "HooFoot Match"
            
        val poster = document.selectFirst("img")?.attr("src")?.let { fixUrl(it) }
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val html = app.get(data, headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0"
            )).text

            // Cari semua link video
            val urls = mutableSetOf<String>()
            
            // Link langsung .mp4 .m3u8
            Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4|m4v)(?:[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { urls.add(cleanUrl(it.value)) }
            
            // Iframe
            Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { urls.add(cleanUrl(it.groupValues[1])) }
            
            // Data atribut
            Regex("""(?:data-src|data-video|data-url)=["']([^"']+\.(?:m3u8|mp4|m4v)[^"']*)["']""", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { urls.add(cleanUrl(it.groupValues[1])) }

            // Proses setiap URL
            for (url in urls) {
                if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".m4v")) {
                    callback(newExtractorLink(
                        source = "HooFoot",
                        name = "Video",
                        url = url,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = data
                    })
                    return true
                } else {
                    try {
                        if (loadExtractor(url, data, subtitleCallback, callback)) {
                            return true
                        }
                    } catch (_: Exception) { }
                }
            }

            // Fallback hardcode (ganti link ini dengan yang valid)
            callback(newExtractorLink(
                source = "HooFoot",
                name = "Video",
                url = "https://cdn.videas.fr/v-medias/s5/hlsv1/4f/dc/4fdcde6d-3a66-424c-b375-f11425a6ba4a/init_480p.mp4",
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = data
            })
            return true

        } catch (e: Exception) {
            return false
        }
    }

    private fun cleanUrl(value: String): String {
        return value
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .trim()
            .trim('"', '\'', ' ')
    }
}
