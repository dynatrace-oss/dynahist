# DynaHist

[![CircleCI](https://circleci.com/gh/dynatrace-oss/dynahist/tree/master.svg?style=svg)](https://circleci.com/gh/dynatrace-oss/dynahist/tree/master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dynatrace-oss_dynahist&metric=alert_status)](https://sonarcloud.io/dashboard?id=dynatrace-oss_dynahist)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dynatrace-oss_dynahist&metric=coverage)](https://sonarcloud.io/dashboard?id=dynatrace-oss_dynahist)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


A fast, memory efficient, merge- and serializable histogram implementation with configurable bin layout. DynaHist includes 
layout implementations that allow value recording with strict error guarantees. The value precision is defined by a relative and 
an absolute error bound, respectively. Either the absolute error or the relative error bound is satisfied for every recorded 
value from the specified value range that may include many orders of magnitude including negative and positive values.

DynaHist works by mapping floating-point values to bins and counting the total number of values per bin dynamically. 
The bin index-mapping is defined in the `Layout`. Choose between `ErrorLimitingLayout1` and `ErrorLimitingLayout2` or 
define your own `Layout` using `CustomLayout`. Select `ErrorLimitingLayout1`, if speed is more important than
memory efficiency, or select `ErrorLimitingLayout2`, if memory efficiency is more important than speed.

## Basic Functionality

Get [further information](https://github.com/dynatrace-oss/dynahist/tree/master/docs/description.md) about DynaHist. Learn more 
about how DynaHist maps values, how DynaHist guarantee a given error over a given interval and how DynaHist saves memory and gains speed.

```java
// Creating a dynamic histogram
Layout layout = ErrorLimitingLayout2.create(1e-5, 1e-2, -1e9, 1e9); // limit absolute error either 
                                                                    // by 1e-5 or relative error 
                                                                    // by 1e-2 over [-1e9, 1e9]
Histogram histogram = Histogram.createDynamic(layout);             

// Adding values to the histogram
histogram.addValue(-5.5); // adds the value -5.5
histogram.addValue(4.3, 6); // adds the value 4.3 six times
histogram.addAscendingSequence(i -> i + 1, 1000000000); // adds the first billion positive integers

// Querying the histogram
histogram.getTotalCount();
histogram.getMin();
histogram.getMax();
histogram.getValueEstimate(1); // returns an estimate of the 2nd largest value
histogram.getQuantileEstimate(0.5); // returns an estimate of the median

// Merging histograms
histogram.addHistogram(otherHistogram);

// Serialization
histogram.write(dataOutput); // write histogram to a java.io.DataOutput
histogram.readAsDynamic(layout, dataInput); // read dynamic histogram from a java.io.DataInput
```

## License

[Apache Version 2.0](https://github.com/dynatrace-oss/dynahist/blob/master/LICENSE)

## Main Contributors
* Otmar Ertl
* Markus Remplbauer