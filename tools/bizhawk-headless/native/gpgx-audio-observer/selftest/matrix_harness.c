#include <assert.h>
#include <stdint.h>
#include <string.h>
#include "shared.h"

struct selftest_m68k m68k;
uint8_t zram[0x2000];
#include "audio_trace.c"

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
  BAD(magic,0); BAD(abi_version,2); BAD(struct_size,63); BAD(kind_size,15); BAD(hook_size,31);
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
  config.max_continuation_frames=5; assert(configure()==TRACE_ABI_OR_CONFIG_LIMIT);
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
  m68k_proof_and_stack_bounds(); overflow_and_reset_bounds();
  guarded_tail_continuation_and_failures();
  typed_nested_multiple_exits_and_distinct_reset();
  same_pc_different_kind_tail_chain();
  return 0;
}
