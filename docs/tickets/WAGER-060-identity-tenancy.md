# WAGER-060 — Identity, tenancy, and responsible gaming

**Parent:** WAGER-000  
**Status:** Backlog  
**Dependencies:** WAGER-010, WAGER-020

## Subtasks

- [ ] Define tenant, operator, player, admin, and support roles.
- [ ] Add OAuth2/JWT resource-server validation and authorization rules.
- [ ] Enforce tenant ownership in commands, queries, indexes, and database constraints.
- [ ] Add daily/session limits, self-exclusion, cool-off period, and account suspension.
- [ ] Add audit records for administrative actions and sensitive changes.
- [ ] Add rate limiting and abuse protection at command boundaries.

## Acceptance criteria

- A user cannot read or mutate another tenant's data by changing an identifier.
- Protected endpoints reject missing, invalid, and insufficient roles consistently.
- Responsible-gaming limits are enforced before money is reserved.
- Sensitive values and tokens never appear in logs.
