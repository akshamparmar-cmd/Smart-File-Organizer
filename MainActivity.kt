package com.aksham.smartfileorganizer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

class MainActivity : Activity() {
    private val pickFolder = 100
    private var root: DocumentFile? = null
    private val planned = mutableListOf<Pair<DocumentFile, String>>()
    private lateinit var status: TextView
    private lateinit var preview: TextView
    private lateinit var organizeButton: Button

    private val groups = linkedMapOf(
        "PDF" to setOf("pdf"),
        "Word" to setOf("doc", "docx", "odt", "rtf"),
        "Excel" to setOf("xls", "xlsx", "csv", "ods"),
        "PowerPoint" to setOf("ppt", "pptx", "odp"),
        "Images" to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg"),
        "Videos" to setOf("mp4", "mkv", "mov", "avi", "webm", "3gp"),
        "Audio" to setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus"),
        "APK" to setOf("apk", "xapk", "apks"),
        "Archives" to setOf("zip", "rar", "7z", "tar", "gz")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }
        scroll.addView(layout)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "Smart File Organizer"
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        layout.addView(title)
        layout.addView(TextView(this).apply {
            text = "By Aksham Parmar  •  Scan → Preview → Organize"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        val choose = Button(this).apply {
            text = "1. Select Folder"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                startActivityForResult(intent, pickFolder)
            }
        }
        layout.addView(choose)

        val scan = Button(this).apply {
            text = "2. Scan & Preview"
            setOnClickListener { scanFolder() }
        }
        layout.addView(scan)

        organizeButton = Button(this).apply {
            text = "3. Organize (Copy Files)"
            isEnabled = false
            setOnClickListener { organizeFiles() }
        }
        layout.addView(organizeButton)

        status = TextView(this).apply {
            text = "Select a folder to begin."
            textSize = 16f
            setPadding(0, 20, 0, 12)
        }
        layout.addView(status)

        preview = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(0, 8, 0, 20)
        }
        layout.addView(preview)

        layout.addView(TextView(this).apply {
            text = "Safety: This first version COPIES files into category folders. It does not delete or modify originals. Review the preview before copying. Files already inside the app's category folders are skipped."
            textSize = 13f
            setPadding(0, 12, 0, 8)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFolder && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            root = DocumentFile.fromTreeUri(this, uri)
            status.text = "Selected: ${root?.name ?: "Folder"}"
            preview.text = ""
            planned.clear()
            organizeButton.isEnabled = false
        }
    }

    private fun categoryFor(file: DocumentFile): String {
        val ext = file.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: ""
        return groups.entries.firstOrNull { ext in it.value }?.key ?: "Others"
    }

    private fun scanFolder() {
        val folder = root
        if (folder == null) {
            status.text = "Please select a folder first."
            return
        }
        planned.clear()
        val lines = mutableListOf<String>()
        val protected = groups.keys + "Others"
        fun walk(dir: DocumentFile, path: String, isRoot: Boolean = false) {
            dir.listFiles().forEach { item ->
                val name = item.name ?: "Unnamed"
                if (item.isDirectory) {
                    if (isRoot && name in protected) return@forEach
                    walk(item, "$path$name/")
                } else if (item.isFile) {
                    val category = categoryFor(item)
                    planned.add(item to category)
                    lines.add("${planned.size}. $path$name  →  $category/")
                }
            }
        }
        walk(folder, "", true)
        preview.text = if (lines.isEmpty()) "No files found." else lines.joinToString("\n")
        status.text = "Found ${planned.size} file(s). Nothing has been changed."
        organizeButton.isEnabled = planned.isNotEmpty()
    }

    private fun uniqueName(dir: DocumentFile, original: String): String {
        if (dir.findFile(original) == null) return original
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var n = 1
        while (dir.findFile("${stem}_copy$n$ext") != null) n++
        return "${stem}_copy$n$ext"
    }

    private fun organizeFiles() {
        val folder = root ?: return
        var copied = 0
        var failed = 0
        planned.forEach { (source, category) ->
            try {
                val targetDir = folder.findFile(category)
                    ?: folder.createDirectory(category)
                    ?: throw Exception("Cannot create $category")
                val originalName = source.name ?: "file"
                val targetName = uniqueName(targetDir, originalName)
                val mime = source.type ?: "application/octet-stream"
                val target = targetDir.createFile(mime, targetName)
                    ?: throw Exception("Cannot create destination")
                contentResolver.openInputStream(source.uri).use { input ->
                    if (input == null) throw Exception("Cannot read source")
                    contentResolver.openOutputStream(target.uri, "w").use { output ->
                        if (output == null) throw Exception("Cannot write destination")
                        input.copyTo(output)
                    }
                }
                copied++
            } catch (_: Exception) {
                failed++
            }
        }
        status.text = "Finished. Copied: $copied  •  Failed: $failed\nOriginal files were kept unchanged."
        organizeButton.isEnabled = false
    }
}
