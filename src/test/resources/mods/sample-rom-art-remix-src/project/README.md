# ROM Art Remix

A minimal Sonic 2 patch mod showing how to request a bounded art, mapping, and DPLC
window from the user's ROM. The packaged mod contains only original level data,
metadata, and Java code; the Tails sheet is materialized in memory at launch.

The level uses the stock default team and contributes no launch-team, input-filter,
or HUD policies. `TailsFlightArtObject` only displays frames 94 and 95 and never
controls or hides the playable character.

The [source-first ROM-art remix guide](../../../../../../docs/modding/guides/rom-art-remix.md)
explains the bounded addresses, launch-memory materialization, DPLC ordering,
decoded-pattern probe, rewind scalar, and final package inspection.
