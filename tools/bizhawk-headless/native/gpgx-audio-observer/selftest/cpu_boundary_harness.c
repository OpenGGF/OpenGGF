#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "shared.h"
#include "z80.h"
#include "audio_trace.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];
uint8_t work_ram[0x10000];
uint32_t selftest_m68k_a7;
static uint8_t memory[0x10000];

static void write_memory(unsigned int address, unsigned char data)
{
  memory[address & 0xffffu] = data;
  gpgx_audio_trace_fm_write(1, data);
}

static unsigned char read_memory(unsigned int address) { return memory[address & 0xffffu]; }
static void write_port(unsigned int port, unsigned char data) { (void)port; (void)data; }
static unsigned char read_port(unsigned int port) { (void)port; return 0; }
static int irq_vector(int line) { (void)line; return 0xc30038; }

int main(void)
{
  struct gpgx_audio_trace_config_v1 config;
  struct gpgx_audio_service_kind_v1 kind;
  struct gpgx_audio_service_hook_v1 hooks[2];
  struct gpgx_audio_snapshot_range_v1 range;
  struct gpgx_audio_trace_event events[8];
  uint8_t mask[8192];
  uint32_t count;
  unsigned int i;

  memset(&config, 0, sizeof(config)); memset(&kind, 0, sizeof(kind));
  memset(hooks, 0, sizeof(hooks)); memset(&range, 0, sizeof(range));
  memset(mask, 0, sizeof(mask)); memset(memory, 0, sizeof(memory)); memset(zram, 0, sizeof(zram));
  config.magic=0x31544147; config.abi_version=1; config.struct_size=64; config.kind_size=16;
  config.hook_size=32; config.range_size=16; config.event_size=32; config.max_depth=8;
  config.max_opcode_bytes=8; config.reset_service_kind=1; config.watch_mask_bytes=8192;
  config.kind_count=1; config.hook_count=2; config.range_count=1;
  config.snapshot_bytes_total=2; config.event_capacity=65536; config.max_service_tokens_per_frame=65535;
  kind.kind_id=1; kind.flags=4; kind.cancellation_range_count=1;
  range.range_id=1; range.start=0x100; range.length=1;
  hooks[0].hook_token=1; hooks[0].action=1; hooks[0].cpu=1; hooks[0].service_kind=1;
  hooks[0].opcode_length=3; hooks[0].opcode[0]=0x32; hooks[0].opcode[1]=0x00; hooks[0].opcode[2]=0x40;
  hooks[1].hook_token=2; hooks[1].action=2; hooks[1].cpu=1; hooks[1].pc=3;
  hooks[1].expected_active_kind=1; hooks[1].range_count=1; hooks[1].opcode_length=1;
  mask[0]=9; zram[0]=memory[0]=0x32; zram[1]=memory[1]=0x00; zram[2]=memory[2]=0x40;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,&range)==0);

  z80_init(NULL, NULL); z80_reset();
  for (i=0;i<64;i++) z80_readmap[i]=memory+(i*0x400);
  for (i=0;i<64;i++) z80_writemap[i]=NULL;
  z80_writemem=write_memory; z80_readmem=read_memory;
  z80_writeport=write_port; z80_readport=read_port;
  assert(gpgx_audio_trace_begin_frame()==0);
  z80_run(255);
  assert(gpgx_audio_trace_end_frame()==0);
  assert(gpgx_audio_trace_drain(events,8,&count)==0 && count==6);
  assert(events[0].kind==1 && events[0].pc==0 && events[0].source_cpu==1);
  assert(events[1].kind==3 && events[1].pc==0 && events[1].source_cpu==1);
  assert(events[2].kind==5 && events[5].kind==2 && events[5].pc==3);

  assert(gpgx_audio_trace_disable()==0); memset(mask,0,sizeof(mask)); memset(hooks,0,sizeof(hooks));
  config.snapshot_bytes_total=2; hooks[0].hook_token=1; hooks[0].action=1; hooks[0].cpu=1;
  hooks[0].pc=0x38; hooks[0].service_kind=1; hooks[0].opcode_length=3;
  hooks[0].opcode[0]=0x32; hooks[0].opcode[1]=0x00; hooks[0].opcode[2]=0x40;
  hooks[1].hook_token=2; hooks[1].action=2; hooks[1].cpu=1; hooks[1].pc=0x3b;
  hooks[1].expected_active_kind=1; hooks[1].range_count=1; hooks[1].opcode_length=1;
  mask[0x38>>3]=(uint8_t)((1u<<(0x38&7))|(1u<<(0x3b&7)));
  zram[0x38]=memory[0x38]=0x32; zram[0x39]=memory[0x39]=0;
  zram[0x3a]=memory[0x3a]=0x40; zram[0x3b]=memory[0x3b]=0;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,&range)==0);
  z80_init(NULL,irq_vector); z80_reset(); Z80.iff1=1; z80_set_irq_line(ASSERT_LINE);
  assert(gpgx_audio_trace_begin_frame()==0); z80_run(450); assert(gpgx_audio_trace_end_frame()==0);
  assert(gpgx_audio_trace_drain(events,8,&count)==0 && count==6);
  assert(events[0].kind==1 && events[0].pc==0x38 && events[1].kind==3 && events[1].pc==0x38);
  return 0;
}
