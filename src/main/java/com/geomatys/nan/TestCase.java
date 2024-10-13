/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.DoubleSummaryStatistics;


/**
 * Base class shared by all test cases.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
class TestCase {
    /**
     * The raster size, in pixels.
     */
    static final int WIDTH = 800, HEIGHT = 600;

    /**
     * Number of points where to interpolate.
     */
    static final int NUM_INTERPOLATION_POINTS = 20000;

    /**
     * Number of iterations for which we verify the conformance against expected results.
     * We do not verify all iterations because the actual results diverge strongly from
     * the expected results after about 8 iterations, because of the intentionally chaotic
     * nature of the calculation.
     */
    static final int NUM_VERIFIED_ITERATIONS = 12;

    /**
     * Total number of iterations over the points to interpolate.
     * All iterations after {@link #NUM_VERIFIED_ITERATIONS} are for performance measurements only.
     */
    static final int NUM_ITERATIONS = 15;

    /**
     * Path to a RAW file containing raster data as {@code float} values in an endianness specified by {@link #byteOrder}.
     * The raster size is {@value #WIDTH} × {@value #HEIGHT} pixels and the values are random {@code float} values
     * between -100 and +100. The file contains random missing values identified by "no data" sentinel values or by
     * NaN values, depending on which test is executed.
     */
    final Path rasterFile;

    /**
     * Whether the values in the raster file are in big-endian or little-endian byte order.
     */
    final ByteOrder byteOrder;

    /**
     * Path to a RAW file containing pixel coordinates as {@code double} values in big-endian byte order.
     * For simplicity, coordinates are from 0 inclusive to the width or height minus one, exclusive.
     * This is for avoiding the need to check for bounds before bilinear interpolations.
     * Some coordinates are declared missing by "no data" sentinel values or by NaN,
     * depending on which test is executed.
     */
    final Path coordinatesFile;

    /**
     * Path to a RAW file containing expected results as {@code double} values in big-endian byte order.
     * This file may be large, so it is not loaded fully in memory. Missing results are represented by
     * sentinel values only. NaNs are not used for avoiding any suspicion about test reliability.
     */
    final Path expectedResultsFile;

    /**
     * Statistics about the difference between computed values and expected values.
     * The array length is {@link #NUM_VERIFIED_ITERATIONS}, as there is no reasons
     * to continue collecting statistics after those iterations.
     */
    private final DoubleSummaryStatistics[] errorStatistics;

    /**
     * Number of times where the "no data" values do not match the expected values.
     * This value should be zero during the first iterations, and become non-zero only
     * after the calculation has drifted. Note that the latter case is not an indication
     * that NaN does no work: identical mismatches happen with the "no data" approach too.
     */
    final int[] nodataMismatches;

    /**
     * Creates a new test case.
     *
     * @param useNaN       {@code true} if using NaN, or {@code false} if using "no data".
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    TestCase(final boolean useNaN, final boolean littleEndian) {
        final var directory = Path.of("data").resolve(useNaN ? "nan" : "nodata");
        byteOrder           = littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        rasterFile          = directory.resolve(littleEndian ? "little-endian.raw" : "big-endian.raw");
        coordinatesFile     = directory.resolve("coordinates.raw");
        expectedResultsFile = directory.resolve("expected-results.raw");
        errorStatistics     = new DoubleSummaryStatistics[NUM_VERIFIED_ITERATIONS];
        nodataMismatches    = new int[NUM_VERIFIED_ITERATIONS];
    }

    /**
     * Loads all floating-point values of the raster. This method involves a copy from the byte buffer
     * to the float array, swapping byte order if needed. No replacement of NaN or "no data" value occurs.
     *
     * @return the raster floating-point values.
     * @throws IOException if an I/O error occurred.
     */
    final float[] loadRaster() throws IOException {
        final var source = ByteBuffer.wrap(Files.readAllBytes(rasterFile)).order(byteOrder).asFloatBuffer();
        final var target = FloatBuffer.allocate(source.capacity());
        return target.put(source).array();
    }

    /**
     * Loads coordinate values.
     *
     * @return the coordinate values.
     * @throws IOException if an I/O error occurred.
     */
    final double[] loadCoordinates() throws IOException {
        final var source = ByteBuffer.wrap(Files.readAllBytes(coordinatesFile)).asDoubleBuffer();
        final var target = DoubleBuffer.allocate(source.capacity());
        return target.put(source).array();
    }

    /**
     * Eventually reads bytes from the given input until the given buffer is filled, then rewind the buffer.
     * If the verified iterations are finished, then this method does nothing.
     *
     * @param  it               the iteration number.
     * @param  input            input from which to read expected values.
     * @param  expectedResults  buffer where to store expected valued.
     * @return statistics where to add errors compared to expected values, or {@code null} if none.
     */
    final DoubleSummaryStatistics prepareNextVerification(final int it,
            final ReadableByteChannel input, final ByteBuffer expectedResults) throws IOException
    {
        if (it >= NUM_VERIFIED_ITERATIONS) {
            return null;
        }
        expectedResults.clear();
        do if (input.read(expectedResults) < 0) {
            throw new EOFException();
        } while (expectedResults.hasRemaining());
        expectedResults.rewind();
        return errorStatistics[it] = new DoubleSummaryStatistics();
    }

    /**
     * Prints the statistics collected by this tests.
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    final void printStatistics() {
        System.out.println("   Count     Minimum     Average     Maximum   Number of \"missing value\" mismatches");
        for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
            DoubleSummaryStatistics stats = errorStatistics[i];
            System.out.printf("%8d %11.4f %11.4f %11.4f %6d%n",
                    stats.getCount(), stats.getMin(), stats.getAverage(), stats.getMax(), nodataMismatches[i]);
        }
        for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
            if (nodataMismatches[i] != 0) {
                if (i < 8) {
                    System.out.println("TEST FAILURE.");
                    return;
                }
                break;
            }
        }
        System.out.println("Success (mismatches in the last iterations are normal).");
    }
}
