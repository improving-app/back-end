package com.improving.app.common.domain

import com.improving.app.common.domain.PostalCode.PostalCodeValue
import scalapb.TypeMapper

/**
 * Postal Code is a protobuf message that requires to be serialized. Due to the interactions of akka actors
 * EventSourceBehavior, ScalaPB, and oneof, a TypeMapper needs to be implemented with the Json annotations.
 */
sealed trait PostalCodeImpl
case class UsPostalCodeImpl(code: String) extends PostalCodeImpl
case class CaPostalCodeImpl(code: String) extends PostalCodeImpl


case class PostalCodeMessageImpl(postalCodeValue: PostalCodeImpl)

object PostalCodeMessageImpl {
  implicit val tm = TypeMapper[PostalCode, PostalCodeMessageImpl] {
    postalCodeProto: PostalCode =>
      postalCodeProto.postalCodeValue match {
        case PostalCodeValue.Empty => PostalCodeMessageImpl(CaPostalCodeImpl(""))
        case PostalCodeValue.CaPostalCodeMessage(value) => PostalCodeMessageImpl(CaPostalCodeImpl(value))
        case PostalCodeValue.UsPostalCodeMessage(value) => PostalCodeMessageImpl(UsPostalCodeImpl(value))
      }
  } {
    postalCodeMessageScala: PostalCodeMessageImpl =>
      postalCodeMessageScala.postalCodeValue match {
        case CaPostalCodeImpl(code) => PostalCode(postalCodeValue = PostalCodeValue.CaPostalCodeMessage(code))
        case UsPostalCodeImpl(code) => PostalCode(postalCodeValue = PostalCodeValue.UsPostalCodeMessage(code))
        case _ => PostalCode(postalCodeValue = PostalCodeValue.CaPostalCodeMessage(""))
      }
  }
}
