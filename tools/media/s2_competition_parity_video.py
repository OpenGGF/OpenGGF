"""Compose the uncut S2 Competition parity video: OpenGGF (left) beside native BizHawk (right).

Inputs: engine-full/engine.mkv + engine-frames.csv (one frame per committed route row) and
native-full/frames/*.png + frames.csv + audio.wav (one screenshot per emulated frame).
Native is the timeline: every native frame appears once, in order. The engine side starts at
the first native frame and is aligned at each ordinary level entry (first LFC-0 frame of an act);
where the engine reaches an entry sooner, its last frame before the entry is held and marked.
Usage: python3 tools/media/s2_competition_parity_video.py TASK_DIR OUT.mp4, where TASK_DIR holds
engine-full/ (DebugSonic2CompetitionRouteVideo output) and native-full/ (capture_native_references.py
with tools/bizhawk/capture_every_frame.lua and --dump-wave-frames). Native audio only.
Origin: 2026-11-08 PR #270 parity video request.
"""
import csv, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(sys.argv[1])
out = Path(sys.argv[2])
W, H, BAND = 640, 448, 48
FPS = "59.922743"
font = ImageFont.truetype("/usr/share/fonts/noto/NotoSansMono-Bold.ttf", 17)
small = ImageFont.truetype("/usr/share/fonts/noto/NotoSansMono-Bold.ttf", 14)

native = list(csv.DictReader(open(root / "native-full/frames.csv")))
engine = list(csv.DictReader(open(root / "engine-full/engine-frames.csv")))

def native_entries():
    hits, prev = [], None
    for i, r in enumerate(native):
        live = r["mode"] == "12" and r["lfc"] == "0"
        if live and not prev:
            hits.append(i)
        prev = live
    return hits

def engine_entries():
    hits, prev = [], None
    for i, r in enumerate(engine):
        live = r["phase"] == "1" and r["lfc"] == "0"
        if live and not prev:
            hits.append(i)
        prev = live
    return hits

ne, ee = native_entries(), engine_entries()
print("native entries", [int(native[i]["frame"]) for i in ne], "engine entry rows", ee)
assert len(ne) == len(ee) and len(ne) >= 1
# engine index shown at each native timeline index; -1 = engine not started (hold first frame).
shown, held = [], []
k = 0
for t in range(len(native)):
    while k + 1 < len(ne) and t >= ne[k + 1]:
        k += 1
    if t < ne[0]:
        e = ee[0] - (ne[0] - t)
    else:
        e = ee[k] + (t - ne[k])
        if k + 1 < len(ee):
            e = min(e, ee[k + 1] - 1)
    hold = e < 0 or (shown and e == shown[-1])
    e = max(0, min(e, len(engine) - 1))
    shown.append(e)
    held.append(hold)

decoder = subprocess.Popen(["ffmpeg", "-loglevel", "error", "-i", str(root / "engine-full/engine.mkv"),
                            "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
size = 320 * 224 * 3
encoder = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
                            "-s", f"{2 * W}x{H + BAND}", "-r", FPS, "-i", "-",
                            "-i", str(root / "native-full/audio.wav"),
                            "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "slow", "-crf", "14",
                            "-pix_fmt", "yuv420p", "-profile:v", "high", "-movflags", "+faststart", "-c:a", "aac", "-b:a", "192k",
                            "-shortest", str(out)], stdin=subprocess.PIPE)
cur, frame = -1, None

def native_image(index):
    im = Image.open(root / "native-full/frames" / f"{int(native[index]['frame']):05d}.png").convert("RGB")
    w, h = im.size
    active_h = 448 if h >= 480 else 224
    top = (h - active_h) // 2
    active_w = 256 if w < 300 else 320
    left = (w - active_w) // 2
    im = im.crop((left, top, left + active_w, top + active_h))
    im = im.resize((active_w * 2, H), Image.NEAREST)
    if active_w != 320:
        pad = Image.new("RGB", (W, H))
        pad.paste(im, ((W - im.width) // 2, 0))
        im = pad
    return im

for t in range(len(native)):
    while cur < shown[t]:
        frame = decoder.stdout.read(size)
        assert len(frame) == size, "engine video ended early"
        cur += 1
    eng = Image.frombytes("RGB", (320, 224), frame).resize((W, H), Image.NEAREST)
    canvas = Image.new("RGB", (2 * W, H + BAND), (18, 18, 24))
    canvas.paste(eng, (0, BAND))
    canvas.paste(native_image(t), (W, BAND))
    d = ImageDraw.Draw(canvas)
    d.text((12, 4), "OpenGGF (engine)", font=font, fill=(255, 210, 90))
    d.text((W + 12, 4), "Sonic 2 REV01 · BizHawk 2.11 GPGX (native + audio)", font=font, fill=(120, 200, 255))
    d.text((12, 26), f"route row {shown[t] + 1:5d}" + ("   HOLD (engine already at next entry)" if held[t] and t > 0 else ""),
           font=small, fill=(200, 200, 200))
    lag = "  LAG" if native[t]["lag"] == "1" else ""
    d.text((W + 12, 26), f"frame {int(native[t]['frame']):5d}{lag}", font=small, fill=(200, 200, 200))
    encoder.stdin.write(canvas.tobytes())
    if t % 2000 == 0:
        print("composed", t, flush=True)
encoder.stdin.close()
assert encoder.wait() == 0
decoder.kill()
print("held engine frames:", sum(held))
