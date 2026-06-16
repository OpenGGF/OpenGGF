# OpenGGF — Development Timeline

*A captioned gallery of the engine clawing its way toward accuracy, roughly Dec 2025 →
Apr 2026. Every clip is a real build, fired across a chat between James and Farrell at the
moment it happened. The grand, fact-checked version of this story — who built what, which AI
did which, and why accuracy needed an oracle — is in [The AI Journey](AI_JOURNEY.md); this page
is just the receipts, in order, bugs and all.*

> GIFs are short and silent (the audio disasters get their due in the journal). Versions in the
> headings are the build's own self-reported version string.

---

## Sound & the first objects (Alpha V0.06, Dec 2025 – Jan 2026)

**2025-12-12** — First build where the **FM tones sound close to right**. PSG is fucked, the noise channel is missing, and the SMPS loops are broken in places.

![fm-tones-close](assets/timeline/fm-tones-close.gif)

**2025-12-12** — Right at the end: a **secret first demo of spindash** — with sound.

![secret-spindash](assets/timeline/secret-spindash.gif)

**2025-12-18** — **PSG starts working** — but badly out of tune, and the noise channel is rough.

![psg-out-of-tune](assets/timeline/psg-out-of-tune.gif)

**2025-12-22** — Still detuned PSG, but **rings exist now** — one of the first objects (with incorrect gaps between them).

![first-rings](assets/timeline/first-rings.gif)

**2026-01-02** — **First build with real Sonic sprites.** Collision is very wonky, no sprite priority yet — Sonic falls through the terrain ten seconds in. *(whoops!)*

![first-sprites-fall](assets/timeline/first-sprites-fall.gif)

**2026-01-05** — A crude spring with a **very flaccid strength** to it — and spikes.

![flaccid-spring](assets/timeline/flaccid-spring.gif)

**2026-01-06** — Rings looking better, but the **EHZ bridge goes very jittery.**

![jittery-bridge](assets/timeline/jittery-bridge.gif)

---

## EHZ playable & the special-stage struggle (0.1, Jan 2026)

**2026-01-10** — **First debug overlay, badniks and item monitors** — ending with Sonic falling through the EHZ bridge.

![first-debug-overlay](assets/timeline/first-debug-overlay.gif)

**2026-01-10** — **First full run of EHZ Act 1**, with all the objects and rings in place. Many bugs, but the first time the stage was truly playable.

![first-ehz-run](assets/timeline/first-ehz-run.gif)

**2026-01-11** — First attempt at **Sonic 2 special stages** (6s in): almost incomprehensible garbage tiles.

![special-garbage](assets/timeline/special-garbage.gif)

**2026-01-11** — Special stage again — palette now correct, but decompression/mapping bugs leave the half-pipe **almost recognisable but incredibly blocky.**

![special-blocky](assets/timeline/special-blocky.gif)

**2026-01-11** — Half-pipe visuals now semi-working, with **mirroring issues breaking left turns.**

![halfpipe-mirror](assets/timeline/halfpipe-mirror.gif)

**2026-01-17** — **First full starpost-to-special-stage entry**, with the stage working with rings and Sonic sprites.

![starpost-to-special](assets/timeline/starpost-to-special.gif)

**2026-01-18** — First Chemical Plant work — Sonic looping through the transport tubes. *"I want to get off Dr. Robotnik's wild ride."*

![cpz-wild-ride](assets/timeline/cpz-wild-ride.gif)

---

## CPZ / ARZ / CNZ and the first bosses (0.2, Jan – Feb 2026)

**2026-01-20** — **Water physics in CPZ**, plus staircase blocks with broken physics.

![cpz-water](assets/timeline/cpz-water.gif)

**2026-01-21** — Sonic in an **infinite loop breaking an ARZ pillar** that immediately respawns (with the wrong palette).

![arz-pillar-loop](assets/timeline/arz-pillar-loop.gif)

**2026-01-22** — First sight of the **performance graph**, built to work out how efficient the engine actually is.

![perf-graph](assets/timeline/perf-graph.gif)

**2026-01-23** — A demo of **Sonic's dust** when he brakes suddenly.

![brake-dust](assets/timeline/brake-dust.gif)

**2026-01-26** — Early demo of the **CNZ slot machine**, 90% working.

![cnz-slot](assets/timeline/cnz-slot.gif)

**2026-01-26** — **CNZ slope physics broken**, showcased by a vertical flipper.

![cnz-flipper](assets/timeline/cnz-flipper.gif)

**2026-01-29** — First attempt at a **Sonic 2 boss**. Robotnik looks like he's been through a car crusher.

![boss-car-crusher](assets/timeline/boss-car-crusher.gif)

**2026-01-29** — **First defeatable boss.** Robotnik's car wheels look a bit wobbly; the egg prison is a bit buggy.

![first-defeatable-boss](assets/timeline/first-defeatable-boss.gif)

**2026-02-04** — First look at **debug overlay V2** — and Sonic has somehow learned to walk on the ceiling.

![ceiling-walk](assets/timeline/ceiling-walk.gif)

---

## Sonic 3 & Knuckles begins (0.3, Feb 2026)

**2026-02-08** — The **sound-test app**, written to start tackling the audio engine's longstanding issues — beginning with Sonic 1 vs Sonic 3&K audio-driver/profile differences. The human's diagnostic oracle for a subsystem that had none.

![sound-test-app](assets/timeline/sound-test-app.gif)

**2026-02-08** — **First attempt at Sonic 3 & Knuckles support** — a very early prototype of loading S3K tiles.

![first-s3k](assets/timeline/first-s3k.gif)

**2026-02-10** — First demo of **Sonic 1 special stage** support. The palette is weird.

![s1-special](assets/timeline/s1-special.gif)

---

## AIZ, shields, and ROM donation (0.4, Feb 2026)

**2026-02-16** — One frame of the **AIZ intro coming together** — across a run of builds it went from a garbled mess to ~90% complete.

![aiz-intro-wip](assets/timeline/aiz-intro-wip.gif)

**2026-02-17** — Early **S3K elemental-shield** prototypes.

![elemental-shields](assets/timeline/elemental-shields.gif)

**2026-02-19** — The **AIZ swing-vine freaking the fuck out.** Very funny.

![aiz-vine](assets/timeline/aiz-vine.gif)

**2026-02-22** — First demo of **ROM donation**: Sonic & Tails (S2 sprites) in Sonic 1's Labyrinth Zone. Underwater Tails' palette is a bit off, but it works.

![rom-donation](assets/timeline/rom-donation.gif)

**2026-02-22** — Tails' palette fixed — and **spindash demonstrated in Sonic 1** too.

![donation-spindash](assets/timeline/donation-spindash.gif)

---

## OpenGGF: the rename, Super Sonic everywhere, an army of Sonics (Feb – Apr 2026)

**2026-02-25** — First prototype carrying the **OpenGGF name** — and very early tech for S2's Super Sonic in Sonic 1 *(?!)*.

![openggf-name](assets/timeline/openggf-name.gif)

**2026-02-25** — **Sonic 3 sprites donated into Sonic 1**, with S3&K Super Sonic — while layering sound profiles almost flawlessly (music and SFX from different audio drivers at once).

![s3-super-in-s1](assets/timeline/s3-super-in-s1.gif)

**2026-02-27** — Buggy S2 Death Egg Egg-Robo defeat. The robo's body **refuses to leave Sonic's side**; Sonic 'escapes', dies anyway.

![death-egg-clingy](assets/timeline/death-egg-clingy.gif)

**2026-03-13** — First demo of **seamless AIZ1 → AIZ2 transitions**, with the fire/flame curtain that took a week of someone's life to perfect.

![aiz-seamless](assets/timeline/aiz-seamless.gif)

**2026-03-19** — A dumb idea: **custom sidekicks identical to the main character.** Thus, Sonic & Sonic in Sonic 2 was born.

![sonic-and-sonic](assets/timeline/sonic-and-sonic.gif)

**2026-03-19** — Couldn't stop there — **more than one sidekick.** Sonic & Sonic & Sonic & Sonic & … (bugs since fixed).

![lots-of-sonics](assets/timeline/lots-of-sonics.gif)

**2026-03-20** — **21 Sonics and 1 Tails**, in AIZ — and the performance monitor shows barely any hit.

![21-sonics](assets/timeline/21-sonics.gif)

**2026-04-08** — **S3K Pachinko bonus stage** working, with Tails & Knuckles as sidekicks.

![pachinko](assets/timeline/pachinko.gif)

**2026-04-08** — **S3K slot-machine bonus stage** freaking out with glitchy graphics.

![slot-glitch](assets/timeline/slot-glitch.gif)

---

*Back to [The AI Journey](AI_JOURNEY.md). For the live accuracy work, see
[`TRACE_FRONTIER_LOG.md`](TRACE_FRONTIER_LOG.md).*
