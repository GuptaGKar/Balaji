# Balaji SMS Gateway

> **Superseded 2026-10-21 by the 2Factor managed-provider path.** Retained only as an optional fallback; see `docs/DECISIONS.md`. Do not deploy this gateway for the current path.

Minimal Android SIM gateway for the Supabase Send SMS Hook. It accepts one authenticated request shape and sends the message through the phone's SIM. It does not generate, store, or verify OTPs.

## Contract

`POST /api/send`

Headers:

- `Content-Type: application/json`
- `X-API-Key: <gateway key>`

Body:

```json
{
  "phone_number": "+919876543210",
  "message": "Balaji Provision Store OTP: 123456. Do not share this code.",
  "request_id": "optional-id"
}
```

Accepted phone numbers are Indian mobiles in `+91[6-9]XXXXXXXXX` form. Messages must be 1-320 characters and contain a six-digit OTP. Responses:

- `200 {"success":true}`: handed to the Android SMS radio.
- `400 {"success":false,"error":"invalid_request"}`
- `401 {"success":false,"error":"unauthorized"}`
- `404 {"success":false,"error":"not_found"}`
- `405 {"success":false,"error":"method_not_allowed"}`
- `429 {"success":false,"error":"rate_limited"}`
- `502 {"success":false,"error":"send_failed"}`

Rate limits are 10 requests per minute and 60 per hour per running process.

## Security properties

- API key is generated with `SecureRandom`, must be at least 32 characters, stored with `EncryptedSharedPreferences`, and never logged.
- Only `INTERNET`, `SEND_SMS`, foreground-service, notification, boot, wake-lock, Wi-Fi-state, and battery-optimization permissions are requested.
- No SMS history, OTP storage, contact access, or read-SMS permission.
- Android backup and device-transfer extraction are disabled.
- Only `POST /api/send` is exposed; there are no status, history, config, or bulk endpoints.
- Requests are authorized by a constant-time API-key comparison.
- While running, the foreground service holds a high-performance Wi-Fi lock and a partial wake lock so the phone can answer OTP requests with the screen off. Keep it on a charger.
- The server binds on port `8080`; it must sit behind Cloudflare Tunnel/Access and must never be port-forwarded directly to the internet.

## Build and test

Use JDK 17 and an installed Android SDK (`ANDROID_HOME`, or `sdk.dir` in an untracked `local.properties`):

```bash
cd tools/sms-gateway
JAVA_HOME=/tmp/opencode/jdk17 PATH=/tmp/opencode/jdk17/bin:$PATH ./gradlew --no-daemon testDebugUnitTest
JAVA_HOME=/tmp/opencode/jdk17 PATH=/tmp/opencode/jdk17/bin:$PATH ./gradlew --no-daemon lintDebug
JAVA_HOME=/tmp/opencode/jdk17 PATH=/tmp/opencode/jdk17/bin:$PATH ./gradlew --no-daemon assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` contains the local Android SDK path and is gitignored. Do not commit it.

## Phone setup

1. Install on the dedicated store phone: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Open the app and grant SMS permission and notifications.
3. Allow unrestricted battery use. Keep the phone charging and on the store Wi-Fi.
4. Copy the generated API key. Set it as the Supabase secret `SMS_GATEWAY_API_KEY`.
5. Tap **Start gateway**. The notification must say **Listening on port 8080**.
6. Reserve the phone's LAN IP in the router DHCP table so the tunnel origin does not change.

The app is not suitable for a personal phone. The SIM receives no inbound messages, but the app can send SMS and must be treated as a store credential.

## Cloudflare Tunnel on the store computer

The Supabase Edge Function cannot reach a private LAN address. Run `cloudflared` on the store computer and point it at the phone:

```yaml
tunnel: <tunnel-uuid>
credentials-file: /home/store/.cloudflared/<tunnel-uuid>.json
ingress:
  - hostname: sms.example.com
    service: http://192.168.1.50:8080
  - service: http_status:404
```

Create a DNS route and run the tunnel:

```bash
cloudflared tunnel create balaji-sms
cloudflared tunnel route dns balaji-sms sms.example.com
cloudflared tunnel run balaji-sms
```

Protect the hostname with a Cloudflare Access application and a service token. Give the Edge Function the token:

```text
SMS_GATEWAY_URL=https://sms.example.com
SMS_GATEWAY_API_KEY=<value copied from the phone>
SMS_GATEWAY_CF_ACCESS_CLIENT_ID=<Cloudflare Access service token id>
SMS_GATEWAY_CF_ACCESS_CLIENT_SECRET=<Cloudflare Access service token secret>
```

The API key and the Cloudflare Access token are separate secrets. Store them only in Supabase secrets or the local untracked env file. Never commit them.

Deploy the hook and set server-only secrets only after the tunnel and phone are verified:

```bash
supabase functions deploy send-sms-hook --project-ref <project-ref>
supabase secrets set \
  SEND_SMS_HOOK_SECRET='<hook secret from Supabase Auth>' \
  SMS_GATEWAY_URL='https://sms.example.com' \
  SMS_GATEWAY_API_KEY='<key copied from the phone>' \
  --project-ref <project-ref>
```

Add `SMS_GATEWAY_CF_ACCESS_CLIENT_ID` and `SMS_GATEWAY_CF_ACCESS_CLIENT_SECRET` in the same command when Cloudflare Access is enabled.

## End-to-end test

This sends a real SMS. Use the store's test number and only after the Supabase hook is deployed.

From the store computer:

```bash
curl -i -X POST http://192.168.1.50:8080/api/send \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: <gateway key>' \
  -d '{"phone_number":"+919876543210","message":"Balaji Provision Store OTP: 123456. Do not share this code.","request_id":"manual-test-1"}'
```

Then test the public URL with the Cloudflare Access service-token headers, and finally request an OTP from the Android app.

## Known limits

- `200` means the Android SMS radio accepted the message, not that the carrier delivered it. Delivery receipts are not checked.
- The gateway is intentionally single-purpose. It does not retry, queue to disk, or persist messages.
- HMAC/replay protection and per-number rate limiting are not implemented yet. Current layers are HTTPS, Cloudflare Access, the 256-bit API key, and global rate limits.
- A consumer SIM used for A2P OTP traffic can be throttled, blocked, or violate carrier terms. This is a low-volume pilot only; a dedicated phone/SIM and carrier review are required.
- Android may still kill the service on aggressive vendor firmware. Keep battery optimization disabled and verify the notification after reboots. The partial wake lock increases battery drain; the phone must stay on a charger.
- The debug APK is for pilot sideloading and is `android:debuggable`. Disable USB debugging after installation. Production distribution needs a signed release build and update process.
