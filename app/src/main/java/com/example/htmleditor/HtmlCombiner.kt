package com.example.htmleditor

import java.io.File

/**
 * Helper for preparing a multi-file project for preview.
 *
 * Strategy: instead of manually inlining every <link>/<script> reference
 * (fragile with relative paths, subfolders, images, fonts, etc.), we load
 * the HTML file directly from disk with WebView.loadUrl("file://...").
 * As long as the file sits inside the project folder alongside its
 * css/js/images with correct relative paths, the WebView resolves them
 * automatically as file:// requests.
 *
 * This object still offers a manual "combine into single HTML string"
 * mode for cases where the caller wants one self-contained HTML blob
 * (e.g. to inject inline for the app-preview WebView using loadDataWithBaseURL,
 * or when only loose files without a real folder hierarchy are available).
 */
object HtmlCombiner {

    /**
     * Reads htmlFile's content and inlines any local (relative, non-http)
     * <link rel="stylesheet" href="..."> and <script src="..."> references
     * found in the same project, so the result is a single standalone HTML string.
     * Falls back gracefully: if a referenced file isn't found, the tag is left as-is.
     */
    fun buildStandaloneHtml(htmlFile: File, projectRoot: File): String {
        var html = htmlFile.readText()
        val baseDir = htmlFile.parentFile ?: projectRoot

        // Inline <link rel="stylesheet" href="X">
        val linkRegex = Regex("""<link[^>]+rel=["']stylesheet["'][^>]+href=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        html = linkRegex.replace(html) { match ->
            val href = match.groupValues[1]
            if (isRemote(href)) return@replace match.value
            val cssFile = resolveRelative(baseDir, href)
            if (cssFile != null && cssFile.exists()) {
                "<style>\n${cssFile.readText()}\n</style>"
            } else match.value
        }

        // Inline <script src="X"></script>
        val scriptRegex = Regex("""<script[^>]+src=["']([^"']+)["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
        html = scriptRegex.replace(html) { match ->
            val src = match.groupValues[1]
            if (isRemote(src)) return@replace match.value
            val jsFile = resolveRelative(baseDir, src)
            if (jsFile != null && jsFile.exists()) {
                "<script>\n${jsFile.readText()}\n</script>"
            } else match.value
        }

        return html
    }

    private fun isRemote(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//")

    private fun resolveRelative(baseDir: File, relativePath: String): File? {
        return try {
            File(baseDir, relativePath).canonicalFile
        } catch (e: Exception) {
            null
        }
    }

    /** Returns a file:// URL suitable for WebView.loadUrl(), preserving folder structure. */
    fun fileUrlFor(htmlFile: File): String = "file://${htmlFile.absolutePath}"
}
