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
