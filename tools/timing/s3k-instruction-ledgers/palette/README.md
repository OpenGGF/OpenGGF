# Palette fade ledger (kos46 research R17, 2026-10-17, unexecuted in the engine)

> Frozen at R17 qualification (2026-10-17). The research sources were copied and hash-bound before promotion; the
> copies here differ from them only in import paths, directory names and the provenance header. Research sha256:
> `palette_cost.py` `5566d4f68202c8cb035d9cea1f7f985e8585884a0a2e379d72cce618b057a84d`,
> `palette_reference.py` `cd729c412c5c9b4a03669c1064ce9f93516f27e6368cd7b1bcecce54aa5d4aaf`,
> `test_palette_cost.py` `5a561a11d4f4bdbd3d30a578e8845b9d4d5a381ec1defe325d1a3dbe4837d81a`,
> `README.md` `38437f9ba2eb9ea3ab452ce7a6fece01b148ae46cc2b57bdfa2e0c4fb9b0a774`,
> `../mini68k/palette_forms.py` `906a1f63877f27e5c48e22ea52446b2775b429d1d788f4169d84b9c1166a4833`,
> `../mini68k/test_palette_forms.py` `059176692c0092fbf68f93120dc7d64728af70fe32e143220b42465d3fe43b22`.
>
> Scope: ROM `$3AF0`–`$3DBE` only, priced from palette-owner inputs. The native side witnesses only loop control
> (Pal_ToBlack's range-dependent PCs) and the negative-timer return of Animate_Palette; colour branches are not
> witnessed. This is no full hardware claim and no claim on the six ending service counts or 46 fingerprints.

ROM SHA-1 CFBF98C3..., pinned GPGX 051d430d instruction table (Motorola cycles equal on every step-routine test),
shipped FixBugs = 0. Instruction time only. No Java; the Java DMA/consumer owner owns production. The frozen
render and deform helpers are used unchanged; new forms live in the `../mini68k/palette_forms.py` wrapper.

## Source provenance (044fa46 sonic3k.asm line; the 1a454a0 checkout is 32 lines lower in this region; ROM address)
Pal_FadeFromBlack 4935 $3AF0, Pal_FromBlack 4953 $3B14, Pal_AddColor 4987 $3B54, Pal_FillBlack 5023 $3B80,
Animate_Palette 5050 $3BB8, Pal_FadeToBlack 5074 $3BE4, Pal_ToBlack 5091 $3C04, Pal_DecColor 5117 $3C32, Pal_FillWhite 5152
$3C62, Pal_FadeFromWhite 5171 $3C82, Pal_FromWhite 5189 $3CA4, Pal_DecColor2 5226 $3CF0, Pal_FadeToWhite 5264 $3D30,
Pal_ToWhite 5281 $3D50, Pal_AddColor2 5307 $3D7E (ends $3DBE). Read with `git show 044fa46:sonic3k.asm`; the extract was
deleted afterwards.

| File | Role | Tests |
|---|---|---|
| `../mini68k/palette_forms.py` | adds SUBI.B/W #imm to Dn/(An)/(An)+/d16(An) and MOVE #imm,SR over frozen deform_forms | `../mini68k/test_palette_forms.py` (4, old-red) |
| `palette_cost.py` | micro-executes the seven step routines and the four blocking fades | `test_palette_cost.py` (6) |
| `palette_reference.py` | independent per-colour rules (comparison oracle only) | used by the tests |

## Closed terms (source constants)
* Pal_DecColor per colour: 42 (black), 64 (red step), 90 (green), 112 (blue). Pal_ToBlack over 64 + 64 colours =
  2 x (44 + 64 x (18 + per colour) + 63 x 10 + 14) + 16. Animate_Palette with Palette_fade_timer < 0: 38.
* Semantics kept: ToBlack/ToWhite always step the water copy; FromBlack/FromWhite/FillBlack only with Water_flag;
  Pal_AddColor's whole-word compares let a finished component carry (+2 when blue and green steps overshoot);
  Pal_DecColor2 keeps only blue in the high byte; FromWhite runs every third call; FillBlack fills white for zone/act
  $0D01 and $1701 (ROM carve-out, not an engine rule); fading to black takes up to 21 steps.
* Blocking fades: per iteration 16 + 18 + Wait_VSync(16 + 12N + 10(N - 1) + 8 + 16) + 18 + step + 18 + dbf, with N
  the Wait_VSync tests (timing-model output); VInt_12 and Process_Nem_Queue_Init are separate terms.
* Refused: Animate_Palette with timer 0 (jmp AnPal_Load, zone palette cycling, open); a Palette_fade_info range past
  the 64-colour palette (would touch the following RAM).

## Evidence classes
* SOURCE/SYNTHETIC: micro-execution equals the reference on both palettes, the timer and Pal_fade_delay2 for all
  seven routines over 120 random states (colour-only and arbitrary words, ranges, Water_flag, zone/act, timers),
  multi-step fades, the four blocking fades (palette result and hand-summed skeleton), hand sums, mutations (red mask,
  AddColor branch, pinned opcode) and refusals; every colour path, carve-out and delay branch is reached.
* NATIVE (comparison only): Pal_ToBlack's range-dependent control PCs match in the 18 buckets that run it (range from
  the caller's source constant move.w #$40-1,(Palette_fade_info).w, 044fa46 121001/121126, caller owned by the
  ending-events worker); Animate_Palette's timer < 0 return matches in all 42 buckets as a TEST-ORACLE gate. Colour
  branches depend on palette RAM, absent from the capture. FromBlack, the white fades and the blocking fades are
  native-absent in the ending buckets.

## Production input contract (palette owner)
Normal_palette $FC00, Target_palette $FC80, Water_palette $F080, Target_water_palette $F000 (64 words each),
Palette_fade_info $F626 (offset) / $F627 (count - 1), Water_flag $F730, Current_zone_and_act $FE10, Palette_fade_timer
$EE50, Pal_fade_delay2 $FACE; for blocking fades the Wait_VSync count per iteration from the timing model. Outputs:
both palettes, Palette_fade_timer, Pal_fade_delay2.

## Still open
AnPal_Load and zone palette cycling; the callers (Ending_ScreenEvent family, parallel worker). Next common term in
the ending buckets: plane-buffer producers (Setup_TileRowDraw, sub_4EA4A, Draw_TileColumn/Row, DrawTilesAsYouMove,
Draw_BG/Draw_BGNoVert; about 130 instructions/bucket).
