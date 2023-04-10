package com.improving.app.common.errors

sealed trait Error {
  val message: String
}

case class ValidationError(message: String) extends Error

case class StateError(message: String) extends Error