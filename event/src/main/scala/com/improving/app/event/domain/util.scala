package com.improving.app.event.domain

import com.improving.app.common.domain.util.{ContactUtil, EditableContactUtil}

object util {
  implicit class EventInfoUtil(info: EventInfo) {
    private[domain] def updateInfo(editableInfo: EditableEventInfo): EventInfo = {
      EventInfo(
        eventName = editableInfo.eventName.getOrElse(info.eventName),
        description = editableInfo.description.orElse(info.description),
        eventUrl = editableInfo.eventUrl.orElse(info.eventUrl),
        sponsoringOrg = editableInfo.sponsoringOrg.orElse(info.sponsoringOrg),
        expectedStart = editableInfo.expectedStart.orElse(info.expectedStart),
        expectedEnd = editableInfo.expectedEnd.orElse(info.expectedEnd),
        isPrivate = editableInfo.isPrivate,
      )
    }

    private[domain] def toEditable: EditableEventInfo = EditableEventInfo(
      eventName = Some(info.eventName),
      description = info.description,
      eventUrl = info.eventUrl,
      sponsoringOrg = info.sponsoringOrg,
      expectedStart = info.expectedStart,
      expectedEnd = info.expectedEnd,
      isPrivate = info.isPrivate,
    )
  }

  implicit class EditableEventInfoUtil(info: EditableEventInfo) {
    private[domain] def updateInfo(editableInfo: EditableEventInfo): EditableEventInfo = {
      EditableEventInfo(
        eventName = editableInfo.eventName.orElse(info.eventName),
        description = editableInfo.description.orElse(info.description),
        eventUrl = editableInfo.eventUrl.orElse(info.eventUrl),
        sponsoringOrg = editableInfo.sponsoringOrg.orElse(info.sponsoringOrg),
        expectedStart = editableInfo.expectedStart.orElse(info.expectedStart),
        expectedEnd = editableInfo.expectedEnd.orElse(info.expectedEnd),
        isPrivate = editableInfo.isPrivate,
      )
    }

    private[domain] def toInfo: EventInfo = EventInfo(
      eventName = info.getEventName,
      description = info.description,
      eventUrl = info.eventUrl,
      sponsoringOrg = info.sponsoringOrg,
      expectedStart = info.expectedStart,
      expectedEnd = info.expectedEnd,
      isPrivate = info.isPrivate,
    )
  }
}
