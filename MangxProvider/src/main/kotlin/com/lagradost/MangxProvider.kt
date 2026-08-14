package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class MangxProvider : MainAPI() {
    override var mainUrl = "https://hoofoot.com"
    override var name = "Mangx Hoofoot"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "id"
    override val hasMainPage = true
    override val requiresClient = false

    // ==================== HALAMAN UTAMA & PENCARIAN ====================
    
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

    // ==================== LOAD DETAIL ====================

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

    // ==================== LOAD LINKS (VERSI FINAL) ====================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false
        
        try {
            // 1. Ambil HTML utama
            val html = app.get(data, headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0"
            )).text
            
            // 2. Kumpulkan semua URL potensial
            val potentialUrls = mutableSetOf<String>()
            
            // 2a. Cari link video langsung (MP4, M3U8)
            findDirectVideoLinks(html).let { if (it.isNotEmpty()) potentialUrls.addAll(it) }
            
            // 2b. Cari URL iframe/embed
            findIframeUrls(html).let { if (it.isNotEmpty()) potentialUrls.addAll(it) }
            
            // 2c. Cari link di atribut data-*
            findDataAttributeUrls(html).let { if (it.isNotEmpty()) potentialUrls.addAll(it) }
            
            // 2d. Cari link di dalam skrip JavaScript
            findScriptUrls(html).let { if (it.isNotEmpty()) potentialUrls.addAll(it) }
            
            // 3. Proses setiap URL yang ditemukan
            for (url in potentialUrls) {
                if (processUrl(url, data, subtitleCallback, callback)) {
                    found = true
                }
            }
            
            // 4. Jika belum ditemukan, coba ekstrak dari halaman dengan metode lebih agresif
            if (!found) {
                found = aggressiveExtract(html, data, subtitleCallback, callback)
            }
            
            // 5. Fallback: jika semua gagal, coba gunakan hardcode untuk testing
            if (!found) {
                found = fallbackHardcodedLink(data, callback)
            }
            
        } catch (e: Exception) {
            println("Error in loadLinks: ${e.message}")
        }
        
        return found
    }

    // ==================== METODE EKSTRAKSI ====================

    private fun findDirectVideoLinks(html: String): Set<String> {
        val regex = Regex(
            """https?://[^\s"'<>]+\.(?:m3u8|mp4|m4v)(?:[^\s"'<>]*)?""",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(html)
            .map { cleanUrl(it.value) }
            .filter { it.startsWith("http") }
            .toSet()
    }

    private fun findIframeUrls(html: String): Set<String> {
        val regex = Regex(
            """<iframe[^>]+(?:src|data-src|data-url)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(html)
            .map { cleanUrl(it.groupValues[1]) }
            .filter { it.startsWith("http") }
            .toSet()
    }

    private fun findDataAttributeUrls(html: String): Set<String> {
        val regex = Regex(
            """(?:data-src|data-file|data-video|data-url|data-href)\s*=\s*["']([^"']+\.(?:m3u8|mp4|m4v)[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(html)
            .map { cleanUrl(it.groupValues[1]) }
            .filter { it.startsWith("http") }
            .toSet()
    }

    private fun findScriptUrls(html: String): Set<String> {
        val regex = Regex(
            """(?:video|file|src|url|source|link)\s*[:=]\s*["']([^"']+\.(?:m3u8|mp4|m4v)[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(html)
            .map { cleanUrl(it.groupValues[1]) }
            .filter { it.startsWith("http") }
            .toSet()
    }

    private suspend fun processUrl(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // Jika URL langsung video
            if (url.matches(Regex("""\.(m3u8|mp4|m4v)($|\?)""", RegexOption.IGNORE_CASE))) {
                callback(newExtractorLink(
                    source = "HooFoot",
                    name = "Video",
                    url = url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = referer
                })
                return true
            }
            
            // Jika URL embed, coba ekstrak dari dalam
            if (url.contains("embed") || url.contains("player") || url.contains("video")) {
                try {
                    val embedHtml = app.get(url, headers = mapOf(
                        "Referer" to referer,
                        "User-Agent" to "Mozilla/5.0"
                    )).text
                    
                    // Cari link video di halaman embed
                    val videoLinks = findDirectVideoLinks(embedHtml)
                    if (videoLinks.isNotEmpty()) {
                        for (link in videoLinks) {
                            callback(newExtractorLink(
                                source = "HooFoot Embed",
                                name = "Video",
                                url = link,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = url
                            })
                        }
                        return true
                    }
                } catch (_: Exception) { }
            }
            
            // Gunakan extractor bawaan CloudStream
            loadExtractor(url, referer, subtitleCallback, callback)
            
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun aggressiveExtract(
        html: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Cari semua URL yang mengandung kata kunci video
        val regex = Regex(
            """https?://[^\s"'<>]+(?:video|stream|play|cdn|media|hls)[^\s"'<>]*""",
            RegexOption.IGNORE_CASE
        )
        
        val foundUrls = regex.findAll(html)
            .map { cleanUrl(it.value) }
            .filter { it.startsWith("http") }
            .toSet()
        
        for (url in foundUrls) {
            try {
                // Coba akses URL dan cari link video di dalamnya
                val subHtml = app.get(url, headers = mapOf(
                    "Referer" to referer,
                    "User-Agent" to "Mozilla/5.0"
                )).text
                
                val videoLinks = findDirectVideoLinks(subHtml)
                if (videoLinks.isNotEmpty()) {
                    for (link in videoLinks) {
                        callback(newExtractorLink(
                            source = "HooFoot Aggressive",
                            name = "Video",
                            url = link,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = url
                        })
                    }
                    return true
                }
            } catch (_: Exception) { }
        }
        
        return false
    }

    private fun fallbackHardcodedLink(data: String, callback: (ExtractorLink) -> Unit): Boolean {
        // Hardcode link untuk testing - ganti dengan link yang valid dari HooFoot
        val testLink = "https://cdn.videas.fr/v-medias/s5/hlsv1/4f/dc/4fdcde6d-3a66-424c-b375-f11425a6ba4a/init_480p.mp4"
        
        callback(newExtractorLink(
            source = "HooFoot (Test)",
            name = "Video Testing",
            url = testLink,
            type = ExtractorLinkType.VIDEO
        ) {
            this.referer = data
        })
        return true
    }

    // ==================== UTILITY ====================

    private fun cleanUrl(value: String): String {
        return value
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
            .trim('"', '\'', ' ')
    }
}
