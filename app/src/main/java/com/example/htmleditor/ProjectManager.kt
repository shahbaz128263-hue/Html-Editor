package com.example.htmleditor

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages the "current project" folder on internal storage.
 * A project is just a folder containing html/css/js/images/etc.
 * Works the same whether the project came from a ZIP or from
 * multiple individually-picked files.
 */
class ProjectManager(private val context: Context) {

    val projectRoot: File by lazy {
        File(context.filesDir, "projects/current").apply { mkdirs() }
    }

    /** Clears the current project folder to start fresh. */
    fun clearProject() {
        if (projectRoot.exists()) {
            projectRoot.deleteRecursively()
        }
        projectRoot.mkdirs()
    }

    /** Returns all files in the project (recursively), sorted with html first. */
    fun listProjectFiles(): List<File> {
        if (!projectRoot.exists()) return emptyList()
        val files = projectRoot.walkTopDown()
            .filter { it.isFile }
            .toList()
        return files.sortedWith(
            compareByDescending<File> { it.extension.equals("html", true) || it.extension.equals("htm", true) }
                .thenBy { it.name.lowercase() }
        )
    }

    /** Guesses the "main" HTML file: prefers index.html, else first html file found. */
    fun findMainHtmlFile(): File? {
        val htmlFiles = listProjectFiles().filter {
            it.extension.equals("html", true) || it.extension.equals("htm", true)
        }
        return htmlFiles.firstOrNull { it.name.equals("index.html", true) }
            ?: htmlFiles.firstOrNull()
    }

    /** Saves text content to a file inside the project (creates parent dirs if needed). */
    fun saveFile(relativePath: String, content: String): File {
        val target = File(projectRoot, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content)
        return target
    }

    /** Creates a new empty file with given name inside project root. */
    fun createNewFile(fileName: String): File {
        val target = File(projectRoot, fileName)
        target.parentFile?.mkdirs()
        if (!target.exists()) {
            target.createNewFile()
        }
        return target
    }

    fun deleteFile(file: File): Boolean = file.exists() && file.delete()

    fun renameFile(file: File, newName: String): File {
        val newFile = File(file.parentFile, newName)
        file.renameTo(newFile)
        return newFile
    }

    /**
     * Extracts a ZIP input stream into the project root.
     * Adds files into the existing project (does not clear automatically;
     * caller decides whether to clearProject() first).
     */
    fun extractZip(zipStream: ZipInputStream) {
        var entry: ZipEntry? = zipStream.nextEntry
        val buffer = ByteArray(8192)

        while (entry != null) {
            // Strip a potential single top-level folder wrapper (common in zips)
            val entryName = entry.name
            val safeName = sanitizeZipPath(entryName)

            if (safeName.isNotEmpty()) {
                val outFile = File(projectRoot, safeName)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        var len: Int
                        while (zipStream.read(buffer).also { len = it } > 0) {
                            out.write(buffer, 0, len)
                        }
                    }
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
    }

    /** Prevents zip-slip path traversal attacks. */
    private fun sanitizeZipPath(entryName: String): String {
        val normalized = entryName.replace("\\", "/")
        val parts = normalized.split("/").filter { it.isNotBlank() && it != "." && it != ".." }
        return parts.joinToString("/")
    }

    /** Exports the whole project folder as a ZIP file, returns the created zip File. */
    fun exportProjectAsZip(outputFile: File): File {
        ZipOutputStream(outputFile.outputStream()).use { zos ->
            val files = projectRoot.walkTopDown().filter { it.isFile }
            for (file in files) {
                val relativePath = file.relativeTo(projectRoot).path
                zos.putNextEntry(ZipEntry(relativePath))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return outputFile
    }

    /** Copies a single external file (from a picked Uri's stream) into the project root. */
    fun importSingleFile(fileName: String, inputStreamProvider: () -> java.io.InputStream) {
        val target = File(projectRoot, fileName)
        target.parentFile?.mkdirs()
        inputStreamProvider().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
