#!/usr/bin/env bash
# Source before launching s2_plc_timing_probe.lua against Sonic 2 World REV01.
# OGGF_PLC_PROBE_OUTPUT must name a new file; the Lua probe refuses replacement.
: "${OGGF_PLC_PROBE_OUTPUT:?set a new diagnostic output path before sourcing}"

# BizHawk mainmemory offsets, not 24-bit 68K CPU addresses.
export OGGF_PLC_BUFFER_RAM=0xF680
export OGGF_PLC_DEST_RAM=0xF684
export OGGF_PLC_LEFT_RAM=0xF6F8
export OGGF_PLC_GAME_MODE_RAM=0xF600
export OGGF_PLC_INTERRUPT_HANDLER_RAM=0xF62A
export OGGF_PLC_LAG_HANDLER=0x00

export OGGF_PLC_ADD_ENTRY=0x00161E
export OGGF_PLC_ADD_POST=0x00164A
export OGGF_PLC_REPLACE_BEGIN=0x001650
export OGGF_PLC_REPLACE_POST=0x001676
export OGGF_PLC_CLEAR_BEGIN=0x00167C
export OGGF_PLC_CLEAR_POST=0x001688
export OGGF_PLC_PREPARE_BEGIN=0x001696
export OGGF_PLC_PREPARE_END=0x0016DE
export OGGF_PLC_FULL_SERVICE_PRE=0x0016E8
export OGGF_PLC_PARTIAL_SERVICE_POST=0x001778
export OGGF_PLC_SMALL_SERVICE_PRE=0x001702
export OGGF_PLC_POP_PRE=0x00177A
export OGGF_PLC_POP_POST=0x001788
export OGGF_PLC_VINT_DISPATCH=0x000408
export OGGF_PLC_HBLANK_DEFERRED_ENTRY=0x001072
export OGGF_PLC_CONSUMER_HOOKS='level_title_card@0x40FE,special_stage_results_exit@0x53EC,two_player_results@0x7ED8,game_over_init@0x13F88,level_results_init@0x140AC,special_stage_results_init@0x14406,arz_boss_init@0x30494'
