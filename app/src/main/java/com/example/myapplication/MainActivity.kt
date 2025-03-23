package com.example.myapplication

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AndroidArchiverTheme
import com.example.myapplication.HomeScreen
class MainActivity : ComponentActivity() {

//    private lateinit var binding: ActivityMainBinding

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

    /**
     * A native method that is implemented by the 'myapplication' native library,
     * which is packaged with this application.
     */

    external fun compressFile(fileUri: String, archiveType: String): ByteArray
    external fun createArchive(compressedData: ByteArray, outputPath: String, archiveType: String): String

    companion object {
        // Used to load the 'myapplication' library on application startup.
        init {
            System.loadLibrary("myapplication")
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(
        text = "Hello, $name!",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AndroidArchiverTheme {
        Greeting("Android")
    }
}


@Composable
fun HomeScreen(){
    val context =  LocalContext.current
    //сохранение при повороте экрана
    var selectedFiles by rememberSaveable { mutableStateOf(listOf<Uri>()) }
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