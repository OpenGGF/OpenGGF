# S3K Launch Base residual compatibility remediation

Date: 2026-07-13

Scope: `LbzLoweringGrappleObjectInstance`, `LbzRollingDrumInstance`, `LbzExplodingTriggerInstance`, and directly coupled LBZ event semantics. Flying Battery Zone is explicitly excluded.

## Result

| Area | Multi-sidekick and rewind | Widescreen | S1 donation |
| --- | --- | --- | --- |
| Lowering grapple | Native P1/P2 arrays remain the first two update passes. Later sidekicks receive identity-owned grab/cooldown state. Promotion, demotion, omission, death, unload, and compact PlayerRef replacement retain or release the correct actor; cleanup does not clear control whose presentation has moved to another owner. | Capture uses an object-local world rectangle. The X-only ROM sprite lifetime remains independent of viewport height and behaves consistently at 320/352/400/528/800 widths. | Capture and jump release do not require spin dash. |
| Rolling drum | Native P1/P2 riding flags and LBZ runtime angle bytes remain first. Extension riders retain independent identity-owned riding/angle state, including drum-to-drum handoff, roster changes, death, unload, and PlayerRef replacement. Cleanup refuses to clear a different object's live latch. | Drum bounds come from subtype/world position; no screen-width threshold is involved. | Entry is position/velocity driven and does not inspect spin-dash state. |
| Exploding trigger | Native collision-property bits still process P1 then P2. Extension touch identities are retained independently and survive roster promotion/demotion and compact rewind until the next object pass consumes them. Omitted/dead actors cannot trigger. | Touch geometry is object-local; no camera-width gate exists. | ROM activation requires the rolling animation, not a spin-dash input. S1 characters can enter rolling through ordinary terrain/object routes, so no donor-only workaround is justified. |
| LBZ events | Progression remains main-player/camera driven; seamless transition player offsets are delegated to the shared team-aware transition executor. | Camera locks and launch coordinates are ROM-authored world positions. Expanding them with viewport width would change arena/launch timing. | No directly coupled event gate reads spin-dash capability. |

## Verification

Focused tests cover main plus three sidekicks, native-first behavior, reorder, omission, death, unload, unrelated-control protection, replacement PlayerRefs, exact non-default grapple/drum map values restored into recreated objects, and 320/352/400/528/800 viewports. Both rewind coverage guards pass.

The LBZ complete-run trace intentionally remains the known red. Exact base and post-change runs both report 5,881 errors with first divergence at frame 2,270: `tails_x` expected `0x04E1`, actual `0x04E0`. No reference data or trace-to-engine hydration changed.
