# BizHawk ad-hoc probes

Put new one-off PC diagnostics in this directory. Copy
`example_stage_probe.lua`, provide a semantic ROM-state `stage` predicate and
declarative `hooks`, then run it through `run_bizhawk_lua.sh` with an absolute
`OGGF_OUT` path.

`probe_runtime.lua` owns fast-headless setup, delayed hook registration,
output teardown, hook removal, and emulator exit. Probe files must not perform
those operations directly. Existing diagnostics elsewhere under
`tools/bizhawk/`, production recorders, and Lua libraries are intentionally
outside this contract.

Hooks default to `kind = "execute"`. Use `kind = "write"` for a memory-write
probe; the runtime registers either kind only after `stage` becomes true.
