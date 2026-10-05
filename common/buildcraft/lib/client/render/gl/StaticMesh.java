/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.gl;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.lib.misc.RenderUtil;
import buildcraft.lib.misc.RenderUtil.AutoTessellator;

/** Geometry built once and drawn many times, in place of a display list: a vanilla {@link VertexBuffer} drawn with
 * GlStateManager's client-array calls (what vanilla's own VBO terrain uses, and what gl46core runs natively on its GL 4.6
 * pipeline), or, with VBOs turned off, the vertices drawn again through a tessellator. Formats with position, colour
 * and texture/lightmap coordinates; normals are not supported (GlStateManager has no buffer-offset normal pointer). */
@SideOnly(Side.CLIENT)
public final class StaticMesh {
    private final VertexFormat format;
    private final int mode;
    private final int vertexCount;
    private final VertexBuffer vbo;
    private final int[] vertices;
    private boolean deleted;

    private StaticMesh(VertexFormat format, int mode, int vertexCount, VertexBuffer vbo, int[] vertices) {
        this.format = format;
        this.mode = mode;
        this.vertexCount = vertexCount;
        this.vbo = vbo;
        this.vertices = vertices;
    }

    /** Takes the vertices of a begun BufferBuilder and resets it (it is not drawn) */
    public static StaticMesh of(BufferBuilder bb) {
        VertexFormat format = bb.getVertexFormat();
        for (VertexFormatElement e : format.getElements()) {
            if (e.getUsage() == VertexFormatElement.EnumUsage.NORMAL) {
                throw new IllegalArgumentException("StaticMesh does not support normals: " + format);
            }
        }
        int mode = bb.getDrawMode();
        int count = bb.getVertexCount();
        bb.finishDrawing();
        ByteBuffer data = bb.getByteBuffer();
        StaticMesh mesh;
        if (OpenGlHelper.useVbo()) {
            VertexBuffer vbo = new VertexBuffer(format);
            vbo.bufferData(data);
            mesh = new StaticMesh(format, mode, count, vbo, null);
        } else {
            int[] vertices = new int[count * format.getIntegerSize()];
            data.asIntBuffer().get(vertices);
            mesh = new StaticMesh(format, mode, count, null, vertices);
        }
        bb.reset();
        return mesh;
    }

    public void draw() {
        if (deleted) throw new IllegalStateException("StaticMesh drawn after delete()");
        if (vertexCount == 0) return;
        if (vbo == null) {
            // A spare tessellator: the caller may be building with the shared one
            try (AutoTessellator tess = RenderUtil.getThreadLocalUnusedTessellator()) {
                BufferBuilder bb = tess.tessellator.getBuffer();
                bb.begin(mode, format);
                bb.addVertexData(vertices);
                tess.tessellator.draw();
            }
            return;
        }
        int stride = format.getSize();
        boolean colour = false;
        vbo.bindBuffer();
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement e = format.getElement(i);
            int offset = format.getOffset(i);
            int type = e.getType().getGlConstant();
            switch (e.getUsage()) {
                case POSITION:
                    GlStateManager.glVertexPointer(e.getElementCount(), type, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case COLOR:
                    GlStateManager.glColorPointer(e.getElementCount(), type, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
                    colour = true;
                    break;
                case UV:
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + e.getIndex());
                    GlStateManager.glTexCoordPointer(e.getElementCount(), type, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                    break;
                default:
                    break;
            }
        }
        vbo.drawArrays(mode);
        vbo.unbindBuffer();
        for (VertexFormatElement e : format.getElements()) {
            switch (e.getUsage()) {
                case POSITION:
                    GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case COLOR:
                    GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                    break;
                case UV:
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + e.getIndex());
                    GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                    break;
                default:
                    break;
            }
        }
        if (colour) {
            // A colour array leaves the current colour undefined: GlStateManager's cached colour must not be trusted
            GlStateManager.resetColor();
        }
    }

    public void delete() {
        if (deleted) return;
        deleted = true;
        if (vbo != null) vbo.deleteGlBuffers();
    }
}
