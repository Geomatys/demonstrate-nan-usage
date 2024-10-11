/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;


/**
 * Generates test data together with the expected results. This class uses an
 * arbitrary large precision for allowing us to opportunistically measure the
 * deviation between computed values and expected values in C/C++ code with
 * different compiler options.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public final class DataGenerator extends TestCase {
    /**
     * Inverse of the proportion of "no data" values.
     * A value of 8 means that 1/8 of the data will be missing, i.e. 12.5%
     */
    private static int NODATA_INVERSE_PROPORTION = 8;

    /**
     * The random generator used for generating raster and coordinate values.
     * A constant seed is used in order to produce the same random numbers on each invocation.
     */
    private final RandomGenerator random;

    /**
     * An arbitrary precision used for calculation.
     */
    private final MathContext precision;

    /**
     * Creates a new data generator.
     *
     * @param littleEndian {@code true} for little-endian byte order, or {@code false} for big-endian.
     */
    private DataGenerator(final boolean littleEndian) {
        super(false, littleEndian);
        random = new Random(2082799447325596418L);
        precision = new MathContext(20, RoundingMode.HALF_EVEN);
    }

    /**
     * Generates and save the raster data, then return the values as big decimals.
     * We use big decimals for internal computation in this class because an opportunistic goal
     * of this test is to measure the drifts from expected results caused by compiler options.
     *
     * @return the raster data.
     * @throws IOException if an error occurred while writing the data.
     */
    private BigDecimal[] generateRaster() throws IOException {
        final var raster = new float[WIDTH * HEIGHT];
        for (int i=0; i < raster.length; i++) {
            if (random.nextInt(NODATA_INVERSE_PROPORTION) == 0) {
                raster[i] = random.nextInt((int) TestNodata.UNKNOWN, (int) TestNodata.MISSING_VALUE_LIMIT + 1);
            } else {
                raster[i] = random.nextFloat(-100, 100);
            }
        }

        // Save to a file.
        final var buffer = ByteBuffer.allocate(raster.length * Float.BYTES).order(byteOrder);
        buffer.asFloatBuffer().put(raster);
        Files.write(rasterFile, buffer.array());

        // Convert to BigDecimals.
        final var numbers = new BigDecimal[raster.length];
        Arrays.setAll(numbers, (i) -> new BigDecimal(raster[i]));
        return numbers;
    }

    /**
     * Generates and save the coordinate data.
     *
     * @return the coordinate data.
     * @throws IOException if an error occurred while writing the data.
     */
    private double[] generateCoordinates() throws IOException {
        final var coordinates = new double[NUM_INTERPOLATION_POINTS * 2];
        for (int i=0; i < coordinates.length;) {
            coordinates[i++] = random.nextDouble(WIDTH  - 1);
            coordinates[i++] = random.nextDouble(HEIGHT - 1);
        }
        final var buffer = ByteBuffer.allocate(coordinates.length * Double.BYTES);
        buffer.asDoubleBuffer().put(coordinates);
        Files.write(coordinatesFile, buffer.array());
        return coordinates;
    }

    /**
     * Generates the test files filled with random values.
     *
     * @throws IOException if an error occurred while writing a file.
     */
    private void generate() throws IOException {
        final BigDecimal   width        = BigDecimal.valueOf(WIDTH  - 1);
        final BigDecimal   height       = BigDecimal.valueOf(HEIGHT - 1);
        final BigDecimal[] raster       = generateRaster();
        final double[]     coordinates  = generateCoordinates();
        final ByteBuffer   results      = ByteBuffer.allocate(NUM_INTERPOLATION_POINTS * Double.BYTES);
        try (WritableByteChannel output = Files.newByteChannel(expectedResultsFile, writeOptions())) {
            for (int it=0; it<NUM_ITERATIONS; it++) {
                for (int i=0; i<NUM_INTERPOLATION_POINTS; i++) {
                    final int ix = i << 1;
                    final int iy = ix | 1;
                    final BigDecimal x  = new BigDecimal(coordinates[ix]);    // Really not BigDecimal.valueOf(double).
                    final BigDecimal y  = new BigDecimal(coordinates[iy]);
                    final BigDecimal xb = x.setScale(0, RoundingMode.FLOOR);
                    final BigDecimal yb = y.setScale(0, RoundingMode.FLOOR);
                    /*
                     * Get the sample values that we need for the interpolation
                     * and check if any of them is flagged as a missing value.
                     */
                    int offset = WIDTH * yb.intValue() + xb.intValue();
                    BigDecimal v00    = raster[offset];
                    BigDecimal v01    = raster[offset + 1];
                    BigDecimal v10    = raster[offset += WIDTH];
                    BigDecimal v11    = raster[offset + 1];
                    BigDecimal value  = v00.min(v01).min(v10).min(v11);
                    boolean isMissing = value.intValue() <= TestNodata.MISSING_VALUE_LIMIT;
                    if (!isMissing) {
                        /*
                         * Apply the bilinear interpolation and compare against the expected value.
                         */
                        BigDecimal xf = x.subtract(xb, precision);
                        BigDecimal yf = y.subtract(yb, precision);
                        BigDecimal v0 = v01.subtract(v00, precision).multiply(xf, precision).add(v00, precision);
                        BigDecimal v1 = v11.subtract(v10, precision).multiply(xf, precision).add(v10, precision);
                        value = v1.subtract(v0, precision).multiply(yf, precision).add(v0, precision);
                    }
                    /*
                     * Store the result and move to the next position. This is where drift may happen and the reason why
                     * this method is using big decimals for producing expected results that we can use as a reference.
                     * Note that we recast each results as a `double` for matching the precision used in the tests.
                     */
                    results.putDouble(value.doubleValue());
                    if (isMissing) {
                        value = BigDecimal.ONE;     // For moving to another position.
                    }
                    coordinates[ix] = x.add(value, precision).abs().remainder(width,  precision).doubleValue();
                    coordinates[iy] = y.add(value, precision).abs().remainder(height, precision).doubleValue();
                }
                results.rewind();
                do output.write(results);
                while (results.hasRemaining());
                results.clear();
            }
        }
    }

    /**
     * Reformats the data written by the given generator.
     * This method can change the byte order.
     */
    private void reformat(final DataGenerator data) throws IOException {
        var source = ByteBuffer.wrap(Files.readAllBytes(data.rasterFile)).order(data.byteOrder);
        var target = ByteBuffer.allocate(source.capacity()).order(byteOrder);
        target.asDoubleBuffer().put(source.asDoubleBuffer());
        Files.write(rasterFile, target.array(), writeOptions());
    }

    /**
     * The options for creating a file in write mode, replacing any previously existing files.
     */
    private static StandardOpenOption[] writeOptions() {
        return new StandardOpenOption[] {
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        };
    }

    /**
     * Invoked on the command-line for generating the data files.
     * This class looks for the {@code data} sub-directory in the current directory.
     *
     * @param  args ignored.
     * @throws IOException if an error occurred while writing a file.
     */
    public static void main(String[] args) throws IOException {
        final var data = new DataGenerator(false);      // Big endian
        final var alt1 = new DataGenerator(true);       // Little endian
        data.generate();
        alt1.reformat(data);
    }
}
