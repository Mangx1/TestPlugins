dependencies {
}

version = 1

cloudstream {
    description = "Mangx Hoofoot"
    authors = listOf("Mangx1")
    status = 1
    tvTypes = listOf("Sports")
    requiresResources = false
    language = "id"
    iconUrl = "https://hoofoot.com/favicon.ico"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
