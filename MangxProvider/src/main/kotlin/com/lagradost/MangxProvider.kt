package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

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

        val html = app.get(data).text

        val links = mutableSetOf<String>()

        // Cari HLS Videas
        Regex(
            """https?://[^"'\\ ]+\.m3u8[^"'\\ ]*"""
        )
            .findAll(html)
            .forEach {
                links.add(it.value)
            }

        // Cari URL Videas yang mungkin tidak langsung berakhiran .m3u8
        Regex(
            """https?://cdn\.videas\.fr/[^"'\\ ]+"""
        )
            .findAll(html)
            .forEach {
                val link = it.value
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")

                if (link.contains(".m3u8")) {
                    links.add(link)
                }
            }

        var found = false

        for (link in links) {

            callback(
                newExtractorLink(
                    source = "Videas",
                    name = "Videas HLS",
                    url = link,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                }
            )

            found = true
        }

        return found
    }
}
