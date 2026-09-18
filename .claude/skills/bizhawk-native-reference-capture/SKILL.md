---
name: bizhawk-native-reference-capture
description: Capture native Genesis screenshots and state observations with a zone-specific Lua exporter in BizHawk. Use for ROM behavior corroboration and visual references; canonical trace fixtures use bizhawk-headless-trace, and engine gameplay captures use gameplay-capture.
---

# BizHawk native reference capture

Use `tools/bizhawk/capture_native_references.py` for diagnostic reference captures
in Linux BizHawk 2.11/GPGX. Read the [host guide](../../../tools/bizhawk/README.md)
for the current flags and Lua environment. This host runs an explicit exporter
and plan; it does not select a zone, supply a route, or infer checkpoint offsets.

## Choose the observation

Start from the owning disassembly routine and the question the capture should
resolve. Identify the state, clock and observation boundary before sampling.
Search existing `tools/bizhawk/capture_*.lua` exporters before writing another.
Keep zone-specific setup and checks in the exporter/plan, outside the common host.

Use [bizhawk-headless-trace](../bizhawk-headless-trace/SKILL.md) for canonical
replay fixtures. Use [gameplay-capture](../gameplay-capture/SKILL.md) for OpenGGF
screenshots or video. Native reference output does not replace either contract.

## Launch

Use an existing official BizHawk **2.11** installation with Mono, `monodis` and
working display access. Verify the supplied ROM against the repository identity
table; do not merely copy its observed hash into the expected-hash argument.
Use absolute input paths and a new output directory under an external task root.

```bash
python3 tools/bizhawk/capture_native_references.py \
  --bizhawk-home "$BIZHAWK_HOME" \
  --rom "$ROM" --rom-sha1 "$ROM_SHA1" --movie "$BK2" \
  --exporter "$EXPORTER" --plan "$PLAN" \
  --output "$OUTPUT" --require-output observations.csv --timeout 180
```

Replace `observations.csv` with the exporter's actual required output; repeat
`--require-output` for other essential artifacts, including a completion marker
when the exporter provides one. The plan is a Lua file returning the table that
exporter expects. An empty `return {}` is appropriate only when it needs no fields.
Optional `--fixture-state "$STATE"` supplies an explicitly identified native save.
The exporter owns its loading, setup, input sequence and `client.exit()` call.

Exporters consume `OGGF_NATIVE_PLAN`, `OGGF_NATIVE_OUTPUT` and, when supplied,
`OGGF_NATIVE_FIXTURE_STATE`; the host guide lists the remaining provenance and
framebuffer-probe variables. Historical `OGGF_FBZ_*` aliases are supplied only by
the FBZ compatibility command. Keep that command for existing FBZ recipes.

## Avoid misleading captures

- Stop movie playback before loading a standalone save made outside playback.
  Check the load result and actual zone/act before observing gameplay.
- A running frame counter does not prove input is admitted. Inspect the native
  control owner. For example, SOZ1's buried entry waits for a fresh jump; idle
  frames cannot finish it. Resolve the owning routine before bypassing a lock.
- Distinguish ordinary route traversal from declared positioned setup. Record
  any one-time RAM writes and the skipped route/events. Never feed native
  observation rows back into engine gameplay state.
- Read `host.json` and `launch.log`, then inspect observation counts, continuity,
  state transitions and selected images. The host rejects logged Lua exceptions,
  timeouts, nonzero emulator exits and missing/empty required files. BizHawk's
  exit code alone can still be zero after a failed Lua script.
- `execution_status: completed` and nonempty files establish execution only.
  The framebuffer probe checks pixel content, not the intended scene. Keep
  native behavior corroboration, matched trajectory comparisons and pixel
  acceptance as distinct claims supported by the actual observations.

Record the source/input hashes, command and bounded conclusion in the existing
task artifact. Preserve reusable probes and lessons under repository policy;
keep captures outside the repository. Follow the user's authorized scope for
any fixture publication or broader implementation work.
