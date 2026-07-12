# Sonic 1 GHZ, MZ, SYZ, and SLZ Compatibility Remediation

## Scope

This pass audited every implemented act in Green Hill, Marble, Spring Yard, and Star Light for arbitrary sidekicks, widescreen event safety, and cross-game donation. Labyrinth and Scrap Brain remain owned by their separate compatibility workstreams.

## Findings and changes

- GHZ/SLZ breakable-wall debris and MZ smash-block debris used `cameraX + 320` as a render-lifetime edge. At 352, 400, 528, or 800 pixels, visible fragments could disappear inside the viewport. Both fragment families now use the shared live viewport width and height; the 320x224 result is unchanged.
- SYZ bumpers stored one pending player. Four contacts in the same touch pass left only the last actor to bounce. The native main-player slot remains first, while extension contacts are identity-keyed and consumed independently afterward.
- SLZ seesaws represented standing state with one boolean and resolved the launch target as the first playable sprite. A sidekick-only landing therefore launched the main player. Native main state remains first; configured sidekicks now retain distinct standing identities and receive the same ball launch in roster order.
- All new cross-frame playable references are centrally classified as `CAPTURED`. Focused replacement-roster tests prove they encode and restore through `PlayerRefId` rather than retaining stale sprite instances.

## Widescreen event audit

`Sonic1GHZEvents`, `Sonic1MZEvents`, `Sonic1SYZEvents`, and `Sonic1SLZEvents` use ROM world-coordinate camera thresholds for vertical sections and boss progression. These are not visible-screen-edge calculations, so this pass intentionally left them unchanged. Existing level walls and boss locks remain native authored geometry; no code in these handlers derives a lethal boundary from a hard-coded screen width.

## Donation audit

No mandatory spin-dash activation was found in these four zones. GHZ/SLZ wall checks preserve the ROM animation predicate, and every currently supported donor maps canonical roll to native animation `$02`; MZ smash blocks also retain a rolling-state fallback. SLZ spring presentation is now assigned through each active player's canonical animation profile. No donor-identity or zone-specific workaround was added.

## Regression evidence

Focused tests cover main plus three sidekicks, 800-pixel debris lifetime, replacement-instance rewind identity, and both rewind coverage guards. Native trace replay verification covers all twelve acts before integration.
