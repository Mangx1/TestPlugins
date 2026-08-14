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

            val iframeRegex = Regex("""<iframe[^>]+src=["'](https://app\.videas\.fr/embed/media/[^"']+)["']""", RegexOption.IGNORE_CASE)
            val iframeMatches = iframeRegex.findAll(html).toList()
            
            if (iframeMatches.isNotEmpty()) {
                val iframeUrl = iframeMatches.first().groupValues[1]
                
                try {
                    val success = loadExtractor(iframeUrl, data, subtitleCallback, callback)
                    if (success) {
                        return true
                    }
                } catch (e: Exception) {
                }
                
                try {
                    val embedHtml = app.get(iframeUrl, headers = mapOf(
                        "Referer" to data,
                        "User-Agent" to "Mozilla/5.0"
                    )).text
                    
                    val videoRegex = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4|m4v)(?:[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
                    val videoMatches = videoRegex.findAll(embedHtml).toList()
                    
                    if (videoMatches.isNotEmpty()) {
                        val videoUrl = videoMatches.first().value
                        
                        callback(newExtractorLink(
                            source = "HooFoot",
                            name = "Video",
                            url = videoUrl,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = iframeUrl
                        })
                        return true
                    }
                } catch (e: Exception) {
                }
            }

            val directVideoRegex = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4|m4v)(?:[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            val directMatches = directVideoRegex.findAll(html).toList()
            
            if (directMatches.isNotEmpty()) {
                val videoUrl = directMatches.first().value
                
                callback(newExtractorLink(
                    source = "HooFoot",
                    name = "Video",
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                })
                return true
            }

            return false

        } catch (e: Exception) {
            return false
        }
    }
}
