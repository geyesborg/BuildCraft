/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteOrder;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.lib.client.model.MutableVertex;

/** Writes whole quads into a {@link DefaultVertexFormats#BLOCK} buffer with one addVertexData: the same bytes
 * pos/color/tex/lightmap/endVertex write per vertex, without their per-element bookkeeping and bounds checks. */
@SideOnly(Side.CLIENT)
public final class BlockQuadPacker {
    public static final int VERTEX_INTS = 7;
    public static final int QUAD_INTS = 4 * VERTEX_INTS;

    private static final MethodHandle X_OFFSET = field("field_179004_l", "xOffset");
    private static final MethodHandle Y_OFFSET = field("field_179005_m", "yOffset");
    private static final MethodHandle Z_OFFSET = field("field_179002_n", "zOffset");
    private static final MethodHandle NO_COLOR = field("field_78939_q", "noColor");
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final ThreadLocal<int[]> QUAD = ThreadLocal.withInitial(() -> new int[QUAD_INTS]);

    private BlockQuadPacker() {}

    /** Getter for a BufferBuilder field: production (SRG) name, else the development (MCP) one */
    private static MethodHandle field(String srg, String mcp) {
        Field f;
        try {
            f = BufferBuilder.class.getDeclaredField(srg);
        } catch (NoSuchFieldException e) {
            try {
                f = BufferBuilder.class.getDeclaredField(mcp);
            } catch (NoSuchFieldException e2) {
                throw new IllegalStateException("BufferBuilder has neither " + srg + " nor " + mcp, e2);
            }
        }
        f.setAccessible(true);
        try {
            return MethodHandles.lookup().unreflectGetter(f);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("BufferBuilder." + mcp + " is not readable", e);
        }
    }

    /** True when bb takes packed quads: the BLOCK format, with colours not switched off (noColor) */
    public static boolean canPack(BufferBuilder bb) {
        try {
            return bb.getVertexFormat() == DefaultVertexFormats.BLOCK && !(boolean) NO_COLOR.invokeExact(bb);
        } catch (Throwable t) {
            throw new IllegalStateException("Reading BufferBuilder.noColor failed", t);
        }
    }

    public static double xOffset(BufferBuilder bb) {
        try {
            return (double) X_OFFSET.invokeExact(bb);
        } catch (Throwable t) {
            throw new IllegalStateException("Reading BufferBuilder.xOffset failed", t);
        }
    }

    public static double yOffset(BufferBuilder bb) {
        try {
            return (double) Y_OFFSET.invokeExact(bb);
        } catch (Throwable t) {
            throw new IllegalStateException("Reading BufferBuilder.yOffset failed", t);
        }
    }

    public static double zOffset(BufferBuilder bb) {
        try {
            return (double) Z_OFFSET.invokeExact(bb);
        } catch (Throwable t) {
            throw new IllegalStateException("Reading BufferBuilder.zOffset failed", t);
        }
    }

    /** One vertex as renderAsBlock writes it, translated by (xo, yo, zo) like BufferBuilder.pos */
    public static void packVertex(int[] out, int o, MutableVertex v, double xo, double yo, double zo) {
        out[o] = Float.floatToRawIntBits((float) (v.position_x + xo));
        out[o + 1] = Float.floatToRawIntBits((float) (v.position_y + yo));
        out[o + 2] = Float.floatToRawIntBits((float) (v.position_z + zo));
        // color(r, g, b, a) bytes: the same int in either byte order
        out[o + 3] = (v.colour_r & 0xFF) | (v.colour_g & 0xFF) << 8 | (v.colour_b & 0xFF) << 16 | (v.colour_a & 0xFF) << 24;
        out[o + 4] = Float.floatToRawIntBits(v.tex_u);
        out[o + 5] = Float.floatToRawIntBits(v.tex_v);
        // lightmap(sky, block) shorts: block first
        int block = (v.light_block << 4) & 0xFFFF, sky = (v.light_sky << 4) & 0xFFFF;
        out[o + 6] = LITTLE_ENDIAN ? block | sky << 16 : block << 16 | sky;
    }

    /** Appends the quad; bb must pass {@link #canPack} */
    public static void putQuad(BufferBuilder bb, MutableVertex a, MutableVertex b, MutableVertex c, MutableVertex d) {
        double xo = xOffset(bb), yo = yOffset(bb), zo = zOffset(bb);
        int[] quad = QUAD.get();
        packVertex(quad, 0, a, xo, yo, zo);
        packVertex(quad, VERTEX_INTS, b, xo, yo, zo);
        packVertex(quad, 2 * VERTEX_INTS, c, xo, yo, zo);
        packVertex(quad, 3 * VERTEX_INTS, d, xo, yo, zo);
        bb.addVertexData(quad);
    }
}
