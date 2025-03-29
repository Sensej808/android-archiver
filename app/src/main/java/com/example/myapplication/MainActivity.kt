package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.myapplication.ui.theme.AndroidArchiverTheme
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

    @SuppressLint("NewApi")
    suspend fun compressFiles(
        uris: List<Uri>,
        activity: MainActivity,
        outputZipPath: String,
        onProgressUpdate: MainActivity.ProgressCallback
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("Archiver", "Начало сжатия файлов")

            val filePaths = uris.map { uri ->
                getFilePathFromUri(activity, uri)
            }.toTypedArray()

            val progressCallback = object : MainActivity.ProgressCallback {
                override fun onProgressUpdate(progress: Float) {
                    println("Прогресс: ${(progress * 100).toInt()}%")
                    _progress.value = progress
                }
            }

            return@withContext activity.createZip(filePaths, outputZipPath, progressCallback)
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
}

class MainActivity : ComponentActivity() {
    interface ProgressCallback {
        fun onProgressUpdate(progress: Float)
    }

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

    external fun createZip(filePaths: Array<String>, outputZipPath: String, progressCallback: ProgressCallback): Boolean

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
    var isArchiveCreated by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { MainViewModel() }
    val progress by viewModel.progress.collectAsState()

    // Лончер для выбора файлов
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris != null) {
            selectedFiles = uris
        }
    }

    // Лончер для выбора места сохранения архива
    val saveArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val progressCallback = object : MainActivity.ProgressCallback {
                            override fun onProgressUpdate(progress: Float) {
                                println("Прогресс: ${(progress * 100).toInt()}%")
                            }
                        }

                        // Сначала создаем архив во временной директории
                        val tempDir = context.cacheDir
                        val tempZipPath = File(tempDir, "temp_archive_${System.currentTimeMillis()}.zip").absolutePath

                        val result = viewModel.compressFiles(
                            selectedFiles,
                            activity,
                            tempZipPath,
                            progressCallback
                        )

                        if (result) {
                            // Копируем временный файл в выбранное место
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                File(tempZipPath).inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            isArchiveCreated = true
                            errorMessage = ""
                            // Удаляем временный файл
                            File(tempZipPath).delete()
                        } else {
                            errorMessage = "Ошибка создания архива"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Ошибка: ${e.message ?: "неизвестная ошибка"}"
                    }
                }
            }
        }
    )

    // Функция для создания архива
    fun createArchive() {
        if (selectedFiles.isEmpty()) {
            errorMessage = "Файлы не выбраны"
            return
        }

        // Запрашиваем у пользователя место для сохранения архива
        saveArchiveLauncher.launch("archive_${System.currentTimeMillis()}.zip")
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Архиватор файлов",
            fontSize = 36.sp,
            fontStyle = FontStyle.Italic,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
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
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { createArchive() },
            enabled = selectedFiles.isNotEmpty()
        ) {
            Text(text = "Создать архив")
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (progress > 0f && progress < 1f) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                LinearProgressIndicator(progress = { progress })
                Text(
                    text = "Прогресс: ${(progress * 100).toInt()}%",
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else if (isArchiveCreated) {
            Text(
                text = "Архив успешно создан!",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AndroidArchiverTheme {
        HomeScreen()
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