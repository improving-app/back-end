package com.improving.app.gateway.domain

import com.improving.app.gateway.domain.demoScenario.Event
import com.improving.app.gateway.domain.event.{
  CancelledEventInfo => GatewayCancelledEventInfo,
  EditableEventInfo => GatewayEditableEventInfo,
  EventCreated,
  EventInfo => GatewayEventInfo,
  EventInfoOrEditable => GatewayEventInfoOrEditable,
  EventMetaInfo => GatewayEventMetaInfo,
  EventState => GatewayEventState,
  EventStateInfo => GatewayEventStateInfo,
  ScheduledEventInfo => GatewayScheduledEventInfo
}
import com.improving.app.event.domain.{
  EditableEventInfo,
  EventInfo,
  EventInfoOrEditable,
  EventMetaInfo,
  EventState,
  EventStateInfo
}

object eventUtil {

  implicit class EventCreatedUtil(created: EventCreated) {
    implicit def toEvent: Event = Event(
      eventId = created.eventId,
      eventInfo = created.info.map(_.toInfo),
      metaInfo = created.meta
    )
  }

  implicit class EventInfoUtil(info: EventInfo) {
    def toGatewayInfo: GatewayEventInfo = GatewayEventInfo(
      eventName = info.eventName,
      description = info.description,
      eventUrl = info.eventUrl,
      sponsoringOrg = info.sponsoringOrg,
      expectedStart = info.expectedStart,
      expectedEnd = info.expectedEnd,
      isPrivate = info.isPrivate,
    )
  }

  implicit class GatewayEditableEventInfoUtil(info: GatewayEditableEventInfo) {

    def toInfo: GatewayEventInfo = GatewayEventInfo(
      eventName = info.getEventName,
      description = info.description,
      eventUrl = info.eventUrl,
      sponsoringOrg = info.sponsoringOrg,
      expectedStart = info.expectedStart,
      expectedEnd = info.expectedEnd,
      isPrivate = info.isPrivate
    )

    def toEditableInfo: EditableEventInfo = EditableEventInfo(
      eventName = info.eventName,
      description = info.description,
      eventUrl = info.eventUrl,
      sponsoringOrg = info.sponsoringOrg,
      expectedStart = info.expectedStart,
      expectedEnd = info.expectedEnd,
      isPrivate = info.isPrivate
    )
  }

  implicit class EditableEventInfoUtil(info: EditableEventInfo) {

    def toGatewayEditableInfo: GatewayEditableEventInfo =
      GatewayEditableEventInfo(
        eventName = info.eventName,
        description = info.description,
        eventUrl = info.eventUrl,
        sponsoringOrg = info.sponsoringOrg,
        expectedStart = info.expectedStart,
        expectedEnd = info.expectedEnd,
        isPrivate = info.isPrivate
      )
  }

  implicit class EventInfoOrEditableUtil(info: EventInfoOrEditable) {
    def toGatewayInfoOrEditable: Option[GatewayEventInfoOrEditable] =
      if (info.value.isInfo)
        info.value.info.map(i => GatewayEventInfoOrEditable(GatewayEventInfoOrEditable.Value.Info(i.toGatewayInfo)))
      else
        info.value.editable.map(i =>
          GatewayEventInfoOrEditable(GatewayEventInfoOrEditable.Value.Editable(i.toGatewayEditableInfo))
        )
  }

  implicit class EventStateUtil(eventState: EventState) {
    def toGatewayEventState: GatewayEventState = {
      if (eventState.isEventStateDraft) GatewayEventState.EVENT_STATE_DRAFT
      else if (eventState.isEventStateScheduled) GatewayEventState.EVENT_STATE_SCHEDULED
      else GatewayEventState.EVENT_STATE_CANCELLED
    }
  }

  implicit class EventStateInfoUtil(stateInfo: EventStateInfo) {
    def toGatewayEventStateInfo: GatewayEventStateInfo = {
      if (stateInfo.value.isDefined)
        if (stateInfo.value.isScheduledEventInfo)
          GatewayEventStateInfo(
            GatewayEventStateInfo.Value.ScheduledEventInfo(
              GatewayScheduledEventInfo()
            )
          )
        else if (stateInfo.value.isCancelledEventInfo)
          GatewayEventStateInfo(
            GatewayEventStateInfo.Value.CancelledEventInfo(
              GatewayCancelledEventInfo(
                stateInfo.getCancelledEventInfo.reason,
                stateInfo.getCancelledEventInfo.timeStartedOpt
              )
            )
          )
        else GatewayEventStateInfo.defaultInstance
      else GatewayEventStateInfo.defaultInstance
    }
  }

  implicit class EventMetaUtil(meta: EventMetaInfo) {
    def toGatewayEventMeta: GatewayEventMetaInfo = GatewayEventMetaInfo(
      createdBy = meta.createdBy,
      createdOn = meta.createdOn,
      scheduledBy = meta.scheduledBy,
      scheduledOn = meta.scheduledOn,
      lastModifiedBy = meta.lastModifiedBy,
      lastModifiedOn = meta.lastModifiedOn,
      actualStart = meta.actualStart,
      actualEnd = meta.actualEnd,
      currentState = GatewayEventState.fromValue(meta.currentState.value),
      eventStateInfo = meta.eventStateInfo.map(_.toGatewayEventStateInfo),
    )
  }
}
