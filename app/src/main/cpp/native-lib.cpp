#include <jni.h>
#include <string>
#include <vector>
#include <fstream>


// JNI-функция для сжатия файла
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_MainActivity_compressFile(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath
        ) {
    return true;
}