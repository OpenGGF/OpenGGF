# Sitar Hero: complete Sonic 3 & Knuckles ROM song catalogue

Implementation branch: `feature/ai-sitar-hero-full-s3k`, isolated worktree
`.worktrees/ai-sitar-hero-full-s3k`, based on shared contract `cabd66f44`.
Parent integration owns shared curation, duration expansion, aggregate inventory,
frozen program snapshot compatibility, changelog, delivery and worktree cleanup.
The worker also owns the general S3K bank track-address-space repair. This worker does not push or integrate.

## Source and method

The input is the existing main-checkout `s3k.gen`, passed by absolute path
(shown below as `<absolute-main>/s3k.gen` to keep machine-local paths out of source),
the locked-on
retail ROM: CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
No ROM copies, replacement links, playlist audio, waveform fitting or disassembly
asset fallback are used. The checkout hook created routine ROM links on worktree
creation; this worker removed those generated links and uses the absolute main ROM.
The read-only disassembly reference is `skdisasm` commit
`1a454a0e335137a1a016d1090a9f4528d36944cf`; its pre-existing unrelated dirty
assets/tools remain preserved. The music scores, driver and equates consulted here
are unchanged in that reference checkout.

The owning music-bank and pointer lists are `z80_MusicBanks` and
`z80_MusicPointers` in `docs/skdisasm/Sound/Z80 Sound Driver.asm`, with the
`SonicDriverVer==3` branches and S&K-only `Snd_SKCredits` slot. The locked-on loader
selects the S&K table for IDs `0x001..0x033` and the S3 table for
`0x101..0x132`: see `Sonic3kSmpsLoader.parseS3MusicTables`,
`loadS3Music`, `findMusicOffset` and `findS3MusicOffset`. A display-name enum is
not proof that two entries contain different music. Inventory covers all 101
loader-supported slots, compares ROM addresses and production stream progression,
and retains every distinct substantive variant actually present.

Each owning score is the corresponding file under `docs/skdisasm/Sound/Music`.
The S&K include owners are `sonic3k.asm:Snd_*`; S3 variants use the matching
`Sonic 3` score and the native S3-table ROM offset below. Scores are research,
never runtime data. Musical grouping here is 24 duration units per quarter:
`$60` is four quarters, `$30` two, `$18` one, and `$0C/$06` subdivisions. Pickups
and shipped data bugs can produce fractional quarters; they are retained in exact
`NativeForm`, not hidden by claiming a rounded beat count is a native boundary.

The recurring [SitarHeroS3kSongProbe](../../../src/main/java/com/openggf/tools/SitarHeroS3kSongProbe.java)
executes actual loader bytes through `SmpsSequencer.serviceOuterFrame` and the
production `Sonic3kCoordFlagHandler`. Native acceptance uses `inspectBank`: the
ROM-backed complete bank address space retains cross-song calls before the current
header. `inspect` separately reports the production loader slice. It observes F6 GOTO source/target, return depth,
progressed duration units, FF 00 tempo changes, attacks and every track's stop.
F7 counted pattern loops and F8/F9 call/return repetition are not song boundaries.
A channel stuck inside a never-returning call can still have an owning song loop:
return depth alone is not a reliable classifier. Source plus all-channel progression
establishes which candidate describes the complete arrangement.

`zBGMLoad` seeds the accumulator with the header tempo. `TempoWait` adds
`zCurrentTempo`; an 8-bit carry increments all duration timeouts, cancelling that
service's duration decrement. The admitting service walks tracks without another
accumulation. These are the production S3K config and sequencer's documented
`tempoWaitPrecedesRequest`, `primeFirstService` and `musicUpdateOverflow` semantics.
Frame counts are observed service progression, independently checked against
these native duration units and the retail overflow clock. A nominal loop service
span is the ceiling of `units * 256 / (256 - tempo)`; this includes the native
integer-service boundary instead of rounding a loop shorter. Tests follow native streams to their complete stops or the ten-minute bound
and require each active part’s complete second arrangement jump at or before
the chosen exclusive PCM endpoint, as well as the exact DAC introduction/period. Data Select
returns at service 7224, precisely the end of its 7224-frame preparation; observing
the return does not require extending playback into the next loop.

`FixBugs=0` / `FixMusicAndSFXDataBugs=0` is the shipped branch. An initial temporary
score traversal read both branches of source conditionals and reported spurious
extra periods (notably Competition Menu 2688 versus actual 2304 units). Selecting
only the shipped branch removed that error; ROM-driver progression remains the
acceptance source. This traversal stays temporary because it is easily regenerated.
Another rejected measurement was filling first-visit maps for every pointer between
two track services: a call/jump can cross unrelated stream offsets, so that range
cannot prove when a loop target was first visited. The committed probe reports
actual executed coordination boundaries instead.

## Provider research inventory and playable selection

The provider retains **50 researched songs**, including the two title themes for
source/form evidence. The parent playable library contains **48**: its consistent
cross-game policy excludes the short finite S3 and S&K title cues (`0x125`,
`0x025`). Endings and credits remain selected; Sonic 3 Credits loops natively.
Thus playable selection has 45 looping songs and three natural finite ending/credits
songs; research also retains both finite titles.

`I/L/E` are exact progressed intro/complete-loop/natural-stop duration units.
`Fi/Fl/Fe` are catalogue intro/loop/end service frames. `Play` is
`max(7200, Fi + 2*Fl)` for looping songs, or `Fe` for non-looping songs. The native
second jump can differ by one service from a ceiling interval because the tempo
accumulator retains its phase. Longer IceCap forms run well beyond two minutes;
all forms fit the ten-minute preparation ceiling.

`L/R/S` list zero-based logical lead/rhythm/PSG channels; `+` is a genuine selected
harmony voice, not a synthesized pitch. Sections with hand-offs are explained below.
All included songs have real mapped DAC attacks. The seven DAC durations in the
provider are the first seven *inter-attack* progressed units, including intervening
rests and ties; they are not assumed to equal the first seven bytes or notes.

| Music ID / ROM offset | Stable ID / label | I/L/E | Fi/Fl/Fe | Play | L/R/S |
|---|---|---|---|---|---|
| `0x001` / `0x2C8000` | `angel-island-1` — Angel Island Zone Act 1 | 0/3456/0 | 0/3933/0 | 7866 | 1/3+4/0+1; sections |
| `0x002` / `0x2C9B6D` | `angel-island-2` — Angel Island Zone Act 2 | 0/3456/0 | 0/3765/0 | 7530 | 1/3+4/0+1 |
| `0x003` / `0x2CB0BC` | `hydrocity-1` — Hydrocity Zone Act 1 | 0/2304/0 | 0/3410/0 | 7200 | 0/3+4/0+1 |
| `0x004` / `0x2CC0C6` | `hydrocity-2` — Hydrocity Zone Act 2 | 0/2688/0 | 0/3143/0 | 7200 | 0/3+4/0+1 |
| `0x005` / `0x2CD364` | `marble-garden-1` — Marble Garden Zone Act 1 | 0/2688/0 | 0/3584/0 | 7200 | 1/2+3/absent; sections |
| `0x006` / `0x2CD97B` | `marble-garden-2` — Marble Garden Zone Act 2 | 0/2688/0 | 0/3529/0 | 7200 | 1/2+3/absent; sections |
| `0x007` / `0x2CE48F` | `carnival-night-1` — Carnival Night Zone Act 1 | 0/2976/0 | 0/4053/0 | 8106 | 0/2+3/0+1 |
| `0x008` / `0x2CDDA9` | `carnival-night-2` — Carnival Night Zone Act 2 | 0/2976/0 | 0/4053/0 | 8106 | 0/2+3/0+1 |
| `0x009` / `0x0E8000` | `flying-battery-1` — Flying Battery Zone Act 1 (S&K) | 24/2400/0 | 24/2400/0 | 7200 | 0/2+3/absent |
| `0x00A` / `0x0E8597` | `flying-battery-2` — Flying Battery Zone Act 2 | 96/2400/0 | 96/2400/0 | 7200 | 0/2+3/absent |
| `0x00B` / `0x2D06AA` | `icecap-1` — IceCap Zone Act 1 | 0/4608/0 | 0/4999/0 | 9998 | 1/3+4/0+1 |
| `0x00C` / `0x2D0000` | `icecap-2` — IceCap Zone Act 2 | 0/5376/0 | 0/5832/0 | 11664 | 1/3+4/0+1 |
| `0x00D` / `0x2D1345` | `launch-base-1` — Launch Base Zone Act 1 | 481/2784/0 | 590/3411/0 | 7412 | 0/2+3/0+1 |
| `0x00E` / `0x2D0DC8` | `launch-base-2` — Launch Base Zone Act 2 | 481/2784/0 | 590/3411/0 | 7412 | 0/2+3/0+1 |
| `0x00F` / `0x0E8AFE` | `mushroom-hill-1` — Mushroom Hill Zone Act 1 | 144/1920/0 | 186/2470/0 | 7200 | 0/2+3/absent; sections |
| `0x010` / `0x0E9106` | `mushroom-hill-2` — Mushroom Hill Zone Act 2 | 96/1920/0 | 119/2364/0 | 7200 | 0/2+3/absent; sections |
| `0x011` / `0x0E9688` | `sandopolis-1` — Sandopolis Zone Act 1 | 384/2304/0 | 414/2479/0 | 7200 | 0/2+3/0 |
| `0x012` / `0x0E9CF2` | `sandopolis-2` — Sandopolis Zone Act 2 | 384/2304/0 | 439/2634/0 | 7200 | 0/2+3/absent |
| `0x013` / `0x0EA2E5` | `lava-reef-1` — Lava Reef Zone Act 1 | 0/1920/0 | 0/2255/0 | 7200 | 1/3+4/0+1; sections |
| `0x014` / `0x0EACF3` | `lava-reef-2` — Lava Reef Zone Act 2 / Hidden Palace | 288/1920/0 | 360/2398/0 | 7200 | 0/3+4/0+1 |
| `0x015` / `0x0EBE80` | `sky-sanctuary` — Sky Sanctuary Zone (S&K) | 0/3456/0 | 0/3511/0 | 7200 | 4/2/0 |
| `0x016` / `0x0EC2B4` | `death-egg-1` — Death Egg Zone Act 1 | 0/2304/0 | 0/2379/0 | 7200 | 0/2+3/absent |
| `0x017` / `0x0EC79F` | `death-egg-2` — Death Egg Zone Act 2 | 0/2304/0 | 0/2304/0 | 7200 | 0/2+3/absent |
| `0x018` / `0x0ECBB1` | `s3k-miniboss` — Mini-Boss (S&K) | 96/1536/0 | 96/1536/0 | 7200 | 0/2+3/absent |
| `0x019` / `0x0ECEE1` | `s3k-boss` — Zone Boss | 0/1920/0 | 0/1920/0 | 7200 | 0/2+3/0 |
| `0x01A` / `0x0ED3DD` | `doomsday` — Doomsday Zone | 768/2832/0 | 800/2948/0 | 7200 | 0/2+3/0+1 |
| `0x01B` / `0x0EDCC0` | `s3k-pachinko` — Bonus Stage — Pachinko | 192/2688/0 | 211/2941/0 | 7200 | 0/2+3/0 |
| `0x01C` / `0x0EE223` | `s3k-special-stage` — Blue Sphere Special Stage | 288/3264/0 | 337/3816/0 | 7969 | 0/2+3/0+1 |
| `0x01D` / `0x0EEABB` | `s3k-slots` — Bonus Stage — Slots | 384/2304/0 | 439/2634/0 | 7200 | 1/2+3/0+1 |
| `0x01E` / `0x2D8AE8` | `s3k-gumball` — Bonus Stage — Gumball | 192/1536/0 | 218/1740/0 | 7200 | 2/3/0+1 |
| `0x01F` / `0x0EF5A3` | `s3k-knuckles` — Knuckles’ Theme (S&K) | 0/864/0 | 0/922/0 | 7200 | 0/3+4/0+1 |
| `0x020` / `0x2D99F7` | `azure-lake` — Azure Lake (Competition) | 96/3264/0 | 96/3264/0 | 7200 | 0/2+3/0 |
| `0x021` / `0x2DA4FD` | `balloon-park` — Balloon Park (Competition) | 48/3072/0 | 50/3172/0 | 7200 | 0/3+4/0+1 |
| `0x022` / `0x2DB0EC` | `desert-palace` — Desert Palace (Competition) | 0/2302/0 | 0/2667/0 | 7200 | 1/3+4/0+1 |
| `0x023` / `0x2DC324` | `chrome-gadget` — Chrome Gadget (Competition) | 0/3072/0 | 0/3592/0 | 7200 | 1/3+4/0+1 |
| `0x024` / `0x2DDA47` | `endless-mine` — Endless Mine (Competition) | 96/3072/0 | 108/3450/0 | 7200 | 0/2+3/0 |
| `0x025` / `0x0EF88E` | `s3k-title` — Title Theme (S&K) | 0/0/1140 | 0/0/1141 | 1141 | 0/3+4/0+1 |
| `0x026` / `0x2DE587` | `s3-credits` — Sonic 3 Credits | 2784/2784/0 | 2921/2921/0 | 8763 | 2/1/0+1 |
| `0x02D` / `0x2DF5E4` | `s3k-competition-menu` — Competition Menu | 0/2304/0 | 0/3241/0 | 7200 | 2/3+4/1 |
| `0x02F` / `0x0E67AF` | `s3k-data-select` — Data Select (S&K) | 42/2688/0 | 56/3584/0 | 7224 | 1/3+4/0+1 |
| `0x030` / `0x0E774C` | `s3k-final-boss` — Final Boss | 636/1668/0 | 964/2527/0 | 7200 | 0/2+3/0+1 |
| `0x032` / `0x0E7CDE` | `s3k-ending` — S&K Ending / Game Complete | 0/0/594 | 0/0/614 | 614 | 0/3+4/0+1 |
| `0x033` / `0x0E4104` | `s3k-credits` — S&K Final Credits | 0/0/9588 | 0/0/10248 | 10248 | 1/3+4/0; sections |
| `0x109` / `0x2CEBF1` | `flying-battery-1-s3` — Flying Battery Zone Act 1 (S3) | 24/2400/0 | 24/2400/0 | 7200 | 0/2+3/absent |
| `0x115` / `0x2D4B29` | `sky-sanctuary-s3` — Sky Sanctuary Zone (S3) | 0/3456/0 | 0/3511/0 | 7200 | 3/2/0 |
| `0x11F` / `0x2D97FD` | `s3-knuckles` — Knuckles’ Theme (S3) | 1536/1536/0 | 2081/2081/0 | 7200 | 0/1/absent |
| `0x125` / `0x2DE18F` | `s3-title` — Title Theme (S3) | 0/0/965 | 0/0/973 | 973 | 0/3/2 noise |
| `0x12E` / `0x2C71A0` | `s3-miniboss` — Mini-Boss (S3) | 0/2688/0 | 0/3661/0 | 7322 | 2/3+4/0 |
| `0x12F` / `0x2D7027` | `s3-data-select` — Data Select (S3) | 42/2688/0 | 56/3584/0 | 7224 | 1/3+4/0+1 |
| `0x132` / `0x2DFBFE` | `s3-ending` — Sonic 3 Ending / Game Complete | 0/0/570 | 0/0/609 | 609 | 0/2+3/2 noise |

The catalogue's integer beat fields are conservative covering counts when a pickup
is fractional: Data Select intro 42 units = 1¾ quarters; Final Boss 636 + 1668 =
26½ + 69½ quarters; Desert Palace 2302 units = 95 11/12 quarters. Parent curation
should consume `nativeForm(id)` for exact form wrapping. Constant-tempo songs
otherwise need no tempo-anchor override. This additive worker metadata leaves the
shared `SongArrangement` record unchanged.

## Form and bank details

- **Zone acts:** all 24 distinct stock zone/act songs (AIZ through DEZ, both acts,
  plus SSZ and DDZ) are included, including both CNZ arrangements and both longer
  IceCap arrangements. LRZ2 also serves Hidden Palace; SSZ and DDZ share the same
  song across their stage entries, so those are not duplicated as second acts.
  Five competition songs, Blue Sphere and all three bonus-stage songs are included
  as substantive musical forms.
- **Both halves:** the seven additional substantive S3 variants are `0x109`,
  `0x115`, `0x11F`, `0x125`, `0x12E`, `0x12F`, `0x132`. The S3 FBZ1 echo's shipped
  repeated timing differs from S&K; SSZ changes melodic-double channel ownership
  and DAC timing; S3 Knuckles is the stripped miniboss motif with its complete DAC
  verse, not just the tiny FM vamp. Data Select differs in native instrumentation
  and PSG behavior even though its high-level form matches. Both titles and both
  game-complete songs are distinct finite forms.
- **Launch Base forms:** DAC/FM intro is 384 native units, but
  `Snd_LBZ1_PSG1/Snd_LBZ2_PSG1` introduces three 96-unit rests plus its 192-unit
  phrase before the loop (480 units); PSG2 adds the shipped one-unit echo delay.
  The complete arrangement therefore has 481 intro units and a 2784-unit loop:
  590 + 2×3411 = **7412** service frames. DAC's second return is service 7290,
  PSG1's 7408 and PSG2's 7409. The rejected DAC-only 7293-frame preparation cut
  the selected synth part's second form short. `dacIntroUnits(id)` retains 384
  for independent DAC control-flow checks while `nativeForm(id)` covers all parts.
- **Sonic 3 credits (`0x026` / `0x126`):** the same bytes in both tables. DAC has a
  2784-unit introduction plus a 2784-unit outer repeat; melodic voices have their
  own entry phasing. The first DAC F6 is at unit 5568, service 5841; its second is
  unit 8352, service 8762. It does not naturally stop. The 8763-frame finite
  arrangement completes the introductory form and two subsequent complete repeats;
  it does not manufacture a loop for a non-looping stream.
- **Completion/title stops:** S&K ending `0x032` naturally stops at service 613
  (614 frames inclusive), S3 ending `0x132` at service 608 (609 frames inclusive).
  S&K title `0x025` stops at service 1140 (1141 frames); S3 title `0x125` at service
  972 (973 frames). Both endings are playable with their native cadence and envelope/rest tails.
  The title metadata remains research evidence; the parent excludes these short
  finite presentation cues consistently with its S1/S2 selection.
- **S&K final credits (`0x033`):** no F6 song loop; its counted phrase repeats are
  part of a finite medley. All nine native tracks stop. DAC stops last at native
  unit 9588, service 10247, so playback is 10248 frames from start to the inclusive
  natural stop. The medley successively quotes Knuckles, Mushroom Hill, Lava Reef,
  Flying Battery, Sandopolis, Death Egg, Sky Sanctuary and Game Complete. The
  score is followed through its final cadence, not stopped after the opening theme.

## S3 ending cross-song call gap

`Game Complete (Sonic 3).asm:Snd_S3_PresSega_FM3/FM4` calls the earlier title score.
The native ROM header is `0x2DFBFE`; the real FM3 calls are:

| Call ROM address | Z80 target | Target ROM address | Owning score routine |
|---|---|---|---|
| `0x2DFD2D` | `$E363` | `0x2DE363` | `Snd_S3_Title_Call04` |
| `0x2DFD30` | `$E398` | `0x2DE398` | `Snd_S3_Title_Call05` |
| `0x2DFD39` | `$E3C7` | `0x2DE3C7` | `Snd_S3_Title_Call06` |
| `0x2DFD8F` | `$E435` | `0x2DE435` | `Snd_S3_Title_Call00` (FM4) |
| `0x2DFD97` | `$E43B` | `0x2DE43B` | `Snd_S3_Title_Call01` (FM4) |
| `0x2DFD9F` | `$E435` | `0x2DE435` | `Snd_S3_Title_Call00` (FM4) |

The `cabd66f44` production loader retains the complete bank only for voices,
while `SmpsSequencer.readJumpPointer/relocate` rejects the negative song-relative
call destinations. That kills FM3 at service 102/native unit 96 (8 attacks),
FM4 at 105/unit 99 (8 attacks), and falsely reports completion at 514 frames.
The native bank probe gives FM3 48 attacks through service 608/unit 570, FM4
72 attacks through service 515/unit 483, and the actual inclusive end **609**.
The catalogue keeps this true end and actual complete rhythm owners. The worker
repairs `Sonic3kSmpsData` generally: when a loaded bank is present, execution length,
byte reads, word reads and relocation base select that bank. Header parsing and
local voice lookup keep the original raw song bytes/address; `getData()` retains
that legacy raw-header contract. No music-ID/name exception, copied/patched asset
bytes or public API change is introduced.
All 101 bank entries were compared. S3 Ending is the only changed natural end;
Launch Base 1 (`0x00D/0x10D`) also recovers its otherwise omitted PSG3 root at
Z80 address `$90FF` (ROM `0x2D10FF`), before its `$9345` header. That track has 4407 native noise
attacks in the 36001-service observation. No other source count changes. Native music still comes exclusively from loaded ROM bytes.

The consumer audit found all direct track execution uses `SmpsProgramView` and
`read16`. `SmpsSourceDescriptor` and `SmpsAssetCatalog.FrozenSmpsData` additionally
consume `getData()`: the original frozen wrapper copied the raw song blob and
sized its word table from that blob. Parent owns the required semantic program
snapshot/identity reconciliation so frozen playback preserves complete bank
coordinates while legacy header access remains meaningful. This dependency was
reported before modifying the data class. The existing full-bank control-flow
inventory already uses the bank getters; its raw-header scans retain their contract.

## Exact credits clock

`cfSetTempo` / meta-command `FF 00` in `Snd_SKCredits_DAC` owns these changes.
The anchor beat field uses a divisor of four returned by
`tempoAnchorBeatDivisor("s3k-credits")`; divide by four before using the neutral
shared quarter clock. This avoids rounding the pickup at quarter 130¼. Each
anchor's `unitsPerBeat` is 24. The final anchor is the exact last native stop,
not a request to restart or stretch the music. The rendered inclusive duration
is one service longer than its zero-based stop coordinate.

| Native units | Exact quarter beat | Anchor beat / divisor | Service frame | Native tempo |
|---|---|---|---|---|
| 0 | 0 | 0 / 4 | 0 | header `$0F` |
| 816 | 34 | 136 / 4 | 866 | `$1F` |
| 3126 | 130¼ | 521 / 4 | 3495 | `$08` |
| 4704 | 196 | 784 / 4 | 5124 | `$10` |
| 5472 | 228 | 912 / 4 | 5943 | `$08` |
| 6336 | 264 | 1056 / 4 | 6835 | `$0C` |
| 9588 | 399½ | 1598 / 4 | 10247 | natural stop |

Sonic 3 Credits `0x026/0x126` stays at header tempo `$0C` and loops natively.
S&K Ending `0x032` stays at `$08`; Sonic 3 Ending `0x132` stays at `$10`.
Neither ending has an `FF 00` change, so both return an empty tempo-anchor list.
Their exact last stop coordinates are `(594 units, 613 service)` and
`(570 units, 608 service)` respectively; playback includes that last service.
Both title themes likewise have constant native tempos (`$00` S&K, `$02` S3).

## Authentic roles and stage ownership

Angel Island 1 follows FM2 through beat 64, FM3 through beat 96, then the actual
FM2/FM3 duet. Marble Garden introduces FM3/FM4 and hands the lead to FM2 before
its chromatic pickup; both acts have no active PSG. Mushroom Hill starts on the
FM3/FM4 harmony and hands off to FM1 at the native melody pickup. Lava Reef 1
starts on its FM4/FM5 riff before FM2 enters. IceCap uses FM2 melody, not FM1's
bass; Chrome Gadget uses FM2/FM3 melody over FM1 bass. S3 miniboss uses FM3 lead
and PSG1, while S3 Knuckles has only two sounding FM tracks and no PSG. S3 title
and ending expose PSG3 as noise rhythm, never pitched synth notes.

The credits lead follows FM2 opening, FM1 Mushroom Hill, FM2 Lava Reef, FM1 Flying
Battery/Sandopolis/Death Egg, FM4 Sky Sanctuary, then FM1 ending. Its rhythm part
switches accordingly; PSG1 remains a real native line. Chorus chords require
actual distinct simultaneous source pitches in the parent curator; selecting
an echo/unison voice does not authorize inventing a harmony note.

DAC sample-note identities come from the retail driver's `zDACBanks`, drum pointer
list, descriptors and `_smps2asm_inc.asm` enumeration, decoded at runtime by
`Sonic3kSmpsLoader.loadDacData`. The catalogue does not substitute generic pitch
ranks for sample families. The provider exposes `drumLane(noteId)` for parent drum curation. It retains kick/snare/tom/cymbal,
metal-hit and hip-hop-hit families for S3-specific songs, ignoring spoken/ambient
samples where they do not represent a percussion hit. In particular `$B2/$B3` are echoed claps, not kicks: S3 Knuckles has a real clap part with no invented kick lane.

The `SonicDriverVer` cases 3 and 4 of `Sound/_smps2asm_inc.asm` give the same
numeric identities for the seven deliberately uncharted nonpercussion families:
`$A5 dComeOn`, `$A9 dWoo`, `$AA dGo`, `$B6 dBassHey`, `$BA dReverseFadingWind`,
`$BB dScratchS3` and `$BE dCrashingNoiseWoo`. Both cases name `$B2/$B3` echoed
claps (with an `_S3` suffix in case 3). Parent full-stream acceptance permits
`drumLane == -1` for exactly those seven actual attack families and requires every
other actual DAC attack to resolve to a real pad. Speech, scratches and atmospheric
effects remain audible ROM events without being fabricated into percussion notes.

Zone songs retain the native matching public zone ID and zero-based act from
`LevelMusic_Playlist`: 0 AIZ, 1 HCZ, 2 MGZ, 3 CNZ, 4 FBZ, 5 ICZ, 6 LBZ, 7 MHZ,
8 SOZ, 9 LRZ, 10 SSZ, 11 DEZ, 12 DDZ. The additional S3 FBZ1 and SSZ variants
retain their same genuine source stage. This worker does not change picture hosts.
On `cabd66f44`, `SceneRomArt` supports only AIZ1/AIZ2, HCZ1, LBZ1 and SSZ1.
The parent must account for missing stage-picture support for the other genuine
zone acts; falsely selecting another zone's image would mislabel those songs.

Non-zone songs explicitly use compatible concert stages: AIZ1 for S3 themes,
Blue Sphere, Gumball, most menus/competition songs; AIZ2 for Zone Boss; SSZ1 for
Pachinko, Slots, Desert Palace, Endless Mine, final boss and S&K ending/credits;
LBZ1 for Chrome Gadget. These are presentation stages, not claims that a bonus,
competition, menu or credits song originated in the pictured act. Every assigned
non-zone concert stage is within the existing picture host's supported set.

## Explicit exclusions and aliases

The eight excluded/aliased S&K slots below plus 43 excluded/duplicate S3 slots account
for all 101 supported bank entries alongside the 50 research entries. The parent
additionally excludes both researched title cues from its 48-song playable selection.

### Initial Knuckles-theme rationale (superseded at integration)

The integration decision excludes both character cues from the public tour under
the user's broader short-loop instruction. S&K repeats a short 36-quarter form;
S3 repeats eight-/four-quarter FM motifs over the repeated clap phrase. Complete
native forms and character-scene callers alone do not establish substantive song
development. The initial rationale below is retained as a rejected selection
approach; all 50 research forms and 101 bank-entry checks remain useful. The
public provider combination contains 46 S3K songs and 79 total. See the
[combined design](2026-10-07-sitar-hero-full-version.md).

This is a bounded musical selection judgment, not a general minimum-length rule.
The owning code selects `mus_Knuckles` for character scenes: S3
`CutsceneKnux_CNZ2A` / `loc_44C36` queues it after a fade; the locked-on
`CutsceneKnux_CNZ2A` / `loc_622E4`, `loc_63D1A` and `sub_65DD6` likewise select
it through the scene music/fade machinery. `sonic3k.asm:loc_622A` also selects it
for the standalone S&K Knuckles opening. These selections are narrative/background
music, not reward jingles or a temporary player power-up. By comparison,
`Player_ResetAirTimer` in both driver halves explicitly selects `mus_Invincibility`
from the invincibility status and Super flag. Invincibility/super music is excluded
by the user's stated functional category, even where it has several sounding parts.

S&K Knuckles (`0x01F`) has an 864-unit / 36-quarter complete outer form,
nominally 922 services (15.37 seconds). `Snd_Knux_Jump01` contains the extended
melody and its closing scalar run; FM echoes, a separate PSG melodic line, noise
and the DAC crash/clap introduction plus eight counted drum phrases all complete
that form. A short inner repeated phrase does not describe this arrangement.
S3 Knuckles (`0x11F`) is musically sparser: its two active FM parts repeat at
192 and 96 units (eight and four quarters), while all remaining FM/PSG parts stop.
Its authored DAC introduction calls `Snd_S3_Knux_Call00` four times (1536 units),
then `Snd_S3_Knux_Jump00` repeats a 1536-unit / 64-quarter verse composed of
`Call01` followed by three `Call00` calls. That complete percussion form takes
nominally 2081 services (34.68 seconds); the BassHey change remains audible but
is not a drum-pad note. Retention therefore rests on its complete character-scene
groove, with explicit limited instrumentation, rather than treating the first
FM motif return as the song boundary. Both tracks use the ordinary two-minute /
intro-plus-two-complete-forms policy. The parent still excludes the two short
finite title cues consistently across S1/S2/S3K.

| Slot(s) | Native identity | Reason |
|---|---|---|
| `0x025`, `0x125` | S&K / S3 Title (research entries) | Parent playable library excludes short finite title cues consistently across games; exact native forms remain in the provider evidence. |
| `0x027`, `0x127` | Game Over | Short failure/result cue. |
| `0x028`, `0x128` | Continue, distinct driver variants | Countdown/menu prompt cue with a 48-unit pickup and 384-unit vamp; does not develop into a substantive full menu song. |
| `0x029`, `0x129` | Act Clear | Result jingle; excluded explicitly. |
| `0x02A`, `0x12A` | Extra Life, distinct driver variants | 1-up jingle; excluded explicitly. |
| `0x02B`, `0x12B` | Chaos Emerald | Item/result jingle; no sounding DAC part. |
| `0x02C`, `0x12C` | Invincibility / super theme | Short power-up loops; expressly out of scope. |
| `0x031`, `0x131` | Drowning | Timed warning cue; expressly out of scope. |
| `0x02E` | S&K miniboss alias | Exact pointer alias of included `0x018`; `0x12E` is the distinct S3 song and is included. |

These S3 slots are exact pointer aliases of their corresponding included S&K
slot, so their full songs are available once with the correct native bytes:
`0x101..0x108`, `0x10A..0x114`, `0x116..0x11E`, `0x120..0x124`, `0x126`,
`0x12D`. In particular, `0x10B/0x10C` and `0x10D/0x10E` do not create
extra IceCap/Launch Base compositions in this retail locked-on ROM;
`0x126` is the already-included Sonic 3 credits stream. Both tables' `0x118` miniboss and the S&K `0x02E` alias have
not been counted as new compositions. No S3 `0x133` slot exists.

Final Boss `0x130` is a **relocated equivalent copy**, not an exact pointer alias:
its header is at ROM `0x2C7A61`, versus `0x0E774C` for included `0x030`.
Both `s3.asm:Snd_FinalBoss` and `sonic3k.asm:Snd_FinalBoss` include the same
`Sound/Music/Final Boss.asm`. All nine part roots and executed F6 source/target
coordinates differ by `$315` (789 bank bytes); tempo, native units, frames,
attacks, notes/voices, noise modes, DAC attacks, stops and return depth agree.
The full 36001-service signatures and relocated control-flow regression establish
that this copy introduces no distinct arrangement. It is represented once by
`s3k-final-boss`, with both native IDs explicitly accounted for.

## Verification and hand-off

The worktree is based on `cabd66f44`; checks below refer to this isolated worker
and its owned changes, not to the parent integration tree. Java is OpenJDK 21.0.12.1.
The absolute ROM was hashed before probing and matches the locked-on identity
above. Required engine-wide validation belongs to the parent combined delivery.

- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests compile -B`:
  **passed**, 3695 source files, 54.543 seconds execution after 249 seconds queued.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base cabd66f44 --preflight`:
  **passed** Java 21, Lua 5.4 and PowerShell. The preceding attempt without explicit
  `LUA_BIN` found the wrong Lua; that prerequisite was repaired before acceptance.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base cabd66f44`:
  inspected the actual selection: full ordinary suite, 3007 classes, plus guards,
  because the new provider/tool paths are not categorized. This was inspected
  before the bank repair expanded scope. The worker now exercises all 101 bank
  streams and focused header/voice/meta-command regressions. The user explicitly
  keeps broad baseline and combined runtime/host validation parent-owned; this
  worker does not repeat the baseline. These are focused checks, not a full-suite pass.
- `java -Xmx512m -cp target/sitar-research/classes:target/classes com.openggf.tools.SitarHeroS3kSongProbe <absolute-main>/s3k.gen`:
  **passed** actual complete-bank progression for all 101 loader-supported entries
  to natural stop or 36001 service frames. No ROM skips. Comparing the bounded
  production loader and the actual bank caught the S3 Ending cross-song gap above.
- A temporary `VerifyNativeBook` runner compiled the actual provider plus probe
  with `javac --release 21`, then inspected each of the 50 selected songs to its
  specified duration plus endpoint. After finding Launch Base’s delayed PSG entry,
  the runner was strengthened to inspect complete native streams and check the
  second complete form on **every active part** against the chosen duration.
  **Passed** 45 complete looping forms and five natural stops, with every authored
  primary channel active. The committed JUnit
  class contains the recurring version; the temporary runner/data stays in `target/`.

- The temporary `CompareNativeBanks` runner followed both complete production
  tables and the independent native bank view for 36001 services per entry:
  **passed all 101**, with identical source counts, attacks, note/voice signatures,
  tempo events, jumps and natural ends. S3 Ending now reaches 609 frames instead
  of the reproduced 514-frame truncation. The committed JUnit class repeats this
  recurring acceptance without relying on the temporary runner.

The initial catalogue-only Maven invocation reached Java 21 test compilation
but failed before JUnit: the ROM lookup in a non-throwing factory callback could
throw `IOException`. The test now resolves the ROM before constructing that
callback. No production code or metadata changed for this repair. The two other
waiting focused requests were cancelled before admission and consolidated with
the catalogue rerun; they do not count as executed validation.

The combined invocation was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSitarHeroS3kSongCatalogue,TestSonic3kBankTrackAddressSpace,TestSmpsHeaderConstruction,TestSmpsDataEndianParsing,TestSonic3kCoordFlagParity,TestSonic3kSmpsMetaCommandOperands,TestSonic3kSmpsMetaCommandReachability,TestFrozenSmpsDataImmutability,TestS3kVoiceResolution,TestSonic3kVoiceData' \
  -Ds3k.rom.path=<absolute-main>/s3k.gen test -B
```

**57 tests passed, zero skips**, in the nine bank/header/endian/coordination/voice/
frozen-data classes. Catalogue setup failed before its six tests: the real mod
packager rejected static object fields `ENTRIES` and `SONGS` under its existing
`STATIC_STATE_UNSUPPORTED` rule. The provider now constructs immutable entries
inside methods, as the sibling game providers do; no static object state, metadata
change or sandbox exception is introduced. Maven reported 58 checks, zero test
failures, one catalogue setup error, zero skips. These passing runtime regressions
remain applicable after the provider-only packaging repair.

The first catalogue-only rerun packaged successfully and ran all six tests with
zero skips. Five passed, including all-101-entry production/bank equivalence and
the real short preparation paths. The native duration test had already checked
all 50 complete forms before an incorrect assertion that Final Boss's two copies
shared a ROM pointer failed. The inventory now distinguishes the relocated copy
above, and the regression compares its actual event signatures and uniformly
relocated roots/jumps instead of claiming pointer identity.

The final catalogue rerun was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestSitarHeroS3kSongCatalogue \
  -Ds3k.rom.path=<absolute-main>/s3k.gen test -B
```

**Passed all six tests, zero failures/errors/skips**, 5.002 seconds JUnit and
28.440 seconds total execution. The mod packaged through the real validator.
This includes complete natural stops / two full arrangement returns on all 50
research entries; exact credits tempo boundaries; all 101 production/native bank
stream comparisons (including S3 Ending 609 versus the reproduced old 514);
the relocated Final Boss copy; explicit role absence; and seven real six-second
production preparations with authentic melodic/noise/DAC owners. Together with
the unchanged runtime regressions above, **63 distinct focused tests passed**.
The short preparations exercise real production role ownership, not acceptance
of full-duration PCM rendering on this pre-expansion base. Parent combined
validation owns that expanded-host and frozen full-bank path, matching stage
pictures, structural guards, broad suite, integration and cleanup. No worker
push or main-workspace integration occurred.
