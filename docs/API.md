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
| Supplier network (V7) | `SUPPLIER_NETWORK_MANAGE`, `PURCHASE_ORDER_MANAGE` (off for staff by default) |

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

## Supplier Management app and Supplier Administration (Phase S1)

These endpoints are separate from the shop API above. Shop tokens are refused there, and their tokens are refused here.

**Supplier Management app** - `/api/v1/supplier-portal` with its own bearer tokens (issuer `supplier-management`,
key `SUPPLIER_JWT_SIGNING_KEY`):

| Purpose | Request | Notes |
| --- | --- | --- |
| Register | `POST /auth/register` `{legalName, tradeName?, contactPerson, mobile, email, password, address, city, state, pinCode, gstin?}` | Always `202`. A verification link is emailed; an already-registered email gets a notice instead, so the answer never reveals which emails exist. |
| Verify the email | `POST /auth/verify-email` `{token}` | Token from the link `https://<server>/supplier/verify-email.html#t=<token>` (24 hours, single use). The business then waits for approval. |
| New link | `POST /auth/resend-verification` `{email}` | Always `202`; at most one email a minute. |
| Sign in | `POST /auth/login` `{email, password, deviceId, deviceName?}` | Only after the email is verified (`EMAIL_NOT_VERIFIED` otherwise; a wrong password always gives `INVALID_CREDENTIALS`). Access token 15 minutes, refresh token 30 days. Works in every review state, so the app can show it. |
| Refresh / sign out | `POST /auth/refresh`, `POST /auth/logout` `{refreshToken}` | Rotation; a reused refresh token ends the session. |
| Passwords | `POST /auth/password/forgot` `{email}` (always `202`), `POST /auth/password/reset` `{token, newPassword}`, `POST /auth/password/change` | Reset links last 1 hour. A reset or change signs out every other phone and emails a security notice to the registered address. |
| Account | `GET /me`, `PUT /profile` (with `If-Match: <org version>`) | Details can change before approval and after a rejection; saving after a rejection sends the business for review again. |

Review states: `PENDING_EMAIL_VERIFICATION`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`. Supplier
business features (later phases) require `APPROVED`, checked on every request. Error codes are listed in
`SupplierPortalError` (the app translates each one into Hindi and English). No SMS and no one-time codes are used.

**Supplier Administration portal** - page at `https://<server>/admin/`, API under `/api/v1/admin` with an HttpOnly,
SameSite=Strict session cookie and the header `X-Requested-With: SupplierAdministration` on every change:
`POST /auth/login` `{username, password, code}` (code from an authenticator app), `POST /auth/logout`, `GET /auth/me`,
`GET /suppliers?status=`, `GET /suppliers/{id}`, `POST /suppliers/{id}/approve | reject | suspend | reinstate`
(`If-Match: <version>`; reject and suspend need `{reason}`, which the supplier sees). Administrators are created only
on the server: see `docs/SUPPLIER_ADMINISTRATION.md`.

## Supplier network: discovery, connections, catalogue, purchase orders (V7)

Shops and suppliers trade through these endpoints. Stock is never kept here: the goods receipt of an order creates an
ordinary completed purchase with one delivery through the purchase rules above, and only that changes stock.

**Shop side** - `/api/v1/supplier-network`, shop tokens. `SUPPLIER_NETWORK_MANAGE`: directory, connections, contact
link, product links. `PURCHASE_ORDER_MANAGE`: orders and receipt. Connections and catalogues can be read with either.
Records of other shops answer `404`.

| Purpose | Request | Notes |
| --- | --- | --- |
| Find suppliers | `GET /directory?q=`, `GET /directory/{supplierOrgId}` | Only `APPROVED` suppliers that turned their listing on. Name, legal name, city and state only. |
| Connect | `POST /connections` `{supplierOrgId, supplierId?}` | Only shops ask; the supplier accepts or declines. One connection per shop and supplier: a declined, cancelled or ended one can be asked again (it becomes `REQUESTED`, never `ACTIVE` directly). |
| Connections | `GET /connections`, `GET /connections/{id}`, `POST /connections/{id}/cancel` (a request), `POST /connections/{id}/end` `{reason?}` | Ending cancels orders not yet dispatched; dispatched orders can still be received. |
| Supplier contact | `PUT /connections/{id}/contact` `{supplierId}` or `{createContact: true}` | Links the supplier to one of the shop's own supplier contacts (purchases are recorded against it). Needed before receiving. |
| Catalogue | `GET /connections/{id}/catalogue` | Active products of an `ACTIVE` connection, each with the shop's link or `null`. |
| Product links | `PUT /connections/{id}/products/{productId}/link` `{itemId \| newItem{name, category?, defaultUnitId}, unitId?, unitsPerPack?}`, `DELETE` the same | Only by the shopkeeper; never automatic (no name or barcode matching). `unitsPerPack`: shop units in one supplier unit. |
| Orders | `POST /orders` `{id?, connectionId, note?, lines[{productId, quantity}]}`, `GET /orders?status=`, `GET /orders/{id}`, `POST /orders/{id}/cancel` `{reason?}` | Every product must be linked. Lines keep the name, unit, price and link of the moment the order was placed. `id` is an idempotency key: the same request again returns the original order; the same `id` with a different connection, note, products or quantities gets `409 ORDER_ID_REUSED`. |
| Goods receipt | `POST /orders/{id}/receive` `{receivedDate?, invoiceNo?, notes?, lines[{lineId, quantity, unitPrice?, batchNo?, expiryDate?}]}` | Once per order, every line once (quantity in the supplier's unit, 0 to the ordered quantity). Creates the purchase, delivery and batches in one transaction; also needs `PURCHASE_RECORD`. The purchase gets the price saved in the order; only a user with `PURCHASE_PRICE_ENTER` may send a different `unitPrice` (anyone else gets `403` and nothing is written). |

Order statuses: `SENT` -> `ACCEPTED` or `REJECTED` (supplier; rejecting needs a reason); `ACCEPTED` -> `DISPATCHED`
(supplier); `ACCEPTED` or `DISPATCHED` -> `RECEIVED` (shop); `SENT` or `ACCEPTED` -> `CANCELLED` (shop). `REJECTED`,
`CANCELLED` and `RECEIVED` are final. Every change is kept in the order's history.

The goods receipt is the single, final receipt of an order. A line may be received short (less than ordered, or 0
if nothing of it arrived); its actual quantity is recorded, only that quantity reaches stock, and the order still
becomes `RECEIVED` and is closed: no later receipt is possible for the missing goods (a new order is needed). At least
one line must have a quantity above 0. Each line shows the ordered and the received quantity, so the shortage stays
visible. *(Current behaviour, awaiting the product owner's confirmation; see PR #20.)*

Changes to one connection and its orders happen one at a time (the server locks the connection, then the order).
Ending a connection cancels its `SENT` and `ACCEPTED` orders in the same step, so no order is placed, accepted or
dispatched under an ended connection; `DISPATCHED` orders are never cancelled and stay receivable.

**Supplier side** - `/api/v1/supplier-portal`, supplier tokens, `APPROVED` businesses only:
`GET | PUT /directory-listing` `{listed}` (off until turned on), `GET /connections?status=`, `GET /connections/{id}`,
`POST /connections/{id}/accept | decline | end` (`{reason?}`), `GET /products`, `POST /products`,
`PUT /products/{id}` `{nameEn, nameHi?, unit, pack?, price}`, `POST /products/{id}/deactivate | activate`,
`GET /orders?status=`, `GET /orders/{id}`, `POST /orders/{id}/accept`, `POST /orders/{id}/reject` `{reason}`,
`POST /orders/{id}/dispatch` `{reason?}`. Suppliers see a shop's name, city and state only.

Error codes of these endpoints are listed in `NetworkError` (for example `CONNECTION_EXISTS`, `PRODUCT_NOT_LINKED`,
`ORDER_STATUS`, `ORDER_ALREADY_RECEIVED`, `ORDER_ID_REUSED`, `CONTACT_NOT_LINKED`).
