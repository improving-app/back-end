**Proposal**
- Instead of handling the organization hierarchy in the individual organizations & projections, we have organization hierarchy in a separate organization
- Each organization hierarchy is its own entity; the root org id is the entity key. It just stores the organization hierarchy, probably as a tree of org ids.
- Individual organizations no longer have a parent field; instead they have a root field
- (If the individual organization is itself a root, that field just points to itself.)
- When a company gets an Update Parent command, instead of checking a projection and then updating itself internally, 
  it sends a command to the organization hierarchy entity with its root ID. Because of the way entities work, the organization hierarchy will do any updates that 
  will do any state updating that results from that command /before it processes any subsequent command/. 
  Therefore we don't have to worry about race conditions or what have you.  From the organization entity's POV, this means that we can effectively "check and update"
  at the same time in a way that I don't really think is feasible from an entity pattern pov if what we're editing is internal to the entity 
  but reliant on external state. I mean, you don't want to reject the update in the event handler, or update state in the command handler...
- Because the organization hierarchy is itself an event-sourced entity, if the system crashes it can return to its state by replaying events; no problem there.
- The organization does still have to wait on the ask to the organization hierarchy entity, but it's only the one ask.
- It seems like this will make doing the necessary checks easier; instead of having to first gather the structure from the projections and then perform the checks,
  the hierarchy as it were comes pre-gathered, so you just have to perform the checks.
- On the Establish Organization command, either the root is passed in as the parent is now, and the organization hierarchy does the check that 
  the parent is actually a descendant of the root, or we do have a projection tracking org-root relationships specifically which it asks, or I suppose it could
  ask the parent directly. This does introduce another ask but it should be a very straightforward retrieval. 
- One con is that because the root is the entity key, you can't make two changes to the hierarchy simultaneously even if doing so would actually be perfectly safe.
  On the other hand, in some sense not doing that evaluation is safer, since you can't make a mistake in your reasoning if you're not doing any, 
  and my guess is that we're not actually likely to be making many simultaneous changes to organization hierarchy such that this will actually result
  in much slowing down.
- 