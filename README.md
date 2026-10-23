# Coursier

[![Build status](https://github.com/coursier/coursier/workflows/CI/badge.svg)](https://github.com/coursier/coursier/actions?query=workflow%3ACI)
[![Join the chat at https://gitter.im/coursier/coursier](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/coursier/coursier?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/io.get-coursier/coursier_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/io.get-coursier/coursier_2.12)
[![javadoc](https://javadoc.io/badge2/io.get-coursier/coursier_2.13/javadoc.svg)](https://javadoc.io/doc/io.get-coursier/coursier_2.13)

## Documentation

Coursier is the Scala application and artifact manager. It can install Scala applications and setup your Scala development environment. It can also download and cache artifacts from the web.

See the [website](https://get-coursier.io) for more details.

## Concurrent cache downloads

Downloads into the same cache file share one owner, including when callers use different
paths to the same cache location. Independent `FileCache` locations can download the same
URL concurrently; one location's pending response or failure does not hold up the other.

`FileCache.withBufferSize` sets the maximum download buffer size. Known uncompressed
response bodies use a buffer no larger than their response length, with a one-byte
minimum for empty bodies. Resumed downloads use the remaining response length;
unknown-length and encoded bodies retain the configured size.

Uncompressed downloads with a declared length stop at that length and fail if the
connection ends early. The error includes the artifact URL and expected and received
byte counts; rejected downloads remove their temporary files and never enter the cache.
Malformed lengths are rejected. Chunked transfer framing takes precedence over a
Content-Length header, and close-delimited responses are read until EOF. Chunked downloads
validate the closing chunk even when a range count or a decoded body has already completed.
Gzip and deflate responses are decoded with case-insensitive coding names; their declared
lengths are checked against response bytes before decoding. Unsupported content codings and
HTTP statuses without an artifact body are rejected. Repeated Content-Encoding fields are
combined in response order. Every gzip member and every encoding layer must finish and
pass its integrity checks before the cache file is published; incomplete later members,
unread outer trailers and extra encoded data are rejected. Decoded download progress is
reported as bytes arrive, including fragments within a gzip member.

A resumed download appends only a valid 206 response whose Content-Range starts at
the saved prefix, ends at the declared total, and agrees with Content-Length when
present. Invalid or missing ranges fail without restarting the request. A full 200
response replaces the prefix. Encoded partial responses cannot append to a saved decoded
prefix. Range-unit names are case-insensitive. Explicit redirects continue to use the final response's
length and framing.

Explicit `FileCache` retry and backoff settings apply to its downloads, including
waiting for another caller to finish the same cache file. These instance settings
take precedence over the JVM retry properties.

## Code of Conduct

The coursier project welcomes contributions from anybody wishing to participate.
All code or documentation that is provided must be licensed with the same
license that coursier is licensed with (Apache 2.0, see LICENSE).

People are expected to follow the [Scala Code of Conduct](https://www.scala-lang.org/conduct)
when discussing coursier on GitHub, Gitter channel, or other venues.

Feel free to open an issue if you notice a bug, have an idea for a feature, or have a question about the code. Pull requests are also gladly accepted.

## Acknowledgments

<img src="https://scala.epfl.ch/resources/img/scala-center-swirl.png" width="40px" />

Large parts of the developments in coursier since Sep. 2018 have been funded by the [Scala Center](https://scala.epfl.ch), through the employment or contracting of myself (Alexandre Archambault).
