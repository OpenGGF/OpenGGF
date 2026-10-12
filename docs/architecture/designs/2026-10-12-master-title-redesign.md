# Master title redesign: cartridge shelf, Game Setup and tabbed Settings

Branch `feature/ai-master-title-redesign`, based on `develop` at `f02bdbfd57`.

## Problem

The two-pane hub listed per-game and engine-wide actions together, packed the
carousel, profile status and mods ticker into one corner, and showed only "3
changes" for the selected game's profile. The same settings lived in up to three
places: global keys, per-game Launch Options, and the raw `launch.*` keys under
Settings > Launch. A master-title launch always applied the per-game rewind, debug,
cross-game and character values, so the global Rewind and Debug switches had no
effect on hub launches. Launch Options saved on Enter/Tab while Settings and Mods
used a draft; `*` meant "not stock" in one screen and "unsaved" in another. Settings
showed four rows per page and spread hotkeys over five sections.

## Decisions

- **Layout:** a cartridge shelf (selected game centred, neighbours receding), with
  per-game actions under the card and engine-wide actions as header tabs.
- **Setup chips** state what Start will launch. Amber means "set for this game only".
  Settings does not list which games override a value; that is visible at launch
  instead.
- **Default/On/Off:** Rewind and Debug tools gained a `global` value
  (`LaunchToggle`); aspect already had one. `LaunchProfileApplier` applies no
  session override for `global`. Legacy booleans migrate in
  `ConfigMigrationService.migrateLaunchProfileToggles` (`true` to `on`, the old
  stock `false` to `global`).
- **Borrow from** names cross-game donation in the UI. Cross-game and Players stay
  per-game only; the global `crossGame.*` and `characters.*` keys only matter for
  direct launches and moved to Advanced settings.
- **One draft flow:** Settings, Game Setup and Mods ask Save / Discard / Keep
  editing on Back with unsaved changes, Save first.
- **Footers** carry only screen-wide controls as key caps; row-specific keys sit in
  the description box; status (unsaved count) sits in its own box on the left;
  Up/Down is never labelled.

## Rejected

- Sidebar hub, level-select grid and attract-screen layouts (round 1 mockups): the
  shelf read best as a game picker and scales to many standalone mod games. The
  level-select grid lost the ROM logos; the attract screen hid Mods and Settings a
  level deeper.
- Listing the games that override a setting on Settings rows (round 2 mockup):
  rejected as cluttered; overrides are shown on the hub chips and in Game Setup.
- "Sprite Donation" as the cross-game label: too narrow, since donation also covers
  spindash, sounds, Super Sonic handling and the S3K save screen.
- A per-frame cache of the hub chips: tests (and any caller that changes ROM
  availability without advancing a frame) saw stale chips; reading the saved
  profile on demand is cheap.

## Verification aids

`MasterTitleCaptureTool` renders every hub, Game Setup and Settings scene offscreen;
see [agent workflow tools](../../agent-workflow/README.md).
