package com.improving.app.organization.utils.serialize

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
import com.improving.app.common.domain.{CaPostalCodeImpl, PostalCodeMessageImpl, UsPostalCodeImpl}
import com.improving.app.organization.OrganizationStatus

object ProtobufEnumSerde {

  lazy val ProtobufEnumSerdeModule: SimpleModule =
    new SimpleModule()
      .addSerializer(
        classOf[OrganizationStatus],
        new OrganizationStatusSerializer
      )
      .addDeserializer(
        classOf[OrganizationStatus],
        new OrganizationDeserializer
      )
      .addSerializer(classOf[PostalCodeMessageImpl], new PostalCodeMessageImplSerializer)
      .addDeserializer(
        classOf[PostalCodeMessageImpl],
        new PostalCodeMessageImplDeserializer
      )

  class OrganizationStatusSerializer extends JsonSerializer[OrganizationStatus] {
    override def serialize(
        value: OrganizationStatus,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ): Unit = {
      gen.writeStartObject()
      gen.writeNumberField("id", value.value)
      gen.writeEndObject()
    }
  }

  class OrganizationDeserializer extends JsonDeserializer[OrganizationStatus] {
    override def deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): OrganizationStatus = {
      val node: JsonNode = p.getCodec.readTree(p)
      val id = node.get("id").numberValue.asInstanceOf[Integer]
      OrganizationStatus.fromValue(id)
    }
  }

  class PostalCodeMessageImplSerializer extends JsonSerializer[PostalCodeMessageImpl] {
    override def serialize(
        value: PostalCodeMessageImpl,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ): Unit = {
      val code = value match {
        case PostalCodeMessageImpl(CaPostalCodeImpl(code)) => s"canada-$code"
        case PostalCodeMessageImpl(UsPostalCodeImpl(code)) => s"usa-$code"
        case _                                             => "error-Postal code IS EMPTY."
      }
      gen.writeStartObject()
      gen.writeStringField("postalCodeValue", code)
      gen.writeEndObject()
    }
  }

  class PostalCodeMessageImplDeserializer extends JsonDeserializer[PostalCodeMessageImpl] {
    override def deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): PostalCodeMessageImpl = {
      val node: JsonNode = p.getCodec.readTree(p)
      val code = node.get("postalCodeValue").asText()
      code.split("-") match {
        case Array("canada", code) => PostalCodeMessageImpl(CaPostalCodeImpl(code))
        case Array("usa", code)    => PostalCodeMessageImpl(UsPostalCodeImpl(code))
        case Array(_, message)     => throw new RuntimeException(s"Error in deserializing postal code - $message")
        case other                 => throw new RuntimeException(s"Error in deserializing postal code - $other")
      }
    }
  }

}
