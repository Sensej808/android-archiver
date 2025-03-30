#include <jni.h>
#include <zip.h>
#include <android/log.h>
#include <vector>
#include <fstream>
#include <string>
#include <thread>
#include <mutex>
#include <queue>
#include <condition_variable>

#define LOG_TAG "ZipArchiver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Структура для хранения данных файла
struct FileData {
    std::vector<char> content;
    std::string name;
};

// Глобальные переменные для очереди и синхронизации
std::queue<FileData> file_queue;
std::mutex queue_mutex;
std::condition_variable queue_cv;
bool all_files_processed = false;
std::mutex zip_mutex;

void process_file(const std::string& file_path, const std::string& file_name) {
    LOGD("Processing file: %s", file_path.c_str());

    FileData file_data;
    file_data.name = file_name;

    // Читаем содержимое файла
    std::ifstream file(file_path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", file_path.c_str());
        return;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    file_data.content.resize(size);

    if (!file.read(file_data.content.data(), size)) {
        LOGE("Failed to read file: %s", file_path.c_str());
        return;
    }

    // Добавляем в очередь
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        file_queue.push(std::move(file_data));
    }
    queue_cv.notify_one();

    LOGD("Finished processing: %s", file_path.c_str());
}

void zip_writer_thread(zip_t* zip) {
    while (true) {
        FileData file_data;

        {
            std::unique_lock<std::mutex> lock(queue_mutex);
            queue_cv.wait(lock, []{
                return !file_queue.empty() || all_files_processed;
            });

            if (file_queue.empty() && all_files_processed) {
                break;
            }

            if (file_queue.empty()) {
                continue;
            }

            file_data = std::move(file_queue.front());
            file_queue.pop();
        }

        // Создаем копию данных для архива
        char* data_copy = new char[file_data.content.size()];
        std::copy(file_data.content.begin(), file_data.content.end(), data_copy);

        {
            std::lock_guard<std::mutex> lock(zip_mutex);
            zip_source_t* source = zip_source_buffer(zip, data_copy, file_data.content.size(), 1);
            if (!source) {
                LOGE("Failed to create source for %s", file_data.name.c_str());
                delete[] data_copy;
                continue;
            }

            if (zip_file_add(zip, file_data.name.c_str(), source, ZIP_FL_ENC_UTF_8) < 0) {
                LOGE("Failed to add %s to archive: %s", file_data.name.c_str(), zip_strerror(zip));
                zip_source_free(source);
            } else {
                LOGI("Added to archive: %s (size: %zu bytes)",
                     file_data.name.c_str(), file_data.content.size());
            }
        }
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_MainActivity_createZip(
        JNIEnv *env,
        jobject thiz,
        jobjectArray file_paths,
        jstring output_zip_path,
        jobject progress_callback
) {
    const char* output_path = env->GetStringUTFChars(output_zip_path, nullptr);
    LOGI("Creating zip archive at: %s", output_path);

    // Создаем архив
    int err = 0;
    zip_t* zip = zip_open(output_path, ZIP_CREATE | ZIP_TRUNCATE, &err);
    if (!zip) {
        LOGE("Failed to create zip archive: error %d", err);
        env->ReleaseStringUTFChars(output_zip_path, output_path);
        return JNI_FALSE;
    }

    jsize file_count = env->GetArrayLength(file_paths);
    LOGI("Processing %d files", file_count);

    // Запускаем writer thread
    std::thread writer(zip_writer_thread, zip);

    // Создаем и запускаем worker threads
    std::vector<std::thread> workers;
    for (jsize i = 0; i < file_count; i++) {
        jstring file_path = (jstring)env->GetObjectArrayElement(file_paths, i);
        const char* raw_path = env->GetStringUTFChars(file_path, nullptr);

        // Получаем имя файла
        std::string full_path(raw_path);
        size_t last_slash = full_path.find_last_of("/\\");
        std::string file_name = (last_slash == std::string::npos) ? full_path : full_path.substr(last_slash + 1);

        workers.emplace_back(process_file, full_path, file_name);

        env->ReleaseStringUTFChars(file_path, raw_path);
        env->DeleteLocalRef(file_path);
    }

    // Ждем завершения всех worker threads
    for (auto& worker : workers) {
        if (worker.joinable()) {
            worker.join();
        }
    }

    // Сообщаем writer thread, что все файлы обработаны
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        all_files_processed = true;
    }
    queue_cv.notify_one();

    // Ждем завершения writer thread
    if (writer.joinable()) {
        writer.join();
    }

    zip_close(zip);
    env->ReleaseStringUTFChars(output_zip_path, output_path);
    LOGI("Zip archive created successfully with %d files", file_count);

    // Сбрасываем флаги для следующего вызова
    all_files_processed = false;

    return JNI_TRUE;
}