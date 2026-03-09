package com.customdiscs.client.screen;

import com.customdiscs.menu.DiscRecorderMenu;
import com.customdiscs.network.OggUploadPacket;
import com.customdiscs.network.PacketHandler;
import com.customdiscs.util.SoundRegistryHelper;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiscRecorderScreen extends AbstractContainerScreen<DiscRecorderMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("customdiscs", "textures/gui/disc_recorder.png");

    /**
     * Dedicated folder for player OGG files — mirrors Create's .minecraft/schematics/ pattern.
     * Players drop their .ogg files here, then hit Refresh to see them in the list.
     */
    public static final Path OGG_DIR = FMLPaths.GAMEDIR.get().resolve("customdiscs");

    private static final int GUI_W    = 200;
    private static final int GUI_H    = 220;
    private static final int LIST_ROWS = 4;
    private static final int ROW_H    = 14;

    private static final int COL_LABEL  = 0x404040;
    private static final int COL_TITLE  = 0x404040;
    private static final int COL_STATUS = 0xFFFFFF;

    // ─── State ────────────────────────────────────────────────────────────────

    private EditBox            titleField;
    private AbstractSliderButton volumeSlider;
    private float              discVolume    = 0.35f;
    private Button             openFolderBtn;
    private Button             refreshBtn;
    private Button             cutButton;

    /** Sorted list of .ogg files found in OGG_DIR */
    private final List<File> oggFiles = new ArrayList<>();
    private int              selectedIdx  = -1;
    private int              scrollOffset = 0;

    /** Upload progress tracking */
    private int     uploadSent  = 0;
    private int     uploadTotal = 0;
    private boolean uploading   = false;

    private String statusMessage = "";
    private long   statusTime    = 0;

    /** List area bounds — computed in init(), used for mouse hit-testing */
    private int listX, listY, listW, listH;

    public DiscRecorderScreen(DiscRecorderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = GUI_W;
        this.imageHeight = GUI_H;
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // Make sure the folder exists so players know where to put files
        ensureOggDirExists();
        refreshFileList();

        // "Open Folder" — opens .minecraft/customdiscs/ in the OS file manager
        openFolderBtn = Button.builder(
                Component.literal("Open Folder"),
                btn -> Util.getPlatform().openFile(OGG_DIR.toFile())
        ).pos(x + 8, y + 34).size(88, 16).build();
        this.addRenderableWidget(openFolderBtn);

        // "Refresh" — rescans OGG_DIR for new files
        refreshBtn = Button.builder(
                Component.literal("Refresh"),
                btn -> refreshFileList()
        ).pos(x + 104, y + 34).size(88, 16).build();
        this.addRenderableWidget(refreshBtn);

        // File list bounds (rendered manually; stored here for click/scroll detection)
        listX = x + 8;
        listY = y + 54;
        listW = GUI_W - 16;
        listH = LIST_ROWS * ROW_H;

        // Song title field
        titleField = new EditBox(this.font, x + 8, y + 130, GUI_W - 16, 16,
                Component.translatable("customdiscs.gui.song_title"));
        titleField.setMaxLength(64);
        titleField.setHint(Component.literal("My Favourite Song").withStyle(
                net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(titleField);

        // Volume slider
        volumeSlider = new AbstractSliderButton(x + 8, y + 162, GUI_W - 16, 14,
                Component.empty(), 0.35) {
            @Override protected void updateMessage() {
                setMessage(Component.literal("Volume: " + Math.round(this.value * 100) + "%"));
            }
            @Override protected void applyValue() {
                discVolume = (float) this.value;
            }
        };
        this.addRenderableWidget(volumeSlider);

        // Cut Disc button
        cutButton = Button.builder(
                Component.translatable("customdiscs.gui.cut_disc"),
                btn -> beginUpload()
        ).pos(x + GUI_W / 2 - 50, y + 182).size(100, 20).build();
        this.addRenderableWidget(cutButton);
    }

    // ─── File list management ─────────────────────────────────────────────────

    private void ensureOggDirExists() {
        try {
            Files.createDirectories(OGG_DIR);
        } catch (Exception e) {
            showStatus("§cCould not create customdiscs/ folder.", true);
        }
    }

    private void refreshFileList() {
        oggFiles.clear();
        selectedIdx  = -1;
        scrollOffset = 0;
        if (titleField != null) titleField.setValue("");

        File dir = OGG_DIR.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] found = dir.listFiles(
                    f -> f.isFile() && f.getName().toLowerCase().endsWith(".ogg"));
            if (found != null) {
                Arrays.sort(found, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : found) oggFiles.add(f);
            }
        }

        if (oggFiles.isEmpty()) {
            showStatus("No .ogg files found — drop some in the folder!", false);
        } else {
            showStatus(oggFiles.size() + " OGG file(s) found. Select one below.", false);
        }
    }

    private int maxScroll() {
        return Math.max(0, oggFiles.size() - LIST_ROWS);
    }

    private void selectRow(int fileIdx) {
        if (fileIdx < 0 || fileIdx >= oggFiles.size()) return;
        selectedIdx = fileIdx;
        File chosen = oggFiles.get(fileIdx);

        // Auto-fill title from filename if blank
        if (titleField != null && titleField.getValue().trim().isEmpty()) {
            String name = chosen.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            name = name.replace('_', ' ').replace('-', ' ');
            if (!name.isEmpty())
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            titleField.setValue(name);
        }
        long kb = chosen.length() / 1024;
        showStatus("Selected: " + chosen.getName() + " (" + kb + " KB)", false);
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    private void beginUpload() {
        if (uploading) return;

        if (selectedIdx < 0 || selectedIdx >= oggFiles.size()) {
            showStatus("§cSelect an OGG file from the list first!", true);
            return;
        }
        File selectedFile = oggFiles.get(selectedIdx);

        String title = titleField.getValue().trim();
        if (title.isEmpty()) {
            showStatus("§cPlease enter a song title!", true);
            return;
        }
        if (selectedFile.length() > OggUploadPacket.MAX_FILE_BYTES) {
            showStatus("§cFile too large! Max " +
                    (OggUploadPacket.MAX_FILE_BYTES / 1024 / 1024) + " MB.", true);
            return;
        }

        cutButton.active     = false;
        openFolderBtn.active = false;
        refreshBtn.active    = false;
        uploading  = true;
        uploadSent = 0; uploadTotal = 0;

        final String finalTitle  = title;
        final float  finalVolume = discVolume;
        final File   finalFile   = selectedFile;

        Thread uploadThread = new Thread(() -> {
            try {
                byte[] bytes = Files.readAllBytes(finalFile.toPath());
                int total = Math.max(1,
                        (int) Math.ceil((double) bytes.length / OggUploadPacket.CHUNK_SIZE));

                this.minecraft.execute(() -> {
                    uploadTotal = total;
                    showStatus("Uploading 0/" + total + "...", false);
                });

                for (int i = 0; i < total; i++) {
                    final int chunk = i;
                    int start = i * OggUploadPacket.CHUNK_SIZE;
                    int end   = Math.min(start + OggUploadPacket.CHUNK_SIZE, bytes.length);
                    final byte[] chunkData = Arrays.copyOfRange(bytes, start, end);

                    this.minecraft.execute(() -> {
                        uploadSent = chunk + 1;
                        showStatus("Uploading " + (chunk + 1) + "/" + total + "...", false);
                        PacketHandler.CHANNEL.sendToServer(
                                new OggUploadPacket(menu.getBlockPos(), finalTitle,
                                        finalVolume, chunk, total, chunkData));
                    });

                    Thread.sleep(2); // yield between chunks to avoid flooding Netty
                }
            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    uploading            = false;
                    cutButton.active     = true;
                    openFolderBtn.active = true;
                    refreshBtn.active    = true;
                    showStatus("§cUpload error: " + e.getMessage(), true);
                });
            }
        }, "CustomDiscs-Upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    // ─── Server response ──────────────────────────────────────────────────────

    public void handleServerResponse(String msg) {
        uploading            = false;
        cutButton.active     = true;
        openFolderBtn.active = true;
        refreshBtn.active    = true;

        if (msg.startsWith("success:")) {
            String soundId = msg.substring(8);
            showStatus("§a✔ Disc created: " + soundId, false);
            SoundRegistryHelper.reloadClientSounds();
        } else if (msg.equals("need_blank")) {
            showStatus("§e⚠ Need a blank Custom Disc in your inventory!", true);
        } else if (msg.startsWith("error:")) {
            showStatus("§c✘ " + msg.substring(6), true);
        } else {
            showStatus("§c✘ " + msg, true);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void showStatus(String msg, boolean error) {
        this.statusMessage = msg;
        this.statusTime    = System.currentTimeMillis();
    }

    // ─── Input routing ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int row     = (int) ((mouseY - listY) / ROW_H);
            int fileIdx = scrollOffset + row;
            selectRow(fileIdx);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll(), scrollOffset - delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (titleField != null && titleField.isFocused()) {
            if (keyCode == 256) { // Escape → unfocus, don't close
                setFocused(null);
                titleField.setFocused(false);
                return true;
            }
            return titleField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (titleField != null && titleField.isFocused())
            return titleField.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

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

        // ── Panel ─────────────────────────────────────────────────────────────
        graphics.fill(x,             y,             x + GUI_W,     y + GUI_H,     0xFF373737);
        graphics.fill(x + 1,         y + 1,         x + GUI_W - 1, y + GUI_H - 1, 0xFFC6C6C6);
        graphics.fill(x + 1,         y + 1,         x + GUI_W - 1, y + 2,         0xFF8B8B8B);
        graphics.fill(x + 1,         y + 1,         x + 2,         y + GUI_H - 1, 0xFF8B8B8B);
        graphics.fill(x + GUI_W - 2, y + 1,         x + GUI_W - 1, y + GUI_H - 1, 0xFFFFFFFF);
        graphics.fill(x + 1,         y + GUI_H - 2, x + GUI_W - 1, y + GUI_H - 1, 0xFFFFFFFF);

        // ── Title separator ───────────────────────────────────────────────────
        graphics.fill(x + 2, y + 19, x + GUI_W - 2, y + 20, 0xFF8B8B8B);
        graphics.fill(x + 2, y + 20, x + GUI_W - 2, y + 21, 0xFFFFFFFF);

        // ── "OGG Files" header label ──────────────────────────────────────────
        graphics.drawString(this.font,
                Component.literal("OGG Files  (.minecraft/customdiscs/)"),
                x + 8, y + 24, COL_LABEL, false);

        // ── File list box ──────────────────────────────────────────────────────
        // Inset border (dark top/left, light bottom/right)
        graphics.fill(listX - 1,     listY - 1,     listX + listW + 1, listY + listH + 1, 0xFF8B8B8B);
        graphics.fill(listX,         listY,          listX + listW,     listY + listH,     0xFFFFFFFF);
        graphics.fill(listX - 1,     listY - 1,      listX + listW,     listY,             0xFF373737);
        graphics.fill(listX - 1,     listY - 1,      listX,             listY + listH,     0xFF373737);

        // Rows
        for (int row = 0; row < LIST_ROWS; row++) {
            int fileIdx = scrollOffset + row;
            int rowY    = listY + row * ROW_H;

            if (fileIdx < oggFiles.size()) {
                boolean selected = (fileIdx == selectedIdx);
                boolean hovered  = mouseX >= listX && mouseX < listX + listW
                                && mouseY >= rowY  && mouseY < rowY + ROW_H;

                if (selected) {
                    graphics.fill(listX, rowY, listX + listW, rowY + ROW_H, 0xBB2255BB);
                } else if (hovered) {
                    graphics.fill(listX, rowY, listX + listW, rowY + ROW_H, 0x33000000);
                }

                String name = oggFiles.get(fileIdx).getName();
                // Clip text so it fits inside the list box
                while (this.font.width("♪ " + name) > listW - 8 && name.length() > 4) {
                    name = name.substring(0, name.length() - 4) + "...";
                }
                int textCol = selected ? 0xFFFFFF : 0x303030;
                graphics.drawString(this.font, "♪ " + name, listX + 3, rowY + 3, textCol, false);

            } else if (oggFiles.isEmpty() && row == LIST_ROWS / 2) {
                // Empty-state hint centred in the list
                String hint = "Drop .ogg files, then Refresh";
                graphics.drawString(this.font, hint,
                        listX + listW / 2 - this.font.width(hint) / 2,
                        rowY + 3, 0xFF888888, false);
            }

            // Row divider (except after last row)
            if (row < LIST_ROWS - 1) {
                graphics.fill(listX, rowY + ROW_H - 1, listX + listW, rowY + ROW_H, 0xFFD0D0D0);
            }
        }

        // Scrollbar (only when there are more files than visible rows)
        if (oggFiles.size() > LIST_ROWS) {
            int sbX    = listX + listW - 4;
            int thumbH = Math.max(8, listH * LIST_ROWS / oggFiles.size());
            int thumbY = listY + (int) ((float) scrollOffset / maxScroll() * (listH - thumbH));
            graphics.fill(sbX, listY,  sbX + 4, listY + listH, 0xFF505050);
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFFAAAAAA);
        }

        // ── Upload progress bar (replaces list while uploading) ───────────────
        if (uploading && uploadTotal > 0) {
            int barW = GUI_W - 16;
            int barX = x + 8;
            int barY = listY + listH + 3;
            graphics.fill(barX,      barY, barX + barW,   barY + 8, 0xFF555555);
            int fill = (int) ((float) uploadSent / uploadTotal * barW);
            graphics.fill(barX,      barY, barX + fill,   barY + 8, 0xFF20A020);
            String prog = "Uploading " + uploadSent + " / " + uploadTotal + " chunks";
            graphics.drawString(this.font, prog,
                    barX + barW / 2 - this.font.width(prog) / 2, barY, 0xFFFFFF, false);
        }

        // ── Song title label ──────────────────────────────────────────────────
        graphics.drawString(this.font,
                Component.translatable("customdiscs.gui.song_title"),
                x + 8, y + 119, COL_LABEL, false);

        // ── Volume label ──────────────────────────────────────────────────────
        graphics.drawString(this.font,
                Component.literal("Volume"),
                x + 8, y + 151, COL_LABEL, false);

        // ── Status bar (6 s auto-hide) ────────────────────────────────────────
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusTime < 6000) {
            graphics.fill(x + 2, y + GUI_H - 14, x + GUI_W - 2, y + GUI_H - 2, 0xFF555555);
            graphics.drawString(this.font, statusMessage,
                    x + 5, y + GUI_H - 12, COL_STATUS, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 7, COL_TITLE, false);
    }
}
