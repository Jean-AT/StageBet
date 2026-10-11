# WAGER-060 — Identity, tenancy, and responsible gaming

**Parent:** WAGER-000  
**Status:** Done
**Dependencies:** WAGER-010, WAGER-020

## Subtasks

- [x] Define tenant, operator, player, admin, and support roles.
- [x] Add OAuth2/JWT resource-server validation and authorization rules.
- [x] Enforce tenant ownership in commands, queries, indexes, and database constraints.
- [x] Add daily/session limits, self-exclusion, cool-off period, and account suspension.
- [x] Add audit records for administrative actions and sensitive changes.
- [x] Add rate limiting and abuse protection at command boundaries.

Implementation note: JWT validation is enabled when
`spring.security.oauth2.resourceserver.jwt.issuer-uri` is configured. Responsible-gaming
counters are row-locked and updated in the same transaction as bet placement, so a
failed reservation rolls the counter update back. Betting commands use an atomic Redis
fixed-window counter keyed by tenant and player; Redis failures remain visible as service
errors rather than silently weakening abuse protection.

## Acceptance criteria

- A user cannot read or mutate another tenant's data by changing an identifier.
- Protected endpoints reject missing, invalid, and insufficient roles consistently.
- Responsible-gaming limits are enforced before money is reserved.
- Sensitive values and tokens never appear in logs.
