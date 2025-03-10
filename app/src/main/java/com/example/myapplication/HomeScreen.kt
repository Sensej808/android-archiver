package com.example.myapplication

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import android.content.Context
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(){
    val context =  LocalContext.current
    //сохранение при повороте экрана
    var selectedFiles by rememberSaveable {mutableStateOf(listOf<Uri>())}
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ){
        uris ->
            if (uris != null){
                selectedFiles = uris
            }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment =  Alignment.CenterHorizontally,
    ){
        Text(
            text="Архиватор файлов",
            fontSize = 36.sp,
            fontStyle = FontStyle.Italic,

            )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        {
            Text(text="Выбрать файлы")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedFiles.isNotEmpty()) {
            Text(text = "Выбранные файлы:")

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Используем itemsCount для указания количества элементов
                items(count = selectedFiles.size) { index ->
                    val uri = selectedFiles.getOrNull(index) ?: return@items
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
                            text = (context.contentResolver.getType(uri)?.let { it } ?: "Неизвестный тип") +"     " + fileName,

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
         onClick = {}
     ) {
         Text(text="Создать архив")
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