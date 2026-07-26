# BizHawk ad-hoc probes

Put new one-off PC diagnostics in this directory. Copy
`example_stage_probe.lua`, provide a semantic ROM-state `stage` predicate and
declarative `hooks`, then run it through `run_bizhawk_lua.sh` with an absolute
`OGGF_OUT` path. The launcher supplies the absolute
`OGGF_BIZHAWK_PROBE_RUNTIME` path, so probes do not depend on BizHawk's process
working directory.

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
