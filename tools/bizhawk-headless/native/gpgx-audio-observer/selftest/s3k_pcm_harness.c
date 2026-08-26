#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "shared.h"
#include "audio_trace.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];
uint8_t work_ram[0x10000];
uint32_t selftest_m68k_a7;

static void configure(void)
{
  struct gpgx_s3k_pcm_config_v1 config;
  memset(&config, 0, sizeof(config));
  config.magic = 0x314d3353u;
  config.abi_version = GPGX_S3K_PCM_ABI_VERSION;
  config.struct_size = sizeof(config);
  config.event_size = sizeof(struct gpgx_s3k_pcm_event_v1);
  config.event_capacity = GPGX_S3K_PCM_EVENT_CAPACITY;
  assert(gpgx_s3k_pcm_configure(&config) == 0);
}

int main(void)
{
  struct gpgx_s3k_pcm_event_v1 events[3];
  uint32_t count, overflow;
  configure();
  assert(gpgx_s3k_pcm_begin_frame() == 0);
  gpgx_s3k_pcm_ym_sample(10, -11, 1, 12);
  gpgx_s3k_pcm_psg_sample(13, -14);
  assert(gpgx_s3k_pcm_end_frame() == 0);
  assert(gpgx_s3k_pcm_event_count(&count, &overflow) == 0
    && count == 3 && overflow == 0);
  assert(gpgx_s3k_pcm_drain(events, 3, &count) == 0 && count == 3);
  assert(events[0].event_ordinal == 0 && events[0].sample_ordinal == 0
    && events[0].master_cycle == 0 && events[0].left == 10
    && events[0].right == -11
    && events[0].tap == GPGX_S3K_PCM_TAP_YM2612_MIX_STEREO);
  assert(events[1].event_ordinal == 1 && events[1].sample_ordinal == 0
    && events[1].master_cycle == 0 && events[1].left == 12
    && events[1].right == 12
    && events[1].tap == GPGX_S3K_PCM_TAP_DAC_LATCH_MONO);
  assert(events[2].event_ordinal == 2 && events[2].sample_ordinal == 0
    && events[2].master_cycle == 0 && events[2].left == 13
    && events[2].right == -14
    && events[2].tap == GPGX_S3K_PCM_TAP_PSG_STEREO_NATIVE);
  assert(gpgx_s3k_pcm_disable() == 0);

  configure();
  assert(gpgx_s3k_pcm_begin_frame() == 0);
  for (uint32_t i = 0; i < GPGX_S3K_PCM_EVENT_CAPACITY; i++)
    gpgx_s3k_pcm_psg_sample((int32_t)i, -(int32_t)i);
  assert(gpgx_s3k_pcm_end_frame() == 0);
  assert(gpgx_s3k_pcm_event_count(&count, &overflow) == 0
    && count == GPGX_S3K_PCM_EVENT_CAPACITY && overflow == 0);
  assert(gpgx_s3k_pcm_disable() == 0);

  configure();
  assert(gpgx_s3k_pcm_begin_frame() == 0);
  for (uint32_t i = 0; i <= GPGX_S3K_PCM_EVENT_CAPACITY; i++)
    gpgx_s3k_pcm_psg_sample((int32_t)i, -(int32_t)i);
  assert(gpgx_s3k_pcm_end_frame() == -5);
  assert(gpgx_s3k_pcm_event_count(&count, &overflow) == -5
    && count == GPGX_S3K_PCM_EVENT_CAPACITY && overflow == 1);
  assert(gpgx_s3k_pcm_disable() == 0);

  assert(gpgx_s3k_pcm_abi_version() == 1);
  assert(gpgx_s3k_pcm_event_size() == 28);
  assert(gpgx_s3k_pcm_capacity() == 16384);
  puts("s3k-pcm-selftest: YM mix, DAC latch, PSG stereo, capacity N/N+1");
  return 0;
}
