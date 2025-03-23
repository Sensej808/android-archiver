#include <jni.h>
#include <string>
#include <vector>
#include <zip.h>

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_archiver_Archiver_compressFile(JNIEnv *env, jobject thiz, jstring file_uri, jstring archive_type) {
    const char *uri = env->GetStringUTFChars(file_uri, 0);
    const char *type = env->GetStringUTFChars(archive_type, 0);

    jbyteArray result = nullptr;



    env->ReleaseStringUTFChars(file_uri, uri);
    env->ReleaseStringUTFChars(archive_type, type);
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_archiver_Archiver_createArchive(JNIEnv *env, jobject thiz, jbyteArray compressed_data, jstring output_path, jstring archive_type) {
    const char *output_path_str = env->GetStringUTFChars(output_path, 0);
    const char *type = env->GetStringUTFChars(archive_type, 0);

    jstring result = nullptr;



    env->ReleaseStringUTFChars(output_path, output_path_str);
    env->ReleaseStringUTFChars(archive_type, type);
    return result;
}