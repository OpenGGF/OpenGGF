#!/usr/bin/env python3
"""Generate the sample's original, public-domain-equivalent test waveform."""

from __future__ import annotations

import math
from pathlib import Path
import struct
import wave


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "audio" / "gallery-theme.wav"
RATE = 8_000
FRAMES = 16_000


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(RATE)
        for frame in range(FRAMES):
            # Two quiet integer-cycle tones make the two-second loop seamless.
            value = 0.35 * math.sin(2.0 * math.pi * 220.0 * frame / RATE)
            value += 0.15 * math.sin(2.0 * math.pi * 330.0 * frame / RATE)
            target.writeframesraw(struct.pack("<h", round(value * 32767)))


if __name__ == "__main__":
    main()
