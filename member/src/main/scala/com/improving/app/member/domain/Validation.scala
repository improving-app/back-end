package com.improving.app.member.domain

import com.improving.app.common.errors.Validation.{
  applyAllValidators,
  editableContactValidator,
  listHasLength,
  required,
  requiredThenValidate,
  Validator
}

object Validation {

  val draftTransitionMemberInfoValidator: Validator[EditableInfo] =
    applyAllValidators[EditableInfo](
      memberInfo => required("handle")(memberInfo.handle),
      memberInfo => required("avatarUrl")(memberInfo.avatarUrl),
      memberInfo => required("firstName")(memberInfo.firstName),
      memberInfo => required("lastName")(memberInfo.lastName),
      memberInfo => requiredThenValidate("contact", editableContactValidator)(memberInfo.contact),
      memberInfo => listHasLength("organizationMembership")(memberInfo.organizationMembership),
      memberInfo => required("tenant")(memberInfo.tenant),
    )

  val memberCommandValidator: Validator[MemberCommand] =
    applyAllValidators[MemberCommand](
      memberCommand => required("memberId")(memberCommand.memberId),
      memberCommand => required("on_behalf_of")(memberCommand.onBehalfOf)
    )

  val memberQueryValidator: Validator[MemberQuery] =
    applyAllValidators[MemberQuery](memberQuery => required("memberId")(memberQuery.memberId))
}
