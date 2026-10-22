pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // MuPDF (AGPL-3.0, Artifex): the PDF engine of Office's PDF app
        maven("https://maven.ghostscript.com") { content { includeGroup("com.artifex.mupdf") } }
        // Tesseract4Android (Apache 2.0): Greek and English OCR for scanned PDFs, offline
        maven("https://jitpack.io") { content { includeGroup("com.github.adaptech-cz.Tesseract4Android") } }
    }
}
rootProject.name = "Local Drive"
include(":app")

