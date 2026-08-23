# Sonic 1 FM5 YM busy-write source program

This checked artifact models the relative timing between hardware data writes for
the source-authenticated first FM5 voice attack. It does not model service-entry
time or use a sound/zone/movie runtime carve-out.

The calculation uses seven master cycles per 68000 cycle, 1,008 master cycles
per internal YM sample, a 47-cycle busy interval after a data write, and a
24-cycle decrement at each internal sample. Only row zero is normalized to zero;
busy state continues through every later section.

| Shape | Writes | Ledger SHA-256 |
|---|---:|---|
| `VOICE_NOTE` | 30 | `cc594fe04b19cdbc6fcc586e4f92b6b6c3e48774529273ace750461cea2c6eca` |
| `VOICE_PAN_NOTE` | 31 | `59000b1cbc90a3340e6f9142dfa96fd9ddea982af2d653ab5e52abf40557b689` |
