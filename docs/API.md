# API documentation

The complete, machine-readable description of every endpoint, request and response is **`openapi.json`**
(OpenAPI 3, generated from the running code by the test suite and shipped in the deployment package). Open it in
any OpenAPI viewer (for example https://editor.swagger.io → File → Import file), or run the backend locally with
`SWAGGER_ENABLED=true` and open `/swagger-ui.html`. This page explains the rules that apply to all endpoints.

Base URL: `https://<API_DOMAIN>/api/v1/` · JSON in UTF-8 · all endpoints except sign-in need a token.

## Authentication

| Step | Call | Notes |
| --- | --- | --- |
| Register a business | `POST /auth/register-business` | Creates the business and its owner. No default accounts exist. |
| Sign in | `POST /auth/login` `{username, password, deviceId, deviceName, expectedRole?}` | Returns `accessToken` (15 min) and `refreshToken` (30 days) and the user. 5 wrong passwords lock the account for 15 minutes; unknown user and wrong password give the same answer. |
| Use the API | header `Authorization: Bearer <accessToken>` | Missing, expired or forged token → `401`. |
| Refresh | `POST /auth/refresh` `{refreshToken}` | Rotates: the old refresh token stops working. Using an old one again revokes the whole session (theft detection). |
| Sensitive actions | `POST /auth/reauth` `{password}` → `stepUpToken` (5 min), sent as `X-Step-Up-Token` | Needed for staff management and business settings. A step-up token only works for the user it was issued to. |
| Sign out | `POST /auth/logout` `{refreshToken}` | |
| Forgotten owner password | `POST /auth/password/forgot` → code by email → `POST /auth/password/reset` | Always answers the same, so usernames cannot be discovered. Staff passwords are reset by the owner. |

Deactivating a staff member, resetting their password or changing a password revokes their sessions at once: the
next request gets `401`.

## Permissions

The server checks every request; hiding a button in the app is never the protection. The business is always the
signed-in user's own (from the token). A record of another business is answered with `404`, exactly like a record
that does not exist.

| Area | Permission codes |
| --- | --- |
| Suppliers | `SUPPLIER_VIEW`, `SUPPLIER_ADD`, `SUPPLIER_EDIT`, `SUPPLIER_DELETE` |
| Items and units | `ITEM_VIEW`, `ITEM_ADD`, `ITEM_EDIT`, `ITEM_DELETE` |
| Out-of-stock lists | `LIST_CREATE`, `LIST_EDIT`, `LIST_DELETE` |
| Purchases | `PURCHASE_RECORD`, `PURCHASE_EDIT`, `PURCHASE_DELETE`, `PURCHASE_PRICE_ENTER`, `SELLING_PRICE_ENTER`, `CATALOG_SELLING_PRICE_CHANGE`, `PURCHASE_HISTORY_VIEW` |
| PDFs and expiry | `PDF_GENERATE`, `PDF_SHARE`, `EXPIRY_VIEW` |
| Sensitive | `EXPECTED_PROFIT_VIEW`, `PROFIT_REPORT_VIEW`, `SETTINGS_MANAGE`, `STAFF_MANAGE` |

The owner has every permission. `GET /permissions` lists them with their labels. Profit figures are removed from
responses (including sync) for users without `EXPECTED_PROFIT_VIEW`. A staff manager can never grant permissions
they do not have themselves.

Endpoints any signed-in user may call: `GET /auth/me`, `GET /business` (for PDF headers), `GET /permissions`,
`GET /units`, `GET /people`, `GET /sync/pull` (returns only what the user may see), `POST /sync/push` (each change is
checked like the matching API call). Everything else needs the permissions above; the automated test
`SystemSecurityIT` calls every endpoint as a staff member without permissions and as another business's owner.

## Records, versions and conflicts

- Ids are UUIDs chosen by the phone, so a record created offline keeps its id.
- Every record has a `version`. Updates and deletes send `If-Match: <version>`; if someone else changed it first
  the answer is `409` with the current copy, never a silent overwrite.
- Deletes are soft (the record stays for history); completed purchases are never deleted, they are cancelled with a
  reason (`POST /purchases/{id}/cancel`), and every edit of a completed purchase stores the previous version
  (`GET /purchases/{id}/revisions`).
- Money and quantities are decimal strings (`"1234.50"`, `"2.5"`), never floating point. Money has 2 decimals,
  unit prices up to 4, quantities up to 3. Dates are `yyyy-MM-dd`.

## Errors

Errors are RFC 7807 problem documents:

```json
{ "status": 403, "code": "PERMISSION_DENIED", "detail": "You do not have permission ..." }
```
`400` invalid input (`VALIDATION_FAILED` with an `errors` list of `{field, message}`), `401` not signed in, wrong
password or locked account, `403` not allowed, `404` not found (or another business's), `409` version conflict
(with `currentVersion` and `current`) or duplicate, `429` too many attempts. Unexpected errors
give `500 INTERNAL_ERROR` without details; the cause is only in the server log.

## Offline sync

| Call | Purpose |
| --- | --- |
| `POST /sync/push` `{ops:[...]}` | Up to 50 changes: `{opId, entityType, entityId, action: UPSERT\|DELETE, baseVersion, <payload>}` for `SUPPLIER`, `ITEM`, `UNIT`, `STOCK_LIST`, `PURCHASE`, `DELIVERY`. Each gets `APPLIED`, `CONFLICT` (with the server copy), `REJECTED` (with the reason) or `RETRY` (nothing saved; send again later). |
| `GET /sync/pull?since=<n>&limit=<m>` | Everything changed after `n` that the user may see, plus `nextSince`, `hasMore` and `dataEpoch`. |

- Sending the same `opId` again returns the stored result: retries never create duplicates. Deliveries are also
  unique by their own id, so goods are never received twice.
- `dataEpoch` changes only when the database is restored from a backup; the app then downloads everything again.
- Conflict rules per record type are in `SyncDtos` and the project notes: whole-record version check for suppliers,
  items, lists and draft purchases; completed purchases keep every earlier version; deliveries are append-only.

## Reports

`GET /reports/purchases`, `/reports/supplier-totals`, `/reports/item-prices`, `/reports/expected-profit`, all with
`from` and `to` (`yyyy-MM-dd`, at most 10 years apart). Only completed purchases are counted. Profit fields need
`EXPECTED_PROFIT_VIEW`; the expected-profit report also needs `PROFIT_REPORT_VIEW`. Expected profit is an estimate
from selling prices entered on purchases, never realised profit.

## Other endpoints

Suppliers `/suppliers`, items `/items` (+`/categories`), units `/units`, lists `/stock-lists` (+`/duplicate`,
`/receiving`), purchases `/purchases` (+`/deliveries`, `/cancel`, `/revisions`, `/by-supplier/{id}`,
`/by-item/{id}`), batches and expiry `/batches` (`expiry=EXPIRED|WITHIN_7|WITHIN_30|WITHIN_90|ALL`, `/alert`),
staff `/staff` (+`/permissions`, `/sessions`, `/activate`, `/deactivate`, `/reset-password`), activity log
`/activity` (owner), business profile `/business`, people `/people`. Details and examples: `openapi.json`.
