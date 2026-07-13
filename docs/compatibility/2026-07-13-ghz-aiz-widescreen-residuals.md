# GHZ and AIZ widescreen residual remediation

Date: 2026-07-13

Scope: Sonic 1 Green Hill background-cache coverage and the S3K AIZ2 post-bombing background-tree entry. Flying Battery and AIZ end-boss debris are excluded.

## Result

`SwScrlGhz#getBgPeriodWidth` is an engine cache-coverage requirement, not the Mega Drive's authored plane period. The engine rebuilds a static background tilemap and samples it across the whole viewport. The old calculation added the native 320-pixel visible span, so its initial 512-pixel cache wrapped inside 528- and 800-pixel viewports. Coverage now uses the configured viewport before retaining the existing power-of-two rounding and 8192-pixel map cap. Results at zero differential scroll are 512 pixels for 320/352/400 and 1024 pixels for 528/800; the 320-pixel result is unchanged.

`AizBgTreeInstance` previously treated screen X=320 as both its spawn origin and hidden-until-entry boundary. It now uses `max(320, viewportWidth)` for both decisions, keeping the tree immediately beyond the right edge at every supported width while preserving the native position exactly. A rewind-captured initialization gate also keeps a newly allocated deferred dynamic slot hidden until its first position update, preventing a one-frame flash when rendering precedes that slot's first execution.

GHZ adds no mutable state; the AIZ initialization flag follows the existing generic rewind field path. Focused tests cover 320, 352, 400, 528, and 800 pixels, including render-before-first-update order. GHZ coverage tests pin the 512/513-pixel power-of-two boundary for camera and cloud differential plus the 8192-pixel source cap. Existing GHZ scroll and AIZ event tests pass, and GHZ1/GHZ2/GHZ3 plus AIZ trace replay fixtures retain their prior results.
