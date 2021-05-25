# Copyright 2020-2021 Dynatrace LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import matplotlib
import matplotlib.pyplot as plt
import os
import numpy             as np

matplotlib.rcParams['svg.hashsalt'] = 0
matplotlib.rcParams['figure.autolayout'] = True
font = {'size': 8}
plt.rc('font', **font)
np.random.seed(0)


config = [
  ("DynaHist (static, log-linear)", "DynaHistStaticLogLinear"),
  ("DynaHist (dynamic, log-linear)", "DynaHistDynamicLogLinear"),
  ("DynaHist (static, log-quadratic)", "DynaHistStaticLogQuadratic"),
  ("DynaHist (dynamic, log-quadratic)", "DynaHistDynamicLogQuadratic"),
  ("DynaHist (static, log-optimal)", "DynaHistStaticLogOptimal"),
  ("DynaHist (dynamic, log-optimal)", "DynaHistDynamicLogOptimal"),
  ("DynaHist (static, otel-exp-buckets)", "DynaHistStaticOpenTelemetryExponentialBuckets"),
  ("DynaHist (dynamic, otel-exp-buckets)", "DynaHistDynamicOpenTelemetryExponentialBuckets"),
  ("HdrHistogram.DoubleHistogram", "HdrDoubleHistogram")
  # ("DDSketch (paginated, log)", "DDSketchPaginatedLogarithmic"),
  # ("DDSketch (paginated, cubic)", "DDSketchPaginatedCubic"),
  # ("DDSketch (paginated, quadratic)", "DDSketchPaginatedQuadratic"),
  # ("DDSketch (paginated, linear)", "DDSketchPaginatedLinear"),
  # ("DDSketch (unbounded-dense, log)", "DDSketchUnboundedSizeDenseLogarithmic"),
  # ("DDSketch (unbounded-dense, cubic)", "DDSketchUnboundedSizeDenseCubic"),
  # ("DDSketch (unbounded-dense, quadratic)", "DDSketchUnboundedSizeDenseQuadratic"),
  # ("DDSketch (unbounded-dense, linear)", "DDSketchUnboundedSizeDenseLinear"),
  # ("DDSketch (sparse, log)", "DDSketchSparseLogarithmic"),
  # ("DDSketch (sparse, cubic)", "DDSketchSparseCubic"), 
  # ("DDSketch (sparse, quadratic)", "DDSketchSparseQuadratic"),
  # ("DDSketch (sparse, linear)", "DDSketchSparseLinear")
]

config.reverse()

def get_index_for_space_consumption_benchmark(label):
  for i in range(0, len(config)):
    if config[i][0] == label:
      return i
  return None

def get_index_for_recording_speed_benchmark(label):
  for i in range(0, len(config)):
    if label == "RecordingSpeedBenchmark.record" + config[i][1]:
      return i
  return None

def create_performance_chart(title, filename, config, values, width, xlabel):
  fig, ax = plt.subplots(figsize=(5, 3))

  for i in range(0, len(values)):
    ax.barh(config[i][0], values[i], width)

  max_value = max(values)*1.2
  ax.set_xlim(0, max_value)
  
  for i, v in enumerate(values):
    ax.text(v + max_value*0.005, i, "{:#.3g}".format(v), ha='left', va='center', color='black')

  ax.set_xlabel(xlabel)
  ax.set_title(title)
  plt.savefig(os.path.join('docs/figures/' + filename + '.svg'), metadata={'creationDate': None}, dpi=50)
  plt.savefig(os.path.join('docs/figures/' + filename + '.png'), metadata={'creationDate': None}, dpi=300)


def create_memory_chart(title, filename, config, sizes, values, width, xlabel):
  fig, ax = plt.subplots(figsize=(5, 3))

  # values2 = [v[-1]/1024. for v in values]

  # for i in range(0, len(values2)):
  #   ax.barh(config[i][0], values2[i], width)

  # max_value = max(values2)*1.2
  # ax.set_xlim(0, max_value)
  
  # for i, v in enumerate(values2):
  #   ax.text(v + max_value*0.005, i, "{:#.3g}".format(v), ha='left', va='center', color='black')

  ax.set_xscale("log")
  ax.set_xlim([1,1e6])
  ax.set_xlabel("number of values")
  ax.set_ylabel(xlabel)
  for i in range(0, len(values)):
    ax.plot(sizes, [v/1024. for v in values[i]], label =config[i][0])



  # ax.set_xlabel(xlabel)
  # ax.set_title(title)
  ax.legend()
  plt.savefig(os.path.join('docs/figures/' + filename + '.svg'), metadata={'creationDate': None}, dpi=50)
  plt.savefig(os.path.join('docs/figures/' + filename + '.png'), metadata={'creationDate': None}, dpi=300)


# memory footprint

f = open('charts/memory-footprint-jol.txt', 'r')
data = f.read().split('\n')
data.pop()
f.close()

sizes = [float(x) for x in data[0].split(';')[1:]]
values = [""] * len(config)
for x in data[1:]:
  line = x.split(';')
  idx = get_index_for_space_consumption_benchmark(line[0])
  if idx is not None:
    values[idx] = [float(x) for x in line[1:]]

create_memory_chart('Memory Footprint', "memory-footprint", config, sizes, values, 0.5, 'size (kB)')

# compressed serialization size

f = open('charts/serialization-size-compressed.txt', 'r')
data = f.read().split('\n')
data.pop()
f.close()

sizes = [float(x) for x in data[0].split(';')[1:]]
values = [""] * len(config)
for x in data[1:]:
  line = x.split(';')
  idx = get_index_for_space_consumption_benchmark(line[0])
  if idx is not None:
    values[idx] = [float(x) for x in line[1:]]

create_memory_chart('Compressed Serialization', "serialization-size-compressed", config, sizes, values, 0.5, 'size (kB)')

# raw serialization size

f = open('charts/serialization-size-raw.txt', 'r')
data = f.read().split('\n')
data.pop()
f.close()

sizes = [float(x) for x in data[0].split(';')[1:]]
values = [""] * len(config)
for x in data[1:]:
  line = x.split(';')
  idx = get_index_for_space_consumption_benchmark(line[0])
  if idx is not None:
    values[idx] = [float(x) for x in line[1:]]

create_memory_chart('Raw Serialization', "serialization-size-raw", config, sizes, values, 0.5, 'size (kB)')

# recording speed

f = open('charts/recording-speed.txt', 'r')
f.readline()
data = f.read().split('\n')
data.pop()
f.close()

values = [""] * len(config)

for x in data:
  line = x.split()
  idx = get_index_for_recording_speed_benchmark(line[0])
  if idx is not None:
    values[idx] = float(line[3])

create_performance_chart('Recording Speed', "recording-speed", config, values, 0.5, 'time per value insertion (ns)')