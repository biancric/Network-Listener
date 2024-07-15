package com.example.network_listener.ui.log

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.network_listener.R
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

class LogFragment : Fragment() {

    // ViewModel for log data
    private lateinit var logViewModel: LogViewModel

    // UI elements
    private lateinit var logTextView: TextView
    private lateinit var exportButton: Button
    private lateinit var deleteButton: Button

    // List of files to export
    private var filesToExport: List<File>? = null

    // ActivityResultLaunchers for handling result of intents
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var openDocumentTreeLauncher: ActivityResultLauncher<Intent>

    // Inflate the fragment's view and initialize ViewModel and UI elements
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_log, container, false)
        logViewModel = ViewModelProvider(this).get(LogViewModel::class.java)

        // Initialize UI elements
        logTextView = root.findViewById(R.id.logTextView)
        exportButton = root.findViewById(R.id.exportButton)
        deleteButton = root.findViewById(R.id.deleteButton)

        // Load the log data into the TextView
        loadLog()

        // Set up click listeners for buttons
        exportButton.setOnClickListener {
            showLogFilesDialog()
        }

        deleteButton.setOnClickListener {
            showDeleteLogFilesDialog()
        }

        // Register the ActivityResultLaunchers
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.also { uri ->
                    if (filesToExport?.size == 1) {
                        saveFile(uri)
                    } else {
                        saveAllFiles(uri)
                    }
                }
            }
        }

        openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.also { uri ->
                    saveAllFiles(uri)
                }
            }
        }

        return root
    }

    // Load the most recent log file into the TextView
    private fun loadLog() {
        val logDir = File(requireContext().getExternalFilesDir(null), "logs")
        val logFile = logDir.listFiles()?.maxByOrNull { it.lastModified() }

        if (logFile != null && logFile.exists()) {
            try {
                FileInputStream(logFile).use { fis ->
                    logTextView.text = fis.bufferedReader().use { it.readText() }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // Show a dialog with the list of log files to export
    private fun showLogFilesDialog() {
        val logDir = File(requireContext().getExternalFilesDir(null), "logs")
        val logFiles = logDir.listFiles()

        if (logFiles != null && logFiles.isNotEmpty()) {
            val logFileNames = logFiles.map { it.name }.toTypedArray()
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Choose Log File")
            builder.setItems(logFileNames) { _, which ->
                val selectedFile = logFiles[which]
                filesToExport = listOf(selectedFile)
                exportLogFile(selectedFile)
            }
            builder.setPositiveButton("Export All") { _, _ ->
                filesToExport = logFiles.toList()
                createDirectory()
            }
            builder.setNegativeButton("Cancel", null)
            builder.create().show()
        } else {
            Toast.makeText(requireContext(), "No log files found", Toast.LENGTH_SHORT).show()
        }
    }

    // Launch an intent to create a document for exporting a log file
    private fun exportLogFile(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "exported_${file.name}")
        }
        createFileLauncher.launch(intent)
    }

    // Launch an intent to open a document tree for exporting all log files
    private fun createDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        openDocumentTreeLauncher.launch(intent)
    }

    // Save a single file to the selected URI
    private fun saveFile(uri: Uri) {
        filesToExport?.firstOrNull()?.let { logFile ->
            if (logFile.exists()) {
                try {
                    val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
                    FileInputStream(logFile).use { inputStream ->
                        outputStream?.use { outStream ->
                            val buffer = ByteArray(1024)
                            var length: Int
                            while (inputStream.read(buffer).also { length = it } > 0) {
                                outStream.write(buffer, 0, length)
                            }
                        }
                    }
                    Toast.makeText(requireContext(), "Log file exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error exporting log file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Save all files to the selected directory URI
    private fun saveAllFiles(directoryUri: Uri) {
        filesToExport?.forEach { logFile ->
            if (logFile.exists()) {
                try {
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri,
                        DocumentsContract.getTreeDocumentId(directoryUri) + "/" + logFile.name)
                    val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(documentUri)
                    FileInputStream(logFile).use { inputStream ->
                        outputStream?.use { outStream ->
                            val buffer = ByteArray(1024)
                            var length: Int
                            while (inputStream.read(buffer).also { length = it } > 0) {
                                outStream.write(buffer, 0, length)
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        Toast.makeText(requireContext(), "All log files exported successfully", Toast.LENGTH_SHORT).show()
    }

    // Show a dialog with the list of log files to delete
    private fun showDeleteLogFilesDialog() {
        val logDir = File(requireContext().getExternalFilesDir(null), "logs")
        val logFiles = logDir.listFiles()

        if (logFiles != null && logFiles.isNotEmpty()) {
            val logFileNames = logFiles.map { it.name }.toTypedArray()
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Choose Log File to Delete")
            builder.setItems(logFileNames) { _, which ->
                val selectedFile = logFiles[which]
                selectedFile.delete()
                Toast.makeText(requireContext(), "Log file deleted: ${selectedFile.name}", Toast.LENGTH_SHORT).show()
                loadLog() // Refresh log display after deletion
            }
            builder.setPositiveButton("Delete All") { _, _ ->
                deleteAllLogs()
            }
            builder.setNegativeButton("Cancel", null)
            builder.create().show()
        } else {
            Toast.makeText(requireContext(), "No log files to delete", Toast.LENGTH_SHORT).show()
        }
    }

    // Delete all log files
    private fun deleteAllLogs() {
        val logDir = File(requireContext().getExternalFilesDir(null), "logs")
        val logFiles = logDir.listFiles()

        if (logFiles != null && logFiles.isNotEmpty()) {
            logFiles.forEach { it.delete() }
            Toast.makeText(requireContext(), "All log files deleted", Toast.LENGTH_SHORT).show()
            logTextView.text = ""
        } else {
            Toast.makeText(requireContext(), "No log files to delete", Toast.LENGTH_SHORT).show()
        }
    }
}
