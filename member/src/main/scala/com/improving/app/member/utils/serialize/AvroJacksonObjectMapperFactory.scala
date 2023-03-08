package com.improving.app.member.utils.serialize

import akka.serialization.jackson.JacksonObjectMapperFactory
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.improving.app.member.utils.serialize.AvroJacksonObjectMapperFactory.applyObjectMapperMixins

class AvroJacksonObjectMapperFactory extends JacksonObjectMapperFactory {

  override def newObjectMapper(bindingName: String, jsonFactory: JsonFactory): ObjectMapper = {
    if (bindingName == "jackson-cbor") {
      val mapper: ObjectMapper = JsonMapper.builder(jsonFactory).build()
      // some customer configuration of the mapper
      applyObjectMapperMixins(mapper)
      mapper
    } else
      super.newObjectMapper(bindingName, jsonFactory)
  }

}

object AvroJacksonObjectMapperFactory {

  def applyObjectMapperMixins(mapper: ObjectMapper): ObjectMapper = {
    /* mapper.addMixIn(classOf[MemberInfo], classOf[IgnoreSchemaProperty])
    mapper.addMixIn(classOf[MemberMetaInfo], classOf[IgnoreSchemaProperty])*/

    mapper.registerModule(ProtobufEnumSerde.ProtobufEnumSerdeModule)
  }

  /*  abstract class IgnoreSchemaProperty { // You have to use the correct package for JsonIgnore,
    // fasterxml or codehaus
    //@JsonIgnore def notificationPreference(): Unit

    @JsonIgnore def memberTypes(): Unit

    @JsonIgnore def memberState(): Unit
  }*/
}
