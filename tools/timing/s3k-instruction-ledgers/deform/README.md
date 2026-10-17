# Background deformation ledger (kos46 research R16, 2026-10-17, unexecuted in the engine)

> Frozen at R16 qualification (2026-10-17). The research sources were copied and hash-bound before promotion; the
> copies here differ from them only in import paths, directory names and the provenance header. Research sha256:
> `deform_cost.py` `2d51168c50e903e3fb0baf78947a575a94412b2d85276b57a4c0ee8f599f7413`,
> `deform_reference.py` `17f614b5bc1c4b629823709f00876d7298c2bf52f083737bb201f0fd2982f06b`,
> `test_deform_cost.py` `89385556ce8ad84dabdada31b1c53ce9acfbb6fa666a1dc0b278b27af7320a4f`,
> `README.md` `bfb6e9915ac9fdac31255e1cf71e22383764095142a7830a6ffb5422dc8413d2`,
> `../mini68k/deform_forms.py` `d49554fb742b175c231d2081c6b312cadee48d475236ba7f268282894275636c`,
> `../mini68k/test_deform_forms.py` `f5556baab8cf7e0b018cde42c06269d47a474784c88090f29d6a2a7c1352f1ff`.

ROM SHA-1 CFBF98C3..., pinned GPGX 051d430d instruction table (Motorola cycles equal on the ROM-table, AIZ1_Deform and water tests),
shipped FixBugs = 0. Instruction time only: the H-scroll DMA in V-int, FIFO and refresh are separate. No Java; the
Java DMA/consumer owner owns production.

## Source provenance (label: pinned 044fa46 line / local checkout 1a454a0 line; ROM address)
| Routine | File | 044fa46 | 1a454a0 | ROM |
|---|---|---|---|---|
| MakeFGDeformArray | sonic3k.asm | 103683 | 103642 | $4F0B6 |
| ApplyDeformation (+3 at +4, +2 at +$10) | sonic3k.asm | 103705 | 103664 | $4F0CE |
| ApplyFGandBGDeformation | sonic3k.asm | 103914 | 103873 | $4F1F0 |
| Ending_BackgroundEvent | sonic3k.asm | 121254 | 121213 | (caller, open) |
| Ending_BGDeformArray1 / 2 | sonic3k.asm | 121484 / 121496 | 121443 / 121455 | $5B144 / $5B15A |
| MakeFGDeformArray | s3.asm | 68721 | 68665 | $39E74 + $200000 |
| ApplyDeformation | s3.asm | 68743 | 68687 | $39E8C + $200000 |
| ApplyFGandBGDeformation | s3.asm | 68847 | 68791 | $39F18 + $200000 |
| AIZ1_Deform | s3.asm | 70328 | 70272 | $3B03E + $200000 |
| AIZ1_ApplyDeformWater | s3.asm | 70393 | 70337 | $3B0D4 + $200000 |
| AIZ1_DeformArray | s3.asm | 70518 | 70462 | $3B258 + $200000 |
The S&K and S3 copies of ApplyDeformation, MakeFGDeformArray and ApplyFGandBGDeformation are byte-identical (checked
at construction). Source read with `git show 044fa46:<file>` into owned scratch and deleted afterwards.

| File | Role | Tests |
|---|---|---|
| `../mini68k/deform_forms.py` | adds ASR.W/L #n,Dn, Scc Dn (true/false outcome), JMP d16(PC) / (xxx).l to the render forms | `../mini68k/test_deform_forms.py` (5, old-red: render_forms refused each) |
| `deform_cost.py` | micro-executes ApplyDeformation (both copies, three entries), AIZ1_Deform, AIZ1_ApplyDeformWater (dry and split, through MakeFGDeformArray and ApplyFGandBGDeformation) | `test_deform_cost.py` (7) |
| `deform_reference.py` | independent band-algorithm and fixed-point references (comparison oracle only) | used by the tests |

## Closed terms
* ApplyDeformation: control flow depends only on the band list (a4), Camera_Y_pos_BG_copy and the line count d1
  ($DF for ApplyDeformation; caller-owned for ApplyDeformation3/2). One $7FFF band at BG y 0 costs 4014 (hand sum:
  entry 40 + band setup 132 + 112 line pairs x 24 + 111 dbf x 10 + 14 + exit 30). Scroll words (a5) and camera X change data only. Band height 0 would
  wrap dbf (refused by the reference).
* AIZ1_Deform: constant 1094 cycles for every input (hand sum, 200 random states); writes Camera_Y_pos_BG_copy =
  Camera_Y_pos_copy asr 1, HScroll_table bands, and advances the drift long at HScroll_table+$3C by $2000.
* AIZ1_ApplyDeformWater: dry when Water_level - Camera_Y_pos_copy >= $E0 (signed; $1000 non-water level means dry
  unless camera Y > $F20); otherwise the split draws the lines above the water with ApplyDeformation3, builds the FG
  array at HScroll_table+$40 and draws the rest with ApplyFGandBGDeformation, ripple tables indexed by
  Level_frame_counter and Water_level. A water line at or above the screen top refuses (ApplyDeformation3 with a
  negative count would write past H_scroll_buffer).

## Evidence classes
* SOURCE/SYNTHETIC: micro-execution equals the references on every H_scroll_buffer long, HScroll_table byte and
  Camera_Y_pos_BG_copy: 240 calls over the three ROM tables (both copies), 120 synthetic band lists (per-line
  bands, odd/even heights, camera above the first band, ApplyDeformation3 line counts), 200 AIZ1_Deform states, 150
  AIZ1_ApplyDeformWater states (dry and split), hand sums, mutations (S3 copy byte, bcc branch, pinned opcode) and
  refusals; every ApplyDeformation branch region is reached.
* NATIVE (comparison only): AIZ1_Deform's input-independent path matches all its PCs in the 28 super-ending buckets;
  ApplyDeformation matches on entry/exit PCs in 28 (S3 copy) + 14 (S&K copy) buckets; the AIZ1_ApplyDeformWater dry
  branch is a TEST-ORACLE gate. ApplyDeformation's body, the water split and MakeFGDeformArray/ApplyFGandBGDeformation
  are native-absent or input-dependent (the capture holds no Camera_Y_pos_BG_copy, Water_level or HScroll_table).

## Production input contract (engine-owned; never native PCs or counts)
Camera_X_pos_copy, Camera_Y_pos_copy, Camera_Y_pos_BG_copy (camera / zone BG owner), Water_level (water owner),
Level_frame_counter (level), HScroll_table contents and the band list / scroll array pointers the caller passes
(zone deformation owner: Ending_BackgroundEvent routine 0/4 -> S&K ApplyDeformation with Ending_BGDeformArray1/2 and
HScroll_table+4; routines 8/$C/$10 -> AIZ1_Deform, routine $10 also AIZ1_ApplyDeformWater). Outputs:
H_scroll_buffer longs, HScroll_table, Camera_Y_pos_BG_copy.

## Still open
Ending_BackgroundEvent_Index and the Ending_ScreenEvent routines themselves (the callers), Apply_FGVScroll and other
zones' deformation callers; next largest common term in the ending buckets: palette fade (Pal_DecColor 243 +
Pal_ToBlack 114 instructions/bucket); largest single routine: Ending_ScreenEvent_Index (662).
