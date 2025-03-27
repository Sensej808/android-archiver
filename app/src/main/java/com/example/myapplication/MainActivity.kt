package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    suspend fun compressFiles(
        uris: List<Uri>,
        context: Context,
        outputZipPath: String,
        resultName: String = "archive",
        progressCallback: MainActivity.ProgressCallback
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            Log.d("Archiver", "Начало сжатия файлов")

            val filePaths = uris.map { uri ->
                getFilePathFromUri(context, uri)
            }.toTypedArray()

            val absZipPath = "$outputZipPath/$resultName.zip"

            val result = (context as MainActivity).createZip(filePaths, absZipPath, object : MainActivity.ProgressCallback {
                override fun onProgressUpdate(progress: Float) {
                    progressCallback.onProgressUpdate(progress)
                    _progress.value = progress
                }
            })

            if (result) {
                Log.d("Archiver", "ZIP-архив успешно создан: $absZipPath")
                true
            } else {
                Log.e("Archiver", "Ошибка создания ZIP-архива")
                false
            }
        } catch (e: Exception) {
            Log.e("Archiver", "Ошибка: ${e.message}")
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
}

class MainActivity : ComponentActivity() {

    interface ProgressCallback {
        fun onProgressUpdate(progress: Float)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen()
        }
    }

    external fun createZip(filePaths: Array<String>, outputZipPath: String, progressCallback: ProgressCallback): Boolean
}

@Composable
fun HomeScreen() {
    val context =  LocalContext.current
    var selectedFiles by rememberSaveable { mutableStateOf(listOf<Uri>()) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        selectedFiles = uris
    }

    val viewModel = remember { MainViewModel() }
    val coroutineScope = rememberCoroutineScope()
    val progress by viewModel.progress.collectAsState()
    var isArchiveCreated by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Архиватор файлов", fontSize = 36.sp, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
            Text(text = "Выбрать файлы")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedFiles.isNotEmpty()) {
            Text(text = "Выбранные файлы:")

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                items(selectedFiles.size) { index ->
                    val uri = selectedFiles[index]
                    val fileName = getFileName(context, uri)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (context.contentResolver.getType(uri)?.let { it } ?: "Неизвестный тип") + "     " + fileName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Text(text = "Файлы не выбраны")
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    val outputZipPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.path ?: ""
                    val result = viewModel.compressFiles(selectedFiles, context, outputZipPath, "archive", object : MainActivity.ProgressCallback {
                        override fun onProgressUpdate(progress: Float) {
                            println("Прогресс: ${(progress * 100).toInt()}%")
                        }

                    })
                    if (result) {
                        isArchiveCreated = true
                    } else {
                    }
                }
            }
        ) {
            Text(text = "Создать архив")
        }

        if (progress > 0f && progress < 1f) {
            Column {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(text = "Прогресс: ${(progress * 100).toInt()}%", modifier = Modifier.padding(8.dp))
            }
        } else if (isArchiveCreated) {
            Text(text = "Архив успешно создан!", color = MaterialTheme.colorScheme.primary)
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var fileName = "Неизвестный файл"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
}