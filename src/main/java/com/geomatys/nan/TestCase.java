/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
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
     * Number of iterations over the points to interpolate.
     */
    static final int NUM_ITERATIONS = 10;

    /**
     * Path to a RAW file containing raster data as {@code float} values.
     * The raster size is {@value #WIDTH} × {@value #HEIGHT} pixels.
     * The file contains NaN or "no data" values.
     */
    final Path rasterFile;

    /**
     * Whether the values in the raster file are in big-endian or little-endian order.
     */
    final ByteOrder byteOrder;

    /**
     * Path to a RAW file containing pixel coordinates as {@code double} values in big-endian byte order.
     * For simplicity, coordinates are from 0 inclusive to the width or height minus one, exclusive.
     * This is for avoiding the need to check for bounds before bilinear interpolations.
     */
    final Path coordinatesFile;

    /**
     * Path to a RAW file containing expected results as {@code float} values in big-endian byte order.
     * This file may be large, so it is not loaded fully in memory.
     */
    final Path expectedResultsFile;

    /**
     * Statistics about the difference between computed values and expected values.
     */
    final DoubleSummaryStatistics[] errorStatistics;

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
        errorStatistics     = new DoubleSummaryStatistics[NUM_ITERATIONS];
        nodataMismatches    = new int[NUM_ITERATIONS];
    }

    /**
     * Loads all floating point values of the raster. This method involves a copy from the byte buffer
     * to the float array, swapping byte order if needed. No replacement of NaN or "no data" value occurs.
     *
     * @return the floating point values.
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
     * Prints the statistics collected by this tests.
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    final void printStatistics() {
        for (int i=0; i<NUM_ITERATIONS; i++) {
            DoubleSummaryStatistics stats = errorStatistics[i];
            System.out.printf("%8d %11.4f %11.4f %11.4f %6d%n",
                    stats.getCount(), stats.getMin(), stats.getAverage(), stats.getMax(), nodataMismatches[i]);
        }
    }
}
