#!/usr/bin/env python3
"""Survey S1 REV01 ROM song forms with an independent duration/control-flow interpreter.

Inputs: existing unmodified S1 REV01 ROM (--rom), optional music ID (--id).
Origin: 2026-10-07 Sitar Hero full S1 catalogue. Research metadata only; this tool
never exports ROM bytes or supplies runtime assets. Native owners: SetDuration,
TempoWait, cfSetTempo, cfJumpTo, cfRepeatAtPos, cfJumpToGosub in s1.sounddriver.asm.
It omits synthesis, modulation and note-fill releases; those do not set form length.
"""
import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path

MUSIC_TABLE = 0x71A9C
SHA1 = '69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b'

@dataclass
class Track:
    kind: str
    channel: int
    pos: int
    divider: int
    duration: int = 1
    saved: int = 0
    saved_scaled: int = 0
    note: int = 0x80
    units: int = 0
    scaled_units: int = 0
    active: bool = True
    stack: list = field(default_factory=list)
    counters: dict = field(default_factory=dict)
    visited: dict = field(default_factory=dict)
    jumps: list = field(default_factory=list)
    attacks: list = field(default_factory=list)
    stop_frame: int | None = None
    noise: bool = False
    tied: bool = False
    transpose: int = 0
    voices: list = field(default_factory=list)


def survey(rom, music_id, frames=36001, events=False):
    base = int.from_bytes(rom[MUSIC_TABLE + (music_id - 0x81) * 4: MUSIC_TABLE + (music_id - 0x81) * 4 + 4], 'big')
    word = lambda p: int.from_bytes(rom[p:p+2], 'big')
    signed_word = lambda p: int.from_bytes(rom[p:p+2], 'big', signed=True)
    fm_count, psg_count, divider, tempo = rom[base+2:base+6]
    countdown = tempo
    tracks = []
    p = base + 6
    for i in range(fm_count):
        kind, channel = ('DAC', 5) if i == 0 else ('FM', i-1)
        tracks.append(Track(kind, channel, base+word(p), divider, transpose=rom[p+2] if i else 0))
        p += 4
    for i in range(psg_count):
        tracks.append(Track('PSG', i, base+word(p), divider, transpose=rom[p+2]))
        p += 6
    changes = []
    divider_changes = []
    for frame in range(frames):
        countdown = (countdown-1) & 255
        hold = countdown == 0
        if hold:
            countdown = tempo
        for t in tracks:
            if not t.active:
                continue
            t.duration = (t.duration + int(hold) - 1) & 255
            if t.duration:
                continue
            t.tied = False
            for guard in range(10000):
                t.visited.setdefault(t.pos, (t.units, t.scaled_units, frame))
                command = rom[t.pos]
                command_pos = t.pos
                t.pos += 1
                if command < 0xE0:
                    if command >= 0x80:
                        t.note = command
                        if rom[t.pos] < 0x80:
                            t.saved = rom[t.pos]
                            t.saved_scaled = (t.saved * t.divider) & 255
                            t.pos += 1
                    else:
                        t.saved = command
                        t.saved_scaled = (t.saved * t.divider) & 255
                    # SavedDuration is already scaled. E5/EB do not rescale a
                    # duration-only continuation which omits an explicit byte.
                    scaled = t.saved_scaled
                    t.duration = scaled
                    if t.note != 0x80 and not t.tied:
                        t.attacks.append(dict(frame=frame, unit=t.units, scaled_unit=t.scaled_units,
                                              offset=t.pos-base, pitch=t.note, noise=t.noise))
                    t.units += t.saved
                    t.scaled_units += scaled or 256
                    break
                if command in (0xF2, 0xEE, 0xE4):
                    t.active = False
                    t.stop_frame = frame
                    break
                if command == 0xE3:
                    t.pos = t.stack.pop()
                elif command in (0xF6, 0xF8):
                    target = t.pos + 1 + signed_word(t.pos)
                    if command == 0xF8:
                        t.stack.append(t.pos+2)
                    else:
                        previous = t.visited.get(target)
                        if previous and len(t.jumps) < 3:
                            t.jumps.append(dict(frame=frame, target=target-base,
                                intro_units=previous[0], loop_units=t.units-previous[0],
                                intro_scaled=previous[1], loop_scaled=t.scaled_units-previous[1],
                                first_frame=previous[2], from_offset=command_pos-base))
                    t.pos = target
                elif command == 0xF7:
                    index, count = rom[t.pos:t.pos+2]
                    t.pos += 2
                    t.counters[index] = (t.counters.get(index, 0) or count)-1
                    t.pos = t.pos+1+signed_word(t.pos) if t.counters[index] else t.pos+2
                elif command == 0xE7:
                    t.tied = True
                elif command in (0xED, 0xF1, 0xF4, 0xF9):
                    pass
                elif command == 0xF0:
                    t.pos += 4
                elif command in (0xE0, 0xE1, 0xE2, 0xE5, 0xE6, 0xE8, 0xE9, 0xEA, 0xEB, 0xEC, 0xEF, 0xF3, 0xF5):
                    value = rom[t.pos]
                    t.pos += 1
                    if command == 0xEA:
                        tempo = countdown = value
                        changes.append(dict(frame=frame, scaled_unit=t.scaled_units, unit=t.units, tempo=value))
                    elif command == 0xE5:
                        t.divider = value
                        divider_changes.append(dict(frame=frame, kind=t.kind, channel=t.channel,
                            scaled_unit=t.scaled_units, unit=t.units, divider=value, all_tracks=False))
                    elif command == 0xEB:
                        for other in tracks:
                            other.divider = value
                        divider_changes.append(dict(frame=frame, kind=t.kind, channel=t.channel,
                            scaled_unit=t.scaled_units, unit=t.units, divider=value, all_tracks=True))
                    elif command == 0xF3:
                        t.noise = True
                    elif command == 0xEF:
                        if not t.voices or t.voices[-1]['voice'] != value:
                            t.voices.append(dict(frame=frame, unit=t.units, voice=value))
                else:
                    raise ValueError(f'unsupported flag {command:02x} at {t.pos-1:06x}')
            else:
                raise ValueError('non-advancing control flow')
        if not any(t.active for t in tracks):
            break
    result = dict(id=f'{music_id:02x}', rom_offset=f'{base:06x}', divider=divider,
                  tempo=rom[base+5], end_frame=frame if not any(t.active for t in tracks) else None,
                  tempo_changes=changes, divider_changes=divider_changes, tracks=[])
    for t in tracks:
        first = t.attacks[:8]
        result['tracks'].append(dict(kind=t.kind, channel=t.channel, attacks=len(t.attacks),
            first_attack=first[0]['frame'] if first else None, stop_frame=t.stop_frame,
            end_units=t.units if not t.active else None, end_scaled=t.scaled_units if not t.active else None,
            jumps=t.jumps, first_units=[b['unit']-a['unit'] for a,b in zip(first,first[1:])],
            pitches=sorted(set(a['pitch'] for a in t.attacks)), noise=any(a['noise'] for a in t.attacks), voices=t.voices))
        if events:
            result['tracks'][-1]['events'] = t.attacks
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', required=True, type=Path)
    parser.add_argument('--id', type=lambda s: int(s, 0))
    parser.add_argument('--frames', type=int, default=36001)
    parser.add_argument('--events', action='store_true', help='include attack coordinates for production comparison')
    args = parser.parse_args()
    rom = args.rom.read_bytes()
    if hashlib.sha1(rom).hexdigest() != SHA1:
        parser.error('expected the S1 World REV01 ROM')
    if args.id is not None and not 0x81 <= args.id <= 0x93:
        parser.error('music ID must be 0x81..0x93')
    if not 1 <= args.frames <= 36001:
        parser.error('frames must be 1..36001')
    print(json.dumps([survey(rom, i, args.frames, args.events) for i in ([args.id] if args.id else range(0x81,0x94))], indent=2))

if __name__ == '__main__':
    main()
