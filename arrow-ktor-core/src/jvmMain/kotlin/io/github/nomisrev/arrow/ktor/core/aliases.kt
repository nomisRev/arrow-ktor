package io.github.nomisrev.arrow.ktor.core

import io.ktor.server.application.*
import io.ktor.util.pipeline.*

typealias RouteCtx =
  PipelineContext<Unit, ApplicationCall>
