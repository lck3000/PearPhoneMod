package com.pearphone.mod.client;

import com.pearphone.mod.AudioPlayerBlockEntity;
import com.pearphone.mod.AudioPlayerMenu;
import com.pearphone.mod.audio.PlaylistManager;
import com.pearphone.mod.audio.TrackInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class AudioPlayerScreen extends AbstractContainerScreen<AudioPlayerMenu> {

    // ── Dimensions ────────────────────────────────────────────────────────────
    private static final int W  = 294;   // imageWidth
    private static final int H  = 254;   // imageHeight
    private static final int PX = 9;     // horizontal padding

    // ── Section Y offsets (relative to topPos) ────────────────────────────────
    private static final int Y_TITLE       = 5;
    private static final int Y_SEP1        = 14;
    private static final int Y_NP_LABEL    = 17;
    private static final int Y_TRACK       = 28;
    private static final int Y_PROGRESS    = 40;   // bar
    private static final int Y_SEP2        = 51;
    private static final int Y_CONTROLS    = 54;   // buttons h=18
    private static final int Y_SEP3        = 75;
    private static final int Y_VOL_SLIDER  = 78;   // slider h=16
    private static final int Y_SEP4        = 98;
    private static final int Y_PL_LABEL    = 101;
    private static final int Y_PL_LIST     = 113;  // playlist items start
    private static final int ITEM_H        = 18;
    private static final int ITEMS_VISIBLE = 6;
    private static final int Y_PL_END      = Y_PL_LIST + ITEMS_VISIBLE * ITEM_H; // 221
    private static final int Y_SEP5        = Y_PL_END + 1;                       // 222
    private static final int Y_URL_INPUT   = Y_SEP5 + 3;                          // 225

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG          = 0xFF1C1C1C;
    private static final int C_PANEL       = 0xFF252530;
    private static final int C_NP_BG       = 0xFF1A2A1A;
    private static final int C_SEP         = 0xFF404055;
    private static final int C_TITLE       = 0xFFAABBDD;
    private static final int C_HEADER      = 0xFF7799AA;
    private static final int C_TEXT        = 0xFFDDDDDD;
    private static final int C_DIM         = 0xFF888888;
    private static final int C_WHITE       = 0xFFFFFFFF;
    private static final int C_GREEN       = 0xFF22CC55;
    private static final int C_YELLOW      = 0xFFDDAA00;
    private static final int C_RED         = 0xFFCC3333;
    private static final int C_ITEM_EVEN   = 0xFF1E1E2A;
    private static final int C_ITEM_ODD    = 0xFF222232;
    private static final int C_ITEM_ACTIVE = 0xFF1E3A1E;
    private static final int C_ITEM_HOVER  = 0xFF2A2A3E;
    private static final int C_PROG_BG     = 0xFF333333;
    private static final int C_PROG_FILL   = 0xFF22AA44;
    private static final int C_PROG_PAUSED = 0xFFBB8800;

    // ── Standalone (item) playlist — shared across screen opens ───────────────
    private static PlaylistManager standalonePlaylist;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private VolumeSlider volumeSlider;
    private EditBox      urlInput;
    private Button       btnPrev, btnPlay, btnPause, btnStop, btnNext;
    private Button       btnAdd, btnScrollUp, btnScrollDown;

    /** First visible playlist row index. */
    private int scrollOffset = 0;
    /** Row the mouse is currently hovering over (-1 = none). */
    private int hoveredRow = -1;

    public AudioPlayerScreen(AudioPlayerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = W;
        this.imageHeight = H;
        // Push the vanilla title / inventory labels off-screen
        this.titleLabelX     = -9999;
        this.titleLabelY     = -9999;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width  - W) / 2;
        this.topPos  = (this.height - H) / 2;

        if (isStandalone() && standalonePlaylist == null) {
            standalonePlaylist = new PlaylistManager();
        }

        PlaylistManager pl = playlist();

        // ── Volume slider ─────────────────────────────────────────────────────
        volumeSlider = new VolumeSlider(
                leftPos + PX + 24, topPos + Y_VOL_SLIDER, W - PX * 2 - 24 - 30, 16,
                pl.getVolume(), pl::setVolume);
        addRenderableWidget(volumeSlider);

        // ── Playback controls ─────────────────────────────────────────────────
        // 5 buttons, each 50px wide with 5px gap → total 270px; centred in W-2*PX=276
        int btnY = topPos + Y_CONTROLS;
        int bw = 50, bg = 5;
        int totalBtns = 5 * bw + 4 * bg;                   // 270
        int bx = leftPos + (W - totalBtns) / 2;
        btnPrev  = btn(bx,              btnY, bw, 18, "|<",  () -> pl.prev());
        btnPlay  = btn(bx + bw + bg,    btnY, bw, 18, "\u25B6", () -> pl.playOrResume());
        btnPause = btn(bx + 2*(bw+bg),  btnY, bw, 18, "||",  () -> pl.pause());
        btnStop  = btn(bx + 3*(bw+bg),  btnY, bw, 18, "\u25A0", () -> pl.stop());
        btnNext  = btn(bx + 4*(bw+bg),  btnY, bw, 18, ">|",  () -> pl.next());

        // ── Playlist scroll arrows ────────────────────────────────────────────
        btnScrollUp   = btn(leftPos + W - 22, topPos + Y_PL_LABEL,     14, 9, "\u25B2", () -> scroll(-1));
        btnScrollDown = btn(leftPos + W - 22, topPos + Y_PL_LABEL + 10, 14, 9, "\u25BC", () -> scroll(+1));

        // ── URL input + Add button ────────────────────────────────────────────
        int addBtnW = 44;
        urlInput = new EditBox(font,
                leftPos + PX, topPos + Y_URL_INPUT,
                W - PX * 2 - addBtnW - 4, 16,
                Component.empty());
        urlInput.setHint(Component.literal("Paste YouTube URL…")
                .withStyle(s -> s.withColor(0x666666)));
        urlInput.setMaxLength(512);
        addRenderableWidget(urlInput);

        btnAdd = btn(leftPos + W - PX - addBtnW, topPos + Y_URL_INPUT, addBtnW, 16, "Add", this::addUrl);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int lx = leftPos, ty = topPos;

        // ── Outer background ──────────────────────────────────────────────────
        g.fill(lx,     ty,     lx + W,     ty + H,     0xFF111111);
        g.fill(lx + 1, ty + 1, lx + W - 1, ty + H - 1, C_BG);

        // ── Title bar ─────────────────────────────────────────────────────────
        g.fill(lx + 1, ty + 1, lx + W - 1, ty + Y_SEP1, C_PANEL);

        // ── Now-playing section ───────────────────────────────────────────────
        g.fill(lx + 1, ty + Y_SEP1 + 1, lx + W - 1, ty + Y_SEP2, C_NP_BG);

        // ── Controls section ──────────────────────────────────────────────────
        g.fill(lx + 1, ty + Y_SEP2 + 1, lx + W - 1, ty + Y_SEP3, 0xFF1C1C28);

        // ── Volume section ────────────────────────────────────────────────────
        g.fill(lx + 1, ty + Y_SEP3 + 1, lx + W - 1, ty + Y_SEP4, 0xFF1C1C28);

        // ── Playlist section ──────────────────────────────────────────────────
        g.fill(lx + 1, ty + Y_SEP4 + 1, lx + W - 1, ty + Y_SEP5, C_ITEM_EVEN);

        // ── URL input area ────────────────────────────────────────────────────
        g.fill(lx + 1, ty + Y_SEP5 + 1, lx + W - 1, ty + H - 1, 0xFF181820);

        // ── Separator lines ───────────────────────────────────────────────────
        sep(g, lx, ty + Y_SEP1);
        sep(g, lx, ty + Y_SEP2);
        sep(g, lx, ty + Y_SEP3);
        sep(g, lx, ty + Y_SEP4);
        sep(g, lx, ty + Y_SEP5);

        // ── Progress bar ──────────────────────────────────────────────────────
        drawProgressBar(g, lx, ty);

        // ── Playlist rows ─────────────────────────────────────────────────────
        drawPlaylistRows(g, lx, ty, mx, my);
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawText(GuiGraphics g, int mx, int my) {
        int lx = leftPos, ty = topPos;
        PlaylistManager pl = playlist();

        // Title
        String title = "PORTABLE AUDIO PLAYER";
        g.drawCenteredString(font, title, lx + W / 2, ty + Y_TITLE, C_TITLE);

        // NOW PLAYING label + status dot
        g.drawString(font, "NOW PLAYING", lx + PX, ty + Y_NP_LABEL, C_HEADER, false);
        String dot   = "\u25CF"; // ●
        int dotColor = pl.isPlaying() ? C_GREEN : pl.isPaused() ? C_YELLOW : C_DIM;
        g.drawString(font, dot, lx + W - PX - font.width(dot), ty + Y_NP_LABEL, dotColor, false);

        // Current track name
        TrackInfo cur = pl.getCurrentTrack();
        String name = cur != null ? cur.getTitle() : "No track selected";
        g.drawString(font, truncate(name, W - PX * 2 - 4), lx + PX, ty + Y_TRACK, C_WHITE, false);

        // Time "1:23 / 4:56"
        String pos  = formatTime(pl.getPositionSeconds());
        String dur  = cur != null ? cur.getFormattedDuration() : "--:--";
        String time = pos + " / " + dur;
        g.drawString(font, time, lx + W - PX - font.width(time), ty + Y_PROGRESS, C_DIM, false);

        // VOL label to the left of the slider
        g.drawString(font, "VOL", lx + PX, ty + Y_VOL_SLIDER + 4, C_DIM, false);

        // PLAYLIST label + count
        String plHeader = "PLAYLIST  (" + pl.getTracks().size() + ")";
        g.drawString(font, plHeader, lx + PX, ty + Y_PL_LABEL, C_HEADER, false);

        // Playlist row text
        drawPlaylistText(g, lx, ty, pl);

        // Status line (small, below now-playing area while not playing)
        if (!pl.isPlaying() && !pl.isPaused()) {
            String status = pl.getStatusMessage();
            if (!status.equals("Idle")) {
                boolean isErr = status.startsWith("Error");
                g.drawString(font, truncate(status, W - PX * 2),
                        lx + PX, ty + Y_TRACK + 11, isErr ? C_RED : C_DIM, false);
            }
        }
    }

    private void drawProgressBar(GuiGraphics g, int lx, int ty) {
        PlaylistManager pl = playlist();
        TrackInfo cur = pl.getCurrentTrack();

        int barX  = lx + PX;
        int barY  = ty + Y_PROGRESS;
        int barW  = W - PX * 2 - 52; // leave room for time text
        int barH  = 5;

        // Background
        g.fill(barX, barY, barX + barW, barY + barH, C_PROG_BG);
        // Border
        g.fill(barX,             barY,             barX + barW, barY + 1,    0xFF555555);
        g.fill(barX,             barY + barH - 1,  barX + barW, barY + barH, 0xFF555555);

        // Fill
        int duration = cur != null ? cur.getDurationSeconds() : -1;
        double pos   = pl.getPositionSeconds();
        if (duration > 0 && pos > 0) {
            double ratio  = Math.min(1.0, pos / duration);
            int    fillW  = (int)(ratio * (barW - 2));
            int    fillC  = pl.isPaused() ? C_PROG_PAUSED : C_PROG_FILL;
            if (fillW > 0) g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, fillC);

            // Playhead dot
            int headX = barX + 1 + fillW;
            g.fill(headX - 1, barY, headX + 2, barY + barH, 0xFFCCCCDD);
        }
    }

    private void drawPlaylistRows(GuiGraphics g, int lx, int ty, int mx, int my) {
        PlaylistManager pl = playlist();
        List<TrackInfo> tracks = pl.getTracks();
        int cur = pl.getCurrentIndex();

        for (int i = 0; i < ITEMS_VISIBLE; i++) {
            int idx = scrollOffset + i;
            int iy  = ty + Y_PL_LIST + i * ITEM_H;

            // Row background
            int bg;
            if      (idx == cur)      bg = C_ITEM_ACTIVE;
            else if (idx == hoveredRow) bg = C_ITEM_HOVER;
            else                       bg = (i % 2 == 0) ? C_ITEM_EVEN : C_ITEM_ODD;

            g.fill(lx + 1, iy, lx + W - 1, iy + ITEM_H, bg);

            // Row separator
            if (i > 0) g.fill(lx + PX, iy, lx + W - PX, iy + 1, 0xFF2A2A3A);

            if (idx < tracks.size()) {
                // Playing indicator column
                if (idx == cur && pl.isPlaying()) {
                    g.fill(lx + 2, iy + 4, lx + 5, iy + ITEM_H - 4, C_GREEN);
                } else if (idx == cur && pl.isPaused()) {
                    g.fill(lx + 2, iy + 4, lx + 4, iy + ITEM_H - 4, C_YELLOW);
                    g.fill(lx + 5, iy + 4, lx + 7, iy + ITEM_H - 4, C_YELLOW);
                }
            }
        }
    }

    private void drawPlaylistText(GuiGraphics g, int lx, int ty, PlaylistManager pl) {
        List<TrackInfo> tracks = pl.getTracks();
        int cur = pl.getCurrentIndex();

        int durCol  = W - PX - 38;  // duration column x (relative to leftPos)
        int delCol  = W - PX - 6;   // delete × x (relative)
        int nameW   = durCol - PX - 10;

        for (int i = 0; i < ITEMS_VISIBLE; i++) {
            int idx = scrollOffset + i;
            if (idx >= tracks.size()) break;

            TrackInfo t  = tracks.get(idx);
            int iy       = ty + Y_PL_LIST + i * ITEM_H;
            int textY    = iy + (ITEM_H - 8) / 2;

            // Track name
            int nameColor = idx == cur ? C_WHITE : C_TEXT;
            if (t.getStatus() == TrackInfo.Status.LOADING) nameColor = C_DIM;
            if (t.getStatus() == TrackInfo.Status.ERROR)   nameColor = C_RED;
            g.drawString(font, truncate(t.getTitle(), nameW), lx + PX + 8, textY, nameColor, false);

            // Duration
            String dur = t.getStatus() == TrackInfo.Status.LOADING ? "…" : t.getFormattedDuration();
            int durColor = idx == cur ? C_GREEN : C_DIM;
            g.drawString(font, dur, lx + durCol, textY, durColor, false);

            // Delete × (only on hovered row)
            if (idx == hoveredRow) {
                g.drawString(font, "\u00D7", lx + delCol, textY, 0xFFAA4444, false);
            }
        }
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Intercept clicks in the playlist area before passing to widgets
        if (isInPlaylistArea(mx, my)) {
            int idx = rowAt((int) my);
            if (idx >= 0) {
                PlaylistManager pl = playlist();
                List<TrackInfo> tracks = pl.getTracks();
                if (idx < tracks.size()) {
                    // Click on the × delete column?
                    int delX = leftPos + W - PX - 6;
                    if (mx >= delX - 4 && mx <= delX + font.width("\u00D7") + 2) {
                        pl.removeTrack(idx);
                        clampScroll();
                    } else {
                        // Play clicked track
                        pl.play(idx);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (isInPlaylistArea(mx, my)) {
            scroll((int) -Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Enter in URL field → add track
        if (urlInput.isFocused() && (key == 257 || key == 335)) {
            addUrl();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── World-close cleanup ───────────────────────────────────────────────────

    public static void shutdownStandalonePlaylist() {
        if (standalonePlaylist != null) {
            standalonePlaylist.shutdown();
            standalonePlaylist = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isStandalone() {
        return menu.getAudioPlayer() == null;
    }

    private PlaylistManager playlist() {
        if (isStandalone()) {
            if (standalonePlaylist == null) standalonePlaylist = new PlaylistManager();
            return standalonePlaylist;
        }
        AudioPlayerBlockEntity be = menu.getAudioPlayer();
        return be.getPlaylist();
    }

    private void addUrl() {
        String url = urlInput.getValue().trim();
        if (!url.isEmpty()) {
            playlist().addTrack(url);
            urlInput.setValue("");
        }
    }

    private void scroll(int delta) {
        int max = Math.max(0, playlist().getTracks().size() - ITEMS_VISIBLE);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
    }

    private void clampScroll() {
        int max = Math.max(0, playlist().getTracks().size() - ITEMS_VISIBLE);
        if (scrollOffset > max) scrollOffset = max;
    }

    private void updateHoveredRow(int my) {
        hoveredRow = isInPlaylistArea(mouse_x_cache, my) ? rowAt(my) : -1;
    }

    // We cache mouseX from render() since updateHoveredRow only receives mouseY
    private double mouse_x_cache;

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        mouse_x_cache = mx;
        super.render(g, mx, my, pt);
        updateHoveredRow(my);
        drawText(g, mx, my);
        updateScrollVisibility();
    }

    private boolean isInPlaylistArea(double mx, double my) {
        return mx >= leftPos + 1 && mx < leftPos + W - 1
            && my >= topPos + Y_PL_LIST && my < topPos + Y_PL_END;
    }

    private int rowAt(int my) {
        int localY = my - topPos - Y_PL_LIST;
        if (localY < 0) return -1;
        int row = localY / ITEM_H;
        return (row < ITEMS_VISIBLE) ? scrollOffset + row : -1;
    }

    private void updateScrollVisibility() {
        boolean needScroll = playlist().getTracks().size() > ITEMS_VISIBLE;
        btnScrollUp.visible   = needScroll;
        btnScrollDown.visible = needScroll;
    }

    private void sep(GuiGraphics g, int lx, int absY) {
        g.fill(lx + 3, absY, lx + W - 3, absY + 1, C_SEP);
    }

    private String truncate(String text, int maxPixels) {
        if (font.width(text) <= maxPixels) return text;
        while (!text.isEmpty() && font.width(text + "…") > maxPixels) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    private static String formatTime(double seconds) {
        if (seconds <= 0) return "0:00";
        int s = (int) seconds;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private Button btn(int x, int y, int w, int h, String label, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(label), b -> action.run())
                      .bounds(x, y, w, h)
                      .build());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Volume slider ─────────────────────────────────────────────────────────

    private static class VolumeSlider extends AbstractSliderButton {
        private final java.util.function.IntConsumer onChange;

        VolumeSlider(int x, int y, int w, int h, int initialVolume, java.util.function.IntConsumer onChange) {
            super(x, y, w, h, Component.empty(), initialVolume / 100.0);
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal((int)(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            onChange.accept((int)(value * 100));
        }

        public void syncValue(int volumePercent) {
            this.value = volumePercent / 100.0;
            updateMessage();
        }
    }
}
