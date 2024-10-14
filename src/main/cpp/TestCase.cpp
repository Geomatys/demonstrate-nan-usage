/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <filesystem>
#include <cmath>

/*
 * The raster size, in pixels.
 */
#define WIDTH  800
#define HEIGHT 600

/*
 * Number of points where to interpolate.
 */
#define NUM_INTERPOLATION_POINTS 20000

/*
 * Number of iterations for which we verify the conformance against expected results.
 * We verify a small number of iterations because the actual results diverge strongly
 * from the expected results after about 8 iterations, because of the intentionally
 * chaotic nature of the calculation.
 */
#define NUM_VERIFIED_ITERATIONS 10

/*
 * The threshold used for deciding if a value should be considered as a missing value
 * when using the "no data" sentinel values approach instead of IEEE 754 NaN values.
 * Any value greater than this threshold will be considered a missing value.
 *
 * Note that this strategy works only if all missing values are greater than all valid values.
 * Conversely, a strategy where all missing values are smaller than valid values would also work.
 * However, if the missing values can be anything (for example some of them smaller and some of
 * them greater than valid values), then the code would need to be more complex and slower.
 */
#define MISSING_VALUE_THRESHOLD 10000


/*
 * Base class shared by the two test cases.
 */
class TestCase {
    /*
     * Whether this test case uses NaN instead of "no data" sentinel values.
     */
    bool useNaN;

    /*
     * Path to a RAW file containing raster data as `float` values in an endianness specified by `byteOrder`.
     * The raster size is WIDTH × HEIGHT pixels and the values are random `float` values between -100 and +100.
     * The file contains random missing values identified by "no data" sentinel values or by NaN values,
     * depending on which test is executed.
     */
    std::filesystem::path rasterFile;

    /*
     * Whether the values in the raster file are in big-endian or little-endian byte order.
     */
    std::endian byteOrder;

    /*
     * Path to a RAW file containing pixel coordinates as {@code double} values in big-endian byte order.
     * For simplicity, coordinates are from 0 inclusive to the width or height minus one, exclusive.
     * This is for avoiding the need to check for bounds before bilinear interpolations.
     * Some coordinates are declared missing by "no data" sentinel values or by NaN,
     * depending on which test is executed.
     */
    std::filesystem::path coordinatesFile;

    /**
     * Path to a RAW file containing expected results as {@code double} values in big-endian byte order.
     * This file may be large, so it is not loaded fully in memory. Missing results are represented by
     * sentinel values only. NaNs are not used for avoiding any suspicion about test reliability.
     */
    std::filesystem::path expectedResultsFile;

    protected:
        /*
         * Statistics about the differences between computed values and expected values.
         * The array length is `NUM_VERIFIED_ITERATIONS`.
         */
        double* errorStatistics;

        /*
         * Number of times where the "no data" values do not match the expected values.
         * This value should be zero during the first iterations, and become non-zero only
         * after the calculation has drifted. Note that the latter case is not an indication
         * that NaN does no work: identical mismatches happen with the "no data" approach too.
         */
        int* nodataMismatches;

        TestCase(bool, std::endian);
        ~TestCase();
        float*  loadRaster();
        double* loadCoordinates();
        double* loadExpectedResults();
        bool    resultEquals(TestCase*);

    public:
        virtual void computeAndCompare() = 0;
        bool    success();
        void    printStatistics();
};


/*
 * Determines the paths to the files needed by the test.
 * The files to load depend on whether the subclass is testing NaN or "no data" sentinel values.
 */
TestCase::TestCase(bool testNaN, std::endian testByteOrder) {
    std::filesystem::path directory("../generated-data");
    directory /= (testNaN ? "nan" : "nodata");
    useNaN               = testNaN;
    byteOrder            = testByteOrder;
    rasterFile           = directory / (testByteOrder == std::endian::little ? "little-endian.raw" : "big-endian.raw");
    coordinatesFile      = directory / "coordinates.raw";
    expectedResultsFile  = directory / "expected-results.raw";
    errorStatistics      = new double[NUM_VERIFIED_ITERATIONS];
    nodataMismatches     = new int[NUM_VERIFIED_ITERATIONS];
    memset(errorStatistics,  0, NUM_VERIFIED_ITERATIONS * sizeof(double));
    memset(nodataMismatches, 0, NUM_VERIFIED_ITERATIONS * sizeof(int));
}

/*
 * Frees the memory allocated by this test case.
 */
TestCase::~TestCase() {
    free(nodataMismatches);
    free(errorStatistics);
}

/*
 * Reads all bytes from the specified file. If the file cannot be found,
 * or if there is not enough memory left, this method returns NULL.
 */
char* readAllBytes(std::filesystem::path file, int numBytes) {
    std::ifstream stream(file, std::ios_base::binary);
    if (stream.is_open()) {
        char* bytes = new char[numBytes];
        if (bytes) {
            stream.read(bytes, numBytes);
            if (!stream.good()) {
                free(bytes);
                bytes = NULL;
            }
        }
        stream.close();
        return bytes;
    }
    return NULL;
}

/*
 * Loads all floating-point values of the raster. If the file cannot be found, return NULL.
 * If the byte order (big-endian versus little-endian) is not the native byte order, this
 * method swaps the bytes. No replacement of NaN or "no data" value occurs.
 */
float* TestCase::loadRaster() {
    char* bytes = readAllBytes(rasterFile, WIDTH * HEIGHT * sizeof(float));
    if (bytes && std::endian::native != byteOrder) {
        uint32_t* p = (uint32_t*) bytes;
        uint32_t* limit = p + WIDTH * HEIGHT;
        do {
            uint32_t v = *p;
            *p =  (v >> 24)
               | ((v <<  8) & 0x00FF0000)
               | ((v >>  8) & 0x0000FF00)
               |  (v << 24);
        } while (++p < limit);
    }
    return (float*) bytes;
}

/*
 * If this system uses the litte-endian byte order, swaps bytes in the given array from big-endian.
 * The number of values must be greater than 0. This method does nothing if the given pointer is NULL.
 */
void toNativeByteOrder(uint64_t* p, int numValues) {
    if (p && std::endian::native == std::endian::little) {
        uint64_t* limit = p + numValues;
        do {
            uint64_t v = *p;
            *p =  (v >> 56)
               | ((v << 40) & 0x00FF000000000000)
               | ((v << 24) & 0x0000FF0000000000)
               | ((v <<  8) & 0x000000FF00000000)
               | ((v >>  8) & 0x00000000FF000000)
               | ((v >> 24) & 0x0000000000FF0000)
               | ((v >> 40) & 0x000000000000FF00)
               |  (v << 56);
        } while (++p < limit);
    }
}

/*
 * Loads coordinate values. If the file cannot be found, return NULL.
 * Otherwise, bytes are swapped from big-endian to native byte order.
 */
double* TestCase::loadCoordinates() {
    char* bytes = readAllBytes(coordinatesFile, 2*NUM_INTERPOLATION_POINTS * sizeof(double));
    toNativeByteOrder((uint64_t*) bytes, 2*NUM_INTERPOLATION_POINTS);
    return (double*) bytes;
}

/*
 * Loads all expected values. This method differs from the Java implementation,
 * which uses streaming in its `prepareNextVerification(...)` method.
 * For the C/C++ version, it was easier to just read everything.
 */
double* TestCase::loadExpectedResults() {
    char* bytes = readAllBytes(expectedResultsFile, NUM_INTERPOLATION_POINTS * NUM_VERIFIED_ITERATIONS * sizeof(double));
    toNativeByteOrder((uint64_t*) bytes, NUM_INTERPOLATION_POINTS * NUM_VERIFIED_ITERATIONS);
    return (double*) bytes;
}

/*
 * Returns whether the test was successful.
 * The test is considered successful if the first iterations have no errors.
 * A drift is tolerated in the last iterations because this test intentionally
 * uses chaotic algorithm in order to test the effect of optimizations enabled
 * by compiler options in the C/C++ variant of this test.
 */
bool TestCase::success() {
    for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
        if (nodataMismatches[i] != 0) {
            if (i < 8) {
                return false;
            }
        }
    }
    return true;
}

/**
 * Returns whether the results of another test are equal to the results of this test.
 */
bool TestCase::resultEquals(TestCase* other) {
    for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
        if (errorStatistics [i] != other->errorStatistics [i] ||
            nodataMismatches[i] != other->nodataMismatches[i])
        {
            return false;
        }
    }
    return true;
}

/*
 * Prints the statistics collected by this test and whether the test was successful.
 * The test is considered successful if the first iterations have no errors.
 * A drift is tolerated in the last iterations because this test intentionally
 * uses chaotic algorithm in order to test the effect of optimizations enabled
 * by compiler options in the C/C++ variant of this test.
 */
void TestCase::printStatistics() {
    std::cout << "Errors in the use of raster data with " << (useNaN ? "NaN" : "\"No data\" sentinel")
              << " values in " << (byteOrder == std::endian::big ? "big" : "little") << "-endian byte order:\n"
              << "    Maximum   Number of \"missing value\" mismatches\n";
    for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
        printf("%11.4f %6d\n", errorStatistics[i], nodataMismatches[i]);
    }
}




/*
 * Demonstrates that NaN values can be read and processed without any lost of information.
 * The calculation is a bilinear interpolation.
 */
class TestNaN : public TestCase {
    /*
     * Value of the first positive quiet NaN.
     */
    const int32_t FIRST_QUIET_NAN = 0x7FC00000;

    /*
     * NaN bit pattern for a missing data. A value may be missing for different reasons, which are identified
     * by different NaN values. This test uses the following values, in precedence order. For example,
     * if a calculation involves two pixels missing for `CLOUD` and `LAND` reasons respectively,
     * then the result will be considered missing for the `LAND` reason.
     *
     *   - Missing interpolation result because of missing coordinate values.
     *   - Missing because the remote sensor didn't pass over that area.
     *   - Missing because the pixel is on a land (assuming that the data are for some oceanographic phenomenon).
     *   - Missing because of a cloud.
     *   - Missing for an unknown reason.
     */
    const int32_t UNKNOWN = FIRST_QUIET_NAN,      // This is the default NaN value in Java.
                  CLOUD   = FIRST_QUIET_NAN + 1,
                  LAND    = FIRST_QUIET_NAN + 2,
                  NO_PASS = FIRST_QUIET_NAN + 3;

    public:
        TestNaN(std::endian testByteOrder);
        void computeAndCompare();
};

/*
 * Creates a new test which will use NaN values for identifying the missing values.
 */
TestNaN::TestNaN(std::endian testByteOrder) : TestCase(true, testByteOrder) {
}

/*
 * Reads the raster, performs interpolations and compares against the expected values.
 * Differences are collected in statistics that can be printed with `printStatistics()`.
 * This method is the interesting part of the tests, where both approaches (NaN versus "no data") differ.
 */
void TestNaN::computeAndCompare() {
    float* raster = loadRaster();
    if (raster) {
        double* coordinates = loadCoordinates();
        if (coordinates) {
            double* expectedResults = loadExpectedResults();
            if (expectedResults) {
                double* expectedResultCursor = expectedResults;
                for (int it=0; it<NUM_VERIFIED_ITERATIONS; it++) {
                    double stats = errorStatistics[it];
                    for (int i=0; i<NUM_INTERPOLATION_POINTS; i++) {
                        /*
                         * Get all sample values that we need for the bilinear interpolation.
                         * Variables starting with "v" are converted from `float` to `double`.
                         */
                        int ix = i << 1;
                        int iy = ix | 1;
                        double x  = coordinates[ix];
                        double y  = coordinates[iy];
                        double xb = std::floor(x);
                        double yb = std::floor(y);
                        /*
                         * The following bound check is implicit in Java.
                         * We make it explicit in C/C++ for avoiding a core dump.
                         */
                        int offset = WIDTH * ((int) yb) + ((int) xb);
                        if (offset < 0 || offset >= (HEIGHT - 1) * WIDTH + (WIDTH - 1)) {
                            printf("Coordinates out of bounds: (%g, %g) for point %d.\n", xb, yb, i);
                            exit(1);
                        }
                        float v00 = raster[offset];
                        float v01 = raster[offset + 1];
                        float v10 = raster[offset += WIDTH];
                        float v11 = raster[offset + 1];
                        /*
                         * Apply bilinear interpolation. Contrarily to the `TestNodata` case, we compute
                         * this interpolation unconditionally without checking if the data are valid.
                         * This is an arbitrary choice, we could have made the two codes more similar.
                         * We do that for illustrating this flexibility, and for showing that we can
                         * rely on the result being some kind of NaN on all platforms and languages.
                         */
                        double xf = x - xb;
                        double yf = y - yb;
                        double v0 = std::fma(v01 - (double) v00, xf, v00);
                        double v1 = std::fma(v11 - (double) v10, xf, v10);
                        double result = std::fma(v1 - v0, yf, v0);
                        /*
                         * Check if any raster value is missing. We could perform this check before calculation as in
                         * `TestNodata` class, but we don't have to. This demo arbitrarily checks after calculation.
                         * As an optimization, we exploit the facts that in this test:
                         *
                         *   1) All NaN values in this test are "positive" (sign bit set to 0).
                         *   2) NaN payloads are sorted with higher values for the reasons having precedence.
                         *
                         * The combination of those two facts allows us to simply check for the maximal value,
                         * using signed integer comparisons, no matter if we have a mix of "no data" and real values.
                         * If fact #1 was not true, we could still apply the same trick with only the addition of a bitmask.
                         */
                        if (std::isnan(result)) {
                            int topRow = offset - WIDTH;
                            int32_t* potentialNaNs = reinterpret_cast<int32_t*>(raster);
                            int32_t missingValueReason = std::max(
                                    std::max(potentialNaNs[topRow], potentialNaNs[topRow + 1]),
                                    std::max(potentialNaNs[offset], potentialNaNs[offset + 1])
                            );
                            /*
                             * Convert the NaN pattern to the "no data" sentinel value used by `DataGenerator`.
                             * This step is not needed in an application using NaN. This test is doing that
                             * conversion only because we choose to store missing values as "no data" in the
                             * "expected-results.raw" file.
                             */
                            double nodata = (missingValueReason - FIRST_QUIET_NAN) + MISSING_VALUE_THRESHOLD;
                            if (nodata != *expectedResultCursor) {
                                nodataMismatches[it]++;
                            }
                            result = 1;      // For moving to another position during the next iteration.
                        } else {
                            double expected = *expectedResultCursor;
                            if (expected >= MISSING_VALUE_THRESHOLD) {
                                nodataMismatches[it]++;
                            } else {
                                stats = std::max(stats, std::abs(result - expected));
                            }
                        }
                        coordinates[ix] = std::fmod(std::abs(x + result), WIDTH  - 1);
                        coordinates[iy] = std::fmod(std::abs(y + result), HEIGHT - 1);
                        expectedResultCursor++;
                    }
                    errorStatistics[it] = stats;
                }
                free(expectedResults);
            }
            free(coordinates);
        }
        free(raster);
    }
}




/*
 * Same calculation as `TestNaN` but using sentinel values.
 * Used only for comparison purposes (reference implementation).
 */
class TestNodata : public TestCase {
    /*
     * Sentinel value for a missing data. A value may be missing for different reasons, which are identified
     * by different sentinel values. This test uses the following values, in precedence order. For example,
     * if a calculation involves two pixels missing for `CLOUD` and `LAND` reasons respectively,
     * then the result will be considered missing for the `LAND` reason.
     *
     *   - Missing interpolation result because of missing coordinate values.
     *   - Missing because the remote sensor didn't pass over that area.
     *   - Missing because the pixel is on a land (assuming that the data are for some oceanographic phenomenon).
     *   - Missing because of a cloud.
     *   - Missing for an unknown reason.
     */
    const float UNKNOWN = 10000,    // Shall be equal to MISSING_VALUE_THRESHOLD for this test.
                CLOUD   = 10001,
                LAND    = 10002,
                NO_PASS = 10003;

    public:
        TestNodata(std::endian testByteOrder);
        void computeAndCompare();
        void testAndCompare();
};

/*
 * Creates a new test which will use "no data" sentinel values for identifying the missing values.
 */
TestNodata::TestNodata(std::endian testByteOrder) : TestCase(false, testByteOrder) {
}

/*
 * Reads the raster, performs interpolations and compares against the expected values.
 * Differences are collected in statistics that can be printed with `printStatistics()`.
 * This method is the interesting part of the tests, where both approaches (NaN versus "no data") differ.
 */
void TestNodata::computeAndCompare() {
    float* raster = loadRaster();
    if (raster) {
        double* coordinates = loadCoordinates();
        if (coordinates) {
            double* expectedResults = loadExpectedResults();
            if (expectedResults) {
                double* expectedResultCursor = expectedResults;
                for (int it=0; it<NUM_VERIFIED_ITERATIONS; it++) {
                    double stats = errorStatistics[it];
                    for (int i=0; i<NUM_INTERPOLATION_POINTS; i++) {
                        /*
                         * Get all sample values that we need for the bilinear interpolation.
                         * Variables starting with "v" are converted from `float` to `double`.
                         */
                        int ix = i << 1;
                        int iy = ix | 1;
                        double x  = coordinates[ix];
                        double y  = coordinates[iy];
                        double xb = std::floor(x);
                        double yb = std::floor(y);
                        /*
                         * The following bound check is implicit in Java.
                         * We make it explicit in C/C++ for avoiding a core dump.
                         */
                        int offset = WIDTH * ((int) yb) + ((int) xb);
                        if (offset < 0 || offset >= (HEIGHT - 1) * WIDTH + (WIDTH - 1)) {
                            printf("Coordinates out of bounds: (%g, %g) for point %d.\n", xb, yb, i);
                            exit(1);
                        }
                        float v00 = raster[offset];
                        float v01 = raster[offset + 1];
                        float v10 = raster[offset += WIDTH];
                        float v11 = raster[offset + 1];
                        double result;    // To be computed below.
                        /*
                         * Check if any raster value is missing. When sentinel values are used (as in this demo),
                         * this check must be done before the calculation. As an optimization, we exploit the facts
                         * that in this test:
                         *
                         *   1) All "no data" values used in this demo are greater than valid values.
                         *   2) "No data" values are sorted with higher values for the reasons having precedence.
                         *
                         * The combination of those two facts allows us to simply check for the maximal value,
                         * no matter if we have a mix of "no data" and real values. However, this trick would not work
                         * anymore if we didn't knew in advance that all "no data" are greater than all valid values.
                         * If they were smaller, the code below would need to use `<=` instead of `>=` (in addition of
                         * `max` being not applicable anymore). The code would be yet more complex if we had a mix of
                         * "no data" smaller and greater than real values.
                         */
                        float missingValueReason = std::max(
                                std::max(v00, v01),
                                std::max(v10, v11));
                        if (missingValueReason >= MISSING_VALUE_THRESHOLD) {
                            if (missingValueReason != *expectedResultCursor) {
                                nodataMismatches[it]++;
                            }
                            result = 1;      // For moving to another position during the next iteration.
                        } else {
                            /*
                             * Apply the bilinear interpolation and compare against the expected value.
                             */
                            double xf = x - xb;
                            double yf = y - yb;
                            double v0 = std::fma(v01 - (double) v00, xf, v00);
                            double v1 = std::fma(v11 - (double) v10, xf, v10);
                            result = std::fma(v1 - v0, yf, v0);
                            double expected = *expectedResultCursor;
                            if (expected >= MISSING_VALUE_THRESHOLD) {
                                nodataMismatches[it]++;
                            } else {
                                stats = std::max(stats, std::abs(result - expected));
                            }
                        }
                        coordinates[ix] = std::fmod(std::abs(x + result), WIDTH  - 1);
                        coordinates[iy] = std::fmod(std::abs(y + result), HEIGHT - 1);
                        expectedResultCursor++;
                    }
                    errorStatistics[it] = stats;
                }
                free(expectedResults);
            }
            free(coordinates);
        }
        free(raster);
    }
}

/*
 * Run many variants of the tests (with "no data", with NaN).
 * The instance on which this method is invoked is taken as the reference.
 * It should be an instance using "no data" sentinel values, for avoiding
 * any doubt.
 */
void TestNodata::testAndCompare() {
    TestNodata nodataLittleEndian(std::endian::little);
    TestNaN    nanBigEndian      (std::endian::big);
    TestNaN    nanLittleEndian   (std::endian::little);

    bool success = true;
    for (int t=0; ; t++) {
        TestCase *test;
        switch (t) {
            case 0:  test = this; break;
            case 1:  test = &nodataLittleEndian; break;
            case 2:  test = &nanBigEndian; break;
            case 3:  test = &nanLittleEndian; break;
            default: test = NULL; break;
        }
        if (!test) break;
        test->computeAndCompare();
        success &= test->success();
        if (!resultEquals(test)) {
            test->printStatistics();
            success = false;
        }
    }
    if (success) {
        nanBigEndian.printStatistics();
        std::cout << "Success (mismatches in the last iterations are normal).\n";
    } else {
        std::cout << "TEST FAILURE.\n";
    }
}


int main() {
    TestNodata test(std::endian::big);
    test.testAndCompare();
    return 0;
}
