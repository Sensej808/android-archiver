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
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

std::mutex mtx;
std::condition_variable cv;
std::queue<std::pair<std::string, std::vector<char>>> compressed_files;
bool done = false;
int active_workers = 0;

std::string get_file_name(const std::string& file_path) {
    size_t last_slash = file_path.find_last_of("/\\");
    if (last_slash == std::string::npos) {
        return file_path;
    }
    return file_path.substr(last_slash + 1);
}

void compress_file(const std::string& file_path) {
    std::string file_name = get_file_name(file_path);
    LOGD("[1] Start compressing file: %s", file_path.c_str());

    std::ifstream input_file(file_path, std::ios::binary | std::ios::ate);
    if (!input_file) {
        LOGE("[ERROR] Failed to open file: %s", file_path.c_str());
        return;
    }

    std::streamsize size = input_file.tellg();
    input_file.seekg(0, std::ios::beg);
    LOGD("[2] File size: %lld bytes", (long long)size);

    std::vector<char> buffer(size);
    if (!input_file.read(buffer.data(), size)) {
        LOGE("[ERROR] Failed to read file: %s", file_path.c_str());
        return;
    }

    // Проверка контрольной суммы буфера
    size_t checksum = 0;
    for (char c : buffer) checksum += c;
    LOGD("[3] Original buffer checksum: %zu, first bytes: %02X %02X %02X",
         checksum, (unsigned char)buffer[0],
         (unsigned char)buffer[1], (unsigned char)buffer[2]);

    {
        std::lock_guard<std::mutex> lock(mtx);
        compressed_files.push({file_name, std::move(buffer)});
        LOGD("[4] Added to queue: %s (size: %zu)", file_name.c_str(), buffer.size());
    }
    cv.notify_one();
}

void write_to_zip(zip_t* zip) {
    while (true) {
        std::unique_lock<std::mutex> lock(mtx);
        cv.wait(lock, [] { return !compressed_files.empty() || (done && active_workers == 0); });

        if (compressed_files.empty() && done && active_workers == 0) {
            LOGD("[5] Writer thread terminating (queue empty and all workers done)");
            break;
        }

        if (compressed_files.empty()) {
            LOGD("[6] Spurious wakeup, queue empty");
            continue;
        }

        auto file_data = compressed_files.front();
        LOGD("[info] : file_data: %s, front: %s", file_data.first.c_str(), compressed_files.front().first.c_str());
        compressed_files.pop();
        size_t queue_size = compressed_files.size();
        lock.unlock();

        LOGD("[7] Processing file: %s (data size: %zu, queue size: %zu)",
             file_data.first.c_str(), file_data.second.size(), queue_size);

        // Проверка контрольной суммы перед записью
        size_t checksum = 0;
        for (char c : file_data.second) checksum += c;
        LOGD("[8] Buffer checksum before write: %zu", checksum);

        zip_source_t* source = zip_source_buffer(zip, file_data.second.data(), file_data.second.size(), 0);
        if (!source) {
            LOGE("[ERROR] Failed to create zip source for: %s", file_data.first.c_str());
            continue;
        }

        zip_int64_t ret = zip_file_add(zip, file_data.first.c_str(), source, ZIP_FL_ENC_UTF_8);
        if (ret < 0) {
            LOGE("[ERROR] Failed to add file to zip: %s (error: %s)",
                 file_data.first.c_str(), zip_strerror(zip));
            zip_source_free(source);
            continue;
        }

        LOGD("[9] Successfully added file to archive: %s", file_data.first.c_str());
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
    LOGD("[START] Creating zip archive at: %s", output_path);

    jobject global_callback = env->NewGlobalRef(progress_callback);
    jclass global_callback_class = (jclass)env->NewGlobalRef(env->GetObjectClass(progress_callback));

    int err = 0;
    zip_t* zip = zip_open(output_path, ZIP_CREATE | ZIP_TRUNCATE, &err);

    if (!zip) {
        LOGE("[ERROR] Failed to create zip at %s (error: %d, message: %s)",
             output_path, err, zip_strerror(nullptr));
        env->ReleaseStringUTFChars(output_zip_path, output_path);
        env->DeleteGlobalRef(global_callback);
        env->DeleteGlobalRef(global_callback_class);
        return false;
    }
    LOGD("[ZIP] Archive created successfully");
    JavaVM* java_vm;
    env->GetJavaVM(&java_vm);

    jsize length = env->GetArrayLength(file_paths);
    std::vector<jstring> global_file_paths(length);
    for (jsize i = 0; i < length; i++) {
        jstring local_ref = (jstring)env->GetObjectArrayElement(file_paths, i);
        global_file_paths[i] = (jstring)env->NewGlobalRef(local_ref);
        env->DeleteLocalRef(local_ref);
    }

    {
        std::lock_guard<std::mutex> lock(mtx);
        active_workers = length;
    }

    std::thread writer_thread(write_to_zip, zip);

    std::vector<std::thread> threads;
    for (jsize i = 0; i < length; i++) {
        threads.emplace_back([=]() {
            JNIEnv* thread_env;
            java_vm->AttachCurrentThread(&thread_env, nullptr);

            const char* file_path_str = thread_env->GetStringUTFChars(global_file_paths[i], nullptr);
            compress_file(file_path_str);

            {
                std::lock_guard<std::mutex> lock(mtx);
                active_workers--;
                //jmethodID update_progress_method = thread_env->GetMethodID(
                //        (jclass)global_callback_class, "onProgressUpdate", "(F)V");
                //thread_env->CallVoidMethod(global_callback, update_progress_method,
                //                           (length - active_workers) * 100 / length);
            }
            cv.notify_one();

            thread_env->ReleaseStringUTFChars(global_file_paths[i], file_path_str);
            java_vm->DetachCurrentThread();
        });
    }

    for (auto& thread : threads) {
        if (thread.joinable()) {
            thread.join();
        }
    }

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

    if (writer_thread.joinable()) {
        writer_thread.join();
    }

    zip_close(zip);
    LOGD("[END] Zip creation finished, cleaning up");
    env->ReleaseStringUTFChars(output_zip_path, output_path);

    done = false;
    active_workers = 0;

    return true;
}