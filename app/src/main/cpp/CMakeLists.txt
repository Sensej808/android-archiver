# Минимальная версия CMake
cmake_minimum_required(VERSION 3.22.1)

# Имя проекта
project("myapplication")

# Путь к исходникам libzip
set(libzip_src_DIR ${CMAKE_SOURCE_DIR}/libzip)

find_library(
        log-lib
        log
)

# Добавляем zlib
find_library(
        zlib-lib
        z
)

# Создание основной библиотеки
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp  # Ваши исходные файлы
)

# Добавляем libzip как подпроект
add_subdirectory(
        ${libzip_src_DIR}  # Директория с CMakeLists.txt libzip
)



# Связывание библиотек
target_link_libraries(${CMAKE_PROJECT_NAME}
        libzip::zip
        ${log-lib}
        ${zlib-lib}
        android  # Системные библиотеки
        log
)