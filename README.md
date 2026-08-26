# WhatsApp Direct – Image Sender (MVP v1.0)

Android utility that opens a WhatsApp chat with any number **without saving it as a contact**.
Built from `WhatsApp_Direct_Image_Sender_PRD_v1.0.docx`.

Country code, number, one button. WhatsApp opens on that chat, and whatever you want to send —
message, photo from the gallery, photo from the camera — is picked there with WhatsApp's own
controls, which work on a chat with an unsaved number. The app holds no permissions and creates no
contact.

The app used to attach the photo itself. That path only ever worked for numbers already in the
address book, so it was removed in v3.0.0 in favour of the one route that works for any number —
see [docs/feasibility.md](docs/feasibility.md).

## Download

For someone other than the developer, follow [docs/install-guide.md](docs/install-guide.md): it
installs through Obtainium, which avoids both the Play Protect block and the greyed-out accessibility
toggle that a plain APK download runs into.

Signed APKs are published under
[Releases](https://github.com/subu1979/EasyWhatsapp/releases) — grab
`ImageSender-<version>-release.apk` from the latest tag.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # minified release APK (unsigned)
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:installDebug         # install on a connected device/emulator
```

Toolchain: JDK 17, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.21, compileSdk/targetSdk 36, minSdk 31.
Application id: `com.subu1979.imagesender`. APKs are named `ImageSender-<versionName>-<buildType>.apk`.

## Signing

Release builds are signed with `keystore/release.jks`, whose credentials live in
`keystore.properties` (alias `whatsappdirect`). Both files are gitignored, and `assembleRelease`
falls back to an unsigned APK when `keystore.properties` is absent.

**Back up `keystore/release.jks` and its password.** Losing them means no future update can be
published for this application id — Play refuses APKs signed with a different key.

## Structure

| Path | Role |
|------|------|
| `MainActivity.kt` | Compose entry point |
| `ui/MainScreen.kt` | One-screen UI: country, number, Initiate |
| `ui/CountryPickerSheet.kt` | Searchable country list |
| `ui/MainViewModel.kt` | State, validation and share orchestration |
| `data/CountryRepository.kt` | Country list from libphonenumber's supported regions |
| `domain/NumberValidator.kt` | E.164 normalisation and validation |
| `share/WhatsAppShareManager.kt` | Click-to-chat launch, WhatsApp / Business detection |

## PRD traceability

| Req | Where |
|-----|-------|
| FR-01 global country selector, default +91, search | `CountryRepository`, `CountryPickerSheet` |
| FR-02 no contacts | `AndroidManifest.xml` declares zero dangerous permissions — verified in the release APK with `aapt2 dump permissions` |
| FR-03 image picker | **Delegated to WhatsApp from v3.0.0.** An in-app picker could not attach to an unsaved recipient; WhatsApp's own gallery and camera can |
| FR-04 preview + replace | Delegated to WhatsApp, which previews and replaces before sending |
| FR-05 WhatsApp launch | `WhatsAppShareManager.openChat` (click-to-chat, the only route to an unsaved number) |
| FR-06 WhatsApp Business | `WhatsAppApp` enum + chooser dialog |
| FR-07 errors | `MainViewModel` → `MessageDialog`, strings in `values/strings.xml` |
| FR-08 privacy | No INTERNET permission, no backend, no analytics |

Release APK size: ~2.6 MB (target <10 MB).

## Feasibility research

[docs/autosend-feasibility.md](docs/autosend-feasibility.md) assesses the v4 idea: pressing Send
inside WhatsApp automatically once the photo is attached. Feasible with an Accessibility service and
no AI, gated on one on-device check that must happen before any code.


[docs/feasibility.md](docs/feasibility.md) is the decision record: every route tried on the target
device, why each fails, the off-device alternatives with their real costs, and the constraint that
has to be dropped for any of them to work.

## Sending without WhatsApp on screen

One-tap silent sending needs a server holding a WhatsApp session — see
[docs/openwa-deploy.md](docs/openwa-deploy.md) for a step-by-step deployment on an always-free VM,
including the two risks that decide whether it is worth doing (cold first contact may be dropped by
WhatsApp, and the paired number can be banned).

## Known platform limits

* **Recipient targeting is gated by the address book.** For a saved number WhatsApp opens the chat
  directly; for an unsaved one it falls back to its own picker, which does not list that number.
  Confirmed on-device across every intent permutation, including `jid` combined with WhatsApp's
  private `ContactPicker` component (v1.6.0 intent lab, removed in v2.0.0 once it had answered).
* **An unsaved number is invisible to WhatsApp.** Neither the `jid` extra nor opening the chat via
  `wa.me` puts a new number into WhatsApp's share picker — verified on a Redmi device on 2026-08-23.
  Saving the number as a contact does surface it, but WhatsApp syncs the address book on its own
  schedule, so a contact created seconds earlier is not yet visible; that approach was built in
  v1.3 and removed in v1.5, because saving a contact is the one thing this app exists to avoid.
* **Nothing can send on WhatsApp's behalf.** WhatsApp exposes no on-device send API, so the last
  press always happens inside WhatsApp. `ACTION_SEND` still carries the chat JID as a best-effort
  hint, but current WhatsApp builds ignore it. An Accessibility service that tapped through
  WhatsApp's UI was built and removed in v1.4: it had nothing to tap, since the recipient never
  reaches the picker, and it triggered a Play Protect block on install for no benefit.
  Truly in-app sending needs a server holding a WhatsApp session, or Meta's Cloud API.
* **Whether a number is registered on WhatsApp cannot be checked** from the app. A local check
  needs `READ_CONTACTS` plus a saved contact (FR-02 forbids it); a server check needs the WhatsApp
  Cloud API and a backend (section 8 forbids it). WhatsApp shows its own error after the deep link.
  Instead, the first hand-off of each session shows a "Check the recipient" dialog with the
  formatted number and a note that WhatsApp will report an unregistered user (`ConfirmRecipientDialog`).

## Device verification still required

Smoke-tested on a Pixel emulator (Android 16). The PRD test matrix (section 10) still needs a run on
Redmi Note 9 Pro / MIUI 14 and Redmi 15 5G / HyperOS 3 with real WhatsApp and WhatsApp Business.
