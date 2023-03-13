package com.improving.app.member.utils.serialize

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
import com.improving.app.member.domain.{MemberStatus, NotificationPreference}

object ProtobufEnumSerde {

  lazy val ProtobufEnumSerdeModule: SimpleModule =
    new SimpleModule()
      .addSerializer(classOf[NotificationPreference], new NotificationPreferenceSerializer)
      .addDeserializer(classOf[NotificationPreference], new NotificationPreferenceDeserializer)
      .addSerializer(classOf[MemberStatus], new MemberStatusSerializer)
      .addDeserializer(classOf[MemberStatus], new MemberStatusDeserializer)

  class NotificationPreferenceSerializer extends JsonSerializer[NotificationPreference] {
    override def serialize(value: NotificationPreference, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeStartObject()
      gen.writeNumberField("id", value.value)
      gen.writeEndObject()
    }
  }
  class NotificationPreferenceDeserializer extends JsonDeserializer[NotificationPreference] {
    override def deserialize(p: JsonParser, ctxt: DeserializationContext): NotificationPreference = {
      val node: JsonNode = p.getCodec.readTree(p)
      val id = node.get("id").numberValue.asInstanceOf[Integer]
      NotificationPreference.fromValue(id)
    }
  }

  class MemberStatusSerializer extends JsonSerializer[MemberStatus] {
    override def serialize(value: MemberStatus, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeStartObject()
      gen.writeNumberField("id", value.value)
      gen.writeEndObject()
    }
  }

  class MemberStatusDeserializer extends JsonDeserializer[MemberStatus] {
    override def deserialize(p: JsonParser, ctxt: DeserializationContext): MemberStatus = {
      val node: JsonNode = p.getCodec.readTree(p)
      val id = node.get("id").numberValue.asInstanceOf[Integer]
      MemberStatus.fromValue(id)
    }
  }
}
