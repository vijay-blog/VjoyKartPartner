# Registration and Login Production Fix

## What was fixed

- Delivery-partner registration no longer generates JWTs. Account/profile persistence is completed first and the endpoint returns a simple success response.
- Registration database conflicts return HTTP 409 instead of a generic 500.
- Delivery-partner login uses the backend `identifier` contract and supports email, username, or normalized Indian phone number.
- Admin login uses the same JWT service and the ADMIN role is checked by the backend.
- JWT configuration is now validated during application startup. A missing or weak `JWT_SECRET` no longer waits until the first login request to fail.
- The bootstrap admin account is synchronized with `ADMIN_USERNAME` / `ADMIN_PASSWORD` on startup, so changing the Railway admin password actually takes effect.

## Required Railway variables

Set these in the **partner backend** Railway service:

```text
JWT_SECRET=<strong random secret, at least 32 UTF-8 bytes>
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<your intended admin password>
ADMIN_EMAIL=admin@nexamart.local
ADMIN_NAME=NexaMart Admin
ACCESS_TOKEN_MINUTES=60
REFRESH_TOKEN_DAYS=30
```

Generate a strong JWT secret locally, for example:

```bash
openssl rand -base64 48
```

Copy the generated value into `JWT_SECRET`. Never commit the secret to Git.

After changing Railway variables, redeploy/restart the backend.

## Expected API behavior

- `POST /api/v1/auth/register` -> HTTP 200 with `{ "message": "Delivery partner account created successfully." }`
- `POST /api/v1/auth/login` -> HTTP 200 with access/refresh JWTs when credentials are correct
- `POST /api/v1/auth/admin/login` -> HTTP 200 with access/refresh JWTs when the configured admin credentials are correct
- Invalid login credentials -> HTTP 401
- Duplicate registration email/phone -> HTTP 409

If the backend cannot start after this fix, check Railway logs for the explicit `JWT_SECRET` or `ADMIN_PASSWORD` configuration error.
