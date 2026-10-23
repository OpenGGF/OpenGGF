/* CD-06, 2026-10-23: observe actual source-owned pcm_run ingress/egress and
 * clipped integers BEFORE config.pcm_volume/blip/resampling. Inputs are read-only
 * native scalars and RAM. Every mutable diagnostic byte is in pcm_observer.invis.
 * No handler, CPU runner, callback or gameplay write is performed here. */
#include "shared.h"
#include <emulibc.h>
#include <stdint.h>
#include "pcm_native_observer.h"

#define CAPACITY 4096
#define BLOCKS 8
#define SAMPLES 4096
#define PCM scd.pcm_hw
typedef struct { uint32_t w[56]; } pcm_state;
typedef struct { uint32_t w[8]; } pcm_sample;
typedef struct { uint32_t w[16]; } pcm_summary;
typedef struct {
  uint32_t header[16];
  pcm_state pre, post;
  uint8_t ram_pre[65536], ram_post[65536];
  pcm_sample samples[SAMPLES];
} pcm_block;
typedef char check_state[(sizeof(pcm_state) == 224) ? 1 : -1];
typedef char check_block[(sizeof(pcm_block) == 262656) ? 1 : -1];
typedef char check_width[(sizeof(int) == 4 && sizeof(PCM.chan[0].addr) == 4 && (-1 >> 5) == -1) ? 1 : -1];
static struct {
  uint32_t enabled, limit, count, fault, dropped, call, ordinal, selected_count;
  uint32_t selected[BLOCKS], ready, current_summary, current_block, loop_mask;
  pcm_summary summaries[CAPACITY];
  pcm_block blocks[BLOCKS];
} pcm_observer ECL_INVISIBLE;

static uint32_t bank_index(void)
{
  uintptr_t p = (uintptr_t)PCM.bank, base = (uintptr_t)PCM.ram;
  if (p < base || p >= base + sizeof(PCM.ram) || ((p-base) & 4095)) {
    pcm_observer.fault |= 2; return UINT32_MAX;
  }
  return (p-base) >> 12;
}
static void snapshot(pcm_state *s)
{
  unsigned n;
  for (n=0; n<8; n++) {
    const chan_t *c = &PCM.chan[n]; uint32_t *w = &s->w[n*6];
    w[0]=c->addr; w[1]=c->st; w[2]=c->ls.w; w[3]=c->fd.w; w[4]=c->env; w[5]=c->pan;
  }
  s->w[48]=PCM.enabled; s->w[49]=PCM.status; s->w[50]=PCM.index; s->w[51]=bank_index();
  s->w[52]=PCM.out[0]; s->w[53]=PCM.out[1]; s->w[54]=PCM.cycles; s->w[55]=config.pcm_volume;
}
void pcm_observe_frame(void) { if (pcm_observer.enabled) pcm_observer.call++; }
void pcm_observe_begin(uint32_t length)
{
  uint32_t n, *w;
  if (!pcm_observer.enabled) return;
  pcm_observer.ordinal++;
  pcm_observer.current_summary = UINT32_MAX; pcm_observer.current_block = UINT32_MAX;
  pcm_observer.loop_mask = 0;
  if (pcm_observer.count >= pcm_observer.limit) {
    pcm_observer.fault |= 1; pcm_observer.dropped++; return;
  }
  pcm_observer.current_summary = pcm_observer.count++;
  w = pcm_observer.summaries[pcm_observer.current_summary].w;
  memset(w, 0, sizeof(pcm_summary));
  w[0]=pcm_observer.ordinal; w[1]=pcm_observer.call; w[2]=length;
  w[3]=PCM.enabled; w[4]=PCM.status; w[5]=PCM.index; w[6]=bank_index();
  w[7]=PCM.cycles; w[9]=PCM.out[0]; w[10]=PCM.out[1]; w[15]=config.pcm_volume;
  for (n=0; n<pcm_observer.selected_count; n++) if (pcm_observer.selected[n] == pcm_observer.ordinal) {
    pcm_block *b = &pcm_observer.blocks[n];
    if (!length || length > SAMPLES || config.pcm_volume != 100) { pcm_observer.fault |= 4; return; }
    pcm_observer.current_block=n;
    memset(b, 0, sizeof(*b));
    b->header[0]=0x424d4350; b->header[1]=1; b->header[2]=pcm_observer.ordinal;
    b->header[3]=pcm_observer.call; b->header[4]=length;
    b->header[5]=sizeof(pcm_state); b->header[6]=sizeof(PCM.ram); b->header[7]=sizeof(pcm_sample);
    b->header[9]=1; /* phase 1 = owning loop, after limiter, before host volume */
    snapshot(&b->pre); memcpy(b->ram_pre, PCM.ram, sizeof(PCM.ram));
  }
}
void pcm_observe_loop(uint32_t channel)
{
  if (!pcm_observer.enabled || pcm_observer.current_summary == UINT32_MAX) return;
  pcm_observer.summaries[pcm_observer.current_summary].w[14]++;
  pcm_observer.loop_mask |= 1U << channel;
}
void pcm_observe_sample(uint32_t index, int l, int r, int pl, int pr)
{
  if (!pcm_observer.enabled || pcm_observer.current_summary == UINT32_MAX) return;
  if (l || r) pcm_observer.summaries[pcm_observer.current_summary].w[13]++;
  if (pcm_observer.current_block != UINT32_MAX) {
    pcm_block *b = &pcm_observer.blocks[pcm_observer.current_block];
    if (index >= b->header[4] || index >= SAMPLES || index != b->header[8]) { pcm_observer.fault |= 8; return; }
    b->samples[index] = (pcm_sample){{index, l, r, l-pl, r-pr, 1, pcm_observer.loop_mask, 0}};
    b->header[8]++;
  }
  pcm_observer.loop_mask=0;
}
void pcm_observe_disabled(uint32_t length, int pl, int pr)
{
  uint32_t n;
  if (!pcm_observer.enabled || pcm_observer.current_block == UINT32_MAX) return;
  /* The actual disabled branch submits at most one mute at offset zero.
   * Remaining logical sample positions are zero holds, not invented loop work. */
  for (n=0; n<length; n++) {
    pcm_block *b=&pcm_observer.blocks[pcm_observer.current_block];
    b->samples[n]=(pcm_sample){{n, 0, 0, n ? 0 : -pl, n ? 0 : -pr, n ? 0 : !!(pl|pr), 0, 0}};
    b->header[8]++;
  }
}
void pcm_observe_end(void)
{
  uint32_t *w;
  if (!pcm_observer.enabled || pcm_observer.current_summary == UINT32_MAX) return;
  w=pcm_observer.summaries[pcm_observer.current_summary].w;
  w[8]=PCM.cycles; w[11]=PCM.out[0]; w[12]=PCM.out[1];
  if (pcm_observer.current_block != UINT32_MAX) {
    pcm_block *b=&pcm_observer.blocks[pcm_observer.current_block];
    snapshot(&b->post); memcpy(b->ram_post, PCM.ram, sizeof(PCM.ram));
    if (b->header[8] != b->header[4]) pcm_observer.fault |= 8;
    b->header[10]=1; pcm_observer.ready |= 1U << pcm_observer.current_block;
  }
}
ECL_EXPORT uint32_t pcm_native_abi(void) { return 1; }
ECL_EXPORT uint32_t pcm_native_summary_bytes(void) { return sizeof(pcm_summary); }
ECL_EXPORT uint32_t pcm_native_block_bytes(void) { return sizeof(pcm_block); }
ECL_EXPORT uint32_t pcm_native_capacity(void) { return CAPACITY; }
ECL_EXPORT uint32_t pcm_native_fault(void) { return pcm_observer.fault; }
ECL_EXPORT uint32_t pcm_native_dropped(void) { return pcm_observer.dropped; }
ECL_EXPORT uint32_t pcm_native_count(void) { return pcm_observer.count; }
ECL_EXPORT uint32_t pcm_native_ready(void) { return pcm_observer.ready; }
ECL_EXPORT int pcm_native_configure(uint32_t enabled, uint32_t limit, const uint32_t *ordinals, uint32_t count)
{
  uint32_t n;
  if (enabled > 1 || limit < 1 || limit > CAPACITY || count > BLOCKS || (count && !ordinals)) return -1;
  for (n=0; n<count; n++) if (!ordinals[n] || ordinals[n] > 10000000 || (n && ordinals[n] <= ordinals[n-1])) return -1;
  memset(&pcm_observer, 0, sizeof(pcm_observer));
  pcm_observer.enabled=enabled; pcm_observer.limit=limit; pcm_observer.selected_count=count;
  for (n=0; n<count; n++) pcm_observer.selected[n]=ordinals[n];
  return 0;
}
ECL_EXPORT int pcm_native_poll(void *destination, uint32_t bytes)
{
  uint32_t count=pcm_observer.count;
  if (!destination || bytes != CAPACITY * sizeof(pcm_summary)) return -1;
  memcpy(destination, pcm_observer.summaries, count*sizeof(pcm_summary)); pcm_observer.count=0;
  return count;
}
ECL_EXPORT int pcm_native_copy_block(uint32_t index, void *destination, uint32_t bytes)
{
  if (index >= BLOCKS || !destination || bytes != sizeof(pcm_block)) return -1;
  if (!(pcm_observer.ready & (1U << index))) return 0;
  memcpy(destination, &pcm_observer.blocks[index], sizeof(pcm_block)); return 1;
}
