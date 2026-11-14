/* CD-02 device observer, 2026-10-23. Inputs are pinned GPGX headers. */
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include "types.h"
#include "cdStream.h"
#include "scd.h"
#include "device_observer_stream.h"

#define FIELD(label, member, kind) printf("%s\t%zu\t%zu\t%s\n", label, offsetof(cd_hw_t, member), sizeof(((cd_hw_t *)0)->member), kind)
#define F(member) FIELD(#member, member, "u")
#define S(member) FIELD(#member, member, "s")

int main(void)
{
    printf("sizeof_cd_hw\t0\t%zu\tmeta\n", sizeof(cd_hw_t));
    FIELD("bootrom", bootrom, "area");
    FIELD("prg_ram", prg_ram, "area");
    FIELD("word_ram_0", word_ram[0], "area");
    FIELD("word_ram_1", word_ram[1], "area");
    FIELD("word_ram_2M", word_ram_2M, "area");
    FIELD("bram", bram, "area");
    F(cycles); F(cycles_per_line); S(stopwatch); S(timer);
    F(pending); F(dmna); F(type);
    F(gfx_hw.cycles); F(gfx_hw.cyclesPerLine); F(gfx_hw.dotMask);
    FIELD("gfx_hw.tracePtr", gfx_hw.tracePtr, "wordram_ptr");
    FIELD("gfx_hw.mapPtr", gfx_hw.mapPtr, "wordram_ptr");
    F(gfx_hw.stampShift); F(gfx_hw.mapShift); F(gfx_hw.bufferOffset); F(gfx_hw.bufferStart);
    F(cdc_hw.ifstat); F(cdc_hw.ifctrl); F(cdc_hw.dbc.w); F(cdc_hw.dac.w);
    F(cdc_hw.pt.w); F(cdc_hw.wa.w); S(cdc_hw.cycles[0]); S(cdc_hw.cycles[1]);
    F(cdc_hw.ar_mask); F(cdc_hw.irq);
    FIELD("cdc_hw.dma_active", cdc_hw.dma_w, "nonnull");
    FIELD("cdc_hw.dma_halted", cdc_hw.halted_dma_w, "nonnull");
    F(cdd_hw.cycles); F(cdd_hw.latency); S(cdd_hw.loaded); S(cdd_hw.index);
    S(cdd_hw.lba); S(cdd_hw.scanOffset); F(cdd_hw.fader[0]); F(cdd_hw.fader[1]);
    F(cdd_hw.status); F(cdd_hw.pending); F(cdd_hw.sectorSize);
    S(cdd_hw.toc.last); S(cdd_hw.toc.end); S(cdd_hw.audio[0]); S(cdd_hw.audio[1]);
    FIELD("cdd_hw.data_stream", cdd_hw.toc.tracks[0].fd, "stream_ptr");
    FIELD("cdd_hw.audio_stream", cdd_hw.toc.tracks[1].fd, "stream_ptr");
    FIELD("pcm_hw.bank", pcm_hw.bank, "pcm_ptr");
    FIELD("pcm_hw.ram", pcm_hw.ram, "area");
    F(pcm_hw.enabled); F(pcm_hw.status); F(pcm_hw.index); F(pcm_hw.cycles);
    S(pcm_hw.out[0]); S(pcm_hw.out[1]);
    for (int i = 0; i < 8; i++) {
        char key[80];
#define CH(member) snprintf(key, sizeof(key), "pcm_hw.chan[%d].%s", i, #member); FIELD(key, pcm_hw.chan[i].member, "u")
        CH(addr); CH(st); CH(ls.w); CH(fd.w); CH(env); CH(pan);
    }
    for (int i = 0; i <= 0x68; i += 2) {
        char key[40]; snprintf(key, sizeof(key), "regs.%02x", i);
        FIELD(key, regs[i / 2].w, "u");
    }
    for (int i = 0; i < 2; i++) {
        char key[80];
#define STREAM(member) snprintf(key, sizeof(key), "%s.%s", i ? "audio_stream" : "data_stream", #member); printf("%s\t%zu\t%zu\t%s\n", key, offsetof(struct cdStream_t, member), sizeof(((struct cdStream_t *)0)->member), i ? "audio_stream" : "data_stream")
        STREAM(sector_size); STREAM(num_sectors); STREAM(current_sector); STREAM(current_offset); STREAM(end_offset);
    }
    return 0;
}
