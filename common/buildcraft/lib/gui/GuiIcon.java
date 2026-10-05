/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.core.render.ISprite;

import buildcraft.lib.client.sprite.SpriteRaw;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.pos.IGuiPosition;

@SideOnly(Side.CLIENT)
public class GuiIcon implements ISimpleDrawable {
    public final ISprite sprite;
    public final int textureSize;
    public final int width, height;

    public GuiIcon(ISprite sprite, int textureSize) {
        this.sprite = sprite;
        this.textureSize = textureSize;
        this.width = (int) (Math.abs(sprite.getInterpU(1) - sprite.getInterpU(0)) * textureSize);
        this.height = (int) (Math.abs(sprite.getInterpV(1) - sprite.getInterpV(0)) * textureSize);
    }

    public GuiIcon(ResourceLocation texture, double u, double v, double width, double height, int texSize) {
        this(new SpriteRaw(texture, u, v, width, height, texSize), texSize);
    }

    public GuiIcon(ResourceLocation texture, double u, double v, double width, double height) {
        this(texture, u, v, width, height, 256);
    }

    public GuiIcon offset(double u, double v) {
        SpriteRaw raw = (SpriteRaw) sprite;
        double uMin = raw.uMin + u / textureSize;
        double vMin = raw.vMin + v / textureSize;
        return new GuiIcon(new SpriteRaw(raw.location, uMin, vMin, raw.width, raw.height), textureSize);
    }

    public boolean containsGuiPos(double x, double y, IGuiPosition pos) {
        return new GuiRectangle(x, y, width, height).contains(pos);
    }

    public DynamicTexture createDynamicTexture(int scale) {
        return new DynamicTexture(width * scale, height * scale);
    }

    @Override
    public void drawAt(double x, double y) {
        this.drawScaledInside(x, y, this.width, this.height);
    }

    public void drawScaledInside(IGuiArea element) {
        drawScaledInside(element.getX(), element.getY(), element.getWidth(), element.getHeight());
    }

    public void drawScaledInside(double x, double y, double drawnWidth, double drawnHeight) {
        draw(sprite, x, y, x + drawnWidth, y + drawnHeight);
    }

    private static final int CUSTOM_QUAD_GRID = 8;

    /** Draws the sprite onto an arbitrary convex quadrilateral (corners in order: bottom-left, bottom-right, top-right,
     * top-left of the sprite) with a projective mapping, so a quad seen in perspective isn't warped along its diagonal.
     * The mapping is evaluated exactly at the corners of an 8x8 grid. Degenerate or non-convex input draws a plain
     * quad. */
    public void drawCustomQuad(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        sprite.bindTexture();

        double uMin = sprite.getInterpU(0);
        double uMax = sprite.getInterpU(1);

        double vMin = sprite.getInterpV(0);
        double vMax = sprite.getInterpV(1);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder vb = tess.getBuffer();
        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        double[] h = isConvex(x1, y1, x2, y2, x3, y3, x4, y4) ? squareToQuad(x1, y1, x2, y2, x3, y3, x4, y4) : null;
        if (h == null) {
            vertex(vb, x1, y1, uMin, vMax);
            vertex(vb, x2, y2, uMax, vMax);
            vertex(vb, x3, y3, uMax, vMin);
            vertex(vb, x4, y4, uMin, vMin);
        } else {
            int n = CUSTOM_QUAD_GRID;
            for (int i = 0; i < n; i++) {
                double a0 = i / (double) n, a1 = (i + 1) / (double) n;
                for (int j = 0; j < n; j++) {
                    double b0 = j / (double) n, b1 = (j + 1) / (double) n;
                    mappedVertex(vb, h, a0, b0, uMin, uMax, vMin, vMax);
                    mappedVertex(vb, h, a1, b0, uMin, uMax, vMin, vMax);
                    mappedVertex(vb, h, a1, b1, uMin, uMax, vMin, vMax);
                    mappedVertex(vb, h, a0, b1, uMin, uMax, vMin, vMax);
                }
            }
        }
        tess.draw();
    }

    /** (a, b) in the unit square: a runs from corner 1 to corner 2 (u min to max), b from corner 1 to corner 4 (v max to
     * min) */
    private static void mappedVertex(BufferBuilder vb, double[] h, double a, double b, double uMin, double uMax,
        double vMin, double vMax) {
        double w = h[6] * a + h[7] * b + 1;
        double x = (h[0] * a + h[1] * b + h[2]) / w;
        double y = (h[3] * a + h[4] * b + h[5]) / w;
        vertex(vb, x, y, uMin + (uMax - uMin) * a, vMax + (vMin - vMax) * b);
    }

    /** The diagonals cross inside both: the quad is convex (the old projective-texcoord code's condition) */
    static boolean isConvex(double x1, double y1, double x2, double y2, double x3, double y3, double x4,
        double y4) {
        double ax = x3 - x1, ay = y3 - y1, bx = x4 - x2, by = y4 - y2;
        double cross = ax * by - ay * bx;
        if (cross == 0) return false;
        double cx = x1 - x2, cy = y1 - y2;
        double s = (ax * cy - ay * cx) / cross;
        double t = (bx * cy - by * cx) / cross;
        return s > 0 && s < 1 && t > 0 && t < 1;
    }

    /** The projective map from the unit square ((0,0), (1,0), (1,1), (0,1)) to the four corners (Heckbert,
     * "Fundamentals of Texture Mapping and Image Warping", 1989), as {a, b, c, d, e, f, g, h}; null if degenerate */
    static double[] squareToQuad(double x0, double y0, double x1, double y1, double x2, double y2, double x3,
        double y3) {
        double sx = x0 - x1 + x2 - x3;
        double sy = y0 - y1 + y2 - y3;
        if (sx == 0 && sy == 0) {
            return new double[] { x1 - x0, x3 - x0, x0, y1 - y0, y3 - y0, y0, 0, 0 };
        }
        double dx1 = x1 - x2, dx2 = x3 - x2, dy1 = y1 - y2, dy2 = y3 - y2;
        double det = dx1 * dy2 - dx2 * dy1;
        if (det == 0) return null;
        double g = (sx * dy2 - dx2 * sy) / det;
        double h = (dx1 * sy - sx * dy1) / det;
        return new double[] { x1 - x0 + g * x1, x3 - x0 + h * x3, x0, y1 - y0 + g * y1, y3 - y0 + h * y3, y0, g, h };
    }
    public void drawCutInside(IGuiArea element) {
        drawCutInside(element.getX(), element.getY(), element.getWidth(), element.getHeight());
    }

    public void drawCutInside(double x, double y, double displayWidth, double displayHeight) {
        sprite.bindTexture();

        displayWidth = Math.min(this.width, displayWidth);
        displayHeight = Math.min(this.height, displayHeight);

        double xMin = x;
        double yMin = y;

        double xMax = x + displayWidth;
        double yMax = y + displayHeight;

        double uMin = sprite.getInterpU(0);
        double vMin = sprite.getInterpV(0);

        double uMax = sprite.getInterpU(displayWidth / width);
        double vMax = sprite.getInterpV(displayHeight / height);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder vb = tess.getBuffer();
        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        vertex(vb, xMin, yMax, uMin, vMax);
        vertex(vb, xMax, yMax, uMax, vMax);
        vertex(vb, xMax, yMin, uMax, vMin);
        vertex(vb, xMin, yMin, uMin, vMin);

        tess.draw();
    }

    public void drawCustomScaledAt(IGuiArea area, double texU0, double texV0, double texU1, double texV1) {
        double xMin = area.getX();
        double yMin = area.getY();
        double xMax = xMin + area.getWidth();
        double yMax = yMin + area.getHeight();
        drawCustomScaledAt(xMin, yMin, xMax, yMax, texU0, texV0, texU1, texV1);
    }

    /** @param xMin Absolute horizontal position to start rendering at
     * @param yMin Absolute vertical position to start rendering at
     * @param xMax Absolute horizontal width to end rendering at
     * @param yMax Absolute vertical width to end rendering at
     * @param texU0 Minimum texture U co-ordinate, always between 0 and 1.
     * @param texV0 Minimum texture V co-ordinate, always between 0 and 1.
     * @param texU1 Maximum texture U co-ordinate, always between 0 and 1.
     * @param texV1 Maximum texture V co-ordinate, always between 0 and 1. */
    public void drawCustomScaledAt(double xMin, double yMin, double xMax, double yMax, double texU0, double texV0, double texU1, double texV1) {
        drawCustomScaledAt(sprite, xMin, yMin, xMax, yMax, texU0, texV0, texU1, texV1);
    }

    /** @param xMin Absolute horizontal position to start rendering at
     * @param yMin Absolute vertical position to start rendering at
     * @param xMax Absolute horizontal width to end rendering at
     * @param yMax Absolute vertical width to end rendering at
     * @param texU0 Minimum texture U co-ordinate, always between 0 and 1.
     * @param texV0 Minimum texture V co-ordinate, always between 0 and 1.
     * @param texU1 Maximum texture U co-ordinate, always between 0 and 1.
     * @param texV1 Maximum texture V co-ordinate, always between 0 and 1. */
    public static void drawCustomScaledAt(ISprite sprite, double xMin, double yMin, double xMax, double yMax, double texU0, double texV0, double texU1, double texV1) {
        sprite.bindTexture();

        double uMin = sprite.getInterpU(texU0);
        double vMin = sprite.getInterpV(texV0);

        double uMax = sprite.getInterpU(texU1);
        double vMax = sprite.getInterpV(texV1);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder vb = tess.getBuffer();
        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        vertex(vb, xMin, yMax, uMin, vMax);
        vertex(vb, xMax, yMax, uMax, vMax);
        vertex(vb, xMax, yMin, uMax, vMin);
        vertex(vb, xMin, yMin, uMin, vMin);

        tess.draw();
    }

    public static void drawAt(ISprite sprite, double x, double y, double size) {
        drawAt(sprite, x, y, size, size);
    }

    public static void drawAt(ISprite sprite, double x, double y, double width, double height) {
        draw(sprite, x, y, x + width, y + height);
    }

    public static void draw(ISprite sprite, double xMin, double yMin, double xMax, double yMax) {
        sprite.bindTexture();

        double uMin = sprite.getInterpU(0);
        double vMin = sprite.getInterpV(0);

        double uMax = sprite.getInterpU(1);
        double vMax = sprite.getInterpV(1);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder vb = tess.getBuffer();
        vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        vertex(vb, xMin, yMax, uMin, vMax);
        vertex(vb, xMax, yMax, uMax, vMax);
        vertex(vb, xMax, yMin, uMax, vMin);
        vertex(vb, xMin, yMin, uMin, vMin);

        tess.draw();
    }

    private static void vertex(BufferBuilder vb, double x, double y, double u, double v) {
        vb.pos(x, y, 0);
        vb.tex(u, v);
        vb.endVertex();
    }
}
