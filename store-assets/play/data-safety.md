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
| Device or advertising ID | No | No | — | Not collected |
| Contacts, SMS, photos, files | No | No | — | Not collected |
| Crash logs | No SDK in current build | No | — | Only hosting/provider logs if enabled |

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
| Precise location | No | No | Not collected by the staff app |
| Passwords | No plaintext retained; auth tokens stored securely | No | Authentication |
| Advertising ID / contacts / SMS / photos | No | No | Not collected |

- Staff accounts are created and removed by the store; self-service deletion is
  not offered in the staff app.
- Privacy policy is available on the staff sign-in screen.
- Encrypted in transit: **Yes**.
- Ads/in-app purchases: **No**.
