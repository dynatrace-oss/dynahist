## Layout

The Layout defines the bins for a histogram and maps a given value to a histogram bin index. The Layout package includes 
multiple implementations of the `AbstractErrorLimitingLayout` class:
- `ErrorLimitingLayout1`, ... TODO ...
- `ErrorLimitingLayout2`, ... TODO ...

Create a layout using `create(final double absoluteBinWidthLimit, final double relativeBinWidthLimit, final double valueRangeLowerBound, final double valueRangeUpperBound)`.
The layout is able to approximate any values within a given range with a maximum error that either satisfies the given absolute or the given relative error bounds.

``` java
Layout layout = LayoutApproxOrder1.create(1e-5, 1e-2, -1e6, 1e6);
```

``` java
Layout layout = LayoutApproxOrder2.create(1e-5, 1e-2, -1e6, 1e6);
```

Create your own histogram Layout using the `CustomLayout` class. Define your own bin boundaries using `create(double... sortedBinBoundaries)`.

``` java
 Layout layout = CustomLayout.create(0, 1, 10, 100, 1000, 10000, 1000000);
````  

## Histogram

The Histogram is a fast, memory efficient, merge- and serializable histogram implementation with configurable bin layout.
The DynaHist Package includes multiple implementations of the `AbstractHistogram` class:
- `StaticHistogram`, ... TODO ...
- `DynamicHistogram`, ... TODO ...
- `PreprocessedHistogram`, a immutable preprocessed Histogram that allows fast order statistic queries.

Create a Histogram using `createDynamic(Layout layout)` or `createStatic(Layout layout)`.

``` java 
Histogram histogram = Histogram.createDynamic(layout);
```

``` java 
Histogram histogram = Histogram.createStatic(layout);
```

Add values to the histogram using `addValue(double value)`, `addValue(double value, long count)`or `addAscendingSequence(LongToDoubleFunction ascendingSequence, long length)`.

``` java
histogram.addValue(-5.5);
```

``` java
histogram.addValue(-4.3, 6);
```

``` java
histogram.addAscendingSequence(i -> i + 1, 1000000000); // adds the first billion positive integers
```

Merge histograms using `addHistogram(Histogram histogram)`. If the given histograms have a different layout, this operation may lead to an unwanted loss of precision.

``` java
Histogram otherHistogram = Histogram.createDynamic(layout);

histogram.addHistogram(otherHistogram);
```

