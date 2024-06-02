package io.github.nomisrev.arrow.core

import arrow.resilience.Schedule
import arrow.resilience.ScheduleStep
import io.github.nomisrev.arrow.core.HttpRequestSchedule.RetryEventData.Failure
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.events.EventDefinition
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.delay

/**
 * A plugin that enables the client to retry failed requests according to [arrow.resilience.Schedule].
 * The default retry policy is 3 retries with exponential jitter'ed delay.
 *
 * Typical usages which shows the default configuration:
 *
 * ```kotlin
 * // use predefined retry policies
 * install(HttpRequestSchedule) {
 *   fun <A> delay() = Schedule.exponential<A>(2.seconds).jittered()
 *
 *   repeat(delay<HttpResponse>.doWhile { request, duration -> request.status.value in 500..599 })
 *   retry(delay<Throwable>().and(Schedule.recurs(3)))
 * }
 *
 * // use custom policies
 * install(HttpRequestSchedule) {
 *   fun <A> delay() = Schedule.spaced<A>(3.seconds)
 *
 *   retry(delay<Throwable>().doWhile { exception, _ -> exception is NetworkError })
 *   repeat(delay<HttpRequest>().doWhile { request, _ -> !response.status.isSuccess() })
 *   modifyRequest { it.headers.append("X_RETRY_COUNT", retryCount.toString()) }
 * }
 * ```
 */
public class HttpRequestSchedule internal constructor(configuration: Configuration) {

  private val repeatSchedule: Schedule<HttpResponse, *> = configuration.repeatSchedule
  private val retrySchedule: Schedule<Throwable, *> = configuration.retrySchedule
  private val modifyRequest: suspend ModifyRequestContext.(HttpRequestBuilder) -> Unit = configuration.modifyRequest

  public data class ModifyRequestContext(val original: HttpRequestBuilder, val lastRetryEventData: RetryEventData) {
    public val request: HttpRequestBuilder = lastRetryEventData.request
    public val retryCount: Int = lastRetryEventData.retryCount

    public fun responseOrNull(): HttpResponse? = when (lastRetryEventData) {
      is Failure -> null
      is RetryEventData.HttpResponse -> lastRetryEventData.response
    }

    public fun exceptionOrNull(): Throwable? = when (lastRetryEventData) {
      is RetryEventData.HttpResponse -> null
      is Failure -> lastRetryEventData.exception
    }
  }

  public sealed interface RetryEventData {
    public val request: HttpRequestBuilder
    public val retryCount: Int

    public fun responseOrNull(): io.ktor.client.statement.HttpResponse? = when (this) {
      is Failure -> null
      is HttpResponse -> response
    }

    public fun exceptionOrNull(): Throwable? = when (this) {
      is HttpResponse -> null
      is Failure -> exception
    }

    public data class HttpResponse(
      public override val request: HttpRequestBuilder,
      public override val retryCount: Int,
      public val response: io.ktor.client.statement.HttpResponse
    ) : RetryEventData

    public data class Failure(
      public override val request: HttpRequestBuilder,
      public override val retryCount: Int,
      public val exception: Throwable
    ) : RetryEventData
  }

  public class RepeatEventData internal constructor(

  )

  /**
   * Contains [HttpRequestSchedule] configurations settings.
   */
  @KtorDsl
  public class Configuration {
    internal var repeatSchedule: Schedule<HttpResponse, *> = Schedule.recurs(0)
    internal var retrySchedule: Schedule<Throwable, *> = Schedule.recurs(0)
    internal var modifyRequest: suspend ModifyRequestContext.(HttpRequestBuilder) -> Unit = {}

    init {
      @Suppress("MagicNumber")
      repeatSchedule =
        Schedule.exponential<HttpResponse>(2.seconds).jittered()
          .doWhile { request, _ -> request.status.value in 500..599 }

      @Suppress("MagicNumber")
      retrySchedule =
        Schedule.exponential<Throwable>(2.seconds).jittered()
          .and(Schedule.recurs(3))
    }

    /**
     * Repeat the request according to the provided Schedule,
     * the output of the schedule is ignored.
     */
    public fun <A> repeat(schedule: Schedule<HttpResponse, A>) {
      repeatSchedule = schedule
    }

    public fun <A> retry(schedule: Schedule<Throwable, A>) {
      retrySchedule = schedule
    }

    public fun modifyRequest(block: suspend ModifyRequestContext.(HttpRequestBuilder) -> Unit) {
      modifyRequest = block
    }
  }

  @Suppress("TooGenericExceptionCaught")
  internal fun intercept(client: HttpClient) {
    client.plugin(HttpSend).intercept { request ->
      var retryCount = 0

      val modifyRequest: suspend ModifyRequestContext.(HttpRequestBuilder) -> Unit =
        request.attributes.getOrNull(ModifyRequestPerRequestAttributeKey) ?: modifyRequest

      var repeatStep: ScheduleStep<HttpResponse, *> =
        request.attributes.getOrNull(RepeatPerRequestAttributeKey)?.step ?: repeatSchedule.step

      var retryStep: ScheduleStep<Throwable, *> =
        request.attributes.getOrNull(RetryPerRequestAttributeKey)?.step ?: retrySchedule.step

      var call: HttpClientCall
      var lastRetryData: RetryEventData? = null

      while (true) {
        val subRequest = prepareRequest(request)

        val retryData = try {
          if (lastRetryData != null) {
            modifyRequest(ModifyRequestContext(request, lastRetryData), subRequest)
          }
          call = execute(subRequest)
          when (val decision = repeatStep(call.response)) {
            is Schedule.Decision.Continue -> {
              if (decision.delay != Duration.ZERO) delay(decision.delay)
              repeatStep = decision.step
            }

            is Schedule.Decision.Done -> break
          }
            RetryEventData.HttpResponse(subRequest, ++retryCount, call.response)
        } catch (cause: Throwable) {
          when (val decision = retryStep(cause)) {
            is Schedule.Decision.Continue -> {
              if (decision.delay != Duration.ZERO) delay(decision.delay)
              retryStep = decision.step
            }

            is Schedule.Decision.Done -> throw cause
          }
          Failure(subRequest, ++retryCount, cause)
        }

        lastRetryData = retryData
        client.monitor.raise(HttpRequestScheduleEvent, lastRetryData)
      }
      call
    }
  }

  private fun prepareRequest(request: HttpRequestBuilder): HttpRequestBuilder {
    val subRequest = HttpRequestBuilder().takeFrom(request)
    request.executionContext.invokeOnCompletion { cause ->
      val subRequestJob = subRequest.executionContext as CompletableJob
      if (cause == null) subRequestJob.complete()
      else subRequestJob.completeExceptionally(cause)
    }
    return subRequest
  }

  public companion object Plugin : HttpClientPlugin<Configuration, HttpRequestSchedule> {
    override val key: AttributeKey<HttpRequestSchedule> = AttributeKey("ScheduleFeature")

    /** Occurs on request retry. */
    public val HttpRequestScheduleEvent: EventDefinition<RetryEventData> = EventDefinition()

    override fun prepare(block: Configuration.() -> Unit): HttpRequestSchedule {
      val configuration = Configuration().apply(block)
      return HttpRequestSchedule(configuration)
    }

    override fun install(plugin: HttpRequestSchedule, scope: HttpClient) {
      plugin.intercept(scope)
    }
  }
}

/**
 * Configures the [HttpRequestSchedule] plugin on a per-request level.
 */
public fun HttpRequestBuilder.schedule(block: HttpRequestSchedule.Configuration.() -> Unit) {
  val configuration = HttpRequestSchedule.Configuration().apply(block)
  attributes.put(RepeatPerRequestAttributeKey, configuration.repeatSchedule)
  attributes.put(RetryPerRequestAttributeKey, configuration.retrySchedule)
  attributes.put(ModifyRequestPerRequestAttributeKey, configuration.modifyRequest)
}

@Suppress("PrivatePropertyName")
private val RepeatPerRequestAttributeKey = AttributeKey<Schedule<HttpResponse, *>>("RepeatPerRequestAttributeKey")

@Suppress("PrivatePropertyName")
private val RetryPerRequestAttributeKey = AttributeKey<Schedule<Throwable, *>>("RetryPerRequestAttributeKey")

@Suppress("PrivatePropertyName")
private val ModifyRequestPerRequestAttributeKey:
  AttributeKey<suspend HttpRequestSchedule.ModifyRequestContext.(HttpRequestBuilder) -> Unit> =
  AttributeKey("ModifyRequestPerRequestAttributeKey")