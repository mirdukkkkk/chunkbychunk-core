package uk.mirdukkkkk.chunkbychunk.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import uk.mirdukkkkk.chunkbychunk.common.ChunkByChunkConstants;
import uk.mirdukkkkk.chunkbychunk.common.menus.BedrockChestMenu;

/**
 * Screen for the Bedrock Chest
 */
public class BedrockChestScreen extends AbstractContainerScreen<BedrockChestMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE = ResourceLocation.fromNamespaceAndPath(ChunkByChunkConstants.MOD_ID, "textures/gui/container/bedrockchest.png");

    public BedrockChestScreen(BedrockChestMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderTransparentBackground(graphics);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(CONTAINER_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }
}