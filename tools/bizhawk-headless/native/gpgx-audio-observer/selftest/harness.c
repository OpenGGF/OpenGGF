#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "shared.h"
#include "audio_trace.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];

static void watch(uint8_t *mask, unsigned pc) { mask[pc >> 3] |= 1u << (pc & 7); }

int main(void)
{
  struct gpgx_audio_trace_config_v1 config;
  struct gpgx_audio_service_kind_v1 kinds[3];
  struct gpgx_audio_service_hook_v1 hooks[4];
  struct gpgx_audio_snapshot_range_v1 ranges[3];
  struct gpgx_audio_trace_event events[32];
  uint8_t mask[8192], previous, nested_previous;
  uint32_t count, overflow, drained;
  memset(&config, 0, sizeof(config)); memset(kinds, 0, sizeof(kinds));
  memset(hooks, 0, sizeof(hooks)); memset(ranges, 0, sizeof(ranges)); memset(mask, 0, sizeof(mask));
  config.magic=0x31544147; config.abi_version=1; config.struct_size=64;
  config.hook_size=32; config.range_size=16; config.event_size=32;
  config.max_depth=8; config.max_opcode_bytes=8; config.reset_service_kind=3;
  config.watch_mask_bytes=8192; config.hook_count=4; config.range_count=3;
  config.snapshot_bytes_total=5; config.event_capacity=65536;
  config.max_service_tokens_per_frame=65535; config.kind_size=16; config.kind_count=3;
  kinds[0].kind_id=1; kinds[0].flags=4; kinds[0].cancellation_range_count=1;
  kinds[1].kind_id=2; kinds[1].cancellation_range_first=1; kinds[1].cancellation_range_count=1;
  kinds[2].kind_id=3; kinds[2].cancellation_range_first=2; kinds[2].cancellation_range_count=1;
  for (unsigned i=0;i<3;i++) { ranges[i].range_id=i+1; ranges[i].start=0x100+i; ranges[i].length=1; }
  for (unsigned i=0;i<4;i++) { hooks[i].hook_token=10+i; hooks[i].cpu=1; hooks[i].pc=i; hooks[i].opcode_length=1; hooks[i].opcode[0]=0xa0+i; watch(mask,i); zram[i]=0xa0+i; }
  hooks[0].action=1; hooks[0].service_kind=1;
  hooks[1].action=1; hooks[1].service_kind=2; hooks[1].expected_active_kind=1;
  hooks[2].action=2; hooks[2].expected_active_kind=2; hooks[2].range_first=1; hooks[2].range_count=1;
  hooks[3].action=2; hooks[3].expected_active_kind=1; hooks[3].range_count=1;
  assert(gpgx_audio_trace_configure(&config,mask,kinds,hooks,ranges)==0);
  memset(hooks,0,sizeof(hooks)); memset(kinds,0,sizeof(kinds)); memset(ranges,0,sizeof(ranges)); memset(mask,0,sizeof(mask));
  assert(gpgx_audio_trace_begin_frame()==0);
  previous=gpgx_audio_trace_enter_cpu(1); gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_leave_cpu(previous);
  previous=gpgx_audio_trace_enter_cpu(2); gpgx_audio_trace_instruction(2,0x1234);
  nested_previous=gpgx_audio_trace_enter_cpu(1); gpgx_audio_trace_instruction(1,4);
  gpgx_audio_trace_leave_cpu(nested_previous); gpgx_audio_trace_fm_write(2,0x55); gpgx_audio_trace_leave_cpu(previous);
  gpgx_audio_trace_enter_cpu(1); gpgx_audio_trace_instruction(1,1); gpgx_audio_trace_psg_write(0xaa);
  zram[0x100]=0x11; zram[0x101]=0x22;
  gpgx_audio_trace_instruction(1,2); gpgx_audio_trace_instruction(1,3);
  assert(gpgx_audio_trace_end_frame()==0);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==0 && count==12 && overflow==0);
  assert(gpgx_audio_trace_drain(events,32,&drained)==0 && drained==12);
  assert(events[0].kind==1 && events[0].service_token==1 && events[0].parent_token==0);
  assert(events[1].kind==3 && events[1].source_cpu==2 && events[1].pc==0x1234 && events[1].value==0x55);
  assert(events[2].kind==1 && events[2].service_token==2 && events[2].parent_token==1 && events[2].depth==1);
  assert(events[3].kind==4 && events[3].service_token==2 && events[3].pc==1 && events[3].value==0xaa);
  assert(events[5].payload[0]==0x22 && events[7].service_token==2);
  assert(events[9].payload[0]==0x11 && events[11].service_token==1);
  for (unsigned i=0;i<count;i++) assert(events[i].ordinal==i);
  assert(gpgx_audio_trace_begin_frame()==0 && gpgx_audio_trace_end_frame()==0);
  gpgx_audio_trace_reset_begin(0);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==-2);
  assert(gpgx_audio_trace_drain(events,32,&drained)==-2);
  assert(gpgx_audio_trace_abort_frame()==0);
  puts("native-observer-selftest: 12 ordered nested events; scoped CPU PCs; READY reset fail-closed");
  return 0;
}
