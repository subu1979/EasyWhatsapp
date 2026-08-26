# Feasibility: auto-pressing Send inside WhatsApp (v4)

Question: after the user attaches or shoots a photo in the chat this app opened, can the send be
completed **without the user pressing Send**?

Short answer: **yes, and it does not need AI.** It needs an Accessibility service and careful arming.
The costs are real but different from the ones that killed the earlier attempt.

---

## Why this is not the v1.4 problem

v1.4 shipped an Accessibility service and it was removed because it had nothing to act on: the
recipient never reached WhatsApp's picker, so no row existed to tap.

This task is the opposite shape:

| | v1.4 auto-send | v4 auto-send |
|---|---|---|
| Target | A recipient row that **does not exist** for unsaved numbers | WhatsApp's **Send** button on the media preview |
| Exists on screen? | No | Yes, always, in every WhatsApp build |
| Chat already correct? | No — that was the problem | Yes — the app opened it by deep link |
| Needs the address book? | Yes | No |

The failure mode of v1.4 does not apply. The button is drawn, it is a normal clickable node, and
`AccessibilityNodeInfo.performAction(ACTION_CLICK)` is the documented way to press it.

---

## AI is the wrong tool here

The request says "intelligent AI bot to monitor". The monitoring is a state check, not a judgement:

```
window belongs to com.whatsapp
    AND a send control is present
    AND this send was armed by our app less than N seconds ago
        → click it, once
```

An on-device or cloud model would add latency, cost, and — critically — would mean **sending
WhatsApp screen contents to a model**, which is a far larger privacy exposure than anything this app
has done so far. The accessibility node tree already gives view ids and content descriptions; there
is nothing to infer.

Vision AI would only earn its place if WhatsApp ever obscured its tree (e.g. rendered the composer
in a way accessibility cannot read). It does not today.

**Design decision: deterministic matcher, no model.**

---

## How it would work

1. User enters the number, taps **Initiate WhatsApp**
2. App **arms** a one-shot session: recipient digits + timestamp + `auto_send=true`
3. WhatsApp opens on that chat; the user attaches a photo or shoots one
4. Service sees a WhatsApp window containing a send control **and** verifies the chat is the armed
   recipient (title or subtitle contains the number, which is how WhatsApp titles an unsaved chat)
5. Clicks Send exactly once, then disarms
6. Anything unexpected — no match, wrong chat, window changed, timeout — disarms and does nothing

Matchers, in order, all cheap:

- `<package>:id/send` (the media-preview send FAB)
- content description equal to "Send"
- fallback: a clickable node whose description starts with "Send"

Everything degrades to "do nothing", so the worst case is the user pressing Send themselves — today's
behaviour.

---

## The safety design is the hard part, not the clicking

Auto-pressing Send means a mistake is unrecoverable: WhatsApp has no unsend beyond "delete for
everyone", and only for a while. The guards matter more than the feature:

| Risk | Guard |
|---|---|
| Fires on the wrong chat | Verify the open chat matches the armed number before clicking; never click on an unverified chat |
| Fires on an old arming | Arming expires (60–90 s) and is single-shot |
| Fires on a chat the user opened themselves | Service is inert unless armed by our app in this session |
| Sends a photo the user was still editing | Only act on the send control of the media preview, and only after it has been stable for ~600 ms |
| User changes their mind | A visible "Auto-send armed — tap to cancel" notification, cancellable at any time |
| Silent misfire | Log every click locally so the user can see what happened |

I would also make auto-send **off by default and per-send**, not a global mode: the user opts in on
the screen where they type the number, for that one send.

---

## Research findings (2026-08-26)

Checked against primary and secondary sources rather than assumed. Two of these correct earlier
statements in this repository.

**1. The approach is proven in production — and it is allowed on Google Play.**
Several published apps automate WhatsApp sending through exactly this mechanism: *AutoResponder for
WA*, *WhatsAuto*, *Auto Text — Schedule Messages*. They are listed on Google Play, which means an
Accessibility service is acceptable when automating the user's own actions is the app's disclosed
core function. **Correction:** earlier notes in this repo (v1.2/v1.4) say Play forbids this outright.
That was too strong — Play restricts *undisclosed or unrelated* use, not this pattern. Distribution
here stays sideload for other reasons, but the policy claim was wrong.

**2. Android 13+ blocks Accessibility for sideloaded apps until the user lifts the restriction.**
On Android 13, 14, 15 and 16, an app installed outside an app store has its accessibility toggle
greyed out with *"For your security, this setting is currently unavailable"*. The user must open
**App info → ⋮ → Allow restricted settings** first. This affects the Redmi 15 5G (Android 16); the
Redmi Note 9 Pro (Android 12) is not affected. Installing over `adb` uses the session installer and
generally avoids the restriction — a practical reason to prefer `adb install -r` for this build.

**3. WhatsApp prohibits automating personal accounts with third-party apps.**
Reported suspensions cluster around automated *bulk* sending; a single user-initiated send is far
lower risk, but it is not zero. **Correction:** I previously said UI automation carries "no ban risk
of that kind". More accurate: the ban risk is much lower than a protocol client such as OpenWA, and
it is not nil.

**4. Android 16's Advanced Protection Mode can revoke this permission.**
When a user turns on Advanced Protection, Android disables the Accessibility API for apps not
classified as accessibility tools, and revokes it if already granted. Anyone running that mode cannot
use this feature at all. It is opt-in and off by default.

**5. `FLAG_SECURE` is not an obstacle.** It blocks screenshots and screen recording, not the
accessibility node tree, so a screen that refuses screenshots can still be read and clicked by a
granted service.

---

## Costs you would be accepting

1. **Install friction, twice over.** Play Protect blocks the sideloaded install of any APK declaring
   an Accessibility service (seen in v1.2.0), and Android 13+ then greys out the toggle until
   *App info → ⋮ → Allow restricted settings*. `adb install -r` sidesteps both in practice.
2. **The service can read WhatsApp screens.** Scoped to the WhatsApp packages in the manifest, and it
   should keep nothing, but the capability is real and the user grants it explicitly.
3. **WhatsApp UI changes can break it.** View ids are not a contract. Expect to re-target
   occasionally; the fallbacks reduce but do not remove this.
4. **WhatsApp prohibits automating personal accounts with third-party apps.** This is on-device
   automation of the user's own hands, not a protocol client like OpenWA, so the exposure is far
   lower — reported suspensions concentrate on automated bulk sending. It is not zero, and one send
   at a time is the safest way to use it.
5. **Advanced Protection Mode disables it.** If the user ever enables Android 16's Advanced
   Protection, the permission is revoked automatically and the feature stops working by design.
6. **MIUI/HyperOS kills background services aggressively.** The Accessibility service needs to be
   exempted from battery optimisation or HyperOS will stop it, and the user must re-enable it after
   some system updates.

---

## Verify before building — 30 seconds, one command

The whole design rests on one unknown: **what the send control looks like in the WhatsApp build on
the Redmi**. That is directly observable, and it must be checked before any code is written.

With the phone connected over USB and developer options on:

1. Open the app, enter a number, tap **Initiate WhatsApp**
2. In WhatsApp, attach a photo so the preview with the Send button is on screen
3. On the laptop:

```bash
adb shell uiautomator dump /sdcard/wa.xml
adb shell cat /sdcard/wa.xml | tr '<' '\n<' | grep -iE 'send|resource-id="com.whatsapp'
```

That prints the exact resource ids, content descriptions and bounds. If a clickable `…:id/send` or a
node described "Send" appears, the matcher is a five-line function and the feature is a day's work.
If the tree comes back empty or unclickable, the approach is dead and no amount of AI fixes it.

I am not able to run that here — there is no WhatsApp on this machine — and guessing it is exactly
the mistake that cost this project days earlier.

---

## Recommendation

Feasible, worth doing, in this order:

1. Run the `uiautomator` dump above and paste the output
2. Build the service against the real ids, with arming, chat verification, expiry and a cancel
   notification
3. Test on the Redmi with a number that is **not** saved, then with a wrong-chat case to prove the
   guard refuses to click
4. Ship as v4.0.0 with auto-send opt-in per send

If the dump shows what I expect, the user flow becomes: type number → Initiate → attach photo →
**sent**, with no final tap, from your own WhatsApp, with no contact saved.
