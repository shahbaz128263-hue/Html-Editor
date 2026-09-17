package com.example.htmleditor

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var projectManager: ProjectManager
    private lateinit var editorWebView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var fileListRecyclerView: RecyclerView
    private lateinit var fileListAdapter: FileListAdapter
    private lateinit var currentFileNameText: TextView

    private var currentOpenFile: File? = null
    private var editorReady = false
    private var pendingContentToLoad: Pair<String, String>? = null // content, lang

    // ---- Activity result launchers ----

    private val openZipLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleZipImport(it) }
    }

    private val openFilesLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) handleMultiFileImport(uris)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectManager = ProjectManager(this)

        drawerLayout = findViewById(R.id.drawerLayout)
        editorWebView = findViewById(R.id.editorWebView)
        fileListRecyclerView = findViewById(R.id.fileListRecyclerView)
        currentFileNameText = findViewById(R.id.currentFileName)

        val topToolbar = findViewById<MaterialToolbar>(R.id.topToolbar)
        topToolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        topToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_project -> { confirmNewProject(); true }
                R.id.action_rename -> { renameCurrentFile(); true }
                R.id.action_delete -> { deleteCurrentFile(); true }
                R.id.action_share_zip -> { exportProjectZip(); true }
                else -> false
            }
        }

        setupEditorWebView()
        setupFileList()
        setupBottomButtons()
        setupQuickInsertBar()

        // Load any existing project files on start
        refreshFileList()
    }

    // ================= Editor WebView setup =================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupEditorWebView() {
        editorWebView.settings.javaScriptEnabled = true
        editorWebView.settings.domStorageEnabled = true
        editorWebView.addJavascriptInterface(EditorBridge(), "AndroidBridge")
        editorWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                editorReady = true
                pendingContentToLoad?.let { (content, lang) ->
                    loadContentIntoEditor(content, lang)
                    pendingContentToLoad = null
                }
            }
        }
        editorWebView.loadUrl("file:///android_asset/editor/index.html")
    }

    inner class EditorBridge {
        @JavascriptInterface
        fun onContentChanged() {
            // Could show an "unsaved changes" dot; kept simple for now.
        }
    }

    private fun loadContentIntoEditor(content: String, lang: String) {
        val escaped = org.json.JSONObject.quote(content)
        editorWebView.evaluateJavascript("setContent($escaped, '$lang')", null)
    }

    private fun getEditorContent(callback: (String) -> Unit) {
        editorWebView.evaluateJavascript("getContent()") { result ->
            // result comes back as a JSON-quoted string; unquote it
            val unquoted = try {
                org.json.JSONTokener(result).nextValue() as String
            } catch (e: Exception) {
                result.trim('"')
            }
            callback(unquoted)
        }
    }

    private fun langForFile(file: File): String = when (file.extension.lowercase()) {
        "css" -> "css"
        "js" -> "js"
        else -> "html"
    }

    // ================= File list (drawer) =================

    private fun setupFileList() {
        fileListAdapter = FileListAdapter(
            files = projectManager.listProjectFiles(),
            onFileClick = { file -> openFileInEditor(file); drawerLayout.closeDrawers() },
            onFileLongClick = { file -> showFileOptionsDialog(file); true }
        )
        fileListRecyclerView.layoutManager = LinearLayoutManager(this)
        fileListRecyclerView.adapter = fileListAdapter

        findViewById<MaterialButton>(R.id.btnOpenZip).setOnClickListener {
            openZipLauncher.launch("application/zip")
        }
        findViewById<MaterialButton>(R.id.btnOpenFiles).setOnClickListener {
            openFilesLauncher.launch("*/*")
        }
        findViewById<MaterialButton>(R.id.btnNewFile).setOnClickListener {
            promptNewFileName()
        }
    }

    private fun refreshFileList() {
        fileListAdapter.updateFiles(projectManager.listProjectFiles())
    }

    private fun showFileOptionsDialog(file: File) {
        val options = arrayOf("Open", "Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { openFileInEditor(file); drawerLayout.closeDrawers() }
                    1 -> promptRename(file)
                    2 -> confirmDelete(file)
                }
            }
            .show()
    }

    private fun promptNewFileName() {
        val input = EditText(this)
        input.hint = "e.g. about.html, style.css, script.js"
        AlertDialog.Builder(this)
            .setTitle("New file name")
            .setView(wrapInputPadding(input))
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val file = projectManager.createNewFile(name)
                    refreshFileList()
                    openFileInEditor(file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptRename(file: File) {
        val input = EditText(this)
        input.setText(file.name)
        AlertDialog.Builder(this)
            .setTitle("Rename file")
            .setView(wrapInputPadding(input))
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val renamed = projectManager.renameFile(file, newName)
                    if (currentOpenFile?.absolutePath == file.absolutePath) {
                        currentOpenFile = renamed
                        currentFileNameText.text = renamed.name
                    }
                    refreshFileList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${file.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                projectManager.deleteFile(file)
                if (currentOpenFile?.absolutePath == file.absolutePath) {
                    currentOpenFile = null
                    currentFileNameText.text = "No file open"
                    loadContentIntoEditor("", "html")
                }
                refreshFileList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameCurrentFile() {
        currentOpenFile?.let { promptRename(it) } ?: toast("Open a file first")
    }

    private fun deleteCurrentFile() {
        currentOpenFile?.let { confirmDelete(it) } ?: toast("Open a file first")
    }

    private fun wrapInputPadding(input: EditText): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        layout.setPadding(padding, padding / 2, padding, 0)
        layout.addView(input)
        return layout
    }

    private fun confirmNewProject() {
        AlertDialog.Builder(this)
            .setTitle("Start new project?")
            .setMessage("This will clear all current project files.")
            .setPositiveButton("Clear") { _, _ ->
                projectManager.clearProject()
                currentOpenFile = null
                currentFileNameText.text = "No file open"
                loadContentIntoEditor("", "html")
                refreshFileList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================= Opening / Saving =================

    private fun openFileInEditor(file: File) {
        // Save currently open file before switching, so edits aren't lost
        saveCurrentFileSilently {
            currentOpenFile = file
            currentFileNameText.text = file.name
            val content = if (file.exists()) file.readText() else ""
            val lang = langForFile(file)
            if (editorReady) {
                loadContentIntoEditor(content, lang)
            } else {
                pendingContentToLoad = Pair(content, lang)
            }
        }
    }

    private fun saveCurrentFileSilently(afterSaved: (() -> Unit)? = null) {
        val file = currentOpenFile
        if (file == null) {
            afterSaved?.invoke()
            return
        }
        getEditorContent { content ->
            file.writeText(content)
            afterSaved?.invoke()
        }
    }

    private fun setupBottomButtons() {
        findViewById<MaterialButton>(R.id.btnOpen).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val file = currentOpenFile
            if (file == null) {
                toast("No file open to save")
            } else {
                getEditorContent { content ->
                    file.writeText(content)
                    toast("Saved ${file.name}")
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnPreviewApp).setOnClickListener {
            previewInApp()
        }

        findViewById<MaterialButton>(R.id.btnPreviewBrowser).setOnClickListener {
            previewInBrowser()
        }
    }

    private fun setupQuickInsertBar() {
        val bar = findViewById<LinearLayout>(R.id.quickInsertBar)
        val snippets = listOf(
            "<div></div>" to "<div>",
            "<h1></h1>" to "<h1>",
            "<p></p>" to "<p>",
            "<a href=\"\"></a>" to "<a>",
            "<img src=\"\">" to "<img>",
            "<span></span>" to "<span>",
            "<button></button>" to "<button>",
            "{ }" to "{ }",
            ";" to ";"
        )
        for ((snippet, label) in snippets) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btn.text = label
            btn.textSize = 11f
            btn.setPadding(20, 0, 20, 0)
            btn.isAllCaps = false
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 8
            btn.layoutParams = params
            btn.setOnClickListener {
                val escaped = org.json.JSONObject.quote(snippet)
                editorWebView.evaluateJavascript("insertAtCursor($escaped)", null)
            }
            bar.addView(btn)
        }
    }

    // ================= Preview =================

    private fun previewInApp() {
        saveCurrentFileSilently {
            val mainHtml = resolvePreviewTarget()
            if (mainHtml == null) {
                toast("No HTML file found in project")
                return@saveCurrentFileSilently
            }
            val intent = Intent(this, PreviewActivity::class.java)
            intent.putExtra(PreviewActivity.EXTRA_HTML_PATH, mainHtml.absolutePath)
            startActivity(intent)
        }
    }

    private fun previewInBrowser() {
        saveCurrentFileSilently {
            val mainHtml = resolvePreviewTarget()
            if (mainHtml == null) {
                toast("No HTML file found in project")
                return@saveCurrentFileSilently
            }
            try {
                val uri = FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", mainHtml
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "text/html")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                toast("No browser app found to open the file")
            }
        }
    }

    /**
     * Decides which HTML file to preview: the currently open one if it's HTML,
     * otherwise the project's main/index.html.
     */
    private fun resolvePreviewTarget(): File? {
        val current = currentOpenFile
        if (current != null && (current.extension.equals("html", true) || current.extension.equals("htm", true))) {
            return current
        }
        return projectManager.findMainHtmlFile()
    }

    // ================= Import: ZIP & multi-file =================

    private fun handleZipImport(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    projectManager.extractZip(zis)
                }
            }
            refreshFileList()
            val main = projectManager.findMainHtmlFile()
            if (main != null) {
                openFileInEditor(main)
            }
            toast("ZIP imported successfully")
        } catch (e: Exception) {
            toast("Failed to import ZIP: ${e.message}")
        }
    }

    private fun handleMultiFileImport(uris: List<Uri>) {
        try {
            for (uri in uris) {
                val name = queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
                projectManager.importSingleFile(name) {
                    contentResolver.openInputStream(uri) ?: throw Exception("Cannot open $uri")
                }
            }
            refreshFileList()
            val main = projectManager.findMainHtmlFile()
            if (main != null) {
                openFileInEditor(main)
            }
            toast("${uris.size} file(s) imported")
        } catch (e: Exception) {
            toast("Failed to import files: ${e.message}")
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }

    // ================= Export =================

    private fun exportProjectZip() {
        saveCurrentFileSilently {
            try {
                val exportsDir = File(cacheDir, "exports").apply { mkdirs() }
                val zipFile = File(exportsDir, "project_export.zip")
                projectManager.exportProjectAsZip(zipFile)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", zipFile)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "application/zip"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, "Share project ZIP"))
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
            }
        }
    }

    // ================= Utils =================

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        // Auto-save on pause so nothing is lost if the app is backgrounded
        saveCurrentFileSilently()
    }
}
