# S3K KosM final-parent retirement single-writer audit

Date: 2026-08-02
Scope: locked-on S&K-half runtime, `Kos_modules_left` (`$FFFFF760`) and
`Kos_module_queue` (`$FFFFF764-$FFFFF77B`)
Source of truth: `docs/skdisasm/sonic3k.asm`

## Finding

The paired transition used by native recorder 6.41/6.42 has one runtime owner.
When the prior modules-left byte is exactly `$81`, the transition to a
decremented/initialized next parent plus the canonical one-entry FIFO shift is
written only by `Process_Kos_Module_Queue` (`$1B28`). The final-parent path
clears bit 7, decrements the low count to zero, queues the DMA, shifts entries
1-3 into entries 0-2, clears entry 3, and either returns empty or tail-jumps to
`Process_Kos_Module_Queue_Init` for the new head
(`sonic3k.asm:2750-2788`; the shift completes at `$1BC4`). This is the ROM
POST owner called after `Process_Sprites` from `LevelLoop`.

Therefore a single observation interval containing all of the following is
sufficient service attribution even if `Level_frame_counter` is unchanged:

- prior modules-left is exactly `$81`;
- exactly one mirrored physical head disappears;
- the next active source is the prior second parent plus its two-byte KosM
  header and its destination is unchanged;
- every trailing six-byte FIFO entry shifts exactly once;
- physical cardinality falls by exactly one; and
- the interval does not cross a reset/game-mode boundary.

The recorder may stamp only that complete proof as `post_objects`. A held
counter by itself remains insufficient.

## Exhaustive writer inventory

`rg -n "Kos_modules_left|Kos_module_queue" docs/skdisasm/sonic3k.asm`
finds the following symbolic writes; all other matches are reads:

| Owner | ROM/source | Writes | Classification |
|---|---|---|---|
| `Queue_Kos_Module` | `$1AD2`; lines 2668-2687 | Appends source/destination to the first free six-byte FIFO entry. If entry 0 is empty it branches to initialization. | Submission, not retirement. |
| `Process_Kos_Module_Queue_Init` | `$1AEA`; lines 2694-2717 | Derives the low-seven-bit module total, handles the `$A000` last-module encoding, writes active destination/head source, and increments the total. | Empty-head or post-shift next-head initialization, not the shift writer. |
| `Process_Kos_Module_Queue` | `$1B28`; lines 2726-2791 | Sets/clears busy bit, decrements module count, advances the active source/destination, and on zero performs the only canonical four-entry FIFO shift/last-slot clear. | Sole final-active retirement/shift writer. |
| `Title_Screen` bulk clear | lines 5391-5397 | `clearRAM Kos_decomp_stored_registers,$6C` clears `$FFFFF710-$FFFFF77B`, including both audited fields. | Mode/reset clear, never a canonical shift. |
| `Level` bulk clear | lines 7507-7516 | The same `$6C` clear while bit 7 is set in `Game_mode` for level loading. | Mode/reset clear, never a canonical shift. |

The two bulk clears explain why a disappearing head alone is not authority.
They clear the entire queue and modules-left state rather than preserving the
exact shifted suffix, and the recorder fences them using the observed
game-mode transition. A nonempty mutation across a mode transition is
malformed and fails closed; an empty reset clears only the recorder mirror and
does not create an event or reset the run-wide ordinal ledger.

## Negative shapes retained

- duplicate counter with no FIFO retirement: no module completion;
- stale `$81` sampled before a later unrelated shift: no completion;
- empty-to-active initialization: no completion;
- more than one lost head: malformed;
- shift plus same-interval append/cardinality mismatch: malformed;
- incorrect next active identity or any noncanonical trailing entry: malformed;
- reset/mode crossing: no completion and no ordinal reuse.

No new PC or RAM callback was added. The maintained native harness continues
to use only its existing `$1B46` direct-child submission callback; frozen Lua
6.37 recorders and their version constants are unchanged.
