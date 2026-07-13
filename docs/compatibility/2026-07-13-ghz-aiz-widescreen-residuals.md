# GHZ and AIZ widescreen residual remediation

Date: 2026-07-13

Scope: Sonic 1 Green Hill background-cache coverage and the S3K AIZ2 post-bombing background-tree entry. Flying Battery and AIZ end-boss debris are excluded.

## Result

`SwScrlGhz#getBgPeriodWidth` is an engine cache-coverage requirement, not the Mega Drive's authored plane period. The engine rebuilds a static background tilemap and samples it across the whole viewport. The old calculation added the native 320-pixel visible span, so its initial 512-pixel cache wrapped inside 528- and 800-pixel viewports. Coverage now uses the configured viewport before retaining the existing power-of-two rounding and 8192-pixel map cap. Results at zero differential scroll are 512 pixels for 320/352/400 and 1024 pixels for 528/800; the 320-pixel result is unchanged.

`AizBgTreeInstance` previously treated screen X=320 as both its spawn origin and hidden-until-entry boundary. It now uses `max(320, viewportWidth)` for both decisions, keeping the tree immediately beyond the right edge at every supported width while preserving the native position exactly.

Neither change adds mutable or rewind state. Focused tests cover 320, 352, 400, 528, and 800 pixels. Existing GHZ scroll and AIZ event tests pass, and GHZ1/GHZ2/GHZ3 plus AIZ trace replay fixtures retain their prior results.
