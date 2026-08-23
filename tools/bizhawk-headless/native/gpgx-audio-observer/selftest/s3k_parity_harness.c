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

static void configure_observer(void)
{
  struct gpgx_audio_trace_config_v1 config;
  struct gpgx_audio_service_kind_v1 kinds[3];
  struct gpgx_audio_service_hook_v1 hooks[4];
  struct gpgx_audio_snapshot_range_v1 ranges[3];
  uint8_t mask[8192];
  memset(&config, 0, sizeof(config));
  memset(kinds, 0, sizeof(kinds));
  memset(hooks, 0, sizeof(hooks));
  memset(ranges, 0, sizeof(ranges));
  memset(mask, 0, sizeof(mask));
  config.magic = 0x31544147u;
  config.abi_version = 1;
  config.struct_size = sizeof(config);
  config.hook_size = sizeof(hooks[0]);
  config.range_size = sizeof(ranges[0]);
  config.event_size = sizeof(struct gpgx_audio_trace_event);
  config.max_depth = 8;
  config.max_opcode_bytes = 8;
  config.reset_service_kind = 3;
  config.watch_mask_bytes = sizeof(mask);
  config.hook_count = 4;
  config.range_count = 3;
  config.snapshot_bytes_total = 5;
  config.event_capacity = GPGX_AUDIO_TRACE_EVENT_CAPACITY;
  config.max_service_tokens_per_frame = 65535;
  config.kind_size = sizeof(kinds[0]);
  config.kind_count = 3;
  kinds[0].kind_id = 1; kinds[0].flags = 4; kinds[0].cancellation_range_count = 1;
  kinds[1].kind_id = 2; kinds[1].cancellation_range_first = 1; kinds[1].cancellation_range_count = 1;
  kinds[2].kind_id = 3; kinds[2].cancellation_range_first = 2; kinds[2].cancellation_range_count = 1;
  for (unsigned i = 0; i < 3; i++) {
    ranges[i].range_id = i + 1; ranges[i].start = 0x300 + i; ranges[i].length = 1;
  }
  for (unsigned i = 0; i < 4; i++) {
    hooks[i].hook_token = 10 + i; hooks[i].cpu = 1; hooks[i].pc = i;
    hooks[i].opcode_length = 1; hooks[i].opcode[0] = 0xA0 + i;
    mask[i >> 3] |= (uint8_t)(1u << (i & 7)); zram[i] = 0xA0 + i;
  }
  hooks[0].action = 1; hooks[0].service_kind = 1;
  hooks[1].action = 1; hooks[1].service_kind = 2; hooks[1].expected_active_kind = 1;
  hooks[2].action = 2; hooks[2].expected_active_kind = 2;
  hooks[2].range_first = 1; hooks[2].range_count = 1;
  hooks[3].action = 2; hooks[3].expected_active_kind = 1; hooks[3].range_count = 1;
  assert(gpgx_audio_trace_configure(&config, mask, kinds, hooks, ranges) == 0);
}

static void configure_parity(uint16_t end_pc, uint8_t expected_kind)
{
  struct gpgx_s3k_audio_parity_config_v1 config;
  struct gpgx_s3k_audio_parity_descriptor_v1 descriptor;
  memset(&config, 0, sizeof(config));
  memset(&descriptor, 0, sizeof(descriptor));
  config.magic = 0x31503353u;
  config.abi_version = GPGX_S3K_AUDIO_PARITY_ABI_VERSION;
  config.struct_size = sizeof(config);
  config.descriptor_size = sizeof(descriptor);
  config.event_size = sizeof(struct gpgx_s3k_audio_parity_event_v1);
  config.descriptor_count = 1;
  config.event_capacity = GPGX_S3K_AUDIO_PARITY_EVENT_CAPACITY;
  config.song_track_first = 0x100;
  config.song_track_end = 0x130;
  config.sfx_track_first = 0x200;
  config.sfx_track_end = 0x230;
  config.track_size = 0x30;
  config.song_bank_address = 0x20;
  config.fixed_sfx_bank = 0x0F;
  descriptor.descriptor_id = 7;
  descriptor.begin_pc = 0x10;
  descriptor.end_pc = end_pc;
  descriptor.begin_opcode = 0xCC;
  descriptor.end_opcode = 0xDD;
  descriptor.expected_service_kind = expected_kind;
  descriptor.expected_track_type = 1;
  assert(gpgx_s3k_audio_parity_configure(&config, &descriptor) == 0);
}

static void begin_owned_frame(void)
{
  assert(gpgx_audio_trace_begin_frame() == 0);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80, 0);
  assert(gpgx_s3k_audio_parity_begin_frame(12) == 0);
  zram[0x100] = 0x80;
  zram[0x101] = 4;
  zram[0x103] = 0x34;
  zram[0x104] = 0x12;
  zram[0x20] = 9;
  gpgx_s3k_audio_parity_instruction(0x10, 1000, 0x100, 0xCC, zram);
}

static void close_observer_frame(void)
{
  uint32_t count, overflow;
  struct gpgx_audio_trace_event events[8];
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80, 3);
  assert(gpgx_audio_trace_end_frame() == 0);
  assert(gpgx_audio_trace_event_count(&count, &overflow) == 0);
  assert(gpgx_audio_trace_drain(events, 8, &count) == 0);
}

int main(void)
{
  struct gpgx_s3k_audio_parity_event_v1 events[2];
  struct gpgx_s3k_audio_parity_fault_v1 fault;
  uint32_t count, overflow;
  configure_observer();
  configure_parity(0x20, 1);
  begin_owned_frame();
  gpgx_s3k_audio_parity_fm_write(1010, 2, 0xB4);
  gpgx_s3k_audio_parity_fm_write(1025, 3, 0xC0);
  gpgx_s3k_audio_parity_psg_write(1040, 0x9F);
  gpgx_s3k_audio_parity_instruction(0x20, 1055, 0x100, 0xDD, zram);
  assert(gpgx_s3k_audio_parity_end_frame() == 0);
  assert(gpgx_s3k_audio_parity_event_count(&count, &overflow) == 0
    && count == 2 && overflow == 0);
  assert(gpgx_s3k_audio_parity_drain(events, 2, &count) == 0 && count == 2);
  assert(events[0].event_ordinal == 0 && events[1].event_ordinal == 1);
  assert(events[0].transaction_id == events[1].transaction_id);
  assert(events[0].service_kind == 1 && events[0].service_ordinal == 1);
  assert(events[0].track_base == 0x100 && events[0].track_type == 1);
  assert(events[0].channel_id == 4 && events[0].bank == 9);
  assert(events[0].source_pointer == 0x1234 && events[0].generation == 1);
  assert(events[0].source_pc == 0x10 && events[0].master_cycle == 1025);
  assert(events[0].vint_ordinal == 12 && events[0].service_entry_master_cycle == 1000);
  assert(events[0].chip == GPGX_S3K_AUDIO_PARITY_CHIP_YM2612
    && events[0].port == 1 && events[0].register_id == 0xB4
    && events[0].value == 0xC0);
  assert(events[1].chip == GPGX_S3K_AUDIO_PARITY_CHIP_PSG
    && events[1].value == 0x9F);
  assert(gpgx_s3k_audio_parity_disable() == 0);
  close_observer_frame();

  configure_parity(0x20, 1);
  begin_owned_frame();
  gpgx_s3k_audio_parity_instruction(0x20, 1010, 0x130, 0xDD, zram);
  assert(gpgx_s3k_audio_parity_first_fault(&fault) == 0
    && fault.reason == GPGX_S3K_AUDIO_PARITY_FAULT_OWNER_MUTATION);
  assert(gpgx_s3k_audio_parity_end_frame() == -3);
  assert(gpgx_s3k_audio_parity_disable() == 0);
  close_observer_frame();

  configure_parity(0x20, 1);
  begin_owned_frame();
  for (uint32_t i = 0; i < GPGX_S3K_AUDIO_PARITY_EVENT_CAPACITY; i++) {
    gpgx_s3k_audio_parity_fm_write(1100 + i * 2u, 0, 0x28);
    gpgx_s3k_audio_parity_fm_write(1101 + i * 2u, 1, i);
  }
  gpgx_s3k_audio_parity_instruction(0x20, 600000, 0x100, 0xDD, zram);
  assert(gpgx_s3k_audio_parity_end_frame() == 0);
  assert(gpgx_s3k_audio_parity_event_count(&count, &overflow) == 0
    && count == GPGX_S3K_AUDIO_PARITY_EVENT_CAPACITY && overflow == 0);
  assert(gpgx_s3k_audio_parity_disable() == 0);
  close_observer_frame();

  configure_parity(0x20, 1);
  begin_owned_frame();
  for (uint32_t i = 0; i <= GPGX_S3K_AUDIO_PARITY_EVENT_CAPACITY; i++) {
    gpgx_s3k_audio_parity_fm_write(1100 + i * 2u, 0, 0x28);
    gpgx_s3k_audio_parity_fm_write(1101 + i * 2u, 1, i);
  }
  assert(gpgx_s3k_audio_parity_end_frame() == -5);
  assert(gpgx_s3k_audio_parity_first_fault(&fault) == 0
    && fault.reason == GPGX_S3K_AUDIO_PARITY_FAULT_CAPACITY);
  assert(gpgx_s3k_audio_parity_disable() == 0);
  close_observer_frame();

  configure_parity(0x20, 1);
  begin_owned_frame();
  gpgx_s3k_audio_parity_instruction(0x11, 1010, 0x130, 0, zram);
  assert(gpgx_s3k_audio_parity_first_fault(&fault) == 0
    && fault.reason == GPGX_S3K_AUDIO_PARITY_FAULT_OWNER_MUTATION);
  assert(gpgx_s3k_audio_parity_end_frame() == -3);
  assert(gpgx_s3k_audio_parity_disable() == 0);
  close_observer_frame();

  configure_parity(0x20, 1);
  begin_owned_frame();
  assert(gpgx_s3k_audio_parity_end_frame() == -3);
  assert(gpgx_s3k_audio_parity_first_fault(&fault) == 0
    && fault.reason == GPGX_S3K_AUDIO_PARITY_FAULT_INTERRUPTED);
  assert(gpgx_s3k_audio_parity_disable() == 0);
  close_observer_frame();

  assert(gpgx_s3k_audio_parity_abi_version() == 1);
  assert(gpgx_s3k_audio_parity_event_size() == 38);
  assert(gpgx_s3k_audio_parity_capacity() == 32768);
  assert(gpgx_audio_trace_disable() == 0);
  puts("s3k-parity-selftest: typed owner, YM pair, PSG, owner mutation, interruption, capacity N/N+1");
  return 0;
}
