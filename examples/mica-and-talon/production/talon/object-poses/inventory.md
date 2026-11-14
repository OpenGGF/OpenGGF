# TALON native mapping inventory

Native player mapping bytes are distinct from object-local and presentation banks. The complete per-ID vocabulary and supplemental canonical/appendage exclusions are in native-inventory.csv. Shared object writes retain the shipped byte even when its Tails drawing differs from Sonic. Existing canonical families serve matching poses; new object drawings serve the remaining views. No ROM art is packaged.

| Host | Native byte(s) | Consumer | Owning reference |
| --- | --- | --- | --- |
| S2 | 01 | Tornado ride capture | loc_3AB18 (docs/s2disasm/s2.asm:79205) |
| S2 | 75–80 | EHZ spiral; S2 object flip_angle selectors (including MTZ/CPZ/WFZ flippers and launchers), resolved by TAnim_Tumble | TAnim_Tumble (docs/s2disasm/s2.asm:41405) |
| S2 | 46–48 | MTZ spin tube: native Roll animation, NOT a raw mapping write | loc_271D0 (docs/s2disasm/s2.asm:53475) |
| S2 | 75–80 | EHZ spiral flip-angle table selects native tumble bank | Obj06_Spiral_MoveCharacter (docs/s2disasm/s2.asm:47228) |
| S3K | 78–7F,90–92 | AIZ/MHZ vines; LBZ grapple; MHZ pulley | AIZRideVineHandle_HangingFrames (docs/skdisasm/sonic3k.asm:46678) |
| S3K | 69–77 | AIZ tree; CNZ cage; MGZ loop; FBZ cages; DEZ gravity tube | AIZTree_PlayerFrames (docs/skdisasm/sonic3k.asm:43867) |
| S3K | 55–5B | HCZ column/CNZ cylinder/LBZ cup/tube elevator/DEZ teleporter | LBZTubeElevator_PlayerFrame (docs/skdisasm/sonic3k.asm:58334) |
| S3K | EF–FA | LRZ corkscrew | RawAni_4247E (docs/skdisasm/sonic3k.asm:87721) |
| S3K | EF–FA | SSZ corkscrew | byte_587A8 (docs/skdisasm/sonic3k.asm:117714) |
| S3K | 55–5B | DEZ miniboss transport | byte_7EF2E (docs/skdisasm/sonic3k.asm:169535) |
| S3K | 55–5B | SSZ carried-player | byte_468C4 (docs/skdisasm/sonic3k.asm:92708) |

S2 CPZ transport tubes and MTZ spin tubes request Roll; moving vines request Hang; WFZ/CPZ/MTZ launchers that write flip_angle select $75–$80 through TAnim_Tumble. They do not directly set an independent drawing ID. Tornado cockpit, ending ObjCF, special-stage actors and Competition miniatures have independent mapping banks and remain presentation-owned.

The supplemental S3K closure follows the full existing object-owned mapping inventory in the design document: $31–$3C gravity loop; $49–$54 cage entry; $55–$5B twist; $5C–$62 pole and $24 release; $63–$68/$94/$95 bars; $69–$77 tumble; $78–$7F swing; $80–$85 chain; $8A–$8D carry; $90–$93 hang; $B4/$B5 cutscene; $BA ride; $C0/$CA/$CB crane; $C4 emerald; $DF–$E3 wall; $E4–$EA parachute; $EF–$FA corkscrew. Character-conditional scripts remain conditional; vocabulary coverage does not claim every branch is reachable by Tails. Zero is always invisible.

## S2 object flip-angle producers

- loc_189FE (docs/s2disasm/s2.asm:33941): `move.b	#1,flip_angle(a1)`
- loc_18A2E (docs/s2disasm/s2.asm:33952): `neg.b	flip_angle(a1)`
- loc_18B42 (docs/s2disasm/s2.asm:34046): `move.b	#1,flip_angle(a1)`
- loc_18B72 (docs/s2disasm/s2.asm:34057): `neg.b	flip_angle(a1)`
- loc_18CE6 (docs/s2disasm/s2.asm:34190): `move.b	#1,flip_angle(a1)`
- loc_18D16 (docs/s2disasm/s2.asm:34201): `neg.b	flip_angle(a1)`
- loc_18E10 (docs/s2disasm/s2.asm:34292): `move.b	#1,flip_angle(a1)`
- loc_18E5C (docs/s2disasm/s2.asm:34303): `neg.b	flip_angle(a1)`
- loc_18F22 (docs/s2disasm/s2.asm:34375): `move.b	#1,flip_angle(a1)`
- loc_18F68 (docs/s2disasm/s2.asm:34386): `neg.b	flip_angle(a1)`
- Obj06_Spiral_MoveCharacter (docs/s2disasm/s2.asm:47242): `move.b	Obj06_FlipAngleTable(pc,d0.w),flip_angle(a1)`
- loc_218E0 (docs/s2disasm/s2.asm:47423): `move.b	d0,flip_angle(a1)`
- loc_24206 (docs/s2disasm/s2.asm:50360): `move.b	#1,flip_angle(a1)`
- loc_24236 (docs/s2disasm/s2.asm:50373): `neg.b	flip_angle(a1)`
- loc_24452 (docs/s2disasm/s2.asm:50583): `move.b	#1,flip_angle(a1)`
- loc_24482 (docs/s2disasm/s2.asm:50596): `neg.b	flip_angle(a1)`
- loc_264BC (docs/s2disasm/s2.asm:52408): `move.b	#1,flip_angle(a1)`
- loc_2650E (docs/s2disasm/s2.asm:52419): `neg.b	flip_angle(a1)`
- loc_26798 (docs/s2disasm/s2.asm:52619): `move.b	#1,flip_angle(a1)`
- loc_26798 (docs/s2disasm/s2.asm:52629): `neg.b	flip_angle(a1)`
- loc_2704C (docs/s2disasm/s2.asm:53369): `move.b	#1,flip_angle(a1)`
- loc_2704C (docs/s2disasm/s2.asm:53379): `neg.b	flip_angle(a1)`
- loc_296C2 (docs/s2disasm/s2.asm:56587): `move.b	#1,flip_angle(a1)`
- loc_296C2 (docs/s2disasm/s2.asm:56597): `neg.b	flip_angle(a1)`
- loc_2A990 (docs/s2disasm/s2.asm:57964): `move.b	#1,flip_angle(a1)`
- ObjB5_CheckPlayer (docs/s2disasm/s2.asm:79937): `move.b	#1,flip_angle(a1)`
