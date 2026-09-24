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

## second host, threshold tightened from 4x to 2x (2026-09-24)

On a 16-core arm64 host (same `taskset -c 0,1`, C1 child JVM) the original allocation measured only 3.19x and 3.16x, so the earlier `large < small * 4` assertion passed with the defect present. The assertion is now `large < small * 2`.

```text
fix reverted  ratio=3.1904761904761907  Failed (2x threshold)
fix reverted  ratio=3.1578947368421053  Failed (2x threshold)
fixed         ratio=0.7368421052631579  Passed
fixed         ratio=0.8823529411764706  Passed
fixed         ratio=1.0                 Passed
```
