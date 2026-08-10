#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "shared.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];

/* Include the implementation to exercise its private token-wrap boundary without a production seam. */
#include "audio_trace.c"

static void watch(uint8_t *mask, unsigned pc) { mask[pc >> 3] |= 1u << (pc & 7); }

int main(void)
{
  struct gpgx_audio_trace_config_v1 config;
  struct gpgx_audio_service_kind_v1 kind;
  struct gpgx_audio_service_hook_v1 hooks[2];
  struct gpgx_audio_snapshot_range_v1 range;
  uint8_t mask[8192];
  uint32_t count, overflow;
  memset(&config, 0, sizeof(config)); memset(&kind, 0, sizeof(kind));
  memset(hooks, 0, sizeof(hooks)); memset(&range, 0, sizeof(range)); memset(mask, 0, sizeof(mask));
  config.magic=0x31544147; config.abi_version=1; config.struct_size=64; config.kind_size=16;
  config.hook_size=32; config.range_size=16; config.event_size=32; config.max_depth=8;
  config.max_opcode_bytes=8; config.reset_service_kind=1; config.watch_mask_bytes=8192;
  config.kind_count=1; config.hook_count=2; config.range_count=1; config.snapshot_bytes_total=2;
  config.event_capacity=65536; config.max_service_tokens_per_frame=65535;
  kind.kind_id=1; kind.cancellation_range_count=1;
  range.range_id=1; range.start=0x100; range.length=1;
  hooks[0].hook_token=1; hooks[0].action=1; hooks[0].service_kind=1;
  hooks[0].cpu=1; hooks[0].opcode_length=1; hooks[0].opcode[0]=0xa0;
  hooks[1].hook_token=2; hooks[1].action=4; hooks[1].service_kind=1;
  hooks[1].expected_active_kind=1; hooks[1].cpu=1; hooks[1].pc=1;
  hooks[1].range_count=1; hooks[1].opcode_length=1; hooks[1].opcode[0]=0xa1;
  watch(mask,0); watch(mask,1); zram[0]=0xa0; zram[1]=0xa1;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,&range)==0);

  assert(gpgx_audio_trace_begin_frame()==0);
  gpgx_audio_trace_instruction(1,0);
  assert(trace_depth==1 && trace_stack[0].token==1 && trace_event_count_value==1);
  trace_next_token=0;
  gpgx_audio_trace_instruction(1,1);
  assert(trace_runtime_error && trace_depth==1 && trace_stack[0].token==1);
  assert(trace_event_count_value==1);
  assert(gpgx_audio_trace_end_frame()==-3);
  assert(gpgx_audio_trace_abort_frame()==0);

  assert(gpgx_audio_trace_begin_frame()==0);
  gpgx_audio_trace_instruction(1,0);
  trace_next_token=0;
  gpgx_audio_trace_reset_begin(0);
  assert(trace_runtime_error && trace_depth==1 && trace_stack[0].token==1);
  assert(trace_event_count_value==1);
  gpgx_audio_trace_reset_end(0);
  assert(trace_depth==1 && trace_stack[0].token==1 && trace_event_count_value==1);
  assert(gpgx_audio_trace_end_frame()==-3);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==0);
  assert(count==1 && overflow==0);
  return 0;
}
