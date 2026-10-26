# Gens native reference observer

`observe_soz_flame.lua` preserves the read-only observer developed for the
2026-10-26 TAS verdict audit. It targets original Gens11b and the original locked-on
S3&K ROM, SHA-1 `cfbf98c36c776677290a872547ac47c53d2761d6`, with disassembly pin
`8b2cf3c4178f2382f844da67798de4befc50c5e0`. The official Gens Lua API documentation
and ROM opcode checks bind its hooks. This is comparison-only native reference
capture, **not** a V5 trace producer, replay input or runtime asset source.

Open the original ROM and GMV read-only in an owned isolated native environment.
Set `SOZ_NATIVE_PREFIX` to a new Windows-visible absolute output prefix; existing
outputs are rejected. `SOZ_NATIVE_FRAMES` defaults to 1200 active SOZ2 emulator
frames and `SOZ_NATIVE_MAX_TOTAL` to 90000 total emulator frames. Use a separately
bounded driver and host timeout. The observer installs callbacks only: it neither
advances emulation nor writes RAM, registers, inputs or save states. By default
it pauses on completion; `SOZ_NATIVE_AUTOPAUSE=0` leaves termination to the driver.

Use **turbo**, never maximum/invisible pacing: the latter bypassed callbacks in
the original runtime. Verify callback dispatch in a tiny bounded control first.
The `.done` reason distinguishes target capture from total-limit/movie-end exit;
a marker alone is not a successful mechanism capture. Preserve and compress
accepted CSV/terrain/provenance outside the repository; clean temporary outputs.

`player_step_begin` at `$10BFC` identifies the actual copied input and increments
`player_serial`; `$10C26` and `$10C8E` identify completed movement and object work.
Compare these owner boundaries. Raw emulator `before_frame`/`after_frame` rows can
interrupt collision and one emulator frame can contain two player steps. Direct
`$113A0`/`$113CE` hooks retain negative-distance and horizontal response evidence.
Do not assume a universal player-serial offset: the authored fixture's offset is
recorded in its capture metadata, not part of the observer contract.

The accepted native executions used this observer before preservation, with only
its task-specific default output path replaced here by a required environment
variable. Syntax was checked with `luac5.4 -p`; no fresh native replay was needed
for that configuration-only change. Launcher, exact drivers, original movie
inputs and compressed comparisons remain in the external task artifact. The
[dated matrix](../../docs/architecture/validation/2026-10-26-tas-verdicts-s3k.md)
records both accepted and rejected capture approaches. ROMs and native emulator
binaries are not redistributed.
