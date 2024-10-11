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
 * Used only for comparison purposes.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public class TestNodata extends TestCase {
    /**
     * Sentinel value for data missing because of a cloud.
     */
    static final float CLOUD = -9996;

    /**
     * Sentinel value for data missing because the pixel is on a land.
     * This is assuming that the data are for some oceanographic phenomenon.
     * If a calculation involves both {@link #CLOUD} and {@code LAND}, then
     * {@code LAND} has precedence as the reason why the result is missing.
     */
    static final float LAND = -9997;

    /**
     * Sentinel value for data missing because the remote sensor didn't pass over that area.
     * This reason has precedence over {@link #LAND} and {@link #CLOUD}.
     */
    static final float NO_PASS = -9998;

    /**
     * Sentinel value for data missing for an unknown reason.
     * This reason has precedence over all other reasons why a value is missing.
     */
    static final float UNKNOWN = -9999;

    /**
     * The threshold used for deciding if a value should be considered as a missing value.
     * Any value smaller than this threshold is considered a missing value.
     */
    static final float MISSING_VALUE_LIMIT = CLOUD;

    /**
     * Creates a new test.
     *
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    private TestNodata(final boolean littleEndian) {
        super(false, littleEndian);
    }

    /**
     * Reads the raster, performs interpolations and compare against expected values.
     * This method is the interesting part of the test, where both approach (NaN versus "no data") differ.
     *
     * @throws IOException if an error occurred while reading a file.
     */
    private void computeAndCompare() throws IOException {
        final float[]  raster = loadRaster();
        final double[] coordinates = loadCoordinates();
        final ByteBuffer expectedResults = ByteBuffer.allocate(NUM_INTERPOLATION_POINTS * Double.BYTES);
        try (ReadableByteChannel input = Files.newByteChannel(expectedResultsFile)) {
            for (int it=0; it<NUM_ITERATIONS; it++) {
                do input.read(expectedResults);
                while (expectedResults.hasRemaining());
                expectedResults.rewind();
                final var stats = new DoubleSummaryStatistics();
                for (int i=0; i<NUM_INTERPOLATION_POINTS; i++) {
                    final int ix = i << 1;
                    final int iy = ix | 1;
                    final double x  = coordinates[ix];
                    final double y  = coordinates[iy];
                    final double xb = Math.floor(x);
                    final double yb = Math.floor(y);
                    /*
                     * Get the sample values that we need for the interpolation
                     * and check if any of them is flagged as a missing value.
                     *
                     * Optimization: the "no data" value having precedence have the lowest values.
                     * So the result is simply the minimal value, no matter if some values are not
                     * missing values.
                     */
                    int offset = WIDTH * ((int) yb) + ((int) xb);
                    final double v00 = raster[offset];
                    final double v01 = raster[offset + 1];
                    final double v10 = raster[offset += WIDTH];
                    final double v11 = raster[offset + 1];
                    final double min = Math.min(Math.min(v00, v01), Math.min(v10, v11));
                    final double expected = expectedResults.getDouble();
                    double value;
                    if (min <= MISSING_VALUE_LIMIT) {
                        if (min != expected) {
                            nodataMismatches[it]++;
                        }
                        value = 1;      // For moving to another position during the next iteration.
                    } else {
                        /*
                         * Apply the bilinear interpolation and compare against the expected value.
                         */
                        double xf = x - xb;
                        double yf = y - yb;
                        double v0 = Math.fma(v01 - v00, xf, v00);
                        double v1 = Math.fma(v11 - v10, xf, v10);
                        value = Math.fma(v1 - v0, yf, v0);
                        stats.accept(Math.abs(value - expected));
                    }
                    coordinates[ix] = Math.abs(x + value) % (WIDTH  - 1);
                    coordinates[iy] = Math.abs(y + value) % (HEIGHT - 1);
                }
                errorStatistics[it] = stats;
                expectedResults.clear();
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
        var test = new TestNodata(false);
        test.computeAndCompare();
        test.printStatistics();
    }
}
