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
static uint8_t defer_on_write;

static void write_memory(unsigned int address, unsigned char data)
{
  memory[address & 0xffffu] = data;
  if (defer_on_write) gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K, 0x71b4c);
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
  struct gpgx_audio_trace_event events[16];
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

  {
    struct gpgx_audio_service_kind_v1 kinds[6];
    struct gpgx_audio_service_hook_v1 deferred_hooks[9];
    uint8_t rom[65536];
    memset(kinds,0,sizeof(kinds)); memset(deferred_hooks,0,sizeof(deferred_hooks));
    memset(mask,0,sizeof(mask)); memset(memory,0,sizeof(memory)); memset(zram,0,sizeof(zram));
    memset(rom,0,sizeof(rom)); memset(&m68k,0,sizeof(m68k));
    assert(gpgx_audio_trace_disable()==0);
    config.abi_version=3; config.kind_count=6; config.hook_count=9;
    config.snapshot_bytes_total=8; config.max_continuation_frames=2;
    for (i=0;i<6;i++)
    {
      kinds[i].kind_id=(uint8_t)(i+1); kinds[i].cancellation_range_count=1;
    }
    kinds[1].flags=7; kinds[1].continuation_frame_limit=2;
    kinds[3].flags=4;
    kinds[5].flags=3; kinds[5].continuation_frame_limit=2;
    deferred_hooks[0].hook_token=1; deferred_hooks[0].action=1;
    deferred_hooks[0].cpu=1; deferred_hooks[0].service_kind=6;
    deferred_hooks[0].opcode_length=3; deferred_hooks[0].opcode[0]=0x32;
    deferred_hooks[0].opcode[1]=0x00; deferred_hooks[0].opcode[2]=0x40;
    deferred_hooks[1].hook_token=2; deferred_hooks[1].action=4;
    deferred_hooks[1].cpu=1; deferred_hooks[1].pc=0x77;
    deferred_hooks[1].service_kind=2;
    deferred_hooks[1].expected_active_kind=6; deferred_hooks[1].range_count=1;
    deferred_hooks[1].opcode_length=1; deferred_hooks[1].opcode[0]=0x1a;
    deferred_hooks[2].hook_token=3; deferred_hooks[2].action=11;
    deferred_hooks[2].cpu=2; deferred_hooks[2].pc=0x71b4c;
    deferred_hooks[2].service_kind=4; deferred_hooks[2].expected_active_kind=6;
    deferred_hooks[2].opcode_length=8; deferred_hooks[2].opcode[0]=0x33;
    deferred_hooks[2].opcode[1]=0xfc; deferred_hooks[2].opcode[2]=0x01;
    deferred_hooks[2].opcode[3]=0x00; deferred_hooks[2].opcode[4]=0x00;
    deferred_hooks[2].opcode[5]=0xa1; deferred_hooks[2].opcode[6]=0x11;
    deferred_hooks[2].opcode[7]=0x00;
    deferred_hooks[3].hook_token=4; deferred_hooks[3].action=7;
    deferred_hooks[3].cpu=2; deferred_hooks[3].pc=0x71b82;
    deferred_hooks[3].expected_active_kind=2;
    deferred_hooks[3].opcode_length=6; deferred_hooks[3].opcode[0]=0x4d;
    deferred_hooks[3].opcode[1]=0xf9; deferred_hooks[3].opcode[2]=0x00;
    deferred_hooks[3].opcode[3]=0xff; deferred_hooks[3].opcode[4]=0xf0;
    deferred_hooks[3].opcode[5]=0x00;
    deferred_hooks[4]=deferred_hooks[3]; deferred_hooks[4].hook_token=5;
    deferred_hooks[4].action=12; deferred_hooks[4].service_kind=4;
    deferred_hooks[5]=deferred_hooks[4]; deferred_hooks[5].hook_token=6;
    deferred_hooks[5].expected_active_kind=6;
    deferred_hooks[6].hook_token=7; deferred_hooks[6].action=2;
    deferred_hooks[6].cpu=2; deferred_hooks[6].pc=0x71c4c;
    deferred_hooks[6].expected_active_kind=4; deferred_hooks[6].range_count=1;
    deferred_hooks[6].opcode_length=2; deferred_hooks[6].opcode[0]=0x4e;
    deferred_hooks[6].opcode[1]=0x75;
    memmove(&deferred_hooks[3],&deferred_hooks[2],5*sizeof(deferred_hooks[0]));
    memset(&deferred_hooks[2],0,sizeof(deferred_hooks[2]));
    deferred_hooks[2].hook_token=8; deferred_hooks[2].action=1;
    deferred_hooks[2].cpu=1; deferred_hooks[2].pc=0x1000;
    deferred_hooks[2].service_kind=6; deferred_hooks[2].expected_active_kind=4;
    deferred_hooks[2].opcode_length=1; deferred_hooks[2].opcode[0]=0xa2;
    deferred_hooks[8]=deferred_hooks[7];
    deferred_hooks[7]=deferred_hooks[6]; deferred_hooks[7].hook_token=9;
    deferred_hooks[7].action=7; deferred_hooks[7].service_kind=0;
    mask[0]=(uint8_t)(1u<<0); mask[0x77>>3]|=(uint8_t)(1u<<(0x77&7));
    mask[0x1000>>3]|=(uint8_t)(1u<<(0x1000&7));
    zram[0]=memory[0]=0x32; zram[1]=memory[1]=0;
    zram[2]=memory[2]=0x40; zram[0x77]=memory[0x77]=0x1a;
    zram[0x1000]=memory[0x1000]=0xa2;
    memory[3]=0xc3; memory[4]=0x77; memory[5]=0x00;
    for (i=0;i<9;i++)
    {
      unsigned int j;
      if(deferred_hooks[i].cpu!=GPGX_AUDIO_TRACE_CPU_M68K)continue;
      for (j=0;j<deferred_hooks[i].opcode_length;j++)
        rom[((deferred_hooks[i].pc+j)&0xffffu)^1u]=deferred_hooks[i].opcode[j];
    }
    m68k.memory_map[7].base=rom;
    assert(gpgx_audio_trace_configure(&config,mask,kinds,deferred_hooks,&range)==0);
    z80_init(NULL,NULL); z80_reset();
    for (i=0;i<64;i++) z80_readmap[i]=memory+(i*0x400);
    for (i=0;i<64;i++) z80_writemap[i]=NULL;
    z80_writemem=write_memory; z80_readmem=read_memory;
    z80_writeport=write_port; z80_readport=read_port;
    defer_on_write=1;
    assert(gpgx_audio_trace_begin_frame()==0); z80_run(450);
    defer_on_write=0;
    {
      uint8_t previous=gpgx_audio_trace_enter_cpu(GPGX_AUDIO_TRACE_CPU_M68K);
      gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x71b82);
      gpgx_audio_trace_fm_write(2,0x2b);
      gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x71c4c);
      gpgx_audio_trace_leave_cpu(previous);
    }
    assert(gpgx_audio_trace_end_frame()==0);
    assert(gpgx_audio_trace_drain(events,16,&count)==0 && count==14);
    assert(events[1].kind==10 && events[1].value==4
      && events[1].service_kind==6 && events[1].source_cpu==2);
    assert(events[2].kind==3 && events[2].service_kind==6 && events[2].source_cpu==1);
    assert(events[3].kind==5 && events[4].kind==6 && events[5].kind==7);
    assert(events[6].kind==2 && events[6].service_kind==6
      && events[6].service_token==events[0].service_token && events[6].subject==2);
    assert(events[7].kind==1 && events[7].service_kind==2
      && events[7].parent_token==0 && events[7].depth==0 && events[7].subject==2);
    assert(events[8].kind==1 && events[8].service_kind==4
      && events[8].parent_token==events[7].service_token && events[8].depth==1
      && events[8].subject==5 && events[8].pc==0x71b82 && events[8].source_cpu==2);
    assert(events[9].kind==3 && events[9].service_kind==4 && events[9].source_cpu==2);
    assert(events[13].kind==2 && events[13].service_kind==4
      && events[13].service_token==events[8].service_token);
    assert(events[1].service_token==events[0].service_token
      && events[1].service_kind==6 && events[1].parent_token==0 && events[1].depth==0);
  }
  return 0;
}
