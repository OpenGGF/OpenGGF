/* Native-source vector generator for the OpenGGF native-compatible PCM output stage.
 *
 * Purpose: produce the byte-exact reference PCM in src/test/resources/audio/native-compatible-pcm/v1/ by running
 * Genesis Plus GX's OWN chip, buffer, equaliser and PSG sources (ym3438.c, blip_buf.c, eq.c, psg.c from the pinned
 * BizHawk 427556b5 checkout, linked from outside this repository) on the production chip's recorded write schedule.
 * Only this driver is OpenGGF code (GPL-3, like the rest of tools/). It does not contain GPGX sound.c or system.c
 * text; it reproduces their documented behaviour, cited by line, in its own structure:
 *   - FM (sound.c YM3438_Update / sound_update, hq_fm 1): the 24-cycle pin sum held per internal clock, times 11, times
 *     fm_preamp 100 / 100, enters the buffer as a delta at master-clock time start + k * 42 inside each 3420 * 262
 *     master-clock NTSC frame; clocks at or past the frame end carry into the next frame.
 *   - PSG (sound.c sound_init/sound_reset/sound_update): psg_init(PSG_INTEGRATED), psg_reset, psg_config(0, 150, 0xff);
 *     each recorded byte goes to psg_write at its in-frame master clock before that YM clock; psg_end_frame before the
 *     buffer frame ends.
 *   - Output (system.c audio_init/audio_update): buffer of 44100 / 10 samples, rates 53,693,175 -> 44,100, every
 *     available sample read at each frame end, then config.filter: none; bit 0 a one-pole 16.16 low-pass with 16-bit
 *     carries between frames; bit 1 the eq.c three-band equaliser with gains percent / 100.0 and 16-bit clipping.
 * The equaliser cutoffs need the core's libm: the stock BizHawk core links musl, whose sin differs from glibc's
 * (see musl_sin.c); build.sh renames eq.o's sin reference to it.
 *
 * Inputs: strobes.csv (cycle,bus_port,value: YM bus strobe applied before internal clock `cycle`), psg_writes.csv
 * (master_clock,value), frame count, chip mode (1 = ym3438_mode_ym2612, BizHawk Nuked_YM2612), filter settings.
 * Output: interleaved stereo s16 LE plus a one-line info file.
 * Originating task: OpenGGF round-2 lane pcm-output, 2026-10-16 (feature/ai-s3k-r2-pcm-native-output).
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ym3438.h"
#include "blip_buf.h"
#include "eq.h"
#include "shared.h" /* tools/audio/native-pcm/shared_stub: types, psg.h, snd/config for psg.c */

struct pcmout_snd snd;
struct pcmout_config config = { 1 };

enum { YM_CLOCK_MCYCLES = 42, NTSC_FRAME_MCYCLES = 3420 * 262, SAMPLE_RATE = 44100, MAX_EVENTS = 200000 };

typedef struct { long when; int port; int value; } event_t;

static int read_events(const char *path, event_t *events, int with_port) {
  FILE *in = fopen(path, "r");
  char line[256];
  int count = 0;
  if (!in || !fgets(line, sizeof line, in)) { fprintf(stderr, "cannot read %s\n", path); exit(3); }
  while (count < MAX_EVENTS && fgets(line, sizeof line, in)) {
    event_t *e = &events[count];
    int fields = with_port ? sscanf(line, "%ld,%d,%d", &e->when, &e->port, &e->value)
                           : sscanf(line, "%ld,%d", &e->when, &e->value);
    if (fields == (with_port ? 3 : 2)) count++;
  }
  fclose(in);
  return count;
}

/* The FM side: the Nuked core, the pin-sum latch and the held level last delivered to the buffer. */
typedef struct {
  ym3438_t chip;
  short pins[24][2];
  int phase;           /* internal clock index inside the 24-cycle slot sweep */
  int held[2];         /* sum of the last complete sweep */
  int delivered[2];    /* level of the previous clock as delivered to the buffer */
  long clocks;         /* internal clocks run since reset */
} fm_side_t;

static void fm_clock(fm_side_t *fm, blip_t *blip, int time) {
  OPN2_Clock(&fm->chip, fm->pins[fm->phase]);
  fm->phase = (fm->phase + 1) % 24;
  if (fm->phase == 0) {
    int left = 0, right = 0;
    for (int slot = 0; slot < 24; slot++) { left += fm->pins[slot][0]; right += fm->pins[slot][1]; }
    fm->held[0] = left;
    fm->held[1] = right;
  }
  fm->clocks++;
  int level[2];
  for (int side = 0; side < 2; side++) level[side] = fm->held[side] * 11 * 100 / 100;
  blip_add_delta(blip, (unsigned) time, level[0] - fm->delivered[0], level[1] - fm->delivered[1]);
  fm->delivered[0] = level[0];
  fm->delivered[1] = level[1];
}

/* One-pole low-pass, 16.16 fixed point: C uint32 products and sum, kept as int32, arithmetic shift. */
static void low_pass(short *pcm, int frames, unsigned range, int16_t carry[2]) {
  for (int side = 0; side < 2; side++) {
    int32_t value = carry[side];
    for (int i = 0; i < frames; i++) {
      uint32_t mixed = (uint32_t) value * range + (uint32_t) (int32_t) pcm[2 * i + side] * (0x10000u - range);
      value = (int32_t) mixed >> 16;
      pcm[2 * i + side] = (short) value;
    }
    carry[side] = (int16_t) value;
  }
}

static int clip16(int value) {
  return value > 32767 ? 32767 : value < -32768 ? -32768 : value;
}

static void three_band(short *pcm, int frames, EQSTATE eq[2]) {
  for (int i = 0; i < frames; i++) {
    int left = (int) do_3band(&eq[0], pcm[2 * i]);
    int right = (int) do_3band(&eq[1], pcm[2 * i + 1]);
    pcm[2 * i] = (short) clip16(left);
    pcm[2 * i + 1] = (short) clip16(right);
  }
}

int main(int argc, char **argv) {
  if (argc != 14) {
    fprintf(stderr, "usage: %s strobes.csv ntsc_frames chip_mode filter lp_range low_freq high_freq "
                    "low_gain mid_gain high_gain out.s16 info.txt psg_writes.csv\n", argv[0]);
    return 2;
  }
  static event_t strobes[MAX_EVENTS], psg_writes[MAX_EVENTS];
  int strobe_count = read_events(argv[1], strobes, 1);
  int psg_count = read_events(argv[13], psg_writes, 0);
  int ntsc_frames = atoi(argv[2]), mode = atoi(argv[3]), filter = atoi(argv[4]);
  unsigned lp_range = (unsigned) atoi(argv[5]);
  int low_freq = atoi(argv[6]), high_freq = atoi(argv[7]);
  int gains[3] = { atoi(argv[8]), atoi(argv[9]), atoi(argv[10]) };
  FILE *pcm_out = fopen(argv[11], "wb"), *info = fopen(argv[12], "w");
  if (!pcm_out || !info) return 4;

  static fm_side_t fm;
  memset(&fm, 0, sizeof fm);
  OPN2_SetChipType(mode);
  OPN2_Reset(&fm.chip);
  blip_t *blip = blip_new(SAMPLE_RATE / 10);
  blip_set_rates(blip, 53693175.0, SAMPLE_RATE);
  blip_clear(blip);
  snd.blips[0] = blip;
  psg_init(PSG_INTEGRATED);
  psg_reset();
  psg_config(0, 150, 0xff);
  EQSTATE eq[2];
  for (int side = 0; side < 2; side++) {
    init_3band_state(&eq[side], low_freq, high_freq, SAMPLE_RATE);
    eq[side].lg = gains[0] / 100.0;
    eq[side].mg = gains[1] / 100.0;
    eq[side].hg = gains[2] / 100.0;
  }
  int16_t low_pass_carry[2] = { 0, 0 };

  int next_strobe = 0, next_psg = 0, time = 0;
  long frame_base = 0, samples_written = 0;
  static short frame_pcm[2 * (SAMPLE_RATE / 10)];
  for (int frame = 0; frame < ntsc_frames; frame++) {
    for (; time < NTSC_FRAME_MCYCLES; time += YM_CLOCK_MCYCLES) {
      for (; next_psg < psg_count && psg_writes[next_psg].when == frame_base + time; next_psg++)
        psg_write((unsigned) time, (unsigned) psg_writes[next_psg].value);
      for (; next_strobe < strobe_count && strobes[next_strobe].when == fm.clocks; next_strobe++)
        OPN2_Write(&fm.chip, (Bit32u) strobes[next_strobe].port, (Bit8u) strobes[next_strobe].value);
      if ((next_psg < psg_count && psg_writes[next_psg].when < frame_base + time)
          || (next_strobe < strobe_count && strobes[next_strobe].when < fm.clocks)) {
        fprintf(stderr, "write schedule out of order at clock %ld\n", fm.clocks);
        return 5;
      }
      fm_clock(&fm, blip, time);
    }
    time -= NTSC_FRAME_MCYCLES;
    psg_end_frame(NTSC_FRAME_MCYCLES);
    frame_base += NTSC_FRAME_MCYCLES;
    blip_end_frame(blip, NTSC_FRAME_MCYCLES);
    int available = blip_samples_avail(blip);
    blip_read_samples(blip, frame_pcm, available);
    if (filter & 1) low_pass(frame_pcm, available, lp_range, low_pass_carry);
    else if (filter & 2) three_band(frame_pcm, available, eq);
    fwrite(frame_pcm, sizeof(short), 2 * (size_t) available, pcm_out);
    samples_written += available;
  }
  fclose(pcm_out);
  union { double d; unsigned long long u; } lf = { eq[0].lf }, hf = { eq[0].hf };
  fprintf(info, "psg_writes=%d psg_applied=%d strobes=%d applied=%d clocks=%ld ntsc_frames=%d pcm_samples=%ld "
                "filter=%d lp_range=%u low=%d high=%d lg=%d mg=%d hg=%d eq_lf=%016llx eq_hf=%016llx\n",
          psg_count, next_psg, strobe_count, next_strobe, fm.clocks, ntsc_frames, samples_written, filter, lp_range,
          low_freq, high_freq, gains[0], gains[1], gains[2], lf.u, hf.u);
  fclose(info);
  return 0;
}
