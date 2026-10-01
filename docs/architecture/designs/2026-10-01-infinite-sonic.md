# Infinite Sonic terrain prototype

Task: create a separate Infinite Sonic mod on a new branch, in the main checkout
without worktrees, then install it for the local IntelliJ engine launch.
Base: `67c850fc5132156acede8687ca169ada074f795f` (`develop`).
Branch: `feature/ai-infinite-sonic`. The user selected terrain-only scope.

## Implementation

The independent source project is [examples/infinite-sonic](../../../examples/infinite-sonic/README.md).
Its owner-scoped registration contributes a Sonic 1 patch and a namespaced invisible
controller. Only solo Sonic activates the terrain patch, and only GHZ1's registry
level identifier is replaced. Other acts retain their stock events and layouts.
The existing Mod API and engine source are unchanged.

The normal ROM loader supplies GHZ art, palettes, chunks, collision and background.
`TerrainLibrary` scans decoded foreground columns for continuous floor profiles with
at most four pixels of height change per horizontal pixel. It rejects missing floors,
ceiling surfaces and loop-flagged columns. It aligns selected columns at chunk boundaries
and pairs each with its horizontal reflection. Section entrances/exits therefore share
a floor height, while the interior may rise or dip. Duplicate sections/blocks are
interned. The current World REV01 ROM produces 14 sections; the combined generated
foreground and original background bank uses 124 of the byte layout's 256 indices.
No ROM bytes are packaged in the jar.

A fixed seed and logical two-column section index choose terrain without consuming
the game's RNG. A 64-column layout is regenerated in 16-column steps: at local X=8192,
Sonic and the camera move left 4096 pixels; reverse travel below X=2048 restores the
preceding window until origin zero. A scalar controller origin participates in the
ordinary object rewind codec. The level is a `MutableLevel.snapshot` and runtime
writes use `LevelMutationSurface`, preserving copy-on-write snapshot boundaries.
`PlayableEntity.shiftX` is the published fractional-position-preserving delta operation;
the engine's equivalent `NativePositionOps` helper is not part of the compiled mod API.
No stock physics constants change. The timer is paused and no gameplay objects other
than the controller are placed. There is no end condition.

## Rejected approaches and evidence

- Whole columns with identical unshifted endpoint heights provided only one distinct
  safe column in GHZ1. Mirrored/vertically aligned ROM sections provide variety without
  introducing bridge/loop object requirements.
- Treating GHZ1 as level ID zero loaded the stock course. Its registry descriptor uses
  `0x80`; the implementation now resolves the descriptor rather than hardcoding either.
- Preserving GHZ1's camera maximum Y=768 caused pit death at frame 1049, X=5837,
  Y=986 in a generated dip. The mod owns a deeper camera bound (1536).
- Returning a plain `Level` implementation supported traversal but failed the full
  rewind registry capture because the level adapter requires `AbstractLevel`. The
  supported mutable snapshot wrapper fixes capture and terrain restoration.

These were uncommitted prototype iterations on the base above, not shipped engine
regressions. Temporary probes remain disposable under `target/`.

## Validation and limits

See the [coverage matrix](../validation/levels/infinite-sonic-ghz1.md).
`run_categories.py --base 67c850fc5132156acede8687ca169ada074f795f` selected the entire
ordinary suite (2,957 classes) because the new `examples/` path is unclassified.
Focused validation is proportionate: this is a separate mod, with no engine, public
API, build-policy, physics or timing changes. The production package validator and
actual ROM-backed traversal/reload/rewind paths are exercised directly.

The build script queues engine compilation and validates the resulting jar. The
local installation was also checked with the production repository scanner, state
store, effective catalog builder and restricted `ModClassLoaderFactory`: enabled,
SHA-256 trusted, zero registration failures, content and terrain patches registered.
The jar/state live in ignored root `mods/`, matching IntelliJ's default project-root
working directory. Restart an already-running engine to discover it.

This is a playable prototype, not stock-ROM parity or complete level certification.
GPU presentation, background continuity at rebases, native live-history rewind UI,
movement donors and physical death/respawn remain unverified. Reflections can mirror
scenery; no loops, rings, enemies, checkpoints or difficulty progression are included.
