# Sonic 2 EHZ/WFZ/DEZ compatibility remediation

Scope: implemented Emerald Hill, Wing Fortress, and Death Egg content, plus the final mandatory-traversal scan.

| Area | Multi-sidekick result | Widescreen result | S1 donation result |
|---|---|---|---|
| EHZ log bridge | Native P1/P2 depression inputs remain first; later riders retain identity-owned log indices and participate in the shared bridge shape. Omission/death naturally drops their live standing entry and compact rewind restores replacement PlayerRefs. | Bridge collision and deformation use world positions and log geometry, with no camera-width threshold. Safe at 320/352/400/528/800. | Standing and running are sufficient; no spin-dash fallback is required. |
| WFZ palette switcher | Native scalar crossing bytes bind to stable owners across reorder; later players use the existing identity map, with omitted state pruned and replacement identities restored by rewind. | Trigger X/Y bounds are world coordinates and do not depend on viewport width. | Positional crossing only; no spin-dash dependency. |
| DEZ final transition | The ROM-authored main-player ending walk remains authoritative. Valid sidekicks are independently contained and forced right; dead, hurt, debug, omitted, unloaded, or unrelated-controlled players are released or left untouched. | DEZ event and boss thresholds are arena/world coordinates and must not expand with the viewport. Team ground containment prevents ultrawide camera exposure from dropping extras into the pit. | The ending walk is scripted by the boss, so donated movement capabilities need no fallback. |
| Remaining traversal scan | Obj85 launcher springs and MTZ subtype-3 proximity platforms now process configured extensions after the native P1/P2 prefix. Badnik `NATIVE_P1_P2` targeting policies are combat selection rather than mandatory traversal ownership and remain unchanged. | No new screen-width constants were found. | Both mechanisms are standing/contact/proximity driven. |

Trace coverage: EHZ1, WFZ, and DEZ-ending fixtures are green at baseline. No EHZ2 trace replay fixture exists in the repository, so EHZ2 trace parity cannot be claimed by this audit; this is an explicit fixture gap rather than a waived failure.
