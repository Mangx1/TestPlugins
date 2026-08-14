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

        val document = app.get(url).document

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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val links = mutableSetOf<String>()

        document
            .select(
                "iframe[src], iframe[data-src], iframe[data-url], embed[src], video[src]"
            )
            .forEach { element ->

                listOf(
                    element.attr("src"),
                    element.attr("data-src"),
                    element.attr("data-url")
                ).forEach { value ->

                    if (value.isNotBlank()) {
                        links.add(fixUrl(value))
                    }
                }
            }

        // HooFoot sometimes exposes the Streamable URL inside
        // the raw HTML rather than a normal iframe[src].
        Regex(
            """https?://(?:www\.)?streamable\.com/(?:e/)?[A-Za-z0-9]+"""
        ).findAll(document.html())
            .forEach {
                links.add(it.value)
            }

        var found = false

        for (link in links) {

            try {
                loadExtractor(
                    link,
                    data,
                    subtitleCallback,
                    callback
                )

                found = true

            } catch (_: Exception) {
            }
        }

        return found
    }
}
