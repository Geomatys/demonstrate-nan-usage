/**
 * Demonstrates how NaNs can be used with raster data instead of "no data" values.
 * This demo performs interpolations in a grid of random values where some values
 * are missing for different reasons, identified by the pad values. Two variants
 * are provided: one using NaN, and one using "no data". This demo shows how NaN
 * works, and opportunistically compares the performance compared to the "no data"
 * approach.
 *
 * @author Martin Desruisseaux (Geomatys)
 */
package com.geomatys.nan;
