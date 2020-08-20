# DynaHist

[![CircleCI](https://circleci.com/gh/dynatrace-oss/dynahist/tree/master.svg?style=svg)](https://circleci.com/gh/dynatrace-oss/dynahist/tree/master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dynatrace-oss_dynahist&metric=alert_status)](https://sonarcloud.io/dashboard?id=dynatrace-oss_dynahist)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dynatrace-oss_dynahist&metric=coverage)](https://sonarcloud.io/dashboard?id=dynatrace-oss_dynahist)
[![Download](https://api.bintray.com/packages/dynatrace/dynahist/com.dynatrace.dynahist/images/download.svg?)](https://bintray.com/dynatrace/dynahist/com.dynatrace.dynahist)
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

View the [documentation](https://github.com/dynatrace-oss/dynahist/tree/master/docs/description.md) to get further information about DynaHist.
Learn more about different layout implementations, different histogram implementations and histogram serialization. 

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
histogram.getValueEstimate(1); // returns an estimate of the 2nd smallest value
histogram.getQuantileEstimate(0.5); // returns an estimate of the median

// Merging histograms
histogram.addHistogram(otherHistogram);

// Serialization
histogram.write(dataOutput); // write histogram to a java.io.DataOutput
histogram.readAsDynamic(layout, dataInput); // read dynamic histogram from a java.io.DataInput
```

## Getting started

DynaHist is available as Maven package on [JCenter](https://bintray.com/dynatrace/dynahist) and should be used via Maven, Gradle or Ivy.
If automatic dependency management is not possible obtain the jar file from [GitHub Releases](https://github.com/dynatrace-oss/dynahist/releases).

## History
At [Dynatrace](https://www.dynatrace.com/) we were looking for a data sketch with a fast update time, which can also answer order statistics queries with error guarantees. As an example, such a data structure should be able to provide the 99-th percentile with a relative error of 1% maximum. Other data structures like [t-digest](https://github.com/tdunning/t-digest) do not have strict error limits. In our search, we finally came across [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram), a histogram implementation that intelligently selects bin boundaries so that 
the relative error is limited over a range of many orders of magnitude. The core of the HdrHistogram is a fast mapping of the values to bin indices by bit widdling, which reduces the recording time to less than 10 ns.

Although we loved this idea, this data structure did not quite meet our requirements for several reasons:
  * The original HdrHistogram was designed for recording integer values. Usually we are dealing with floating point values. The wrapper class for `double` values, which is delivered with HdrHistogram, introduces an indirection, which slows down the recording.
  * Another disadvantage is that HdrHistogram does not give you full control over the error specification. It is only possible to define the number of significant digits corresponding to relative errors of 10%, 1%, 0.1%, etc. It is not possible to select a relative error of 5%. You must fall back on 1%, which would unnecessarily increase the number of bins and waste memory space.
  * HdrHistogram has no support for negative values. You have to use two histograms, one for the positive and one for the negative value range. 
  * With HdrHistogram it is not possible to define the maximum error for values that are between 0 and the range where the relative error limit applies.
  * The mapping of values to bin indices is fast, but not optimal. The mapping used by HdrHistogram requires about 40% more bins than necessary to satisfy the specified relative error. In 2015 we have proposed a better and similarly fast mapping for HdrHistogram (see https://github.com/HdrHistogram/HdrHistogram/issues/54) with less than 10% space overhead. However, as this would have resulted in an incompatible change, the author of HdrHistogram decided not to pursue our idea any further.

Therefore, we decided to develop our own histogram data sketch which uses the proposed better mapping and which also solves all the mentioned issues. After many years of successful application and the emergence of an open source initiative at Dynatrace, we decided to publish this data structure as a separate library here on GitHub.

## License

[Apache Version 2.0](https://github.com/dynatrace-oss/dynahist/blob/master/LICENSE)

## Main Contributors
* Otmar Ertl
* Markus Remplbauer