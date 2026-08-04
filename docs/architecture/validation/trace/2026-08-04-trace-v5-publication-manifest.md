# Trace v5 publication manifest

This is the deterministic record of the v5 fixture-tree transaction. It is
paired with the installed inventory aggregate
`04851c0a146eeb101a0ce0d76c78ba9c861a4eb3d6c9ff50612c84112d868790`.

## Protected predecessor archive

The eight S1 credits directories were moved, not deleted. Each directory's
three archived files (`aux_state.jsonl`, `metadata.json`, `physics.csv`) is
preserved under `2026-08-04-s1-credits-predecessor/`. The archive contains 24
files; their SHA-256 values are verified by the following deterministic
command from the repository root:

```text
find docs/architecture/validation/trace/2026-08-04-s1-credits-predecessor \
  -type f -print0 | sort -z | xargs -0 sha256sum
```

The protected directories are `credits_00_ghz1`, `credits_01_mz2`,
`credits_02_syz3`, `credits_03_lz3`, `credits_04_slz3`, `credits_05_sbz1`,
`credits_06_sbz2`, and `credits_07_ghz1b`.

## Rename

| action | old path | new path |
| --- | --- | --- |
| rename (metadata identity only) | `src/test/resources/traces/s3k/ending_completerun/metadata.json` | `src/test/resources/traces/s3k/hpz22_completerun/metadata.json` |

## True deletions

These paths are obsolete and are not represented by a compatibility reader:

```text
src/test/resources/traces/s1/ghz1_fullrun/aux_state_retro.jsonl
src/test/resources/traces/s1/ghz1_fullrun/aux_state_retro.jsonl.gz
src/test/resources/traces/s1/ghz1_fullrun/metadata_retro.json
src/test/resources/traces/s1/ghz1_fullrun/physics_retro.csv
src/test/resources/traces/s1/mz1_fullrun/aux_state_retro.jsonl
src/test/resources/traces/s1/mz1_fullrun/aux_state_retro.jsonl.gz
src/test/resources/traces/s1/mz1_fullrun/metadata_retro.json
src/test/resources/traces/s1/mz1_fullrun/physics_retro.csv
src/test/resources/traces/s3k/ending_completerun/aux_state.jsonl.gz
src/test/resources/traces/s3k/ending_completerun/hardware_timing.jsonl
src/test/resources/traces/s3k/ending_completerun/physics.csv.gz
src/test/resources/traces/synthetic/basic_3frames/aux_state.jsonl
src/test/resources/traces/synthetic/basic_3frames/metadata.json
src/test/resources/traces/synthetic/basic_3frames/physics.csv
src/test/resources/traces/synthetic/execution_v3_2frames/metadata.json
src/test/resources/traces/synthetic/execution_v3_2frames/physics.csv
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/run_manifest.json
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/seg00_aiz/metadata.json
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/seg00_aiz/physics.csv
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/seg01_gumball/metadata.json
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/seg01_gumball/physics.csv
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/seg02_aiz/metadata.json
src/test/resources/traces/synthetic/run_aiz_gumball_3seg/seg02_aiz/physics.csv
src/test/resources/traces/synthetic/run_ehz_ss_3seg/run_manifest.json
src/test/resources/traces/synthetic/run_ehz_ss_3seg/seg1_ehz1/metadata.json
src/test/resources/traces/synthetic/run_ehz_ss_3seg/seg1_ehz1/physics.csv
src/test/resources/traces/synthetic/run_ehz_ss_3seg/seg2_ehz1/metadata.json
src/test/resources/traces/synthetic/run_ehz_ss_3seg/seg2_ehz1/physics.csv
src/test/resources/traces/synthetic/run_ehz_ss_3seg/ss/metadata.json
src/test/resources/traces/synthetic/run_ehz_ss_3seg/ss/physics.csv
src/test/resources/traces/synthetic/s2_execution_v3_2frames/aux_state.jsonl
src/test/resources/traces/synthetic/s2_execution_v3_2frames/metadata.json
src/test/resources/traces/synthetic/s2_execution_v3_2frames/physics.csv
src/test/resources/traces/synthetic/s3k_execution_v3_2frames/aux_state.jsonl
src/test/resources/traces/synthetic/s3k_execution_v3_2frames/metadata.json
src/test/resources/traces/synthetic/s3k_execution_v3_2frames/physics.csv
```

The publication check compares this exact path set to the predecessor diff. No
file under the protected S1 archive is included in this deletion set.
