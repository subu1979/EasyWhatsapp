# Installing on someone else's phone

Written for the person who will *use* the app, not build it. No laptop, no adb.

The app declares an Accessibility service, because Auto mode presses Send inside WhatsApp. Android
treats that as sensitive, which creates two obstacles for an app that did not come from a store:

1. **Play Protect** refuses the install ("App blocked to protect your device")
2. **Restricted settings** greys out the accessibility toggle ("For your security, this setting is
   currently unavailable") — and on ColorOS and similar builds, the "Allow restricted settings"
   override is sometimes refused outright

Installing through an **installer app** avoids both, because Android then does not consider the app
sideloaded. That is what this guide does. Manual mode alone needs none of this — see the last
section.

---

## Before you send it to anyone

Obtainium reads GitHub releases directly. The repository is currently **private**, so pick one:

- **Make it public** — simplest for the user, nothing to configure. Note the repo also holds the PRD
  document; remove it first if that should not be public.
- **Keep it private** — the user must add a GitHub personal access token in Obtainium
  (Settings → Source-specific → GitHub → Personal Access Token), with `repo` scope. Fine for one
  trusted person, awkward beyond that.

---

## Step 1 — Install Obtainium (once)

[Obtainium](https://github.com/ImranR98/Obtainium) is an open-source installer that keeps apps
updated straight from their GitHub releases.

1. On the phone, open <https://github.com/ImranR98/Obtainium/releases/latest>
2. Download `app-release.apk`
3. Open it and allow the browser to install unknown apps if asked

Obtainium itself needs no accessibility permission, so it installs without any of the friction above.

## Step 2 — Add this app to Obtainium

1. Open Obtainium → **Add App**
2. Paste: `https://github.com/subu1979/EasyWhatsapp`
3. Tap **Add**, then **Install**

Obtainium will offer every new release from now on, so updates need no further explanation.

## Step 3 — Turn on Auto mode

Do these in order. Manual mode switches the accessibility service off by itself, so enabling the
service while the app is set to Manual will simply turn it back off.

1. Open **WhatsApp Direct**
2. Under **Sending**, choose **Auto**
3. Tap **Accessibility settings**
4. Turn on **WhatsApp Direct auto-send**
5. Go back to the app — the warning under **Auto** should be gone

If the toggle is still greyed out, the store-install exemption did not apply on that device. Fall
back to: **App info → ⋮ → Allow restricted settings**, then repeat step 3.

## Step 4 — Use it

1. Enter the country code and number
2. **Initiate WhatsApp** — WhatsApp opens on that chat, and a notification shows the send is armed
3. Attach a photo, or take one with WhatsApp's camera
4. It sends by itself

The arming lasts ten minutes, covers one photo, and can be cancelled from its notification.

**After every app update, turn the accessibility service off and on again.** ColorOS, HyperOS and
similar builds leave it listed as enabled while quietly no longer running it. This is the one piece
of maintenance the user has to know about.

---

## If Auto mode is not needed

Manual mode has no accessibility service, no notification and no background presence: enter a number,
tap Initiate, WhatsApp opens on that chat, attach and send by hand. Installing for that is an
ordinary APK install — the Play Protect warning still appears, but the greyed-toggle problem never
comes up because nothing needs to be granted.

---

## Status of this guide

Steps 3 and 4 are verified on a CPH2637 (Android 16, WhatsApp 2.26.30.97) — both the gallery and
camera paths send with no final tap.

Steps 1 and 2 follow from how Android classifies installer-installed apps; Obtainium is not
individually documented for the restricted-settings exemption, so confirm on the target phone that
the accessibility toggle is available after installing through it. If it is not, use the fallback in
step 3 or publish to Google Play, where neither obstacle exists.
