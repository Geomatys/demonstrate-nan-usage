/*
 * This file is hereby placed into the Public Domain.
 * This means anyone is free to do whatever they wish with this file.
 */
package com.geomatys.nan;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests execution of the main method.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
public class MainTest {
    /**
     * Creates a new test case.
     */
    public MainTest() {
    }

    /**
     * Runs {@link Runner#main(String[])} and verifies that the test is considered successful.
     *
     * @throws IOException if an error occurred while reading or writing a file.
     */
    @Test
    public void run() throws IOException {
        Runner.main(null);
        assertTrue(Runner.wasSuccess);
    }
}
