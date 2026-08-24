# WhatsApp Direct – Image Sender (MVP v1.0)

Android utility that sends an image to a WhatsApp number **without saving it as a contact**.
Built from `WhatsApp_Direct_Image_Sender_PRD_v1.0.docx`.

## Download

Signed APKs are published under
[Releases](https://github.com/subu1979/EasyWhatsapp/releases) — grab
`ImageSender-<version>-release.apk` from the latest tag.

### Play Protect

The app declares an Accessibility service (the optional auto-send helper), so Play Protect blocks
the sideloaded install with "App blocked to protect your device". Two ways past it:

```bash
adb install -r ImageSender-<version>-release.apk   # adb installs are not gated
```

or on the phone: Settings → Google → All services → Play Protect → gear icon → turn off
**Scan apps with Play Protect**, install, then turn it back on. Installed apps keep working.
Some builds offer **More details → Install anyway** in the dialog itself.

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
| `ui/MainScreen.kt` | One-screen UI, alerts, app chooser |
| `ui/CountryPickerSheet.kt` | Searchable country list |
| `ui/MainViewModel.kt` | State, validation and share orchestration |
| `data/CountryRepository.kt` | Country list from libphonenumber's supported regions |
| `domain/NumberValidator.kt` | E.164 normalisation and validation |
| `share/WhatsAppShareManager.kt` | ACTION_SEND / wa.me launch, target detection |
| `share/ImageStore.kt` | Preview decoding, FileProvider fallback copy |

## PRD traceability

| Req | Where |
|-----|-------|
| FR-01 global country selector, default +91, search | `CountryRepository`, `CountryPickerSheet` |
| FR-02 no contacts | **Overridden 2026-08-23.** WhatsApp only offers numbers present in the address book, so `ContactBridge` creates the recipient as a local contact for one send and deletes it on return; `cleanUpLeftovers` clears one left by a crash. Verified by `ContactBridgeTest`. |
| FR-03 image picker | `ActivityResultContracts.PickVisualMedia` in `MainScreen`; camera capture via `ActivityResultContracts.TakePicture` into `ImageStore.createCaptureUri` (no `CAMERA` permission needed) |
| FR-04 preview + replace | `ImageSection` in `MainScreen` |
| FR-05 WhatsApp launch | `WhatsAppShareManager.shareImage` (ACTION_SEND, content URI, grant flag) |
| FR-06 WhatsApp Business | `WhatsAppApp` enum + chooser dialog |
| FR-07 errors | `MainViewModel` → `MessageDialog`, strings in `values/strings.xml` |
| FR-08 privacy | No INTERNET permission, no backend, no analytics |

Release APK size: ~2.6 MB (target <10 MB).

## Known platform limits

* **An unsaved number is invisible to WhatsApp.** Neither the `jid` extra nor opening the chat via
  `wa.me` puts a new number into WhatsApp's share picker — verified on a Redmi device on
  2026-08-23. The address book is the only route, hence the temporary contact.
* **Nothing can send on WhatsApp's behalf.** WhatsApp exposes no on-device send API, so the last
  press always happens inside WhatsApp. To keep that to a single tap, `ACTION_SEND` carries the
  chat JID (`<digits>@s.whatsapp.net`) so WhatsApp opens on the right conversation with the photo
  already attached. That extra is undocumented — PRD section 7 excluded it, and the product owner
  overrode that on 2026-08-23 — so it is used as an optimisation only: if WhatsApp rejects the
  intent, the same share is retried without the extra and WhatsApp asks for the recipient.
  Truly in-app sending would require the WhatsApp Cloud API (business sender number, a backend,
  paid conversations) or an Accessibility service that drives WhatsApp's UI (Play-policy banned).
* **Whether a number is registered on WhatsApp cannot be checked** from the app. A local check
  needs `READ_CONTACTS` plus a saved contact (FR-02 forbids it); a server check needs the WhatsApp
  Cloud API and a backend (section 8 forbids it). WhatsApp shows its own error after the deep link.
  Instead, the first hand-off of each session shows a "Check the recipient" dialog with the
  formatted number and a note that WhatsApp will report an unregistered user (`ConfirmRecipientDialog`).

## Device verification still required

Smoke-tested on a Pixel emulator (Android 16). The PRD test matrix (section 10) still needs a run on
Redmi Note 9 Pro / MIUI 14 and Redmi 15 5G / HyperOS 3 with real WhatsApp and WhatsApp Business.
