/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.DoubleSummaryStatistics;


/**
 * Demonstrates that NaN values can be read and processed without any lost of information.
 * The calculation is a bilinear interpolation.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public class TestNaN extends TestCase {
    /**
     * Value of the first positive quiet NaN.
     */
    private static final int FIRST_QUIET_NAN = 0x7FC00000;

    /**
     * NaN bit pattern for a missing data. A value may be missing for different reasons, which are identified
     * by different NaN values. This test uses the following values, in precedence order. For example,
     * if a calculation involves two pixels missing for {@code CLOUD} and {@code LAND} reasons respectively,
     * then the result will be considered missing for the {@code LAND} reason.
     *
     * <ol>
     *   <li>Missing for an unknown reason.</li>
     *   <li>Missing interpolation result because of missing coordinate values.</li>
     *   <li>Missing because the remote sensor didn't pass over that area.</li>
     *   <li>Missing because the pixel is on a land (assuming that the data are for some oceanographic phenomenon).</li>
     *   <li>Missing because of a cloud.</li>
     * </ol>
     */
    static final int CLOUD   = FIRST_QUIET_NAN | 1,     // Note: using | operator, but + would work as well.
                     LAND    = FIRST_QUIET_NAN | 2,
                     NO_PASS = FIRST_QUIET_NAN | 3,
                     UNKNOWN = FIRST_QUIET_NAN | 5;

    /**
     * Creates a new test which will use NaN for identifying the missing values.
     *
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    private TestNaN(final boolean littleEndian) {
        super(true, littleEndian);
    }

    /**
     * Reads the raster, performs interpolations and compare against the expected values.
     * Differences are collected in statistics that can be printed with {@link #printStatistics()}.
     * This method is the interesting part of the tests, where both approaches (NaN versus "no data") differ.
     *
     * @throws IOException if an error occurred while reading a file.
     */
    private void computeAndCompare() throws IOException {
        final float[]  raster = loadRaster();
        final double[] coordinates = loadCoordinates();
        final ByteBuffer expectedResults = ByteBuffer.allocate(NUM_INTERPOLATION_POINTS * Double.BYTES);
        try (ReadableByteChannel input = Files.newByteChannel(expectedResultsFile)) {
            for (int it=0; it<NUM_ITERATIONS; it++) {
                final DoubleSummaryStatistics stats = prepareNextVerification(it, input, expectedResults);
                for (int i=0; i<NUM_INTERPOLATION_POINTS; i++) {
                    /*
                     * Get all sample values that we need for the bilinear interpolation.
                     * Variables starting with "v" are converted from `float` to `double`.
                     * Note that it includes conversions from `float` NaN to `double` NaN.
                     */
                    final int ix = i << 1;
                    final int iy = ix | 1;
                    final double x  = coordinates[ix];
                    final double y  = coordinates[iy];
                    final double xb = Math.floor(x);
                    final double yb = Math.floor(y);

                    int offset = WIDTH * ((int) yb) + ((int) xb);
                    final float v00 = raster[offset];
                    final float v01 = raster[offset + 1];
                    final float v10 = raster[offset += WIDTH];
                    final float v11 = raster[offset + 1];
                    double result;    // To be computed below.
                    /*
                     * Apply bilinear interpolation. Contrarily to the `TestNodata` case, we compute
                     * this interpolation unconditionally without checking if the data are valid.
                     * This is an arbitrary choice, we could have made the two codes more similar.
                     * We do that for illustrating this flexibility, and for showing that we can
                     * rely on the result being some kind of NaN on all platforms and languages.
                     */
                    double xf = x - xb;
                    double yf = y - yb;
                    double v0 = Math.fma(v01 - (double) v00, xf, v00);
                    double v1 = Math.fma(v11 - (double) v10, xf, v10);
                    result = Math.fma(v1 - v0, yf, v0);
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
                    if (Double.isNaN(result)) {
                        if (stats != null) {
                            final int max = Math.max(
                                    Math.max(Float.floatToRawIntBits(v00), Float.floatToRawIntBits(v01)),
                                    Math.max(Float.floatToRawIntBits(v10), Float.floatToRawIntBits(v11)));
                            /*
                             * Convert the NaN pattern to the "no data" sentinel value used by `DataGenerator`.
                             * This step is not needed in an application using NaN. This test is doing that
                             * conversion only because we choose to store missing values as "no data" in the
                             * "expected-results.raw" file.
                             */
                            final double nodata;
                            switch (max) {
                                case CLOUD:   nodata = TestNodata.CLOUD;   break;
                                case LAND:    nodata = TestNodata.LAND;    break;
                                case NO_PASS: nodata = TestNodata.NO_PASS; break;
                                default:      nodata = TestNodata.UNKNOWN; break;
                            }
                            if (nodata != expectedResults.getDouble()) {
                                nodataMismatches[it]++;
                            }
                        }
                        result = 1;      // For moving to another position during the next iteration.
                    } else if (stats != null) {
                        final double expected = expectedResults.getDouble();
                        if (expected >= TestNodata.MISSING_VALUE_THRESHOLD) {
                            nodataMismatches[it]++;
                        } else {
                            stats.accept(Math.abs(result - expected));
                        }
                    }
                    coordinates[ix] = Math.abs(x + result) % (WIDTH  - 1);
                    coordinates[iy] = Math.abs(y + result) % (HEIGHT - 1);
                }
            }
        }
    }

    /**
     * Invoked on the command-line for running the test with NaN values.
     * This class looks for the {@code data} sub-directory in the current directory.
     *
     * @param  args ignored.
     * @throws IOException if an error occurred while reading a file.
     */
    public static void main(String[] args) throws IOException {
        var test = new TestNaN(false);
        test.computeAndCompare();
        test.printStatistics();
    }
}
