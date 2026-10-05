/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.gl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Scissor and stencil state, which Minecraft 1.12's GlStateManager has no methods for. With gl46core installed this goes
 * through its public RasterState API (it tracks the state and flushes its batched draws first); otherwise straight to
 * OpenGL. Both are bound as method handles, so no BuildCraft class contains a GL call for gl46core's transformer to
 * rewrite. */
@SideOnly(Side.CLIENT)
public final class RasterState {
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_STENCIL_TEST = 0x0B90;

    private static final MethodHandle SCISSOR_TEST, STENCIL_TEST; // (boolean)void
    private static final MethodHandle SCISSOR, STENCIL_FUNC, STENCIL_OP, STENCIL_MASK, CLEAR_STENCIL;

    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType z = MethodType.methodType(void.class, boolean.class);
        MethodType i = MethodType.methodType(void.class, int.class);
        MethodType iii = MethodType.methodType(void.class, int.class, int.class, int.class);
        MethodType iiii = MethodType.methodType(void.class, int.class, int.class, int.class, int.class);
        boolean gl46core = Loader.isModLoaded("gl46core");
        try {
            if (gl46core) {
                Class<?> api = Class.forName("com.github.gl46core.api.render.RasterState");
                SCISSOR_TEST = lookup.findStatic(api, "setScissorTest", z);
                STENCIL_TEST = lookup.findStatic(api, "setStencilTest", z);
                SCISSOR = lookup.findStatic(api, "scissor", iiii);
                STENCIL_FUNC = lookup.findStatic(api, "stencilFunc", iii);
                STENCIL_OP = lookup.findStatic(api, "stencilOp", iii);
                STENCIL_MASK = lookup.findStatic(api, "stencilMask", i);
                // The clear value is not tracked or batched: OpenGL directly either way
                CLEAR_STENCIL = lookup.findStatic(Class.forName("org.lwjgl.opengl.GL11"), "glClearStencil", i);
            } else {
                Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
                MethodHandle enable = lookup.findStatic(gl11, "glEnable", i);
                MethodHandle disable = lookup.findStatic(gl11, "glDisable", i);
                SCISSOR_TEST = toggle(enable, disable, GL_SCISSOR_TEST);
                STENCIL_TEST = toggle(enable, disable, GL_STENCIL_TEST);
                SCISSOR = lookup.findStatic(gl11, "glScissor", iiii);
                STENCIL_FUNC = lookup.findStatic(gl11, "glStencilFunc", iii);
                STENCIL_OP = lookup.findStatic(gl11, "glStencilOp", iii);
                STENCIL_MASK = lookup.findStatic(gl11, "glStencilMask", i);
                CLEAR_STENCIL = lookup.findStatic(gl11, "glClearStencil", i);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Binding scissor/stencil calls failed (gl46core installed: " + gl46core + ")", e);
        }
    }

    private RasterState() {}

    /** (boolean enabled) -> glEnable(cap) or glDisable(cap) */
    private static MethodHandle toggle(MethodHandle enable, MethodHandle disable, int cap) {
        MethodHandle on = MethodHandles.dropArguments(MethodHandles.insertArguments(enable, 0, cap), 0, boolean.class);
        MethodHandle off = MethodHandles.dropArguments(MethodHandles.insertArguments(disable, 0, cap), 0, boolean.class);
        return MethodHandles.guardWithTest(MethodHandles.identity(boolean.class), on, off);
    }

    public static void setScissorTest(boolean enabled) {
        try {
            SCISSOR_TEST.invokeExact(enabled);
        } catch (Throwable t) {
            throw new IllegalStateException("Toggling the scissor test failed", t);
        }
    }

    /** In framebuffer pixels */
    public static void scissor(int x, int y, int width, int height) {
        try {
            SCISSOR.invokeExact(x, y, width, height);
        } catch (Throwable t) {
            throw new IllegalStateException("glScissor failed", t);
        }
    }

    public static void setStencilTest(boolean enabled) {
        try {
            STENCIL_TEST.invokeExact(enabled);
        } catch (Throwable t) {
            throw new IllegalStateException("Toggling the stencil test failed", t);
        }
    }

    public static void stencilFunc(int func, int ref, int mask) {
        try {
            STENCIL_FUNC.invokeExact(func, ref, mask);
        } catch (Throwable t) {
            throw new IllegalStateException("glStencilFunc failed", t);
        }
    }

    public static void stencilOp(int stencilFail, int depthFail, int depthPass) {
        try {
            STENCIL_OP.invokeExact(stencilFail, depthFail, depthPass);
        } catch (Throwable t) {
            throw new IllegalStateException("glStencilOp failed", t);
        }
    }

    public static void stencilMask(int mask) {
        try {
            STENCIL_MASK.invokeExact(mask);
        } catch (Throwable t) {
            throw new IllegalStateException("glStencilMask failed", t);
        }
    }

    /** The value GlStateManager.clear(GL_STENCIL_BUFFER_BIT) clears to */
    public static void clearStencil(int value) {
        try {
            CLEAR_STENCIL.invokeExact(value);
        } catch (Throwable t) {
            throw new IllegalStateException("glClearStencil failed", t);
        }
    }
}
