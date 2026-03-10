package com.customdiscs.client.screen;

import com.customdiscs.network.PacketHandler;
import com.customdiscs.network.UpdateSpeakerPacket;
import com.customdiscs.menu.TrainSpeakerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class TrainSpeakerScreen extends AbstractContainerScreen<TrainSpeakerMenu> {

    private static final int BG_W = 240;
    private static final int BG_H = 200;
    private static final int ROW_H = 22;
    private static final int MAX_VISIBLE = 5;

    private final LinkedHashMap<String, EditBox> stationFields = new LinkedHashMap<>();
    private EditBox addStationField;
    private int scrollOffset = 0;

    public TrainSpeakerScreen(TrainSpeakerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = BG_W;
        this.imageHeight = BG_H;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - BG_W) / 2;
        int top  = (this.height - BG_H) / 2;

        stationFields.clear();
        for (var e : this.menu.getCustomAnnouncements().entrySet())
            createFieldForStation(e.getKey(), e.getValue(), left, top);

        addStationField = new EditBox(this.font, left + 8, top + BG_H - 56, BG_W - 60, 14,
                Component.literal("New station…"));
        addStationField.setMaxLength(64);
        addStationField.setHint(Component.literal("Station name...")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.addWidget(addStationField);

        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
            String name = addStationField.getValue().trim();
            if (!name.isEmpty() && !stationFields.containsKey(name)) {
                createFieldForStation(name, "", left, top);
                addStationField.setValue("");
            }
        }).bounds(left + BG_W - 48, top + BG_H - 56, 40, 14).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.customdiscs.train_speaker.save"), btn -> {
                    sendPacket();
                    this.onClose();
                }).bounds(left + BG_W / 2 - 48, top + BG_H - 32, 96, 18).build());
    }

    private void createFieldForStation(String name, String value, int left, int top) {
        EditBox box = new EditBox(this.font, left + 80, top, BG_W - 100, 14, Component.literal(name));
        box.setMaxLength(256);
        box.setValue(value);
        box.setHint(Component.literal("Now arriving at " + name)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.addWidget(box);
        stationFields.put(name, box);
    }

    private Map<String, String> collectAnnouncements() {
        Map<String, String> map = new HashMap<>();
        for (var e : stationFields.entrySet())
            map.put(e.getKey(), e.getValue().getValue().trim());
        return map;
    }

    private void sendPacket() {
        PacketHandler.CHANNEL.sendToServer(
                new UpdateSpeakerPacket(this.menu.getBlockPos(), collectAnnouncements()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : stationFields.values()) {
            if (box.isFocused()) {
                if (keyCode == 256) { box.setFocused(false); return true; }
                return box.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        if (addStationField != null && addStationField.isFocused()) {
            if (keyCode == 256) { addStationField.setFocused(false); return true; }
            if (keyCode == 257 || keyCode == 335) {
                String name = addStationField.getValue().trim();
                if (!name.isEmpty() && !stationFields.containsKey(name)) {
                    int left = (this.width - BG_W) / 2;
                    int top  = (this.height - BG_H) / 2;
                    createFieldForStation(name, "", left, top);
                    addStationField.setValue("");
                }
                return true;
            }
            return addStationField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (EditBox box : stationFields.values())
            if (box.isFocused()) return box.charTyped(c, modifiers);
        if (addStationField != null && addStationField.isFocused())
            return addStationField.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, stationFields.size() - MAX_VISIBLE);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int left = (this.width - BG_W) / 2;
        int top  = (this.height - BG_H) / 2;

        g.fill(left, top, left + BG_W, top + BG_H, 0xCC1A1A2E);
        g.fill(left, top, left + BG_W, top + 1, 0xFF5599FF);
        g.fill(left, top, left + 1, top + BG_H, 0xFF5599FF);
        g.fill(left + BG_W - 1, top, left + BG_W, top + BG_H, 0xFF5599FF);
        g.fill(left, top + BG_H - 1, left + BG_W, top + BG_H, 0xFF5599FF);

        g.drawString(this.font, "Train Announcement Speaker", left + 8, top + 6, 0xFFFFFF);
        g.drawString(this.font, "Per-Station Announcements:", left + 8, top + 20, 0xAAAAAA);

        List<Map.Entry<String, EditBox>> entries = new ArrayList<>(stationFields.entrySet());
        int listTop = top + 34;
        for (int i = 0; i < MAX_VISIBLE && i + scrollOffset < entries.size(); i++) {
            var entry = entries.get(i + scrollOffset);
            int y = listTop + i * ROW_H;
            String label = entry.getKey();
            if (this.font.width(label) > 68)
                label = this.font.plainSubstrByWidth(label, 62) + "...";
            g.drawString(this.font, label, left + 8, y + 3, 0x88CCFF);

            EditBox box = entry.getValue();
            box.setX(left + 80); box.setY(y); box.setWidth(BG_W - 100);
            box.visible = true;
            box.render(g, mouseX, mouseY, partialTick);
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i < scrollOffset || i >= scrollOffset + MAX_VISIBLE)
                entries.get(i).getValue().visible = false;
        }
        if (entries.size() > MAX_VISIBLE)
            g.drawString(this.font, "▲▼ scroll", left + BG_W - 60, listTop + MAX_VISIBLE * ROW_H + 2, 0x666666);

        g.fill(left + 4, top + BG_H - 66, left + BG_W - 4, top + BG_H - 65, 0xFF5599FF);
        g.drawString(this.font, "Add Station:", left + 8, top + BG_H - 62, 0xAAAAAA);
        addStationField.render(g, mouseX, mouseY, partialTick);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {}
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
}
