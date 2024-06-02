package io.github.nomisrev.arrow.ktor.core

import arrow.core.NonEmptyList
import io.ktor.http.*
import io.ktor.http.content.*

public class ValidatedContent(
  public val text: String,
  public override val status: HttpStatusCode
) : OutgoingContent.ByteArrayContent() {
  override val contentType: ContentType = ContentType.Text.Plain
  private val bytes = text.toByteArray(contentType.charset() ?: Charsets.UTF_8)

  override val contentLength: Long
    get() = bytes.size.toLong()

  override fun bytes(): ByteArray = bytes
  override fun toString(): String = "ValidatedContent[$contentType] \"${text.take(30)}\""
}

data class ValidationContent(val content: NonEmptyList<TextContent>) : OutgoingContent.ByteArrayContent() {
  override val contentType: ContentType = ContentType.Text.Plain
  private val bytes =
    content.joinToString { it.text }.toByteArray(contentType.charset() ?: Charsets.UTF_8)

  override val contentLength: Long
    get() = bytes.size.toLong()

  override fun bytes(): ByteArray = bytes
  override fun toString(): String = "ValidatedContent[$contentType]"
}
