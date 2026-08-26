#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "shared.h"
#include "audio_trace.h"

#define ACTION_PUSH_BEGIN 1u
#define KIND_ALLOW_CHILDREN 0x04u

struct selftest_m68k m68k;
uint8_t zram[0x2000];
uint8_t work_ram[0x10000];
uint32_t selftest_m68k_a7;

static struct gpgx_audio_trace_config_v1 config;
static struct gpgx_audio_service_kind_v1 kind;
static struct gpgx_audio_service_hook_v1 hook;
static struct gpgx_audio_snapshot_range_v1 range;
static uint8_t mask[8192];

static void configure(void)
{
  gpgx_audio_trace_disable();
  memset(&config, 0, sizeof(config));
  memset(&kind, 0, sizeof(kind));
  memset(&hook, 0, sizeof(hook));
  memset(&range, 0, sizeof(range));
  memset(mask, 0, sizeof(mask));
  config.magic = 0x31544147;
  config.abi_version = 1;
  config.struct_size = sizeof(config);
  config.kind_size = sizeof(kind);
  config.hook_size = sizeof(hook);
  config.range_size = sizeof(range);
  config.event_size = sizeof(struct gpgx_audio_trace_event);
  config.max_depth = 8;
  config.max_opcode_bytes = 8;
  config.reset_service_kind = 1;
  config.watch_mask_bytes = sizeof(mask);
  config.kind_count = 1;
  config.hook_count = 1;
  config.range_count = 1;
  config.snapshot_bytes_total = 1;
  config.event_capacity = GPGX_AUDIO_TRACE_EVENT_CAPACITY;
  config.max_service_tokens_per_frame = 65535;
  kind.kind_id = 1;
  kind.flags = KIND_ALLOW_CHILDREN;
  kind.cancellation_range_count = 1;
  hook.hook_token = 1;
  hook.action = ACTION_PUSH_BEGIN;
  hook.cpu = GPGX_AUDIO_TRACE_CPU_Z80;
  hook.service_kind = 1;
  hook.opcode_length = 1;
  hook.opcode[0] = 0xa0;
  range.range_id = 1;
  range.start = 0x100;
  range.length = 1;
  mask[0] = 1;
  zram[0] = 0xa0;
  assert(gpgx_audio_trace_configure(
    &config, mask, &kind, &hook, &range) == 0);
}

static void assert_unowned_fm_fault(void)
{
  struct gpgx_audio_trace_first_fault_v1 fault;
  configure();
  assert(gpgx_audio_trace_begin_frame() == 0);
  gpgx_audio_trace_fm_write(0, 0x2a);
  assert(gpgx_audio_trace_first_fault(&fault) == 0);
  assert(fault.reason == GPGX_AUDIO_TRACE_FAULT_CHIP_OWNERSHIP);
  assert(gpgx_audio_trace_end_frame() == -3);
  assert(gpgx_audio_trace_disable() == 0);
}

static void assert_unowned_psg_fault(void)
{
  struct gpgx_audio_trace_first_fault_v1 fault;
  configure();
  assert(gpgx_audio_trace_begin_frame() == 0);
  gpgx_audio_trace_psg_write(0x9f);
  assert(gpgx_audio_trace_first_fault(&fault) == 0);
  assert(fault.reason == GPGX_AUDIO_TRACE_FAULT_CHIP_OWNERSHIP);
  assert(gpgx_audio_trace_end_frame() == -3);
  assert(gpgx_audio_trace_disable() == 0);
}

static void assert_wrong_s2_owner_poison(void)
{
  uint32_t fault = 0;
  assert(gpgx_ym_timing_lab_configure_z80_admission(
    0x0975u, 0x0e03u, 0xb5u, 4u, 0x1d90u) == 0);
  assert(gpgx_ym_timing_lab_begin_frame() == 0);
  gpgx_ym_timing_lab_z80_instruction(
    0x0975u, 0u, 0xb5u, 0u, 0u);
  gpgx_ym_timing_lab_z80_instruction(
    0x0e03u, 1u, 0xb5u, 0x1d91u, 0u);
  assert(gpgx_ym_timing_lab_first_fault(&fault) == 0);
  assert(fault == 0x7006u);
  assert(gpgx_ym_timing_lab_abort_frame() == 0);
}

int main(void)
{
  assert_unowned_fm_fault();
  assert_unowned_psg_fault();
  assert_wrong_s2_owner_poison();
  puts("ym-timing-lab-selftest: observer ownership and S2 IX poison pass");
  return 0;
}
