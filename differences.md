# Differences in NaN versus "no data" usage
This page shows the main differences between the `TestNodata` and `TestNaN` classes.

## Missing values declaration
The `TestNodata` class uses the following Sentinel values for missing data:

```java
/**
 * Sentinel value for missing data. A value may be missing for different reasons,
 * which are identified by different Sentinel values.
 */
static final float UNKNOWN = 10000,
                   CLOUD   = 10001,
                   LAND    = 10002,
                   NO_PASS = 10003;

/**
 * The threshold used for deciding if a value should be considered as a missing value.
 * Any value greater than this threshold will be considered a missing value.
 */
static final float MISSING_VALUE_THRESHOLD = UNKNOWN;
```

Note that the `MISSING_VALUE_THRESHOLD` value is arbitrary and data-dependent, as it must be a value not used by real values.
Changing the threshold can require changes in the code, for example if the threshold becomes a value smaller than all of the valid values
instead of greater than them. By contrast, an approach based on NaN does not need such an arbitrary choice.
The `TestNaN` class uses the following NaN values for missing data:

```java
/**
 * Value of the first positive quiet NaN.
 */
private static final int FIRST_QUIET_NAN = 0x7FC00000;

/**
 * NaN bit pattern for missing data. A value may be missing for different reasons,
 * which are identified by each of the different NaN values.
 */
static final int UNKNOWN = FIRST_QUIET_NAN,      // This is the default NaN value in Java.
                 CLOUD   = FIRST_QUIET_NAN + 1,
                 LAND    = FIRST_QUIET_NAN + 2,
                 NO_PASS = FIRST_QUIET_NAN + 3;
```


## Missing values check
The `TestNodata` class uses the following code for checking for missing values.
In this case, this check **must** be done before using the values in formulas.
The use of `max` is an optimization based on the fact that, in this test,
the reasons why a value is considered missing are sorted in order of precedence.
Production codes may be more complex if they cannot rely on this assumption.

```java
float v00, v01, v10, v11 = ...;   // The raster data to interpolate.
float missingValueReason = max(
        max(v00, v01),
        max(v10, v11));

double result;
if (missingValueReason >= MISSING_VALUE_THRESHOLD) {
    // At least one value is missing.
    result = missingValueReason;
} else {
    // All values are valid. Interpolate now.
    result = ...;
}
```

The `TestNaN` class uses the following code for checking for missing values.
In this case, contrary to `TestNodata`, the developers are free to check before or after any interpolation is computed.
This example arbitrarily performs the check after the interpolation.
The use of `max` below is the same trick as the one used above.
Production codes may be more complex for the same reasons
but have no reason to be more complex than with "no data" Sentinel values.

```java
float v00, v01, v10, v11 = ...;   // The raster data to interpolate.
double result = ...;              // Interpolate regardless if values are valid.
if (isNaN(result)) {
    // At least one value is missing.
    int missingValueReason = max(
            max(floatToRawIntBits(v00), floatToRawIntBits(v01)),
            max(floatToRawIntBits(v10), floatToRawIntBits(v11)));

    // Result was already NaN, but replace with another NaN for the selected reason.
    result = intBitsToFloat(missingValueReason);
}
```


# Value missing for multiple reasons
Handling NaN values as bit patterns allows a more advanced use case which is not possible
(or at least not as efficiently) with "no data" Sentinel values.
Instead of associating the missing value reasons to different NaN values (over 2 million possibilities),
we can associate these reasons to different *bits*.
The NaN payload has 21 bits (ignoring the signaling NaN bit and the sign bit),
which gives room for 21 different reasons (or 22 if we use also the sign bit).
The `TestNaN` code declaring NaN values can be replaced by the following:

```java
static final int UNKNOWN = FIRST_QUIET_NAN,     // No bit set. This is the default NaN value in Java.
                 CLOUD   = FIRST_QUIET_NAN | 1,
                 LAND    = FIRST_QUIET_NAN | 2,
                 NO_PASS = FIRST_QUIET_NAN | 4,
                 OTHER   = FIRST_QUIET_NAN | 8;
```

And the code computing the missing value reasons (previously using `max`) become the following:

```java
if (isNaN(result)) {  // Optional, this check is also done by `if (missingValueReasons != 0)`.
    int missingValueReasons = 0;
    if (isNaN(v00)) missingValueReasons |= floatToRawIntBits(v00);
    if (isNaN(v01)) missingValueReasons |= floatToRawIntBits(v01);
    if (isNaN(v10)) missingValueReasons |= floatToRawIntBits(v10);
    if (isNaN(v11)) missingValueReasons |= floatToRawIntBits(v11);
    if (missingValueReasons != 0) {
        // At least one value is NaN.
    }
}
```

With this approach, it is possible to declare that a value is missing for multiple reasons.
For example because of `LAND` **and** `NO_PASS`.
