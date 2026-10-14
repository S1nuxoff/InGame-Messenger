package com.sinuxoff.ingamemessenger.client.compat;

//? if <1.20 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/^*
 * Minecraft's GuiGraphicsExtractor (1.20+) for older versions: the part of it the mod draws with, on a PoseStack. The sources
 * use GuiGraphicsExtractor everywhere; before 1.20 the build points them here, and each hook wraps the PoseStack it gets.
 ^/
public final class GuiGraphicsExtractor {
    private final PoseStack pose;

    public GuiGraphicsExtractor(PoseStack pose) {
        this.pose = pose;
    }

    public PoseStack pose() {
        return pose;
    }

    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /^* Nothing is batched before 1.20. ^/
    public void flush() {
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public void setColor(float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
    }

    public void blit(Identifier tex, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        GuiComponent.blit(pose, x, y, w, h, u, v, uw, vh, texW, texH);
    }

    /^* Clips to a GUI-space rectangle, like Minecraft's own (no nesting needed here). ^/
    public void enableScissor(int x0, int y0, int x1, int y1) {
        var window = Minecraft.getInstance().getWindow();
        double s = window.getGuiScale();
        RenderSystem.enableScissor((int) (x0 * s), (int) (window.getHeight() - y1 * s), Math.max(0, (int) ((x1 - x0) * s)),
                Math.max(0, (int) ((y1 - y0) * s)));
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    /^* The item renderer works on the model-view stack, so our transform goes there for the call. ^/
    public void renderItem(ItemStack stack, int x, int y) {
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
        view.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen != null) {
            screen.renderComponentTooltip(pose, lines, x, y);
        }
    }
}
*///?}
