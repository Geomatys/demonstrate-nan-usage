This project demonstrates how NaNs can be used instead of "no data" sentinel values in raster data.
This demo aims to answer misunderstandings regarding NaN values, and in particular to refute the idea
that NaN values should not be used in geospatial applications. For proving the refutations listed below,
this demo reads a raster from a RAW file containing values stored as IEEE 754 `float` type, with random
missing values encoded as NaNs. Then, bilinear interpolations are performed at random coordinate values
and compared against expected values. The expected values are computed with a variant of the code using
the classical approach based on "no data" sentinel values, used as a reference.

**Table of content:**

* Misunderstandings about NaN
  * ["There is only one NaN, while we need many no-data values"](#there-is-only-one-nan-while-we-need-many-no-data-values)
  * ["Not the right semantic (we want `null`, `unknown`, …)"](#not-the-right-semantic-we-want-null-unknown-)
  * ["The payload that distinguish NaN values may be lost"](#the-payload-that-distinguish-nan-values-may-be-lost)
  * ["Payload propagation is implementation-dependent"](#payload-propagation-is-implementation-dependent)
* [Analysis](#analysis)
  * [Code differences](#code-differences)
  * [Note on an optimization strategy (optional)](#note-on-an-optimization-strategy-optional)
* [Running the tests](#running-the-tests)



# Misunderstandings about NaN

## "There is only one NaN, while we need many no-data values"
This is false. The IEEE 754 `float` type has 8,388,608 distinct quiet NaN values
(ignoring signaling NaNs) while the `double` type has over 10¹⁵ distinct quiet NaNs.
When the bit pattern of a `float` is interpreted as an `int`, all values in the range `0x7fc00000` to
`0x7fffffff` inclusive are NaN values. This is not the only range of NaN values, but this is the most
convenient range and the one used in this demo.

### Demonstration
For proving that point, this demo uses five distinct NaN values:

  * `CLOUD`   for a value missing because of a cloud.
  * `LAND`    for a value missing because the pixel is on a land (assuming that the data are for some oceanographic phenomenon).
  * `NO_PASS` for a value missing because the remote sensor didn't pass over that area.
  * `UNKNOWN` for any value missing for an unknown reason.

In order to demonstrate that NaN values can be analyzed, the bilinear interpolation arbitrarily applies the following rule:
if exactly one value needed for the interpolation is missing, the interpolation result is missing for the same reason.
If two or more needed values are missing, the interpolation result is missing for the reason having the highest priority
in that order (highest priority first): `UNKNOWN`, `NO_PASS`, `LAND`, and `CLOUD`.


## "Not the right semantic (we want `null`, `unknown`, …)"
NaN means "Not A Number". There is no semantic associated to why a value is NaN. It can be any reason at user's choice.
With more than 4 millions distinct NaN values available, there is plenty of room for assigning whatever semantic we want
to different values. Assigning a "no data" meaning to, e.g., -9999, which *is* a number contrarily to NaN,
is not semantically better.

NaN cannot represent directly a JavaScript `null` or `unknown` value (real values such as -9999 cannot neither).
But `null` and `unknown` semantics are a layer on top of the IEEE 754 values. For example, in the Java language
and C/C++ languages, `null` values cannot be assigned to primitive types. It can only be assigned to wrappers
(Java) or pointers (C/C++). For example, a `java.lang.Float` (in Java) or a `float*` (in C/C++) can be null,
but a `float` cannot. In JavaScript, the fact that `null` and `unknown` apply to all kinds of object
is an indication that they are handled at a level other than primitive types.


## "The payload that distinguish NaN values may be lost"
This argument is based on the fact that IEEE 754 recommends, but does not mandate, NaN's _payload propagation_.
This issue is discussed in the next sub-section, but does not apply to loading or saving values from/to files.
When reading an array of `float` values, the operating system is actually reading **bytes**.
If data are compressed, decompression algorithms such as GZIP usually operate on **bytes**.
Next, the conversion from big-endian byte order to little-endian or conversely is applied on **bytes**.
Only after all those steps have been completed, the final result is interpreted as an array of floating-point values.
Casting a `byte*` pointer to a `float*` pointer in C/C++ does not rewrite the array,
it only changes the way that the computer interprets the bit patterns that are stored in the array.
Even after the cast, if a `float` array needs to be copied, the `memcpy` (C/C++) or `System.arraycopy` (Java)
functions copy bytes without doing any interpretation or conversion of the bit patterns.
Nowhere the Floating-Point Unit (FPU) needs to be involved.
Therefore, there is no way that the NaN's payload can be lost with the above steps.

### Demonstration
This demo provides the same raster data in two versions:
one file using big-endian byte order, and another file using little-endian byte order.
The demo reads the binary files, changes the byte order if needed, then interprets the result as floating-point values.
In the Java case, the read operation involves a copy from the `byte[]` array to a newly allocated `float[]` array.
Execution shows that no payload is lost.


## "Payload propagation is implementation-dependent"
When a quiet NaN value is used in an arithmetic operation, IEEE 754 _recommends_ that the result is the same NaN as the operand.
But this is not mandatory and the actual behavior may vary depending on platform, language or optimizations applied.
Furthermore, the situation is yet more uncertain when two or more operands are different NaN values.
Which one takes precedence?
Empirical tests suggest that, at least with Java 22 on Intel® Core™ i7-8750H, the leftmost operand takes precedence
in additions and multiplications, while the rightmost operand takes precedence in subtractions and divisions.
However, despite this uncertainty, we still have the guarantee that the result will be _some_ NaN value.
The fact that we don't know for sure _which_ NaN values is not worst than the approach using "no data" sentinel values.
For example, when computing the average value of {2, 8, 9999, 3} where 9999 represents a missing value,
if developers forget to check for missing values, they will get 2503.
Subsequent operations consuming that value are likely to fail to recognize 2503 as a missing value,
leading to unpredictable consequences (the first Ariane V rocket exploded because of a floating-point overflow).
By contrast, when using NaN, developers get a NaN result even if they forgot to check for missing values.
Even if we are uncertain about _which_ NaN they got,
it is still better than an accidental value indistinguishable from real values.
Above example computed the average of only 4 values. But larger is the number of values to average,
more difficult it become to notice "no data" sentinel values pollution.

### Demonstration
Applications using "no data" sentinel values need to check for missing values _before_ using them in calculations.
The same is true (in a more flexible way) for applications using NaN values if they wish to distinguish between the different NaN payloads.
However, users of NaN values have two additional flexibilities that users of "no data" sentinel values do not have:

* If an application doesn't care about the reason why a value is missing, it can skip the check completely.
* An application can check for NaN results afterward instead of before doing the calculation,
  and go back to the original inputs only if it decides that it wants to know more about the reasons.

The `TestNodata` demo has to check for sentinel values before any computation. It has no choice.
The `TestNaN` demo could have done the same, but arbitrarily chooses to check for NaN payloads _after_ calculations.
This is an arbitrary choice, `TestNaN` could have been kept in a form almost identical to `TestNodata`.
This is done for illustrating the flexibility mentioned in above points.
This strategy can provide a performance gain when the majority of the values are not missing.

### Note on signaling NaNs
All the discussion in this page and all demos in this project apply to _quiet_ NaNs.
Another kind of NaN exists, named _signaling_ NaNs.
The two kinds of NaN are differentiated by the bit #22, which shall be 1 for quiet NaNs.
Signaling NaNs are out of scope for this discussion.
In particular, signaling NaNs may be silently converted to quiet NaNs, or may cause an interrupt.
They are used for different purposes (e.g., lazy initialization) than the "no data" purpose discussed here.


# Analysis
The two codes (`TestNaN` and `TestNodata`) are very similar,
especially if we ignore the arbitrary choice of testing NaN after the calculation instead of before.
Using NaN in Java and C/C++ does not bring significant complexity compared to using "no data" sentinel values.
NaN values are as reliable as "no data" values: payload cannot be lost during read and copy operations.
On the other hand, using NaN provides a level of safety impossible to achieve with "no data" values,
as developers are protected against accidental sentinel value pollution.

## Code differences
The difference between the `TestNodata` and `TestNaN` is described in a [separated page](differences.md).


## Note on an optimization strategy (optional)
`TestNaN` and `TestNodata` both use the same optimization strategy for selecting a missing reason
in `UNKNOWN`, `NO_PASS`, `LAND`, and `CLOUD` precedence order. This demo exploits the facts that:

1. All "no data" values used in this demo are greater than all valid values.
2. Missing reasons having highest priority are assigned highest "No data" values.

The combination of those two facts allows us to simply check for the maximal value,
no matter if we have a mix of "no data" and real values. However, this trick would not work
anymore if we didn't knew in advance that all "no data" are greater than all valid values.
If they were smaller, some `>=` operators would need to be replaced by `<=` in the `TestNodata` code
(in addition of the `max` function being not applicable anymore).
`TestNodata` would be yet more complex if we had a mix of "no data" smaller and greater than real values
(the use of `abs` could reduce that complexity, but requires positive and negative "no data" in the same range of absolute values).
By contrast, optimizations can be done more easily with NaN because condition equivalent to #1 is more reliable.


## Running the tests
The same test is available in the following languages:
Java.
All commands listed below should be executed in a Unix shell with the project root directory
(the directory containing this `README.md` file) as the current directory.

# Java
Requirements: Java 17 or later, Maven 3 or later.

```bash
mvn clean package
java --module-path target/NaN-1.0-SNAPSHOT.jar --module com.geomatys.nan/com.geomatys.nan.DataGenerator
java --module-path target/NaN-1.0-SNAPSHOT.jar --module com.geomatys.nan/com.geomatys.nan.TestNodata
java --module-path target/NaN-1.0-SNAPSHOT.jar --module com.geomatys.nan/com.geomatys.nan.TestNaN
```
