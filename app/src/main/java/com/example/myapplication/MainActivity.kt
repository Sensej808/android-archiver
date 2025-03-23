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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.myapplication.ui.theme.AndroidArchiverTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.content.ContentValues
import android.provider.MediaStore

class MainViewModel : ViewModel() {

    suspend fun compressFiles(uris: List<Uri>, activity: MainActivity, resultName: String = "archive"): Boolean =
        withContext(Dispatchers.Default) {
            try {
                Log.d("Archiver", "Начало сжатия файлов")

                // Преобразуем URI в абсолютные пути
                val filePaths = uris.map { uri ->
                    getFilePathFromUri(activity, uri)
                }.toTypedArray()

                // Временный путь для сохранения ZIP-архива
                val tempZipPath = "${activity.cacheDir.absolutePath}/$resultName.zip"

                // Вызываем нативный метод для создания ZIP-архива
                val result = activity.createZip(filePaths, tempZipPath)

                if (result) {
                    Log.d("Archiver", "ZIP-архив успешно создан: $tempZipPath")
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

    // Функция для преобразования URI в путь
    private fun getFilePathFromUri(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                // Пытаемся получить имя файла из столбца DISPLAY_NAME
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName = if (displayNameIndex != -1) {
                    it.getString(displayNameIndex)
                } else {
                    // Если столбец DISPLAY_NAME не найден, используем последний сегмент URI
                    uri.lastPathSegment ?: "unknown_file"
                }

                // Создаем файл в кэше приложения
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
    external fun createZip(filePaths: Array<String>, outputZipPath: String): Boolean

    companion object {
        init {
            System.loadLibrary("myapplication")
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

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity
    var selectedFiles by rememberSaveable { mutableStateOf(listOf<Uri>()) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris != null) {
            selectedFiles = uris
        }
    }

    val viewModel = remember { MainViewModel() }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var isArchiveCreated by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
            onClick = {
                coroutineScope.launch {
                    try {
                        val outputZipPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                        val result = viewModel.compressFiles(selectedFiles, activity, outputZipPath)
                        if (result) {
                            isArchiveCreated = true
                            errorMessage = ""
                        } else {
                            errorMessage = "Ошибка создания архива"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Ошибка: ${e.message}"
                    }
                }
            }
        ) {
            Text(text = "Создать архив")
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        if (isArchiveCreated) {
            Text(text = "Архив успешно создан!", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
    }
}

// Функция для получения имени файла
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