package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.example.myapplication.ui.theme.AndroidArchiverTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainViewModel : ViewModel() {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _currentFileIndex = MutableStateFlow(0)
    val currentFileIndex: StateFlow<Int> = _currentFileIndex

    private val _totalFiles = MutableStateFlow(0)
    val totalFiles: StateFlow<Int> = _totalFiles

    @SuppressLint("NewApi")
    suspend fun compressFiles(
        uris: List<Uri>,
        activity: MainActivity,
        outputZipPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("Archiver", "Начало сжатия файлов")
            _totalFiles.value = uris.size
            _currentFileIndex.value = 0

            val filePaths = uris.mapIndexed { index, uri ->
                _currentFileIndex.value = index + 1
                _progress.value = (index + 1).toFloat() / uris.size
                getFilePathFromUri(activity, uri)
            }.toTypedArray()

            return@withContext activity.createZip(filePaths, outputZipPath) { progress ->
                _progress.value = progress
            }
        } catch (e: Exception) {
            Log.e("Archiver", "Ошибка: ${e.message}", e)
            false
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName = if (displayNameIndex != -1) {
                    it.getString(displayNameIndex)
                } else {
                    uri.lastPathSegment ?: "unknown_file"
                }

                val file = File(context.cacheDir, displayName)
                val inputStream = contentResolver.openInputStream(uri)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                return file.absolutePath
            }
        }
        throw IllegalArgumentException("Не удалось получить путь из Uri: $uri")
    }

    fun resetProgress() {
        _progress.value = 0f
        _currentFileIndex.value = 0
        _totalFiles.value = 0
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidArchiverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }

    external fun createZip(
        filePaths: Array<String>,
        outputZipPath: String,
        progressCallback: (Float) -> Unit
    ): Boolean

    companion object {
        init {
            System.loadLibrary("myapplication")
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity
    var selectedFiles by rememberSaveable { mutableStateOf(listOf<Uri>()) }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var isArchiveReady by rememberSaveable { mutableStateOf(false) }
    var tempZipPath by rememberSaveable { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { MainViewModel() }
    val progress by viewModel.progress.collectAsState()
    val currentFileIndex by viewModel.currentFileIndex.collectAsState()
    val totalFiles by viewModel.totalFiles.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) {
            errorMessage = "Файлы не выбраны"
        } else {
            selectedFiles = uris
            isArchiveReady = false
            viewModel.resetProgress()
            errorMessage = ""
        }
    }

    val saveArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val tempFile = File(tempZipPath)
                        if (tempFile.exists()) {
                            val inputStream: InputStream = tempFile.inputStream()
                            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)

                            outputStream?.let {
                                inputStream.copyTo(it)
                                inputStream.close()
                                it.close()
                                tempFile.delete()

                                Toast.makeText(
                                    context,
                                    "Архив успешно сохранён",
                                    Toast.LENGTH_LONG
                                ).show()

                                tryOpenFolderWithArchive(context, uri)
                            } ?: run {
                                errorMessage = "Не удалось сохранить архив"
                            }
                        } else {
                            errorMessage = "Временный файл архива не найден"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Ошибка при сохранении: ${e.message}"
                    }
                }
            }
        }
    )

    fun createArchive() {
        if (selectedFiles.isEmpty()) {
            errorMessage = "Файлы не выбраны"
            return
        }

        coroutineScope.launch {
            try {
                val tempDir = context.cacheDir
                tempZipPath = File(tempDir, "temp_archive_${System.currentTimeMillis()}.zip").absolutePath

                val result = viewModel.compressFiles(
                    selectedFiles,
                    activity,
                    tempZipPath
                )

                if (result) {
                    isArchiveReady = true
                    errorMessage = ""
                } else {
                    errorMessage = "Ошибка при создании архива"
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка: ${e.message ?: "неизвестная ошибка"}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Архиватор файлов",
            fontSize = 36.sp,
            fontStyle = FontStyle.Italic,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Выбрать файлы")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedFiles.isNotEmpty()) {
            Text(text = "Выбранные файлы (${selectedFiles.size}):")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 8.dp)
            ) {
                items(selectedFiles) { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getFileName(context, uri),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            Text(text = "Файлы не выбраны")
        }

        if (progress > 0f && progress < 1f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Обработка файла $currentFileIndex из $totalFiles")
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Прогресс: ${(progress * 100).toInt()}%",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (isArchiveReady) {
            Text(
                text = "Архив готов к сохранению!",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Button(
            onClick = { createArchive() },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedFiles.isNotEmpty() && !(progress > 0f && progress < 1f)
        ) {
            Text(text = if (isArchiveReady) "Пересоздать архив" else "Создать архив")
        }

        if (isArchiveReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { saveArchiveLauncher.launch("archive_${System.currentTimeMillis()}.zip") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Сохранить архив")
            }
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

private fun tryOpenFolderWithArchive(context: Context, archiveUri: Uri) {
    try {
        val docFile = DocumentFile.fromSingleUri(context, archiveUri)
        docFile?.let { file ->
            val parentUri = file.parentFile?.uri
            parentUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "resource/folder")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return
                }
            }
        }

        Toast.makeText(
            context,
            "Архив сохранён",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Архив сохранён, но не удалось открыть папку",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AndroidArchiverTheme {
        HomeScreen()
    }
}

@SuppressLint("Range")
fun getFileName(context: Context, uri: Uri): String {
    var fileName = "Неизвестный файл"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}