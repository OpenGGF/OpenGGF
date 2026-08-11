#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "m68kconf.h"
#include "m68k.h"
#include "audio_trace.h"

uint8_t zram[0x2000];
uint8_t work_ram[0x10000];
static uint8_t page0[0x10000];
static unsigned int writes;
static unsigned int invoke_irq_delay;
int vdp_68k_irq_ack(int int_level) { return int_level; }

static void write_fm(unsigned int address, unsigned int data)
{
  (void)address;
  writes++;
  gpgx_audio_trace_fm_write(1, data);
  if (invoke_irq_delay) { invoke_irq_delay=0; m68k_set_irq_delay(0); }
}

static void put_byte(unsigned int address, uint8_t value) { page0[address ^ 1u] = value; }

int main(void)
{
  struct gpgx_audio_trace_config_v1 config;
  struct gpgx_audio_service_kind_v1 kind;
  struct gpgx_audio_service_hook_v1 hooks[2];
  struct gpgx_audio_snapshot_range_v1 range;
  struct gpgx_audio_trace_event events[8];
  uint8_t mask[8192];
  const uint8_t setup[4]={0x10,0x3c,0x00,0x7f};
  const uint8_t instruction[4]={0x11,0xc0,0x40,0x00};
  uint32_t count;
  unsigned int i;
  memset(&config,0,sizeof(config)); memset(&kind,0,sizeof(kind)); memset(hooks,0,sizeof(hooks));
  memset(&range,0,sizeof(range)); memset(mask,0,sizeof(mask)); memset(page0,0,sizeof(page0));
  config.magic=0x31544147; config.abi_version=1; config.struct_size=64; config.kind_size=16;
  config.hook_size=32; config.range_size=16; config.event_size=32; config.max_depth=8;
  config.max_opcode_bytes=8; config.reset_service_kind=1; config.watch_mask_bytes=8192;
  config.kind_count=1; config.hook_count=2; config.range_count=1;
  config.snapshot_bytes_total=2; config.event_capacity=65536; config.max_service_tokens_per_frame=65535;
  kind.kind_id=1; kind.flags=4; kind.cancellation_range_count=1;
  range.range_id=1; range.start=0; range.length=1;
  hooks[0].hook_token=1; hooks[0].action=1; hooks[0].cpu=2; hooks[0].pc=4; hooks[0].service_kind=1;
  hooks[0].opcode_length=4; memcpy(hooks[0].opcode,instruction,4);
  hooks[1].hook_token=2; hooks[1].action=2; hooks[1].cpu=2; hooks[1].pc=8;
  hooks[1].expected_active_kind=1; hooks[1].range_count=1; hooks[1].opcode_length=2;
  hooks[1].opcode[0]=0x4e; hooks[1].opcode[1]=0x71;
  for(i=0;i<4;i++) put_byte(i,setup[i]);
  for(i=0;i<4;i++) put_byte(i+4,instruction[i]); put_byte(8,0x4e); put_byte(9,0x71);
  m68k.memory_map[0].base=page0; m68k.memory_map[0].write8=write_fm;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,&range)==0);
  m68k_init(); m68k_set_reg(M68K_REG_PC,0);
  m68k.pref_addr=~0u; m68k.cycles=0; m68k.refresh_cycles=1000000;
  invoke_irq_delay=1; assert(gpgx_audio_trace_begin_frame()==0); m68k_run(200);
  assert(gpgx_audio_trace_end_frame()==0);
  assert(invoke_irq_delay==0);
  assert(writes==1); assert(gpgx_audio_trace_drain(events,8,&count)==0 && count==6);
  assert(events[0].kind==1 && events[0].pc==4 && events[0].source_cpu==2);
  assert(events[1].kind==3 && events[1].pc==4 && events[1].source_cpu==2);
  assert(events[5].kind==2 && events[5].pc==8);
  return 0;
}
