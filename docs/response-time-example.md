## DynaHist Response Time Example

This example shows how to use DynaHist for recording response times. 
Using DynaHist with absolute error set to `1e-5` and a relative error set to 
`1e-2` either the absolute error bound `1e-5` or the relative error bound `1e-2` (1%) is satisfied for every recorded 
 value.
 
 
By setting the min value to `0` and the max value to `1e6` response times from `1ms` to `1000s` can 
be recorded and analyzed with value precision.

``` java
Layout layout = ErrorLimitingLayout2.create(1e-5, 1e-2, 0, 1e6); 
Histogram histogram = Histogram.createDynamic(layout); 
```

After value recording the histogram can be mapped to a new `Histogram` with a `CustomLayout` and 
a small amount of bins. For instance, by creating a `CustomLayout` using `CustomLayout.create(0, 1, 10, 100, 1000, 10000, 1000000)`
the `Histogram` can be mapped to a small amount of bins. The values can be mapped to the new `Histogram` using `addHistogram(Histogram histogram)`. 

```` java
Layout resultLayout = CustomLayout.create(0, 1, 10, 100, 1000, 10000, 1000000);
Histogram resultHistogram = Histogram.createDynamic(resultLayout);

resultHistogram.addHistogram(histogram);
````

View the DynaHist Response Time Example [source code](https://github.com/dynatrace-oss/dynahist/tree/master/src/test/java/com/dynatrace/dynahist/examples/ResponseTimeExample.java).

