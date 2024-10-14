/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;


/**
 * Base class shared by the two test cases.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public abstract class TestCase extends Configuration {
    /**
     * Statistics about the differences between computed values and expected values.
     * The array length is {@link #NUM_VERIFIED_ITERATIONS}.
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
     * Determines the paths to the files needed by the test.
     * The files to load depend on whether the subclass is testing NaN or "no data" sentinel values.
     *
     * @param useNaN       {@code true} if using NaN, or {@code false} if using "no data".
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    TestCase(final boolean useNaN, final boolean littleEndian) {
        super(useNaN, littleEndian);
        errorStatistics  = new DoubleSummaryStatistics[NUM_VERIFIED_ITERATIONS];
        nodataMismatches = new int[NUM_VERIFIED_ITERATIONS];
    }

    /**
     * Reads the raster, performs interpolations and compares against the expected values.
     * Differences are collected in statistics that can be printed with {@link #printStatistics()}.
     *
     * @throws IOException if an error occurred while reading a file.
     */
    public abstract void computeAndCompare() throws IOException;

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
     * @return statistics where to add errors compared to expected values.
     */
    final DoubleSummaryStatistics prepareNextVerification(final int it,
            final ReadableByteChannel input, final ByteBuffer expectedResults) throws IOException
    {
        expectedResults.clear();
        do if (input.read(expectedResults) < 0) {
            throw new EOFException();
        } while (expectedResults.hasRemaining());
        expectedResults.rewind();
        return errorStatistics[it] = new DoubleSummaryStatistics();
    }

    /**
     * Runs the test and returns whether the test was successful.
     * The test is considered successful if the first iterations have no errors.
     * A drift is tolerated in the last iterations because this test intentionally
     * uses chaotic algorithm in order to test the effect of optimizations enabled
     * by compiler options in the C/C++ variant of this test.
     */
    final boolean run() throws IOException {
        Arrays.fill(nodataMismatches, 0);
        computeAndCompare();
        for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
            if (nodataMismatches[i] != 0) {
                if (i < 8) {
                    return false;   // Mistmatch found after too few iterations.
                }
            }
        }
        return true;
    }

    /**
     * Returns whether the results of another test are equal to the results of this test.
     */
    final boolean resultEquals(final TestCase other) {
        for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
            if (errorStatistics[i].getMax() != other.errorStatistics[i].getMax()
                    || nodataMismatches[i] != other.nodataMismatches[i])
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Prints the statistics collected by this test.
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void printStatistics() {
        System.out.println("Errors in the use of raster data with " + (useNaN ? "NaN" : "\"No data\" sentinel")
                + " values in " + byteOrder.toString().replace("_", " ").toLowerCase() + " byte order:");
        System.out.println("   Count     Minimum     Average     Maximum   Number of \"missing value\" mismatches");
        for (int i=0; i<NUM_VERIFIED_ITERATIONS; i++) {
            DoubleSummaryStatistics stats = errorStatistics[i];
            System.out.printf("%8d %11.4f %11.4f %11.4f %6d%n",
                    stats.getCount(), stats.getMin(), stats.getAverage(), stats.getMax(), nodataMismatches[i]);
        }
    }
}
