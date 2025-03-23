# Install script for directory: C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "C:/Program Files (x86)/myapplication")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "C:/Users/sasha/AppData/Local/Android/Sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-objdump.exe")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/pkgconfig" TYPE FILE FILES "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/libzip.pc")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip/modules" TYPE FILE FILES
    "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/cmake/FindNettle.cmake"
    "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/cmake/Findzstd.cmake"
    "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/cmake/FindMbedTLS.cmake"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/zipconf.h")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip" TYPE FILE FILES
    "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/libzip-config.cmake"
    "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/libzip-config-version.cmake"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip/libzip-targets.cmake")
    file(DIFFERENT EXPORT_FILE_CHANGED FILES
         "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip/libzip-targets.cmake"
         "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/CMakeFiles/Export/lib/cmake/libzip/libzip-targets.cmake")
    if(EXPORT_FILE_CHANGED)
      file(GLOB OLD_CONFIG_FILES "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip/libzip-targets-*.cmake")
      if(OLD_CONFIG_FILES)
        message(STATUS "Old export file \"$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip/libzip-targets.cmake\" will be replaced.  Removing files [${OLD_CONFIG_FILES}].")
        file(REMOVE ${OLD_CONFIG_FILES})
      endif()
    endif()
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip" TYPE FILE FILES "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/CMakeFiles/Export/lib/cmake/libzip/libzip-targets.cmake")
  if("${CMAKE_INSTALL_CONFIG_NAME}" MATCHES "^([Dd][Ee][Bb][Uu][Gg])$")
    file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/libzip" TYPE FILE FILES "C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/CMakeFiles/Export/lib/cmake/libzip/libzip-targets-debug.cmake")
  endif()
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for each subdirectory.
  include("C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/lib/cmake_install.cmake")
  include("C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/man/cmake_install.cmake")
  include("C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/src/cmake_install.cmake")
  include("C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/ossfuzz/cmake_install.cmake")
  include("C:/Users/sasha/Documents/android-archiver/app/src/main/cpp/libzip/outputs/examples/cmake_install.cmake")

endif()

