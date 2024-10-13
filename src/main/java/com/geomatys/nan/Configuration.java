/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.nio.ByteOrder;
import java.nio.file.Path;


/**
 * Directories and other information used by a test or the data generator.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
class Configuration {
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
     * Whether this test case uses NaN instead of "no data" sentinel values.
     */
    final boolean useNaN;

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
     * Creates a new configuration.
     *
     * @param useNaN       {@code true} if using NaN, or {@code false} if using "no data".
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    Configuration(final boolean useNaN, final boolean littleEndian) {
        final var directory = Path.of("target").resolve("generated-data").resolve(useNaN ? "nan" : "nodata");
        this.useNaN         = useNaN;
        byteOrder           = littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        rasterFile          = directory.resolve(littleEndian ? "little-endian.raw" : "big-endian.raw");
        coordinatesFile     = directory.resolve("coordinates.raw");
        expectedResultsFile = directory.resolve("expected-results.raw");
    }
}
