/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.scaladsl.model

import language.implicitConversions
import akka.http.impl.util._
import java.util.Optional
import akka.http.javadsl.{ model => jm }
import akka.http.impl.util.JavaMapping.Implicits._

final case class ContentTypeRange(mediaRange: MediaRange, charsetRange: HttpCharsetRange) extends jm.ContentTypeRange with ValueRenderable {
  def matches(contentType: jm.ContentType) =
    convertToScala(contentType) match {
      case ContentType.Binary(mt)             => mediaRange.matches(mt)
      case ContentType.WithMissingCharset(mt) => mediaRange.matches(mt)
      case x: ContentType.NonBinary           => mediaRange.matches(x.mediaType) && charsetRange.matches(x.charset)
    }

  def render[R <: Rendering](r: R): r.type = charsetRange match {
    case HttpCharsetRange.`*` => r ~~ mediaRange
    case x                    => r ~~ mediaRange ~~ ContentType.`; charset=` ~~ x
  }
}

object ContentTypeRange {
  val `*` = ContentTypeRange(MediaRanges.`*/*`)

  implicit def apply(mediaType: MediaType): ContentTypeRange = apply(mediaType, HttpCharsetRange.`*`)
  implicit def apply(mediaRange: MediaRange): ContentTypeRange = apply(mediaRange, HttpCharsetRange.`*`)
  implicit def apply(contentType: ContentType): ContentTypeRange =
    contentType match {
      case ContentType.Binary(mt)             => ContentTypeRange(mt)
      case ContentType.WithFixedCharset(mt)   => ContentTypeRange(mt)
      case ContentType.WithCharset(mt, cs)    => ContentTypeRange(mt, cs)
      case ContentType.WithMissingCharset(mt) => ContentTypeRange(mt)
    }
}

/**
 * A `ContentType` represents a specific MediaType / HttpCharset combination.
 *
 * If the MediaType is not flexible with regard to the charset used, e.g. because it's a binary MediaType or
 * the charset is fixed, then the `ContentType` is a simple wrapper.
 */
sealed trait ContentType extends jm.ContentType with ValueRenderable {
  def mediaType: MediaType
  def charsetOption: Option[HttpCharset]

  private[this] var _value: String = _
  override def value: String = {
    if (_value eq null)
      _value = super.value
    _value
  }

  private[http] def render[R <: Rendering](r: R): r.type = r ~~ mediaType

  /** Java API */
  def getCharsetOption: Optional[jm.HttpCharset] = charsetOption.asJava
}

object ContentType {
  /** Represents a content-type which we know not to contain text (will never have have a charset) */
  final case class Binary(mediaType: MediaType.Binary) extends jm.ContentType.Binary with ContentType {
    def binary = true
    def charsetOption = None
  }

  /** Represents a content-type which we know to contain text, and has a specified charset. */
  sealed trait NonBinary extends jm.ContentType.NonBinary with ContentType {
    def binary = false
    def charset: HttpCharset
    def charsetOption = Some(charset)
  }

  /** Represents a content-type which we know to contain text, where the charset always has the same predefined value. */
  final case class WithFixedCharset(mediaType: MediaType.WithFixedCharset)
    extends jm.ContentType.WithFixedCharset with NonBinary {
    def charset = mediaType.charset
  }

  /** Represents a content-type which we know to contain text, and the charset is known at runtime. */
  final case class WithCharset(mediaType: MediaType.WithOpenCharset, charset: HttpCharset)
    extends jm.ContentType.WithCharset with NonBinary {

    private[http] override def render[R <: Rendering](r: R): r.type =
      super.render(r) ~~ ContentType.`; charset=` ~~ charset
  }

  /**
   * Represents a content-type which we know to contain text, and would be better off having a charset,
   * but the client or server hasn't provided that. For example, "text/xml" without a charset parameter.
   */
  final case class WithMissingCharset(mediaType: MediaType.WithOpenCharset)
    extends jm.ContentType.WithMissingCharset with ContentType {
    def binary = false
    def charsetOption = None
  }

  implicit def apply(mediaType: MediaType.Binary): Binary = Binary(mediaType)
  implicit def apply(mediaType: MediaType.WithFixedCharset): WithFixedCharset = WithFixedCharset(mediaType)
  def apply(mediaType: MediaType.WithOpenCharset, charset: HttpCharset): WithCharset = WithCharset(mediaType, charset)
  def apply(mediaType: MediaType, charset: () => HttpCharset): ContentType =
    mediaType match {
      case x: MediaType.Binary           => ContentType(x)
      case x: MediaType.WithFixedCharset => ContentType(x)
      case x: MediaType.WithOpenCharset  => ContentType(x, charset())
    }

  def unapply(contentType: ContentType): Option[(MediaType, Option[HttpCharset])] =
    Some(contentType.mediaType -> contentType.charsetOption)

  /**
   * Tries to parse a `ContentType` value from the given String. Returns `Right(contentType)` if successful and
   * `Left(errors)` otherwise.
   */
  def parse(value: String): Either[List[ErrorInfo], ContentType] =
    headers.`Content-Type`.parseFromValueString(value).right.map(_.contentType)

  private[http] case object `; charset=` extends SingletonValueRenderable
}

object ContentTypes {
  val `application/json` = ContentType(MediaTypes.`application/json`)
  val `application/octet-stream` = ContentType(MediaTypes.`application/octet-stream`)
  val `application/x-www-form-urlencoded` = ContentType(MediaTypes.`application/x-www-form-urlencoded`)
  val `text/plain(UTF-8)` = MediaTypes.`text/plain` withCharset HttpCharsets.`UTF-8`
  val `text/html(UTF-8)` = MediaTypes.`text/html` withCharset HttpCharsets.`UTF-8`
  val `text/xml(UTF-8)` = MediaTypes.`text/xml` withCharset HttpCharsets.`UTF-8`
  val `text/csv(UTF-8)` = MediaTypes.`text/csv` withCharset HttpCharsets.`UTF-8`

  val `application/grpc+proto` = ContentType(MediaTypes.`application/grpc+proto`)

  // used for explicitly suppressing the rendering of Content-Type headers on requests and responses
  val NoContentType = ContentType(MediaTypes.NoMediaType)
}
