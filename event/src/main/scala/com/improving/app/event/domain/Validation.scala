package com.improving.app.event.domain

import com.improving.app.common.errors.Validation.{
  applyAllValidators,
  endBeforeStartValidator,
  required,
  requiredThenValidate,
  Validator
}

object Validation {

  val draftTransitionEventInfoValidator: Validator[EditableEventInfo] =
    applyAllValidators[EditableEventInfo](
      eventInfo => required("eventName")(eventInfo.eventName),
      eventInfo => required("description")(eventInfo.description),
      eventInfo => required("eventUrl")(eventInfo.eventUrl),
      eventInfo => required("sponsoringOrg")(eventInfo.sponsoringOrg),
      eventInfo => required("expectedStart")(eventInfo.expectedStart),
      eventInfo =>
        requiredThenValidate("expectedEnd", endBeforeStartValidator(eventInfo.expectedStart))(
          eventInfo.expectedEnd
        ),
    )

  val eventCommandValidator: Validator[EventCommand] =
    applyAllValidators[EventCommand](
      eventCommand => required("memberId")(eventCommand.eventId),
      eventCommand => required("on_behalf_of")(eventCommand.onBehalfOf)
    )

  val eventQueryValidator: Validator[EventQuery] =
    applyAllValidators[EventQuery](eventQuery => required("memberId")(eventQuery.eventId))
}
