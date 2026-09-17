package com.example.htmleditor

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HTML_PATH = "extra_html_path"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val toolbar = findViewById<MaterialToolbar>(R.id.previewToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val progress = findViewById<ProgressBar>(R.id.previewProgress)
        val webView = findViewById<WebView>(R.id.previewWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        webView.webViewClient = object : WebViewClient() {}
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }
        }

        val htmlPath = intent.getStringExtra(EXTRA_HTML_PATH)
        if (htmlPath != null) {
            val htmlFile = File(htmlPath)
            toolbar.title = htmlFile.name
            // Loading directly via file:// lets the WebView resolve relative
            // css/js/image paths on its own using the file's parent as base.
            webView.loadUrl(HtmlCombiner.fileUrlFor(htmlFile))
        } else {
            webView.loadData("<h3>No file to preview</h3>", "text/html", "UTF-8")
        }
    }
}
