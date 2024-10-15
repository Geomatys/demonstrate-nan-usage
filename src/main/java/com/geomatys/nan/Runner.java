/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.IOException;
import java.nio.file.Files;


/**
 * The main code for launching data generation and test execution.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public class Runner {
    /**
     * Whether the last execution of the main method has been successful.
     * This flag is used only for verification purpose by JUnit test.
     */
    static boolean wasSuccess;

    /**
     * Do not allow instantiation of this class.
     */
    private Runner() {
    }

    /**
     * Invoked on the command-line for running the test with "no data" and NaN values.
     * The date are searched in the {@code data} sub-directory in the current directory.
     * This directory must exist but may be empty. Test files will be created if missing.
     *
     * @param  args ignored.
     * @throws IOException if an error occurred while reading or writing a file.
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void main(String[] args) throws IOException {
        final TestCase reference = new TestNodata(false);
        if (Files.notExists(reference.rasterFile)) {
            System.out.println("Generating test data. The data are saved for reuse in next test executions.");
            DataGenerator.main(args);
        }
        final TestCase[] tests = {
            reference,
            new TestNodata(true),       // "No data" sentinel value in little-endian byte order.
            new TestNaN(false),         // NaN in big-endian byte order.
            new TestNaN(true)           // NaN in little-endian byte order.
        };
        boolean success = true;
        boolean hasShownStatistics = false;
        System.out.println("Running 10 iterations of the tests.");
        for (int i=0; i<10; i++) {
            for (TestCase test : tests) {
                success &= test.run();
                if (!reference.resultEquals(test)) {
                    test.printStatistics();
                    hasShownStatistics = true;
                    success = false;
                }
            }
        }
        if (!hasShownStatistics) {
            tests[2].printStatistics();     // NaN in big-endian byte order.
        }
        if (success) {
            System.out.println("Success (mismatches in the last iterations are normal).");
        } else {
            System.out.println("TEST FAILURE.");
        }
        wasSuccess = success;
    }
}
