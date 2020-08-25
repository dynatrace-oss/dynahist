# DynaHist: A Dynamic Histogram Library for Java

[![CircleCI](https://circleci.com/gh/dynatrace-oss/dynahist/tree/master.svg?style=svg)](https://circleci.com/gh/dynatrace-oss/dynahist/tree/master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dynatrace-oss_dynahist&metric=alert_status)](https://sonarcloud.io/dashboard?id=dynatrace-oss_dynahist)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dynatrace-oss_dynahist&metric=coverage)](https://sonarcloud.io/dashboard?id=dynatrace-oss_dynahist)
[![Download](https://api.bintray.com/packages/dynatrace/dynahist/com.dynatrace.dynahist/images/download.svg?)](https://bintray.com/dynatrace/dynahist/com.dynatrace.dynahist)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


This Java library contains histogram implementations with configurable bin layouts specifically designed for fast value recording. The basis are three different implementations:
* The static histogram enables an allocation-free recording of values, as the internal bin count array is already fully occupied during construction.
* The dynamic histogram is memory-efficient as it resizes the internal bin count array on demand. Furthermore, it dynamically adjusts the number of bits used for each counter.
* The preprocessed histogram is an immutable implementation which contains the cumulative bin counts. In this way sublinear queries for order statistics are possible through binary search. If many of those queries are performed subsequently, it is recommended to convert to a preprocessed histogram first.

The library ships with predefined bin layout implementations that allow values to be quickly mapped to bin indexes while preserving the recorded value with defined precision. The layouts can be configured using an absolute and a relative error limit, at least one of which must be satisfied for each recorded value within the specified value range.
* `ErrorLimitingLayout1` uses a piecewise linear approximation to the optimal mapping. It is the fastest mapping, but in comparison to the optimal mapping, it needs up to 40% more bins and therefore correspondingly more space.
* `ErrorLimitingLayout2` uses a piecewise quadratic approximation to the optimal mapping. It is the slightly slower than `ErrorLimitingLayout1`, but reduces the space overhead to less than 10% compared to the ideal mapping.

In addition there is also the `CustomLayout` which allows to set the bin boundaries individually. It can be used to map a histogram with a fine-grained layout to a coarse custom bin layout, for example, as a preparatory step for creating a visualization. 

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

## Getting Started

DynaHist is available as Maven package on [JCenter](https://bintray.com/dynatrace/dynahist) and should be used via Maven, Gradle or Ivy.
If automatic dependency management is not possible obtain the jar file from [GitHub Releases](https://github.com/dynatrace-oss/dynahist/releases).

## History
At [Dynatrace](https://www.dynatrace.com/) we were looking for a data sketch with a fast update time, which can also answer order statistic queries with error guarantees. As an example, such a data structure should be able to provide the 99th percentile with a maximum relative error of 1%. Other data structures like [t-digest](https://github.com/tdunning/t-digest) do not have strict error limits. In our search, we finally came across [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram), a histogram implementation that intelligently selects bin boundaries so that 
the relative error is limited over a range of many orders of magnitude. The core of HdrHistogram is a fast mapping of values to bin indices by bit twiddling, which reduces the recording time to less than 10ns. Although we loved this idea, this data structure did not quite meet our requirements for several reasons:
  * The original HdrHistogram was designed for recording integer values. Usually we are dealing with floating point values. The wrapper class for `double` values, which is shipped with HdrHistogram, introduces an indirection, which slows down the recording.
  * Another disadvantage is that HdrHistogram does not give you full control over the error specification. It is only possible to define the number of significant digits corresponding to relative errors of 10%, 1%, 0.1%, etc. It is not possible to select a relative error of 5%. You must fall back on 1%, which unnecessarily increases the number of bins and wastes memory space.
  * HdrHistogram has no support for negative values. You have to use two histograms, one for the positive and one for the negative value range. 
  * With HdrHistogram it is not possible to define the maximum error for values that are between zero and the range where the relative error limit applies.
  * The mapping of values to bin indices is fast, but not optimal. The mapping used by HdrHistogram requires about 40% more bins than necessary to satisfy the specified relative error. In 2015 we have proposed a better and similarly fast mapping for HdrHistogram (see https://github.com/HdrHistogram/HdrHistogram/issues/54) with less than 10% space overhead. However, as this would have resulted in an incompatible change, the author of HdrHistogram decided not to pursue our idea any further.

Therefore, we started developing our own histogram data sketch which uses the proposed better mapping and which also solves all the mentioned issues. After many years of successful application and the emergence of an open source initiative at Dynatrace, we decided to publish this data structure as a separate library here on GitHub.

## Benchmarks
For our benchmarks we used random values drawn from a [reciprocal distribution](https://en.wikipedia.org/wiki/Reciprocal_distribution) (log-uniform distribution) with a minimum value of 1000 and a maximum value of 1e12. In order not to distort the test results, we have generated 1M random numbers in advance and kept them in main memory. For the comparison with HdrHistogram we used the `DoubleHistogram` with `highestToLowestValueRatio=1e9` and `numberOfSignificantValueDigits=2`. To record values with equivalent precision we used an absolute error of 10 and a relative error of 1% over the range [0, 1e12]. The corresponding layouts `ErrorLimitingLayout1(10, 0.01, 0, 1e12)` and `ErrorLimitingLayout2(10, 0.01, 0, 1e12)` have been combined with the static and dynamic implementations of DynaHist resulting in 4 different cases.

The recording speed was measured using [JMH](https://openjdk.java.net/projects/code-tools/jmh/) on a Dell Precision 5530 Notebook with an Intel Core i9-8950HK CPU. We measured the average time to insert the 1M random values into an empty histogram data structure, from which we derived the average time for recording a single value. All four investigated DynaHist variants outperform HdrHistogram's DoubleHistogram significantly. The static histogram implementation with the  `ErrorLimitingLayout1` was the fastest one and more than 40% faster than HdrHistogram.

![Recording Speed](docs/figures/recording-speed.svg)

The memory usage of the histogram data structures was analyzed after adding 1M random values as in the speed benchmark before. Again due to the better bin layout DynaHist significantly outperforms HdrHistogram. Especially the dynamic histogram implementation together with `ErrorLimitingLayout2` requires just 15% of the memory space HdrHistogram takes.

![Memory Footprint](docs/figures/memory-footprint.svg)

Similarly, the serialization, which is more or less a memory snapshot of the dynamic histogram implementation, is much more compact than that of HdrHistogram.

![Raw Serialization](docs/figures/serialization-size-raw.svg)

The space advantage is maintained even with compression. The reason is that DynaHist requires much fewer bins to guarantee the same relative error and therefore less information has to be stored.

![Compressed Serialization](docs/figures/serialization-size-compressed.svg)

## License

[Apache Version 2.0](https://github.com/dynatrace-oss/dynahist/blob/master/LICENSE)

## Main Contributors
* [Otmar Ertl](https://github.com/oertl)
* [Markus Remplbauer](https://github.com/markusremplbauer)
