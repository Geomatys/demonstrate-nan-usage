#
# Compiles the C/C++ versions of the `TestNodata.java` and `TestNaN.java` demos.
# See the `README.md` file for build instructions.
#
cmake_minimum_required(VERSION 3.5)

# Set the project name and version.
project(NaN-test VERSION 1.0 LANGUAGES CXX)

# Specify the C++ standard.
set(CMAKE_CXX_STANDARD 20)

# Omit debugging information.
set(CMAKE_BUILD_TYPE Release)

#
# Add compiler options for showing that NaN still work even with `-ffast-math`,
# provided that the `-ffinite-math-only` option is excluded. Note that the test
# can also work even with `-ffinite-math-only` with a minor change in the code.
# See "note-on-cpp.md" for more information.
#
add_compile_options(-ffast-math -fno-finite-math-only)

# Create an executable.
add_executable(NaN-test TestCase.cpp)

# Compile all C++ files in the source directory.
file(GLOB SOURCES "*.cpp")
