"""Private owner-ledger ABI 1; raw clock domains never form a global clock.

2026-10-23 CD-02. Each event is exactly 64 little-endian unsigned words.
Signed residuals are decoded explicitly by name. Epochs partition native
rebases/resets; sequence orders retained observations, not guest execution.
"""
FIELDS = '''sequence_lo sequence_hi call kind cpu width address value
reset_epoch scheduler_epoch cd_epoch main_epoch pcm_epoch gfx_operation
m68k_cycles s68k_cycles scd_cycles gfx_cycles gfx_per_line cdc_cycle0
cdc_cycle1 pcm_cycles cdd_phase3 cdd_latency cdd_lba cdd_status cdd_pending
word_mode dmna gfx58 gfx5a gfx5c gfx5e gfx60 gfx62 gfx64 gfx66 trace_offset
map_offset buffer_start cdc_ifstat cdc_ifctrl cdc_dbc cdc_dma_active
irq_pending timer stopwatch main_map_kind sub_map_kind main_read_handler
sub_read_handler aux0 aux1 reserved53 reserved54 reserved55 reserved56
reserved57 reserved58 reserved59 reserved60 reserved61 reserved62 reserved63'''.split()
SIGNED = {'m68k_cycles', 's68k_cycles', 'cdc_cycle0', 'cdc_cycle1',
          'cdd_lba', 'timer', 'stopwatch'}
KINDS = dict(enumerate('''frame_begin frame_end scheduler_reset_before
scheduler_reset_after cd_rebase_before cd_rebase_after main_rebase_before
main_rebase_after pcm_service_begin pcm_counter_reset_before
pcm_counter_reset_after reset_before reset_after write_before_handler
write_after_handler wordram_handler_commit gfx_start_initialized
gfx_busy_clear_before_render gfx_final_lines_rendered cdd_tick_before
cdd_tick_after cdd_command_before cdd_command_after cdd_seek_applied
cdd_latency_exhausted cdc_trigger_before cdc_trigger_after cdc_dma_complete
timer_expired_before_reload timer_irq_pending irq_ack_before irq_ack_after'''.split(), 1))
assert len(FIELDS) == 64
assert len(set(FIELDS)) == 64
DOMAINS = {
    'm68k_cycles': 'VDP/main master clocks; main_epoch + reset_epoch',
    's68k_cycles': 'CD master clocks; cd_epoch + reset_epoch',
    'scd_cycles': 'CD scheduler accumulator (may trail CPU); scheduler_epoch + reset_epoch',
    'gfx_cycles': 'unsigned CD graphics boundary; cd_epoch + reset_epoch + gfx_operation; inactive wraps retained',
    'cdc_cycle0': 'signed CD transfer deadline/residual; cd_epoch + reset_epoch',
    'cdc_cycle1': 'signed CD decoder deadline/residual; cd_epoch + reset_epoch',
    'pcm_cycles': 'CD PCM sample-service progress; pcm_epoch + reset_epoch',
    'cdd_phase3': 'three-scaled CD phase accumulator modulo 2000000; reset_epoch; tick phases explicit',
    'cdd_latency': 'remaining native CDD service ticks, no physical drive claim',
    'timer': 'CD timer residual; reload uses native register * 1536',
    'stopwatch': 'signed CD stopwatch residual; cd_epoch + reset_epoch',
    'call': 'native gpgx_advance envelope since configure, not gameplay/V5/BK2 frames',
    'aux0': 'kind-specific source argument: write CPU clock, rebase amount, IRQ level, service argument',
}
