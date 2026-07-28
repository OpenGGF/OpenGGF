# S3K Zone Bring-Up Skill Carrier/Graph Forward Test

## Exact evaluation prompt

```text
Read only the s3k-zone-bring-up skill. A zone has carriers/grabs used by arbitrary configured sidekicks and dynamic parent/child platform families with child-specific roles. State mandatory architecture/tests. Score explicit PASS/FAIL: R18 identity-keyed scalable participant state, no fixed arrays/list-order indexing, allocation-free ObjectPlayerQuery hot-path iteration preserving native order; R19 captured child role metadata beyond shared ObjectSpawn and real ObjectManager capture -> remove/diverge -> restore proving exact count/config, settled parent relink, no duplicate respawn, lifetime cascade.
```

## Baseline raw output

```text
R18: FAIL

The skill requires ObjectPlayerQuery / ObjectPlayerParticipationPolicy, multi-sidekick testing with three or more characters, and rewind capture/relink behavior. It does not explicitly mandate identity-keyed scalable participant state, prohibition of fixed-size arrays or list-order indexing, allocation-free ObjectPlayerQuery hot-path iteration, or preservation of native participant order.

R19: FAIL

The skill requires a complete dynamic-spawn graph, allocation primitives, lifecycle handling, rewind capture/relink behavior, coverage guards, and focused capture/restore round trips. It does not explicitly mandate capturing child-role metadata beyond shared ObjectSpawn, a real ObjectManager capture -> remove/diverge -> restore test, exact restored child count/configuration, settled parent relinking, no duplicate respawn, or restored parent-to-child lifetime cascade.

Overall: FAIL.
```

## Updated-skill raw output

```text
R18: PASS

The skill explicitly mandates identity-keyed participant state scalable to configured sidekick count, prohibits fixed native-slot arrays and list-order indexing, requires allocation-free ObjectPlayerQuery hot-path iteration, and preserves native P1/P2 branch order through participation policy.

R19: PASS

The skill explicitly requires captured recreation metadata for every child role, phase, radius, delay, and special flag beyond shared ObjectSpawn. It mandates a real ObjectManager capture -> remove/diverge -> restore test proving exact child count/configuration, settled parent relinking despite recreation order, no duplicate respawn, and restored parent-child lifetime behavior. Constructor-only probes are explicitly rejected.

Overall: PASS.
```

Verdict: **GREEN, 2/2** after a **RED, 0/2** baseline.
