package com.sinuxoff.ingamemessenger.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * All drawing that differs between Minecraft versions: transforms, layers, text, textures, shapes, gradients,
 * scissor, blur. The rest of the mod draws through this class.
 */
public final class Gfx {
    private Gfx() {
    }

    // Transforms and layers

    /** Saves the current transform; restore with pop. */
    //? if >=1.21.6 {
    /*public static void push(GuiGraphics g) {
        g.pose().pushMatrix();
    }

    public static void pop(GuiGraphics g) {
        g.pose().popMatrix();
    }

    /^* Translates what's drawn from now on. ^/
    public static void move(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y);
    }

    /^* Translates, and raises by z where Minecraft still has depth. ^/
    public static void move(GuiGraphics g, float x, float y, float z) {
        g.pose().translate(x, y);
    }

    /^* Draws everything after this above what was drawn before (a window over the chat, a tooltip). ^/
    public static void raise(GuiGraphics g, float z) {
        g.nextStratum();
    }

    /^* Scales what's drawn from now on. ^/
    public static void scale(GuiGraphics g, float x, float y) {
        g.pose().scale(x, y);
    }
    *///?} else {
    public static void push(GuiGraphics g) {
        g.pose().pushPose();
    }

    public static void pop(GuiGraphics g) {
        g.pose().popPose();
    }

    /** Translates what's drawn from now on. */
    public static void move(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y, 0);
    }

    /** Translates, and raises by z where Minecraft still has depth. */
    public static void move(GuiGraphics g, float x, float y, float z) {
        g.pose().translate(x, y, z);
    }

    /** Draws everything after this above what was drawn before (a window over the chat, a tooltip). */
    public static void raise(GuiGraphics g, float z) {
        g.pose().translate(0, 0, z);
    }

    /** Scales what's drawn from now on. */
    public static void scale(GuiGraphics g, float x, float y) {
        g.pose().scale(x, y, 1f);
    }
    //?}

    /**
     * Starts a new layer above everything drawn before. Needed on 26.x, which orders shapes, textures and text within a
     * layer by type, so a window over the chat needs a layer of its own.
     */
    public static void layerAbove(GuiGraphics g) {
        //? if >=1.21.6 {
        /*g.nextStratum();
        *///?}
    }

    // Text

    /** Text with a shadow. A color without alpha is drawn opaque, as older versions did. */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        text(g, font, text, x, y, color, true);
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, opaque(color), shadow);
        *///?} else {
        g.drawString(font, text, x, y, opaque(color), shadow);
        //?}
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        text(g, font, text, x, y, color, true);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, opaque(color), shadow);
        *///?} else {
        g.drawString(font, text, x, y, opaque(color), shadow);
        //?}
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        text(g, font, text, x, y, color, true);
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, opaque(color), shadow);
        *///?} else {
        g.drawString(font, text, x, y, opaque(color), shadow);
        //?}
    }

    public static void centeredText(GuiGraphics g, Font font, String text, int x, int y, int color) {
        text(g, font, text, x - font.width(text) / 2, y, color);
    }

    public static void centeredText(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        text(g, font, text, x - font.width(text) / 2, y, color);
    }

    public static void centeredText(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        text(g, font, text, x - font.width(text) / 2, y, color);
    }

    /** Text scaled down to fit maxWidth if needed (long translations on small buttons), vertically centered on its line. */
    public static void fittedText(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color, boolean centered,
                                  boolean shadow) {
        int w = font.width(text);
        if (w <= maxWidth) {
            text(g, font, text, centered ? x - w / 2 : x, y, color, shadow);
            return;
        }
        float k = maxWidth / (float) w;
        push(g);
        move(g, centered ? x - maxWidth / 2f : x, y + 4 * (1 - k));
        scale(g, k, k);
        text(g, font, text, 0, 0, color, shadow);
        pop(g);
    }

    /** 0xRRGGBB becomes opaque: older versions drew it opaque, newer ones would draw nothing. */
    private static int opaque(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    // Textures and shapes

    /** Region (u, v, uw x vh of texW x texH) of a texture in a w x h box, tinted by argb. */
    static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, float u, float v, int uw, int vh,
                     int texW, int texH, int argb) {
        //? if >=1.21.6 {
        /*float u0 = u / texW, v0 = v / texH, u1 = (u + uw) / texW, v1 = (v + vh) / texH;
        shape(g, tex, new float[]{x, y, x, y + h, x + w, y + h, x + w, y}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0},
                new int[]{argb, argb, argb, argb});
        *///?} else if >=1.21.4 {
        /*g.blit(net.minecraft.client.renderer.RenderType::guiTextured, tex, x, y, u, v, w, h, uw, vh, texW, texH, argb);
        *///?} else {
        g.setColor((argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
        //?}
    }

    /** A whole size x size texture at x, y with the given alpha. */
    static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float alpha) {
        blit(g, tex, x, y, size, size, 0, 0, size, size, size, size, white(alpha));
    }

    /** Clips drawing to this screen rectangle until unscissor, regardless of the current transform. */
    public static void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        //? if >=1.21.6 {
        /*g.pose().pushMatrix();
        g.pose().identity();
        g.enableScissor(x0, y0, x1, y1);
        g.pose().popMatrix();
        SCISSORS.push(new net.minecraft.client.gui.navigation.ScreenRectangle(x0, y0, x1 - x0, y1 - y0));
        *///?} else if >=1.21.4 {
        /*g.pose().pushPose();
        g.pose().last().pose().identity();
        g.enableScissor(x0, y0, x1, y1);
        g.pose().popPose();
        *///?} else {
        g.enableScissor(x0, y0, x1, y1);
        //?}
    }

    /** The current scissor box in screen coordinates (1.21.6+), or null. */
    //? if >=1.21.6 {
    /*public static net.minecraft.client.gui.navigation.ScreenRectangle currentScissor() {
        return SCISSORS.peek();
    }
    *///?}

    public static void unscissor(GuiGraphics g) {
        g.disableScissor();
        //? if >=1.21.6 {
        /*SCISSORS.poll();
        *///?}
    }

    /** White with this alpha (0..1), as a tint. */
    static int white(float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | 0xFFFFFF;
    }

    /**
     * A triangle fan cut out of a texture: xy are screen points (x0, y0, x1, y1...), uv texture coordinates (0..1),
     * counterclockwise like Minecraft's quads, white with the given alpha.
     */
    static void fan(GuiGraphics g, ResourceLocation tex, float[] xy, float[] uv, float alpha) {
        int a = Math.round(alpha * 255), n = xy.length / 2;
        //? if >=1.21.4 {
        /*// each triangle as a quad with the last corner repeated: GUI drawing takes quads
        int q = Math.max(0, n - 2);
        float[] qxy = new float[q * 8], quv = new float[q * 8];
        int[] col = new int[q * 4];
        java.util.Arrays.fill(col, a << 24 | 0xFFFFFF);
        for (int i = 1, o = 0; i + 1 < n; i++) {
            for (int k : new int[]{0, i, i + 1, i + 1}) {
                qxy[o] = xy[k * 2];
                qxy[o + 1] = xy[k * 2 + 1];
                quv[o] = uv[k * 2];
                quv[o + 1] = uv[k * 2 + 1];
                o += 2;
            }
        }
        *///?}
        //? if >=1.21.6 {
        /*shape(g, tex, qxy, quv, col);
        *///?} else if >=1.21.4 {
        /*Matrix4f m = g.pose().last().pose();
        g.drawSpecial(buffers -> {
            com.mojang.blaze3d.vertex.VertexConsumer b = buffers.getBuffer(net.minecraft.client.renderer.RenderType.guiTextured(tex));
            for (int k = 0; k < qxy.length / 2; k++) {
                b.addVertex(m, qxy[k * 2], qxy[k * 2 + 1], 0).setUv(quv[k * 2], quv[k * 2 + 1]).setColor(col[k]);
            }
        });
        *///?} else {
        Matrix4f m = g.pose().last().pose();
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tex);
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_FAN, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int k = 0; k < n; k++) {
            b.addVertex(m, xy[k * 2], xy[k * 2 + 1], 0).setUv(uv[k * 2], uv[k * 2 + 1]).setColor(255, 255, 255, a);
        }
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        //?}
    }

    /** Quads with a color per corner (gradients): four points per quad in xy, one color per point in argb. */
    static void shade(GuiGraphics g, float[] xy, int[] argb) {
        int n = xy.length / 2;
        //? if >=1.21.6 {
        /*shape(g, null, xy, null, argb);
        *///?} else if >=1.21.4 {
        /*Matrix4f m = g.pose().last().pose();
        g.drawSpecial(buffers -> {
            com.mojang.blaze3d.vertex.VertexConsumer b = buffers.getBuffer(net.minecraft.client.renderer.RenderType.gui());
            for (int k = 0; k < n; k++) {
                b.addVertex(m, xy[k * 2], xy[k * 2 + 1], 0).setColor(argb[k]);
            }
        });
        *///?} else {
        Matrix4f m = g.pose().last().pose();
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        for (int k = 0; k < n; k++) {
            b.addVertex(m, xy[k * 2], xy[k * 2 + 1], 0).setColor(argb[k]);
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
        //?}
    }

    //? if >=1.21.6 {
    /*/^* Textures drawn without smoothing (player faces); everything else is smoothed when scaled. ^/
    private static final java.util.Set<ResourceLocation> PIXELATED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /^* Active scissor boxes in screen coordinates, newest first. ^/
    private static final java.util.ArrayDeque<net.minecraft.client.gui.navigation.ScreenRectangle> SCISSORS = new java.util.ArrayDeque<>();

    /^* Quads (four points each), textured or plain (tex null), added to Minecraft's GUI render state. ^/
    private static void shape(GuiGraphics g, ResourceLocation tex, float[] xy, float[] uv, int[] argb) {
        org.joml.Matrix3x2f pose = new org.joml.Matrix3x2f(g.pose());
        net.minecraft.client.gui.render.TextureSetup setup;
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline;
        if (tex != null) {
            var t = Minecraft.getInstance().getTextureManager().getTexture(tex);
            //? if >=1.21.11 {
            /^var sampler = PIXELATED.contains(tex) ? t.getSampler() : com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
            setup = net.minecraft.client.gui.render.TextureSetup.singleTexture(t.getTextureView(), sampler);
            ^///?} else {
            /^setup = net.minecraft.client.gui.render.TextureSetup.singleTexture(t.getTextureView());
            ^///?}
            pipeline = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
        } else {
            setup = net.minecraft.client.gui.render.TextureSetup.noTexture();
            pipeline = net.minecraft.client.renderer.RenderPipelines.GUI;
        }
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int k = 0; k < xy.length; k += 2) {
            x0 = Math.min(x0, xy[k]);
            x1 = Math.max(x1, xy[k]);
            y0 = Math.min(y0, xy[k + 1]);
            y1 = Math.max(y1, xy[k + 1]);
        }
        var scissor = SCISSORS.peek();
        var bounds = new net.minecraft.client.gui.navigation.ScreenRectangle((int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1).transformMaxBounds(pose);
        var shape = new Shape(pipeline, setup, pose, xy, uv, argb, scissor, scissor != null ? scissor.intersection(bounds) : bounds);
        //? if >=26.1 {
        /^((com.sinuxoff.ingamemessenger.mixin.GuiGraphicsAccessor) g).ingamemessenger$state().addGuiElement(shape);
        ^///?} else {
        /^((com.sinuxoff.ingamemessenger.mixin.GuiGraphicsAccessor) g).ingamemessenger$state().submitGuiElement(shape);
        ^///?}
    }

    /^* A custom element in Minecraft's GUI render state. ^/
    private record Shape(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, net.minecraft.client.gui.render.TextureSetup textureSetup,
                         org.joml.Matrix3x2f pose, float[] xy, float[] uv, int[] argb,
                         net.minecraft.client.gui.navigation.ScreenRectangle scissorArea,
                         net.minecraft.client.gui.navigation.ScreenRectangle bounds)
            implements net.minecraft.client.gui.render.state.GuiElementRenderState {
        //? if >=1.21.9 {
        /^@Override
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer b) {
            build(b, 0);
        }
        ^///?} else {
        /^@Override
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer b, float z) {
            build(b, z);
        }
        ^///?}

        private void build(com.mojang.blaze3d.vertex.VertexConsumer b, float z) {
            for (int k = 0; k < xy.length / 2; k++) {
                //? if >=1.21.9 {
                /^var v = b.addVertexWith2DPose(pose, xy[k * 2], xy[k * 2 + 1]);
                ^///?} else {
                /^var v = b.addVertexWith2DPose(pose, xy[k * 2], xy[k * 2 + 1], z);
                ^///?}
                if (uv != null) {
                    v = v.setUv(uv[k * 2], uv[k * 2 + 1]);
                }
                v.setColor(argb[k]);
            }
        }
    }
    *///?}

    /** Draw this texture without smoothing (player faces). */
    static void pixelated(ResourceLocation tex) {
        //? if >=1.21.11 {
        /*PIXELATED.add(tex);
        *///?}
    }

    /** Smooth this texture when scaled (from 1.21.11 everything but faces is). */
    static void smooth(net.minecraft.client.renderer.texture.AbstractTexture texture) {
        //? if <1.21.11 {
        texture.setFilter(true, false);
        SMOOTH.add(texture);
        //?}
    }

    /** Textures we smooth (TextureFilterMixin keeps it on in 1.21.4-1.21.5). */
    private static final java.util.Set<net.minecraft.client.renderer.texture.AbstractTexture> SMOOTH =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    public static boolean isSmooth(net.minecraft.client.renderer.texture.AbstractTexture texture) {
        return SMOOTH.contains(texture);
    }

    static void smooth(ResourceLocation tex) {
        smooth(Minecraft.getInstance().getTextureManager().getTexture(tex));
    }

    /** Fades everything drawn after this by alpha; fade(g, 1) ends it. Does nothing on 26.x, which has no such tint. */
    static void fade(GuiGraphics g, float alpha) {
        //? if >=1.21.4 && <1.21.6 {
        /*g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        *///?} else if <1.21.4 {
        g.setColor(1f, 1f, 1f, alpha);
        //?}
    }

    /** Blurs what's drawn so far, like Minecraft does behind its menus. */
    public static void blur(GuiGraphics g, float partialTick) {
        //? if >=1.21.6 {
        /*g.blurBeforeThisStratum();
        *///?} else if >=1.21.5 {
        /*Minecraft.getInstance().gameRenderer.processBlurEffect();
        *///?} else if >=1.21.4 {
        /*Minecraft.getInstance().gameRenderer.processBlurEffect();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        *///?} else {
        Minecraft.getInstance().gameRenderer.processBlurEffect(partialTick);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        //?}
    }

    static void item(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=26.1 {
        /*g.item(stack, x, y);
        *///?} else {
        g.renderItem(stack, x, y);
        //?}
    }

    /** Tooltip at the mouse, above everything. */
    static void tooltip(GuiGraphics g, Font font, java.util.List<Component> lines, int mx, int my) {
        //? if >=1.21.6 {
        /*g.setTooltipForNextFrame(font, lines.stream().map(Component::getVisualOrderText).toList(), mx, my);
        *///?} else {
        g.renderComponentTooltip(font, lines, mx, my);
        //?}
    }

    /** One pixel wide rectangle outline. */
    static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Partial tick (0..1) for drawing between ticks. */
    static float partialTick() {
        //? if >=1.21.4 {
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        *///?} else {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        //?}
    }
}
