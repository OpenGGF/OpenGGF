# Task 1 — ROM DPLC Invariants Report

## Scope

Created an immutable `DynamicArtRomProfile` for the pinned Sonic 1 and Sonic
2 REV01 retail ROMs. It provides the decision entry/return windows, accepted
S2 DMA return, completion callback sites, RAM state, DPLC table addresses, ROM
art spans, VRAM banks, and byte signatures consumed by later native observers.

## ROM evidence

The values were derived independently from the local disassemblies and checked
against the supplied retail ROM SHA-1s:

- S1 `Sonic_LoadGfx`: entry `0x14312`, RTS `0x1436A`; DPLC table `0x22310`;
  art `0x22610` / `0xA120`; staging RAM `0xC800` / `0x2E0`; pending state
  `0xF766` / `0xF767`; VRAM `0xF000`.
- S1 has separate `f_sonframechg` completion checks at `0x0D20`, `0x0E34`,
  `0x0F24`, and `0x1030`, covering level, special-stage, title-card, and
  continue VBlank variants.
- S2 `QueueDMATransfer`: entry `0x144E`, accepted return `0x14AA`; mixed DMA
  service `0x14AC`. Normal owner windows are Sonic `0x1B848..0x1B89A`,
  Tails' tails `0x1D184..0x1D1FE`, and Tails `0x1D1AC..0x1D1FE`.
- S2 additionally pins special-stage owner windows, the three LastLoadedDPLC
  bytes, command buffer `0xDC00`, next-slot pointer `0xDCFC`, normal and
  special-stage DPLC tables, normal ROM art spans, and all six VRAM banks.

`docs/s1disasm/_incObj/01 Sonic.asm`, `docs/s1disasm/sonic.asm`, and
`docs/s2disasm/s2.asm` supplied the routine semantics; the ROM-backed tests
pin the final retail offsets and bytes.

## TDD evidence

1. Added the two ROM-backed tests and project registrations before the profile.
2. Expected red command:

   ```bash
   BIZHAWK_HOME=<BIZHAWK_HOME> \
   S1_ROM_PATH='<S1 REV01>' S2_ROM_PATH='<S2 REV01>' \
   tools/bizhawk-headless/test.sh --filter DynamicArtRomProfile --jobs 1
   ```

   It failed as expected with `CS0246`: `DynamicArtRomProfile` was absent.
3. Implemented the immutable profile and reran the same command green.
4. Mutation check: changed the S1 decision entry from `0x14312` to `0x14313`.
   The S1 test failed with `Expected <82706> but was <82707>`. Restoring
   `0x14312` returned both focused tests to green.

## Verification

Final focused command (same as above) completed with:

```text
PASS DynamicArtRomProfile pins Sonic 1 REV01 player-DPLC callbacks and data
PASS DynamicArtRomProfile pins Sonic 2 REV01 player-DPLC callbacks and data
```

## Review round 2 fixes

The S2 queue test no longer treats `14 * 18` as its only stride/capacity
evidence. It pins QueueDMATransfer's five `move.w ..., (a1)+` command stores
at `0x145E`, `0x1468`, `0x1472`, `0x147C`, and `0x1486`, plus its final
`move.l ..., (a1)+` command store at `0x149A`. The test derives a 14-byte
stride from those ROM instructions, then derives capacity as the independently
pinned `0xFC` queue span divided by that stride. A paired, mutually consistent
but wrong profile stride/capacity now fails.

Verification command and output:

```bash
BIZHAWK_HOME=<BIZHAWK_HOME> \
S1_ROM_PATH='<S1 REV01>' S2_ROM_PATH='<S2 REV01>' \
tools/bizhawk-headless/test.sh --filter DynamicArtRomProfile --jobs 1
```

```text
/usr/lib/mono/xbuild/14.0/bin/Microsoft.Common.targets:  warning : TargetFrameworkVersion 'v4.8' not supported by this toolset (ToolsVersion: 14.0).
PASS DynamicArtRomProfile pins Sonic 1 REV01 player-DPLC callbacks and data
PASS DynamicArtRomProfile pins Sonic 2 REV01 player-DPLC callbacks and data
```

Mono/xbuild emitted its existing TargetFrameworkVersion v4.8 compatibility
warning, but the compile and both selected ROM-backed tests passed.

## Review

Reviewed every literal against the byte windows in the REV01 ROMs. The tests
hold their expected bytes separately from the profile, then also validate each
profile-provided opcode signature against ROM. Read-only collections prevent
later observers from mutating the published profile data.

## Review round 1 fixes

- Split the overloaded S2 DMA value into `DmaCommandStrideBytes = 14` and
  `DmaCommandCapacity = 18`. The focused ROM test now derives the queue span
  from the `0xDCFC` next-slot and `0xDC00` queue-base instruction operands,
  verifies the resulting `0xFC` bytes, and checks that it equals
  `14 * 18`.
- Added independent REV01 instruction/data checks for the S1 staging address,
  VDP DMA length, and encoded VRAM destination; S1 art end boundary; S2 normal
  art source operands and end boundaries; and each S2 normal/special-stage
  VRAM-bank immediate. These cover profile literals that previously had only
  matching profile assertions or head signatures.
- The preceding commit also captured the S2 special-stage project and
  `TestMain` registrations. They were pre-existing shared prerequisite
  registrations, not created or modified by this review-fix round, so they are
  deliberately preserved for final scope adjudication.

### TDD and verification transcript

The new coverage was added before the profile correction. The focused command
first failed as expected because `RamLayout.DmaCommandStrideBytes` did not
exist; after adding the explicit member and correcting capacity to 18, it
completed as follows:

```bash
BIZHAWK_HOME=<BIZHAWK_HOME> \
S1_ROM_PATH='<S1 REV01>' S2_ROM_PATH='<S2 REV01>' \
tools/bizhawk-headless/test.sh --filter DynamicArtRomProfile --jobs 1
```

```text
/usr/lib/mono/xbuild/14.0/bin/Microsoft.Common.targets:  warning : TargetFrameworkVersion 'v4.8' not supported by this toolset (ToolsVersion: 14.0).
PASS DynamicArtRomProfile pins Sonic 1 REV01 player-DPLC callbacks and data
PASS DynamicArtRomProfile pins Sonic 2 REV01 player-DPLC callbacks and data
```
