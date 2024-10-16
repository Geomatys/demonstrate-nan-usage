# Notes on C/C++
The C/C++ standard library supports NaN values.
Not only the standard defines a `NAN` constant and a `std::isnan(float)` function (among others),
but the existence of multiple distinct NaN values is explicitly supported since the C++ 11 standard.

* The `std::nanf(char*)` function generates a quiet NaN that can be used by library implementations to distinguish
  different NaN values (sources: [cplusplus.com](https://cplusplus.com/reference/cmath/nan-function/),
  [cppreference.com](https://en.cppreference.com/w/c/numeric/math/nan)).
* The `std::strtof(char*)` function can parse character strings such as `NAN("cloud")`
  (source: [cppreference.com](https://en.cppreference.com/w/cpp/string/byte/strtof)).

Note: in our tests with GCC 14.2.1 20240912 (Red Hat 14.2.1-3),
`strtof` returns `7fc00000` for all character strings that do not represent a number.
For character strings that represent a decimal number, that value seems to be simply added to `7fc00000`.
Therefore, codes like below:

```cpp
const int32_t UNKNOWN = FIRST_QUIET_NAN,
              CLOUD   = FIRST_QUIET_NAN + 1,
              LAND    = FIRST_QUIET_NAN + 2,
              NO_PASS = FIRST_QUIET_NAN + 3;
```

Could be replaced by:

```cpp
const float UNKNOWN = std::nanf("0"),
            CLOUD   = std::nanf("1"),
            LAND    = std::nanf("2"),
            NO_PASS = std::nanf("3");
```

Developers may which to keep former approach for better control.
However, it shows that the existence of multiple distinct NaN values is recognized by the C++ 11 standard.
The documentation of `std::nanf(char*)` also said:

> If the implementation supports IEEE floating-point arithmetic (IEC 60559), it also supports quiet NaNs.


## Getting the bits of a float
The following function is equivalent to `java.lang.Float.floatToRawIntBits(float)`.
Note: NaN values have a sign bit (we can have "negative" NaN), so we really need the signed type below.
The choice of signed or unsigned type changes the way that the `max` function will behave when checking
which NaN has precedence in this test.

```cpp
inline int32_t floatToRawIntBits(float value) {
    return *(reinterpret_cast<int32_t*>(&value));
}
```


## Compliance
By default, C/C++ compilers (or at least `gcc`) produces code compliant with IEEE / ISO rules.
The code generated with those default options passes the C/C++ test provided in this project.
However, the `gcc` compiler provide a `-ffast-math` option with the following warning:

> This option is not turned on by any `-O` option besides `-Ofast` since it can result in incorrect output
> for programs that depend on an exact implementation of IEEE or ISO rules/specifications for math functions.
> It may, however, yield faster code for programs that do not require the guarantees of these specifications.

This project checks whether `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `floor`, `ceil`, `trunc`,
`pow`, `sqrt`, `hypot` and `nanf` functions work as expected with NaN. The test shows that those functions
continue to work as expected even with the `-ffast-math` option. The only function that fails is `std::isnan`.
However, the following check:

```cpp
if (std::isnan(result))
```

can be trivially replaced by the following, providing that the NaN values are quiet NaNs and are "positive"
(sign bit unset):

```cpp
if (missingValueReason >= FIRST_QUIET_NAN)
```

The above trick is possible in the particular case of this test (see the C/C++ source code) because of the way
that `missingValueReason` is computed. This test uses `max` operations, together with the fact that "positive"
quiet NaNs are greater than all other IEEE 754 values when compared as signed integers.

However, developers should consider whether they really need the `-ffinite-math-only` option.
This option and the `-fno-signed-zeros` option seem to have an effect mostly at compile-time,
and mostly on trivial codes such as `x + 0.0`. We did not observed in this test any change of
the behavior of arithmetic operations and mathematical functions other than `std::isnan(…)`.
Those options may be a high risk for a performance gain that may not exist.
