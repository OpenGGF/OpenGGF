/*
 * blip_buf 1.1.0. http://www.slack.net/~ant/
 * Library Copyright (C) 2003-2009 Shay Green.
 * Modified for Genesis Plus GX by EkeEke.
 * Java port Copyright (C) 2026 the OpenGGF contributors.
 * Modified 2026-10 by the OpenGGF contributors: translated from C to Java;
 * stereo configuration only; state copy helpers and value equality added.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version. This library is distributed in the hope
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * module; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/**
 * Java port of Shay Green's blip_buf 1.1.0 band-limited delta buffer, in the
 * stereo form Genesis Plus GX ships.
 *
 * <p>The port derives from exactly one source: {@code core/sound/blip_buf.c}
 * (sha256 {@code f807ee6711663d67f15e38b82c0f8445375b13ec3a265f34c510fdcc0fc80461})
 * and {@code blip_buf.h} (sha256
 * {@code 367414a873407fad24cf0207429cf71e62aed080a278f40884b157f25930428e})
 * of the Genesis Plus GX tree inside BizHawk commit
 * {@code 427556b5ef3ac437eba754d90c5e7e9096c9a8df} (the BizHawk source pinned
 * by TraceChaser's GPGX audio observer). That file is blip_buf 1.1.0 as
 * modified for Genesis Plus GX by EkeEke (assertions disabled, multiple
 * time-frame support fixed, stereo buffers added). Every ported function
 * cites its {@code blip_buf.c} line range. The package is a pure buffer: it
 * has no dependency on the rest of the engine and no static mutable state.
 * It is used only by the opt-in native-compatible PCM output stage
 * ({@link com.openggf.audio.synth.NativeCompatiblePcmOutput}); ordinary
 * presentation does not use it.
 *
 * <h2>NOTICE: licensing</h2>
 * OpenGGF as a whole is distributed under the GNU General Public License,
 * version 3 only, with the OpenGGF Mod Exception (see {@code LICENSE} and
 * {@code LICENSES/OpenGGF-Mod-Exception.txt}). The files in this
 * package are a derivative work of blip_buf, Copyright (C) 2003-2009 Shay
 * Green, as modified for Genesis Plus GX, and remain licensed under the GNU
 * Lesser General Public License, version 2.1 or (at your option) any later
 * version ({@code LGPL-2.1-or-later}); the LGPL text is provided at
 * {@code LICENSES/LGPL-2.1.txt}. The files stay under that licence and may be
 * extracted and reused under it on their own; the per-file LGPL headers and
 * the upstream copyright notice are retained for that purpose. They are not
 * converted to the GPL through LGPL-2.1 section 3: the rest of OpenGGF uses
 * this package as a library, and distributions meet the LGPL's conditions for
 * it, including section 6. Modifications relative to the source are
 * recorded in each file header (LGPL-2.1 section 2(b)): translation into
 * Java, the stereo-only configuration, and the state copy helpers and value
 * equality. The root {@code NOTICE.md} carries the same notice for
 * distributions.
 */
package com.openggf.audio.synth.blip;
