# MHZ2 floor-grab Y orientation

**Status:** fixed in `cf47c406480af6f40af768e862b487c8f1873e4a`.

## ROM behavior

`CutsceneKnux_MHZ2` calls `sub_65E62/sub_65E72` throughout the press sequence
(`docs/skdisasm/sonic3k.asm:134163-134192`). `sub_65E72` selects animation 5
before the cutscene object's animation frame reaches `$0C`. At `$0C` and later,
it writes `object_control=$83`, selects animation 0, sets `render_flags` bit 1,
and publishes the raw mappings `$B4/$B5` for Sonic or `$A7/$A8` for Tails.
That V-flip is part of the floor-grab pose; the routine does not gate it on
gravity state.

## Engine gap and fix

`CutsceneKnucklesMhz2Instance.updateNativePlayerPressPresentation` already
switched to those raw mappings and full object control at `$0C`, but omitted the
ROM's V-flip write. Sonic therefore stayed upright instead of facing down and
holding the ground. The owner now preserves H-flip and sets V-flip when it enters
the raw-mapping phase. No global gravity behavior is involved.

The regression
`TestMhz1CutsceneObjects.mhz2CutsceneAppliesRomVerticalFlipWhenSonicGrabsTheFloor`
drives the cutscene to the raw mappings and checks both the V-flip and Sonic's
`$B4/$B5` mapping selection. It failed before the fix because the V-flip stayed
clear, then passed with the fix. The MHZ cutscene class and required S3K
load/bootstrap/decoder checks passed together (144 tests, no failures or skips).
