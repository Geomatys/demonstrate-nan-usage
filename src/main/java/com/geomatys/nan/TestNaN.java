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
     * Creates a new test.
     *
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    private TestNaN(final boolean littleEndian) {
        super(true, littleEndian);
    }

    /**
     * Reads the raster, performs interpolations and compare against expected values.
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
                    final double xf = x - xb;
                    final double yf = y - yb;
                    /*
                     * Apply bilinear interpolation.
                     */
                    double v, v0, v1;
                    int offset = WIDTH * ((int) yb) + ((int) xb);
                    v0 = Math.fma(raster[offset+1] - (v=raster[offset]), xf, v); offset += WIDTH;
                    v1 = Math.fma(raster[offset+1] - (v=raster[offset]), xf, v);
                    v  = Math.fma(v1 - v0, yf, v0);
                    /*
                     * Compare against the expected value,
                     * then compute the position to use in the next iteration.
                     */
                    stats.accept(Math.abs(v - expectedResults.getDouble()));
                    coordinates[ix] = Math.abs(x + v) % (WIDTH  - 1);
                    coordinates[iy] = Math.abs(y + v) % (HEIGHT - 1);
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
        var test = new TestNaN(false);
        test.computeAndCompare();
        test.printStatistics();
    }
}
