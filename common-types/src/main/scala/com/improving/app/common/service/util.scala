package com.improving.app.common.service

object util {
  def doIfHas[I, O](newData: Option[I], oldData: O, doFunc: I => O): O = {
    newData match {
      case None    => oldData
      case Some(v) => doFunc(v)
    }
  }

  def doForSameIfHas[I](newData: Option[I], oldData: I, doFunc: I => I = (v: I) => v): I =
    doIfHas[I, I](newData, oldData, doFunc)
}
