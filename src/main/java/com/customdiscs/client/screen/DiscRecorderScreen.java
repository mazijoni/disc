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
    private static final int GUI_W = 256;
    private static final int GUI_H = 210;

    private EditBox pathField;
    private EditBox titleField;
    private AbstractSliderButton volumeSlider;
    private float discVolume = 0.35f;  // written by the slider
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

        // Path field — slightly narrower to leave room for Browse button
        pathField = new EditBox(this.font, x + 10, y + 45, GUI_W - 90, 18,
                Component.translatable("customdiscs.gui.file_path"));
        pathField.setMaxLength(512);
        pathField.setHint(Component.literal("C:\\Music\\mysong.ogg"));
        this.addRenderableWidget(pathField);

        // Browse button next to the path field
        browseButton = Button.builder(
                Component.literal("Browse..."),
                btn -> openFileBrowser()
        ).pos(x + GUI_W - 75, y + 44).size(65, 20).build();
        this.addRenderableWidget(browseButton);

        // Song title field
        titleField = new EditBox(this.font, x + 10, y + 82, GUI_W - 20, 18,
                Component.translatable("customdiscs.gui.song_title"));
        titleField.setMaxLength(64);
        titleField.setHint(Component.literal("My Favourite Song"));
        this.addRenderableWidget(titleField);

        // Volume slider (0–100 %, default 35 %)
        volumeSlider = new AbstractSliderButton(x + 10, y + 106, GUI_W - 20, 16,
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

        // Cut Disc button — moved down to make room for the slider
        cutButton = Button.builder(
                Component.translatable("customdiscs.gui.cut_disc"),
                btn -> sendCraftRequest()
        ).pos(x + GUI_W / 2 - 55, y + 128).size(110, 20).build();
        this.addRenderableWidget(cutButton);
    }

    /**
     * Opens the native Windows file picker by running a PowerShell subprocess.
     * PowerShell shows a real Windows.Forms OpenFileDialog and prints the chosen
     * path to stdout. We read that on a background thread and pump the result
     * back to Minecraft's main thread. Works even while LWJGL owns the display.
     */
    private void openFileBrowser() {
        browseButton.active = false;
        showStatus("Opening file browser...", false);

        Thread t = new Thread(() -> {
            try {
                // Build the PowerShell one-liner that opens a native file dialog
                String psScript =
                    "Add-Type -AssemblyName System.Windows.Forms;" +
                    "$d = New-Object System.Windows.Forms.OpenFileDialog;" +
                    "$d.Title = 'Select an OGG audio file';" +
                    "$d.Filter = 'OGG Audio (*.ogg)|*.ogg';" +
                    "$d.Multiselect = $false;" +
                    // Try to pre-open the folder of any existing path in the box
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
                // Post back to Minecraft main thread
                this.minecraft.execute(() -> {
                    browseButton.active = true;
                    if (result != null && !result.isEmpty()) {
                        pathField.setValue(result);
                        // Auto-fill title from filename if title field is empty
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
            showStatus("§a✔ Disc created! Sound: " + soundId, false);
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

    // -------------------------------------------------------------------------
    // Fix: prevent inventory key (E) and other hotkeys from closing the GUI
    // while the player is typing in a text field.
    // -------------------------------------------------------------------------
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean pathFocused  = pathField  != null && pathField.isFocused();
        boolean titleFocused = titleField != null && titleField.isFocused();

        if (pathFocused || titleFocused) {
            // Escape: unfocus the active field (don't close the GUI)
            if (keyCode == 256) {
                setFocused(null);
                if (pathFocused)  pathField.setFocused(false);
                if (titleFocused) titleField.setFocused(false);
                return true;
            }

            // Tab: cycle between fields
            if (keyCode == 258) {
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

            // Route all other keys directly to the focused field,
            // SKIPPING AbstractContainerScreen's hotkey processing entirely
            if (pathFocused)  return pathField.keyPressed(keyCode, scanCode, modifiers);
            if (titleFocused) return titleField.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (pathField != null && pathField.isFocused())   return pathField.charTyped(c, modifiers);
        if (titleField != null && titleField.isFocused()) return titleField.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------
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

        // Background panels
        graphics.fill(x, y, x + GUI_W, y + GUI_H, 0xFF1A1A2E);
        graphics.fill(x + 2, y + 2, x + GUI_W - 2, y + GUI_H - 2, 0xFF16213E);

        // Header bar
        graphics.fill(x, y, x + GUI_W, y + 26, 0xFF0F3460);
        graphics.drawString(this.font,
                Component.translatable("container.customdiscs.disc_recorder"),
                x + 8, y + 8, 0xFFE94560, false);

        // Section labels
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.file_path"),
                x + 10, y + 34, 0xFFAAAAAA, false);
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.song_title"),
                x + 10, y + 71, 0xFFAAAAAA, false);
        graphics.drawString(this.font,
                Component.literal("Volume"),
                x + 10, y + 96, 0xFFAAAAAA, false);

        // Loaded sounds list
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.available"),
                x + 10, y + 142, 0xFF888888, false);

        List<String> sounds = SoundRegistryHelper.getLoadedSoundNames();
        int listY = y + 153;
        for (int i = 0; i < Math.min(sounds.size(), 3); i++) {
            graphics.drawString(this.font, "♪ " + sounds.get(i), x + 14, listY, 0xFF4CAF50, false);
            listY += 10;
        }
        if (sounds.size() > 3) {
            graphics.drawString(this.font, "... and " + (sounds.size() - 3) + " more",
                    x + 14, listY, 0xFF666666, false);
        }
        if (sounds.isEmpty()) {
            graphics.drawString(this.font, "(none yet)", x + 14, listY, 0xFF555555, false);
        }

        // Status message (disappears after 6s)
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusTime < 6000) {
            graphics.drawString(this.font, statusMessage, x + 10, y + 196, 0xFFFFFFFF, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // skip default labels (we draw our own in renderBg)
    }
}
