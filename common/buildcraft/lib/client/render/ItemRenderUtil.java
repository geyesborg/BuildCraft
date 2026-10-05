/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render;

import java.util.List;
import java.util.Random;

import javax.vecmath.Vector3f;


import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.core.EnumPipePart;

import buildcraft.lib.BCLibConfig;
import buildcraft.lib.client.model.MutableQuad;

@SideOnly(Side.CLIENT)
public class ItemRenderUtil {

    private static final Random modelOffsetRandom = new Random(0);

    /** The tint colour of each tinted quad, in render order (null without tinted quads) */
    private static int[] tintColours(IBakedModel model, ItemStack stack) {
        int count = 0;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            for (BakedQuad quad : model.getQuads(null, part.face, 0)) {
                if (quad.hasTintIndex()) count++;
            }
        }
        if (count == 0) return null;
        int[] tints = new int[count];
        int i = 0;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            for (BakedQuad quad : model.getQuads(null, part.face, 0)) {
                if (quad.hasTintIndex()) {
                    int colour = Minecraft.getMinecraft().getItemColors().colorMultiplier(stack, quad.getTintIndex());
                    if (EntityRenderer.anaglyphEnable) {
                        colour = TextureUtil.anaglyphColor(colour);
                    }
                    tints[i++] = colour;
                }
            }
        }
        return tints;
    }

    // Batch item rendering

    private static boolean inBatch = false;

    /** Used to render a lot of items in sequential order. Assumes that you don't change the glstate inbetween calls.
     * You must call {@link #endItemBatch()} after your have rendered all of the items. */
    public static void renderItemStack(double x, double y, double z, ItemStack stack, int lightc, EnumFacing dir,
        BufferBuilder bb) {
        renderItemStack(x, y, z, stack, stack.getCount(), lightc, dir, bb);
    }

    /** Used to render a lot of items in sequential order. Assumes that you don't change the glstate inbetween calls.
     * You must call {@link #endItemBatch()} after your have rendered all of the items. */
    public static void renderItemStack(double x, double y, double z, ItemStack stack, int stackCount, int lightc,
        EnumFacing dir, BufferBuilder bb) {
        renderItemStack(x, y, z, stack, stackCount, lightc, dir, bb, 0);
    }

    /** As above, with the item moved towards the camera by the fraction nearer (0 .. 1) of its distance, along the line
     * of sight, so it stays at the same place on screen: items drawn overlapping (bunched in a pipe) are equal cubes whose
     * faces would be coplanar and z-fight; slightly different distances let the depth test pick one, the same one every
     * frame. x, y, z are relative to the camera, as in a tile entity renderer. */
    public static void renderItemStack(double x, double y, double z, ItemStack stack, int stackCount, int lightc,
        EnumFacing dir, BufferBuilder bb, double nearer) {
        if (stack.isEmpty()) {
            return;
        }
        x *= 1 - nearer;
        y *= 1 - nearer;
        z *= 1 - nearer;
        try {
            renderItemStackInternal(x, y, z, stack, stackCount, lightc, dir, bb);
        } catch (Throwable exception) {
            CrashReport report = CrashReport.makeCrashReport(exception, "Rendering Item Stack");
            CrashReportCategory category = report.makeCategory("Item being rendered");
            category.addCrashSection("Stack Count", stackCount);
            category.addDetail("Item Class", () -> "" + stack.getItem().getClass());
            category.addDetail("Item ID", () -> "" + ForgeRegistries.ITEMS.getKey(stack.getItem()));
            category.addDetail("Item Meta", () -> "" + stack.getMetadata());
            category.addDetail("Item NBT", () -> "" + stack.getTagCompound());
            throw new ReportedException(report);
        }
    }

    private static void renderItemStackInternal(
        double x, double y, double z, ItemStack stack, int stackCount, int lightc, EnumFacing dir, BufferBuilder bb
    ) {
        if (dir == null) {
            dir = EnumFacing.EAST;
        }
        dir = BCLibConfig.rotateTravelingItems.changeFacing(dir);

        IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(stack);
        model = model.getOverrides().handleItemState(model, stack, null, null);
        boolean requireGl = stack.hasEffect() || model.isBuiltInRenderer();

        if (bb != null && !requireGl) {

            final int itemModelCount = getStackModelCount(stackCount);
            MutableQuad[] quads = ItemQuadTransforms.get(model, dir, tintColours(model, stack));

            if (itemModelCount > 1) {
                setupModelOffsetRandom(stack);
            }

            for (int i = 0; i < itemModelCount; i++) {
                if (i == 0) {
                    bb.setTranslation(x, y, z);
                } else {
                    float dx = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    float dy = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    float dz = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    bb.setTranslation(x + dx, y + dy, z + dz);
                }

                for (MutableQuad q : quads) {
                    q.lighti(lightc);
                    q.render(bb);
                }
            }

            bb.setTranslation(0, 0, 0);
            return;
        }

        if (!inBatch) {
            inBatch = true;
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.pushMatrix();
            RenderHelper.disableStandardItemLighting();
        }
        // Each item at its own position (the translation used to be set once per batch, so every item after the
        // first was drawn at the first one's position)
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(0.3, 0.3, 0.3);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightc % (float) 0x1_00_00,
            lightc / (float) 0x1_00_00);
        Minecraft.getMinecraft().getRenderItem().renderItem(stack, model);
        GlStateManager.popMatrix();
    }

    private static void setupModelOffsetRandom(ItemStack stack) {
        final long seed;
        if (stack.isEmpty()) {
            seed = 137;
        } else {
            ResourceLocation regName = stack.getItem().getRegistryName();
            if (regName == null) {
                seed = 127;
            } else {
                int regNameSeed = regName.getNamespace().hashCode() ^ regName.getPath().hashCode();
                seed = (regNameSeed & 0x7F_FF_FF_FF) | (((long) stack.getMetadata()) << 32);
            }
        }
        modelOffsetRandom.setSeed(seed);
    }

    private static int getStackModelCount(int stackCount) {
        if (stackCount > 1) {
            if (stackCount > 16) {
                if (stackCount > 32) {
                    if (stackCount > 48) {
                        return 5;
                    } else {
                        return 4;
                    }
                } else {
                    return 3;
                }
            } else {
                return 2;
            }
        } else {
            return 1;
        }
    }

    public static void endItemBatch() {
        if (inBatch) {
            inBatch = false;
            GlStateManager.popMatrix();
        }
    }
}
