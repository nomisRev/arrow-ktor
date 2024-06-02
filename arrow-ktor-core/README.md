# Module Arrow Ktor Core

Work In Progress (Including this README)

# Validation DSL

A small DSL between Ktor, and Arrow's Raise `accumulate` DSL.

1. `putOrRaise` brings `context(Raise<OutgoingContent>)` and `context(Raise<HttpStatusCode>)` into scope, such that you can use `Raise` to short-circuit from the Ktor DSL. 
2. `validate` is a small utility between `Raise<OutgoingContent>`, and `context(PipelineContext<ApplicationCall, Unit>)` to automatically send back a formatted error response based on the accumulated errors. 
3. pathOrRaise, etc. are utility functions all defined as `context(PipelineContext<ApplicationCall, Unit>) suspend fun Raise<String>.pathOrRaise(..)` with a bunch of variants for customisation.

```kotlin
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class Info(val email: String)
data class Person(
  val name: String,
  val age: Int,
  val info: Info
)

fun Routing.example(): Route =
  putOrRaise("/user/{name}") {
    val person = validate {
      val name by accumulating { pathOrRaise("name") }
      val age by accumulating { queryIntOrRaise("age") }
      val info by accumulating { receiveOrRaise<Info>() }
      Person(name, age, info)
    }
    call.respond(Created, person)
  }
```
