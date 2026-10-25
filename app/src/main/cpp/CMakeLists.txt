cmake_minimum_required(VERSION 3.22)
project(fireassist LANGUAGES CXX)

add_library(fireassist SHARED native-lib.cpp)
target_compile_features(fireassist PRIVATE cxx_std_20)
target_compile_options(fireassist PRIVATE -O2 -fvisibility=hidden)
target_link_libraries(fireassist log)