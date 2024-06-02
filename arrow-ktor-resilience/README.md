# Module Arrow Resilience Ktor

[![Maven Central](https://img.shields.io/maven-central/v/io.github.nomisrev/arrow-resilience-ktor?color=4caf50&label=latest%20release)](https://maven-badges.herokuapp.com/maven-central/io.github.nomisrev/arrow-ktor-core)
[![Latest snapshot](https://img.shields.io/badge/dynamic/xml?color=orange&label=latest%20snapshot&prefix=v&query=%2F%2Fmetadata%2Fversioning%2Flatest&url=https%3A%2F%2Fs01.oss.sonatype.org%2Fservice%2Flocal%2Frepositories%2Fsnapshots%2Fcontent%2Fio%2Fgithub%2Fnomisrev%2Farrow-ktor-core%2Fmaven-metadata.xml)](https://s01.oss.sonatype.org/service/local/repositories/snapshots/content/io/github/nomisrev)

This module exposes two Ktor plugins for easily working with Arrow Resilience.
It allows you to `install` `Schedule` and `CircuitBreaker` into an `HttpClient`, and thus easily allows you to make your `HttpClient` more resilient.

## HttpRequestSchedule

A plugin that enables the client to retry failed requests according to [arrow.resilience.Schedule](https://apidocs.arrow-kt.io/arrow-resilience/arrow.resilience/-schedule/index.html?query=value%20class%20Schedule%3Cin%20Input,%20out%20Output%3E(val%20step:%20ScheduleStep%3CInput,%20Output%3E)).
The default retry policy is 3 retries with exponential jitter'ed delay.

Typical usages which shows the default configuration:

```kotlin
install(HttpRequestSchedule) {
  fun <A> delay() = Schedule.exponential<A>(2.seconds).jittered()

  repeat(delay<HttpResponse>.doWhile { request, duration -> request.status.value in 500..599 })
  retry(delay<Throwable>().and(Schedule.recurs(3)))
}
```

## HttpCircuitBreaker

A plugin that enables the client to work through a [CircuitBreaker](https://apidocs.arrow-kt.io/arrow-resilience/arrow.resilience/-circuit-breaker/index.html?query=class%20CircuitBreaker),
when the remote service gets overloaded `CircuitBreaker` will _open_ and now allow any traffic to pass through.

Typical usages which shows the default configuration:

```kotlin
install(HttpCircuitBreaker) {
  circuitBreaker(
    resetTimeout = 5.seconds,
    windowDuration = 5.seconds,
    maxFailures = 10
  )
}
```
