/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.laser;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.lib.client.render.gl.StaticMesh;
import buildcraft.lib.misc.RenderUtil;
import buildcraft.lib.misc.RenderUtil.AutoTessellator;

@SideOnly(Side.CLIENT)
public final class LaserCompiledList {
    private final StaticMesh mesh;
    private final boolean useColour;

    private LaserCompiledList(StaticMesh mesh, boolean useColour) {
        this.mesh = mesh;
        this.useColour = useColour;
    }

    public void render() {
        mesh.draw();
        if (useColour) {
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    public void delete() {
        mesh.delete();
    }

    public static class Builder implements ILaserRenderer, AutoCloseable {
        private final AutoTessellator tess;
        private final boolean useColour;

        public Builder(boolean useNormalColour) {
            this.useColour = useNormalColour;
            tess = RenderUtil.getThreadLocalUnusedTessellator();
            BufferBuilder bufferBuilder = tess.tessellator.getBuffer();
            bufferBuilder.begin(GL11.GL_QUADS,
                useNormalColour ? LaserRenderer_BC8.FORMAT_ALL : LaserRenderer_BC8.FORMAT_LESS);
        }

        @Override
        public void vertex(double x, double y, double z, double u, double v, int lmap, float nx, float ny, float nz,
            float diffuse) {
            BufferBuilder bufferBuilder = tess.tessellator.getBuffer();
            bufferBuilder.pos(x, y, z);
            bufferBuilder.tex(u, v);
            bufferBuilder.lightmap((lmap >> 16) & 0xFFFF, lmap & 0xFFFF);
            if (useColour) {
                bufferBuilder.color(diffuse, diffuse, diffuse, 1.0f);
            }
            bufferBuilder.endVertex();
        }

        public LaserCompiledList build() {
            return new LaserCompiledList(StaticMesh.of(tess.tessellator.getBuffer()), useColour);
        }

        @Override
        public void close() {
            tess.close();
        }
    }
}
