package com.lagradost

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

class MangxProvider : MainAPI() {

    override var mainUrl = "https://hoofoot.com"

    override var name = "Mangx Hoofoot"

    override val supportedTypes = setOf(TvType.TvSeries)

    override var lang = "id"

    override val hasMainPage = true
}
