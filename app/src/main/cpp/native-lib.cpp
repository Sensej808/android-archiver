#include <jni.h>
#include <zip.h>
#include <zlib.h>
#include <vector>
#include <string>
#include <thread>
#include <mutex>
#include <queue>
#include <fstream>
#include <condition_variable>
#include <android/log.h>

#define LOG_TAG "ZipArchiver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Глобальные переменные для синхронизации
std::mutex mtx;
std::condition_variable cv;
std::queue<std::pair<std::string, std::vector<char>>> compressed_files;
bool done = false;
int active_workers = 0;

// Функция для извлечения имени файла из пути
std::string get_file_name(const std::string& file_path) {
    LOGD("[1] Entering get_file_name()");
    size_t last_slash = file_path.find_last_of("/\\");
    if (last_slash == std::string::npos) {
        LOGD("[2] No slashes in path, returning full path");
        return file_path;
    }
    LOGD("[3] Extracted filename from path");
    return file_path.substr(last_slash + 1);
}

// Функция для сжатия данных с использованием zlib
std::vector<char> compress_data(const std::vector<char>& input) {
    LOGD("[4] Starting compress_data()");
    std::vector<char> output;
    z_stream zs = {};
    if (deflateInit(&zs, Z_DEFAULT_COMPRESSION) != Z_OK) {
        LOGD("[5] deflateInit() failed");
        return output;
    }

    LOGD("[6] deflateInit() succeeded");
    const size_t buffer_size = 1024 * 1024;
    std::vector<char> buffer(buffer_size);

    zs.next_in = reinterpret_cast<Bytef*>(const_cast<char*>(input.data()));
    zs.avail_in = input.size();

    LOGD("[7] Starting compression loop");
    int ret;
    do {
        zs.next_out = reinterpret_cast<Bytef*>(buffer.data());
        zs.avail_out = buffer.size();

        ret = deflate(&zs, Z_FINISH);
        if (ret == Z_STREAM_ERROR) {
            LOGD("[8] Compression error (Z_STREAM_ERROR)");
            break;
        }

        LOGD("[9] Compressed chunk size: %zu", buffer.size() - zs.avail_out);
        size_t compressed_size = buffer.size() - zs.avail_out;
        output.insert(output.end(), buffer.begin(), buffer.begin() + compressed_size);
    } while (zs.avail_out == 0);

    LOGD("[10] Finished compression, calling deflateEnd()");
    deflateEnd(&zs);
    return output;
}

// Функция для сжатия файла
void compress_file(const std::string& file_path) {
    LOGD("[11] Starting compress_file() for: %s", file_path.c_str());
    std::string file_name = get_file_name(file_path);

    LOGD("[12] Opening file: %s", file_path.c_str());
    std::ifstream input_file(file_path, std::ios::binary | std::ios::ate);
    if (!input_file) {
        LOGD("[ERROR] Failed to open file: %s", file_path.c_str());
        return;
    }

    LOGD("[13] Getting file size");
    std::streamsize size = input_file.tellg();
    input_file.seekg(0, std::ios::beg);

    LOGD("[14] Reading file data");
    std::vector<char> buffer(size);
    if (!input_file.read(buffer.data(), size)) {
        LOGD("[ERROR] Failed to read file: %s", file_path.c_str());
        return;
    }

    LOGD("[15] Compressing data");
    std::vector<char> compressed_data = compress_data(buffer);

    // Добавляем сжатые данные в очередь
    {
        LOGD("[16] Locking mutex to push data");
        std::lock_guard<std::mutex> lock(mtx);
        compressed_files.push({file_name, compressed_data});
    }
    LOGD("[17] Notifying writer thread");
    cv.notify_one();
}

// Функция для записи сжатых данных в ZIP-архив
void write_to_zip(zip_t* zip) {
    LOGD("[18] Writer thread started");
    while (true) {
        LOGD("[19] Waiting for data or completion signal");
        std::unique_lock<std::mutex> lock(mtx);
        cv.wait(lock, [] { return !compressed_files.empty() || (done && active_workers == 0); });

        LOGD("[20] Condition met - checking state");
        if (compressed_files.empty() && done && active_workers == 0) {
            LOGD("[21] No more work - exiting writer thread");
            break;
        }

        if (compressed_files.empty()) {
            LOGD("[22] Spurious wakeup - continuing");
            continue;
        }

        LOGD("[23] Getting next file from queue");
        auto file_data = compressed_files.front();
        compressed_files.pop();
        lock.unlock();

        // Добавляем файл в архив
        LOGD("[24] Creating zip source for: %s", file_data.first.c_str());
        zip_source_t* source = zip_source_buffer(zip, file_data.second.data(), file_data.second.size(), 0);
        if (!source) {
            LOGD("[ERROR] Failed to create zip source for: %s", file_data.first.c_str());
            continue;
        }

        LOGD("[25] Adding file to zip: %s", file_data.first.c_str());
        if (zip_file_add(zip, file_data.first.c_str(), source, ZIP_FL_ENC_UTF_8) < 0) {
            LOGD("[ERROR] Failed to add file to zip: %s", file_data.first.c_str());
            zip_source_free(source);
        }
    }
}

// JNI-функция для создания ZIP-архива
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_MainActivity_createZip(
        JNIEnv *env,
        jobject thiz,
        jobjectArray file_paths,
        jstring output_zip_path,
        jobject progress_callback
) {
    LOGD("[26] JNI createZip() started");
    const char* output_path = env->GetStringUTFChars(output_zip_path, nullptr);
    LOGD("[27] Output path: %s", output_path);

    // Получаем глобальные ссылки
    jobject global_callback = env->NewGlobalRef(progress_callback);
    jclass global_callback_class = (jclass)env->NewGlobalRef(env->GetObjectClass(progress_callback));

    // Создаем ZIP-архив
    int err = 0;
    zip_t* zip = zip_open(output_path, ZIP_CREATE | ZIP_TRUNCATE, &err);
    if (!zip) {
        LOGD("[ERROR] Failed to create zip at %s (error: %d)", output_path, err);
        env->ReleaseStringUTFChars(output_zip_path, output_path);
        env->DeleteGlobalRef(global_callback);
        env->DeleteGlobalRef(global_callback_class);
        return false;
    }

    // Получаем JavaVM для прикрепления потоков
    JavaVM* java_vm;
    env->GetJavaVM(&java_vm);

    // Создаем глобальные ссылки на все file_paths
    jsize length = env->GetArrayLength(file_paths);
    std::vector<jstring> global_file_paths(length);
    for (jsize i = 0; i < length; i++) {
        jstring local_ref = (jstring)env->GetObjectArrayElement(file_paths, i);
        global_file_paths[i] = (jstring)env->NewGlobalRef(local_ref);
        env->DeleteLocalRef(local_ref); // Удаляем локальную ссылку
    }

    {
        std::lock_guard<std::mutex> lock(mtx);
        active_workers = length;
    }

    // Запускаем поток для записи в архив
    std::thread writer_thread(write_to_zip, zip);

    std::vector<std::thread> threads;
    for (jsize i = 0; i < length; i++) {
        threads.emplace_back([=]() {
            // Прикрепляем поток к JVM
            JNIEnv* thread_env;
            java_vm->AttachCurrentThread(&thread_env, nullptr);

            // Получаем строку из глобальной ссылки
            const char* file_path_str = thread_env->GetStringUTFChars(global_file_paths[i], nullptr);

            compress_file(file_path_str);

            // Обновляем прогресс
            {
                std::lock_guard<std::mutex> lock(mtx);
                active_workers--;
                jmethodID update_progress_method = thread_env->GetMethodID(
                        (jclass)global_callback_class, "onProgressUpdate", "(F)V");
                thread_env->CallVoidMethod(global_callback, update_progress_method,
                                           (length - active_workers) * 100 / length);
            }
            cv.notify_one();

            // Освобождаем ресурсы
            thread_env->ReleaseStringUTFChars(global_file_paths[i], file_path_str);

            // Отсоединяем поток от JVM
            java_vm->DetachCurrentThread();
        });
    }

    // Ждем завершения всех рабочих потоков
    for (auto& thread : threads) {
        if (thread.joinable()) {
            thread.join();
        }
    }

    // Освобождаем глобальные ссылки
    for (jsize i = 0; i < length; i++) {
        env->DeleteGlobalRef(global_file_paths[i]);
    }
    env->DeleteGlobalRef(global_callback);
    env->DeleteGlobalRef(global_callback_class);

    {
        std::lock_guard<std::mutex> lock(mtx);
        done = true;
    }
    cv.notify_one();

    // Ждем завершения потока записи
    if (writer_thread.joinable()) {
        writer_thread.join();
    }

    zip_close(zip);
    env->ReleaseStringUTFChars(output_zip_path, output_path);

    // Сбрасываем флаги
    done = false;
    active_workers = 0;

    return true;
}