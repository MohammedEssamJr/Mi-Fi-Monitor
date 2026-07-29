# MiFi Monitor — ZTE MF937 companion app

Kotlin + Jetpack Compose app that talks to your MF937's built-in web admin
API (there's no official SDK — this is the same JSON API the stock
`http://192.168.8.1` page uses).

## Get an installable APK without installing Android Studio
This project includes a GitHub Actions workflow (`.github/workflows/build-apk.yml`)
that compiles a debug APK on GitHub's own servers. All from a browser:

1. Go to https://github.com/new and create a new repository (any name,
   public or private — private is fine, this only needs your own access).
2. On the new repo's page, click "uploading an existing file", then drag
   in **everything inside this unzipped folder** (including the hidden
   `.github` folder — if your file manager hides dotfolders, unhide them
   first, or use GitHub Desktop instead, which shows them by default).
3. Commit the upload to the `main` branch.
4. Click the **Actions** tab at the top of the repo. A run called
   "Build debug APK" starts automatically (takes ~3-5 minutes).
5. When it finishes (green check), click into that run, scroll to
   **Artifacts** at the bottom, and download `mifi-monitor-debug-apk`.
   It's a zip containing `app-debug.apk`.
6. Transfer that APK to your phone (email it to yourself, Google Drive,
   whatever's easiest) and open it there. Android will ask you to allow
   installs from that source ("unknown apps") the first time — that's
   expected for anything not from the Play Store.
7. Android may also show a Play Protect warning since it's an unsigned
   debug build — that's normal for personal test builds, not a sign
   anything's wrong.

Every time you push a code change to `main`, this rebuilds automatically —
so once it's set up, updates are just "upload the changed files again."

## Alternative: run it from Android Studio

1. Open this folder in Android Studio (Koala/2024.1+).
2. Let Gradle sync.
3. Connect your phone to the MF937's Wi-Fi hotspot.
4. Run the app, enter your admin password (same one you use to log into
   `http://192.168.8.1` in a browser), tap Connect.

## Before it will show real data: verify the field names (10 min)
Your MF937's firmware build may use slightly different JSON keys than the
common ones I pre-filled. To confirm:

1. On a laptop connected to the MF937's Wi-Fi, open `http://192.168.8.1`
   in Chrome and log in.
2. Open DevTools (F12) → Network tab → refresh the status page.
3. Find the request to `goform_get_cmd_process`. Look at:
   - the `cmd=...` list in the request URL
   - the JSON keys in the **response**
4. Compare against `ZteApiClient.kt` (`STATUS_CMDS`) and `DeviceStatus.kt`
   (the `json.optString("field_name", ...)` calls). Rename any that don't
   match what you see.
5. Do the same for changing the Wi-Fi name/password in the admin UI —
   capture the `goform_set_cmd_process` request and match `goformId` +
   field names in `setSsid` / `setWifiPassword`. Same for the admin login
   password change (`changeAdminPassword`) — this one varies more across
   ZTE firmware builds than the others, so check it carefully.

This step is unavoidable because ZTE ships several slightly different
firmware variants under the same model number depending on carrier/region.

## What's covered
- Battery %, charging state
- Signal bars, network type (LTE/3G), carrier name
- Live download/upload speed (computed from two polls 2s apart)
- Session and billing-cycle data usage
- Connected device count
- Edit Wi-Fi SSID and password
- Change the router admin login password (Login tab)
- Real-time estimated time-to-full-charge while plugged in (derived from
  watching the battery % climb over the last minute — no separate API
  field needed)
- On-device connection log (Logs tab, or "View connection logs" from the
  login screen): records every request/response/error to a file, with a
  "Copy all" button so you can paste it back for debugging without
  needing a computer or Logcat access
- Custom dark teal theme with icons per stat, instead of default Material

## Not covered yet (natural next additions)
- "Time to be filled" / time-remaining-on-plan: MF937 firmware doesn't
  universally expose a WE-package quota or renewal date via this API —
  that number typically only lives in the WE operator's own app/USSD.
  If you can capture that request via DevTools too, it's a small addition.
- Background monitoring / notifications (would need a foreground service,
  since Android restricts background network polling).
- MAC filtering, guest network, APN editing — same pattern as the Wi-Fi
  settings screen, just more `goformId` values to wire up.
