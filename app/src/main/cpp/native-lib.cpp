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

// Глобальные переменные для синхронизации
std::mutex mtx;
std::condition_variable cv;
std::queue<std::pair<std::string, std::vector<char>>> compressed_files;
bool done = false;

// Функция для сжатия данных с использованием zlib
std::vector<char> compress_data(const std::vector<char>& input) {
    std::vector<char> output;
    z_stream zs = {};
    deflateInit(&zs, Z_DEFAULT_COMPRESSION);

    const size_t buffer_size = 1024 * 1024; // 1 MB
    std::vector<char> buffer(buffer_size);

    zs.next_in = reinterpret_cast<Bytef*>(const_cast<char*>(input.data()));
    zs.avail_in = input.size();

    do {
        zs.next_out = reinterpret_cast<Bytef*>(buffer.data());
        zs.avail_out = buffer.size();

        deflate(&zs, Z_FINISH);

        size_t compressed_size = buffer.size() - zs.avail_out;
        output.insert(output.end(), buffer.begin(), buffer.begin() + compressed_size);
    } while (zs.avail_out == 0);

    deflateEnd(&zs);
    return output;
}

// Функция для сжатия файла
void compress_file(const std::string& file_path, const std::string& file_name) {
    std::ifstream input_file(file_path, std::ios::binary);
    if (!input_file) {
        return;
    }

    std::vector<char> buffer((std::istreambuf_iterator<char>(input_file)), std::istreambuf_iterator<char>());
    std::vector<char> compressed_data = compress_data(buffer);

    // Добавляем сжатые данные в очередь
    {
        std::lock_guard<std::mutex> lock(mtx);
        compressed_files.push({file_name, compressed_data});
    }
    cv.notify_one();
}

// Функция для записи сжатых данных в ZIP-архив
void write_to_zip(zip_t* zip) {
    while (true) {
        std::unique_lock<std::mutex> lock(mtx);
        cv.wait(lock, [] { return !compressed_files.empty() || done; });

        if (compressed_files.empty() && done) break;

        auto file_data = compressed_files.front();
        compressed_files.pop();
        lock.unlock();

        // Добавляем файл в архив
        zip_source_t* source = zip_source_buffer(zip, file_data.second.data(), file_data.second.size(), 0);
        if (!source) {
            continue;
        }

        if (zip_file_add(zip, file_data.first.c_str(), source, ZIP_FL_ENC_UTF_8) < 0) {
            zip_source_free(source);
        }
    }
}

// JNI-функция для создания ZIP-архива
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_myapplication_MainActivity_createZip(JNIEnv *env, jobject thiz,
                                                      jobjectArray file_paths,
                                                      jstring output_zip_path) {
    const char* output_path = env->GetStringUTFChars(output_zip_path, nullptr);

    // Создаем ZIP-архив
    int err = 0;
    zip_t* zip = zip_open(output_path, ZIP_CREATE | ZIP_TRUNCATE, &err);
    if (!zip) {
        env->ReleaseStringUTFChars(output_zip_path, output_path);
        return false;
    }

    // Получаем список файлов
    jsize length = env->GetArrayLength(file_paths);
    std::vector<std::thread> threads;

    for (jsize i = 0; i < length; i++) {
        jstring file_path = (jstring) env->GetObjectArrayElement(file_paths, i);
        const char* file_path_str = env->GetStringUTFChars(file_path, nullptr);

        threads.emplace_back(compress_file, std::string(file_path_str), std::string(file_path_str));

        env->ReleaseStringUTFChars(file_path, file_path_str);
        env->DeleteLocalRef(file_path);
    }

    // Запускаем поток для записи в архив
    std::thread writer(write_to_zip, zip);

    // Ожидаем завершения всех потоков
    for (auto& t : threads) {
        t.join();
    }

    // Сообщаем writer-потоку, что работа завершена
    {
        std::lock_guard<std::mutex> lock(mtx);
        done = true;
    }
    cv.notify_one();

    // Ожидаем завершения writer-потока
    writer.join();

    // Закрываем ZIP-архив
    zip_close(zip);
    env->ReleaseStringUTFChars(output_zip_path, output_path);

    return true;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_myapplication_MainActivity_createArchive(JNIEnv *env, jobject thiz,
                                                          jbyteArray compressed_data,
                                                          jstring output_path,
                                                          jstring archive_type) {
    // TODO: implement createArchive()
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_myapplication_MainActivity_compressFile(JNIEnv *env, jobject thiz,
                                                         jstring file_uri, jstring archive_type) {
    // TODO: implement compressFile()
}