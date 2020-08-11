# DynaHist

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


A fast, memory efficient, merge- and serializable histogram implementation with configurable bin layout. DynaHist includes 
layout implementations that allow value recording with strict error guarantees. The value precision is defined by a relative and 
an absolute error bound, respectively. Either the absolute error or the relative error bound is satisfied for every recorded 
value from the specified value range that may include many orders of magnitude including negative and positive values.

DynaHist works by mapping floating-point values to bins and counting the total number of values per bin dynamically. 
The bin index-mapping is defined in the `Layout`. Choose between `ErrorLimitingLayout1` and `ErrorLimitingLayout2` or 
define your own `Layout` using `CustomLayout`. Select `ErrorLimitingLayout1`, if speed is more important than
memory efficiency, or select `ErrorLimitingLayout2`, if memory efficiency is more important than speed.

## Basic functionality

Get [further information](https://github.com/dynatrace-oss/dynahist/tree/master/docs/description.md) about DynaHist. Learn more 
about how DynaHist maps values, how DynaHist guarantee a given error over a given interval and how DynaHist saves memory and gains speed.

```java
// Creating a dynamic Histogram
Layout layout = ErrorLimitingLayout2.create(1e-5, 1e-2, -1e6, 1e6); // limit absolute error either 
                                                                    // by 1e-5 or relative error 
                                                                    // by 1e-2 over [-1e-6, 1e6]
Histogram histogram = Histogram.createDynamic(layout);             

// Adding values to the Histogram
histogram.addValue(-5.5); // adds a given value
histogram.addValue(4.3, 6); // adds a given value with a given multiplicity

// Querying the Histogram
histogram.getQuantileEstimate(0.5); // returns the median
histogram.getMin();
histogram.getMax();
histogram.getTotalCount();

// Merging Histograms
histogram.addHistogram(otherHistogram);

// Serialization
histogram.write(dataOutput); // write Histogram in DataOutput
histogram.readAsDynamic(layout, dataInput); // read dynamic Histogram

```
## License

[Apache Version 2.0](https://github.com/dynatrace-oss/dynahist/blob/master/LICENSE)

## Main Contributors
* Otmar Ertl
* Markus Remplbauer