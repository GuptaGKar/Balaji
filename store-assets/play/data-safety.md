# Play Data Safety worksheet

Confirm every answer against the final build and the hosting configuration before
submitting. This worksheet does not replace Play Console's questionnaire.

## Customer app (`com.balaji.provision`)

### Data collected

| Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|
| Name | Yes | No | No | Account, order fulfilment |
| Phone number | Yes | No | No | OTP sign-in, delivery contact, support |
| Address | Yes | No | No | Delivery |
| Approximate and precise location | Yes | No | Yes | Only when the customer taps “Use my current location” to fill an address |
| Order history | Yes | No | No | Fulfilment, bills, support |
| Payment info | No card data | No | — | Cash on delivery only |
| App interactions | Yes (orders, bills) | No | No | App functionality |
| Photos and videos | Yes | No | Yes | One optional photo attached to an order at checkout (for example a handwritten list) |
| Device or other IDs | Yes | No | No | Firebase Cloud Messaging registration token and Firebase Installation ID, uploaded to `device_tokens` after sign-in for order-status push notifications. Not an advertising ID; no advertising SDK |
| Contacts, SMS | No | No | — | Not collected |
| Crash logs | No crash/analytics SDK in current build | No | — | Only hosting/provider logs if enabled |

Evidence for the two rows added on 2026-11-13: `firebase-messaging` is in the
dependency set and `PushRegistrar` uploads this install's FCM token to
`device_tokens` after sign-in (`app_flavour = "customer"`); the checkout screen
picks one optional image (`ActivityResultContracts.GetContent()`) and uploads it
with the order (`attachPhoto`). No analytics, crash or advertising SDK is
present in the dependency list.

- Encrypted in transit: **Yes (HTTPS)**.
- Users can request deletion: **Yes**, in-app and on the public account-deletion
  page.
- Data shared with third parties for advertising: **No**.
- Service providers: Supabase (database, auth, storage) and the web/API host.
  Treat them as processors, not advertising recipients.
- Data processed ephemerally? **No** for the listed account/order data.
- Prominent disclosure: location is requested only from the address editor, with
  an in-context explanation, and is optional.

### Account deletion

- In app: Profile → Privacy & account → Delete my account → type `DELETE`.
- Web: `https://<deployed-domain>/account-deletion`.
- Phone: 9837803293.
- Deletes: name, phone, addresses, location coordinates, credentials, active
  sessions. Retains anonymised completed order/bill amounts for accounting, tax
  and legal records. Active orders must be completed or cancelled first.

## Staff app (`com.balaji.provision.staff`)

| Data type | Collected | Shared | Purpose |
|---|---|---|---|
| Name / login ID | Yes | No | Staff authentication and role display |
| Phone number | If provisioned by the store | No | Staff account contact |
| Work activity (orders, delivery updates, stock/billing actions) | Yes | No | Store operations and audit trail |
| Photos and files | Yes | No | Dispatch/delivery proof image picked from the device and uploaded with the order status change |
| Device or other IDs | Yes | No | Firebase Cloud Messaging registration token and Firebase Installation ID, uploaded to `device_tokens` after sign-in (`app_flavour = "staff"`). Not an advertising ID; no advertising SDK |
| Precise location | No | No | Not collected by the staff app |
| Passwords | No plaintext retained; auth tokens stored securely | No | Authentication |
| Contacts, SMS, advertising ID | No | No | Not collected |

- Staff accounts are created and removed by the store; self-service deletion is
  not offered in the staff app.
- Privacy policy is available on the staff sign-in screen.
- Encrypted in transit: **Yes**.
- Ads/in-app purchases: **No**.
