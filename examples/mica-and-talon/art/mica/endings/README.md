# MICA ending-art inventory

Research only: no drawings, Java build, runtime wiring or visual certification.
`endings-inventory.yaml` is the handoff, read against W4/W6/W7 of
[the character design](../../../../../docs/architecture/designs/2026-11-08-original-character-mod.md).
Both Sonic archetypes share the same native main-character ending path; native Tails
pilots/companions remain separate. Standalone Sonic 3 and Knuckles/Tails-alone endings
are outside this locked-on route.

| Game | Character-bearing sequence entries | Bespoke native artwork slots | Existing playable bank | Estimated new drawings |
| --- | ---: | ---: | ---: | ---: |
| S1 | 11 | 8 | 88 | 8 |
| S2 | 10 | 18 | 214 | 23 |
| S3K | 7 | 11 | 251 | 25 |

Sequences are inventory units, not complete playthroughs: S1 includes eight credits
demos; S2 counts its four photo panels separately. Bespoke counts deduplicate route
reuse and exclude the ordinary playable banks. S2's 18 includes four 96×72 scene
panels, eight normal ending selectors and six Super flight selectors. Plane-only
selectors are excluded. S3K's 11 includes two normal pose slots, three Super pose
slots, two tiny passengers and four trio-eyecatch poses. Super pose slots 0/1 are
conservative source coverage; the current powered reunion displays the playable
transformation before the final pose 2. Counts include mapping slots that could be
assembled from mirrored/shared parts; they are drawing budgets, not minimum unique
pixel blobs. S2 adds five missing Float2 poses; S3K adds eight Float2, one plane ride
and five unique transform poses (repeated animation entries do not multiply drawings).
No cross-game deduplication is assumed.

The checked-in `src/main/mod/mica-sheet.yaml` has 96 frames and no Float2, native
plane-ride or transformation animation. WALK/RUN/ROLL/WAIT and other gameplay poses
can serve the ordinary gameplay and demo sections. LOOK_UP/HANG/SPRING are possible
approximations, not exact substitutes for the dedicated native silhouettes. Bounds
in the inventory are sprite-piece unions, including transparent tile margins and
vehicle portions of composites; they are not tight visible-pixel boxes.

Custom art needs engine changes in S1's fixed `END_SONIC` renderer; S2's photo loader
and mod presentation wiring (including preserving the Super branch); S3K's rescue,
reunion and tiny-passenger renderers and native controller type gates; and S3K's
post-credits Sonic child. S2's internal `Presentation` interface supplies player and
composite frames but does not replace the photo panels, and currently substitutes
normal rotation/appearance behavior in parts of the Super path. S1 demos already
use the current main-player roster. S3K's profile controller bypasses native binding,
while scene directors still test the native controller type: custom sheet availability
alone does not establish Super/Hyper ending support.

Doomsday is gameplay leading into the rescue entry, not an extra dedicated ending
sheet. Its powered art belongs to the W6 playable profile. No Sonic drawing is
needed for S1 TRY AGAIN/END, S2 text credits/wordmark, or S3K Eggman/Egg Robo terminal
scenes. The Hyper-progress trio eyecatch does show Sonic and is included.

Native citations below mirror the YAML because the repository citation checker scans
Markdown but not YAML. Per-frame bounds were decoded from pinned mapping macros
(S1/S2) and six-byte sprite pieces (S3K), with no ROM extraction or drawing.

- Nem_EndSonic (docs/s1disasm/sonic.asm:4809)
- Map_ESon (docs/s1disasm/_incObj/87, 88, 89 Ending Sequence Sonic, Emeralds, Logo.asm:299)
- Ani_ESon (docs/s1disasm/_anim/Ending Sequence Sonic.asm:5-23)
- Map_Sonic / SonicDynPLC / Art_Sonic (docs/s1disasm/sonic.asm:4408-4412)
- End_LoadSonic (docs/s1disasm/sonic.asm:3624-3633)
- EndDemo_Levels (docs/s1disasm/sonic.asm:3941-3950)
- EndingSequence_LoadCharacterArt (docs/s2disasm/s2.asm:14269-14300)
- ArtNem_EndingPics / ArtNem_EndingSonic / ArtNem_EndingSuperSonic (docs/s2disasm/s2.asm:90923-90932)
- ObjCF_MapUnc_ADA2 (docs/s2disasm/s2.asm:14374)
- ArtUnc_Sonic / MapUnc_Sonic / MapRUnc_Sonic (docs/s2disasm/s2.asm:90473-90480)
- off_A29C (docs/s2disasm/s2.asm:13414-13418)
- Pal_AC7E / Pal_AD1E / Pal_AD3E (docs/s2disasm/s2.asm:14332-14336)
- byte_A602 (docs/s2disasm/s2.asm:13739-13744)
- byte_A748 (docs/s2disasm/s2.asm:13803-13806)
- loc_A83E (docs/s2disasm/s2.asm:13883-13910)
- SonAni_Float2 (docs/s2disasm/s2.asm:38734)
- ObjCE_Init (docs/s2disasm/s2.asm:13928-14004)
- ArtKosM_SonicEndPose / ArtKosM_SuperSonicEndPose / ArtKosM_SonicPlaneEnding (docs/skdisasm/sonic3k.asm:201742-201749)
- Map_SonicTailsEndPoses (docs/skdisasm/sonic3k.asm:127399-127400)
- Map_SonicPlaneEnding (docs/skdisasm/sonic3k.asm:127389-127390)
- ArtUnc_Sonic / Map_Sonic / PLC_Sonic (docs/skdisasm/sonic3k.asm:201532-201548)
- off_5E328 (docs/skdisasm/sonic3k.asm:124513-124516)
- sub_5FD88 (docs/skdisasm/sonic3k.asm:126788-126802)
- loc_5D8BC (docs/skdisasm/sonic3k.asm:123676-123728)
- off_5EFB6 (docs/skdisasm/sonic3k.asm:125557-125576)
- loc_5F31C (docs/skdisasm/sonic3k.asm:125868-125886)
- AniRaw_6025C / AniRaw_60260 (docs/skdisasm/sonic3k.asm:127345-127353)
- AniSonic0F (docs/skdisasm/General/Sprites/Sonic/Anim - Sonic.asm:60)
- AniSonic1F (docs/skdisasm/General/Sprites/Sonic/Anim - Sonic.asm:119)
- Map_S3EndingGraphics / ArtNem_S3EndingGraphics (docs/skdisasm/Lockon S3/LockOn Data.asm:246-249)

Frame bounds and mapping selectors:

- Map_objCF_0034 (docs/s2disasm/mappings/sprite/objCF.asm:1-241) — 26 mapping slots (including excluded companions/vehicles).
- word_607F0 (docs/skdisasm/General/Ending/Map - Sonic Tails Ending Poses.asm:1-64) — 5 mapping slots (including excluded companions/vehicles).
- word_604BC (docs/skdisasm/General/Ending/Map - Sonic Plane Ending.asm:1-110) — 27 mapping slots (including excluded companions/vehicles).
- Frame_2417AC (docs/skdisasm/General/Ending/Map - S3 Ending Graphics.asm:1-93) — 17 mapping slots (including excluded companions/vehicles).
