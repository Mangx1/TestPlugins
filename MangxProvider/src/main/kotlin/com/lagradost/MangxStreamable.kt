package com.lagradost

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class MangxStreamable : ExtractorApi() {

    override var name = "Streamable"

    override var mainUrl = "https://streamable.com"

    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): List<ExtractorLink>? {

        val match = Regex(
            """streamable\.com/(?:e/)?([A-Za-z0-9]+)"""
        ).find(url)

        val id = match?.groupValues?.get(1)
            ?: return null

        val apiUrl = "https://api.streamable.com/videos/$id"

        val response = app.get(apiUrl)

        if (!response.isSuccessful) {
            return null
        }

        val json = response.text

        val mp4Url = Regex(
            """"mp4"\s*:\s*\{\s*"url"\s*:\s*"([^"]+)""""
        ).find(json)
            ?.groupValues
            ?.get(1)
            ?.replace("\\/", "/")
            ?.replace("\\u0026", "&")

        if (mp4Url.isNullOrBlank()) {
            return null
        }

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = mp4Url,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
