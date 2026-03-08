package com.customdiscs.client.screen;

import com.customdiscs.menu.DiscRecorderMenu;
import com.customdiscs.network.DiscCraftPacket;
import com.customdiscs.network.PacketHandler;
import com.customdiscs.util.SoundRegistryHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class DiscRecorderScreen extends AbstractContainerScreen<DiscRecorderMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("customdiscs", "textures/gui/disc_recorder.png");

    // Vanilla-standard panel width; height tall enough for our widgets
    private static final int GUI_W = 200;
    private static final int GUI_H = 200;

    // Vanilla UI colours
    private static final int COL_LABEL  = 0x404040;   // same as vanilla inventory labels
    private static final int COL_TITLE  = 0x404040;
    private static final int COL_SOUND  = 0x207020;   // dark green for loaded-sounds list
    private static final int COL_STATUS = 0xFFFFFF;

    private EditBox pathField;
    private EditBox titleField;
    private AbstractSliderButton volumeSlider;
    private float discVolume = 0.35f;
    private Button cutButton;
    private Button browseButton;
    private String statusMessage = "";
    private long statusTime = 0;

    public DiscRecorderScreen(DiscRecorderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = GUI_W;
        this.imageHeight = GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // Path field — narrower to leave room for Browse button
        pathField = new EditBox(this.font, x + 8, y + 35, GUI_W - 78, 16,
                Component.translatable("customdiscs.gui.file_path"));
        pathField.setMaxLength(512);
        pathField.setHint(Component.literal("C:\\Music\\mysong.ogg").withStyle(
                net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(pathField);

        // Browse button
        browseButton = Button.builder(
                Component.literal("Browse..."),
                btn -> openFileBrowser()
        ).pos(x + GUI_W - 68, y + 34).size(60, 18).build();
        this.addRenderableWidget(browseButton);

        // Song title field — 4px below path field bottom (y+35+16=y+51), label at y+57, field at y+68
        titleField = new EditBox(this.font, x + 8, y + 68, GUI_W - 16, 16,
                Component.translatable("customdiscs.gui.song_title"));
        titleField.setMaxLength(64);
        titleField.setHint(Component.literal("My Favourite Song").withStyle(
                net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(titleField);

        // Volume slider — label at y+90, slider at y+101
        volumeSlider = new AbstractSliderButton(x + 8, y + 101, GUI_W - 16, 14,
                Component.empty(), 0.35) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Volume: " + Math.round(this.value * 100) + "%"));
            }
            @Override
            protected void applyValue() {
                discVolume = (float) this.value;
            }
        };
        this.addRenderableWidget(volumeSlider);

        // Cut Disc button — 6px below slider bottom (y+101+14=y+115), button at y+121
        cutButton = Button.builder(
                Component.translatable("customdiscs.gui.cut_disc"),
                btn -> sendCraftRequest()
        ).pos(x + GUI_W / 2 - 50, y + 121).size(100, 20).build();
        this.addRenderableWidget(cutButton);
    }

    // ─── File picker ────────────────────────────────────────────────────────────

    private void openFileBrowser() {
        browseButton.active = false;
        showStatus("Opening file browser...", false);

        Thread t = new Thread(() -> {
            try {
                String psScript =
                    "Add-Type -AssemblyName System.Windows.Forms;" +
                    "$d = New-Object System.Windows.Forms.OpenFileDialog;" +
                    "$d.Title = 'Select an OGG audio file';" +
                    "$d.Filter = 'OGG Audio (*.ogg)|*.ogg';" +
                    "$d.Multiselect = $false;" +
                    (pathField.getValue().trim().isEmpty() ? "" :
                        "$d.InitialDirectory = [System.IO.Path]::GetDirectoryName('" +
                        pathField.getValue().trim().replace("'", "''") + "');") +
                    "if ($d.ShowDialog() -eq 'OK') { Write-Output $d.FileName }";

                ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-Command", psScript
                );
                pb.redirectErrorStream(false);
                Process proc = pb.start();

                String chosen = null;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) chosen = line;
                    }
                }
                proc.waitFor();

                final String result = chosen;
                this.minecraft.execute(() -> {
                    browseButton.active = true;
                    if (result != null && !result.isEmpty()) {
                        pathField.setValue(result);
                        if (titleField.getValue().trim().isEmpty()) {
                            File f = new File(result);
                            String name = f.getName();
                            int dot = name.lastIndexOf('.');
                            if (dot > 0) name = name.substring(0, dot);
                            name = name.replace('_', ' ').replace('-', ' ');
                            if (!name.isEmpty())
                                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                            titleField.setValue(name);
                        }
                        showStatus("File selected!", false);
                    } else {
                        showStatus("No file selected.", false);
                    }
                });
            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    browseButton.active = true;
                    showStatus("§cCould not open file browser: " + e.getMessage(), true);
                });
            }
        }, "CustomDiscs-FilePicker");
        t.setDaemon(true);
        t.start();
    }

    // ─── Craft ──────────────────────────────────────────────────────────────────

    private void sendCraftRequest() {
        String path  = pathField.getValue().trim();
        String title = titleField.getValue().trim();

        if (path.isEmpty() || !path.toLowerCase().endsWith(".ogg")) {
            showStatus("§cFile path must point to a .ogg file!", true);
            return;
        }
        if (title.isEmpty()) {
            showStatus("§cPlease enter a song title!", true);
            return;
        }

        cutButton.active = false;
        showStatus("Sending to server...", false);

        PacketHandler.CHANNEL.sendToServer(
                new DiscCraftPacket(menu.getBlockPos(), path, title, discVolume));
    }

    public void handleServerResponse(String msg) {
        cutButton.active = true;
        if (msg.startsWith("success:")) {
            String soundId = msg.substring(8);
            showStatus("§a✔ Disc created: " + soundId, false);
            SoundRegistryHelper.reloadClientSounds();
        } else if (msg.equals("need_blank")) {
            showStatus("§e⚠ Need a blank Custom Disc — press an Unassembled Disc first!", true);
        } else {
            showStatus("§c✘ " + msg, true);
        }
    }

    private void showStatus(String msg, boolean error) {
        this.statusMessage = msg;
        this.statusTime    = System.currentTimeMillis();
    }

    // ─── Input routing (keep hotkeys from closing GUI while typing) ───────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean pathFocused  = pathField  != null && pathField.isFocused();
        boolean titleFocused = titleField != null && titleField.isFocused();

        if (pathFocused || titleFocused) {
            if (keyCode == 256) {           // Escape → unfocus, don't close
                setFocused(null);
                if (pathFocused)  pathField.setFocused(false);
                if (titleFocused) titleField.setFocused(false);
                return true;
            }
            if (keyCode == 258) {           // Tab → cycle fields
                if (pathFocused) {
                    pathField.setFocused(false);
                    titleField.setFocused(true);
                    setFocused(titleField);
                } else {
                    titleField.setFocused(false);
                    pathField.setFocused(true);
                    setFocused(pathField);
                }
                return true;
            }
            if (pathFocused)  return pathField.keyPressed(keyCode, scanCode, modifiers);
            if (titleFocused) return titleField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (pathField  != null && pathField.isFocused())  return pathField.charTyped(c, modifiers);
        if (titleField != null && titleField.isFocused()) return titleField.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    // ─── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // ── Vanilla-style stone-grey panel built from fills ──────────────────
        // Outer dark border
        graphics.fill(x,             y,             x + GUI_W,     y + GUI_H,     0xFF373737);
        // Main grey face
        graphics.fill(x + 1,         y + 1,         x + GUI_W - 1, y + GUI_H - 1, 0xFFC6C6C6);
        // Top-left inner shadow (dark)
        graphics.fill(x + 1,         y + 1,         x + GUI_W - 1, y + 2,         0xFF8B8B8B);
        graphics.fill(x + 1,         y + 1,         x + 2,         y + GUI_H - 1, 0xFF8B8B8B);
        // Bottom-right inner highlight (bright)
        graphics.fill(x + GUI_W - 2, y + 1,         x + GUI_W - 1, y + GUI_H - 1, 0xFFFFFFFF);
        graphics.fill(x + 1,         y + GUI_H - 2, x + GUI_W - 1, y + GUI_H - 1, 0xFFFFFFFF);

        // ── Horizontal title separator line ──────────────────────────────────
        graphics.fill(x + 2,         y + 19,        x + GUI_W - 2, y + 20,        0xFF8B8B8B);
        graphics.fill(x + 2,         y + 20,        x + GUI_W - 2, y + 21,        0xFFFFFFFF);

        // ── Section labels ────────────────────────────────────────────────────
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.file_path"),
                x + 8, y + 25, COL_LABEL, false);      // label above path field (y+35)
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.song_title"),
                x + 8, y + 57, COL_LABEL, false);      // label above title field (y+68)
        graphics.drawString(this.font,
                Component.literal("Volume"),
                x + 8, y + 90, COL_LABEL, false);      // label above slider (y+101)

        // ── Thin separator above sounds list ─────────────────────────────────
        graphics.fill(x + 2,  y + 147, x + GUI_W - 2, y + 148, 0xFF8B8B8B);
        graphics.fill(x + 2,  y + 148, x + GUI_W - 2, y + 149, 0xFFFFFFFF);

        // ── Loaded sounds list ────────────────────────────────────────────────
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.available"),
                x + 8, y + 152, COL_LABEL, false);

        List<String> sounds = SoundRegistryHelper.getLoadedSoundNames();
        int listY = y + 163;
        for (int i = 0; i < Math.min(sounds.size(), 3); i++) {
            graphics.drawString(this.font, "♪ " + sounds.get(i), x + 12, listY, COL_SOUND, false);
            listY += 10;
        }
        if (sounds.size() > 3) {
            graphics.drawString(this.font, "... and " + (sounds.size() - 3) + " more",
                    x + 12, listY, COL_LABEL, false);
        }
        if (sounds.isEmpty()) {
            graphics.drawString(this.font, "(none yet)", x + 12, listY, 0xFF888888, false);
        }

        // ── Status bar at the bottom ──────────────────────────────────────────
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusTime < 6000) {
            // thin recessed bar at the very bottom
            graphics.fill(x + 2, y + GUI_H - 14, x + GUI_W - 2, y + GUI_H - 2, 0xFF8B8B8B);
            graphics.drawString(this.font, statusMessage,
                    x + 5, y + GUI_H - 12, COL_STATUS, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title drawn at the vanilla position (8, 6) above the separator line
        graphics.drawString(this.font, this.title, 8, 7, COL_TITLE, false);
    }
}
