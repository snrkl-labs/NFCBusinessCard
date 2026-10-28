# Changelog

Versions are `X.YY.ZZ`: `YY` moves for new functionality, `ZZ` for fixes.
Releases up to 0.03.06 were debug-signed internal builds; 0.04.00 is the first
signed public release.

## 0.04.00 — 2026-10-28

### Added
- **Published on GitHub, installable and updatable with Obtainium.** Each
  release attaches one signed APK, `NFCBusinessCard-vX.YY.ZZ.apk`, and its tag
  is the version, so Obtainium reads updates straight from the releases page.
  Install steps are in the README.
- **Signed with a dedicated release key.** Every release from here on carries the
  same certificate, printed in the README and in each release's notes, so a
  download can be checked and an update installs over the top.

### Changed
- **Releases are release builds.** Code shrinking and resource shrinking are on;
  earlier releases were unminified debug builds.

### Upgrading from 0.03.06 or earlier
Those builds were signed with a development key, and Android will not install
an update signed with a different one. Uninstall the old build first. Its saved
items do not carry over and need re-entering, or re-importing from Contacts or
a `.vcf` file.

## 0.03.06 — 2026-09-18

### Changed
- **The widget tile is transparent.** It no longer paints a rounded rectangle
  behind itself, so it sits on the wallpaper rather than cutting a panel out of
  it. Each of the NFC and QR buttons now carries its own rounded ground in the
  Material 3 secondary container role — dynamic on API 31+, static below — which
  is what keeps the two marks legible and obviously tappable without a tile
  behind them. The link caption gained a shadow in the opposite tone for the
  same reason.
- **The button marks are tinted from resources** rather than a hardcoded value,
  so they follow dark mode and the wallpaper palette like their new grounds do.

### Added
- **The widget resizes between 5x2 and 3x2 cells.** Below roughly four columns
  the button column is hidden and the card has the tile to itself; everything
  the buttons do is one tap further in from the card. It is still one widget and
  one entry in the picker — two providers could never be resized into one
  another, which is what was asked for.

## 0.03.05 — 2026-09-18

### Removed
- **The NFC section in settings.** Its only row was "Unlock required to share",
  a read-only switch reporting `requireDeviceUnlock` — a manifest attribute the
  platform reads at install time, with no runtime API to change it. A setting
  that can be read and never changed is a row that answers a question nobody
  asked, and the section held nothing else.

  **The behaviour is unchanged.** `requireDeviceUnlock` is still `true` in
  `apduservice.xml`: a tap against a locked phone still does nothing. Only the
  display of that fact is gone. The manifest now carries the reasoning, updated
  to record the decision as settled rather than pending.

## 0.03.04 — 2026-09-18

### Changed
- **The real application icon.** The launcher icon was a placeholder drawn from
  primitives at build time; it is now the operator's artwork, rasterised from the
  layered master into all three adaptive-icon layers at all five densities.

  The themed (monochrome) layer needed work the other two did not. Android keeps
  only the alpha of a monochrome drawable and tints every opaque pixel the same
  shade, so the supplied dark-card-with-white-details artwork would have
  flattened into a featureless slab. Luminance is the only thing separating the
  card from its details, so luminance becomes alpha: the card body stays opaque
  and the details are knocked through to transparency.

### Fixed
- **Two lint warnings introduced in 0.03.03.** The widget's caption nested a
  layout weight inside a weight and left baseline alignment on, both of which
  cost an extra measure pass on every widget update. The card slot is a
  RelativeLayout now. The 0.03.03 release notes claimed lint was clean; it was
  not.

### Tooling
- `tools/split-icon-layers.py` splits the layered icon master into one SVG per
  adaptive layer, switching the designer's magenta guides off by name and by
  colour.
- `tools/generate-launcher-icons.sh` rasterises those to committed PNGs, writes
  the adaptive-icon XML, and calls
- `tools/check-icon-guides.py`, which decodes the rendered PNGs and fails if any
  guide artwork survived.

  None of these run at build time, matching `tools/generate-icon-pngs.sh`: the
  PNGs are committed, so no build host needs rsvg-convert.
- The build-time launcher icon generator is gone from `buildSrc`. There is now
  one source of the launcher icon rather than two.

## 0.03.03 — 2026-09-18

### Changed
- **A link's QR code now has the link text beside it.** A QR is square and the
  card region is 90:55 landscape, so a centred code left most of the card empty.
  The code keeps its square — a stretched QR does not scan — and the link sits
  next to it, so the card says *which* link it will share rather than only that
  it has something to share. Applied in the same way on the main screen and on
  the widget.

  The scheme and any trailing slash are dropped as noise; the path is left
  intact, since that is what tells one of the operator's links from another. The
  rule is shared by both surfaces so they cannot drift apart.

## 0.03.02 — 2026-09-18

From the v0.03.01 hardware test pass.

### Fixed
- **The widget's NFC and QR icons were on their side.** The widget spec called
  for the buttons "rotated 90 degrees clockwise", but that described the
  transform from the portrait mockup rather than the result. The widget is
  already landscape, so the buttons were upright to begin with and rotating them
  put the NFC mark's lettering vertical. The rotation path is removed.
- **A link item showed a specimen business card.** A URL has no details to draw a
  card from, so it fell through to the placeholder — a card-shaped thing carrying
  nobody's information. A link's useful face is its code, so it now shows its QR:
  on the main screen, in Full Card Mode, and on the widget. The same cause left
  the widget's card area blank, since it had nothing to draw at all.

  In Full Card Mode the code is left upright and the hint reads "Scan this to
  open the link" — a QR has no orientation, so offering to flip it would be a
  lie.

### Changed
- **The specimen card now appears only when the library is empty.** It exists so
  a fresh install looks like something rather than an empty rectangle, and with
  nothing added there is no active item, so it cannot be mistaken for a card
  about to be shared. Once anything exists the card view always shows real
  content.

  The full rule: empty library shows the specimen; a contact card draws from its
  own details; a link shows its QR; a supplied image wins over all of them.

## 0.03.01 — 2026-09-11

### Fixed
- **The widget would not have drawn.** RemoteViews cross a Binder transaction
  with roughly a 1 MB budget, shared by everything in the update. The card is
  authored at 1242 × 759, which as ARGB_8888 is **3.6 MB on its own** — three and
  a half times over. The failure mode is a blank widget and
  `!!! FAILED BINDER TRANSACTION !!!` in the log, not an exception the code could
  catch.

  The card is now capped at 480 px wide (about 549 KB) and the button icons at
  96 px square (about 36 KB each), for roughly 621 KB total. The card is upscaled
  slightly by the launcher as a result, which is the right trade for a glanceable
  tile — tapping it opens the crisp 1:1 version, which is where legibility
  actually matters.

  Caught by calculation immediately after tagging v0.03.00, before the operator
  had spent a test cycle on it. v0.03.00's widget should be considered broken.

## 0.03.00 — 2026-09-11

From the v0.02.02 hardware test pass. Seven items raised, seven addressed.

### Added
- **5×2 home screen widget.** The active card in landscape with NFC and QR
  controls beside it. Unlike Full Card Mode in the app — where the phone stays
  upright and the card turns — the widget is already landscape, so the card reads
  normally and the buttons are rotated a quarter turn. `RemoteViews` cannot
  rotate a view, so the button images are rotated as bitmaps.

  Three tap targets: the card opens Full Card Mode, NFC opens it with send mode
  already claimed, QR goes straight to the code. The destination is set as
  initial state rather than navigated to afterwards, so a widget tap does not
  show the main screen for a frame first. It redraws from the same signal that
  updates the NFC payload, so it cannot display a card the operator has switched
  away from — `updatePeriodMillis` is zero, so nothing polls.
- **A generated card.** With no supplied artwork the app now draws a card from
  the vCard's own name, title, organisation, first phone, email and website, with
  a circle-cropped photo where one exists. Rendered at the published pixel
  dimensions, so it displays 1:1 like supplied artwork. Text shrinks to fit
  rather than truncating: a card that abbreviates a surname is worse than one set
  a point smaller. Replaces the pro-forma placeholder, which had done its job
  once the geometry was proven.
- **Swipe between items on the QR screen.** The details panel cycles through the
  library, for sharing a link first and a contact card second. It sets the Active
  Item, so NFC and the QR cannot disagree about which card is showing.
- **Contact photos on import**, from the Contacts picker and from an embedded
  `PHOTO` in a `.vcf`. Copied into app storage, because the original reference
  dies once the grant lapses. Display-only, as before.

### Fixed
- The FAB sat over the delete button of the card behind it — the worst available
  mis-tap in a list of saved cards. Now centred, with the list padded so the last
  card clears it.
- Long email addresses wrapped mid-string on the QR screen. An email has no
  spaces to break at, and a two-column row left it two-thirds of the width. The
  label now sits above the value, which gets the full row.

### Investigated
- **vCard share from Contacts is not truncated by AOSP.** Driving the real share
  sheet on API 34, the received card contained `N, FN, TEL×2, EMAIL, ADR, ORG×2,
  TITLE×2, NOTE` — and all of it was stored. The limitation is this app's data
  model, which holds one address, one website, and no middle name, prefix,
  suffix, nickname or birthday. Awaiting word on which fields were missing before
  widening the schema. The importer now logs which properties arrived — names
  only, never values.

## 0.02.02 — 2026-09-10

Driven by operator testing of v0.02.00.

### Fixed
- **"Copy from Contacts" never worked.** The system contact picker grants
  temporary read access to *the exact URI it returns* — and only that URI. The
  first implementation derived `Contacts.CONTENT_VCARD_URI` from the picked
  contact's lookup key; the second appended `Entity.CONTENT_DIRECTORY`. Both are
  different URIs, outside the grant, and the provider refused them:

      Permission Denial: reading ...contacts/lookup/<key>/2/entities
      requires android.permission.READ_CONTACTS, or grantUriPermission()

  Without that permission a contact's display name is readable and essentially
  nothing else — no phone numbers, emails or organisation. `READ_CONTACTS` is now
  declared and **requested on demand**, only when "Copy from Contacts" is used,
  never at launch. On GrapheneOS, Contact Scopes can narrow the grant to a single
  contact.

  The failure was also silent: `runCatching{}.getOrNull()` swallowed the
  exception, so a refused read and an empty contact reported identically and left
  nothing in logcat. Failures now carry a reason and are logged.
- **The phone and email fields were too narrow to read their own contents.** A
  number would display clipped, with the leading `+` scrolled out of view. The
  type selector was a full outlined text field at a fixed 132dp with its own
  floating label, taking roughly a third of the row for a four-value choice that
  is usually left alone. It is now a compact chip and the value field takes the
  space.
- **Settings described the card image as "Using the placeholder"** after real
  sample artwork became the default. Now "Using the built-in card".

### Added
- **Share a contact into the app.** `Contacts → Share → NFC Business Card`
  imports directly, as does opening a `.vcf` from a file manager. **Neither needs
  any permission** — the sharing app grants read on the exact URI it hands over,
  which is sufficient precisely because nothing derives a different URI from it.

### Changed
- The add menu reads **Create a link** and **Create a vCard**, alongside **Copy
  from Contacts** and **Import a .vcf file**, so creating and importing are
  visibly different verbs. "Add contact card" had read as importing *from*
  Contacts, particularly once "Copy from Contacts" sat directly beneath it.
  Editor titles follow: "New vCard" / "Edit vCard".
- The built-in card image is the operator's sample artwork at 1242 × 759.

## 0.02.01 — 2026-09-10

Licensing and attribution. No functional change.

### Added
- **Apache License 2.0.** The project is now licensed. The README previously
  said "Personal project. No licence granted", which was a scaffolding
  placeholder, not a decision — and it would have made the project unusable by
  anyone, since without a licence default copyright forbids use, modification
  and redistribution. Apache 2.0 over MIT because the dependency stack is almost
  entirely Apache 2.0 and it carries an explicit patent grant that MIT does not.
- **`NOTICE`** with third-party attribution, bundled into the APK as
  `res/raw/notice.txt` and readable from **Settings → Open source licences**.
  Apache 2.0 requires attribution notices to accompany redistributed code, and
  an APK redistributes it — a notice living only in the repository would reach
  people reading the source and nobody who installed the app.
- **MIT licence text for the vendored `material-3` agent skill**, at
  `.agents/skills/material-3/LICENSE`, taken verbatim from upstream. MIT
  requires the notice be retained in redistributions, and the skill's files are
  tracked in this repository.
- **`docs/publishing.md`** covering what differs between the internal Forgejo
  repository and a public GitHub one.

## 0.02.00 — 2026-09-10

Driven by on-device testing feedback. Every item below came from the operator's
issues log after the v0.01.01 hardware test pass.

### Added
- **Full Card Mode.** Tapping the card region opens it at a quarter turn inside
  the portrait window, so someone standing beside you can read it. Tapping again
  flips it to the other quarter turn, to aim it left or right. The activity does
  **not** rotate: the controls stay upright and under the operator's thumb, and
  there is no relayout mid-conversation. Send mode and the QR view are reachable
  from two large buttons below the card.
- **The QR view says what it is sharing.** A panel beneath the code lists each
  field and its value, and updates as the detail level changes. A QR is opaque —
  handing someone a phone without knowing whether the code carries a mobile
  number or a home address is a real privacy problem, and it worsens as the
  levels get denser. The list is derived from the same selection the encoder
  uses, so it cannot drift from the payload.
- **Photos on contact cards.** Display-only, and never transmitted. See below.
- **Import.** A contact card can be loaded from a `.vcf` file or copied from the
  device's Contacts. The contacts path uses the system picker's temporary grant
  on the single chosen contact, so **no `READ_CONTACTS` permission is requested**
  — asking for the whole address book to import one card would be a far larger
  request than the feature justifies.
- **Tap confirmation.** Optionally flash the screen, play a sound, or vibrate
  when a reader has taken the card. Each is independently switchable. Detection
  is real rather than assumed: the service reports a read only once the reader
  has consumed the whole NDEF file.

### Changed
- Name, organisation, job title and address fields capitalise as you type. Email,
  URL and phone fields deliberately do not.
- The card image ships real sample artwork at 1242 × 759 instead of the
  measurement placeholder. The placeholder is still generated for the ISO and US
  ratios, where no sample artwork exists.

### Fixed
- The on-screen keyboard covered the lower fields of the contact card editor,
  and they could not be scrolled into view.
- The QR code was clipped by the new detail panel. A clipped code is an
  unscannable code; the code now sizes to the space actually available and the
  panel scrolls.

### Notes on the photo
A photo can never go into a QR code: base64 image data is precisely what the
detail levels exist to fight. It is also kept out of the NFC payload. A photo
takes a card from a few hundred bytes to tens of kilobytes, and at NFC's roughly
106 kbit/s that turns an instant tap into seconds of held contact, which many
readers abandon part way — saving nothing and reporting nothing to either party.
Anyone who wants the photo gets it by following the hosted vCard link.

### Database
Schema version 2. A real migration adds the photo column; existing items are
preserved.

### Testing
122 tests, up from 104. New coverage for the vCard parser, the shared-field
summary, and the rule that a photo never reaches the wire.

## 0.01.01 — 2026-09-10

### Fixed
- **Card image geometry.** The image was laid out with `fillMaxWidth()`, which
  constrains width but leaves height free, so the image node kept the bitmap's
  intrinsic *pixel* height while its width stretched to the parent. The node was
  therefore a different shape from the card region containing it, and the image
  did not fill that region at any density other than 3.0.

  It happened to draw at exactly 1:1 on the target Pixel 8 Pro, but only because
  `ContentScale.Fit` computed a scale of exactly 1.0 by coincidence — the
  guarantee the published artwork dimensions rest on was resting on luck.

  The region's width cap is now expressed in **pixels** converted to dp at
  runtime rather than a fixed 414dp. Capping in dp yields 1242px only at density
  3.0; on a 560dpi device 414dp is 1449px and the region would have upscaled the
  bitmap — softening precisely the small type this view exists to keep legible.

  **Artwork dimensions are unchanged: 1242 × 759 for the default 90 × 55 card.**
  The fix makes them hold on any device rather than only on the target one.

### Testing
- Compose UI tests added for the main screen, settings, QR view, card face and
  item editor, and run under Robolectric in the ordinary unit test gate. They
  found the card image defect above.
- These deliberately do **not** require an emulator. An unattended build loop
  should not depend on one: it is slow to boot, it is the largest consumer of
  memory on the build host, and under memory pressure it is what gets killed —
  taking the test run with it.
- Test count: 75 → 104.

## 0.01.00 — 2026-09-10

First build.

### Sharing
- Share a link or a contact card over NFC by holding the phone against a reader,
  presenting as an NFC Forum Type 4 tag.
- Shareable item library with two item types: links (`https://` URLs) and
  contact cards (structured vCard fields).
- Pick the active item in one tap from the main screen.
- Send mode toggle, claiming foreground NFC card-emulation routing while the app
  is open.

### QR code
- Full-screen QR view of the active item, generated at runtime.
- Four detail levels — link only, minimal, standard, full — swipeable, so a code
  that will not scan can be made sparser.
- The detail control collapses for link items, which have only one form.
- On-screen brightness slider, and the screen is held awake while a code shows.
- Error correction level L throughout: on a clean screen, larger modules beat
  damage tolerance.

### Card image
- Card image region at business-card proportions, defaulting to Australian
  90 × 55 with ISO and US alternatives.
- Ships a pro-forma placeholder carrying a border, corner-to-corner diagonals and
  its own pixel dimensions, so layout and resampling faults are self-evident.
- Your own PNG can replace it from settings.

### Other
- Material Design 3 throughout, with dynamic colour on Android 12+.
- Settings screen covering the item library, card image and shape, QR defaults
  and brightness.

### Known limitations
- Send mode cannot persist while the app is backgrounded. `setPreferredService`
  requires a resumed activity; this is an Android constraint, not a choice.
- Unlocking is required before a tap is answered (`requireDeviceUnlock="true"`).
- Lock-screen presentation is not implemented.
- No NFC behaviour has been verified on hardware by the build. See the release
  notes' operator test checklist.
