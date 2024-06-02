package io.github.nomisrev.arrow.ktor.core

import arrow.core.raise.Raise
import arrow.core.raise.recover
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

fun Routing.getOrRaise(
  path: String,
  body: suspend context(Raise<HttpStatusCode>, Raise<OutgoingContent>) PipelineContext<Unit, ApplicationCall>.() -> Unit
): Route =
  get(path) get@ {
    recover(statusCode@ {
      recover(content@ {
        body(this@statusCode, this@content, this@get)
      }) { content: OutgoingContent -> call.respond(content) }
    }) { statusCode: HttpStatusCode -> call.respond(statusCode, EmptyContent) }
  }

fun Routing.getOrRaise(
  path: Regex,
  body: suspend context(Raise<HttpStatusCode>, Raise<OutgoingContent>) PipelineContext<Unit, ApplicationCall>.() -> Unit
): Route =
  get(path) get@ {
    recover(statusCode@ {
      recover(content@ {
        body(this@statusCode, this@content, this@get)
      }) { content: OutgoingContent -> call.respond(content) }
    }) { statusCode: HttpStatusCode -> call.respond(statusCode, EmptyContent) }
  }

fun Routing.putOrRaise(
  path: String,
  body: suspend context(Raise<HttpStatusCode>, Raise<OutgoingContent>) PipelineContext<Unit, ApplicationCall>.() -> Unit
): Route =
  put(path) get@ {
    recover(statusCode@ {
      recover(content@ {
        body(this@statusCode, this@content, this@get)
      }) { content: OutgoingContent -> call.respond(content) }
    }) { statusCode: HttpStatusCode -> call.respond(statusCode, EmptyContent) }
  }

fun Routing.putOrRaise(
  path: Regex,
  body: suspend context(Raise<HttpStatusCode>, Raise<OutgoingContent>) PipelineContext<Unit, ApplicationCall>.() -> Unit
): Route =
  put(path) get@ {
    recover(statusCode@ {
      recover(content@ {
        body(this@statusCode, this@content, this@get)
      }) { content: OutgoingContent -> call.respond(content) }
    }) { statusCode: HttpStatusCode -> call.respond(statusCode, EmptyContent) }
  }


data object EmptyContent : OutgoingContent.NoContent() {
  override val contentLength: Long = 0

  override fun toString(): String = "EmptyContent"
}
