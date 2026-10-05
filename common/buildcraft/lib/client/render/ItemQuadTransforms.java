/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3f;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.core.EnumPipePart;

import buildcraft.lib.client.model.MutableQuad;

/** Item quads transformed for a travelling item (translate, scale, rotate, tint, normal, shade: everything but the
 * light), per model, direction and tint colours. The values and order of operations are the per-quad transform's;
 * it is the same for every stack copy and every frame, so it is built once. Client thread only. */
@SideOnly(Side.CLIENT)
public final class ItemQuadTransforms {
    private static final int LIMIT = 2048;
    private static final float SCALE = 0.30f;
    /** Size steps for items drawn close together: equal cubes on one axis would have coplanar faces and z-fight where
     * they overlap (bunched items in a pipe flicker at their boundaries). Step n is (1 + n * SIZE_STEP) times larger. */
    public static final int SIZE_STEPS = 8;
    private static final float SIZE_STEP = 0.004f;

    private static final class Key {
        final IBakedModel model;
        final EnumFacing dir;
        final int[] tints;
        final int sizeStep;
        final int hash;

        Key(IBakedModel model, EnumFacing dir, int[] tints, int sizeStep) {
            this.model = model;
            this.dir = dir;
            this.tints = tints;
            this.sizeStep = sizeStep;
            this.hash = ((System.identityHashCode(model) * 31 + dir.ordinal()) * 31 + Arrays.hashCode(tints)) * 31 + sizeStep;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Key)) return false;
            Key o = (Key) obj;
            return model == o.model && dir == o.dir && sizeStep == o.sizeStep && Arrays.equals(tints, o.tints);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final Map<Key, MutableQuad[]> cache = new LinkedHashMap<Key, MutableQuad[]>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, MutableQuad[]> eldest) {
            return size() > LIMIT;
        }
    };

    private ItemQuadTransforms() {}

    /** Models are replaced on a resource reload */
    public static void clear() {
        cache.clear();
    }

    /** The transformed quads in render order; tints holds the colour of each tinted quad in that order (null if
     * none); sizeStep (0 .. SIZE_STEPS - 1) makes the item slightly larger. Callers set the light and render; they must
     * not change anything else. */
    public static MutableQuad[] get(IBakedModel model, EnumFacing dir, int[] tints, int sizeStep) {
        if (sizeStep < 0 || sizeStep >= SIZE_STEPS) throw new IllegalArgumentException("sizeStep " + sizeStep);
        Key key = new Key(model, dir, tints, sizeStep);
        MutableQuad[] quads = cache.get(key);
        if (quads == null) {
            quads = build(model, dir, tints, scale(sizeStep));
            cache.put(key, quads);
        }
        return quads;
    }

    public static float scale(int sizeStep) {
        return sizeStep == 0 ? SCALE : SCALE * (1 + sizeStep * SIZE_STEP);
    }

    static MutableQuad[] build(IBakedModel model, EnumFacing dir, int[] tints, float scale) {
        List<MutableQuad> built = new ArrayList<>();
        int tint = 0;
        for (EnumPipePart part : EnumPipePart.VALUES) {
            for (BakedQuad quad : model.getQuads(null, part.face, 0)) {
                MutableQuad q = new MutableQuad(-1, null);
                q.fromBakedItem(quad);
                q.translated(-0.5, -0.5, -0.5);
                q.scaled(scale);
                q.rotate(EnumFacing.SOUTH, dir, 0, 0, 0);
                if (quad.hasTintIndex()) {
                    int colour = tints[tint++];
                    q.multColouri(colour, colour >> 8, colour >> 16, 0xFF);
                }
                Vector3f normal = q.getCalculatedNormal();
                q.normalvf(normal);
                q.multShade();
                built.add(q);
            }
        }
        return built.toArray(new MutableQuad[0]);
    }
}
