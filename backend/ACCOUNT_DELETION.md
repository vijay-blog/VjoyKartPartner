# VJoyKart Partner account deletion

Public deletion page:
`https://nexamartpartner-production.up.railway.app/vjoykart-partner/delete-account`

Users enter their email/mobile number and password. The backend verifies the credentials and permanently deletes the delivery-partner account, profile, notifications and earnings. Existing historical orders are retained for transaction/audit integrity, with the deleted partner unassigned.

## Railway one-time Flyway repair
The current Railway database has migration history from an older schema. The deployment log shows migrations 2-5 are applied in the database but missing locally, and migration 6 has a checksum mismatch. Set `FLYWAY_REPAIR_ON_STARTUP=true` for ONE deployment. The startup strategy runs Flyway `repair()` and then `migrate()`.

After the service starts successfully, set `FLYWAY_REPAIR_ON_STARTUP=false` (or remove it) and redeploy once more.
