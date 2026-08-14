package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class MangxProvider : MainAPI() {

    override var mainUrl = "https://hoofoot.com"

    override var name = "Mangx Hoofoot"

    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "id"

    override val hasMainPage = true

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(mainUrl).document

        val matches = document
            .select("a[href*=?match=]")
            .mapNotNull { element ->

                val href = element.attr("href").trim()
                val title = element.text().trim()

                if (href.isBlank() || title.isBlank()) {
                    return@mapNotNull null
                }

                val poster = element
                    .selectFirst("img")
                    ?.attr("src")
                    ?.let { fixUrl(it) }

                newMovieSearchResponse(
                    title,
                    fixUrl(href),
                    TvType.Movie,
                    false
                ) {
                    this.posterUrl = poster
                }
            }
            .distinctBy { it.url }

        return newHomePageResponse(
            "Latest Football Highlights",
            matches
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val document = app.get(mainUrl).document

        return document
            .select("a[href*=?match=]")
            .mapNotNull { element ->

                val href = element.attr("href").trim()
                val title = element.text().trim()

                if (href.isBlank() || title.isBlank()) {
                    return@mapNotNull null
                }

                if (!title.contains(query, ignoreCase = true)) {
                    return@mapNotNull null
                }

                val poster = element
                    .selectFirst("img")
                    ?.attr("src")
                    ?.let { fixUrl(it) }

                newMovieSearchResponse(
                    title,
                    fixUrl(href),
                    TvType.Movie,
                    false
                ) {
                    this.posterUrl = poster
                }
            }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(
            url,
            headers = mapOf(
                "Referer" to mainUrl
            )
        ).document

        val title =
            document.selectFirst("h1")?.text()?.trim()
                ?: document.selectFirst("h2")?.text()?.trim()
                ?: "HooFoot Match"

        val poster =
            document.selectFirst("img")
                ?.attr("src")
                ?.let { fixUrl(it) }

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
        }
    }

    private suspend fun collectVideoLinks(
        pageUrl: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        links: MutableSet<Pair<String, String>>
    ) {

        if (depth > 3) return
        if (!visited.add(pageUrl)) return

        val response = try {
            app.get(
                pageUrl,
                headers = mapOf(
                    "Referer" to referer,
                    "User-Agent" to "Mozilla/5.0"
                )
            )
        } catch (_: Exception) {
            return
        }

        val html = response.text

        fun cleanUrl(value: String): String {
            return value
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&")
                .trim()
                .trim('"', '\'', ' ')
        }

        /*
         * 1. Cari M3U8 dan MP4 langsung dari HTML / JavaScript.
         */
        val directVideoRegex = Regex(
            """https?://[^"'\\\s<>]+(?:\.m3u8|\.mp4|\.m4v)(?:\?[^"'\\\s<>]*)?""",
            RegexOption.IGNORE_CASE
        )

        directVideoRegex
            .findAll(html)
            .forEach { match ->
                val videoUrl = cleanUrl(match.value)

                if (
                    videoUrl.startsWith("http://") ||
                    videoUrl.startsWith("https://")
                ) {
                    links.add(videoUrl to pageUrl)
                }
            }

        /*
         * 2. Cari iframe / embed player.
         */
        val iframeRegex = Regex(
            """<iframe[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        iframeRegex
            .findAll(html)
            .forEach { match ->

                val rawUrl = cleanUrl(match.groupValues[1])

                if (rawUrl.isBlank()) return@forEach

                val iframeUrl = try {
                    fixUrl(rawUrl)
                } catch (_: Exception) {
                    return@forEach
                }

                if (
                    iframeUrl.startsWith("http://") ||
                    iframeUrl.startsWith("https://")
                ) {
                    collectVideoLinks(
                        iframeUrl,
                        pageUrl,
                        depth + 1,
                        visited,
                        links
                    )
                }
            }

        /*
         * 3. Cari source/video tag.
         */
        val sourceRegex = Regex(
            """<(?:source|video)[^>]+(?:src|file)=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        sourceRegex
            .findAll(html)
            .forEach { match ->

                val rawUrl = cleanUrl(match.groupValues[1])

                if (rawUrl.isBlank()) return@forEach

                val videoUrl = try {
                    fixUrl(rawUrl)
                } catch (_: Exception) {
                    return@forEach
                }

                if (
                    videoUrl.contains(".m3u8", ignoreCase = true) ||
                    videoUrl.contains(".mp4", ignoreCase = true) ||
                    videoUrl.contains(".m4v", ignoreCase = true)
                ) {
                    links.add(videoUrl to pageUrl)
                }
            }

        /*
         * 4. Beberapa player menyimpan URL di atribut data-src / data-file.
         */
        val dataVideoRegex = Regex(
            """(?:data-src|data-file|data-video|data-url)=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )

        dataVideoRegex
            .findAll(html)
            .forEach { match ->

                val rawUrl = cleanUrl(match.groupValues[1])

                if (
                    rawUrl.contains(".m3u8", ignoreCase = true) ||
                    rawUrl.contains(".mp4", ignoreCase = true) ||
                    rawUrl.contains(".m4v", ignoreCase = true)
                ) {

                    val videoUrl = try {
                        fixUrl(rawUrl)
                    } catch (_: Exception) {
                        return@forEach
                    }

                    links.add(videoUrl to pageUrl)
                }
            }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val document = try {
        app.get(
            data,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0"
            )
        ).document
    } catch (_: Exception) {
        return false
    }

    val playerUrls = linkedSetOf<String>()

    /*
     * Cari iframe player.
     */
    document.select("iframe").forEach { iframe ->

        val src = iframe.attr("src").trim()

        if (src.isNotBlank()) {
            try {
                val url = fixUrl(src)

                if (
                    url.startsWith("http://") ||
                    url.startsWith("https://")
                ) {
                    playerUrls.add(url)
                }
            } catch (_: Exception) {
            }
        }
    }

    /*
     * Cari player yang disimpan di data-src/data-url/data-video.
     */
    document.select("[data-src], [data-url], [data-video]")
        .forEach { element ->

            val raw = when {
                element.hasAttr("data-src") ->
                    element.attr("data-src")

                element.hasAttr("data-url") ->
                    element.attr("data-url")

                else ->
                    element.attr("data-video")
            }.trim()

            if (raw.isNotBlank()) {
                try {
                    val url = fixUrl(raw)

                    if (
                        url.startsWith("http://") ||
                        url.startsWith("https://")
                    ) {
                        playerUrls.add(url)
                    }
                } catch (_: Exception) {
                }
            }
        }

    /*
     * Serahkan setiap player ke extractor bawaan CloudStream.
     */
    var found = false

    for (playerUrl in playerUrls) {

        try {
            val extracted = loadExtractor(
                playerUrl,
                data,
                subtitleCallback,
                callback
            )

            if (extracted) {
                found = true
            }
        } catch (_: Exception) {
        }
    }

    /*
     * Fallback jika HooFoot memberikan link video langsung.
     */
    val html = try {
        app.get(
            data,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to "Mozilla/5.0"
            )
        ).text
    } catch (_: Exception) {
        ""
    }

    val directLinks = linkedSetOf<String>()

    Regex(
        """https?://[^"'\\\s<>]+(?:\.m3u8|\.mp4|\.m4v)(?:\?[^"'\\\s<>]*)?""",
        RegexOption.IGNORE_CASE
    )
        .findAll(html)
        .forEach { match ->

            val link = match.value
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&")
                .trim()

            if (
                link.startsWith("http://") ||
                link.startsWith("https://")
            ) {
                directLinks.add(link)
            }
        }

    for (link in directLinks) {

        val sourceName = when {
            link.contains(".m3u8", ignoreCase = true) ->
                "HooFoot HLS"

            link.contains(".mp4", ignoreCase = true) ->
                "HooFoot MP4"

            else ->
                "HooFoot Video"
        }

        callback(
            newExtractorLink(
                source = sourceName,
                name = sourceName,
                url = link,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = data
            }
        )

        found = true
    }

    return found
    }
