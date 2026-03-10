package com.customdiscs.client.screen;

import com.customdiscs.network.PacketHandler;
import com.customdiscs.network.UpdateSpeakerPacket;
import com.customdiscs.menu.TrainSpeakerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for the Train Announcement Speaker block.
 * Lets the player configure the station name and a list of upcoming stops.
 */
@OnlyIn(Dist.CLIENT)
public class TrainSpeakerScreen extends AbstractContainerScreen<TrainSpeakerMenu> {

    private static final int BG_W = 220;
    private static final int BG_H = 210;

    private EditBox stationField;
    private EditBox addStopField;
    private final List<String> stops = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 5;
    private static final int ROW_H = 18;

    public TrainSpeakerScreen(TrainSpeakerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = BG_W;
        this.imageHeight = BG_H;
        // Load existing data from the menu (sent from server via openScreen buffer)
        this.stops.addAll(menu.getStops());
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - BG_W) / 2;
        int top  = (this.height - BG_H) / 2;

        // Station name field
        stationField = new EditBox(this.font, left + 8, top + 28, BG_W - 16, 16,
                Component.literal("Station name"));
        stationField.setMaxLength(64);
        stationField.setValue(this.menu.getStationName());
        this.addWidget(stationField);

        // "Add stop" field
        addStopField = new EditBox(this.font, left + 8, top + 76, BG_W - 60, 16,
                Component.literal("New stop…"));
        addStopField.setMaxLength(64);
        this.addWidget(addStopField);

        // Add button
        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            String stop = addStopField.getValue().trim();
            if (!stop.isEmpty()) {
                stops.add(stop);
                addStopField.setValue("");
            }
        }).bounds(left + BG_W - 48, top + 76, 18, 16).build());

        // Scroll up
        this.addRenderableWidget(Button.builder(Component.literal("▲"), btn -> {
            if (scrollOffset > 0) scrollOffset--;
        }).bounds(left + BG_W - 26, top + 100, 18, 16).build());

        // Scroll down
        this.addRenderableWidget(Button.builder(Component.literal("▼"), btn -> {
            if (scrollOffset + MAX_VISIBLE < stops.size()) scrollOffset++;
        }).bounds(left + BG_W - 26, top + 120, 18, 16).build());

        // Remove last stop button
        this.addRenderableWidget(Button.builder(Component.literal("✕"), btn -> {
            if (!stops.isEmpty()) stops.remove(stops.size() - 1);
            if (scrollOffset > 0 && scrollOffset >= stops.size()) scrollOffset--;
        }).bounds(left + BG_W - 26, top + 140, 18, 16).build());

        // Test Announce button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.customdiscs.train_speaker.test"), btn -> {
            sendPacket(true);
        }).bounds(left + 8, top + BG_H - 32, 96, 18).build());

        // Save button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.customdiscs.train_speaker.save"), btn -> {
            sendPacket(false);
            this.onClose();
        }).bounds(left + BG_W - 104, top + BG_H - 32, 96, 18).build());
    }

    private void sendPacket(boolean test) {
        BlockPos pos = this.menu.getBlockPos();
        PacketHandler.CHANNEL.sendToServer(
                new UpdateSpeakerPacket(pos, stationField.getValue().trim(), new ArrayList<>(stops), test));
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // If a text field is focused, all keys (except Escape) go to it — prevents
        // 'E' from closing the screen while typing.
        if (stationField != null && stationField.isFocused()) {
            if (keyCode == 256) { // Escape
                stationField.setFocused(false);
                return true;
            }
            return stationField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (addStopField != null && addStopField.isFocused()) {
            if (keyCode == 256) { // Escape
                addStopField.setFocused(false);
                return true;
            }
            // Enter key adds the stop
            if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
                String stop = addStopField.getValue().trim();
                if (!stop.isEmpty()) {
                    stops.add(stop);
                    addStopField.setValue("");
                }
                return true;
            }
            return addStopField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (stationField != null && stationField.isFocused())
            return stationField.charTyped(c, modifiers);
        if (addStopField != null && addStopField.isFocused())
            return addStopField.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - BG_W) / 2;
        int top  = (this.height - BG_H) / 2;

        // Focus handling: click on a field → focus it, click elsewhere → unfocus
        if (stationField != null) {
            stationField.setFocused(
                mouseX >= stationField.getX() && mouseX < stationField.getX() + stationField.getWidth() &&
                mouseY >= stationField.getY() && mouseY < stationField.getY() + stationField.getHeight());
        }
        if (addStopField != null) {
            addStopField.setFocused(
                mouseX >= addStopField.getX() && mouseX < addStopField.getX() + addStopField.getWidth() &&
                mouseY >= addStopField.getY() && mouseY < addStopField.getY() + addStopField.getHeight());
        }

        // Check individual row ✕ buttons
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= stops.size()) break;
            int y = top + 98 + i * ROW_H;
            if (mouseX >= left + BG_W - 56 && mouseX <= left + BG_W - 32 &&
                    mouseY >= y + 1 && mouseY <= y + ROW_H - 1) {
                stops.remove(idx);
                if (scrollOffset > 0 && scrollOffset >= stops.size()) scrollOffset--;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int left = (this.width - BG_W) / 2;
        int top  = (this.height - BG_H) / 2;

        // Background panel
        g.fill(left, top, left + BG_W, top + BG_H, 0xCC1A1A2E);
        g.fill(left, top, left + BG_W, top + 1, 0xFF5599FF);
        g.fill(left, top, left + 1, top + BG_H, 0xFF5599FF);
        g.fill(left + BG_W - 1, top, left + BG_W, top + BG_H, 0xFF5599FF);
        g.fill(left, top + BG_H - 1, left + BG_W, top + BG_H, 0xFF5599FF);

        // Title
        g.drawString(this.font, "Train Announcement Speaker", left + 8, top + 8, 0xFFFFFF);

        // Labels
        g.drawString(this.font, "Station Name:", left + 8, top + 18, 0xAAAAAA);
        g.drawString(this.font, "Add Stop:", left + 8, top + 66, 0xAAAAAA);

        // Stop list box
        g.fill(left + 8, top + 96, left + BG_W - 30, top + 96 + MAX_VISIBLE * ROW_H + 4, 0xFF0D0D1A);
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int idx = i + scrollOffset;
            if (idx >= stops.size()) break;
            int y = top + 98 + i * ROW_H;
            String label = (idx + 1) + ". " + stops.get(idx);
            g.drawString(this.font, label, left + 12, y + 4, 0xFFFFFF);
            // Per-row remove button
            g.fill(left + BG_W - 56, y + 1, left + BG_W - 32, y + ROW_H - 1, 0x88CC2222);
            g.drawCenteredString(this.font, "x", left + BG_W - 44, y + 5, 0xFFAAAA);
        }

        // Render text fields
        stationField.render(g, mouseX, mouseY, partialTick);
        addStopField.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Background drawn in render()
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Labels drawn in render() with absolute coords
    }
}
