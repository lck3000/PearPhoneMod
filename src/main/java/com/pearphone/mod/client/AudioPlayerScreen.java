package com.pearphone.mod.client;

import com.pearphone.mod.AudioPlayerBlockEntity;
import com.pearphone.mod.AudioPlayerMenu;
import com.pearphone.mod.audio.PlaylistManager;
import com.pearphone.mod.audio.PlaylistPersistence;
import com.pearphone.mod.audio.TrackInfo;
import com.pearphone.mod.network.SpeakerBroadcastPacket;
import net.neoforged.neoforge.network.PacketDistributor;
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
    private Button       btnShuffle, btnRepeat;
    private Button       btnAdd, btnScrollUp, btnScrollDown;
    private Button       btnListen;

    /** First visible playlist row index. */
    private int scrollOffset = 0;
    /** Row the mouse is currently hovering over (-1 = none). */
    private int hoveredRow = -1;
    /** True while the user is dragging the progress bar. */
    private boolean progressDragging = false;
    /** Visual seek position (0–1) while dragging; -1 when not dragging. */
    private double seekPreviewRatio = -1;
    /** Last URL sent to the server for speaker-mode broadcasting (null = not sent). */
    private String lastSentSpeakerUrl = null;

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
            PlaylistPersistence.load(standalonePlaylist);
        }

        PlaylistManager pl = playlist();

        // ── Listen mode button + Volume slider ───────────────────────────────
        // The listen button replaces the old static "VOL" text label.
        btnListen = btn(leftPos + PX, topPos + Y_VOL_SLIDER, 22, 16,
                Component.literal("\u266A").withStyle(s -> s.withColor(0x55FF55)), pl::cycleListenMode);
        volumeSlider = new VolumeSlider(
                leftPos + PX + 26, topPos + Y_VOL_SLIDER, W - PX * 2 - 26 - 30, 16,
                pl.getVolume(), pl::setVolume);
        addRenderableWidget(volumeSlider);

        // ── Playback controls ─────────────────────────────────────────────────
        // Layout: [shuffle 22] [4] [|< 42] [3] [▶ 42] [3] [|| 42] [3] [■ 42] [3] [>| 42] [4] [repeat 22]
        // Total = 22+4+222+4+22 = 274px, centred in W-2*PX=276
        int btnY  = topPos + Y_CONTROLS;
        int bw = 42, bg = 3, iconW = 22;
        int totalRow = iconW + 4 + (5 * bw + 4 * bg) + 4 + iconW;  // 274
        int bx = leftPos + PX + (W - 2 * PX - totalRow) / 2;
        int mainBx = bx + iconW + 4;

        btnShuffle = btn(bx, btnY, iconW, 18, Component.literal("⇌").withStyle(s -> s.withColor(0x999999)), pl::cycleShuffleMode);
        btnPrev    = btn(mainBx,                btnY, bw, 18, "|<",        pl::prev);
        btnPlay    = btn(mainBx +   (bw + bg),  btnY, bw, 18, "\u25B6",   pl::playOrResume);
        btnPause   = btn(mainBx + 2*(bw + bg),  btnY, bw, 18, "||",       pl::pause);
        btnStop    = btn(mainBx + 3*(bw + bg),  btnY, bw, 18, "\u25A0",   pl::stop);
        btnNext    = btn(mainBx + 4*(bw + bg),  btnY, bw, 18, ">|",       pl::next);
        btnRepeat  = btn(mainBx + 5*(bw + bg) - bg + 4, btnY, iconW, 18,
                Component.literal("\u21BA").withStyle(s -> s.withColor(0x999999)), pl::cycleRepeatMode);

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

        // Time "1:23 / 4:56" — show seek target while dragging
        double displayPos = seekPreviewRatio >= 0 && cur != null && cur.getDurationSeconds() > 0
                ? seekPreviewRatio * cur.getDurationSeconds()
                : pl.getPositionSeconds();
        String pos  = formatTime(displayPos);
        String dur  = cur != null ? cur.getFormattedDuration() : "--:--";
        String time = pos + " / " + dur;
        int timeColor = seekPreviewRatio >= 0 ? 0xFF55BBFF : C_DIM;
        g.drawString(font, time, lx + W - PX - font.width(time), ty + Y_PROGRESS, timeColor, false);

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

        // Hover highlight when cursor is near and track has duration
        int duration = cur != null ? cur.getDurationSeconds() : -1;
        double pos   = pl.getPositionSeconds();

        double ratio;
        if (seekPreviewRatio >= 0) {
            ratio = seekPreviewRatio;
        } else if (duration > 0 && pos > 0) {
            ratio = Math.min(1.0, pos / duration);
        } else {
            return; // nothing to draw
        }

        int fillW = (int)(ratio * (barW - 2));
        int fillC = seekPreviewRatio >= 0 ? 0xFF55BBFF
                  : pl.isPaused()        ? C_PROG_PAUSED
                  :                        C_PROG_FILL;
        if (fillW > 0) g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, fillC);

        // Playhead — slightly larger while dragging so it's easy to grab
        int headX = barX + 1 + fillW;
        if (seekPreviewRatio >= 0) {
            g.fill(headX - 2, barY - 1, headX + 3, barY + barH + 1, 0xFFFFFFFF);
        } else {
            g.fill(headX - 1, barY, headX + 2, barY + barH, 0xFFCCCCDD);
        }
    }

    private void drawPlaylistRows(GuiGraphics g, int lx, int ty, int mx, int my) {
        PlaylistManager pl = playlist();
        List<TrackInfo> tracks = pl.getDisplayTracks();
        int cur = pl.getCurrentDisplayIndex();

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
        List<TrackInfo> tracks = pl.getDisplayTracks();
        int cur = pl.getCurrentDisplayIndex();

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
        // Progress bar seek — only when a track with known duration is loaded
        if (button == 0 && isOnProgressBar(mx, my)) {
            PlaylistManager pl = playlist();
            TrackInfo cur = pl.getCurrentTrack();
            if (cur != null && cur.getDurationSeconds() > 0 && (pl.isPlaying() || pl.isPaused())) {
                progressDragging = true;
                seekPreviewRatio = progressRatioAt(mx);
                return true;
            }
        }
        // Intercept clicks in the playlist area before passing to widgets
        if (isInPlaylistArea(mx, my)) {
            int displayIdx = rowAt((int) my);
            if (displayIdx >= 0) {
                PlaylistManager pl = playlist();
                List<TrackInfo> tracks = pl.getDisplayTracks();
                if (displayIdx < tracks.size()) {
                    int trackIdx = pl.displayIndexToTrackIndex(displayIdx);
                    // Click on the × delete column?
                    int delX = leftPos + W - PX - 6;
                    if (mx >= delX - 4 && mx <= delX + font.width("\u00D7") + 2) {
                        pl.removeTrack(trackIdx);
                        clampScroll();
                    } else {
                        pl.play(trackIdx);
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
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        // Progress bar: update preview position while dragging (seek fires on release)
        if (button == 0 && progressDragging) {
            seekPreviewRatio = progressRatioAt(mx);
            return true;
        }
        // Volume slider: AbstractContainerScreen.mouseDragged may consume the event before
        // it reaches the focused widget, so we route it explicitly when the slider is focused.
        if (button == 0 && getFocused() == volumeSlider && isDragging()) {
            return volumeSlider.mouseDragged(mx, my, button, dragX, dragY);
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && progressDragging) {
            progressDragging = false;
            if (seekPreviewRatio >= 0) {
                PlaylistManager pl = playlist();
                TrackInfo cur = pl.getCurrentTrack();
                if (cur != null && cur.getDurationSeconds() > 0) {
                    pl.seekTo(seekPreviewRatio * cur.getDurationSeconds());
                }
                seekPreviewRatio = -1;
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
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
            PlaylistPersistence.save(standalonePlaylist);
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
        updateToggleButtonLabels();
        syncSpeakerMode();
        super.render(g, mx, my, pt);
        updateHoveredRow(my);
        drawText(g, mx, my);
        updateScrollVisibility();
    }

    /**
     * Polls speaker mode each frame; sends a {@link SpeakerBroadcastPacket} to the
     * server only when the active URL or mode changes.  Standalone/item mode only
     * (block entities handle their own broadcasting via the block's tile tick).
     */
    private void syncSpeakerMode() {
        if (!isStandalone()) return;
        PlaylistManager pl = playlist();
        boolean speakerActive = pl.getListenMode() == PlaylistManager.ListenMode.SPEAKER
                && pl.isPlaying();

        TrackInfo cur = pl.getCurrentTrack();
        String url = (speakerActive && cur != null) ? cur.getUrl() : null;

        if (java.util.Objects.equals(url, lastSentSpeakerUrl)) return; // no change

        lastSentSpeakerUrl = url;
        if (url != null) {
            PacketDistributor.sendToServer(new SpeakerBroadcastPacket(url, pl.getVolume(), true));
        } else {
            PacketDistributor.sendToServer(new SpeakerBroadcastPacket("", 0, false));
        }
    }

    private boolean isOnProgressBar(double mx, double my) {
        int barX = leftPos + PX;
        int barY = topPos + Y_PROGRESS;
        int barW = W - PX * 2 - 52;
        // Use a slightly taller hit region than the 5px visual bar for easier grabbing
        return mx >= barX && mx <= barX + barW && my >= barY - 3 && my <= barY + 8;
    }

    private double progressRatioAt(double mx) {
        int barX = leftPos + PX;
        int barW = W - PX * 2 - 52;
        return Math.max(0.0, Math.min(1.0, (mx - barX - 1.0) / Math.max(1, barW - 2)));
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
        return btn(x, y, w, h, Component.literal(label), action);
    }

    private Button btn(int x, int y, int w, int h, Component label, Runnable action) {
        return addRenderableWidget(
                Button.builder(label, b -> action.run())
                      .bounds(x, y, w, h)
                      .build());
    }

    /** Refresh shuffle/repeat/listen button labels and slider lock state. */
    private void updateToggleButtonLabels() {
        PlaylistManager pl = playlist();

        if (btnShuffle != null) {
            boolean on = pl.getShuffleMode() == PlaylistManager.ShuffleMode.ON;
            btnShuffle.setMessage(Component.literal("\u21CC")   // ⇌
                    .withStyle(s -> s.withColor(on ? 0x55FF55 : 0x999999)));
        }
        if (btnRepeat != null) {
            Component msg = switch (pl.getRepeatMode()) {
                case NONE -> Component.literal("\u21BA")         // ↺
                        .withStyle(s -> s.withColor(0x999999));
                case ALL  -> Component.literal("\u21BB")         // ↻
                        .withStyle(s -> s.withColor(0x55FF55));
                case ONE  -> Component.literal("\u21BB1")        // ↻1
                        .withStyle(s -> s.withColor(0xFFFF55));
            };
            btnRepeat.setMessage(msg);
        }
        if (btnListen != null) {
            Component msg = switch (pl.getListenMode()) {
                // ♪ headphones — only you hear it
                case HEADPHONES -> Component.literal("\u266A").withStyle(s -> s.withColor(0x55FF55));
                // ♫ speaker — nearby players hear it
                case SPEAKER    -> Component.literal("\u266B").withStyle(s -> s.withColor(0x55BBFF));
                // × muted
                case MUTE       -> Component.literal("\u00D7").withStyle(s -> s.withColor(0xFF5555));
            };
            btnListen.setMessage(msg);
        }
        if (volumeSlider != null) {
            volumeSlider.active = true;
        }
    }

    @Override
    public void onClose() {
        // If the screen is closed while broadcasting in speaker mode, stop the broadcast
        if (isStandalone() && lastSentSpeakerUrl != null) {
            PacketDistributor.sendToServer(new SpeakerBroadcastPacket("", 0, false));
            lastSentSpeakerUrl = null;
        }
        super.onClose();
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
