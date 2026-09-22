VJoyKart Partner - Phone Login & Registration Fix
===================================================

Changes:
- Added required mobile number field to both delivery-partner registration screens.
- Registration request now sends `phone`.
- Client validates Indian mobile numbers (10 digits, or +91/0091 input).
- Backend validates the registration phone format.
- Backend normalizes +91/0091 to a 10-digit mobile number.
- Backend prevents duplicate mobile numbers.
- Partner login now supports email, username, or mobile number.
- Registration success pre-fills the mobile number on the login screen.
- Updated login/registration UI wording.
- Added/updated contract tests.

API base URL remains:
https://nexamartpartner-production.up.railway.app/api/v1/

Build:
./gradlew clean
./gradlew assembleDevDebug

Production backend must be deployed with the updated backend source/migration set.
