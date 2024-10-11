Demonstrates how NaNs can be used with raster data instead of "no data" values.
This demo reads a raster of pre-defined size from RAW files where values are stored as IEEE 754 `float` type.
Some files use big-endian byte order a other files use little-endian byte order.
Some files use `NaN` for missing valeus and other files use sentinel values such as `-9999`.
Different `NaN` or sentinel values are used for representing data that are missing for different reasons
(cloud, land, satellite not passing over that area, _etc_).
Then, the demo performs interpolations in the raster and asserts that the approach using with `NaN` works in all cases.
