# Минимальная версия CMake
cmake_minimum_required(VERSION 3.22.1)

# Имя проекта
project("myapplication")

# Путь к исходникам libzip
set(libzip_src_DIR ${CMAKE_SOURCE_DIR}/../libzip)

# Путь к директории для сборки libzip
set(libzip_build_DIR ${CMAKE_SOURCE_DIR}/../libzip/outputs)

# Создаем директорию, если она не существует
file(MAKE_DIRECTORY ${libzip_build_DIR})

# Добавляем libzip как подпроект
add_subdirectory(
        ${libzip_src_DIR}  # Директория с CMakeLists.txt libzip
        ${libzip_build_DIR}  # Директория для сборки libzip
)


# Импортируем собранную библиотеку libzip
add_library(libzip STATIC IMPORTED)

# Construct the absolute path to the libzip.a file
set(LIBZIP_LIBRARY_PATH ${libzip_build_DIR}/lib/${ANDROID_ABI}/libzip.a)
message(STATUS "LIBZIP_LIBRARY_PATH: ${LIBZIP_LIBRARY_PATH}")

# Set the IMPORTED_LOCATION property with the absolute path
set_target_properties(libzip PROPERTIES IMPORTED_LOCATION ${LIBZIP_LIBRARY_PATH})

# Подключаем заголовочные файлы libzip
include_directories(${libzip_src_DIR}/include)

# Создание основной библиотеки
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp  # Ваши исходные файлы
)

# Связывание библиотек
target_link_libraries(${CMAKE_PROJECT_NAME}
        libzip
        android  # Системные библиотеки
        log
)