package com.example.notepad98 // Replace with your actual package name

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var editTextNotepad: EditText
    private var currentFileUri: Uri? = null // To keep track of the currently open file URI

    // Activity Result Launchers for SAF (Storage Access Framework)
    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let {
            currentFileUri = it // Remember the URI for direct saving later
            saveFile(it)
        }
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            currentFileUri = it // Remember the URI
            readFileContent(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Untitled - Notepad" // Classic title

        editTextNotepad = findViewById(R.id.editTextNotepad)

        // --- Add basic styling attempts here if needed ---
        // Example: Set background programmatically if not done in XML
        // editTextNotepad.setBackgroundResource(R.drawable.win98_edittext_background)
        // Apply custom font (requires font file in res/font)
        // val typeface = ResourcesCompat.getFont(this, R.font.your_win98_font)
        // editTextNotepad.typeface = typeface
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new -> actionNew()
            R.id.action_open -> actionOpen()
            R.id.action_save -> actionSave()
            R.id.action_save_as -> actionSaveAs()
            R.id.action_cut -> actionCut()
            R.id.action_copy -> actionCopy()
            R.id.action_paste -> actionPaste()
            R.id.action_select_all -> actionSelectAll()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun actionNew() {
        editTextNotepad.text.clear()
        currentFileUri = null
        supportActionBar?.title = "Untitled - Notepad"
        Toast.makeText(this, "New file", Toast.LENGTH_SHORT).show()
    }

    private fun actionOpen() {
        // Launch the SAF file picker
        openFileLauncher.launch(arrayOf("text/plain"))
    }

    private fun actionSave() {
        if (currentFileUri != null) {
            // If we have a URI, save directly to it
            saveFile(currentFileUri!!)
        } else {
            // Otherwise, act like "Save As"
            actionSaveAs()
        }
    }

    private fun actionSaveAs() {
        // Launch the SAF file saver, suggest a default name
        val currentTitle = supportActionBar?.title?.toString() ?: "Untitled.txt"
        val suggestedName = if (currentTitle.endsWith(" - Notepad")) {
            currentTitle.substring(0, currentTitle.length - " - Notepad".length) + ".txt"
        } else {
            "Untitled.txt"
        }
        createFileLauncher.launch(suggestedName)
    }

    // --- Clipboard Actions ---
    private fun actionCut() {
        val selectedText = getSelectedText()
        if (selectedText != null) {
            copyToClipboard(selectedText)
            editTextNotepad.text?.delete(editTextNotepad.selectionStart, editTextNotepad.selectionEnd)
        }
    }

    private fun actionCopy() {
        val selectedText = getSelectedText()
        if (selectedText != null) {
            copyToClipboard(selectedText)
            Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actionPaste() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType("text/plain") == true) {
            val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (pasteData != null) {
                val start = editTextNotepad.selectionStart.coerceAtLeast(0)
                val end = editTextNotepad.selectionEnd.coerceAtLeast(0)
                // Replace selected text or insert at cursor
                editTextNotepad.text?.replace(start.coerceAtMost(end), start.coerceAtLeast(end), pasteData)
            }
        } else {
            Toast.makeText(this, "Clipboard is empty or contains non-text data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actionSelectAll() {
        editTextNotepad.selectAll()
    }

    private fun getSelectedText(): String? {
        val start = editTextNotepad.selectionStart
        val end = editTextNotepad.selectionEnd
        return if (start != -1 && end != -1 && start != end) {
            editTextNotepad.text?.substring(start.coerceAtMost(end), start.coerceAtLeast(end))
        } else {
            null
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Win98Notepad", text)
        clipboard.setPrimaryClip(clip)
    }


    // --- File I/O Helper Functions using SAF URIs ---

    private fun readFileContent(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append('\n')
                    }
                    // Remove trailing newline if added
                    if (stringBuilder.isNotEmpty()) {
                        stringBuilder.setLength(stringBuilder.length - 1)
                    }
                    editTextNotepad.setText(stringBuilder.toString())

                    // Update Title bar
                    val fileName = getFileNameFromUri(uri) ?: "Untitled"
                    supportActionBar?.title = "$fileName - Notepad"

                    Toast.makeText(this, "File opened: $fileName", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveFile(uri: Uri) {
        try {
            // Use "wt" mode to truncate and write (overwrite)
            contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fileOutputStream ->
                    fileOutputStream.write(editTextNotepad.text.toString().toByteArray())
                    // Update Title bar
                    val fileName = getFileNameFromUri(uri) ?: currentFileUri?.let { getFileNameFromUri(it)} ?: "Untitled"
                    supportActionBar?.title = "$fileName - Notepad"
                    Toast.makeText(this, "File saved: $fileName", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Helper to try and get a filename from a content URI (may not always work)
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // Find the display name column index dynamically
                    val nameIndex = it.getColumnIndex("_display_name")
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }
        return fileName
    }
}