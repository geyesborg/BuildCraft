package buildcraft.lib.client.render.fluid;

import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import buildcraft.lib.client.model.MutableVertex;

/** Packed quads (FluidRenderer, MutableQuad.render) must be byte-for-byte what the per-element BufferBuilder calls write */
public class FluidRendererPackTest {

    private static final class CountingBuffer extends BufferBuilder {
        int writes;

        CountingBuffer() {
            super(8);
        }

        @Override
        public void addVertexData(int[] data) {
            writes++;
            super.addVertexData(data);
        }
    }

    @Test
    public void fluidBoxesUseOneWriteAndPreserveEveryByte() {
        Random random = new Random(6238);
        BufferBuilder expected = new BufferBuilder(8);
        CountingBuffer actual = new CountingBuffer();
        for (int round = 0; round < 4; round++) {
            expected.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            actual.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            for (int mask = 0; mask <= 64; mask++) {
                boolean[] sides = mask == 64 ? null : new boolean[6];
                if (sides != null) {
                    for (int face = 0; face < 6; face++) sides[face] = (mask & (1 << face)) != 0;
                }
                int faces = mask == 64 ? 6 : Integer.bitCount(mask);
                double x = (random.nextDouble() - 0.5) * 60_000_000;
                double y = (random.nextDouble() - 0.5) * 512;
                double z = (random.nextDouble() - 0.5) * 60_000_000;
                expected.setTranslation(x, y, z);
                actual.setTranslation(x, y, z);
                FluidRenderer.startQuads(actual, sides);
                int writesBefore = actual.writes;
                for (int v = 0; v < faces * 4; v++) {
                    MutableVertex vertex = new MutableVertex();
                    vertex.positiond(random.nextDouble() * 4 - 2, random.nextDouble() * 4 - 2,
                        random.nextDouble() * 4 - 2);
                    vertex.colouri(random.nextInt());
                    vertex.texf(random.nextFloat(), random.nextFloat());
                    vertex.lighti(random.nextInt(16), random.nextInt(16));
                    vertex.renderAsBlock(expected);
                    FluidRenderer.packVertex(vertex);
                }
                Assert.assertEquals("mask " + mask, faces == 0 ? 0 : 1, actual.writes - writesBefore);
                Assert.assertEquals(expected.getVertexCount(), actual.getVertexCount());
            }
            int bytes = expected.getVertexCount() * DefaultVertexFormats.BLOCK.getSize();
            for (int i = 0; i < bytes; i++) {
                Assert.assertEquals("byte " + i + ", round " + round,
                    expected.getByteBuffer().get(i), actual.getByteBuffer().get(i));
            }
            expected.finishDrawing();
            actual.finishDrawing();
            expected.reset();
            actual.reset();
        }
    }

    @Test
    public void packedQuadsMatchPerElementCalls() {
        Random random = new Random(4722);
        for (int round = 0; round < 200; round++) {
            double tx = (random.nextDouble() - 0.5) * 400, ty = (random.nextDouble() - 0.5) * 300, tz = (random.nextDouble() - 0.5) * 400;
            BufferBuilder slow = new BufferBuilder(256), fast = new BufferBuilder(256);
            slow.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            fast.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            slow.setTranslation(tx, ty, tz);
            fast.setTranslation(tx, ty, tz);
            FluidRenderer.startQuads(fast);
            for (int i = 0; i < 8; i++) {
                MutableVertex v = new MutableVertex();
                v.positiond(random.nextDouble() * 2 - 0.5, random.nextDouble() * 2 - 0.5, random.nextDouble() * 2 - 0.5);
                v.colouri(random.nextInt());
                v.texf(random.nextFloat(), random.nextFloat());
                v.lighti(random.nextInt(16), random.nextInt(16));
                v.renderAsBlock(slow);
                FluidRenderer.packVertex(v);
            }
            Assert.assertEquals(slow.getVertexCount(), fast.getVertexCount());
            int bytes = slow.getVertexCount() * DefaultVertexFormats.BLOCK.getSize();
            ByteBuffer a = slow.getByteBuffer(), b = fast.getByteBuffer();
            for (int i = 0; i < bytes; i++) {
                Assert.assertEquals("byte " + i + " of round " + round, a.get(i), b.get(i));
            }
        }
    }
    @Test
    public void packedMutableQuadMatchesPerVertexRendering() {
        Random random = new Random(4556);
        for (int round = 0; round < 200; round++) {
            double tx = (random.nextDouble() - 0.5) * 400, ty = (random.nextDouble() - 0.5) * 300, tz = (random.nextDouble() - 0.5) * 400;
            BufferBuilder slow = new BufferBuilder(256), fast = new BufferBuilder(256);
            slow.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            fast.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            slow.setTranslation(tx, ty, tz);
            fast.setTranslation(tx, ty, tz);
            for (int q = 0; q < 3; q++) {
                buildcraft.lib.client.model.MutableQuad quad = new buildcraft.lib.client.model.MutableQuad();
                for (MutableVertex v : new MutableVertex[] { quad.vertex_0, quad.vertex_1, quad.vertex_2, quad.vertex_3 }) {
                    v.positiond(random.nextDouble() * 2 - 0.5, random.nextDouble() * 2 - 0.5, random.nextDouble() * 2 - 0.5);
                    v.colouri(random.nextInt());
                    v.texf(random.nextFloat(), random.nextFloat());
                    v.lighti(random.nextInt(16), random.nextInt(16));
                    v.renderAsBlock(slow);
                }
                quad.render(fast);
            }
            Assert.assertEquals(slow.getVertexCount(), fast.getVertexCount());
            int bytes = slow.getVertexCount() * DefaultVertexFormats.BLOCK.getSize();
            ByteBuffer a = slow.getByteBuffer(), b = fast.getByteBuffer();
            for (int i = 0; i < bytes; i++) {
                Assert.assertEquals("byte " + i + " of round " + round, a.get(i), b.get(i));
            }
        }
    }
}
