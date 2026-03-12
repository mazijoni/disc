package com.customdiscs.client.screen;

import com.customdiscs.block.TrainSpeakerBlockEntity;
import com.customdiscs.menu.TrainSpeakerMenu;
import com.customdiscs.network.PacketHandler;
import com.customdiscs.network.UpdateSpeakerPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * Vanilla-style GUI for the Train Announcement Speaker.
 *
 * <p>Layout (all Y values relative to {@code top}):
 * <pre>
 *   5   Title
 *  17   Format label
 *  27   Format EditBox (h=14)
 *  45   Color/style buttons (1 row, 20 × 12 px = 240 px wide)
 *  62   Preview text
 *  76   Separator
 *  80   Per-Station label
 *  91   Station rows (4 visible × 20 px = 80 px)
 * 172   Separator
 * 176   Add-Station label
 * 188   Add EditBox + Plus button
 * 206   Save button
 * 222   Bottom
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class TrainSpeakerScreen extends AbstractContainerScreen<TrainSpeakerMenu> {

    // ── Dimensions ────────────────────────────────────────────────────────────
    private static final int BG_W        = 248;
    private static final int BG_H        = 222;
    private static final int ROW_H       = 20;
    private static final int MAX_VISIBLE = 4;

    // Standard vanilla inventory texture for the panel background
    private static final ResourceLocation GUI_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    // Color/style insert codes: [display label, code to insert]
    private static final String[][] COLOR_BTNS = {
        {"0","§0"},{"1","§1"},{"2","§2"},{"3","§3"},{"4","§4"},
        {"5","§5"},{"6","§6"},{"7","§7"},{"8","§8"},{"9","§9"},
        {"a","§a"},{"b","§b"},{"c","§c"},{"d","§d"},{"e","§e"},
        {"f","§f"},{"B","§l"},{"I","§o"},{"U","§n"},{"R","§r"},
    };

    // ── Widgets ───────────────────────────────────────────────────────────────
    private EditBox formatField;
    private EditBox addStationField;
    private final LinkedHashMap<String, EditBox> stationFields = new LinkedHashMap<>();

    // ── State ─────────────────────────────────────────────────────────────────
    private int scrollOffset = 0;

    public TrainSpeakerScreen(TrainSpeakerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = BG_W;
        this.imageHeight = BG_H;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        final int left = (this.width  - BG_W) / 2;
        final int top  = (this.height - BG_H) / 2;

        // ── Format EditBox  (y = top+27, h=14) ───────────────────────────────
        formatField = new EditBox(this.font, left + 4, top + 27, BG_W - 8, 14,
                Component.literal("Format"));
        formatField.setMaxLength(512);
        formatField.setValue(this.menu.getGlobalFormat());
        formatField.setHint(Component.literal(TrainSpeakerBlockEntity.DEFAULT_FORMAT)
                .withStyle(ChatFormatting.GRAY));
        this.addWidget(formatField);

        // ── Color/style buttons (y = top+45, each 12×12) ─────────────────────
        // 20 buttons × 12 px = 240 px, centred in BG_W=248 → 4 px margin each side
        final int btnY   = top + 45;
        final int btnW   = 12;
        final int startX = left + 4;           // 4 px left inset
        for (int i = 0; i < COLOR_BTNS.length; i++) {
            final String label = COLOR_BTNS[i][0];
            final String code  = COLOR_BTNS[i][1];
            final int bx = startX + i * btnW;
            this.addRenderableWidget(
                Button.builder(Component.literal(code + label), pressed -> {
                    EditBox target = getFocusedEditBox();
                    if (target == null) target = formatField;
                    String cur = target.getValue();
                    int pos    = target.getCursorPosition();
                    target.setValue(cur.substring(0, pos) + code + cur.substring(pos));
                    target.setCursorPosition(pos + code.length());
                })
                .bounds(bx, btnY, btnW, 12)
                .build());
        }

        // ── Per-station EditBoxes (y = top+91, stride=ROW_H=20) ──────────────
        stationFields.clear();
        for (var e : this.menu.getCustomAnnouncements().entrySet())
            addStationRow(e.getKey(), e.getValue(), left, top);

        // ── Add-station row  (y = top+188, h=14) ─────────────────────────────
        addStationField = new EditBox(this.font, left + 4, top + 188, BG_W - 52, 14,
                Component.literal("Station name"));
        addStationField.setMaxLength(64);
        addStationField.setHint(Component.literal("Station name…")
                .withStyle(ChatFormatting.GRAY));
        this.addWidget(addStationField);

        // "+ Add" button (right of add field)
        this.addRenderableWidget(
            Button.builder(Component.literal("+ Add"), pressed -> {
                String name = addStationField.getValue().trim();
                if (!name.isEmpty() && !stationFields.containsKey(name)) {
                    addStationRow(name, "", left, top);
                    addStationField.setValue("");
                }
            })
            .bounds(left + BG_W - 46, top + 188, 46, 14)
            .build());

        // ── Save button  (y = top+206, h=16) ─────────────────────────────────
        this.addRenderableWidget(
            Button.builder(Component.literal("Save"), pressed -> {
                sendPacket();
                this.onClose();
            })
            .bounds(left + BG_W / 2 - 40, top + 206, 80, 16)
            .build());
    }

    /** Creates an EditBox widget for a station row (hidden until render repositions it). */
    private void addStationRow(String name, String value, int left, int top) {
        EditBox box = new EditBox(this.font, left + 72, top + 91, BG_W - 76, 14,
                Component.literal(name));
        box.setMaxLength(256);
        box.setValue(value);
        box.setHint(Component.literal("Custom text (§ codes ok)")
                .withStyle(ChatFormatting.GRAY));
        this.addWidget(box);
        stationFields.put(name, box);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    /**
     * Returns the currently focused EditBox, or {@code null} if none is focused.
     */
    private EditBox getFocusedEditBox() {
        if (formatField.isFocused()) return formatField;
        for (EditBox b : stationFields.values()) if (b.isFocused()) return b;
        if (addStationField.isFocused()) return addStationField;
        return null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        EditBox focused = getFocusedEditBox();
        if (focused != null) {
            if (key == 256) { focused.setFocused(false); return true; }  // Escape
            if ((key == 257 || key == 335) && focused == addStationField) {
                // Enter in the add-station field
                String name = addStationField.getValue().trim();
                if (!name.isEmpty() && !stationFields.containsKey(name)) {
                    addStationRow(name, "", (this.width - BG_W) / 2, (this.height - BG_H) / 2);
                    addStationField.setValue("");
                }
                return true;
            }
            return focused.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        EditBox focused = getFocusedEditBox();
        if (focused != null) return focused.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, stationFields.size() - MAX_VISIBLE);
        scrollOffset  = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta));
        return true;
    }

    // ── Packet ────────────────────────────────────────────────────────────────

    private void sendPacket() {
        String fmt = formatField.getValue().trim();
        if (fmt.isEmpty()) fmt = TrainSpeakerBlockEntity.DEFAULT_FORMAT;
        Map<String, String> map = new LinkedHashMap<>();
        for (var e : stationFields.entrySet()) map.put(e.getKey(), e.getValue().getValue().trim());
        PacketHandler.CHANNEL.sendToServer(
                new UpdateSpeakerPacket(this.menu.getBlockPos(), fmt, map));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        this.renderBackground(g);   // dirt/stone background

        final int left = (this.width  - BG_W) / 2;
        final int top  = (this.height - BG_H) / 2;

        // ── Vanilla-style grey panel ──────────────────────────────────────────
        // Fill with standard inventory grey
        g.fill(left,     top,     left + BG_W, top + BG_H, 0xFFC6C6C6);
        // Highlight (top-left edges, lighter)
        g.fill(left,     top,     left + BG_W - 1, top + 1,     0xFFFFFFFF);
        g.fill(left,     top,     left + 1,     top + BG_H - 1, 0xFFFFFFFF);
        // Shadow (bottom-right edges, darker)
        g.fill(left + 1, top + BG_H - 1, left + BG_W, top + BG_H, 0xFF555555);
        g.fill(left + BG_W - 1, top + 1, left + BG_W, top + BG_H, 0xFF555555);
        // Inner inset on the format EditBox area (matching vanilla "slot" look)
        drawInset(g, left + 3,  top + 26, BG_W - 6, 16);
        // Inner inset rows (station slots) will be done during render loop below

        // ── Title ─────────────────────────────────────────────────────────────
        g.drawString(this.font, "Train Announcement Speaker", left + 5, top + 5, 0x404040, false);

        // ── Format label ──────────────────────────────────────────────────────
        g.drawString(this.font, "Format  ({station}, {message}, §-codes):",
                left + 5, top + 17, 0x404040, false);
        formatField.render(g, mx, my, partial);

        // ── Color button labels (the buttons are already rendered by super) ───
        // Draw a thin separator above buttons
        g.fill(left + 3, top + 43, left + BG_W - 3, top + 44, 0xFF888888);

        super.render(g, mx, my, partial);   // renders all addRenderableWidget items

        // ── Preview strip ─────────────────────────────────────────────────────
        // Note: drawn AFTER super.render so it appears above button fills
        String previewFmt  = formatField.getValue().isEmpty()
                ? TrainSpeakerBlockEntity.DEFAULT_FORMAT : formatField.getValue();
        String previewText = previewFmt
                .replace("{station}", "Central Station")
                .replace("{message}", "Now arriving at Central Station");
        // Inset box at top+62
        drawInset(g, left + 3, top + 61, BG_W - 6, 13);
        g.fill(left + 4,  top + 62, left + BG_W - 4, top + 74, 0xFF1A1A2E);
        // Clip text to fit
        String clipped = previewText;
        if (this.font.width(clipped) > BG_W - 12) {
            clipped = this.font.plainSubstrByWidth(clipped, BG_W - 18) + "…";
        }
        g.drawString(this.font, clipped, left + 6, top + 64, 0xFFFFFF, false);

        // ── Per-station section header ────────────────────────────────────────
        g.fill(left + 3, top + 76, left + BG_W - 3, top + 77, 0xFF888888);  // separator
        g.drawString(this.font, "Per-Station Messages:", left + 5, top + 80, 0x404040, false);

        // ── Station rows ──────────────────────────────────────────────────────
        List<Map.Entry<String, EditBox>> entries = new ArrayList<>(stationFields.entrySet());
        final int listTop = top + 91;
        for (int i = 0; i < MAX_VISIBLE && i + scrollOffset < entries.size(); i++) {
            var entry = entries.get(i + scrollOffset);
            int y = listTop + i * ROW_H;

            // Row background (slot-like inset)
            drawInset(g, left + 3, y - 1, BG_W - 6, 16);
            g.fill(left + 4, y, left + BG_W - 4, y + 14, 0xFFFFFFFF);

            // Station label on the left
            String label = entry.getKey();
            if (this.font.width(label) > 64)
                label = this.font.plainSubstrByWidth(label, 58) + "…";
            g.drawString(this.font, label, left + 6, y + 3, 0x404040, false);

            // Position and show the EditBox
            EditBox box = entry.getValue();
            box.setX(left + 72);
            box.setY(y + 1);
            box.setWidth(BG_W - 76);
            box.setHeight(12);
            box.visible = true;
            box.render(g, mx, my, partial);
        }
        // Hide offscreen boxes
        for (int i = 0; i < entries.size(); i++) {
            if (i < scrollOffset || i >= scrollOffset + MAX_VISIBLE)
                entries.get(i).getValue().visible = false;
        }
        // Scroll hint
        if (entries.size() > MAX_VISIBLE) {
            g.drawString(this.font, "  ▲ ▼ scroll",
                    left + BG_W - 70, listTop + MAX_VISIBLE * ROW_H + 1, 0x606060, false);
        }

        // ── Add station section ───────────────────────────────────────────────
        g.fill(left + 3, top + 172, left + BG_W - 3, top + 173, 0xFF888888);
        g.drawString(this.font, "Add Station:", left + 5, top + 176, 0x404040, false);
        // Inset for the EditBox
        drawInset(g, left + 3, top + 187, BG_W - 6, 16);
        addStationField.render(g, mx, my, partial);
    }

    /**
     * Draws a standard Minecraft inset border (dark top-left, light bottom-right)
     * around the given region.
     */
    private void drawInset(GuiGraphics g, int x, int y, int w, int h) {
        // Outer dark shadow
        g.fill(x,         y,         x + w,     y + 1,     0xFF373737);  // top
        g.fill(x,         y,         x + 1,     y + h,     0xFF373737);  // left
        // Inner highlight
        g.fill(x + 1,     y + h - 1, x + w,     y + h,     0xFFFFFFFF);  // bottom
        g.fill(x + w - 1, y + 1,     x + w,     y + h,     0xFFFFFFFF);  // right
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        // No-op: we draw everything in render()
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // No-op: labels drawn directly in render() with absolute coords
    }
}
