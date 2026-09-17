# Download buffer CPU evidence

Public FileCache fetches from a real loopback HTTP server, C1 (-XX:TieredStopAtLevel=1), taskset -c 0,1. 16 warmup pairs then 64 one-byte responses for each buffer size. All returned bytes asserted. Baseline fails twice, fixed code passes; buffer size stays 1 MiB in production.

## baseline

```text
[306] DOWNLOAD_BUFFER_CPU smallNs=590000000 largeNs=2770000000 ratio=4.694915254237288
[306] Tests: 1, Passed: 0, Failed: 1
wall=282.481 user=159.573 system=13.132
```

## baseline2

```text
[306] DOWNLOAD_BUFFER_CPU smallNs=640000000 largeNs=2780000000 ratio=4.34375
[306] Tests: 1, Passed: 0, Failed: 1
wall=55.010 user=19.783 system=2.605
```

## fixed

```text
[306] DOWNLOAD_BUFFER_CPU smallNs=510000000 largeNs=520000000 ratio=1.0196078431372548
[306] Tests: 1, Passed: 1, Failed: 0
wall=86.740 user=32.135 system=3.416
```

