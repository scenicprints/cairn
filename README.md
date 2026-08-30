# Cairn

A home launcher for a Pixel 8 Pro. Kotlin, Jetpack Compose, one module.

Ordinary on the surface, unusual underneath. The layout is a conventional grid because every
launcher that competes on being weird about arranging squares is a two-week novelty. The
ambition goes into what it can do.

## The design

Achromatic chrome. Grey, black, white, and one accent used once, for the alarm mark beside the
clock. The app icons are already a riot of colour they did not ask permission for, so the
launcher's own furniture stays out of the way.

**No long press anywhere.** A long press is a timer with no face on it: nothing tells you how
long to hold, and nothing distinguishes "still waiting" from "it did not take". Cairn replaces
it with a pull. Drag an icon down and what is inside it comes out as far as you pull, tracking
your finger, reversible at every point.

- A folder gives its apps.
- A messaging app gives the message and a reply field.
- Anything else gives its deep shortcuts.

One gesture, one meaning, everywhere.

**Levels, not badges.** The rule under an icon is a length, not a dot. A badge tells you
something exists; a length tells you how much. It is fed generically rather than per app:
media position comes from the `MediaSession` token, and download or sync progress comes from
`EXTRA_PROGRESS` on the notification itself, so any app posting a progress notification drives
its own rule with no code written for it.

**Captions carry information or they are absent.** An icon's label is the app's name until
something is waiting, and then it is the sender's name. Dock icons have no captions at all
until they need one.

**Nothing repacks itself.** A gap you left in the grid stays a gap. The grid never reorders.

## Structure

Unlimited home pages, one of which you mark as home; the home button returns there. The page
indicator is a hairline with a moving segment rather than dots, because dots stop being
readable past about eight. The drawer holds everything, tracks your thumb the whole way up, and
has no search bar: the keyboard is already rising, and the keyboard is the place you type, so
you just type and the list filters. Across the top of the drawer, one row toggling between
recent, new, and frequent.

Real third-party widgets via `AppWidgetHost`, snapped to the same four-column grid as the
icons. The Google Calendar widget is the acceptance test.

## Permissions it asks for, and why

| Permission | Granted where | Without it |
|---|---|---|
| Notification access | Settings, Notifications, Device and app notifications | No replies, no captions, no levels |
| Usage access | Settings, Special app access | Recent and frequent fall back to alphabetical |
| Install unknown apps | Prompted by the updater | Cannot self-update |

Notification access is revoked on reinstall, so it needs re-granting after an update.

## Building

There is no Gradle wrapper jar in the repo. CI provides Gradle. Everything is built by GitHub
Actions and installed from the release it publishes.

Signing is optional. Set `CAIRN_KEYSTORE_BASE64`, `CAIRN_STORE_PASSWORD`, `CAIRN_KEY_ALIAS`,
and `CAIRN_KEY_PASSWORD` as repo secrets and the release APK is signed with a stable key, which
is what lets it upgrade in place. Without them the build still produces an APK, signed with the
debug key, which will not upgrade an existing install.

## Updating

The launcher reads the latest GitHub release, downloads the APK, and fires the install intent.
Tags are `v<run number>` and the updater reads the digits out of the tag as the version code.

**You are updating the app that is currently your home screen.** Installing over yourself kills
the process, so the home screen goes blank for a second and Android restarts it. That is
expected. A bad build leaves you with a dead home screen until you can reach Settings, so keep
Pixel Launcher installed as a fallback.

## Editing

There is no long press anywhere, so editing is entered by direction instead of by duration.

- **Drag an icon sideways** and it lifts under your finger. Drop it on an empty cell to move it,
  on another icon to make a folder of the two, on a folder to add it, on the dock to dock it, or
  on the word **Remove** at the top of the screen, which only appears while something is in the
  air.
- **Carry it to the screen edge and wait** and you cross to the next page.
- **Pull a folder open and drag a child out** of the panel to take it back out. A folder that
  drops to a single app becomes that app again.
- **The folder's name is editable in the panel**, in place.
- **Pull a widget down** to get resize bars on its right and bottom edges. Dragging them moves
  the span by whole cells, and the widget is told its new box in dp so it re-lays itself out
  rather than being stretched.
- **Pinch in** for the page overview: jump to a page, reorder, set which one is home, delete
  one, or add one.

## Known gaps

None of this has been compiled yet. There is no JDK or Android SDK on the machine it was
written on, so the first syntax check happens on the CI runner and the first few builds are
likely to be compile fixes.
