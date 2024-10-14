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
 * Same calculation as {@code TestNaN} but using sentinel values.
 * Used only for comparison purposes (reference implementation).
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public class TestNodata extends TestCase {
    /**
     * Sentinel value for a missing data. A value may be missing for different reasons, which are identified
     * by different sentinel values. This test uses the following values, in precedence order. For example,
     * if a calculation involves two pixels missing for {@code CLOUD} and {@code LAND} reasons respectively,
     * then the result will be considered missing for the {@code LAND} reason.
     *
     * <ol>
     *   <li>Missing because the remote sensor didn't pass over that area.</li>
     *   <li>Missing because the pixel is on a land (assuming that the data are for some oceanographic phenomenon).</li>
     *   <li>Missing because of a cloud.</li>
     *   <li>Missing for an unknown reason.</li>
     * </ol>
     */
    public static final float UNKNOWN = 10000,
                              CLOUD   = 10001,
                              LAND    = 10002,
                              NO_PASS = 10003;

    /**
     * The threshold used for deciding if a value should be considered as a missing value.
     * Any value greater than this threshold will be considered a missing value.
     *
     * <p>Note that this strategy works only if all missing values are greater than all valid values.
     * Conversely, a strategy where all missing values are smaller than valid values would also work.
     * However, if the missing values can be anything (for example some of them smaller and some of
     * them greater than valid values), then the code would need to be more complex and slower.</p>
     */
    static final float MISSING_VALUE_THRESHOLD = UNKNOWN;

    /**
     * Creates a new test which will use "no data" sentinel values for identifying the missing values.
     *
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    public TestNodata(final boolean littleEndian) {
        super(false, littleEndian);
    }

    /**
     * Reads the raster, performs interpolations and compares against the expected values.
     * Differences are collected in statistics that can be printed with {@link #printStatistics()}.
     * This method is the interesting part of the tests, where both approaches (NaN versus "no data") differ.
     *
     * @throws IOException if an error occurred while reading a file.
     */
    @Override
    public void computeAndCompare() throws IOException {
        final float[]  raster = loadRaster();
        final double[] coordinates = loadCoordinates();
        final ByteBuffer expectedResults = ByteBuffer.allocate(NUM_INTERPOLATION_POINTS * Double.BYTES);
        try (ReadableByteChannel input = Files.newByteChannel(expectedResultsFile)) {
            for (int it=0; it<NUM_VERIFIED_ITERATIONS; it++) {
                final DoubleSummaryStatistics stats = prepareNextVerification(it, input, expectedResults);
                for (int i=0; i<NUM_INTERPOLATION_POINTS; i++) {
                    /*
                     * Get all sample values that we need for the bilinear interpolation.
                     * Variables starting with "v" are converted from `float` to `double`.
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
                    final float missingValueReason = Math.max(
                            Math.max(v00, v01),
                            Math.max(v10, v11));
                    if (missingValueReason >= MISSING_VALUE_THRESHOLD) {
                        if (missingValueReason != expectedResults.getDouble()) {
                            nodataMismatches[it]++;
                        }
                        result = 1;      // For moving to another position during the next iteration.
                    } else {
                        /*
                         * Apply the bilinear interpolation and compare against the expected value.
                         */
                        double xf = x - xb;
                        double yf = y - yb;
                        double v0 = Math.fma(v01 - (double) v00, xf, v00);
                        double v1 = Math.fma(v11 - (double) v10, xf, v10);
                        result = Math.fma(v1 - v0, yf, v0);
                        final double expected = expectedResults.getDouble();
                        if (expected >= MISSING_VALUE_THRESHOLD) {
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
}
