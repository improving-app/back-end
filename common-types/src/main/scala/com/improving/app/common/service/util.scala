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

  def doForOptionSameIfHas[I](
      newData: Option[I],
      oldData: Option[I],
      doFunc: I => I = (v: I) => v
  ): Option[I] = {
    if (oldData.isDefined) Some(doIfHas[I, I](newData, oldData.get, doFunc))
    else if (newData.isDefined) newData
    else oldData
  }
}
