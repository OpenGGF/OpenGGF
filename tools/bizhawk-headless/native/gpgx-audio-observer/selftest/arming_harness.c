#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "shared.h"
#include "audio_trace.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];
uint8_t work_ram[0x10000];
uint32_t selftest_m68k_a7;
static uint8_t rom_page[0x10000];

static void watch(uint8_t *mask, unsigned pc) { mask[pc >> 3] |= 1u << (pc & 7); }
static void rom_byte(unsigned pc, uint8_t value) { rom_page[(pc & 0xffff) ^ 1] = value; }

int main(void)
{
  struct gpgx_audio_trace_config_v1 config;
  struct gpgx_audio_service_kind_v1 kinds[3];
  struct gpgx_audio_service_hook_v1 hooks[3];
  struct gpgx_audio_snapshot_range_v1 ranges[3];
  struct gpgx_audio_trace_event events[1100];
  uint8_t mask[8192];
  uint32_t count, overflow, drained;
  memset(&config,0,sizeof(config)); memset(kinds,0,sizeof(kinds));
  memset(hooks,0,sizeof(hooks)); memset(ranges,0,sizeof(ranges)); memset(mask,0,sizeof(mask));
  m68k.memory_map[0].base=rom_page;
  config.magic=0x31544147; config.abi_version=1; config.struct_size=64;
  config.hook_size=32; config.range_size=16; config.event_size=32;
  config.max_depth=8; config.max_opcode_bytes=8; config.reset_service_kind=3;
  config.max_continuation_frames=1; config.watch_mask_bytes=8192;
  config.hook_count=3; config.range_count=3; config.snapshot_bytes_total=16386;
  config.event_capacity=65536; config.max_service_tokens_per_frame=65535;
  config.kind_size=16; config.kind_count=3;
  kinds[0].kind_id=1; kinds[0].cancellation_range_count=1;
  kinds[1].kind_id=2; kinds[1].flags=2; kinds[1].cancellation_range_first=1;
  kinds[1].cancellation_range_count=1; kinds[1].continuation_frame_limit=1;
  kinds[2].kind_id=3; kinds[2].cancellation_range_first=2; kinds[2].cancellation_range_count=1;
  ranges[0].range_id=1; ranges[0].length=0x2000;
  ranges[1].range_id=2; ranges[1].start=0x100; ranges[1].length=1;
  ranges[2].range_id=3; ranges[2].start=0x101; ranges[2].length=1;
  hooks[0].hook_token=1; hooks[0].action=1; hooks[0].cpu=1; hooks[0].pc=0x38;
  hooks[0].service_kind=2; hooks[0].opcode_length=1; hooks[0].opcode[0]=0xf5; watch(mask,0x38);
  hooks[1].hook_token=2; hooks[1].action=1; hooks[1].cpu=2; hooks[1].pc=0x100;
  hooks[1].service_kind=1; hooks[1].opcode_length=2; hooks[1].opcode[0]=0x4e; hooks[1].opcode[1]=0x71;
  hooks[2].hook_token=3; hooks[2].action=2; hooks[2].cpu=2; hooks[2].pc=0x102;
  hooks[2].expected_active_kind=1; hooks[2].flags=1; hooks[2].opcode_length=2;
  hooks[2].opcode[0]=0x4e; hooks[2].opcode[1]=0x75; hooks[2].range_count=1;
  rom_byte(0x100,0x4e); rom_byte(0x101,0x71); rom_byte(0x102,0x4e); rom_byte(0x103,0x75);
  assert(gpgx_audio_trace_configure(&config,mask,kinds,hooks,ranges)==0);
  assert(gpgx_audio_trace_begin_frame()==0);
  uint8_t previous = gpgx_audio_trace_enter_cpu(1);
  gpgx_audio_trace_instruction(1,0x38); gpgx_audio_trace_leave_cpu(previous);
  previous = gpgx_audio_trace_enter_cpu(2);
  gpgx_audio_trace_instruction(2,0x100); gpgx_audio_trace_fm_write(0,0x22);
  memset(zram,0x5a,sizeof(zram)); zram[0x38]=0xf5;
  gpgx_audio_trace_instruction(2,0x102); gpgx_audio_trace_leave_cpu(previous);
  previous = gpgx_audio_trace_enter_cpu(1);
  gpgx_audio_trace_instruction(1,0x38); gpgx_audio_trace_leave_cpu(previous);
  assert(gpgx_audio_trace_end_frame()==0);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==0 && count==1030 && !overflow);
  assert(gpgx_audio_trace_drain(events,1100,&drained)==0 && drained==count);
  assert(events[0].kind==1 && events[0].subject==2);
  assert(events[1].kind==3 && events[1].service_kind==1);
  assert(events[1028].kind==2 && events[1028].subject==3);
  assert(events[1029].kind==1 && events[1029].subject==1);

  /* An in-frame reset disarms proofs, retains reset records, and a later
     full upload completion rearms before the first subsequent Z80 service. */
  assert(gpgx_audio_trace_begin_frame()==0);
  gpgx_audio_trace_reset_begin(0); gpgx_audio_trace_reset_end(0);
  memset(zram,0,sizeof(zram));
  previous=gpgx_audio_trace_enter_cpu(1); gpgx_audio_trace_instruction(1,0x38);
  gpgx_audio_trace_leave_cpu(previous);
  previous=gpgx_audio_trace_enter_cpu(2); gpgx_audio_trace_instruction(2,0x100);
  memset(zram,0x5a,sizeof(zram)); zram[0x38]=0xf5;
  gpgx_audio_trace_instruction(2,0x102); gpgx_audio_trace_leave_cpu(previous);
  previous=gpgx_audio_trace_enter_cpu(1); gpgx_audio_trace_instruction(1,0x38);
  gpgx_audio_trace_leave_cpu(previous);
  assert(gpgx_audio_trace_end_frame()==0);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==0 && count==1038 && !overflow);
  assert(gpgx_audio_trace_drain(events,1100,&drained)==0 && drained==count);
  assert(events[0].kind==8 && events[4].kind==2 && events[8].kind==9);
  assert(events[9].kind==1 && events[1036].kind==2 && events[1037].kind==1);

  /* A late proof mismatch emits the upload boundary but never partially arms. */
  assert(gpgx_audio_trace_abort_frame()==-2);
  assert(gpgx_audio_trace_disable()==0);
  assert(gpgx_audio_trace_configure(&config,mask,kinds,hooks,ranges)==0);
  assert(gpgx_audio_trace_begin_frame()==0);
  previous=gpgx_audio_trace_enter_cpu(2); gpgx_audio_trace_instruction(2,0x100);
  memset(zram,0x5a,sizeof(zram)); zram[0x38]=0;
  gpgx_audio_trace_instruction(2,0x102); gpgx_audio_trace_leave_cpu(previous);
  assert(gpgx_audio_trace_end_frame()==-3);
  assert(gpgx_audio_trace_abort_frame()==0);
  assert(gpgx_audio_trace_disable()==0);

  /* Reset outside RECORDING is sticky for status departures only. */
  assert(gpgx_audio_trace_configure(&config,mask,kinds,hooks,ranges)==0);
  gpgx_audio_trace_reset_begin(1);
  assert(gpgx_audio_trace_begin_frame()==-3);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==-3);
  assert(gpgx_audio_trace_drain(events,1100,&drained)==-3);
  assert(gpgx_audio_trace_abort_frame()==-3);
  assert(gpgx_audio_trace_abi_version()==2 && gpgx_audio_trace_event_size()==32
    && gpgx_audio_trace_capacity()==65536);
  assert(gpgx_audio_trace_disable()==0);
  return 0;
}
