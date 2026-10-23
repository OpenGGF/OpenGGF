#!/usr/bin/env python3
"""Check constructed PCM integer cases against pinned GPGX pcm.c, without resampling.

Inputs: an explicitly supplied local directory containing the verified 051d430d
pcm.c, pcm.h, types.h and blip_buf.h, plus a C11 compiler. Origin: bounded PCM
device task on feature/ai-sonic-cd-local at 39dd03c505e2d72008e39c0684b611806d310760.
The actual unchanged pcm.c is compiled in an external temporary directory with
a minimal shared.h and a stub that only collects blip deltas. No libm, ROM,
commercial RAM, Java, Maven, target directory, audio sink or resampling is used.
This modified build environment is secondary integer source-model evidence,
NOT the official native core, a native capture, or independent hardware proof.
"""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


PIN = "051d430d3d1b54625f9900c8f152d7f232e06daf"
SOURCE_HASHES = {
    "pcm.c": "64a657104fb7f958601c75587de37cf81ccf1f5d51d7d54f3138462a474ba3a6",
    "pcm.h": "b86a4d0fc67175dd5124b76a20a898f230682025969bead722863bede13e032d",
    "types.h": "1159e5ebd0f4bc004b3df0f9f661630a0da65f3fb0abcfbe4298b2f1332d6442",
    "blip_buf.h": "367414a873407fad24cf0207429cf71e62aed080a278f40884b157f25930428e",
}

SHARED_STUB = r"""
#ifndef PCM_INTEGER_SHARED_H
#define PCM_INTEGER_SHARED_H
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include "types.h"
#include "pcm.h"
_Static_assert(CHAR_BIT == 8 && sizeof(uint32) == 4 && sizeof(int) == 4,
               "requires eight-bit bytes and 32-bit C integer arithmetic");
_Static_assert(sizeof(int16) == 2 && sizeof(reg16_t) == 2 && (-1 >> 5) == -1,
               "requires native register sizes and arithmetic negative shift");
static struct { pcm_t pcm_hw; reg16_t regs[0x80]; } scd;
static struct { reg16_t dac; uint8 ram[0x4000]; } cdc;
static struct { blip_t *blips[2]; } snd;
static struct { int pcm_volume; } config = {100};
#define save_param(src, size) do { memcpy(state + bufferptr, (src), (size)); bufferptr += (size); } while (0)
#define load_param(dst, size) do { memcpy((dst), state + bufferptr, (size)); bufferptr += (size); } while (0)
#endif
"""

HARNESS = r"""
#include "shared.h"
struct event { unsigned time; int left, right; };
static struct event deltas[4096];
static unsigned delta_count, requested, ended;
static void fail(void) { fputs("invalid integer oracle operation\n", stderr); exit(2); }
void blip_clear(blip_t *b) { (void)b; delta_count = 0; }
void blip_set_rates(blip_t *b, double c, double s) { (void)b; (void)c; (void)s; fail(); }
int blip_clocks_needed(const blip_t *b, int s) { (void)b; (void)s; fail(); return 0; }
void blip_end_frame(blip_t *b, unsigned length) { (void)b; ended = length; }
void blip_add_delta_fast(blip_t *b, unsigned time, int l, int r) {
  (void)b;
  if (delta_count >= 4096 || time >= requested) fail();
  deltas[delta_count++] = (struct event){time, l, r};
}
/* pcm.c itself is byte-identical to the pinned source. Only its surrounding owners are stubs. */
#include "pcm.c"
static void run_samples(unsigned count) {
  if (!count || count > 4096) fail();
  int left = scd.pcm_hw.out[0], right = scd.pcm_hw.out[1];
  requested = count; delta_count = 0; ended = 0;
  pcm_run(count);
  if (ended != count) fail();
  unsigned event_index = 0;
  printf("{\"samples\":[");
  for (unsigned i = 0; i < count; i++) {
    int dl = 0, dr = 0, present = 0;
    if (event_index < delta_count && deltas[event_index].time == i) {
      dl = deltas[event_index].left; dr = deltas[event_index].right;
      present = 1; event_index++;
    }
    left += dl; right += dr;
    printf("%s[%d,%d,%d,%d,%s]", i ? "," : "", left, right, dl, dr, present ? "true" : "false");
  }
  if (event_index != delta_count || left != scd.pcm_hw.out[0] || right != scd.pcm_hw.out[1]) fail();
  printf("],\"channels\":[");
  for (unsigned i = 0; i < 8; i++) {
    const chan_t *c = &scd.pcm_hw.chan[i];
    printf("%s[%u,%u,%u,%u,%u,%u]", i ? "," : "", c->addr, c->st, c->ls.w, c->fd.w, c->env, c->pan);
  }
  printf("],\"bank\":%u,\"index\":%u,\"mask\":%u,\"enabled\":%s,\"output\":[%d,%d]}\n",
    (unsigned)((scd.pcm_hw.bank - scd.pcm_hw.ram) >> 12), scd.pcm_hw.index,
    scd.pcm_hw.status, scd.pcm_hw.enabled ? "true" : "false", left, right);
}
int main(void) {
  char line[128], op, extra;
  uint8 context[0x20000];
  int context_length = 0;
  pcm_reset();
  while (fgets(line, sizeof(line), stdin)) {
    unsigned long long a = 0, b = 0;
    if (!strchr(line, '\n')) fail();
    int fields = sscanf(line, " %c %llu %llu %c", &op, &a, &b, &extra);
    if (fields == 3 && op == 'W' && a < 0x2000 && b < 256) {
      /* Suppress the CPU synchronization layer explicitly; semantic samples use N. */
      pcm_write((unsigned)a, (uint8)b, scd.pcm_hw.cycles);
    } else if (fields == 2 && op == 'R' && a < 0x2000) {
      printf("{\"read\":%u}\n", pcm_read((unsigned)a, scd.pcm_hw.cycles));
    } else if (fields == 2 && op == 'N' && a > 0 && a <= 4096) {
      run_samples((unsigned)a);
    } else if (fields == 3 && op == 'P' && a < 8 && b <= 0xffffffffULL) {
      /* Constructed accumulator boundary input, not a native gameplay observation. */
      scd.pcm_hw.chan[a].addr = (uint32)b;
    } else if (fields == 1 && op == 'Z') {
      pcm_reset();
    } else if (fields == 1 && op == 'C') {
      context_length = pcm_context_save(context);
      if (context_length <= 0 || context_length > (int)sizeof(context)) fail();
    } else if (fields == 1 && op == 'L' && context_length > 0) {
      if (pcm_context_load(context) != context_length) fail();
    } else fail();
  }
  return ferror(stdin) ? 2 : 0;
}
"""


def verified_sources(source_dir: Path) -> dict[str, bytes]:
    result = {}
    for name, expected in SOURCE_HASHES.items():
        data = (source_dir / name).read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected:
            raise ValueError(f"{name}: SHA-256 mismatch: expected {expected}, got {actual}")
        result[name] = data
    return result


@contextlib.contextmanager
def built_oracle(source_dir: Path, compiler: str = "cc"):
    sources = verified_sources(source_dir)
    if sys.byteorder != "little":
        raise RuntimeError("the pinned LSB_FIRST register-byte setup requires a little-endian host")
    executable = shutil.which(compiler)
    if executable is None:
        raise RuntimeError(f"C compiler not found: {compiler}")
    # Never write into the checkout or its target. The unmodified sources and binary are temporary.
    with tempfile.TemporaryDirectory(prefix="openggf-pcm-integer-", dir="/tmp") as directory:
        scratch = Path(directory)
        for name, data in sources.items():
            (scratch / name).write_bytes(data)
        (scratch / "shared.h").write_text(SHARED_STUB)
        (scratch / "oracle.c").write_text(HARNESS)
        program = scratch / "pcm-oracle"
        subprocess.run([executable, "-std=c11", "-O2", "-Wall", "-Wextra", "-Werror",
                        "-Wno-sign-compare", "-DLSB_FIRST", str(scratch / "oracle.c"), "-o", str(program)],
                       check=True, capture_output=True, text=True, timeout=30)
        yield program


def run_commands(program: Path, commands: list[str]) -> list[dict]:
    text = "\n".join(commands) + "\n"
    if len(text) > 1_000_000:
        raise ValueError("integer oracle command input exceeds its bound")
    result = subprocess.run([str(program)], input=text, check=True, capture_output=True,
                            text=True, timeout=10)
    return [json.loads(line) for line in result.stdout.splitlines()]


def ram(address: int, *values: int) -> list[str]:
    commands = []
    for value in values:
        commands.extend([f"W 7 {0x80 | (address >> 12)}", f"W {0x1000 | (address & 0xfff)} {value}"])
        address += 1
    return commands


def channel(index=0, envelope=32, pan=0x11, frequency=0x0800, loop=0, start=1) -> list[str]:
    return [f"W 7 {0xc0 | index}", f"W 0 {envelope}", f"W 1 {pan}",
            f"W 2 {frequency & 255}", f"W 3 {frequency >> 8}",
            f"W 4 {loop & 255}", f"W 5 {loop >> 8}", f"W 6 {start}"]


def check_constructed_cases(program: Path) -> list[str]:
    """Literal expectations shared in meaning with Jupiter checks, never Java-generated output."""
    passed = []

    def check(name, commands, expected_samples, cursor=None):
        runs = run_commands(program, commands)
        actual = [step for run in runs for step in run["samples"]]
        if actual != expected_samples:
            raise AssertionError(f"{name}: expected {expected_samples}, got {actual}")
        if cursor is not None and runs[-1]["channels"][0][0] != cursor:
            raise AssertionError(f"{name}: unexpected channel-0 cursor {runs[-1]['channels'][0][0]:x}")
        passed.append(name)

    # Constructed magnitudes; negative products must round down at >> 5, not toward zero.
    for data, env, pan, left, right in [(0, 255, 255, 0, 0), (128, 255, 255, 0, 0),
            (1, 1, 17, -1, -1), (129, 1, 17, 0, 0), (127, 255, 255, -15181, -15181),
            (254, 255, 255, 15060, 15060), (130, 16, 33, 1, 2), (2, 16, 33, -1, -2),
            (254, 255, 33, 1004, 2008), (126, 255, 33, -1005, -2009)]:
        check(f"signed-{data}-{env}-{pan}", ram(0x100, data) + channel(envelope=env, pan=pan, frequency=0)
              + ["W 8 254", "N 1"], [[left, right, left, right, True]], 0x80000)

    check("fractional-read-before-increment", ram(0x1200, 0x81, 0x82, 0x83)
          + channel(pan=0x21, frequency=0x0801, start=0x12) + ["W 8 254", "N 3"],
          [[1, 2, 1, 2, True], [2, 4, 1, 2, True], [3, 6, 1, 2, True]], 0x00901803)
    check("marker-loop-no-increment", ram(0x100, 0x81, 0xff) + ram(0x1234, 0x85, 0x87)
          + channel(frequency=0x0801, loop=0x1234) + ["W 8 254", "N 1", "N 1", "N 2"],
          [[1, 1, 1, 1, True], [5, 5, 4, 4, True], [5, 5, 0, 0, True], [7, 7, 2, 2, True]], 0x91b002)
    check("double-marker", ram(0x100, 0x83, 0xff) + ram(0xffff, 0xff)
          + channel(loop=0xffff) + ["W 8 254", "N 3"],
          [[3, 3, 3, 3, True], [0, 0, -3, -3, True], [0, 0, 0, 0, True]], 0x7fff800)
    commands = []
    for index in range(8):
        commands += ram((index + 1) << 8, 0xfe if index < 4 else 0x7e)
        commands += channel(index, 255, 0xff, index + 1, start=index + 1)
    check("eight-channel-final-clip", commands + ["W 8 0", "N 1"], [[-4, -4, -4, -4, True]])
    commands = ram(0x100, 0xfe)
    for index in range(8):
        commands += channel(index, 255, 0xff, 0)
    check("clipping-and-wide-deltas", commands + ["W 8 0", "N 1"] + ram(0x100, 0x7f)
          + ["N 1"] + ram(0x100, 0xfe) + ["N 1"],
          [[32767, 32767, 32767, 32767, True], [-32768, -32768, -65535, -65535, True],
           [32767, 32767, 65535, 65535, True]])
    check("disabled-mute-and-resume", ram(0x100, 0x81, 0x82, 0x83) + channel()
          + ["W 8 254", "N 1", "W 7 64", "N 3", "N 1", "W 6 2", "W 7 192", "N 1"],
          [[1, 1, 1, 1, True], [0, 0, -1, -1, True], [0, 0, 0, 0, False],
           [0, 0, 0, 0, False], [0, 0, 0, 0, False], [2, 2, 2, 2, True]], 0x81000)
    check("uint32-progress-wrap", ram(0xffff, 0x82) + ram(0x1f, 0x83)
          + channel(frequency=0xffff, start=0xff) + ["W 8 254", "P 0 4294967280", "N 1", "N 1"],
          [[2, 2, 2, 2, True], [3, 3, 1, 1, True]], 0x1ffee)

    base = ram(0x100, 0x81, 0x02, 0x85, 0xff) + channel(frequency=0x401, loop=0x101) + ["W 8 254"]
    whole = run_commands(program, base + ["N 37"])[0]
    chunks = run_commands(program, base + [f"N {n}" for n in [1, 7, 4, 19, 6]])
    if whole["samples"] != [step for chunk in chunks for step in chunk["samples"]]:
        raise AssertionError("chunking changed native integer samples/deltas")
    if whole["channels"] != chunks[-1]["channels"]:
        raise AssertionError("chunking changed native integer channel progress")
    passed.append("chunked-sample-equivalence")
    restored = run_commands(program, base + ["N 7", "C", "N 29", "Z", "W 4096 255", "L", "N 29"])
    if restored[1] != restored[2]:
        raise AssertionError("native integer context did not replay RAM/progress/output")
    passed.append("context-restore-replay")
    reads = run_commands(program, ["W 7 10", "W 4096 171", "W 8191 255", "W 7 255", "W 0 73",
                                  "W 7 138", "R 4096", "R 8191", "R 0", "R 16", "R 17"])
    if reads != [{"read": n} for n in [171, 255, 255, 0, 0]]:
        raise AssertionError("bank/readback/register selection mismatch")
    passed.append("bank-and-readback")
    return passed


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--cc", default="cc")
    args = parser.parse_args(argv)
    with built_oracle(args.source_dir, args.cc) as program:
        checks = check_constructed_cases(program)
    print(json.dumps({"basis": "secondary pinned-C integer harness; not official core or hardware proof",
                      "gpgx_commit": PIN, "source_sha256": SOURCE_HASHES,
                      "passed": checks, "count": len(checks), "java_executed": False,
                      "resampling": False}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
