package com.example.htmleditor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileListAdapter(
    private var files: List<File>,
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Boolean
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.fileIcon)
        val name: TextView = view.findViewById(R.id.fileNameText)
    }

    fun updateFiles(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = file.relativeTo(file.parentFile?.let { findRootAncestor(it) } ?: file).path
            .ifEmpty { file.name }
        holder.name.text = file.name
        holder.icon.text = iconFor(file.extension)
        holder.itemView.setOnClickListener { onFileClick(file) }
        holder.itemView.setOnLongClickListener { onFileLongClick(file) }
    }

    override fun getItemCount(): Int = files.size

    private fun findRootAncestor(file: File): File = file

    private fun iconFor(extension: String): String = when (extension.lowercase()) {
        "html", "htm" -> "🌐"
        "css" -> "🎨"
        "js" -> "⚙️"
        "json" -> "🗂️"
        "png", "jpg", "jpeg", "gif", "webp", "svg" -> "🖼️"
        "woff", "woff2", "ttf", "otf" -> "🔤"
        else -> "📄"
    }
}
