# Publishing Expense Tracker on Google Play

This is the full path from this repository to a live Play Store listing. The project is already set up for
it: release signing, App Bundle builds, store graphics and a privacy policy. The steps below need your
own Google account, so only you can do them.

Google changes Play Console's rules from time to time. The numbers here (fees, tester counts, API levels)
were correct when written; Play Console always shows the current requirements.

---

## 0. Decide two things first (they can't be changed later)

| | Current value | Where to change it |
|---|---|---|
| **Package name** (unique ID on Play) | `com.expensetracker`. Not on Play when checked, but Play Console only confirms it on first upload | `applicationId` in `app/build.gradle.kts` |
| **Free or paid** | Free | Play Console, when creating the app. A free app can never become paid. |

The app name shown on the store ("Expense Tracker") **can** be changed any time. A more distinctive name
is easier to find and less likely to clash with other apps, for example "Expense Tracker – Cash Flow".

---

## 1. Create a Google Play developer account

1. Go to <https://play.google.com/console/signup> and sign in with the Google account that will own the app.
2. Choose **Personal** (just you) or **Organization** (needs a D-U-N-S number).
3. Pay the one-time **US$25** registration fee and complete **identity verification** (ID document).
   This can take a few days.

> **Testing requirement for new personal accounts:** before you can publish to everyone, you must run a
> **closed test with at least 12 testers who stay opted in for 14 days in a row**. Plan for this: ask
> friends and family with Android phones early (step 6).

---

## 2. Create your upload key (once, keep it safe)

Google Play signs the app for users (**Play App Signing**). You sign each upload with your own
**upload key**. If you lose the upload key, Google can reset it, but that takes time, so back it up.

On your computer (JDK installed, or use Android Studio → *Build → Generate Signed Bundle* which does the same):

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a password and your name. Then, in the **project root**, create `keystore.properties`.
It's in `.gitignore`, so it's never committed:

```properties
storeFile=upload-keystore.jks
storePassword=YOUR_PASSWORD
keyAlias=upload
keyPassword=YOUR_PASSWORD
```

**Never commit the `.jks` file or the passwords.** Keep a copy of both somewhere safe, such as a password manager.

---

## 3. Build the App Bundle (.aab)

Google Play needs an **App Bundle**, not an APK.

**Option A, on your computer:**

```bash
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

If the build prints *"Release build is signed with the DEBUG key"*, `keystore.properties` wasn't found.
Play rejects debug-signed bundles.

**Option B, on GitHub (no Android Studio needed):**

1. Convert the key to text: `base64 -w0 upload-keystore.jks > upload.txt` (on macOS: `base64 -i upload-keystore.jks -o upload.txt`).
2. In GitHub → repository → **Settings → Secrets and variables → Actions**, add:
   `UPLOAD_KEYSTORE_BASE64` (contents of upload.txt), `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS` (`upload`), `UPLOAD_KEY_PASSWORD`.
3. **Actions → Android build → Run workflow**. When it finishes, download the **play-store-bundle** artifact.

**Every update** needs a higher `versionCode` (and usually a new `versionName`) in `app/build.gradle.kts`.

---

## 4. Create the app in Play Console

**Create app** → name *Expense Tracker*, language *English*, **App**, **Free**, accept the declarations.

---

## 5. Fill in "App content" (Policy → App content)

| Section | What to answer for this app |
|---|---|
| **Privacy policy** | A public URL to `docs/privacy-policy.html`. First replace **[your contact email]** in `docs/privacy-policy.md` / `.html`. Host it with GitHub Pages (Settings → Pages, needs a public repo or a paid plan) or paste the text into a free Google Sites page. |
| **Ads** | No, the app contains no ads. |
| **App access** | All functionality is available without special access (no login). |
| **Content rating** | Fill in the questionnaire. Category: *Utility, Productivity, Communication or Other*. Answer "No" to violence, gambling etc. Expect *Everyone / PEGI 3*. |
| **Target audience** | 18 and over (avoids the extra Families rules; it's a finance app). |
| **Data safety** | **No data collected, no data shared.** The app has no internet permission and keeps everything on the device. Data can be deleted in the app (Settings → Delete all). |
| **Financial features** | Declare what applies. This is a personal budgeting tool: it doesn't move money, offer loans, or connect to bank accounts. Statements are imported from files on the phone. |
| **Government app / News / Health** | No. |

---

## 6. Store listing (Grow → Store presence → Main store listing)

All graphics are ready in [`docs/play-store/`](play-store/):

| Asset | File | Play's requirement |
|---|---|---|
| App icon | `play-store/icon-512.png` | 512 × 512 PNG |
| Feature graphic | `play-store/feature-graphic.png` | 1024 × 500 PNG/JPEG |
| Phone screenshots | `play-store/phone/*.png` (8) | 2–8, max 2:1 ratio |

Re-render them after UI changes with `./gradlew recordRoborazziDebug`.

**Category:** Finance. **Contact email:** required (shown publicly).

Suggested texts (edit freely):

**App name** (max 30): `Expense Tracker – Cash Flow`

**Short description** (max 80):
`Track spending & income, import bank statements, and see your cash flow.`

**Full description** (max 4000):

```
Know where your money goes, and what's left.

Expense Tracker is a simple, private budget app. Add an expense in seconds, or import your bank
statement and let the app do the rest.

SMART CASH FLOW
• Money in, money out, and what's left over each month
• Safe-to-spend per day, counting the salary and bills still to come
• Month-end forecast at your current pace
• Regular income and bills spotted automatically, with their next dates
• Running balance and 6-month charts

IMPORT BANK STATEMENTS
• CSV, Excel (.xlsx) and PDF statements, including password-protected PDFs
• Columns, dates and money in/out detected automatically
• Statement check: totals and balances must add up before you import
• Categories suggested for you, and your choices are remembered
• Already-imported transactions are skipped, so importing twice is safe

CLEAR ANALYTICS
• Spending by category, by day and by month
• Monthly budget with progress on the home screen
• Week, month and year views with insights

PRIVATE BY DESIGN
• No account, no ads, no tracking
• No internet permission: your data never leaves your phone
• Export to CSV whenever you like

Also: 12 currencies including USD, EUR, KHR, THB and VND, plus dark mode and Material You colours.
```

---

## 7. Test, then release

1. **Testing → Closed testing → Create track.** Upload `app-release.aab`, write release notes ("First release").
2. **Testers:** add an email list (or a Google Group) with **at least 12 people**, then share the opt-in link.
   Testers install from the Play Store link and should keep it installed.
3. Send the release for review (usually a few hours to a few days).
4. After **14 days** with 12+ opted-in testers, go to **Dashboard → Apply for production** and answer the
   questions about your test.
5. Once approved: **Production → Create new release**, upload a bundle (higher `versionCode`), and roll out.
   A **staged rollout** (e.g. 20% first) is a safe way to start.

---

## Checklist

- [ ] Developer account created and verified
- [ ] Package name and app name decided
- [ ] Upload key created and backed up; `keystore.properties` in place (or GitHub secrets added)
- [ ] `./gradlew bundleRelease` → signed `.aab` (no "DEBUG key" warning)
- [ ] Privacy policy email filled in and hosted at a public URL
- [ ] App content: privacy policy, ads, app access, content rating, target audience, data safety, financial features
- [ ] Store listing: name, descriptions, icon, feature graphic, screenshots, category, contact email
- [ ] Closed test with 12+ testers for 14 days
- [ ] Production access approved → production release
