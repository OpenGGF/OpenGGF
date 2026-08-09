# BizHawk ad-hoc probes

Put new one-off PC diagnostics in this directory. Copy
`example_stage_probe.lua`, provide a semantic ROM-state `stage` predicate and
declarative `hooks`, then run it through `run_bizhawk_lua.sh` with an absolute
`OGGF_OUT` path. The launcher supplies the absolute
`OGGF_BIZHAWK_PROBE_RUNTIME` path, so probes do not depend on BizHawk's process
working directory. This remains true for probes organized in nested
subdirectories: always load the launcher-provided canonical runtime rather than
looking for a sibling `probe_runtime.lua`.

`probe_runtime.lua` owns fast-headless setup, delayed hook registration,
output teardown, hook removal, and emulator exit. Probe files must not perform
those operations directly. Existing diagnostics elsewhere under
`tools/bizhawk/`, production recorders, and Lua libraries are intentionally
outside this contract.

Hooks default to `kind = "execute"`. `kind = "write"` observes a ROM memory
write; it does not authorize the callback to mutate emulated memory. Probe
callbacks are read/log-only: do not call `mainmemory.write*`, `memory.write*`,
`joypad.set`, savestate mutation APIs, or `emu.setregister`. The runtime
registers either hook kind only after `stage` becomes true.

## Sonic 1 audio-driver parity observer

`s1_audio_driver_parity_probe.lua` observes the shipped Sonic 1 World REV01
music driver without changing emulated state. Run its short callback proof
before a full capture:

```bash
mkdir -p "$PWD/target/audio-parity"
OGGF_AUDIO_CALLBACK_VALIDATE_ONLY=1 \
OGGF_OUT="$PWD/target/audio-parity/s1-callback-validation.jsonl" \
BIZHAWK_HOME=/absolute/path/to/BizHawk-2.11-linux-x64 \
tools/bizhawk/run_bizhawk_lua.sh \
  tools/bizhawk/probes/s1_audio_driver_parity_probe.lua \
  src/test/resources/audio/parity/s1/s1-soundtest-ghz.bk2 \
  /absolute/path/to/Sonic-1-World-REV01.gen
```

Remove `OGGF_AUDIO_CALLBACK_VALIDATE_ONLY` and choose a different `OGGF_OUT`
file for the full GHZ cycle capture. The observer verifies the ROM, movie,
core, callback arguments, and complete opcode fallback manifest before it
records anything. It continues past the end of the movie under verified
neutral input and exits after proving a complete repeated music cycle. Output
is local diagnostic material under ignored `target/`; never add the detailed
tick or raw register stream to source control or test resources.

Both launchers hash the exact BK2 path before starting EmuHawk and supply that
digest to identity-pinning probes. A caller-provided digest is always replaced;
the S1 observer rejects any content other than the pinned controller movie.
