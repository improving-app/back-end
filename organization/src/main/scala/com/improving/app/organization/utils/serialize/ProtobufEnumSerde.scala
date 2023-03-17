package com.improving.app.organization.utils.serialize

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
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

  class OrganizationStatusSerializer
      extends JsonSerializer[OrganizationStatus] {
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

}
