# Trace coverage and duplicate-fixture audit — 2026-11-06

Base: `0d8ffdcd6` (develop). The audit examines all 438 committed metadata directories, grouping first by game, source BK2, movie offset and frame count, then by SHA-256 of the committed physics and aux payloads. No fixture is identified by a heuristic test-name match.

## Removed copies

All 25 segments of `s3k/runs/s3k-multibonus` match the canonical `s3k/runs/s3-knux-multibonus-ss` in source movie, offsets, lengths, and the exact compressed physics, compressed aux and timing file bytes. Its manifest has 22 transitions without gap census and no expected movie end mode. The canonical manifest has gap census and expected end mode. Canonical metadata additionally records entry observations; no runtime payload or recorder input is changed.

Five more standalone directories have the same source/offset/length and byte-identical physics, aux and timing as the retained run segments below. S1 metadata is also byte-identical. S3K special-stage metadata differs only in run ID/date; bonus metadata additionally lacks the canonical entry observations. Existing replay identities and assertions are retained while their fixture paths use the canonical data. The two initial-setup parameter identities remain `bonus_gumball` and `bonus_pachinko`. Four obsolete standalone S3K publication rows/pins are removed; canonical rows/hashes are unchanged. No publication expectation is regenerated from current files.

| Removed directory (under traces/) | Retained directory | BK2 offset | Rows | Removed bytes | Physics SHA-256 | Aux SHA-256 | Timing SHA-256 |
| --- | --- | ---: | ---: | ---: | --- | --- | --- |
| `s3k/runs/s3k-multibonus/aiz` | `s3k/runs/s3-knux-multibonus-ss/aiz` | 915 | 4654 | 1119945 | `39c9689eab06ca543c1953ca78c23d65c98107f6fa39d0c990938308876494f7` | `e202bdc57dff86bc75fab58d0d87bfe879b48ee72a0c34affcb7c09a6a6f395b` | `471d13719b3d328997e203c5d72fcbc9d3fe9646af729d2012885b45f2c6ffed` |
| `s3k/runs/s3k-multibonus/gumball` | `s3k/runs/s3-knux-multibonus-ss/gumball` | 5570 | 1430 | 403350 | `81b12446b031e8731295e521e1e51afd5b5c916a623affb8575713e5bd308d42` | `fa1fef59dd4b81336aa277b369ce4d47b54027db5c963de3625dbc88b38a5b6f` | `94ddd1aa68402b266cc60595c4447f4f47b6b82bcc294a3e0e1722f2906f6d9d` |
| `s3k/runs/s3k-multibonus/aiz_2` | `s3k/runs/s3-knux-multibonus-ss/aiz_2` | 7001 | 2140 | 529798 | `2254194f983d6e7eb06349e14c548f501313843c830e96afb6016cefb94deb62` | `dcdaf1ed43400e73b85b01574a7497357ca5f380bcb83f9fa732546b54df800d` | `a63cd3c2f8b8b244effea5f479d0acf1710525b5fc806b9018bd0ec5b4b5b9c9` |
| `s3k/runs/s3k-multibonus/slots` | `s3k/runs/s3-knux-multibonus-ss/slots` | 9142 | 1200 | 175172 | `e9952835e9584452b0957a9b7662dbe221483dda6ef49ef2bb1bb08dbfbc23d0` | `9d4da171d225b066f53eca889e82a424bb8508db0731475f733f4696403ac6a4` | `b653f69966354e4337d27872884ea874e5bd925a5878d08226995b30436a43cd` |
| `s3k/runs/s3k-multibonus/aiz_3` | `s3k/runs/s3-knux-multibonus-ss/aiz_3` | 10343 | 7568 | 2091873 | `cf16d5a4fc1878496ac1e18d62756cda9d6d5aeea6ee8eb9020736e175be1f81` | `5f993b6074cf2c0b4510dabbaa0483fe12f7351f7afa13586f698c68cddf9748` | `8a35cf2bfed3e77120d25a3195afacb3b9e39a6f42c7deb36363cf352a78515a` |
| `s3k/runs/s3k-multibonus/slots_2` | `s3k/runs/s3-knux-multibonus-ss/slots_2` | 17912 | 1278 | 178407 | `589177a6b8b9af2be6da4828ce70d2015aacf3e29a6cc2f49fc96a675cd4af73` | `8902c25bc49975e9f1522c69308cc1bacb7c9769cb0f4599553927e10c3ec530` | `dad635bff651dfddc8346eccb8967e03d5fcd69e6bb8e0b29024a52922e03a41` |
| `s3k/runs/s3k-multibonus/aiz_4` | `s3k/runs/s3-knux-multibonus-ss/aiz_4` | 19191 | 3210 | 822732 | `38a1b185fec2353369ead945818f777402916701951fc276294a9f41158d25e3` | `293adb2bee6f225c76f7c1943df64b3240010198bdc096ed8cee1ac89ed67ef6` | `4e9b76eb928b7639e4b36d2002b7163dab2663f5232aec99088d474b83af1903` |
| `s3k/runs/s3k-multibonus/gumball_2` | `s3k/runs/s3-knux-multibonus-ss/gumball_2` | 22402 | 1648 | 435700 | `aee365f7c4fa79c94a21fda94eb91a641c30851e2d73d3920710a4d09136e2de` | `60f301b460ade92c946d270e8997b72fe768affdd39cdaec497dd36f2ef59441` | `cf97fcd28611af033c9c7160b32169294366bb2826b0253193539668bcfe159b` |
| `s3k/runs/s3k-multibonus/aiz_5` | `s3k/runs/s3-knux-multibonus-ss/aiz_5` | 24051 | 3631 | 1105858 | `9f347934425e61e3c3c17f91c33434b8d38054f9fce74b65b4e9f3872deaa18c` | `3587ef94d6e70d29f888a799900c654d1c304e9cea5d8b5998c6f0f4781ea373` | `b47bf409582bc6143789677b94af503e082c10976f3bb62915f76f1ba0fa9889` |
| `s3k/runs/s3k-multibonus/hcz` | `s3k/runs/s3-knux-multibonus-ss/hcz` | 27683 | 3176 | 578785 | `452d943931c6fd3e3a914195ae1eb59cce938791a70a42c207b8aeedf77bb573` | `4234ac42397f2d36b5d5c7dff0352bed5baee42405e0376f8212425abe5cb7a8` | `026a2bdae0e56f29735960b15d80e730524271795bba9e60f580d1e194edbd38` |
| `s3k/runs/s3k-multibonus/slots_3` | `s3k/runs/s3-knux-multibonus-ss/slots_3` | 30860 | 5379 | 908847 | `cbe1d24936c62d46baa9277bf86e7d14a3daed6692931510423484481db6c6be` | `86272e8cee122d50a944f2dc854be2a9c14b9487e15efc02744c718677240873` | `4243726b7a15235694568f60b9da9139c7cea4f9f3bce05c844194961728e30c` |
| `s3k/runs/s3k-multibonus/hcz_2` | `s3k/runs/s3-knux-multibonus-ss/hcz_2` | 36240 | 11933 | 3476216 | `5d3c89988e6af69cc7820cfd27377409b825dffdc1479d52c85c2aa4f4c04b72` | `4528b6c9312ad37dd89c8a045d1b09987906e6fe22eb87b6d22db5ef55bbe363` | `27f5cef9511539cb3b52aef7393b990b972bd191b2fba51fac2201275de3952b` |
| `s3k/runs/s3k-multibonus/ss` | `s3k/runs/s3-knux-multibonus-ss/ss` | 48174 | 4630 | 69298 | `b9b9d76efa61cf9980c3bdb71225eeabf09ea68a02a33e56429708c3664698e0` | `b0dfa2590c62caadd487e34bcb7d79edaf7474d855bbb94ccc2572c3aee00cbd` | `8f16906bb964c474e326778c475c4c9b7fc7cc312bd4a6283f505185dd6971d7` |
| `s3k/runs/s3k-multibonus/hcz_3` | `s3k/runs/s3-knux-multibonus-ss/hcz_3` | 54274 | 3949 | 955211 | `b3ffb8243d2728acb3389155a105182a1428154da64fba311bb7227c713001c2` | `d1482557827806e70991c5c22c0c1ea64574968d1bdb56eb07d1c73a1ba705d9` | `e4ae3d8d4f96540b525afbfcc2b5b40e5d038982da04553f8d138d642f3f3e05` |
| `s3k/runs/s3k-multibonus/slots_4` | `s3k/runs/s3-knux-multibonus-ss/slots_4` | 58224 | 1603 | 241609 | `c0707c5c2baa7ebb64fb1e491a59e81187da906a89d7591644b4cb130c9eaba3` | `0ee081ff5ffdcb2f2678868ae5616383d9f905261c37a3a426ebe983ecf2bd63` | `fc962f97b38946f549580a74bac2e97c68afbfe5d96ab66c14a423c7bd4c179a` |
| `s3k/runs/s3k-multibonus/hcz_4` | `s3k/runs/s3-knux-multibonus-ss/hcz_4` | 59828 | 2097 | 458254 | `40488d97360900472714fad981e90a7ad7ee7cae1902556df8adf309314866c7` | `bb00da749169d4a2b375413d6ac49c362dcc8616d45c53983bc8c419c4d77a20` | `d3d0f2e37035fff1af3d54cb9019304da8721bf00a06a9e4b8be9ef584fc0fb4` |
| `s3k/runs/s3k-multibonus/ss_2` | `s3k/runs/s3-knux-multibonus-ss/ss_2` | 61926 | 7194 | 108434 | `2d42582198a65c73bc5ff2757bd638ae3868438bbf88272354280dab93a976c8` | `15021d4f6d3fe594379106e2aa25067a9ecb41b88fa6e7710f2c5f8d84a1f814` | `f5f8adc9c8f31553c679bc27356ebff8a3dce588af52f27f4438dfb0c1d09f35` |
| `s3k/runs/s3k-multibonus/hcz_5` | `s3k/runs/s3-knux-multibonus-ss/hcz_5` | 70590 | 3435 | 674066 | `d36a9c5af7db572c4835749a88ff2c0bc899558c26028e304cc846c4d2bb8b8c` | `3d1af2209cb6eb1e398c7e41369ec890b781ae779634e58375818e334a04e576` | `9fe4ab2c11a11c82617ae51b39cc5f7872ee667256d68a2511baa4f5dd330a2a` |
| `s3k/runs/s3k-multibonus/slots_5` | `s3k/runs/s3-knux-multibonus-ss/slots_5` | 74026 | 1791 | 312634 | `0155c1889bf309595015599c2b4fac2e965770190fb60fe54f3e0e43af0b6ade` | `5e97d7945123d363996de6cdab9aa5e6941b39805b623b0f6c060f919c1a2d48` | `2da99db2e1c55f5803dd9e6686faeab9e932bff366ebc256cdabb0a4e03f0021` |
| `s3k/runs/s3k-multibonus/hcz_6` | `s3k/runs/s3-knux-multibonus-ss/hcz_6` | 75818 | 8422 | 2872333 | `6fb237125416e25e1b982f0e3612ad04752594b2a755c0a0272b4f94d149f8ee` | `2b4b2433922934146b7e01b26ce76908d865e782bdfbc8ef463644ead288d81d` | `0301b48e8c343ba3c08d61d7ad429ee608a9211c5a791922ec07c47b0f30a6f3` |
| `s3k/runs/s3k-multibonus/mgz` | `s3k/runs/s3-knux-multibonus-ss/mgz` | 84241 | 8721 | 1925334 | `436c7c5bc066a3fd21fd2c2ba01532af47b2f2ced1dbe8a544a053012dc649f9` | `29dde0b925ac7d2c4767d57d32f57e6cd2cf1f4e580b89c89e50178e201a4a91` | `6fe169200a6569f666a93366a0ff85f31f091034911b8737b72860a7cc41ae4b` |
| `s3k/runs/s3k-multibonus/pachinko` | `s3k/runs/s3-knux-multibonus-ss/pachinko` | 92963 | 3051 | 677161 | `b1d6a45be5f9c971a406988992f582ac2489abc3b386102994e1a77e6e6e4200` | `b5646294a26db199938a65613751ec834b093b1b9fb338c3cf689efad51c66d4` | `e7135fd9a46d572876dd547676477ae505a1b6daef3af070222c48b406444d52` |
| `s3k/runs/s3k-multibonus/mgz_2` | `s3k/runs/s3-knux-multibonus-ss/mgz_2` | 96015 | 2076 | 469978 | `564ba119565480614786805d6fc51ab8b363ad22abd365558b31e77f0e241e61` | `b648210c95c8c00d45272baf703bb6b1fab537af27b3e1346e9555d532e5d88b` | `d894c165968523b796f370f4af24173252af99ea5d57f9dab21b1cb4b1336691` |
| `s3k/runs/s3k-multibonus/ss_3` | `s3k/runs/s3-knux-multibonus-ss/ss_3` | 98092 | 6537 | 97499 | `d726be5249528022e39cdb65d1b08b4f80bf26793546483f3d1a81153692eaca` | `353e70c4fc05fe500aa5cdd738c32ef4003a2355d23d10fe617f9d90a7c49d32` | `5d2bbe548ed915800d14d935dc20a10f55d0f3c1ba67a7181951fab058678ae1` |
| `s3k/runs/s3k-multibonus/mgz_3` | `s3k/runs/s3-knux-multibonus-ss/mgz_3` | 106104 | 8517 | 2069019 | `2a2c7071e1ee334186de44a06f7b31fc3837ce92b2d04e314b7fc3357a01e625` | `2ab0d9080843026a968bae24238c1a7b0c2f6781a9abfa6c942cada80a59ff1f` | `b0cc6dd6cb48d31fd951cc8ca6a8da0f288dbe16e193082765b9d15adf9decdf` |
| `s1/special_stage` | `s1/runs/s1-ghz-maze-roundtrip/ss` | 4957 | 3091 | 87123 | `9b1aa77a44bf15725d4c4e2b94ad8b0d6bacea27e6932b4a0f0688fcf132793b` | `3f24f6397073dc924639abe87f33e141f62f02054f99182fff7db219dd8fb29b` | absent in both |
| `s3k/special_stage` | `s3k/runs/s3-knux-multibonus-ss/ss` | 48174 | 4630 | 115514 | `b9b9d76efa61cf9980c3bdb71225eeabf09ea68a02a33e56429708c3664698e0` | `b0dfa2590c62caadd487e34bcb7d79edaf7474d855bbb94ccc2572c3aee00cbd` | `8f16906bb964c474e326778c475c4c9b7fc7cc312bd4a6283f505185dd6971d7` |
| `s3k/bonus_gumball` | `s3k/runs/s3-knux-multibonus-ss/gumball` | 5570 | 1430 | 403350 | `81b12446b031e8731295e521e1e51afd5b5c916a623affb8575713e5bd308d42` | `fa1fef59dd4b81336aa277b369ce4d47b54027db5c963de3625dbc88b38a5b6f` | `94ddd1aa68402b266cc60595c4447f4f47b6b82bcc294a3e0e1722f2906f6d9d` |
| `s3k/bonus_pachinko` | `s3k/runs/s3-knux-multibonus-ss/pachinko` | 92963 | 3051 | 677161 | `b1d6a45be5f9c971a406988992f582ac2489abc3b386102994e1a77e6e6e4200` | `b5646294a26db199938a65613751ec834b093b1b9fb338c3cf689efad51c66d4` | `e7135fd9a46d572876dd547676477ae505a1b6daef3af070222c48b406444d52` |
| `s3k/bonus_slots` | `s3k/runs/s3-knux-multibonus-ss/slots` | 9142 | 1200 | 175172 | `e9952835e9584452b0957a9b7662dbe221483dda6ef49ef2bb1bb08dbfbc23d0` | `9d4da171d225b066f53eca889e82a424bb8508db0731475f733f4696403ac6a4` | `b653f69966354e4337d27872884ea874e5bd925a5878d08226995b30436a43cd` |

The removed tree contains 30 redundant fixture directories plus one obsolete run manifest. Other same-provenance groups are all contained in these removals. There are no other pairs with identical physics-plus-aux hashes. No BK2 is renamed or rerecorded.

## Coverage and naming limits

New segment tests compare their own observed entry state under the existing carried-state import (ruling A); they do not certify continuous campaign completion. Their new identities and first frontiers must be listed separately from pre-existing tests. Gumball and Pachinko round-trip fixtures are still absent; the existing skips remain, and this batch records nothing.

The S3K labels require a separate follow-up: `hpz*` represents SSZ (`$0A`), `ssz*` DEZ (`$0B`), `zone0c` DDZ, `ddz` ending (`$0D`) and `dez23*` shrine (`$17`). This batch performs no zone/fixture rename.

## Validation

Focused queued Java25/EGL checks pass: ordinary fixture/publication/entry/source/producer guards; r6 start-position policy and S1 special-stage replay; r7 Gumball, Pachinko, Slots and special-stage replays (all five r7 cases pass). Commands and full reports are archived under `/tmp/trace-coverage-20261106/lead/focus-*`. The frozen three-profile comparison remains lead-owned; final results are recorded in the trace frontier log. Existing negative identities use complete assertions and reports, separately from newly added coverage.

## Cheap-win investigations and rejected shortcuts

S1 complete emerald chain retains five axes on base `0d8ffdcd6`. Four queue axes are 3/6/3/12 errors in `mz2_3`, `mz3_2`, `lz3`, `lz4`, at first rows 101/102/10185/11. The test-only observer sees the correct queued head, no decoder and untimed held-tail preparation on every one of the eight error rows; the next row prepares that same descriptor without consuming patterns. All 29 reports and the complete 3,821,477-byte assertion reproduce baseline. `RunPLC (sonic.asm:1379-1415)` writes its pattern count before code-table construction in shipped FixBugs=0; the fixed branch writes it afterward. `Level_MainLoop (sonic.asm:3000-3036)` owns the loop tail. A held gameplay counter cannot identify the interruption point. Matching committed S1 timing streams are absent. The owner is `Sonic1PlcArmTiming` and the dedicated readiness publication contract. Blanket early arming or changing shared service budgets is rejected: neither follows from the actual missing preparation boundary.

The fifth S1 axis is SBZ3 terminal art: 4,704 native versus 4,784 engine edges, 10,334 ERROR and 4,704 WARNING observations. Engine ending admission by movie 214181 and movement by 214185 reproduce the retained native audit's early engine witness; that native audit has no initialized actor through 214283. `GM_Ending (sonic.asm:3560-3632)` owns synchronous setup with interrupts disabled, followed by enabling interrupts and player initialization. A correct extension needs a real resumable production load through `NativeLevelLoadController` and the admitted initialization timing contract. No fitted ending delay or new native observation is claimed. Test-only commit `205a6b50b` adds three passing service/coordinator readiness controls; all 341 existing focused identities/outcomes and terminal tuples are unchanged. Its final ordinary selection passes 343 cases. An earlier mixed trace/guard attempt admitted an unwanted exhaustive tooling case and was cancelled; the final separated selections are the evidence, not that incomplete run.

KiS2's unchanged five-axis chain stops at segment 14/movie cursor 65856. Two read-only observer runs reproduce the full assertion and all 16 reports. The two observed streams match 25,162 actual rows exactly; terminal-finalizer and bootstrap fields outside the callback remain separate limits. Monitor contents consumes an extra pending-init skip in its actual first execution: engine child slot 16 is below parent 20, whereas the native child is 26. That explains the one-row award mismatch at 826 but does not by itself identify the upstream allocator cause. Removing the delay solely to advance the reward is rejected pending allocation/phase evidence; the shared monitor/object-dispatch owner is responsible, with `Obj26_SpawnIcon (s2.asm:25690-25712)` and `Obj2E_Raise (s2.asm:25785-25818)` as references.

KiS2 segment 13's first error is camera X at 2275, before its queue frontier at 2578. Actual `EggPrisonObjectInstance.triggerEndOfAct` submits results PLC 38 five rows late at 2583; all 576 queue field values over 64 rows agree with ROM when diagnostically aligned to that submission. This is not evidence of a Nemesis service-rate defect. The unresolved owner is shared Egg Prison/end-checker/animal retirement, with the earlier EHZ camera frontier retained. `loc_3F406 (s2.asm:85128-85145)` and `Load_EndOfAct (s2.asm:34848-34884)` own the handoff; the upstream cause of late checker admission remains unproven.

KiS2 CPZ row 3490's actual wrong writer is `CPZSpinTubeObjectInstance.calculateVelocity`, with ability flag zero: player `$2540/$0570`, target `$254A/$056F`, duration `$0140`, engine Y speed `-$CC` versus native `-$E3`. All 12 entry/exit tables match pinned disassembly and physical KiS2 ROM. `loc_2271A (s2.asm:48636-48660)` and `loc_22902/loc_22952 (s2.asm:48820-48878)` own the path/calculation. Native call inputs and table-pointer/overlapping-object execution remain unobserved; fitting a quotient or target is rejected. Shared Obj1E arithmetic/dispatch owns this blocker, not a demonstrated glide failure. Segment 0's 38 bootstrap history errors remain in its complete report despite not being in the chain's asserted segment list; this batch does not suppress or hydrate them. The other fleet retains KiS2 glide/history ownership.

A lead comparison-only allocator probe reproduces all 16 corresponding KiS2 chain reports and its complete assertion after literal worktree-prefix normalization. The two extra files from the lane's full-class selection were not emitted by the lead's chain-method-only selection and are not called compared. At the affected monitor's touch callback the first free slot is 16; at its first broken-shell update (native V-int 10032), it is 26. This proves the allocator changes across those phases. It does not establish a counterfactual allocation sequence with the two already-spawned children removed. `Touch_Monitor.breakMonitor (s2.asm:85463-85473)` selects the later monitor routine; `Obj26_SpawnIcon (s2.asm:25690-25712)` owns allocation. Correcting that native producer boundary and reconciling allocation/rewind requires the shared monitor/object-dispatch owner; neither a live-cursor shortcut nor a fitted skip is delivered. Probe source is restored byte-for-byte and its sole instrumented class removed before composed validation. The results and CPZ owners above remain open; no KiS2-local runtime fix or claimed frontier advance.

## Completed coverage and entry binding

All 163 segments in the four requested manifests now have direct strict identities, plus all six standalone slices. Nineteen existing identities are reused; 150 strict replay classes are new. Four new Mega classes were removed because the existing Gumball/Pachinko/Slots/special-stage identities now consume those canonical segments. Every fixture-to-source link is verified literally, joining compile-time string fragments where needed; none depends on a test-name heuristic.

The seven previously blocked KiS2 special stages now use recorded solo Knuckles configuration and the existing production module resolver. Two real entry controls prove stock Sonic+Tails stays stock and solo Knuckles activates the built-in lock-on module. No runtime special-stage physics or art is changed. All seven strict replays produce reports: first frontier 108, dynamic-art owner `ss-knuckles` versus `ss-sonic`, retained as new coverage. The existing eight S2 special-stage replay cases stay green. The root entry focus completes 61 cases: 30 pass, 31 new KiS2 coverage failures, zero errors/skips.

The existing single entry-state import now reads S2-family stage-exit destination `rings_after`: observed zero for stock S2, retained counts for lock-on. This is manifest state at entry under ruling A, not later row hydration. S1 result-screen counts remain excluded because they do not describe the next level's entry. A resolver control covers both stock and lock-on records; it passes with the two team controls. The protected title-card count and solo recorder-object loop are unchanged.

`TestTraceBaselineRegression` was inspected. Its opt-in legacy seven-class manifest does not require these new identities, and it is not rewritten to absorb new reds. Its inherited default path points into the old `docs/superpowers/research` location although the frozen file lives in `docs/architecture/research`; that path defect remains a follow-up. The report-backed audit was not enabled or claimed passing. Newly exposed red identities and complete first frontiers are recorded separately after composed validation.

## Upstream reconciliation before candidate freeze

Develop advanced from `0d8ffdcd6` to `df96b343c` during the lanes. Its 0.7 release and 0.8 rollover, editor input fix, route/controller tests, policy/tooling and release metadata are preserved. POM changes merge cleanly. The sole conflict is the rolled-over 0.7 prerelease changelog: the upstream file is retained exactly, and this batch's Crawl/coverage/entry notes move into `CHANGELOG.0.8.prerelease.md`. No released notes or version are overwritten. The original 0d8 baseline remains archived. Because executable source differs, a fresh once-per-profile updated-base measurement is taken rather than relabelling those old archives. One final combined candidate sweep is compared against both epochs; one change-based smoke qualifies the frozen updated candidate.

## Frozen combined gate and maintenance qualification

The measured candidate is `93a8196459c662fc59a054724586f2d859ae4c6b`.
Fresh baseline `0d8ffdcd6` and the actual integration base `df96b343c` each ran
one serial three-profile selection. All 1,089 baseline outcomes, complete
messages/bodies and 428 corresponding JSON reports are identical between those
bases after only literal worktree-prefix normalization. The older a89 archives
are not reused as a baseline. The candidate ran one combined three-profile sweep:

| Profile | Updated-base cases / F / E / skips | Frozen cases / F / E / skips | New cases | Added JUnit seconds |
| --- | --- | --- | ---: | ---: |
| trace-replay | 898 / 9 / 0 / 7 | 957 / 60 / 0 / 7 | 59 | 91.325 |
| trace-replay-r7 | 121 / 66 / 0 / 0 | 214 / 133 / 1 / 0 | 93 | 174.475 |
| trace-segments | 70 / 40 / 0 / 0 | 70 / 40 / 0 / 0 | 0 | 0 |

The new strict identities account for 32 passes, 117 assertion failures and one
FIFO execution error, with zero skips. Their measured 264.388 JUnit seconds plus
1.412 seconds for two new team/bootstrap controls add 265.800 profile seconds.
These are test execution sums, not elapsed sweep or queue time. All six standalone
slices have direct comparisons; 150 new identities and 19 existing identities
cover the 169 requested fixtures without duplicating the four canonical Mega
bonus/special-stage consumers.

No existing identity or report disappears. All existing r7 and trace-segments
outcomes and reports are exact. The two changed r6 chain reports are exactly the
clean D-lane candidate reports. CNZ2 improves from 188,220 raw ERROR observations
at row 7849 to 49,194 at row 10361; chain boundary ownership advances from segment
21/CNZ2 at movie cursor 138828 to segment 23/HTZ2 at 155218. Actual common-row
comparison finds zero added errors on the 7,849 baseline-clean rows. Later
additions follow the existing divergence and newly reached HTZ suffixes remain
unqualified. ARZ2 remains 4,442 errors at row 5512; its actual witness is capsule
Obj3E admission, not the earlier unverified Obj89 hypothesis. Four special-stage
return gaps retain their one-frame disagreement. The owning Crawl routines and
rejected shortcuts are in [the S2 audit](2026-11-06-s2-crawl-enemy-handoff.md).

The frozen sweep catches one new existing maintenance failure: the animation
header inventory still expects 22 root S3K directories after three duplicate
bonus directories were removed. Commit `6de8264fb` pins 19 root directories and
adds the three canonical bonus paths to the same checks, retaining all 62 logical
headers. Its isolated trace-replay test passes. No known-red list or baseline is
rewritten, and no comparison is relaxed.

One change-based smoke ran against `df96b343c`, with workers 2 and a 40-minute
Maven execution cap per invocation (queue excluded). It completed all 4,468
selected classes, 91/91 ordinary batches and the guard batch. All 836 guards
pass; all 49 skips are classified allowed, with no missing ROM property or
unexplained skip. Its raw verdict remains:

```text
VERDICT FAILED: head=93a8196459c6 source=07d27bb7de6a change-based 4468/4468 candidate classes; ordinary 91/91 batches; guards 1/1 batches; tests=37837 failures=29 errors=2 skipped=49 (allowed=49 unexplained=0); missing ROM properties=none; skip inputs audio_reference_files=false,gl=false,s1_bizhawk_reference=false,s2_bk2=false,s3k_observations=false; bounded smoke selection, not a full-suite pass
```

Five disjoint focused selections actually executed on `df96b343c` reproduce all
29 assertion failures across 88 cases (59 pass / 29 fail / no errors or skips).
Twenty-eight complete messages and bodies are literal matches. The remaining
Three Islands message differs only in the Java `Screen@` object identity display;
both full strings are retained, and the examples/mod source is unchanged. The
inherited failures belong to Three Islands/private-lobby, native route/physical
checks and the fixed S3K audio request-manifest guard. No unrelated repair or
blanket flakiness claim is made.

The two new smoke errors are missing deleted standalone paths in the typed
`TestSpecialStageVisualTraceSession` consumers. The same maintenance commit
repoints both to retained canonical fixtures; all four cases then pass with no
skip. It also repoints the upstream hero-reel manifest's Gumball path; all 21
media-tooling unit cases pass. These focused repairs retain the original
assertions. The frozen run is not relabelled as a later-SHA sweep or a broad pass.

The final source boundary is explicit: since the frozen candidate, only those
two maintenance test classes, the hero manifest and qualification prose change.
Production source, fixture resources, POM and testing-runner subtrees are exact
Git objects from the frozen candidate. Replay helpers and strict identities are
also unchanged. The reviewed smoke diagnostics are archived with a 101-file
hash receipt before the original run is acknowledged. Full commands, uncapped
XML/JSON, baseline controls, row gates, source/ROM hashes and focused repair
results remain under `/tmp/trace-coverage-20261106/`. No second broad sweep or
smoke is claimed. Delivery is qualified against inherited failures; after the
focused repairs there is no newly red existing identity, earlier existing
frontier or added error on a baseline-clean row.

Both deleted standalone movie copies are exact Git blobs retained in the
canonical S1 run and S3K movie/run roots. No movie input is lost. The four
obsolete standalone hardware publication rows were removed with their proven
copies; canonical publication pins, recorder gitlink and all six ROM images
remain unchanged.

## Cost and remaining ownership

Before post-integration focus, 136 owned Maven queue requests sum to 30,574.0
seconds of waiting and 9,751.5 seconds of slot hold. Of the latter, 338.4 seconds
belong to the cancelled mixed-selection attempt, rather than completed checks.
These are overlapping per-request sums, not end-to-end elapsed time; slot hold
includes compile, launch, testing and teardown. Smoke preparation adds 96.13
seconds outside those queued holds. The smoke command wall is 8,466.298 seconds.
Post-integration costs are recorded separately in the delivery receipt. User
input wait is zero. Off-job analysis, archival and coordination time is unknown;
it is not manufactured from residuals or called execution time.

C remains blocked at S1 readiness publication/timed preparation and the real
resumable ending-load owner. E remains blocked at shared monitor producer phase
and allocation/rewind, Egg Prison/end-checker retirement, and native Obj1E call
inputs/dispatch. The other fleet retains KiS2 glide/history and the protected
LBZ2/CNZ/FBZ footprint; there is no evidence that CPZ's observed wrong writer has
the glide flag set. The seven newly exposed lock-on special-stage art-owner
frontiers belong to the shared special-stage presentation owner. The Knuckles
LRZ2 FIFO exception retains a partial report (5,956 of 8,945 rows); its 2,989-row
suffix is unknown. No new recorder field, recording, tolerance, fitted delay,
CPU simulation or protected-bootstrap edit is introduced.


## New strict replay identities and frozen first frontiers

Measured on frozen `93a819645`: 150 new strict replays, 32 pass / 117 assertion failures / one FIFO execution error / zero skips. Report error counts are grouped diagnostics, not expanded per-row observations. Each pass is conditional segment replay. Bootstrap/frontier errors are retained, and a partial report does not certify its missing suffix.

| Exact identity | Outcome | First frontier (expected / actual) | Report errors | Compared rows | JUnit seconds |
| --- | --- | --- | ---: | ---: | ---: |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg10Cpz2SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 6410 | 1.601 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg11Cpz2SegmentTraceReplay#replayMatchesTrace` | failure | 245 `y`: 0x059C / 0x059E | 2720 | 5510 | 1.411 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg12Arz1SegmentTraceReplay#replayMatchesTrace` | failure | 2647 `x_speed`: -04F0 / 0x0000 | 2098 | 4591 | 1.330 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg13Arz2SegmentTraceReplay#replayMatchesTrace` | failure | 643 `dynamic_art.outstanding_transfer_ids`: [] / [181] | 754 | 1998 | 0.693 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg14Arz2SegmentTraceReplay#replayMatchesTrace` | failure | 0 `rings`: 7 / 0 | 2118 | 5183 | 1.408 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg15Cnz1SegmentTraceReplay#replayMatchesTrace` | failure | 3750 `y`: 0x03C1 / 0x03CB | 5300 | 8294 | 2.045 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg16Cnz2SegmentTraceReplay#replayMatchesTrace` | failure | 688 `y_speed`: 0x0000 / 0x01B2 | 11675 | 11564 | 2.673 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg17Htz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[26]`: 0x03F3 / 0x03F4 | 4732 | 8911 | 1.617 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg18Htz2SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[26]`: 0x06B3 / 0x06B4 | 6062 | 8373 | 1.721 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg19Mcz1SegmentTraceReplay#replayMatchesTrace` | failure | 4439 `rings`: 56 / 46 | 1851 | 7009 | 1.546 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg1Ehz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[26]`: 0x0293 / 0x0294 | 39 | 3180 | 0.862 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg20Mcz2SegmentTraceReplay#replayMatchesTrace` | failure | 2555 `y`: 0x0263 / 0x0265 | 7662 | 11014 | 2.273 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg21Ooz1SegmentTraceReplay#replayMatchesTrace` | failure | 699 `player_animation_id`: 0x0000 / 0x0020 | 5633 | 12332 | 2.127 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg22Ooz2SegmentTraceReplay#replayMatchesTrace` | failure | 1661 `g_speed`: 0x0464 / 0x01F8 | 1412 | 15141 | 2.858 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg23Mtz1SegmentTraceReplay#replayMatchesTrace` | failure | 2527 `rings`: 1 / 0 | 6672 | 11358 | 2.410 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg24Mtz2SegmentTraceReplay#replayMatchesTrace` | failure | 2766 `x`: 0x0635 / 0x063B | 5446 | 11893 | 2.523 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg25Mtz3SegmentTraceReplay#replayMatchesTrace` | failure | 4486 `dynamic_art.edge[0].mapping_frame`: 86 / 161 | 8665 | 13804 | 3.000 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg26Scz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.x[0]`: 0x0120 / 0x0135 | 1182 | 1644 | 0.588 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg27Scz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.x[0]`: 0x0120 / 0x0135 | 5876 | 7638 | 1.875 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg28Wfz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.x[0]`: 0x0060 / 0x0077 | 8855 | 14347 | 2.914 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg29Dez1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[26]`: 0x0131 / 0x0130 | 2962 | 9331 | 1.871 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg2Ehz1SegmentTraceReplay#replayMatchesTrace` | failure | 826 `rings`: 95 / 85 | 1 | 1316 | 0.483 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg3Ehz1SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 2525 | 0.746 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg4Ehz1SegmentTraceReplay#replayMatchesTrace` | failure | 155 `dynamic_art.frame`: 155 / 154 | 431 | 2392 | 0.639 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg5Ehz2SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[26]`: 0x02B3 / 0x02B4 | 38 | 3561 | 0.955 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg6Ehz2SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 1177 | 0.470 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg7Ehz2SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 2215 | 0.651 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg8Ehz2SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[0]`: 0x03E9 / 0x03F0 | 1568 | 3222 | 0.855 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSeg9Cpz1SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 5574 | 1.253 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSs2SpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 16559 | 5926 | 0.525 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSs3SpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 17861 | 6664 | 1.093 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSs4SpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 20582 | 6663 | 0.431 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSs5SpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 17892 | 6210 | 0.389 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSs6SpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 22147 | 7705 | 0.428 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSs7SpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 29296 | 8104 | 0.445 |
| `com.openggf.tests.trace.kis2.completeemeralds.TestKis2CompleteEmeraldsSsSpecialStageTraceReplay#replayProducesFaithfulReport` | failure | 108 `dynamic_art.edge[0].owner`: ss-knuckles / ss-sonic | 13588 | 5270 | 0.369 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg12Arz1SegmentTraceReplay#replayMatchesTrace` | failure | 0 `tails_y_sub`: 0x3E00 / 0x1E00 | 8528 | 4889 | 1.545 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg13Arz2SegmentTraceReplay#replayMatchesTrace` | failure | 2175 `x_speed`: 0x01DE / 0x018A | 9130 | 6409 | 1.890 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg14Cnz1SegmentTraceReplay#replayMatchesTrace` | failure | 4089 `x_speed`: 0x0235 / 0x01ED | 17841 | 12145 | 3.320 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg15Cnz2SegmentTraceReplay#replayMatchesTrace` | failure | 5719 `x_speed`: 0x01EF / 0x01A7 | 27837 | 13045 | 3.778 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg16Htz1SegmentTraceReplay#replayMatchesTrace` | failure | 605 `y_speed`: 0x00B1 / -004F | 18690 | 7535 | 1.938 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg17Htz2SegmentTraceReplay#replayMatchesTrace` | failure | 4676 `x_speed`: 0x024D / 0x0205 | 18001 | 8460 | 2.147 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg18Mcz1SegmentTraceReplay#replayMatchesTrace` | failure | 2294 `dynamic_art.edge[1].present`: true / false | 15262 | 6213 | 1.641 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg19Mcz2SegmentTraceReplay#replayMatchesTrace` | failure | 416 `dynamic_art.edge[0].mapping_frame`: 1 / 200 | 26199 | 8610 | 2.228 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg20Ooz1SegmentTraceReplay#replayMatchesTrace` | failure | 229 `tails_y`: 0x0746 / 0x0747 | 23935 | 11557 | 2.546 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg21Ooz2SegmentTraceReplay#replayMatchesTrace` | failure | 2882 `x`: 0x1445 / 0x1448 | 9846 | 8591 | 2.012 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg22Mtz1SegmentTraceReplay#replayMatchesTrace` | failure | 2106 `x_speed`: 0x01AF / 0x01F7 | 24019 | 7590 | 2.074 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg23Mtz2SegmentTraceReplay#replayMatchesTrace` | failure | 759 `tails_g_speed`: 0x0000 / 0x000C | 21569 | 6542 | 1.843 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg24Mtz3SegmentTraceReplay#replayMatchesTrace` | failure | 336 `player_animation_id`: 0x0005 / 0x000C | 31987 | 11341 | 3.087 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg25Scz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.x[0]`: 0x0120 / 0x0135 | 5635 | 4707 | 1.252 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg26Scz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.x[0]`: 0x0120 / 0x0135 | 8785 | 7611 | 1.883 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg27Wfz1SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.x[0]`: 0x0060 / 0x0077 | 5154 | 9667 | 2.032 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg28Dez1SegmentTraceReplay#replayMatchesTrace` | failure | 1748 `y_speed`: 0x0588 / -0588 | 2315 | 5578 | 1.204 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg3Ehz1SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 3960 | 1.131 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg4Ehz1SegmentTraceReplay#replayMatchesTrace` | failure | 1 `rings`: 1 / 2 | 1 | 1288 | 0.483 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg5Ehz2SegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 6046 | 1.659 |
| `com.openggf.tests.trace.s2.completeemeralds.TestS2CompleteEmeraldsSeg7Ehz2SegmentTraceReplay#replayMatchesTrace` | failure | entry 0 `player_history.y[0]`: 0x03E9 / 0x03F0 | 364 | 3997 | 1.142 |
| `com.openggf.tests.trace.s3k.TestS3kDdzCompleteRunTraceReplay#replayMatchesTrace` | failure | 0 `g_speed`: 0x0800 / 0x0000 | 33 | 719 | 0.723 |
| `com.openggf.tests.trace.s3k.TestS3kDezCompleteRunTraceReplay#replayMatchesTrace` | failure | 0 `camera_y`: 0x0010 / 0x0020 | 738 | 6040 | 2.667 |
| `com.openggf.tests.trace.s3k.TestS3kHpz22CompleteRunTraceReplay#replayMatchesTrace` | failure | 0 `rings`: 144 / 0 | 3545 | 15960 | 5.559 |
| `com.openggf.tests.trace.s3k.TestS3kHpzCompleteRunTraceReplay#replayMatchesTrace` | failure | 158 `queue.s3k_kos_direct.busy`: true / false | 3342 | 18462 | 6.384 |
| `com.openggf.tests.trace.s3k.TestS3kLrzCompleteRunTraceReplay#replayMatchesTrace` | failure | 34 `queue.s3k_kos_direct.busy`: true / false | 8022 | 38637 | 11.436 |
| `com.openggf.tests.trace.s3k.TestS3kSszCompleteRunTraceReplay#replayMatchesTrace` | failure | 0 `camera_x`: 0x0040 / 0x0000 | 8143 | 44083 | 13.297 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperAiz2SegmentTraceReplay#replayMatchesTrace` | failure | 127 `x`: 0x1CE1 / 0x1CD9 | 649 | 5019 | 1.877 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperAiz3SegmentTraceReplay#replayMatchesTrace` | failure | 39 `x`: 0x0275 / 0x0271 | 1222 | 9137 | 2.986 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperAiz4SegmentTraceReplay#replayMatchesTrace` | failure | 47 `rings`: 108 / 109 | 375 | 6262 | 2.123 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperAiz5SegmentTraceReplay#replayMatchesTrace` | failure | 1349 `queue.s3k_kos_module.queued_fingerprints`:  / 5e515d8e2d56da8c7f1de79839441f3ef10611aa7770adcd8cd66161dc08f5e1 | 235 | 3886 | 1.580 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperAizSegmentTraceReplay#replayMatchesTrace` | pass | none | 0 | 1653 | 0.763 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperCnz2SegmentTraceReplay#replayMatchesTrace` | failure | 1600 `g_speed`: 0x00C0 / -00C0 | 692 | 11125 | 2.845 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperCnzSegmentTraceReplay#replayMatchesTrace` | failure | 0 `x_sub`: 0x0000 / 0x0C00 | 779 | 4362 | 1.365 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez232SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 147 | 0.409 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez233SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 171 | 0.426 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez234SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 194 | 0.448 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez235SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 186 | 0.423 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez236SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 209 | 0.423 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez237SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 418 | 0.480 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperDez23SegmentTraceReplay#replayMatchesTrace` | failure | 0 `queue.s3k_kos_module.queued_fingerprints`: ff6c936ffb5dbccf76f2ed01c6135150ff6fb58d3769410d6061d913e414711b /  | 4 | 988 | 0.657 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperFbz2SegmentTraceReplay#replayMatchesTrace` | failure | 152 `rings`: 189 / 190 | 34 | 2024 | 0.973 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperFbz3SegmentTraceReplay#replayMatchesTrace` | failure | 6593 `y_speed`: -0378 / -0478 | 455 | 13990 | 4.338 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperFbz4SegmentTraceReplay#replayMatchesTrace` | failure | 4348 `player_animation_id`: 0x0000 / 0x001F | 140 | 8188 | 3.058 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperFbzSegmentTraceReplay#replayMatchesTrace` | failure | 1610 `rings`: 31 / 32 | 99 | 11489 | 3.376 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperGumball2BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 613 | 0.599 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperGumball3BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 761 | 0.614 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperGumballBonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 508 | 0.533 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHcz2SegmentTraceReplay#replayMatchesTrace` | failure | 51 `rings`: 215 / 216 | 177 | 5142 | 1.851 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHcz3SegmentTraceReplay#replayMatchesTrace` | failure | 592 `x`: 0x0000 / 0x359E | 35 | 584 | 0.577 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHcz4SegmentTraceReplay#replayMatchesTrace` | failure | 2176 `x_speed`: 0x044C / 0x0000 | 454 | 11055 | 3.288 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHcz5SegmentTraceReplay#replayMatchesTrace` | failure | 789 `player_mapping_frame`: 0x00A1 / 0x009F | 61 | 5476 | 1.998 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHcz6SegmentTraceReplay#replayMatchesTrace` | failure | 1110 `player_animation_id`: 0x001B / 0x0002 | 2 | 1974 | 1.017 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHcz7SegmentTraceReplay#replayMatchesTrace` | failure | 3762 `player_animation_id`: 0x0000 / 0x0008 | 399 | 9263 | 3.042 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHczSegmentTraceReplay#replayMatchesTrace` | failure | 0 `player_mapping_frame`: 0x00CB / 0x00CA | 557 | 4096 | 1.388 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHpz22SegmentTraceReplay#replayMatchesTrace` | failure | 0 `x_sub`: 0x0000 / 0x0C00 | 282 | 875 | 0.591 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperHpzSegmentTraceReplay#replayMatchesTrace` | failure | 575 `status_byte`: 0x0002 / 0x0003 | 371 | 21439 | 5.152 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperIczSegmentTraceReplay#replayMatchesTrace` | failure | 1693 `y_speed`: -003C / 0x0000 | 1267 | 20008 | 5.750 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperLbzSegmentTraceReplay#replayMatchesTrace` | failure | 0 `y`: 0x0650 / 0x064C | 3035 | 24832 | 5.545 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperLrz2SegmentTraceReplay#replayMatchesTrace` | error | 34 `queue.s3k_kos_direct.busy`: true / false; execution abort: KosM FIFO full; suffix unobserved | 803 | 5956 | 2.318 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperLrz3SegmentTraceReplay#replayMatchesTrace` | failure | 34 `queue.s3k_kos_direct.busy`: true / false | 631 | 6891 | 2.602 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperLrzSegmentTraceReplay#replayMatchesTrace` | failure | 34 `queue.s3k_kos_direct.busy`: true / false | 280 | 3199 | 1.239 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMgz2SegmentTraceReplay#replayMatchesTrace` | failure | 223 `rings`: 225 / 224 | 3103 | 30981 | 7.483 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMgzSegmentTraceReplay#replayMatchesTrace` | failure | 1131 `rings`: 49 / 50 | 1365 | 8832 | 2.506 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMhz2SegmentTraceReplay#replayMatchesTrace` | failure | 1120 `y`: 0x09F1 / 0x09F0 | 901 | 6345 | 1.990 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMhz3SegmentTraceReplay#replayMatchesTrace` | failure | 1483 `y_speed`: -0096 / 0x0096 | 568 | 10691 | 3.182 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMhz4SegmentTraceReplay#replayMatchesTrace` | failure | 1795 `y_speed`: 0x0450 / -0450 | 142 | 2685 | 1.164 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMhz5SegmentTraceReplay#replayMatchesTrace` | failure | 256 `rings`: 135 / 136 | 17 | 1537 | 0.840 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMhz6SegmentTraceReplay#replayMatchesTrace` | failure | 168 `y_speed`: 0x0000 / -0400 | 377 | 5181 | 1.787 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperMhzSegmentTraceReplay#replayMatchesTrace` | failure | 0 `y_speed`: 0x0038 / 0x0000 | 25 | 533 | 0.507 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperPachinko2BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 127 | 0.384 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperPachinko3BonusTraceReplay#replayMatchesTrace` | failure | 1451 `rings`: 66 / 65 | 11 | 2973 | 1.348 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperPachinko4BonusTraceReplay#replayMatchesTrace` | failure | 1567 `rings`: 70 / 71 | 12 | 3177 | 1.388 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperPachinko5BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 2730 | 1.265 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperPachinkoBonusTraceReplay#replayMatchesTrace` | failure | 151 `rings`: 39 / 38 | 13 | 2589 | 1.167 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSlots2BonusTraceReplay#replayMatchesTrace` | failure | 2007 `player_mapping_frame`: 0x0096 / 0x009A | 2 | 2062 | 0.834 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSlotsBonusTraceReplay#replayMatchesTrace` | failure | 232 `rings`: 156 / 157 | 405 | 1919 | 0.775 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSoz2SegmentTraceReplay#replayMatchesTrace` | failure | 99 `rings`: 252 / 251 | 3388 | 34010 | 9.621 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSozSegmentTraceReplay#replayMatchesTrace` | failure | 3216 `x`: 0x0000 / 0x1A33 | 36 | 3208 | 1.172 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs10SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4624 | 0.416 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs11SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 5200 | 0.417 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs12SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 5020 | 0.402 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs13SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 8403 | 0.471 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs14SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4463 | 0.407 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs2SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 5415 | 0.419 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs3SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 5566 | 0.430 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs4SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4981 | 0.421 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs5SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4494 | 0.400 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs6SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 5887 | 0.414 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs7SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4788 | 0.402 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs8SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4948 | 0.405 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSs9SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 3834 | 0.388 |
| `com.openggf.tests.trace.s3k.knucklessuper.TestS3kKnucklesSuperSsSpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 4344 | 0.392 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaAiz2SegmentTraceReplay#replayMatchesTrace` | failure | 92 `rings`: 70 / 69 | 375 | 2021 | 0.709 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaAiz3SegmentTraceReplay#replayMatchesTrace` | failure | 97 `rings`: 85 / 86 | 516 | 7446 | 2.113 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaAiz4SegmentTraceReplay#replayMatchesTrace` | failure | 96 `rings`: 35 / 34 | 213 | 3115 | 1.054 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaAiz5SegmentTraceReplay#replayMatchesTrace` | failure | 729 `rings`: 80 / 0 | 212 | 3502 | 1.246 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaAizSegmentTraceReplay#replayMatchesTrace` | failure | 4568 `x`: 0x0000 / 0x2775 | 35 | 4560 | 1.364 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaGumball2BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 1475 | 0.620 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaHcz2SegmentTraceReplay#replayMatchesTrace` | failure | 91 `rings`: 44 / 43 | 1038 | 11909 | 3.274 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaHcz3SegmentTraceReplay#replayMatchesTrace` | failure | 3834 `x`: 0x0000 / 0x357B | 39 | 3830 | 1.305 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaHcz4SegmentTraceReplay#replayMatchesTrace` | failure | 1110 `player_animation_id`: 0x001B / 0x0002 | 2 | 2097 | 0.772 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaHcz5SegmentTraceReplay#replayMatchesTrace` | failure | 3320 `x`: 0x0000 / 0x42C2 | 39 | 3316 | 1.028 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaHcz6SegmentTraceReplay#replayMatchesTrace` | failure | 2109 `player_animation_id`: 0x0021 / 0x0020 | 303 | 8294 | 2.600 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaHczSegmentTraceReplay#replayMatchesTrace` | failure | 0 `player_mapping_frame`: 0x00CB / 0x00CA | 36 | 3057 | 0.848 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaMgz2SegmentTraceReplay#replayMatchesTrace` | failure | 1854 `y`: 0x026E / 0x0265 | 2 | 2076 | 0.620 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaMgz3SegmentTraceReplay#replayMatchesTrace` | failure | 267 `rings`: 151 / 150 | 919 | 8515 | 2.056 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaMgzSegmentTraceReplay#replayMatchesTrace` | failure | 1516 `g_speed`: 0x0000 / 0x0398 | 529 | 8584 | 2.029 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaSlots2BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 1096 | 0.359 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaSlots3BonusTraceReplay#replayMatchesTrace` | failure | 248 `rings`: 80 / 81 | 867 | 5225 | 1.272 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaSlots4BonusTraceReplay#replayMatchesTrace` | failure | 232 `rings`: 124 / 123 | 295 | 1455 | 0.431 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaSlots5BonusTraceReplay#replayMatchesTrace` | pass | none | 0 | 1637 | 0.481 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaSs2SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 7059 | 0.188 |
| `com.openggf.tests.trace.s3k.megarun.TestS3kMegaSs3SpecialStageTraceReplay#replayProducesFaithfulReport` | pass | none | 0 | 6402 | 0.189 |

The root additionally adds two recorded-team bootstrap controls (both pass), one destination-ring resolver control, and the S1 lane adds three held-tail controls; the Crawl lane adds four object controls. These are separated from the 150 replay frontiers above. Existing report-backed known-red expectations are not rewritten.
