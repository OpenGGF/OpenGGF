# 68000 Assembly Primer

This is a one-page reference for reading Motorola 68000 assembly as it appears in the
Sonic disassemblies. It covers just enough to follow object routines and understand the
code shown in the [Mapping Exercises](mapping-exercises.md). It is not a complete 68000
architecture guide.

## Registers

The 68000 has 16 general-purpose registers:

- **d0-d7** -- Data registers. Used for arithmetic, comparisons, and temporary values.
- **a0-a7** -- Address registers. Used as pointers to memory locations.

In Sonic object code, there are strong conventions:

| Register | Typical use |
|----------|-------------|
| `a0` | Pointer to the current object's data (its "slot" in object RAM) |
| `a1` | Pointer to another object, or to the player character |
| `d0` | General scratch register, often holds the routine index |
| `d1` | Distance results, temporary calculations |
| `d2`-`d4` | Temporary values specific to each routine |
| `a7` | Stack pointer (rarely seen explicitly in object code) |

## Size Suffixes

Most instructions have a size suffix that controls how many bytes they operate on:

| Suffix | Name | Size | Range (signed) |
|--------|------|------|----------------|
| `.b` | Byte | 1 byte | -128 to 127 |
| `.w` | Word | 2 bytes | -32768 to 32767 |
| `.l` | Long | 4 bytes | Full 32-bit range |

Example: `move.b routine(a0),d0` reads one byte from the object's routine field.

## Common Instructions

### Data Movement

| Instruction | What it does |
|-------------|-------------|
| `move.b src, dst` | Copy a byte from src to dst |
| `move.w src, dst` | Copy a word (2 bytes) |
| `move.l src, dst` | Copy a long (4 bytes) |
| `moveq #N, dN` | Quick move: load a small constant (-128 to 127) into a data register |
| `lea addr, aN` | Load effective address: put the address itself (not its contents) into an address register |
| `clr.b dst` | Clear (set to zero) |

### Arithmetic

| Instruction | What it does |
|-------------|-------------|
| `add.w src, dst` | dst = dst + src |
| `sub.w src, dst` | dst = dst - src |
| `addq.b #N, dst` | Quick add small constant (1-8) |
| `subq.b #N, dst` | Quick subtract small constant (1-8) |
| `neg.w dst` | Negate (two's complement) |
| `ext.w dN` | Sign-extend byte to word |

### Bitwise

| Instruction | What it does |
|-------------|-------------|
| `and.b src, dst` | Bitwise AND |
| `or.b src, dst` | Bitwise OR |
| `ori.b #N, dst` | OR immediate value |
| `andi.b #N, dst` | AND immediate value |
| `btst #N, dst` | Test bit N (sets zero flag if bit is 0) |
| `bset #N, dst` | Set bit N to 1 |
| `bclr #N, dst` | Clear bit N to 0 |

### Comparison and Branching

The 68000 compares values first, then branches based on the result:

| Instruction | What it does |
|-------------|-------------|
| `cmp.w src, dst` | Compare (dst - src), set condition flags, discard result |
| `cmpi.w #N, dst` | Compare with immediate value |
| `tst.b dst` | Compare with zero |

Then a conditional branch:

| Branch | Condition | Meaning |
|--------|-----------|---------|
| `beq` | Equal / zero | Result was zero |
| `bne` | Not equal | Result was not zero |
| `bcc` / `bhs` | Carry clear / higher or same | Unsigned >= |
| `bcs` / `blo` | Carry set / lower | Unsigned < |
| `bhi` | Higher | Unsigned > |
| `bls` | Lower or same | Unsigned <= |
| `bpl` | Plus | Negative flag clear (includes zero) |
| `bmi` | Minus | Negative flag set |
| `bge` | Greater or equal | Signed >= |
| `blt` | Less than | Signed < |

`bra` branches unconditionally (like a goto). `bsr` branches to a subroutine (like a
function call), and `rts` returns from it.

### Jumps and Subroutines

| Instruction | What it does |
|-------------|-------------|
| `jmp addr` | Jump to the effective address (including indirect/indexed modes) |
| `jsr addr` | Jump to subroutine (pushes return address onto stack) |
| `rts` | Return from subroutine |
| `bra label` | Branch always (relative, short jump) |
| `bsr label` | Branch to subroutine (relative) |

### Loops

| Instruction | What it does |
|-------------|-------------|
| `dbf dN, label` | Decrement the low word of dN; branch if it is not $FFFF (loop counter) |

## Object Field Access

Object data in the disassembly is accessed as offsets from the `a0` register. Named
constants make these readable:

```asm
move.b  routine(a0),d0       ; Read this object's current routine number
move.w  x_pos(a0),d0         ; Read this object's X position
addq.b  #2,routine(a0)       ; Advance to the next routine (next state)
move.b  #$9B,collision_flags(a0) ; Set collision type to "hurts player"
```

The field names (`routine`, `x_pos`, `collision_flags`, etc.) are defined in the
disassembly's constants file and resolve to byte offsets. See the
[Per-Game Notes](per-game-notes.md) for the field name differences between S1 and S2.

## The Routine Dispatch Pattern

Almost every object starts with the same four-instruction pattern:

```asm
Obj22:                                    ; Object entry point
    moveq   #0,d0                         ; Clear d0
    move.b  routine(a0),d0                ; Load routine number (0, 2, 4, 6...)
    move.w  Obj22_Index(pc,d0.w),d1       ; Look up offset in jump table
    jmp     Obj22_Index(pc,d1.w)          ; Jump to the routine

Obj22_Index:
    dc.w Obj22_Init - Obj22_Index         ; routine 0: initialization
    dc.w Obj22_Main - Obj22_Index         ; routine 2: main behavior
    dc.w Obj22_ShootArrow - Obj22_Index   ; routine 4: shoot arrow
```

This is a state machine. The `routine` field determines which behavior runs. Routine
values increment by **2** (not 1) because the jump table contains 16-bit word offsets,
and each word is 2 bytes. So the states are numbered 0, 2, 4, 6, etc.

When an object advances to the next state, it does:
```asm
addq.b  #2,routine(a0)   ; Move from routine 0 to routine 2
```

## Animation Script Format

Animation scripts are inline data tables with a specific command byte convention:

```asm
Ani_obj22:
    dc.w byte_idle - Ani_obj22       ; Animation 0 offset
    dc.w byte_detect - Ani_obj22     ; Animation 1 offset
    dc.w byte_fire - Ani_obj22       ; Animation 2 offset

byte_idle:   dc.b $1F, 1, $FF             ; delay=$1F, frame 1, then loop
byte_detect: dc.b $03, 1, 2, $FF          ; delay=$03, frames 1,2, then loop
byte_fire:   dc.b $07, 3, 4, $FC, 4, 3, 1, $FD, 0  ; complex sequence
```

For this ordinary `AnimateSprite` script, the first byte reloads the animation
timer. Because it decrements and advances when negative, a delay of 3 holds a
normal frame for four updates. Then come mapping frame indices and command bytes.
Player-specific animation routines can give the leading byte other meanings.

| Byte | Command | Meaning |
|------|---------|---------|
| `$FF` | Loop | Restart the animation from the beginning |
| `$FE` | Loop back | Move back by the number of script entries in the next byte |
| `$FD` | Change animation | Set the animation ID from the next byte |
| `$FC` | Routine increment | Advance `routine` by 2, clear the timer and advance past this command |

In the firing animation above, frames 3 and 4 are shown, then `$FC` advances to
the arrow-spawn routine for the next object dispatch. Animation continues through
frames 4, 3 and 1, then `$FD,0` selects animation 0. See
`Anim_End_FE`, `Anim_End_FD` and `Anim_End_FC (s2.asm:30465-30488)`.

## Fixed-Point Numbers

The usual velocity fields use signed **8.8 fixed-point**: divide the word by 256
to obtain pixels per update. Position accumulators commonly use **16.16 fixed-point**;
their integer word is what `x_pos`/`y_pos` reads. Do not treat the two layouts as
interchangeable.

| Hex value | Decimal | Meaning |
|-----------|---------|---------|
| `$0400` | 1024 | 4.0 pixels per frame |
| `$0180` | 384 | 1.5 pixels per frame |
| `$0040` | 64 | 0.25 pixels per frame |
| `$FC00` | -1024 | -4.0 pixels per frame (leftward/upward) |

When you see `move.w #$400,x_vel(a0)`, that sets the horizontal velocity to 4 pixels
per frame.

## dc Directives

These are data definitions, not instructions. They place raw values into the ROM:

| Directive | Size | Example |
|-----------|------|---------|
| `dc.b` | Byte | `dc.b $1F, 1, $FF` -- three bytes |
| `dc.w` | Word | `dc.w $0400` -- one 16-bit value |
| `dc.l` | Long | `dc.l Obj22_MapUnc_25804` -- a 32-bit address |

## Putting It Together

Here is a complete annotated example -- the ArrowShooter's player detection routine:

```asm
Obj22_DetectPlayer:
    move.w  x_pos(a0),d0          ; d0 = shooter's X position
    sub.w   x_pos(a1),d0          ; d0 = shooter X - player X
    bcc.s   +                     ; if result >= 0 (player is to the left), skip
    neg.w   d0                    ; make d0 positive (absolute distance)
+
    cmpi.w  #$40,d0               ; is distance < 64 pixels?
    bhs.s   +                     ; if distance >= 64, skip (not detected)
    moveq   #1,d2                 ; player detected: set d2 = 1
+
    rts                           ; return
```

This reads as: "Calculate the horizontal distance between the shooter and the player.
If the absolute distance is less than 64 pixels, set the detection flag."

## Next Steps

- [Mapping Exercises](mapping-exercises.md) -- Apply this knowledge to trace real features
- [Per-Game Notes](per-game-notes.md) -- S1/S2/S3K field name and format differences
