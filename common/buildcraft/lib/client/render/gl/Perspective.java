/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.gl;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;


import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.GlStateManager;

/** gluPerspective through GlStateManager. LWJGL's GLU multiplies with glMultMatrix itself, which goes around any renderer
 * that replaces the fixed-function matrix stack (gl46core); the matrix is the same as GLU's. */
@SideOnly(Side.CLIENT)
public final class Perspective {
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private Perspective() {}

    public static void multiply(float fovy, float aspect, float zNear, float zFar) {
        float radians = fovy / 2 * (float) Math.PI / 180;
        float deltaZ = zFar - zNear;
        float sine = (float) Math.sin(radians);
        if (deltaZ == 0 || sine == 0 || aspect == 0) {
            return; // GLU does nothing then either
        }
        float cotangent = (float) Math.cos(radians) / sine;
        MATRIX.clear();
        MATRIX.put(new float[] {
            cotangent / aspect, 0, 0, 0,
            0, cotangent, 0, 0,
            0, 0, -(zFar + zNear) / deltaZ, -1,
            0, 0, -2 * zNear * zFar / deltaZ, 0 });
        MATRIX.flip();
        GlStateManager.multMatrix(MATRIX);
    }
}
