# Mutator Lab: Sonic 2 Emerald Hill Act 1

Original implementation base: `6d817a9d74f135714f3da59ab9aab156cc09473e`.
This is an opening-play prototype, not certification of the entire stock act.
The [implementation plan](../../plans/2026-10-07-mutators-prototype-implementation.md)
and [creator guide](../../../modding/guides/mutators.md) own delivery and authoring.
The earlier integration base is `37a57ebdbe62864737f39e7b14c72932fbaf3d74`.
Its intent-merged 16-class focused invocation 35517 passed 106 cases, zero
failures/errors/skips, including the native revision/recreation replay and
stock power-up music rules. Captures below use normal compiled merge source
`157401f252` against 2fc; the subsequent 37a merge changes tools/docs only.
Those historical checks are supplemented by the completed composed evidence below.

The composed repair source `0a874c2371` contains published `02796b4` and Sitar's
SMPS/scene contracts. Its fresh 2,077-frame GPU/state/PCM walkthrough observes
title, help (620), settings, native animation (1,130 → 1,135), Stealth (1,360),
resume and restart. The real Engine window capture observes play, both LIVE
edits, restart, hub return and repeated launch, with close zero and all owned
cleanup. That run required an exact owned frameless window mapping workaround;
the fresh default-WM attempt did not become viewable within 90 seconds. Device
monitor PCM has varying AC output around navigation and gameplay, including
producer rebuilds. Physical speaker output and an isolated SFX waveform are
not certified. A separately inspected native help image shows the help page;
its synthetic Escape snapshot stayed there, so it is not evidence of Back.
Both actual fixed keyboard Back edges pass the controlled production-menu test.

After those captures, published `33d3976c5` adds optional capture pixel readback
and fixture cleanup. Intent composition retains title drawing in the new
draw-only method. Six native title/level/results pixel checks, two controlled
help Back cases and 31 API cases pass (39 total, zero skips). Fresh compiled
candidate export remains byte-identical, 20,392 lines. Earlier source evidence
is retained under its original commit; required normal trace profile and final
combined ordinary/fresh guards against actual destination remain pending.

## Supported cell and obligations

Sonic 2 World REV01, native 320×224 logical viewport, solo Sonic, donor off,
Emerald Hill Act 1. Gravity affects dry ordinary airborne acceleration only.
Jump impulse, hurt, death, water, flight and scripted movement retain native
behavior. Stealth filters the player's immutable body/appendage presentation;
attached effects are optional. Objects, collision, targeting, audio and HUD
remain native. Deposited skid puffs are world effects.

| Boundary or route | Evidence obligation | Current evidence |
| --- | --- | --- |
| Native opening run/jump | Stock-off movement identity; 25–200% dry gravity; real ROM terrain and object updates | Gameplay child: 243 unaffected cases plus seven repaired fixtures, zero skips. Final `157401f252` GPU frames 1,130/1,360/1,570 and native window show real run/jump at requested 50% dry Gravity; broad final-source regression pending |
| Title → configure → play | Actual title/help, slider/checkbox/enum, load transition and native rendered play | Final normal-source 2,077-frame GPU+PCM capture and 98-action native-window walkthrough; actual title/options images inspected at logical 320×224 |
| Play → configure → Resume | No simulation during hold; explicit LIVE publication; simultaneous host Escape/Start, window/user pause, focus and frame step | Final CSV holds 1,218–1,254 and 1,467–1,514 preserve position/velocity/camera, resume revisions 2/3. Native focus return exercised. Focused host regressions passed; final broad pending |
| Configuration during fade | Retain command, visible waiting feedback, apply once after the active fade | Focused host/fade repairs passed in invocations 70179, 50600 and final affected-case 83824, zero skips; acknowledgment follows the finishing fade frame |
| Restart and new launch | Qualifying full restart, repeated native assembly, clean hub retirement, requested preferences independent of historical state | Final GPU title card 1,800 and fresh level 2,076; native restart, hub, two fresh launches, title Back and close-zero with all owned cleanup successful |
| Rewind across edits | Complete registry restore, recreated roster, forward replay across two effective revisions | Earlier integrated ROM-backed registry regression passed; contract child 45 cases, zero skips. Final merged broad reruns the complete affected class |
| Fault/recovery | Typed preparation abort, quarantine outside history, rejected graph keeps old revision, teardown attempts native owners | Contract/core cases passed. Actual-store failure/retry tests preserve old admission/effective revision and visible draft; 50600 20 cases and 83824 two affected cases passed, zero skips. Helper five failure-injection checks passed |
| Stock trace/time attack/movie | External-content exclusion and no configuration interception of movie input | Existing owners preserved; both final EHZ1 trace segments passed in the 21-case domain invocation (three separately matched inherited S3K divergences, zero skips). No complete stock act or recording-route certification |

## Explicit limits

No EHZ act-clear or EHZ1 → EHZ2 route is claimed. Outside EHZ1, the prepared
settings remain visible but effects suspend, and configuration reports that
cell. Other characters, teams, donors, water Gravity, wider viewports and
special/bonus stages are not qualified by this prototype. The schema's
leader/all-team enum is usable authoring data; both values select the same
solo leader in the advertised cell.

Native SAT admission, priority and mask behavior happen before selective
presentation suppression. Headless tests alone do not prove that visual or
audio output is polished. `GameplayCaptureTool --title-screen --audio`
drives the real title/level owners, but its headless boot lacks the engine's
game-hub callback; hub/relaunch evidence must come from the actual engine
window. Captures belong in the task's outside-repository directory.

Inherited stock-act coverage remains in this backlog. Future breadth must
add per-character, donor, viewport, event/load and rewind evidence instead of
promoting this opening sequence into a full route claim.


## Composed implementation evidence

Engine source `0a827ab96a` at base `33d3976c53` completed the full change-based
ordinary selection: 26,458 tests, 28 fully matched inherited assertions, zero
errors and 62 identical baseline skips; fresh guards 672 pass, zero skips.
All previous nine additional failures/twelve changed assertions/three guards
are resolved. Normal `trace-replay` has 18 passes and three literally matched
inherited S3K assertions, no skips; both EHZ segments pass. The complete
[implementation evidence](../../plans/2026-10-07-mutators-prototype-implementation.md#verified-implementation-handoff-2026-10-08)
records command, source and comparison limits.

Published 09cf ancestry is retained by `d4dc7a8e91`; its Sitar example-only
changes leave engine/API/rendering bytes unchanged. Latest bounded composition
passes 69 affected-example/API/docs cases and SDK verification, zero skips;
this does not relabel the 33d full run as a 09cf full-suite pass. Current GPU
frames/CSV/PCM establish the opening title/configuration/Gravity/Stealth/restart
slice. Native Engine play/hub/relaunch is observed on the owned frameless
window path. Native Help Back remains unobserved input; controlled fixed-key
Back cases pass. Confirmation has measured PCM contribution; navigation has
none in the neutral control. Physical speakers/controllers, default-WM
reliability and a complete act route remain unqualified. Root-owned Opus polish
and promo follow the clean implementation handoff; feature push/PR is held.
