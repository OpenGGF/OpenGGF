# ICZ late-window RNG ownership audit

Date: 2026-07-27
Branch: `bugfix/ai-trace-int-icz-24140`

## Native evidence

The three captures used a preliminary probe that waited for ICZ2 gameplay
with `Camera_X_pos` (`$EE78`) at or beyond `$3F00` and
`Game_frame_counter >= $52DE`. The retained
`tools/bizhawk/probes/icz_rng_ownership_probe.lua` corrects that gate to the
intended `Camera_X_pos_copy` (`$EE80`) before any future capture. Both versions
use the canonical probe runtime and only then hook `Random_Number` entry
`$001D24`, its paired RTS at `$001D4A`, and a `$01AADA` watchdog. It predicts
and asserts both the returned value and stored post-seed for every call.

Three independent captures were byte-identical (SHA-256
`296a6a5ac95789fc44c87ea6cc53d1f6efe605b51ca538867af62a6ac051d6cc`).
The bounded window contained:

| Trace frame | Caller return | Owner | Seed transition |
|---:|---:|---|---|
| 22486 | `$02C92E` | subtype-zero `Obj_Animal` initialization | `528B07B3 -> 73EF3BAB` |
| 22733 | `$08B6C2` | ICZ snow-particle initialization | `73EF3BAB -> 1FB38E63` |

`$02C92E` is the return from the call at `loc_2C924`
(`docs/skdisasm/sonic3k.asm:61049-61055`). `$08B6C2` is the return from the
snow child call (`docs/skdisasm/sonic3k.asm:189957-189985`). There were no
other native calls after the semantic gate and before the first snow call.
The scratch captures under `target/`, including the earlier `.stage` files,
are evidence only and are not repository artifacts.

## Engine crosswalk and bounded conclusion

Temporary engine instrumentation starting at the same pre-seed
`528B07B3` observed, in order, an `ICZIceCubeDebris` draw, three additional
Animal draws, the final Animal draw, and then snow. The instrumentation was
removed after capture.

The engine's IceCube-debris draw is therefore a candidate ownership/order
mismatch, not the earliest demonstrated cross-runtime difference. Equality at
the pre-seed does not ordinally pair that engine call with an absent native
call: the native probe armed after the earlier cube shatter and debris
initialization. Establishing the cross-runtime ordering requires a native
capture spanning allocation and first dispatch plus an aligned engine
crosswalk.

This evidence also does **not** establish that IceCube debris has no ROM RNG
ownership. `Obj_ICZIceCube` creates up to twelve children with
`CreateChild1_Normal`; each allocated child enters `loc_8B432`, whose first
dispatch calls `Random_Number` before selecting `anim_frame`
(`docs/skdisasm/sonic3k.asm:189625-189705`). The native probe only proves that
no such child was dispatched in its late, stage-gated window. It does not
cover the earlier cube shatter.

The engine currently draws in both `IceCubeDebris` constructors, including
the constructor used by generic rewind recreation
(`IczIceCubeObjectInstance.java:231-247`). Normal construction is therefore
earlier than the ROM routine body, and rewind reconstruction can introduce a
draw for an object whose native initialization already happened. More
specifically, `spawnChild` invokes `factory.get()` before asking the object
manager to allocate an SST slot. A failed allocation therefore currently
constructs the child and eagerly consumes RNG even though that child is
immediately marked destroyed and never receives a real dispatch.

## Next semantic checks

The minimal owner is the IceCube-debris object's one-shot native
initialization state, executed on its first real scheduled update and captured
by rewind. Tests should establish:

1. normal construction and rewind recreation consume zero RNG values;
2. the first real update consumes exactly one value and later updates consume
   none;
3. a later-slot child executes in the same `Process_Sprites` pass after
   successful `AllocateObjectAfterCurrent`, preserving intervening-slot order;
4. a child that cannot allocate, is cancelled, or is restored after native
   initialization never consumes an initialization value.

Before changing production code, cross-check the sibling ICZ debris routine
`loc_8B230` (`docs/skdisasm/sonic3k.asm:189494-189508`) so temporary logger
labels cannot confuse stalactite debris with IceCube debris. Then account for
all IceCube-debris construction sites (`spawnDebris` and generic rewind
recreation) and remeasure the full native/engine call sequence. Deferring the
constructor draw alone is not claimed to fix the frame-24140 rings frontier or
the snow-seed mismatch: the three extra Animal draws remain an unresolved,
separate object-lifetime/allocation investigation.
