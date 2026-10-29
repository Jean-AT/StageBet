# OAuth2/OIDC operation

StageBet delegates authentication to an OpenID Connect provider and acts only as a JWT
resource server. It does not issue tokens or store user passwords.

## Contract

Set these values in every deployed environment:

| Variable | Required value |
| --- | --- |
| `STAGEBET_OIDC_ISSUER_URI` | Exact issuer URL used in the token `iss` claim. |
| `STAGEBET_OIDC_JWK_SET_URI` | HTTPS JWK Set endpoint belonging to that issuer. |
| `STAGEBET_OIDC_AUDIENCE` | Audience accepted by this API, normally `stagebet-api`. |

The API verifies the JWK signature, issuer, `exp`/`nbf` timestamps, audience, and a
`roles` list containing at least one StageBet role: `TENANT`, `OPERATOR`, `PLAYER`,
`ADMIN`, or `SUPPORT`. Other provider roles grant no StageBet authority. `/api/**`
requires a valid JWT except the signed Stripe webhook endpoint. Health and WebSocket
handshake paths retain their documented public access.

For player betting routes, a `PLAYER` role is necessary and the token's `tenant_id` and
`player_id` string UUID claims must exactly match the route. These claims are mapped from
the provider account, rather than accepted from client request headers or request bodies.

## Local/demo provider

The `oidc` Docker Compose profile runs Keycloak and imports the `stagebet` realm. It
contains a public Postman client, `stagebet-postman`, configured for Authorization Code
with PKCE. It has no seeded users and no committed credentials.

Bootstrap Keycloak by exporting `KEYCLOAK_ADMIN_USERNAME` and
`KEYCLOAK_ADMIN_PASSWORD`, then run:

```bash
docker compose --profile oidc up -d --wait
```

Create users and assign realm roles through the Keycloak administration UI. For a player,
set `tenant_id` and `player_id` attributes to the matching UUIDs. The realm maps those
attributes, roles, and the `stagebet-api` audience into access tokens. Rotate a user
password or delete the local user instead of putting credentials or generated tokens in
source control.

## Deployment and rollback

Use a managed OIDC provider in deployed environments and expose its JWK endpoint over
TLS. Rotate signing keys at the provider while keeping old public JWKs available until
all tokens signed by them expire. To roll back an API deployment, retain the same issuer,
audience, and JWK configuration; changing any of those values immediately invalidates
active access tokens.
