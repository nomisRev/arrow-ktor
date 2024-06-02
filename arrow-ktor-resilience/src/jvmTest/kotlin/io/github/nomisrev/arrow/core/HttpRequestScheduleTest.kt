package io.github.nomisrev.arrow.core

import arrow.resilience.Schedule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.isSuccess
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.assertThrows

class HttpRequestScheduleTest : StringSpec({

  "recurs" {
    checkAll(Arb.long(0, 19)) { l ->
      testApplication {
        var counter = 0
        routing {
          get("/") {
            counter++
            call.respond(OK)
          }
        }

        val client = createClient {
          install(HttpRequestSchedule) {
            repeat(Schedule.recurs(l))
          }
        }

        val response = client.get("/")

        counter shouldBe l + 1
        response.status shouldBe OK
      }
    }
  }

  "doWhile" {
    checkAll(Arb.long(0, 19)) { l ->
      testApplication {
        var counter = 0
        routing {
          get("/") {
            counter++
            if (counter <= l) call.respond(NotFound)
            else call.respond(OK)
          }
        }

        val client = createClient {
          install(HttpRequestSchedule) {
            repeat(Schedule.doWhile { request, _ -> !request.status.isSuccess() })
          }
        }

        val response = client.get("/")

        counter shouldBe l + 1
        response.status shouldBe OK
      }
    }
  }

  class NetworkError : Throwable()

  "retry" {
    checkAll(Arb.long(0, 19)) { l ->
      testApplication {
        var counter = 0
        routing {
          get("/") {
            counter++
            if (counter <= l) throw NetworkError()
            else call.respond(OK)
          }
        }

        val client = createClient {
          install(HttpRequestSchedule) {
            retry(Schedule.doWhile { throwable, _ -> throwable is NetworkError })
          }
        }

        val response = client.get("/")

        counter shouldBe l + 1
        response.status shouldBe OK
      }
    }
  }

  "schedule" {
    checkAll(Arb.long(1, 19)) { l ->
      testApplication {
        var counter = 0
        routing {
          get("/") {
            counter++
            if (counter <= l) throw NetworkError()
            else call.respond(OK)
          }
        }

        val client = createClient {
          install(HttpRequestSchedule) {
            retry(Schedule.recurs(0))
          }
        }

        assertThrows<NetworkError> { client.get("/") }

        val response = client.get("/") {
          schedule {
            retry(Schedule.doWhile { throwable, _ -> throwable is NetworkError })
          }
        }

        counter shouldBe l + 1
        response.status shouldBe OK
      }
    }
  }
})

