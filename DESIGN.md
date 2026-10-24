# DESIGN.md — Tsuyu design

## Who, and in what moment
People rarely open Tsuyu: once to enable it, then only when an app asks for access or when something stops working. They do it from a couch, about 3 m from the screen, with a 5-key remote, often without knowing what ADB is.

Success means:
- the user enables Tsuyu once, understands what they allow, then forgets about it;
- when an app asks for access, they know who is asking and what it allows;
- when something breaks, the screen tells them what to do.

## Principles
- **One thing to do per screen.** The main action holds the initial focus.
- **State readable from across the room.** Running, stopped, or needs setup: clear at a glance, with no technical detail.
- **Honesty.**
  - Say what will change on the TV (debugging, access Tsuyu grants itself) before doing it, and show it afterwards.
  - Name each access by its effect, not by its technical name.
- **Never a dead end.**
  - Every error gives the likely cause and offers the next action: retry, or open the right setting.
  - Back always returns to the previous screen.
- **Nothing at rest.** No animation or refresh when nothing changes.

## Screens
- **Home:** the service state with its main action (enable, start, retry), « Revenir à <app> » when another app opened Tsuyu, the apps' access, updates, restart preparation and help.
- **Guided setup:**
  - turn on developer options and debugging;
  - confirm "Always allow" on the system prompt;
  - each step shows progress and detects by itself when it is done.
- **Permission request:**
  - opens over the requesting app;
  - shows the app's icon and name, and what the access allows;
  - two buttons, "Autoriser" and "Refuser"; "Refuser" holds the initial focus.
- **Accès des applications:** authorized and refused apps with their status; revoke an access or let a refused app ask again.
- **Mises à jour:** the installed and offered versions, the last check, the release notes as a QR code, automatic updates and prereleases.

The exact scope of each screen is decided in OpenSpec changes.

## Remote navigation
- **Focus:**
  - every screen defines its initial focus;
  - when returning from a sub-screen, focus goes back to the element that opened it.
- **Edges and actions:**
  - no wrap-around at the edges;
  - no hidden long press: secondary actions are visible on screen.
- **Focused element:** it stands out clearly through a slight scale, contrast or shadow, never through a change of its layout size.
- **Back:** leaves a sub-screen; on Home, it exits the app.

## Visual language
- **Reference:** Google Material 3 for TV, laid out in the Penpot file « Tsuyu · Material 3 TV » (pages per journey, shared components on « 01 · Components »). The written specs take precedence over static boards.
- **Palette:** background `#10161E`, surfaces `#1E2936` / `#293645`, primary `#A8D5FF` on `#003353`, text `#E6EDF5` / `#C6D1DF`, outline `#667585`, error `#FFB4AB` on `#493133`.
- **Layout:** state screens put the information on the left and the actions in a right column (about 55/45); permission requests use a centered surface; access rows group the identity, its status and its action.
- **Style:** same family as SygixOs:
  - dark theme only;
  - quiet surfaces;
  - 250–400 ms animations, never an abrupt jump;
  - the system animation scale is respected.
- **Mascot:** a rain-drop ghost holding a D-pad, on black. It is the adaptive launcher icon and, with the Tsuyu name, the Android TV banner and the README logo.
- **Accent color:** a single cool accent evoking rain.
  - It marks focus and the active state.
  - It is never the only carrier of information.
- **Text readable at 3 m:**
  - no body text under 16 sp;
  - titles clearly larger;
  - at most two levels of information per screen.
- **Safe areas:** TV safe areas are respected, so nothing interactive sits in the edges that overscan crops.
- **Components:**
  - built with Compose for TV;
  - shared components (surface, button, list row with state, numbered step) are defined once and reused.

## Copy
- **Tone:** French UI, same tone as SygixOs.
- **Sentences:** short, with action verbs on buttons.
- **Technical terms:** ADB, shell and binder appear only under "Détails".
