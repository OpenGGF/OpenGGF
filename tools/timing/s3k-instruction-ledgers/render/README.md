# Sprite renderer ledger (kos46 research R15, 2026-10-17, unexecuted in the engine)

Source: skdisasm **044fa46** via `git show` (passes 1-13 cited the local checkout 1a454a0; converted on promotion), shipped FixBugs = 0,
ROM SHA-1 CFBF98C3..., pinned GPGX 051d430d table (Motorola cycles equal on the 60 real-mapping scenes; other
scenes are checked on GPGX only). Instruction time
only: the SAT DMA, VDP FIFO and all hardware terms stay separate. No Java; the Java DMA/consumer owner owns production.

| File | Role | Tests |
|---|---|---|
| `../mini68k/render_forms.py` | mini68k forms the renderer needs (ORI/ANDI/EORI.B/W, SUBI.W Dn, BTST #,Dn, AND.W/CMP <ea>,Dn, CMPA, ADDA.W <ea>, NEG.W Dn, dbcc expired/fall) | `../mini68k/test_render_forms.py` (6: opcode/flag/EA, refusals) |
| `render_cost.py` | micro-executes Render_Sprites ($1AD20), sub_1AF6C writers (plain/x/y/xy), sub_1B070 clipped multi-draw writers (plain/x/y/xy), Draw_Sprite ($1ABC6) over a private RAM model built from engine inputs | `test_render_cost.py` (10) |
| `render_reference.py` | independent renderer from the mapping format and stated rules (comparison oracle only) | used by the tests |

Env: S3K_ROM, GPGX_GIT, KOS46_NATIVE_ROOT.

## Closed terms (source constants)
* Empty frame (8 empty lists, Level_started_flag 0, no Spritemask): 2858 = head 78 + levels 618 (level 0 98 with
  the LRZ zone test, 74 each for 1-6, 76 for 7) + 80-entry clear loop 2100 + tail 62.
* One more unclipped piece costs 116 (plain), 150 (x-flip), 154 (y-flip), 180 (xy-flip), and saves one clear
  iteration (26). Multi-draw pieces are priced by execution (clip outcome per piece).
* Draw_Sprite: 92 to append; +62 per full level passed over; dropped when level 7 is also full (516 from level 0).

## Semantics kept (ROM, FixBugs = 0)
* Order: levels 0..7, each list in Draw_Sprite order; the list counts are drained to 0.
* on-screen bit 7 of render_flags is set whenever the cull passes, even when the 80-entry SAT budget is exhausted
  (objects read it for logic); pieces stop when the budget goes negative; unused entries get y = 0 only (size, tile,
  x and link bytes keep their previous contents); Sprites_drawn = $50 - entries left.
* Cull (render_flags bit 2 set): x = x_pos - camera, hidden if x + width < 0 or x - width >= 320 (signed); y =
  (y_pos - camera + height) & Screen_Y_wrap_value, hidden if >= 2 * height + 224 (unsigned). Bit 2 clear: x/y are
  screen coordinates, no cull (multi-draw: screen-coordinate cull around +128).
* Bit 5: static mappings (one piece at the mapping pointer). Frame table offsets are signed words.
* Flip writers: x-flip tile ^ $800, x = -x - width(size); y-flip tile ^ $1000, y = -y - height(size); both ^ $1800.
  Unclipped writers turn x & $1FF == 0 into 1. Size bytes index 16-byte ROM tables; values above $F would read
  past them (the reference refuses; shipped mapping tables contain unused garbage frames that real objects do not
  select).
* Multi-draw (bit 6): main frame 0 draws nothing; up to 8 child records (x, y, pad, frame) from sub2_x_pos; each piece
  is clipped: y outside 97..351 skips it (the y-flip writer has already stored its size byte), x outside 97..447
  rewinds the entry after y/size/tile were stored; clipped pieces do not consume the budget.
* Spritemask (Spritemask_flag): cleared unless Player_1 routine >= 6; scans tile words for $7C0 and writes x = 1 to
  that entry and x = 0 to the next.
* Fail closed (OpenPath): Competition_mode, Level_started_flag (Render_HUD/Render_Rings write SAT entries and move
  d7/a6), Current_zone 9 after level 0 (sub_1CB68); more than 63 entries in a list; more than 8 child records.

## Evidence classes
* SOURCE/SYNTHETIC: micro-execution equals the independent reference (SAT bytes, Sprites_drawn, on-screen flags,
  drained lists) on 60 random scenes over real ROM mappings (Map_Sonic, Map_Tails, Map_Ring, Map_Monitor,
  Map_Spring; valid frames only), synthetic mapping blobs (static, x == 0 in all four writers, 120-piece budget
  overflow, 96 multi-draw scenes across all eight flag combinations with 0-8 children, Spritemask), hand sums,
  Draw_Sprite overflow/drop, mutations (x-flip width table entry, x == 0 fix-up branch, pinned opcode) and refusals.
* NATIVE (comparison only): 42 ending buckets match on the 25 display-list-independent PCs; Level_started_flag 0
  and Spritemask_flag 0 are TEST-ORACLE gate values taken from the bucket's path. The buckets execute the plain and
  x-flip writers and multi-draw plain/x-flip; the y/xy writers and Spritemask are native-absent.

## Production input contract (all engine-owned; never native PCs or counts)
| Input | RAM | Owner |
|---|---|---|
| Display lists in Draw_Sprite order (per priority level, overflow to lower levels, drop when level 7 is full) | Sprite_table_input | object manager (Draw_Sprite calls in physical slot order, including the split loop's draw-only slots) |
| render_flags (bits 0/1 flip, 2 coordinate mode, 5 static, 6 multi-draw), x_pos, y_pos, width_pixels, height_pixels, mappings, mapping_frame, art_tile, priority, mainspr_childsprites and child records | SST | object owners |
| Camera_X_pos_copy, Camera_Y_pos_copy, Screen_Y_wrap_value | camera / level | camera owner |
| Level_started_flag, Current_zone, Competition_mode, Spritemask_flag, Player_1 routine | level / player | level, player |
| SAT prior contents (links, bytes not rewritten) | Sprite_table | renderer (persisting between frames) |
Outputs a production owner must reproduce: SAT bytes, Sprites_drawn, render_flags bit 7, drained lists.

## Still open
Render_HUD, Render_Rings, sub_1CB68 (LRZ), Render_Sprites_CompetitionMode; the Draw_Sprite producers (object
handlers). Next largest common source term in the ending buckets: the deformation family (S3-half ApplyDeformation2
359, ApplyDeformation2 203, S3:AIZ1_Deform 97 instructions/bucket), then Ending_ScreenEvent_Index (662, ending-only).
