package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

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

                if (href.isBlank()) return@mapNotNull null

                val title = element.text().trim()

                if (title.isBlank()) return@mapNotNull null

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

        if (matches.isEmpty()) {
            throw ErrorLoadingException("Tidak menemukan pertandingan HooFoot")
        }

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

        val document = app.get(url).document

        val title =
            document.selectFirst("h1")?.text()?.trim()
                ?: document
                    .selectFirst("h2")
                    ?.text()
                    ?.trim()
                ?: "HooFoot Match"

        val poster =
            document
                .selectFirst("img")
                ?.attr("src")
                ?.let { fixUrl(it) }

        val description =
            document
                .select("body")
                .text()
                .substringAfter("Description")
                .take(1000)
                .trim()

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val iframeLinks = document
            .select("iframe[src]")
            .mapNotNull {
                it.attr("src")
                    .takeIf { src -> src.isNotBlank() }
                    ?.let { src -> fixUrl(src) }
            }
            .distinct()

        for (iframe in iframeLinks) {
            try {
                loadExtractor(
                    iframe,
                    data,
                    subtitleCallback,
                    callback
                )
            } catch (_: Exception) {
                // Coba iframe berikutnya
            }
        }

        return iframeLinks.isNotEmpty()
    }
}
