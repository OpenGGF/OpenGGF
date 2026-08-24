#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define uint8 unsigned char
#define uint16 unsigned short
#define uint32 unsigned int
#define int8 signed char
#define int16 signed short
#define int32 signed int
typedef uint8_t UINT8;
typedef uint16_t UINT16;
typedef uint32_t UINT32;
typedef int8_t INT8;
typedef int16_t INT16;
typedef int32_t INT32;
#define INLINE static __inline__
#define LSB_FIRST 1
enum {
  YM2612_DISCRETE = 0,
  YM2612_INTEGRATED,
  YM2612_ENHANCED
};
#define save_param(param, size) do { \
  memcpy(&state[bufferptr], param, size); \
  bufferptr += (size); \
} while (0)
#define load_param(param, size) do { \
  memcpy(param, &state[bufferptr], size); \
  bufferptr += (size); \
} while (0)
#define _SHARED_H_

typedef struct {
  uint64_t master_cycle;
  int32_t left;
  int32_t right;
  int32_t dac_latch;
  uint32_t dac_enabled;
} CapturedSample;

static CapturedSample captured[64];
static size_t captured_count;
static uint64_t rendered_master_cycles;
volatile uint8_t gpgx_s3k_pcm_enabled = 1;

void gpgx_s3k_pcm_ym_sample(int32_t left, int32_t right,
  uint32_t dac_enabled, int32_t dac_latch)
{
  assert(captured_count < sizeof(captured) / sizeof(captured[0]));
  CapturedSample *sample = &captured[captured_count++];
  sample->master_cycle = rendered_master_cycles;
  sample->left = left;
  sample->right = right;
  sample->dac_latch = dac_latch;
  sample->dac_enabled = dac_enabled;
  rendered_master_cycles += 1008u;
}

void gpgx_s3k_pcm_psg_sample(int32_t left, int32_t right)
{
  (void)left;
  (void)right;
}

#include "ym2612.c"

typedef struct {
  uint64_t due_master_cycle;
  uint8_t port;
  uint8_t reg;
  uint8_t value;
} ScheduledWrite;

static const ScheduledWrite schedule[] = {
  {12096u, 1u, 0xb5u, 0xc0u},
  {15120u, 1u, 0xa5u, 0x23u},
  {17136u, 1u, 0xa1u, 0x40u},
  {20160u, 0u, 0x2bu, 0x80u},
  {21168u, 0u, 0x2au, 0xa0u},
  {24192u, 0u, 0x2au, 0x60u}
};

static void write_register(uint8_t port, uint8_t reg, uint8_t value)
{
  YM2612Write(port ? 2u : 0u, reg);
  YM2612Write(port ? 3u : 1u, value);
}

static void render_until(uint64_t due)
{
  int buffer[2];
  assert(due >= rendered_master_cycles);
  assert(((due - rendered_master_cycles) % 1008u) == 0u);
  while (rendered_master_cycles < due)
    YM2612Update(buffer, 1);
}

static size_t replay(const ScheduledWrite *writes, size_t write_count,
  uint64_t end_master_cycle, CapturedSample *out)
{
  captured_count = 0;
  for (size_t i = 0; i < write_count; i++) {
    render_until(writes[i].due_master_cycle);
    write_register(writes[i].port, writes[i].reg, writes[i].value);
  }
  render_until(end_master_cycle);
  memcpy(out, captured, captured_count * sizeof(captured[0]));
  return captured_count;
}

int main(void)
{
  unsigned char state[65536];
  CapturedSample native[64], projected[64], poison[64];
  ScheduledWrite shifted[sizeof(schedule) / sizeof(schedule[0])];

  YM2612Init();
  YM2612Config(YM2612_ENHANCED);
  YM2612ResetChip();
  rendered_master_cycles = 0;
  render_until(10080u);
  int state_size = YM2612SaveContext(state);
  assert(state_size > 0 && state_size < (int)sizeof(state));

  size_t native_count = replay(schedule,
    sizeof(schedule) / sizeof(schedule[0]), 30240u, native);

  assert(YM2612LoadContext(state) == state_size);
  rendered_master_cycles = 10080u;
  size_t projected_count = replay(schedule,
    sizeof(schedule) / sizeof(schedule[0]), 30240u, projected);
  assert(projected_count == native_count);
  assert(memcmp(native, projected,
    native_count * sizeof(native[0])) == 0);

  memcpy(shifted, schedule, sizeof(schedule));
  shifted[4].due_master_cycle += 1008u;
  assert(YM2612LoadContext(state) == state_size);
  rendered_master_cycles = 10080u;
  size_t poison_count = replay(shifted,
    sizeof(shifted) / sizeof(shifted[0]), 30240u, poison);
  assert(poison_count == native_count);
  assert(memcmp(native, poison,
    native_count * sizeof(native[0])) != 0);

  assert(native_count == 20u);
  assert(native[11].master_cycle == 21168u);
  assert(native[11].dac_enabled != 0u && native[11].dac_latch == 0x20);
  assert(native[14].master_cycle == 24192u);
  assert(native[14].dac_enabled != 0u && native[14].dac_latch == -0x20);
  puts("s3k-pcm-replay-selftest: actual YM core restore, absolute schedule, poison");
  return 0;
}
