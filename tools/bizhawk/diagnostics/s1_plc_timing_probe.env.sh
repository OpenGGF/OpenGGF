#!/usr/bin/env bash
# Source before launching s1_plc_timing_probe.lua against Sonic 1 World REV01.
# OGGF_PLC_PROBE_OUTPUT must name a new file; the Lua probe refuses replacement.
: "${OGGF_PLC_PROBE_OUTPUT:?set a new diagnostic output path before sourcing}"

# BizHawk mainmemory offsets, not 24-bit 68K CPU addresses.
export OGGF_PLC_BUFFER_RAM=0xF680
export OGGF_PLC_DEST_RAM=0xF684
export OGGF_PLC_LEFT_RAM=0xF6F8
export OGGF_PLC_GAME_MODE_RAM=0xF600
export OGGF_PLC_INTERRUPT_HANDLER_RAM=0xF62A
export OGGF_PLC_LAG_HANDLER=0x00

export OGGF_PLC_ADD_ENTRY=0x001578
export OGGF_PLC_ADD_POST=0x0015A4
export OGGF_PLC_REPLACE_BEGIN=0x0015AA
export OGGF_PLC_REPLACE_POST=0x0015D0
export OGGF_PLC_CLEAR_BEGIN=0x0015DA
export OGGF_PLC_CLEAR_POST=0x0015E2
export OGGF_PLC_PREPARE_BEGIN=0x0015F0
export OGGF_PLC_PREPARE_END=0x001638
export OGGF_PLC_FULL_SERVICE_PRE=0x001642
export OGGF_PLC_PARTIAL_SERVICE_POST=0x0016D2
export OGGF_PLC_SMALL_SERVICE_PRE=0x00165C
export OGGF_PLC_POP_PRE=0x0016D4
export OGGF_PLC_POP_POST=0x0016E2
export OGGF_PLC_VINT_DISPATCH=0x000B14
export OGGF_PLC_HBLANK_DEFERRED_ENTRY=0x00119E
export OGGF_PLC_CONSUMER_HOOKS='level_title_card@0x3936,results_card@0xCC20,game_over_card@0xCB56,special_results_card@0xCE60,special_results_exit@0x4882,final_zone_boss@0x1A640'
