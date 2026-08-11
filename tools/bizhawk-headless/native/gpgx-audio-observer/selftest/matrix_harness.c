#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "shared.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];
uint8_t work_ram[0x10000];
uint32_t selftest_m68k_a7;
#include "audio_trace.c"

extern int32_t gpgx_audio_trace_first_fault(
  struct gpgx_audio_trace_first_fault_v1 *out);

static struct gpgx_audio_trace_config_v1 config;
static struct gpgx_audio_service_kind_v1 kind;
static struct gpgx_audio_service_hook_v1 hooks[2];
static struct gpgx_audio_snapshot_range_v1 range;
static uint8_t mask[8192];

static void fixture(int with_pop)
{
  gpgx_audio_trace_disable();
  memset(&config,0,sizeof(config)); memset(&kind,0,sizeof(kind));
  memset(hooks,0,sizeof(hooks)); memset(&range,0,sizeof(range)); memset(mask,0,sizeof(mask));
  config.magic=0x31544147; config.abi_version=1; config.struct_size=64; config.kind_size=16;
  config.hook_size=32; config.range_size=16; config.event_size=32; config.max_depth=8;
  config.max_opcode_bytes=8; config.reset_service_kind=1; config.watch_mask_bytes=8192;
  config.kind_count=1; config.hook_count=with_pop?2:1; config.range_count=1;
  config.snapshot_bytes_total=with_pop?2:1; config.event_capacity=65536;
  config.max_service_tokens_per_frame=65535;
  kind.kind_id=1; kind.flags=KIND_ALLOW_CHILDREN; kind.cancellation_range_count=1;
  range.range_id=1; range.start=0x100; range.length=1;
  hooks[0].hook_token=1; hooks[0].action=ACTION_PUSH_BEGIN; hooks[0].service_kind=1;
  hooks[0].cpu=GPGX_AUDIO_TRACE_CPU_Z80; hooks[0].opcode_length=1; hooks[0].opcode[0]=0xa0;
  hooks[1].hook_token=2; hooks[1].action=ACTION_POP_END_AT_PC; hooks[1].expected_active_kind=1;
  hooks[1].cpu=GPGX_AUDIO_TRACE_CPU_Z80; hooks[1].pc=1; hooks[1].range_count=1;
  hooks[1].opcode_length=1; hooks[1].opcode[0]=0xa1;
  mask[0]=with_pop?3:1; zram[0]=0xa0; zram[1]=0xa1;
}

static int configure(void)
{ return gpgx_audio_trace_configure(&config,mask,&kind,hooks,&range); }

static void config_negatives(void)
{
  struct gpgx_audio_service_kind_v1 kinds[2];
  struct gpgx_audio_snapshot_range_v1 ranges[2];
  fixture(0); assert(gpgx_audio_trace_configure(NULL,mask,&kind,hooks,&range)==TRACE_INVALID_ARGUMENT);
  fixture(0); assert(gpgx_audio_trace_configure(&config,NULL,&kind,hooks,&range)==TRACE_INVALID_ARGUMENT);
  fixture(0); assert(gpgx_audio_trace_configure(&config,mask,NULL,hooks,&range)==TRACE_INVALID_ARGUMENT);
  fixture(0); assert(gpgx_audio_trace_configure(&config,mask,&kind,NULL,&range)==TRACE_INVALID_ARGUMENT);
  fixture(0); assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,NULL)==TRACE_INVALID_ARGUMENT);
#define BAD(field,value) do { fixture(0); config.field=(value); assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT); } while(0)
  BAD(magic,0); BAD(abi_version,4); BAD(struct_size,63); BAD(kind_size,15); BAD(hook_size,31);
  BAD(range_size,15); BAD(event_size,31); BAD(max_depth,9); BAD(max_opcode_bytes,7);
  BAD(watch_mask_bytes,8191); BAD(watch_mask_bytes,8193); BAD(event_capacity,65535);
  BAD(max_service_tokens_per_frame,65534); BAD(kind_count,0); BAD(hook_count,0); BAD(range_count,0);
  BAD(kind_count,256); BAD(hook_count,513); BAD(range_count,129);
  BAD(snapshot_bytes_total,1048577); BAD(reset_service_kind,2); BAD(max_continuation_frames,1);
#undef BAD
  fixture(0); kind.kind_id=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); range.length=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); range.range_id=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); range.start=0x1fff; range.length=2; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].opcode[1]=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].pc=0x2000; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); mask[0]=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); config.flags=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); config.reserved[0]=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.flags=8; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.continuation_frame_limit=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.flags|=KIND_ALLOW_CONTINUATION; kind.continuation_frame_limit=0;
  assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.flags|=KIND_ALLOW_CONTINUATION; kind.continuation_frame_limit=5;
  config.max_continuation_frames=4; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.flags|=KIND_ALLOW_CONTINUATION; kind.continuation_frame_limit=5;
  config.max_continuation_frames=5; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); config.abi_version=2; kind.flags|=KIND_ALLOW_CONTINUATION;
  kind.continuation_frame_limit=5; config.max_continuation_frames=5;
  assert(configure()==TRACE_OK);
  fixture(0); kind.cancellation_range_count=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.cancellation_range_first=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.reserved0=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].hook_token=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].action=5; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].cpu=3; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].flags=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].reserved[0]=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kind.flags=0; hooks[0].expected_active_kind=1;
  assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); range.flags=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); range.reserved[0]=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(1); hooks[1].hook_token=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(1); hooks[1].range_count=0; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(1); hooks[1].range_first=1; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); config.snapshot_bytes_total=2; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].cpu=2; hooks[0].pc=0xffffff; hooks[0].opcode_length=2; mask[0]=0;
  assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kinds[0]=kind; kinds[1]=kind; config.kind_count=2; config.snapshot_bytes_total=2;
  assert(gpgx_audio_trace_configure(&config,mask,kinds,hooks,&range)==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); kinds[0]=kind; kinds[1]=kind; kinds[0].kind_id=2; kinds[1].kind_id=1;
  config.kind_count=2; config.snapshot_bytes_total=2;
  assert(gpgx_audio_trace_configure(&config,mask,kinds,hooks,&range)==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); ranges[0]=range; ranges[1]=range; ranges[1].start=0x101;
  config.range_count=2; kind.cancellation_range_count=2; config.snapshot_bytes_total=2;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,ranges)==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(1); ranges[0]=range; ranges[1]=range; ranges[1].range_id=2; ranges[1].start=0x101;
  config.range_count=2; hooks[1].range_first=1; config.snapshot_bytes_total=2;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,ranges)==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); ranges[0]=range; ranges[1]=range; ranges[1].range_id=2; config.range_count=2;
  kind.cancellation_range_count=2; config.snapshot_bytes_total=2;
  assert(gpgx_audio_trace_configure(&config,mask,&kind,hooks,ranges)==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[1]=hooks[0]; hooks[1].hook_token=2; config.hook_count=2;
  assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
  fixture(0); hooks[0].pc=1; hooks[0].opcode[0]=0xa1; hooks[0].hook_token=1;
  hooks[1]=hooks[0]; hooks[1].pc=0; hooks[1].opcode[0]=0xa0; hooks[1].hook_token=2;
  config.hook_count=2; mask[0]=3; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
}

static void phases_copy_and_drain(void)
{
  struct gpgx_audio_trace_event events[8];
  uint8_t previous;
  uint32_t count, overflow, drained;
  fixture(1); assert(configure()==TRACE_OK);
  assert(gpgx_audio_trace_end_frame()==TRACE_INVALID_PHASE);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_INVALID_PHASE);
  assert(gpgx_audio_trace_drain(NULL,0,&drained)==TRACE_INVALID_PHASE);
  assert(gpgx_audio_trace_abort_frame()==TRACE_INVALID_PHASE);
  memset(mask,0,sizeof(mask)); memset(hooks,0,sizeof(hooks));
  memset(&kind,0,sizeof(kind)); memset(&range,0,sizeof(range)); memset(&config,0,sizeof(config));
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_INVALID_PHASE);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_INVALID_PHASE);
  previous=gpgx_audio_trace_enter_cpu(1);
  gpgx_audio_trace_instruction(1,0);
  gpgx_audio_trace_fm_write(2,0x2a); zram[0x100]=0x5a; gpgx_audio_trace_instruction(1,1);
  gpgx_audio_trace_leave_cpu(previous);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_end_frame()==TRACE_INVALID_PHASE);
  assert(gpgx_audio_trace_event_count(NULL,&overflow)==TRACE_INVALID_ARGUMENT);
  assert(gpgx_audio_trace_event_count(&count,NULL)==TRACE_INVALID_ARGUMENT);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_OK && count==6 && overflow==0);
  assert(gpgx_audio_trace_drain(NULL,8,&drained)==TRACE_INVALID_ARGUMENT && drained==6);
  memset(events,0xa5,sizeof(events));
  assert(gpgx_audio_trace_drain(events,5,&drained)==TRACE_OUTPUT_CAPACITY && drained==6);
  assert(events[0].ordinal==0xa5a5a5a5u);
  assert(gpgx_audio_trace_drain(events,8,&drained)==TRACE_OK && drained==6);
  assert(events[0].kind==EVENT_SERVICE_BEGIN && events[1].kind==EVENT_FM_WRITE);
  assert(events[3].kind==EVENT_SNAPSHOT_CHUNK && events[3].payload[0]==0x5a);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK && gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_OK && count==0);
  assert(gpgx_audio_trace_drain(NULL,0,&drained)==TRACE_OK && drained==0);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK && gpgx_audio_trace_abort_frame()==TRACE_OK);
  assert(gpgx_audio_trace_disable()==TRACE_OK && gpgx_audio_trace_disable()==TRACE_OK);
}

static void first_fault_is_read_only_and_session_scoped(void)
{
  struct gpgx_audio_trace_first_fault_v1 fault, preserved;
  uint32_t before;
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  memset(&fault,0xa5,sizeof(fault));
  assert(sizeof(fault)==16);
  assert(gpgx_audio_trace_first_fault(NULL)==TRACE_INVALID_ARGUMENT);
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_INVALID_PHASE);

  fixture(1); assert(configure()==TRACE_OK);
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_OK);
  assert(fault.reason==GPGX_AUDIO_TRACE_FAULT_NONE && fault.pc==0
    && fault.source_cpu==0 && fault.active_kind==0 && fault.active_depth==0
    && fault.continuation_count==0 && fault.continuation_limit==0
    && fault.reserved[0]==0 && fault.reserved[1]==0 && fault.reserved[2]==0);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  zram[1]=0xa2;
  before=trace_event_count_value;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  assert(trace_event_count_value==before && trace_runtime_error);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_OK);
  assert(fault.reason==GPGX_AUDIO_TRACE_FAULT_HOOK_PROOF && fault.pc==1
    && fault.source_cpu==GPGX_AUDIO_TRACE_CPU_Z80 && fault.active_kind==1
    && fault.active_depth==1 && fault.continuation_count==0
    && fault.continuation_limit==0);
  preserved=fault;
  trace_issue_source=0;
  gpgx_audio_trace_fm_write(0,0x2a);
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_OK);
  assert(!memcmp(&fault,&preserved,sizeof(fault)));
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
  memset(&fault,0,sizeof(fault));
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_OK);
  assert(!memcmp(&fault,&preserved,sizeof(fault)));
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_INVALID_PHASE);
  zram[1]=0xa1;
  fixture(1); assert(configure()==TRACE_OK);
  assert(gpgx_audio_trace_first_fault(&fault)==TRACE_OK
    && fault.reason==GPGX_AUDIO_TRACE_FAULT_NONE);
  assert(gpgx_audio_trace_disable()==TRACE_OK);
}

static void prearm_filter_and_publication_epoch(void)
{
  struct gpgx_audio_trace_config_v1 c;
  struct gpgx_audio_service_kind_v1 kinds[2];
  struct gpgx_audio_service_hook_v1 hs[4];
  struct gpgx_audio_snapshot_range_v1 r;
  struct gpgx_audio_trace_event events[1100];
  uint8_t m[8192], rom[65536];
  uint32_t count, overflow, drained;
  uint16_t carried_token;
  memset(&c,0,sizeof(c)); memset(kinds,0,sizeof(kinds)); memset(hs,0,sizeof(hs));
  memset(&r,0,sizeof(r)); memset(m,0,sizeof(m)); memset(rom,0,sizeof(rom));
  c.magic=0x31544147; c.abi_version=2; c.struct_size=64; c.kind_size=16;
  c.hook_size=32; c.range_size=16; c.event_size=32; c.max_depth=8;
  c.max_opcode_bytes=8; c.reset_service_kind=2; c.max_continuation_frames=4;
  c.flags=1; c.watch_mask_bytes=8192; c.kind_count=2; c.hook_count=4;
  c.range_count=1; c.snapshot_bytes_total=0x6000; c.event_capacity=65536;
  c.max_service_tokens_per_frame=65535;
  kinds[0].kind_id=1; kinds[0].flags=KIND_ALLOW_CONTINUATION;
  kinds[0].cancellation_range_count=1; kinds[0].continuation_frame_limit=4;
  kinds[1]=kinds[0]; kinds[1].kind_id=2;
  r.range_id=1; r.length=0x2000;
  hs[0].hook_token=1; hs[0].action=ACTION_PUSH_BEGIN; hs[0].cpu=GPGX_AUDIO_TRACE_CPU_Z80;
  hs[0].service_kind=2; hs[0].opcode_length=1; hs[0].opcode[0]=0xa0; m[0]=1;
  hs[1].hook_token=2; hs[1].action=ACTION_PUSH_BEGIN; hs[1].cpu=GPGX_AUDIO_TRACE_CPU_M68K;
  hs[1].pc=0x100; hs[1].service_kind=1; hs[1].flags=HOOK_PREARM_PERMITTED;
  hs[1].opcode_length=1; hs[1].opcode[0]=0xb0;
  hs[2].hook_token=3; hs[2].action=ACTION_POP_END_AT_PC; hs[2].cpu=GPGX_AUDIO_TRACE_CPU_M68K;
  hs[2].pc=0x102; hs[2].expected_active_kind=1;
  hs[2].flags=HOOK_ARM_Z80_PROOFS_ON_COMPLETION|HOOK_PREARM_PERMITTED;
  hs[2].opcode_length=1; hs[2].opcode[0]=0xb1; hs[2].range_count=1;
  hs[3].hook_token=4; hs[3].action=ACTION_PUSH_BEGIN; hs[3].cpu=GPGX_AUDIO_TRACE_CPU_M68K;
  hs[3].pc=0x200; hs[3].service_kind=2; hs[3].opcode_length=1; hs[3].opcode[0]=0xb2;
  rom[0x100^1]=0xb0; rom[0x102^1]=0xb1; rom[0x200^1]=0xb2;
  memset(&m68k,0,sizeof(m68k)); m68k.memory_map[0].base=rom; zram[0]=0xa0;

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  c.abi_version=1;
  assert(gpgx_audio_trace_configure(&c,m,kinds,hs,&r)==TRACE_ABI_OR_CONFIG_LIMIT);
  c.flags=0; hs[1].flags=0; hs[2].flags=HOOK_ARM_Z80_PROOFS_ON_COMPLETION;
  assert(gpgx_audio_trace_configure(&c,m,kinds,hs,&r)==TRACE_OK);
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  c.flags=1; hs[1].flags=HOOK_PREARM_PERMITTED;
  hs[2].flags=HOOK_ARM_Z80_PROOFS_ON_COMPLETION|HOOK_PREARM_PERMITTED;
  c.abi_version=2;
  assert(gpgx_audio_trace_configure(&c,m,kinds,hs,&r)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x200);
  trace_issue_source=GPGX_AUDIO_TRACE_CPU_Z80; trace_z80_instruction_pc=0x44;
  gpgx_audio_trace_fm_write(0,0x2a); gpgx_audio_trace_psg_write(0x9f);
  assert(trace_depth==0 && trace_event_count_value==0 && !trace_runtime_error);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  assert(trace_depth==1 && trace_stack[0].kind==1);
  trace_issue_source=GPGX_AUDIO_TRACE_CPU_M68K; trace_m68k_instruction_pc=0x101;
  gpgx_audio_trace_fm_write(0,0x22);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_z80_proofs_armed && trace_depth==0);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  assert(trace_depth==1 && trace_stack[0].kind==2);
  carried_token=trace_stack[0].token;
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_OK && count!=0 && overflow==0);
  assert(gpgx_audio_trace_drain(events,1100,&drained)==TRACE_OK && drained==count);
  assert(gpgx_audio_trace_begin_publication_epoch()==TRACE_OK);
  assert(trace_depth==1 && trace_stack[0].token==carried_token
    && trace_stack[0].carried_frames==0 && trace_z80_proofs_armed);
  assert(gpgx_audio_trace_begin_publication_epoch()==TRACE_INVALID_PHASE);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  trace_issue_source=0; gpgx_audio_trace_fm_write(0,0x2a);
  assert(trace_runtime_error);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
  assert(gpgx_audio_trace_disable()==TRACE_OK);
}

static void chip_port_vectors(void)
{
  struct gpgx_audio_trace_event events[16];
  uint8_t previous;
  uint32_t count;
  fixture(1); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  previous=gpgx_audio_trace_enter_cpu(GPGX_AUDIO_TRACE_CPU_Z80);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  gpgx_audio_trace_fm_write(0,0x2a); gpgx_audio_trace_fm_write(1,0x11);
  gpgx_audio_trace_fm_write(1,0x22); gpgx_audio_trace_fm_write(2,0x2a);
  gpgx_audio_trace_fm_write(3,0x33); gpgx_audio_trace_psg_write(0xaa);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  gpgx_audio_trace_leave_cpu(previous);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(events,16,&count)==TRACE_OK && count==11);
  assert(events[1].kind==EVENT_FM_WRITE && events[1].subject==0 && events[1].value==0x2a);
  assert(events[2].subject==1 && events[2].value==0x11);
  assert(events[3].subject==1 && events[3].value==0x22);
  assert(events[4].subject==2 && events[4].value==0x2a);
  assert(events[5].subject==3 && events[5].value==0x33);
  assert(events[6].kind==EVENT_PSG_WRITE && events[6].value==0xaa);
}

static void m68k_proof_and_stack_bounds(void)
{
  uint8_t page[65536];
  struct gpgx_audio_service_hook_v1 push;
  memset(page,0,sizeof(page)); fixture(0); mask[0]=0; hooks[0].cpu=GPGX_AUDIO_TRACE_CPU_M68K;
  hooks[0].pc=0x1234; page[0x1234^1]=0xa0; m68k.memory_map[0].base=page;
  assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x1235); assert(trace_depth==0 && trace_event_count_value==0);
  gpgx_audio_trace_instruction(2,0x1234); assert(trace_depth==1);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK); assert(gpgx_audio_trace_disable()==TRACE_OK);
  fixture(0); mask[0]=0; hooks[0].cpu=2; hooks[0].pc=0x1234; m68k.memory_map[0].base=NULL;
  assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x1234); assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
  fixture(0); mask[0]=0; hooks[0].cpu=2; hooks[0].pc=0x1234;
  m68k.memory_map[0].base=page; m68k.memory_map[0].read8=(void *)1;
  assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x1234); assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK); m68k.memory_map[0].read8=NULL;
  fixture(0); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  push=trace_hooks[0]; for(int i=0;i<8;i++) assert(push_service(&push,1,0));
  assert(trace_depth==8 && trace_stack[7].depth==7 && trace_stack[7].parent==trace_stack[6].token);
  assert(!push_service(&push,1,0) && trace_depth==8 && trace_runtime_error);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void overflow_and_reset_bounds(void)
{
  struct gpgx_audio_service_hook_v1 push;
  uint32_t drained;
  fixture(0); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  push=trace_hooks[0]; assert(push_service(&push,1,0)); trace_issue_source=GPGX_AUDIO_TRACE_CPU_Z80;
  trace_event_count_value=GPGX_AUDIO_TRACE_EVENT_CAPACITY-1; trace_omitted_count=0;
  gpgx_audio_trace_fm_write(0,0); gpgx_audio_trace_fm_write(0,0);
  assert(trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY && trace_omitted_count==1);
  trace_omitted_count=0xffffffffu; gpgx_audio_trace_fm_write(0,0);
  assert(trace_omitted_count==0xffffffffu);
  assert(gpgx_audio_trace_end_frame()==TRACE_OVERFLOW);
  assert(gpgx_audio_trace_drain(NULL,0,NULL)==TRACE_INVALID_ARGUMENT);
  assert(gpgx_audio_trace_drain(NULL,0,&drained)==TRACE_OVERFLOW);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
  fixture(0); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  for(int i=0;i<8;i++) assert(push_service(&push,1,0));
  zram[0x100]=0x5a;
  gpgx_audio_trace_reset_begin(1); assert(trace_depth==1 && trace_issue_source==GPGX_AUDIO_TRACE_CPU_RESET);
  assert(trace_events[8].kind==EVENT_RESET_BEGIN && trace_events[8].service_token==9);
  for(int i=0;i<8;i++)
  {
    uint32_t at=9u+(uint32_t)i*4u;
    assert(trace_events[at].kind==EVENT_SNAPSHOT_BEGIN);
    assert(trace_events[at+1].kind==EVENT_SNAPSHOT_CHUNK && trace_events[at+1].payload[0]==0x5a);
    assert(trace_events[at+3].kind==EVENT_SERVICE_END);
    assert(trace_events[at+3].service_token==(uint16_t)(8-i));
    assert(trace_events[at+3].flags==FLAG_RESET_CANCELLED);
  }
  assert(trace_protected_tail==4);
  gpgx_audio_trace_reset_end(1); assert(trace_depth==0 && trace_protected_tail==0);
  assert(trace_event_count_value==45 && trace_events[44].kind==EVENT_RESET_END);
  assert(trace_events[44].service_token==9 && trace_events[44].flags==FLAG_POWER);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  fixture(1); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); trace_event_count_value=GPGX_AUDIO_TRACE_EVENT_CAPACITY-3;
  trace_omitted_count=0; gpgx_audio_trace_instruction(1,1);
  assert(trace_depth==1 && trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY-3);
  assert(trace_omitted_count==4);
  assert(gpgx_audio_trace_end_frame()==TRACE_OVERFLOW); assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  fixture(0); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_reset_begin(0); assert(trace_protected_tail==4);
  trace_event_count_value=GPGX_AUDIO_TRACE_EVENT_CAPACITY-trace_protected_tail;
  trace_omitted_count=0; gpgx_audio_trace_fm_write(0,0);
  assert(trace_omitted_count==1 && trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY-4);
  gpgx_audio_trace_reset_end(0);
  assert(trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY);
  assert(trace_events[GPGX_AUDIO_TRACE_EVENT_CAPACITY-1].kind==EVENT_RESET_END);
  assert(gpgx_audio_trace_end_frame()==TRACE_OVERFLOW); assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  memset(trace_ranges,0,sizeof(trace_ranges));
  for(int i=0, start=0;i<128;i++)
  {
    int length=i<112?1:505;
    trace_ranges[i].length=(uint16_t)length; trace_ranges[i].start=(uint16_t)start; start+=length;
  }
  assert(range_group_reservation(0,128,0)==1393);
  assert(1u+8u*1393u+1393u==12538u);
  memset(trace_kinds,0,sizeof(trace_kinds)); memset(trace_kind_lookup,0,sizeof(trace_kind_lookup));
  memset(trace_stack,0,sizeof(trace_stack)); trace_kinds[0].kind_id=1;
  trace_kinds[0].cancellation_range_count=128; trace_kind_lookup[1]=1;
  trace_kind_reservation[1]=1393; trace_config.reset_service_kind=1;
  trace_depth=8; trace_next_token=9; trace_event_count_value=trace_omitted_count=0;
  trace_phase=PHASE_RECORDING; gpgx_audio_trace_enabled=1;
  for(int i=0;i<8;i++) { trace_stack[i].token=(uint16_t)(i+1); trace_stack[i].kind=1; trace_stack[i].depth=(uint8_t)i; }
  gpgx_audio_trace_reset_begin(0); gpgx_audio_trace_reset_end(0);
  assert(trace_event_count_value==12538 && trace_omitted_count==0);
  assert(trace_events[0].kind==EVENT_RESET_BEGIN && trace_events[12537].kind==EVENT_RESET_END);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK); assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void guarded_tail_continuation_and_failures(void)
{
  uint32_t count, overflow, drained;
  struct gpgx_audio_trace_event events[8];
  fixture(1); hooks[1].action=ACTION_TAIL_POP_PUSH; hooks[1].service_kind=1;
  hooks[1].pc=0; hooks[1].opcode[0]=0xa0; mask[0]=1;
  assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,0);
  assert(trace_depth==1 && trace_stack[0].token==2 && trace_event_count_value==6);
  assert(trace_events[4].kind==EVENT_SERVICE_END && trace_events[5].kind==EVENT_SERVICE_BEGIN);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
  fixture(1); hooks[1].pc=0; hooks[1].expected_active_kind=0; hooks[1].opcode[0]=0xa0; mask[0]=1;
  assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);

  fixture(0); kind.flags=KIND_ALLOW_CHILDREN|KIND_ALLOW_CONTINUATION;
  kind.continuation_frame_limit=1; config.max_continuation_frames=1;
  assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(events,8,&drained)==TRACE_OK && drained==1);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK && gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_OK && count==0);
  assert(gpgx_audio_trace_drain(NULL,0,&drained)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  fixture(0); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  trace_issue_source=GPGX_AUDIO_TRACE_CPU_Z80; gpgx_audio_trace_fm_write(0,0);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
  fixture(0); assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  zram[0]=0xff; gpgx_audio_trace_instruction(1,0);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK); zram[0]=0xa0;

  fixture(1); kind.flags=KIND_ALLOW_CHILDREN|KIND_TYPED_ASYNC;
  assert(configure()==TRACE_OK); assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,1);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,1);
  assert(trace_event_count_value==10 && trace_events[0].service_token==1);
  assert(trace_events[5].service_token==2 && trace_depth==0);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK); assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void suspended_parent_continuation_exposure(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_service_kind_v1 kinds[2];
  struct gpgx_audio_service_hook_v1 local_hooks[4];
  struct gpgx_audio_snapshot_range_v1 local_range;
  struct gpgx_audio_trace_event events[16];
  uint8_t local_mask[8192];
  uint32_t drained;
  memset(&local_config,0,sizeof(local_config)); memset(kinds,0,sizeof(kinds));
  memset(local_hooks,0,sizeof(local_hooks)); memset(&local_range,0,sizeof(local_range));
  memset(local_mask,0,sizeof(local_mask));
  local_config.magic=0x31544147; local_config.abi_version=1; local_config.struct_size=64;
  local_config.kind_size=16; local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8; local_config.max_opcode_bytes=8;
  local_config.reset_service_kind=1; local_config.max_continuation_frames=4;
  local_config.watch_mask_bytes=8192; local_config.kind_count=2; local_config.hook_count=4;
  local_config.range_count=1; local_config.snapshot_bytes_total=4;
  local_config.event_capacity=65536; local_config.max_service_tokens_per_frame=65535;
  kinds[0].kind_id=1; kinds[0].flags=KIND_ALLOW_CHILDREN|KIND_ALLOW_CONTINUATION;
  kinds[0].cancellation_range_count=1; kinds[0].continuation_frame_limit=1;
  kinds[1].kind_id=2; kinds[1].flags=KIND_ALLOW_CONTINUATION;
  kinds[1].cancellation_range_count=1; kinds[1].continuation_frame_limit=4;
  local_range.range_id=1; local_range.start=0x100; local_range.length=1;
  for(int i=0;i<4;i++)
  {
    local_hooks[i].hook_token=(uint16_t)(i+1); local_hooks[i].cpu=1;
    local_hooks[i].pc=(uint32_t)i; local_hooks[i].opcode_length=1;
    local_hooks[i].opcode[0]=(uint8_t)(0xa0+i); local_mask[0]|=(uint8_t)(1u<<i);
    zram[i]=(uint8_t)(0xa0+i);
  }
  local_hooks[0].action=ACTION_PUSH_BEGIN; local_hooks[0].service_kind=1;
  local_hooks[1].action=ACTION_PUSH_BEGIN; local_hooks[1].service_kind=2;
  local_hooks[1].expected_active_kind=1;
  local_hooks[2].action=ACTION_POP_END_AT_PC; local_hooks[2].expected_active_kind=2;
  local_hooks[2].range_count=1;
  local_hooks[3].action=ACTION_POP_END_AT_PC; local_hooks[3].expected_active_kind=1;
  local_hooks[3].range_count=1;

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,kinds,local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,1);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(events,16,&drained)==TRACE_OK && drained==2);
  for(int frame=0;frame<3;frame++)
  {
    assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
    assert(gpgx_audio_trace_end_frame()==TRACE_OK);
    assert(gpgx_audio_trace_drain(NULL,0,&drained)==TRACE_OK && drained==0);
  }
  assert(trace_stack[0].carried_frames==0 && trace_stack[1].carried_frames==3);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,2);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(events,16,&drained)==TRACE_OK && drained==4);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK && gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(NULL,0,&drained)==TRACE_OK && drained==0);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,kinds,local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,1);
  gpgx_audio_trace_reset_begin(0); gpgx_audio_trace_reset_end(0);
  assert(trace_depth==0 && gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void typed_nested_multiple_exits_and_distinct_reset(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_service_kind_v1 kinds[2];
  struct gpgx_audio_service_hook_v1 local_hooks[5];
  struct gpgx_audio_snapshot_range_v1 ranges[2];
  struct gpgx_audio_trace_event drained_events[32];
  uint8_t local_mask[8192];
  uint8_t previous;
  uint32_t drained;
  memset(&local_config,0,sizeof(local_config)); memset(kinds,0,sizeof(kinds));
  memset(local_hooks,0,sizeof(local_hooks)); memset(ranges,0,sizeof(ranges));
  memset(local_mask,0,sizeof(local_mask));
  local_config.magic=0x31544147; local_config.abi_version=1; local_config.struct_size=64;
  local_config.kind_size=16; local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8; local_config.max_opcode_bytes=8;
  local_config.reset_service_kind=1; local_config.watch_mask_bytes=8192;
  local_config.kind_count=2; local_config.hook_count=5; local_config.range_count=2;
  local_config.snapshot_bytes_total=5; local_config.event_capacity=65536;
  local_config.max_service_tokens_per_frame=65535;
  kinds[0].kind_id=1; kinds[0].flags=KIND_ALLOW_CHILDREN; kinds[0].cancellation_range_count=1;
  kinds[1].kind_id=2; kinds[1].flags=KIND_TYPED_ASYNC; kinds[1].cancellation_range_first=1;
  kinds[1].cancellation_range_count=1;
  ranges[0].range_id=1; ranges[0].start=0x100; ranges[0].length=1;
  ranges[1].range_id=2; ranges[1].start=0x101; ranges[1].length=1;
  for(int i=0;i<5;i++) { local_hooks[i].hook_token=(uint16_t)(i+1); local_hooks[i].cpu=1;
    local_hooks[i].opcode_length=1; local_hooks[i].pc=(uint32_t)(i==3?4:i==4?5:i);
    local_hooks[i].opcode[0]=(uint8_t)(0xb0+local_hooks[i].pc); local_mask[local_hooks[i].pc>>3]|=(uint8_t)(1u<<(local_hooks[i].pc&7));
    zram[local_hooks[i].pc]=local_hooks[i].opcode[0]; }
  local_hooks[0].action=ACTION_PUSH_BEGIN; local_hooks[0].service_kind=1;
  local_hooks[1].action=ACTION_PUSH_BEGIN; local_hooks[1].service_kind=2; local_hooks[1].expected_active_kind=1;
  local_hooks[2].action=ACTION_POP_END_AT_PC; local_hooks[2].expected_active_kind=2;
  local_hooks[2].range_first=1; local_hooks[2].range_count=1;
  local_hooks[3].action=ACTION_POP_END_FALLTHROUGH; local_hooks[3].expected_active_kind=2;
  local_hooks[3].range_first=1; local_hooks[3].range_count=1;
  local_hooks[4].action=ACTION_POP_END_AT_PC; local_hooks[4].expected_active_kind=1;
  local_hooks[4].range_count=1;
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,kinds,local_hooks,ranges)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  previous=gpgx_audio_trace_enter_cpu(1);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_fm_write(0,0x11);
  gpgx_audio_trace_instruction(1,1); gpgx_audio_trace_psg_write(0x22);
  gpgx_audio_trace_instruction(1,2); gpgx_audio_trace_instruction(1,1);
  gpgx_audio_trace_instruction(1,4); gpgx_audio_trace_instruction(1,5);
  gpgx_audio_trace_leave_cpu(previous);
  assert(trace_depth==0 && trace_events[0].service_kind==1 && trace_events[2].service_kind==2);
  assert(trace_events[1].kind==EVENT_FM_WRITE && trace_events[1].service_token==trace_events[0].service_token);
  assert(trace_events[3].kind==EVENT_PSG_WRITE && trace_events[3].service_token==trace_events[2].service_token);
  assert(trace_events[2].parent_token==trace_events[0].service_token && trace_events[2].depth==1);
  assert(trace_events[8].parent_token==trace_events[0].service_token && trace_events[8].depth==1
    && trace_events[8].service_token!=trace_events[2].service_token);
  assert(trace_events[7].kind==EVENT_SERVICE_END && trace_events[7].service_kind==2);
  assert(trace_events[8].kind==EVENT_SERVICE_BEGIN && trace_events[8].service_kind==2);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(drained_events,32,&drained)==TRACE_OK && drained==17);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,1);
  zram[0x100]=0xaa; zram[0x101]=0xbb; gpgx_audio_trace_reset_begin(0);
  assert(trace_events[2].kind==EVENT_RESET_BEGIN);
  assert(trace_events[4].kind==EVENT_SNAPSHOT_CHUNK && trace_events[4].subject==2
    && trace_events[4].payload[0]==0xbb);
  assert(trace_events[8].kind==EVENT_SNAPSHOT_CHUNK && trace_events[8].subject==1
    && trace_events[8].payload[0]==0xaa);
  assert(trace_events[6].kind==EVENT_SERVICE_END && trace_events[6].service_kind==2);
  assert(trace_events[10].kind==EVENT_SERVICE_END && trace_events[10].service_kind==1);
  gpgx_audio_trace_reset_end(0); assert(trace_events[14].kind==EVENT_RESET_END);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK); assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(1,1);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void same_pc_different_kind_tail_chain(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_service_kind_v1 kinds[3];
  struct gpgx_audio_service_hook_v1 local_hooks[3];
  struct gpgx_audio_snapshot_range_v1 local_range;
  uint8_t local_mask[8192];
  memset(&local_config,0,sizeof(local_config)); memset(kinds,0,sizeof(kinds));
  memset(local_hooks,0,sizeof(local_hooks)); memset(&local_range,0,sizeof(local_range));
  memset(local_mask,0,sizeof(local_mask));
  local_config.magic=0x31544147; local_config.abi_version=1; local_config.struct_size=64;
  local_config.kind_size=16; local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8; local_config.max_opcode_bytes=8;
  local_config.reset_service_kind=1; local_config.watch_mask_bytes=8192;
  local_config.kind_count=3; local_config.hook_count=3; local_config.range_count=1;
  local_config.snapshot_bytes_total=5; local_config.event_capacity=65536;
  local_config.max_service_tokens_per_frame=65535;
  local_range.range_id=1; local_range.start=0x100; local_range.length=1;
  for(int i=0;i<3;i++) { kinds[i].kind_id=(uint8_t)(i+1); kinds[i].cancellation_range_count=1;
    local_hooks[i].hook_token=(uint16_t)(i+1); local_hooks[i].cpu=1; local_hooks[i].pc=0;
    local_hooks[i].opcode_length=1; local_hooks[i].opcode[0]=0xa0; }
  local_hooks[0].action=ACTION_PUSH_BEGIN; local_hooks[0].service_kind=1;
  local_hooks[1].action=ACTION_TAIL_POP_PUSH; local_hooks[1].expected_active_kind=1;
  local_hooks[1].service_kind=2; local_hooks[1].range_count=1;
  local_hooks[2].action=ACTION_TAIL_POP_PUSH; local_hooks[2].expected_active_kind=2;
  local_hooks[2].service_kind=3; local_hooks[2].range_count=1;
  local_mask[0]=1; zram[0]=0xa0;
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,kinds,local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); assert(trace_depth==1 && trace_stack[0].kind==1);
  gpgx_audio_trace_instruction(1,0); assert(trace_depth==1 && trace_stack[0].kind==2
    && trace_event_count_value==6);
  assert(trace_events[4].kind==EVENT_SERVICE_END && trace_events[4].service_kind==1);
  assert(trace_events[5].kind==EVENT_SERVICE_BEGIN && trace_events[5].service_kind==2);
  gpgx_audio_trace_instruction(1,0); assert(trace_depth==1 && trace_stack[0].kind==3
    && trace_event_count_value==11);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void cpu_index_and_same_pc_alternatives(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_service_kind_v1 local_kind;
  struct gpgx_audio_service_hook_v1 local_hooks[5];
  struct gpgx_audio_snapshot_range_v1 local_range;
  uint8_t local_mask[8192], page[65536], high_page[65536];
  memset(&local_config,0,sizeof(local_config)); memset(&local_kind,0,sizeof(local_kind));
  memset(local_hooks,0,sizeof(local_hooks)); memset(&local_range,0,sizeof(local_range));
  memset(local_mask,0,sizeof(local_mask)); memset(page,0,sizeof(page));
  memset(high_page,0,sizeof(high_page));
  local_config.magic=0x31544147; local_config.abi_version=1; local_config.struct_size=64;
  local_config.kind_size=16; local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8; local_config.max_opcode_bytes=8;
  local_config.reset_service_kind=1; local_config.watch_mask_bytes=8192;
  local_config.kind_count=1; local_config.hook_count=5; local_config.range_count=1;
  local_config.snapshot_bytes_total=3; local_config.event_capacity=65536;
  local_config.max_service_tokens_per_frame=65535;
  local_kind.kind_id=1; local_kind.flags=KIND_ALLOW_CHILDREN;
  local_kind.cancellation_range_count=1;
  local_range.range_id=1; local_range.start=0x100; local_range.length=1;
  local_hooks[0].hook_token=1; local_hooks[0].action=ACTION_PUSH_BEGIN;
  local_hooks[0].cpu=GPGX_AUDIO_TRACE_CPU_Z80; local_hooks[0].service_kind=1;
  local_hooks[0].opcode_length=1; local_hooks[0].opcode[0]=0xa0;
  local_hooks[1].hook_token=2; local_hooks[1].action=ACTION_PUSH_BEGIN;
  local_hooks[1].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[1].pc=0x100;
  local_hooks[1].service_kind=1; local_hooks[1].opcode_length=1; local_hooks[1].opcode[0]=0xa0;
  local_hooks[2]=local_hooks[1]; local_hooks[2].hook_token=3;
  local_hooks[2].action=ACTION_TAIL_POP_PUSH; local_hooks[2].expected_active_kind=1;
  local_hooks[2].range_count=1;
  local_hooks[3]=local_hooks[2]; local_hooks[3].hook_token=4;
  local_hooks[3].action=ACTION_POP_END_AT_PC; local_hooks[3].service_kind=0;
  local_hooks[3].pc=0x101; local_hooks[3].opcode[0]=0xa1;
  local_hooks[4]=local_hooks[1]; local_hooks[4].hook_token=5; local_hooks[4].pc=0x10100;
  local_mask[0]=1; zram[0]=0xa0; page[0x100^1]=0xa0; page[0x101^1]=0xa1;
  high_page[0x100^1]=0xa0;
  m68k.memory_map[0].base=page; m68k.memory_map[0].read8=NULL; m68k.memory_map[0].read16=NULL;
  m68k.memory_map[1].base=high_page; m68k.memory_map[1].read8=NULL; m68k.memory_map[1].read16=NULL;
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,&local_kind,local_hooks,&local_range)==TRACE_OK);
  assert(trace_hook_first[1]==0 && trace_hook_count[1]==1);
  assert(trace_hook_first[2]==1 && trace_hook_count[2]==4);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x100);
  assert(trace_depth==1 && trace_stack[0].token==1 && trace_event_count_value==1);
  gpgx_audio_trace_instruction(2,0x100);
  assert(trace_depth==1 && trace_stack[0].token==2 && trace_event_count_value==6);
  gpgx_audio_trace_instruction(2,0x101);
  assert(trace_depth==0 && trace_event_count_value==10);
  gpgx_audio_trace_instruction(2,0x10100);
  assert(trace_depth==1 && trace_stack[0].token==3 && trace_event_count_value==11);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void put_m68k_long(uint16_t offset, uint32_t value)
{
  work_ram[(offset + 0u) ^ 1u]=(uint8_t)(value >> 24);
  work_ram[(offset + 1u) ^ 1u]=(uint8_t)(value >> 16);
  work_ram[(offset + 2u) ^ 1u]=(uint8_t)(value >> 8);
  work_ram[(offset + 3u) ^ 1u]=(uint8_t)value;
}

static void conditional_m68k_return_predicate(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_trace_config_v1 base_config;
  struct gpgx_audio_service_kind_v1 local_kind;
  struct gpgx_audio_service_kind_v1 base_kind;
  struct gpgx_audio_service_hook_v1 local_hooks[3];
  struct gpgx_audio_service_hook_v1 base_hooks[3];
  struct gpgx_audio_snapshot_range_v1 local_ranges[3];
  struct gpgx_audio_snapshot_range_v1 base_ranges[3];
  struct gpgx_audio_trace_event events[16];
  uint8_t local_mask[8192], rom_page[65536];
  uint32_t drained;
  memset(&local_config,0,sizeof(local_config)); memset(&local_kind,0,sizeof(local_kind));
  memset(local_hooks,0,sizeof(local_hooks)); memset(local_ranges,0,sizeof(local_ranges));
  memset(local_mask,0,sizeof(local_mask)); memset(rom_page,0,sizeof(rom_page));
  memset(work_ram,0,sizeof(work_ram)); memset(&m68k,0,sizeof(m68k));
  local_config.magic=0x31544147; local_config.abi_version=2; local_config.struct_size=64;
  local_config.kind_size=16; local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8; local_config.max_opcode_bytes=8;
  local_config.reset_service_kind=1; local_config.watch_mask_bytes=8192;
  local_config.kind_count=1; local_config.hook_count=3; local_config.range_count=3;
  local_config.snapshot_bytes_total=2; local_config.event_capacity=65536;
  local_config.max_service_tokens_per_frame=65535;
  local_kind.kind_id=1; local_kind.flags=KIND_ALLOW_CHILDREN;
  local_kind.cancellation_range_count=1;
  local_ranges[0].range_id=1; local_ranges[0].start=0xf000; local_ranges[0].length=1;
  local_ranges[0].flags=2;
  local_ranges[1].range_id=2; local_ranges[1].flags=1; local_ranges[1].reserved[0]=0x71bd4;
  local_ranges[2].range_id=3; local_ranges[2].flags=1; local_ranges[2].reserved[0]=0x71be6;
  local_hooks[0].hook_token=1; local_hooks[0].action=ACTION_PUSH_BEGIN;
  local_hooks[0].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[0].pc=0x100;
  local_hooks[0].service_kind=1; local_hooks[0].opcode_length=1; local_hooks[0].opcode[0]=0xa0;
  local_hooks[1].hook_token=2; local_hooks[1].action=6;
  local_hooks[1].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[1].pc=0x100;
  local_hooks[1].expected_active_kind=1; local_hooks[1].opcode_length=1; local_hooks[1].opcode[0]=0xa0;
  local_hooks[2].hook_token=3; local_hooks[2].action=5;
  local_hooks[2].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[2].pc=0x102;
  local_hooks[2].expected_active_kind=1; local_hooks[2].range_count=1;
  local_hooks[2].opcode_length=1; local_hooks[2].opcode[0]=0xa1;
  local_hooks[2].reserved[0]=1; local_hooks[2].reserved[2]=2;
  rom_page[0x100^1]=0xa0; rom_page[0x102^1]=0xa1;
  m68k.memory_map[0].base=rom_page;
  m68k.memory_map[0xff].base=work_ram;
  base_config=local_config; base_kind=local_kind;
  memcpy(base_hooks,local_hooks,sizeof(base_hooks)); memcpy(base_ranges,local_ranges,sizeof(base_ranges));
#define RESTORE_CONDITIONAL_FIXTURE() do { \
  local_config=base_config; local_kind=base_kind; \
  memcpy(local_hooks,base_hooks,sizeof(local_hooks)); \
  memcpy(local_ranges,base_ranges,sizeof(local_ranges)); \
} while(0)
#define REJECT_CONDITIONAL_CONFIG(change) do { \
  RESTORE_CONDITIONAL_FIXTURE(); change; \
  assert(gpgx_audio_trace_disable()==TRACE_OK); \
  assert(gpgx_audio_trace_configure(&local_config,local_mask,&local_kind,local_hooks,local_ranges) \
    ==TRACE_ABI_OR_CONFIG_LIMIT); \
} while(0)
  REJECT_CONDITIONAL_CONFIG(local_hooks[2].cpu=GPGX_AUDIO_TRACE_CPU_Z80);
  REJECT_CONDITIONAL_CONFIG(local_hooks[1].expected_active_kind=0);
  REJECT_CONDITIONAL_CONFIG(local_hooks[1].cpu=GPGX_AUDIO_TRACE_CPU_Z80);
  REJECT_CONDITIONAL_CONFIG(local_hooks[1].flags=HOOK_ARM_Z80_PROOFS_ON_COMPLETION);
  REJECT_CONDITIONAL_CONFIG(local_hooks[2].reserved[2]=0);
  REJECT_CONDITIONAL_CONFIG(local_hooks[2].reserved[0]=3);
  REJECT_CONDITIONAL_CONFIG(local_ranges[2].flags=RANGE_M68K_RAM; local_ranges[2].length=1;
    local_ranges[2].reserved[0]=0);
  REJECT_CONDITIONAL_CONFIG(local_ranges[2].reserved[0]=local_ranges[1].reserved[0]);
  REJECT_CONDITIONAL_CONFIG(local_ranges[2].reserved[0]=0x71be7);
  REJECT_CONDITIONAL_CONFIG(local_ranges[2].reserved[0]=0x1000000u);
  REJECT_CONDITIONAL_CONFIG(local_kind.cancellation_range_first=1);
  REJECT_CONDITIONAL_CONFIG(local_ranges[0].flags=RANGE_M68K_RETURN_PC;
    local_ranges[0].start=local_ranges[0].length=0; local_ranges[0].reserved[0]=0x123456);
  REJECT_CONDITIONAL_CONFIG(local_ranges[0].flags=RANGE_FLAGS_ALL);
  RESTORE_CONDITIONAL_FIXTURE();
#undef REJECT_CONDITIONAL_CONFIG
#undef RESTORE_CONDITIONAL_FIXTURE
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,&local_kind,local_hooks,local_ranges)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  assert(trace_depth==1 && trace_event_count_value==2);
  assert(trace_events[1].kind==10 && trace_events[1].value==2);
  assert(trace_events[1].service_token==trace_stack[0].token
    && trace_events[1].parent_token==trace_stack[0].parent
    && trace_events[1].service_kind==trace_stack[0].kind
    && trace_events[1].depth==trace_stack[0].depth);
  selftest_m68k_a7=0x00fff100; put_m68k_long(0xf100,0x71bd4);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_depth==1 && trace_event_count_value==3);
  assert(trace_events[2].kind==10 && trace_events[2].value==0);
  work_ram[0xf000^1]=0x5a; put_m68k_long(0xf100,0x123456);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_depth==0 && trace_event_count_value==8);
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_drain(events,16,&drained)==TRACE_OK && drained==8);
  assert(events[3].kind==10 && events[3].value==1);
  assert(events[5].kind==EVENT_SNAPSHOT_CHUNK && events[5].payload[0]==0x5a);
  assert(events[7].kind==EVENT_SERVICE_END);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,&local_kind,local_hooks,local_ranges)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  m68k.memory_map[0xff].base=rom_page;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_depth==1 && trace_event_count_value==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  m68k.memory_map[0xff].base=work_ram;
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&base_config,local_mask,&base_kind,base_hooks,base_ranges)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  selftest_m68k_a7=0x00fff101; put_m68k_long(0xf100,0x71bd4);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_event_count_value==1 && trace_depth==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  selftest_m68k_a7=0x00fff100; put_m68k_long(0xf100,0x123457);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_event_count_value==1 && trace_depth==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  selftest_m68k_a7=0x00fef100;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_event_count_value==1 && trace_depth==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  selftest_m68k_a7=0x00fffffe;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_event_count_value==1 && trace_depth==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  selftest_m68k_a7=0x00fff100; put_m68k_long(0xf100,0x123456);
  trace_event_count_value=GPGX_AUDIO_TRACE_EVENT_CAPACITY-4; trace_omitted_count=0;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY-4);
  assert(trace_omitted_count==5 && trace_depth==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_OVERFLOW);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void observation_marker_alternatives(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_service_kind_v1 local_kinds[4];
  struct gpgx_audio_service_hook_v1 local_hooks[7], bad_hooks[7];
  struct gpgx_audio_snapshot_range_v1 local_range;
  uint8_t local_mask[8192], rom_page[65536];
  uint32_t before;
  memset(&local_config,0,sizeof(local_config)); memset(local_kinds,0,sizeof(local_kinds));
  memset(local_hooks,0,sizeof(local_hooks)); memset(&local_range,0,sizeof(local_range));
  memset(local_mask,0,sizeof(local_mask)); memset(rom_page,0,sizeof(rom_page));
  local_config.magic=0x31544147; local_config.abi_version=2; local_config.struct_size=64;
  local_config.kind_size=16; local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8; local_config.max_opcode_bytes=8;
  local_config.reset_service_kind=1; local_config.watch_mask_bytes=8192;
  local_config.kind_count=4; local_config.hook_count=7; local_config.range_count=1;
  local_config.snapshot_bytes_total=4; local_config.event_capacity=65536;
  local_config.max_service_tokens_per_frame=65535;
  for(int i=0;i<4;i++)
  {
    local_kinds[i].kind_id=(uint8_t)(i+1); local_kinds[i].flags=KIND_ALLOW_CHILDREN;
    local_kinds[i].cancellation_range_count=1;
  }
  local_range.range_id=1; local_range.start=0x100; local_range.length=1;
  local_hooks[0].hook_token=1; local_hooks[0].action=ACTION_PUSH_BEGIN;
  local_hooks[0].cpu=GPGX_AUDIO_TRACE_CPU_Z80; local_hooks[0].service_kind=2;
  local_hooks[0].opcode_length=1; local_hooks[0].opcode[0]=0xa0;
  local_hooks[1]=local_hooks[0]; local_hooks[1].hook_token=2; local_hooks[1].pc=1;
  local_hooks[1].service_kind=3; local_hooks[1].opcode[0]=0xa1;
  local_hooks[2].hook_token=3; local_hooks[2].action=ACTION_PUSH_BEGIN;
  local_hooks[2].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[2].pc=0x100;
  local_hooks[2].service_kind=4; local_hooks[2].opcode_length=1; local_hooks[2].opcode[0]=0xb0;
  for(int i=0;i<3;i++)
  {
    local_hooks[3+i].hook_token=(uint16_t)(4+i);
    local_hooks[3+i].action=ACTION_OBSERVATION_MARKER;
    local_hooks[3+i].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[3+i].pc=0x200;
    local_hooks[3+i].expected_active_kind=(uint8_t)(i==0?0:i+1);
    local_hooks[3+i].opcode_length=1; local_hooks[3+i].opcode[0]=0xb1;
  }
  local_hooks[6].hook_token=7; local_hooks[6].action=ACTION_OBSERVATION_MARKER;
  local_hooks[6].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[6].pc=0x202;
  local_hooks[6].expected_active_kind=4; local_hooks[6].opcode_length=1; local_hooks[6].opcode[0]=0xb2;
  local_mask[0]=3; zram[0]=0xa0; zram[1]=0xa1;
  rom_page[0x100^1]=0xb0; rom_page[0x200^1]=0xb1; rom_page[0x202^1]=0xb2;
  memset(&m68k,0,sizeof(m68k)); m68k.memory_map[0].base=rom_page;

  memcpy(bad_hooks,local_hooks,sizeof(bad_hooks)); bad_hooks[3].cpu=GPGX_AUDIO_TRACE_CPU_Z80;
  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,bad_hooks,&local_range)
    ==TRACE_ABI_OR_CONFIG_LIMIT);
  memcpy(bad_hooks,local_hooks,sizeof(bad_hooks)); bad_hooks[3].service_kind=1;
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,bad_hooks,&local_range)
    ==TRACE_ABI_OR_CONFIG_LIMIT);
  memcpy(bad_hooks,local_hooks,sizeof(bad_hooks)); bad_hooks[3].range_count=1;
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,bad_hooks,&local_range)
    ==TRACE_ABI_OR_CONFIG_LIMIT);
  memcpy(bad_hooks,local_hooks,sizeof(bad_hooks)); bad_hooks[3].flags=1;
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,bad_hooks,&local_range)
    ==TRACE_ABI_OR_CONFIG_LIMIT);
  memcpy(bad_hooks,local_hooks,sizeof(bad_hooks)); bad_hooks[3].reserved[0]=1;
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,bad_hooks,&local_range)
    ==TRACE_ABI_OR_CONFIG_LIMIT);

  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x200);
  assert(trace_event_count_value==1 && trace_events[0].kind==EVENT_HOOK_MARKER
    && trace_events[0].value==3 && trace_events[0].service_token==0
    && trace_events[0].service_kind==0);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,0); gpgx_audio_trace_instruction(2,0x200);
  assert(trace_event_count_value==2 && trace_events[1].service_token==trace_stack[0].token
    && trace_events[1].service_kind==2 && trace_events[1].source_cpu==2);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(1,1); gpgx_audio_trace_instruction(2,0x200);
  assert(trace_event_count_value==2 && trace_events[1].service_kind==3);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x100); gpgx_audio_trace_instruction(2,0x202);
  assert(trace_event_count_value==2 && trace_events[1].service_kind==4);
  before=trace_event_count_value; gpgx_audio_trace_instruction(2,0x200);
  assert(trace_event_count_value==before && trace_runtime_error);
  assert(gpgx_audio_trace_end_frame()==TRACE_ABI_OR_CONFIG_LIMIT);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(2,0x100);
  trace_event_count_value=GPGX_AUDIO_TRACE_EVENT_CAPACITY; trace_omitted_count=0;
  gpgx_audio_trace_instruction(2,0x202);
  assert(trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY
    && trace_omitted_count==1 && trace_depth==1);
  assert(gpgx_audio_trace_end_frame()==TRACE_OVERFLOW);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

static void direct_parent_close_promotes_top_child(void)
{
  struct gpgx_audio_trace_config_v1 local_config;
  struct gpgx_audio_service_kind_v1 local_kinds[3];
  struct gpgx_audio_service_hook_v1 local_hooks[8];
  struct gpgx_audio_service_hook_v1 ordered_hooks[8];
  struct gpgx_audio_snapshot_range_v1 local_range;
  uint8_t local_mask[8192], rom_page[65536];
  uint8_t previous;
  uint16_t parent_token, child_token;
  uint32_t before, count, overflow, drained;
  struct gpgx_audio_trace_event drained_events[16];
  memset(&local_config,0,sizeof(local_config));
  memset(local_kinds,0,sizeof(local_kinds));
  memset(local_hooks,0,sizeof(local_hooks));
  memset(&local_range,0,sizeof(local_range));
  memset(local_mask,0,sizeof(local_mask));
  memset(rom_page,0,sizeof(rom_page));
  local_config.magic=0x31544147; local_config.abi_version=3;
  local_config.struct_size=64; local_config.kind_size=16;
  local_config.hook_size=32; local_config.range_size=16;
  local_config.event_size=32; local_config.max_depth=8;
  local_config.max_opcode_bytes=8; local_config.reset_service_kind=1;
  local_config.watch_mask_bytes=8192; local_config.kind_count=3;
  local_config.hook_count=8; local_config.range_count=1;
  local_config.snapshot_bytes_total=7; local_config.event_capacity=65536;
  local_config.max_service_tokens_per_frame=65535;
  local_config.max_continuation_frames=1;
  local_kinds[0].kind_id=1; local_kinds[0].cancellation_range_count=1;
  local_kinds[1].kind_id=2; local_kinds[1].flags=KIND_ALLOW_CHILDREN;
  local_kinds[1].cancellation_range_count=1;
  local_kinds[2].kind_id=4;
  local_kinds[2].flags=KIND_ALLOW_CONTINUATION|KIND_ALLOW_CHILDREN;
  local_kinds[2].continuation_frame_limit=1;
  local_kinds[2].cancellation_range_count=1;
  local_range.range_id=1; local_range.start=0x100; local_range.length=1;
  local_hooks[0].hook_token=1; local_hooks[0].action=ACTION_PUSH_BEGIN;
  local_hooks[0].cpu=GPGX_AUDIO_TRACE_CPU_Z80; local_hooks[0].service_kind=2;
  local_hooks[0].opcode_length=1; local_hooks[0].opcode[0]=0xa0;
  local_hooks[1].hook_token=3; local_hooks[1].action=ACTION_POP_END_AT_PC;
  local_hooks[1].cpu=GPGX_AUDIO_TRACE_CPU_Z80; local_hooks[1].pc=1;
  local_hooks[1].expected_active_kind=2; local_hooks[1].range_count=1;
  local_hooks[1].opcode_length=1; local_hooks[1].opcode[0]=0xa1;
  local_hooks[2]=local_hooks[1]; local_hooks[2].hook_token=4;
  local_hooks[2].action=ACTION_POP_DIRECT_PARENT_PROMOTE_TOP;
  local_hooks[2].expected_active_kind=4; local_hooks[2].service_kind=2;
  local_hooks[3].hook_token=2; local_hooks[3].action=ACTION_PUSH_BEGIN;
  local_hooks[3].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[3].pc=0x100;
  local_hooks[3].service_kind=4; local_hooks[3].expected_active_kind=2;
  local_hooks[3].opcode_length=1; local_hooks[3].opcode[0]=0xb0;
  local_hooks[4].hook_token=5; local_hooks[4].action=ACTION_POP_END_AT_PC;
  local_hooks[4].cpu=GPGX_AUDIO_TRACE_CPU_M68K; local_hooks[4].pc=0x102;
  local_hooks[4].expected_active_kind=4; local_hooks[4].range_count=1;
  local_hooks[4].opcode_length=1; local_hooks[4].opcode[0]=0xb1;
  local_hooks[5]=local_hooks[0]; local_hooks[5].hook_token=6;
  local_hooks[5].expected_active_kind=4;
  local_hooks[6]=local_hooks[3]; local_hooks[6].hook_token=7;
  local_hooks[6].expected_active_kind=0;
  local_hooks[7]=local_hooks[4]; local_hooks[7].hook_token=8;
  local_hooks[7].action=ACTION_POP_DIRECT_PARENT_PROMOTE_TOP;
  local_hooks[7].service_kind=4; local_hooks[7].expected_active_kind=2;
  ordered_hooks[0]=local_hooks[0]; ordered_hooks[1]=local_hooks[5];
  ordered_hooks[2]=local_hooks[1]; ordered_hooks[3]=local_hooks[2];
  ordered_hooks[4]=local_hooks[3]; ordered_hooks[5]=local_hooks[6];
  ordered_hooks[6]=local_hooks[4]; ordered_hooks[7]=local_hooks[7];
  memcpy(local_hooks,ordered_hooks,sizeof(local_hooks));
  local_mask[0]=3; zram[0]=0xa0; zram[1]=0xa1; zram[0x100]=0x5a;
  rom_page[0x100^1]=0xb0; rom_page[0x102^1]=0xb1;
  memset(&m68k,0,sizeof(m68k)); m68k.memory_map[0].base=rom_page;

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  local_config.abi_version=2;
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_ABI_OR_CONFIG_LIMIT);
  local_config.abi_version=3;
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  parent_token=trace_stack[0].token;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  child_token=trace_stack[1].token;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  assert(trace_depth==1 && trace_stack[0].token==child_token
    && trace_stack[0].parent==0 && trace_stack[0].depth==0);
  assert(trace_event_count_value==7);
  assert(trace_events[2].kind==EVENT_SNAPSHOT_BEGIN
    && trace_events[2].service_token==parent_token);
  assert(trace_events[5].kind==EVENT_SERVICE_END
    && trace_events[5].service_token==parent_token);
  assert(trace_events[6].kind==EVENT_SERVICE_PROMOTE
    && trace_events[6].service_token==child_token
    && trace_events[6].parent_token==0 && trace_events[6].depth==0);
  previous=gpgx_audio_trace_enter_cpu(GPGX_AUDIO_TRACE_CPU_M68K);
  gpgx_audio_trace_fm_write(0,0x2a);
  gpgx_audio_trace_leave_cpu(previous);
  assert(trace_events[7].service_token==child_token
    && trace_events[7].parent_token==0 && trace_events[7].depth==0);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_depth==0);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  parent_token=trace_stack[0].token;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  child_token=trace_stack[1].token;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_depth==1 && trace_stack[0].token==child_token
    && trace_stack[0].parent==0 && trace_stack[0].depth==0);
  assert(trace_events[5].kind==EVENT_SERVICE_END
    && trace_events[5].service_token==parent_token);
  assert(trace_events[6].kind==EVENT_SERVICE_PROMOTE
    && trace_events[6].service_token==child_token);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  assert(trace_depth==0);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  child_token=trace_stack[0].token;
  assert(gpgx_audio_trace_end_frame()==TRACE_OK);
  assert(gpgx_audio_trace_event_count(&count,&overflow)==TRACE_OK
    && count<=16 && overflow==0);
  assert(gpgx_audio_trace_drain(drained_events,16,&drained)==TRACE_OK
    && drained==count);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  assert(trace_depth==1 && trace_stack[0].token==child_token
    && trace_stack[0].parent==0 && trace_stack[0].depth==0
    && trace_stack[0].carried_frames==1);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x102);
  assert(trace_depth==0);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  child_token=trace_stack[0].token; before=trace_event_count_value;
  gpgx_audio_trace_reset_begin(0);
  assert(trace_depth==1 && trace_stack[0].kind==1);
  assert(trace_events[before].kind==EVENT_RESET_BEGIN);
  assert(trace_events[before+4].kind==EVENT_SERVICE_END
    && trace_events[before+4].service_token==child_token
    && trace_events[before+4].parent_token==0
    && trace_events[before+4].depth==0);
  gpgx_audio_trace_reset_end(0);
  assert(trace_depth==0);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  before=trace_event_count_value;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  assert(trace_event_count_value==before+4 && trace_depth==0);
  assert(trace_events[before+3].kind==EVENT_SERVICE_END);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);

  assert(gpgx_audio_trace_disable()==TRACE_OK);
  assert(gpgx_audio_trace_configure(&local_config,local_mask,local_kinds,
    local_hooks,&local_range)==TRACE_OK);
  assert(gpgx_audio_trace_begin_frame()==TRACE_OK);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,0);
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_M68K,0x100);
  parent_token=trace_stack[0].token; child_token=trace_stack[1].token;
  trace_event_count_value=GPGX_AUDIO_TRACE_EVENT_CAPACITY-4;
  trace_omitted_count=0;
  gpgx_audio_trace_instruction(GPGX_AUDIO_TRACE_CPU_Z80,1);
  assert(trace_event_count_value==GPGX_AUDIO_TRACE_EVENT_CAPACITY-4);
  assert(trace_omitted_count==5 && trace_depth==2
    && trace_stack[0].token==parent_token && trace_stack[1].token==child_token);
  assert(gpgx_audio_trace_end_frame()==TRACE_OVERFLOW);
  assert(gpgx_audio_trace_abort_frame()==TRACE_OK);
}

int main(void)
{
  struct gpgx_audio_trace_event vector;
  const uint8_t *bytes=(const uint8_t *)&vector;
  assert(sizeof(config)==64 && sizeof(kind)==16 && sizeof(hooks[0])==32
    && sizeof(range)==16 && sizeof(struct gpgx_audio_trace_event)==32);
  memset(&vector,0,sizeof(vector)); vector.ordinal=0x04030201u; vector.service_token=0x0605;
  vector.pc=0x0c0b0a09u; vector.payload[0]=0x18;
  assert(bytes[0]==1 && bytes[3]==4 && bytes[4]==5 && bytes[5]==6
    && bytes[8]==9 && bytes[11]==12 && bytes[24]==0x18);
  config_negatives(); phases_copy_and_drain(); chip_port_vectors();
  first_fault_is_read_only_and_session_scoped();
  prearm_filter_and_publication_epoch();
  m68k_proof_and_stack_bounds(); overflow_and_reset_bounds();
  guarded_tail_continuation_and_failures();
  suspended_parent_continuation_exposure();
  typed_nested_multiple_exits_and_distinct_reset();
  same_pc_different_kind_tail_chain();
  cpu_index_and_same_pc_alternatives();
  conditional_m68k_return_predicate();
  observation_marker_alternatives();
  direct_parent_close_promotes_top_child();
  return 0;
}
