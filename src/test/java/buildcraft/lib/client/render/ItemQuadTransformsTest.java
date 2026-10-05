package buildcraft.lib.client.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

import javax.vecmath.Vector3f;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

import buildcraft.api.core.EnumPipePart;

import buildcraft.lib.client.model.MutableQuad;

/** Cached travelling-item quads must render exactly like the per-frame transform they replace */
public class ItemQuadTransformsTest {

    private static IBakedModel model(Random random, boolean tinted) {
        EnumMap<EnumPipePart, List<BakedQuad>> quads = new EnumMap<>(EnumPipePart.class);
        for (EnumPipePart part : EnumPipePart.VALUES) {
            List<BakedQuad> list = new ArrayList<>();
            for (int q = 0; q < 1 + random.nextInt(3); q++) {
                int[] data = new int[28];
                for (int v = 0; v < 4; v++) {
                    data[v * 7] = Float.floatToRawIntBits(random.nextFloat());
                    data[v * 7 + 1] = Float.floatToRawIntBits(random.nextFloat());
                    data[v * 7 + 2] = Float.floatToRawIntBits(random.nextFloat());
                    data[v * 7 + 3] = random.nextInt();
                    data[v * 7 + 4] = Float.floatToRawIntBits(random.nextFloat());
                    data[v * 7 + 5] = Float.floatToRawIntBits(random.nextFloat());
                    data[v * 7 + 6] = random.nextInt();
                }
                int tint = tinted && random.nextBoolean() ? random.nextInt(2) : -1;
                list.add(new BakedQuad(data, tint, part.face == null ? EnumFacing.UP : part.face, null, random.nextBoolean(),
                    DefaultVertexFormats.ITEM));
            }
            quads.put(part, list);
        }
        return new IBakedModel() {
            @Override
            public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
                return quads.get(EnumPipePart.fromFacing(side));
            }

            @Override
            public boolean isAmbientOcclusion() {
                return false;
            }

            @Override
            public boolean isGui3d() {
                return false;
            }

            @Override
            public boolean isBuiltInRenderer() {
                return false;
            }

            @Override
            public TextureAtlasSprite getParticleTexture() {
                return null;
            }

            @Override
            public ItemOverrideList getOverrides() {
                return new ItemOverrideList(Collections.emptyList());
            }
        };
    }

    /** ItemRenderUtil's per-frame code before the cache, for one model copy */
    private static void renderPerFrame(IBakedModel model, EnumFacing dir, int[] tints, int lightc, BufferBuilder bb, float scale) {
        MutableQuad q = new MutableQuad(-1, null);
        int tint = 0;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            for (BakedQuad quad : model.getQuads(null, part.face, 0)) {
                q.fromBakedItem(quad);
                q.translated(-0.5, -0.5, -0.5);
                q.scaled(scale);
                q.rotate(EnumFacing.SOUTH, dir, 0, 0, 0);
                if (quad.hasTintIndex()) {
                    int colour = tints[tint++];
                    q.multColouri(colour, colour >> 8, colour >> 16, 0xFF);
                }
                q.lighti(lightc);
                Vector3f normal = q.getCalculatedNormal();
                q.normalvf(normal);
                q.multShade();
                q.render(bb);
            }
        }
    }

    @Test
    public void cachedQuadsRenderLikeThePerFrameTransform() {
        Random random = new Random(4475);
        for (int round = 0; round < 60; round++) {
            IBakedModel model = model(random, round % 2 == 1);
            int tinted = 0;
            for (EnumPipePart part : EnumPipePart.VALUES) {
                for (BakedQuad quad : model.getQuads(null, part.face, 0)) {
                    if (quad.hasTintIndex()) tinted++;
                }
            }
            int[] tints = null;
            if (tinted > 0) {
                tints = new int[tinted];
                for (int i = 0; i < tinted; i++) tints[i] = random.nextInt();
            }
            for (EnumFacing dir : EnumFacing.VALUES) {
                BufferBuilder expected = new BufferBuilder(512), actual = new BufferBuilder(512);
                expected.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                actual.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                // Several frames and stack copies at different translations, light levels and size steps share the
                // cache entries (step 0 is the original 0.30 scale)
                for (int copy = 0; copy < 6; copy++) {
                    double tx = random.nextDouble() * 40 - 20, ty = random.nextDouble() * 40 - 20, tz = random.nextDouble() * 40 - 20;
                    int lightc = random.nextInt(16) << 4 | random.nextInt(16) << 20;
                    int step = copy % 3;
                    expected.setTranslation(tx, ty, tz);
                    actual.setTranslation(tx, ty, tz);
                    renderPerFrame(model, dir, tints, lightc, expected, step == 0 ? 0.30f : ItemQuadTransforms.scale(step));
                    for (MutableQuad q : ItemQuadTransforms.get(model, dir, tints, step)) {
                        q.lighti(lightc);
                        q.render(actual);
                    }
                }
                Assert.assertEquals(expected.getVertexCount(), actual.getVertexCount());
                int bytes = expected.getVertexCount() * DefaultVertexFormats.BLOCK.getSize();
                ByteBuffer a = expected.getByteBuffer(), b = actual.getByteBuffer();
                for (int i = 0; i < bytes; i++) {
                    Assert.assertEquals("byte " + i + ", round " + round + ", " + dir, a.get(i), b.get(i));
                }
            }
        }
    }
}
