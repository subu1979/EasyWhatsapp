# Feasibility: sending a photo to an unsaved WhatsApp number from an Android app

Decision record, 2026-08-24. Written after five build-and-test cycles on the target device, plus a
review of primary sources. It exists so this ground is not re-covered.

## The requirement

> Android app → enter any WhatsApp number → attach an image → send → recipient receives it,
> **without saving a contact** and **without the WhatsApp UI appearing**, sent from the
> **user's own WhatsApp identity**.

Four constraints. Every approach below satisfies some and fails others; none satisfies all four.

---

## Part 1 — On-device routes (tested, not theorised)

Tested on a Redmi running HyperOS with current WhatsApp, 2026-08-23/24.

| # | Route | Mechanism | Result |
|---|-------|-----------|--------|
| 1 | `ACTION_SEND` + `EXTRA_STREAM` | Documented WhatsApp integration | Image attaches; **WhatsApp asks the user to pick the recipient** |
| 2 | `ACTION_SEND` + `jid` extra | Undocumented `<digits>@s.whatsapp.net` | **Ignored** by current builds; falls through to the picker |
| 3 | `wa.me` / `api.whatsapp.com` deep link | Documented click-to-chat | Opens a chat with an unsaved number, **carries no attachment** |
| 4 | Deep link first, then share | Make the chat exist, then attach | Number **still absent** from the share picker |
| 5 | Temporary contact, then share | Insert contact → share → delete | Contact insert/delete works (instrumented test), but **WhatsApp had not synced it** by send time |
| 6 | Accessibility service tapping WhatsApp's UI | Click the recipient row, then Send | **Nothing to click** — the row does not exist (see 4, 5). Also blocked by Play Protect on install |

### Why they fail — one mechanism, three faces

**WhatsApp accepts a recipient or an attachment, never both.**

- Click-to-chat is documented to carry exactly two things: the number, and optional pre-filled text
  that is *never* auto-sent. No media parameter exists.
- WhatsApp's own developer FAQ describes sharing as: the app fires `ACTION_SEND`, and **the user
  chooses the recipient inside WhatsApp**. Recipient selection is not delegable.
- The share picker lists chats and contacts. An unsaved number is neither, and the picker offers no
  field to type a number — so no automation can select it.
- Saving the number does make it selectable, but WhatsApp syncs the address book on its own
  schedule; a contact created seconds earlier is not yet visible. Waiting longer is not a fix, it is
  a race with another app's background job. And it violates the core requirement anyway.

**Conclusion for Part 1: the requirement is unachievable on-device.** Not difficult — unachievable,
because the recipient must be chosen by a human inside WhatsApp.

---

## Part 2 — Off-device routes

### A. WhatsApp Cloud API (official, Meta)

| Property | Reality |
|---|---|
| Sender identity | **A business number registered to Cloud API** — not the user's personal WhatsApp. That number can no longer be used in the normal WhatsApp app |
| Any recipient | Yes. Unverified businesses are capped at **250 business-initiated conversations / rolling 24 h**; Meta Business Verification raises the tier |
| Test tier | Meta's free test number is limited to **5 OTP-verified recipients** — a sandbox, not a product |
| First contact | Must be a **pre-approved template**. An image rides in the template's **media header** (≤15 MB) |
| Free tier | The old "1,000 free conversations/month" ended with the July 2025 move to per-message billing. It no longer exists |
| Cost (India list, from 1 July 2026) | Utility ₹0.115/msg · Marketing ₹0.8631/msg · Authentication ₹0.1163/msg → ~₹115–860 per 1,000 |
| Opt-in | Policy requires recipients to have opted in. Marketing templates may be withheld from contacts with no engagement history |
| Prerequisites | A working Facebook login, a Meta Business account, a dedicated phone number |
| Risk | None to any personal account |

**Fails the identity constraint.** Everything else it satisfies cleanly.

### B. Self-hosted gateway (OpenWA, whatsapp-web.js / Baileys)

| Property | Reality |
|---|---|
| Sender identity | **The user's own number** — the session is a linked device |
| Any recipient | Yes |
| Image | Yes — `POST /api/sessions/{id}/messages/send-image`, `base64` + `mimetype` |
| WhatsApp UI | Never appears |
| Infrastructure | A server running 24/7. Oracle Cloud Always Free (2 OCPU / 12 GB ARM) covers it at no cost |
| On-device privacy | **Lost.** The photo leaves the phone to reach the server |
| Ban risk | **Real.** Reverse-engineered client; the project itself says to use a number you can afford to lose, and bans cannot be appealed |
| Cold first contact | OpenWA documents that a first message to a contact who never wrote to you **may be accepted by the API and silently dropped by WhatsApp** (issue #830) — which is exactly this use case |

**Satisfies all four constraints on paper**, at the cost of a server, on-device privacy, and a real
chance the paired number is banned or the message silently not delivered.

### C. Puter.js (raised as an option)

Puter provides AI, storage, database, auth and serverless workers under a user-pays model, so app
infrastructure can cost the developer nothing. It has **no WhatsApp transport** — its own proponents
concede this. It can reduce the cost of an AI layer around the app; it cannot deliver a WhatsApp
message. It is orthogonal to the blocker, and adding it changes nothing about feasibility.

---

## Part 3 — Decision matrix

| | No contact saved | No WhatsApp UI | User's own identity | Any recipient | Image | Risk |
|---|---|---|---|---|---|---|
| Ship today (v1.5.0) | ✅ | ❌ | ✅ | ❌ contacts only | ✅ | none |
| Click-to-chat only | ✅ | ❌ | ✅ | ✅ | ❌ | none |
| Cloud API | ✅ | ✅ | ❌ business number | ✅ | ✅ | none |
| Self-hosted gateway | ✅ | ✅ | ✅ | ✅ | ✅ | ban + delivery |
| Puter.js | n/a | n/a | n/a | n/a | n/a | not a transport |

**No row satisfies all four.** The requirement as written cannot be met by any 2026 mechanism. The
choice is which constraint to drop:

- Drop **"no WhatsApp UI"** → the app that exists today, free and safe
- Drop **"own identity"** → Cloud API, ~₹115/month at your volume, fully supported
- Drop **"no ban risk / on-device privacy"** → self-hosted gateway, free hosting, real risk

---

## Recommendation

If the WhatsApp UI appearing is the dealbreaker and the sender must remain the user's own number,
the only route is the self-hosted gateway, and it should be validated before any app code:
deploy per [openwa-deploy.md](openwa-deploy.md), pair a **spare SIM**, and send to three numbers —
one that has chatted with the paired number and two that never have. If the two cold numbers do not
receive the photo, issue #830 applies to this use case and the route is dead regardless of effort.

That test costs an hour and settles what five build cycles could not.

---

## Sources

- WhatsApp click-to-chat parameters (phone + optional pre-filled text, never auto-sent):
  [faq.whatsapp.com/5913398998672934](https://faq.whatsapp.com/5913398998672934)
- WhatsApp Android integration — recipient is chosen by the user in WhatsApp:
  [faq.whatsapp.com/en/android/28000012](https://faq.whatsapp.com/en/android/28000012)
- Cloud API templates, media headers, service window:
  [developers.facebook.com — send messages](https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-messages/),
  [template fundamentals](https://developers.facebook.com/documentation/business-messaging/whatsapp/templates/overview)
- Unverified cap of 250 business-initiated conversations / 24 h, verification unlocks higher tiers:
  [WhatsApp API prerequisites](https://www.wati.io/en/blog/whatsapp-api-prerequisites/)
- Per-message pricing replaced conversation billing in July 2025; India rates from 1 July 2026:
  [Authgear](https://www.authgear.com/post/whatsapp-api-pricing/),
  [myoperator](https://myoperator.com/blog/whatsapp-business-api-pricing-india-2026)
- OpenWA engines, ban-risk guidance, and cold-first-contact delivery (#830):
  [github.com/rmyndharis/OpenWA](https://github.com/rmyndharis/OpenWA)
- Oracle Always Free tier limits (2 OCPU / 12 GB ARM since June 2026):
  [TerminalBytes](https://terminalbytes.com/oracle-cloud-free-tier-changes-2026/)
- Puter capabilities and user-pays model:
  [docs.puter.com](https://docs.puter.com/), [developer.puter.com/pricing](https://developer.puter.com/pricing/)
- On-device results 1–6: this repository's git history, 2026-08-23/24 (v1.1.0 – v1.5.0)
