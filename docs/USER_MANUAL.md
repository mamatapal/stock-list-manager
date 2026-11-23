# Stock List Manager — user manual

Stock List Manager keeps your shop's suppliers, items, out-of-stock lists, purchases, goods received, batches and
expiry dates on every phone of your business. It works without internet and sends changes when the connection
returns.

## 1. Getting started (owner)

1. Install the app and open it. Choose **Register your business**.
2. Enter your business details and choose a username and a strong password (at least 8 characters; avoid common passwords). Your email
   address is used only to send you a code if you forget your password.
3. You are now the **owner**: you can do everything and you decide what each staff member may do.

Signing in later: **Owner sign-in** → username and password. After 5 wrong passwords the account is locked for
15 minutes.

**Forgot your password?** On the owner sign-in screen tap **Forgot password?**, enter your username, and enter the
code from the email with a new password. (Staff ask the owner instead.)

## 2. Staff

**Add a staff member**: Dashboard → **Staff** → **Add** (top right). Enter their name and a username. Choose what
they may do, or keep the safe defaults (they can record purchases and lists but cannot see profit, delete things or
manage staff). The app shows a **temporary password** once: give it to them in person. On first sign-in they must
choose their own password.

Staff sign in with **Staff sign-in** on their own phone.

**Permissions** (Staff → a person → Permissions) take effect on their next action, even if their app is open.
Sensitive changes ask you to re-enter your password.

**Deactivate** a person who leaves: they are signed out on every phone at once and cannot sign in; their past work
stays in the history with their name. **Reset password** gives them a new temporary password and signs them out.

## 3. Suppliers and items

- **Suppliers**: name, contact person, phone numbers, address, GSTIN, notes. Deleting hides a supplier; its past
  purchases are kept.
- **Items**: name, category, unit (pcs, kg, litre, box … or your own units), latest purchase and selling prices,
  preferred supplier. Item history shows every purchase of the item with its price at that time.

## 4. Out-of-stock lists

Dashboard → **Out-of-stock lists** → **New list**. Add items with quantity and an expected price (per unit or a
total). Save it as a draft or as a final list; share it as a PDF with the supplier. When goods arrive, **Record
purchase** from the list: the list shows what has been received and what is still missing. The requested quantity
never changes.

## 5. Purchases and goods received

Dashboard → **Purchases** → **New purchase** (top right).

1. Supplier, invoice number, purchase date.
2. Lines: item, quantity, unit, purchase price (per unit or total for the line), selling price (optional).
3. **Save draft** to finish later, or **Complete**. Completing can also record the goods received now, with batch
   numbers and expiry dates.

Totals are worked out exactly (no rounding drift). Prices are stored with the purchase: later price changes never
alter old purchases.

**Partial deliveries**: open the purchase → **Receive goods** each time goods arrive. Each delivery is kept
separately with date, quantities, batch and expiry. Mistakes are fixed with a correction, not by deleting.

**Editing and cancelling**: a completed purchase can be edited by people with that permission; the earlier version
is kept (**Earlier versions**). Completed purchases are never deleted: **Cancel** asks for a reason, keeps the record
and removes it from all totals.

## 6. History, batches and expiry

- **Purchases** screen: search by invoice, supplier or item; filter by status and dates.
- Supplier screen: that supplier's purchases and totals. Item screen → **Purchase history**: prices over time.
- **Expiry**: Expired, within 7 / 30 / 90 days, or all. Tap a batch to see it and open its purchase.
- The app can remind you daily about stock close to expiry (allow notifications when asked).

## 7. PDFs, printing and sharing

Tap the **PDF** icon (top right) on a list, purchase, supplier, item history, purchases list or expiry screen. The
preview shows the pages exactly as printed (A4, your business details at the top, page numbers). Then:
- **Save** — choose a folder on the phone (Downloads, Drive …).
- **Print** — Android's print screen; pick a printer or "Save as PDF".
- **Share** — WhatsApp, Gmail or any app; the PDF is attached as a file.

PDFs work offline. What you can make and share depends on your permissions.

## 8. Reports

Dashboard → **Reports**: purchases, supplier totals, item prices and (with permission) expected profit, for this
month, last month, the last 3 months, this financial year or any dates. Reports need internet because they cover
all phones of the business. Each report can be made into a PDF.

**Expected profit** is selling price minus purchase cost for lines with a selling price. It is an estimate, **not
the profit you actually made**: the app does not record sales.

## 9. Working offline and on several phones

- Everything you can do offline is saved on the phone at once. The dashboard card shows **Offline**, **Waiting to
  sync**, **Syncing** or **Up to date**, and when the last sync was.
- When the internet returns the app syncs by itself (and every 15 minutes). **Sync now** forces it.
- Sending the same change twice never creates a duplicate.
- If two people changed the same record, the app asks: **Keep mine** or **Use theirs**. Nothing is overwritten
  silently. Purchases and goods received are never thrown away without asking.
- **Details** on the sync card lists what has not been sent yet.
- Signing out deletes this phone's copy; if there are unsent changes you are warned first.

## 10. Privacy and security

- The data on the phone is encrypted; the app keeps no passwords, only a sign-in token protected by the phone.
- Each business sees only its own data.
- Staff only see what their permissions allow; the server checks every action.
- Profit figures never reach a phone whose user may not see them.

## 11. Language (हिन्दी + English)

- Every screen, message, notification and PDF shows **both Hindi and English**: the main language first, the other
  language underneath in smaller, lighter text. Short labels inside boxes and chips show both on one line:
  "मात्रा / Quantity".
- Choose which language comes first at the top right of the first screen, or later in **My account → Main language**.
  Until you choose, the phone's language decides. Each phone keeps its own choice.
- **Item, supplier, category and unit names have two boxes: English and Hindi.** Type the name in either box and the
  other fills in by itself, written the same way it sounds (Sugar → शुगर), not translated (not चीनी). The automatic
  spelling is only a guess for names that are not common shop words, so correct the other box if needed; a box you
  have corrected is not overwritten. Both spellings are saved and shown everywhere, and search finds either one.
  Names saved before this update stay as they were until you edit them and add the Hindi.
- Notes, list titles and descriptions can be typed in Hindi or English and are shown exactly as typed.
- Numbers typed with a Hindi keyboard (१२३) are read as normal numbers (123).
- Usernames, mobile numbers, PIN codes and GST numbers use English letters and digits 0-9.
- PDF file names use the main language, for example `खरीद_K-1_2026-11-16.pdf`.

## Using the web app (iPhone or any browser)

The web app is for people who do not have an Android phone. It uses the same business, the same staff accounts and the same permissions as the Android app, and it shows both Hindi and English.

- Open `https://<your server address>/app/` in Safari (iPhone) or any browser and sign in with your usual username and password. Choose **Owner sign-in** or **Staff sign-in**.
- To keep it on the home screen on an iPhone: Share button → **Add to Home Screen**.
- The web app needs an internet connection. It does not work offline and does not send expiry reminders. Available now: suppliers, items (with units and categories), out-of-stock lists, purchases (record from a list, receive goods with batch and expiry, cancel) and the expiry screen. Names fill in Hindi or English by sound while you type, exactly like the Android app. PDFs and reports are in too (see below); the owner screens (staff, permissions, business profile) are being added step by step, so the Android app remains the complete app until all steps are released.
- Register a new business on the Android app first. A user with a temporary password must set a new one at first sign-in.

### PDFs and reports in the web app

- **Out-of-stock list**: open a list and tap **Share PDF**. **Purchase**: open it and choose **Cost price and selling price** (the full document) or **Selling price only** (items, quantities and selling prices, with no cost, profit, supplier or invoice, so you can show it to anyone). **Expiry**: tap **Share PDF** above the list.
- **Reports** card on the Home screen: pick a date range, then **Purchase report**, **Supplier totals**, **Item purchase prices** or (if you may see profit) **Expected profit report**. Pick a supplier or an item below it for its purchase history over the same dates.
- Every PDF opens as a preview. Tap **Download PDF** to save it, or **Share PDF** to send it with WhatsApp, e-mail and so on (on an iPhone, Share opens the usual share sheet). If your browser cannot share, the PDF is saved instead.
- Hindi and English are on every PDF, the same as on the Android app. The PDF is a picture of each page, so its text cannot be selected or searched. Expected-profit columns appear only for users who may see profit.
