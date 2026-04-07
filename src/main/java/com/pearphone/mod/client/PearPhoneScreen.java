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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PearPhoneScreen extends AbstractContainerScreen<AudioPlayerMenu> {

    // ── Outer frame (same as original screen — keeps menu system happy) ────────
    private static final int W = 294;
    private static final int H = 254;

    // ── Phone screen area (inside the bezel) ──────────────────────────────────
    private static final int SCR_X = 4;   // offset from leftPos
    private static final int SCR_Y = 2;   // offset from topPos
    private static final int SCR_W = 286;
    private static final int SCR_H = 250;

    // Status bar (top of phone screen)
    private static final int STATUS_H = 12;
    // Home indicator bar (bottom of phone screen)
    private static final int HOME_BAR_H = 10;
    // Content area starts below status bar
    private static final int CONTENT_Y = SCR_Y + STATUS_H; // = 14
    // Content area ends above home bar
    private static final int CONTENT_BOTTOM = SCR_Y + SCR_H - HOME_BAR_H; // = 242

    // ── App icon grid (HOME mode) ─────────────────────────────────────────────
    private static final int ICON_SIZE    = 44;
    private static final int ICON_LABEL_H = 10;
    private static final int ICON_COL_GAP = 14;
    private static final int ICON_ROW_GAP = 16;
    private static final int ICON_COLS    = 3;
    private static final int ICON_ROWS    = 2;

    // Computed absolute positions per icon [row][col] — filled in initHome()
    private final int[][] iconX = new int[ICON_ROWS][ICON_COLS];
    private final int[][] iconY = new int[ICON_ROWS][ICON_COLS];

    // ── Music player layout (PEAR_MUSIC mode, relative to topPos) ────────────
    // Compressed to fit inside the phone content area (topPos+14 → topPos+242)
    private static final int MP_PX         = 9;    // horizontal padding from phone left edge
    private static final int MP_Y_BACK     = 14;   // back button (just below status bar)
    private static final int MP_Y_TITLE    = 17;
    private static final int MP_Y_SEP1     = 26;
    private static final int MP_Y_NP_LABEL = 29;
    private static final int MP_Y_TRACK    = 39;
    private static final int MP_Y_PROGRESS = 50;
    private static final int MP_Y_SEP2     = 61;
    private static final int MP_Y_CONTROLS = 63;   // buttons h=16
    private static final int MP_Y_SEP3     = 82;
    private static final int MP_Y_VOL      = 84;   // slider h=14
    private static final int MP_Y_SEP4     = 100;
    private static final int MP_Y_PL_LABEL = 102;
    private static final int MP_Y_PL_LIST  = 113;
    private static final int MP_ITEM_H     = 17;
    private static final int MP_ITEMS_VIS  = 5;
    private static final int MP_Y_PL_END   = MP_Y_PL_LIST + MP_ITEMS_VIS * MP_ITEM_H; // 198
    private static final int MP_Y_SEP5     = MP_Y_PL_END + 1;                          // 199
    private static final int MP_Y_URL      = MP_Y_SEP5 + 2;                            // 201

    // Phone screen left edge (used as origin for music player coordinates)
    private int mpLeft()  { return leftPos + SCR_X; }
    private int mpWidth() { return SCR_W; }

    // ── Colors — phone chrome ─────────────────────────────────────────────────
    private static final int C_BEZEL       = 0xFF0A0A0A;
    private static final int C_SCREEN_BG   = 0xFF111118;
    private static final int C_STATUS_BG   = 0xFF080810;
    private static final int C_HOME_BAR_BG = 0xFF080810;
    private static final int C_PILL        = 0xFF666677;

    // ── Colors — home screen ─────────────────────────────────────────────────
    private static final int C_HOME_BG         = 0xFF0D0D1A;
    private static final int C_BRAND           = 0xFF99BBFF;
    private static final int C_APP_MUSIC_BG    = 0xFF0D2B0D;
    private static final int C_APP_EMPTY_BG    = 0xFF1A1A2A;
    private static final int C_APP_BORDER      = 0xFF333355;
    private static final int C_APP_LABEL       = 0xFFAAAAAA;
    private static final int C_APP_LABEL_ACTIVE = 0xFFFFFFFF;

    // ── Colors — music player ─────────────────────────────────────────────────
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

    // ── App state ─────────────────────────────────────────────────────────────
    private enum PhoneApp { HOME, PEAR_MUSIC }
    private PhoneApp currentApp = PhoneApp.HOME;

    // ── Standalone playlist (shared across screen opens, same as AudioPlayerScreen) ──
    private static PlaylistManager standalonePlaylist;

    /** Called by {@link ClientEventHandlers} on world unload. */
    public static void shutdownStandalonePlaylist() {
        if (standalonePlaylist != null) {
            PlaylistPersistence.save(standalonePlaylist);
            standalonePlaylist.shutdown();
            standalonePlaylist = null;
        }
    }

    // ── Music player widgets ──────────────────────────────────────────────────
    private VolumeSlider volumeSlider;
    private EditBox      urlInput;
    private Button       btnPrev, btnPlay, btnPause, btnStop, btnNext;
    private Button       btnShuffle, btnRepeat, btnListen;
    private Button       btnAdd, btnScrollUp, btnScrollDown;
    private Button       btnBack;

    // ── Music player interaction state ────────────────────────────────────────
    private int     scrollOffset     = 0;
    private int     hoveredRow       = -1;
    private boolean progressDragging = false;
    private double  seekPreviewRatio = -1;
    private String  lastSentSpeakerUrl = null;
    private double  mouse_x_cache;

    // ─────────────────────────────────────────────────────────────────────────

    public PearPhoneScreen(AudioPlayerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = W;
        this.imageHeight = H;
        // Push vanilla title/inventory labels off-screen so they don't render
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
        if (currentApp == PhoneApp.HOME) {
            initHome();
        } else {
            initMusicPlayer();
        }
    }

    /** Computes icon grid positions. No widgets needed — icons are click-detected manually. */
    private void initHome() {
        int totalGridW = ICON_COLS * ICON_SIZE + (ICON_COLS - 1) * ICON_COL_GAP;
        int totalGridH = ICON_ROWS * (ICON_SIZE + ICON_LABEL_H) + (ICON_ROWS - 1) * ICON_ROW_GAP;
        int contentW   = SCR_W;
        int contentH   = CONTENT_BOTTOM - CONTENT_Y; // 228px
        // Leave 28px at top of content for branding text
        int brandH     = 28;
        int gridStartX = leftPos + SCR_X + (contentW - totalGridW) / 2;
        int gridStartY = topPos + CONTENT_Y + brandH + (contentH - brandH - totalGridH) / 2;

        for (int r = 0; r < ICON_ROWS; r++) {
            for (int c = 0; c < ICON_COLS; c++) {
                iconX[r][c] = gridStartX + c * (ICON_SIZE + ICON_COL_GAP);
                iconY[r][c] = gridStartY + r * (ICON_SIZE + ICON_LABEL_H + ICON_ROW_GAP);
            }
        }
    }

    /** Creates all music player widgets. Layout mirrors AudioPlayerScreen but compressed to fit phone. */
    private void initMusicPlayer() {
        if (isStandalone() && standalonePlaylist == null) {
            standalonePlaylist = new PlaylistManager();
            PlaylistPersistence.load(standalonePlaylist);
        }

        PlaylistManager pl = playlist();
        int lx = mpLeft();
        int mw = mpWidth();

        // Back button
        btnBack = btn(lx + MP_PX, topPos + MP_Y_BACK, 30, 10, "\u25C4 Back", () -> switchTo(PhoneApp.HOME));

        // Listen mode toggle + volume slider
        btnListen = btn(lx + MP_PX, topPos + MP_Y_VOL, 20, 14,
                Component.literal("\u266A").withStyle(s -> s.withColor(0x55FF55)), pl::cycleListenMode);
        volumeSlider = new VolumeSlider(
                lx + MP_PX + 24, topPos + MP_Y_VOL,
                mw - MP_PX * 2 - 24 - 26, 14,
                pl.getVolume(), pl::setVolume);
        addRenderableWidget(volumeSlider);

        // Playback controls row
        // Layout: [shuffle 20][4][|< 40][3][▶ 40][3][|| 40][3][■ 40][3][>| 40][4][repeat 20]
        // totalRow = 20+4+212+4+20 = 260 — fits in mw-2*MP_PX = 268
        int btnY   = topPos + MP_Y_CONTROLS;
        int bw     = 40, bg = 3, iconW = 20;
        int totalRow = iconW + 4 + (5 * bw + 4 * bg) + 4 + iconW;
        int bx     = lx + MP_PX + (mw - 2 * MP_PX - totalRow) / 2;
        int mainBx = bx + iconW + 4;

        btnShuffle = btn(bx,                              btnY, iconW, 16, Component.literal("\u21CC").withStyle(s -> s.withColor(0x999999)), pl::cycleShuffleMode);
        btnPrev    = btn(mainBx,                          btnY, bw,    16, "|<",      pl::prev);
        btnPlay    = btn(mainBx +   (bw + bg),            btnY, bw,    16, "\u25B6", pl::playOrResume);
        btnPause   = btn(mainBx + 2*(bw + bg),            btnY, bw,    16, "||",     pl::pause);
        btnStop    = btn(mainBx + 3*(bw + bg),            btnY, bw,    16, "\u25A0", pl::stop);
        btnNext    = btn(mainBx + 4*(bw + bg),            btnY, bw,    16, ">|",     pl::next);
        btnRepeat  = btn(mainBx + 5*(bw + bg) - bg + 4,  btnY, iconW, 16, Component.literal("\u21BA").withStyle(s -> s.withColor(0x999999)), pl::cycleRepeatMode);

        // Playlist scroll arrows
        btnScrollUp   = btn(lx + mw - 18, topPos + MP_Y_PL_LABEL,      12, 9, "\u25B2", () -> scroll(-1));
        btnScrollDown = btn(lx + mw - 18, topPos + MP_Y_PL_LABEL + 10, 12, 9, "\u25BC", () -> scroll(+1));

        // URL input + Add button
        int addBtnW = 36;
        urlInput = new EditBox(font,
                lx + MP_PX, topPos + MP_Y_URL,
                mw - MP_PX * 2 - addBtnW - 3, 13,
                Component.empty());
        urlInput.setHint(Component.literal("Paste YouTube URL\u2026").withStyle(s -> s.withColor(0x666666)));
        urlInput.setMaxLength(512);
        addRenderableWidget(urlInput);
        btnAdd = btn(lx + mw - MP_PX - addBtnW, topPos + MP_Y_URL, addBtnW, 13, "Add", this::addUrl);
    }

    // ── App switching ─────────────────────────────────────────────────────────

    private void switchTo(PhoneApp app) {
        this.currentApp = app;
        rebuildWidgets(); // clears widgets then calls init() again
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        mouse_x_cache = mx;
        renderBackground(g, mx, my, pt);
        if (currentApp == PhoneApp.PEAR_MUSIC) {
            updateToggleButtonLabels();
            syncSpeakerMode();
        }
        super.render(g, mx, my, pt); // calls renderBg() then draws widgets
        if (currentApp == PhoneApp.PEAR_MUSIC) {
            updateHoveredRow(my);
            drawMusicPlayerText(g);
            updateScrollVisibility();
        }
        // Status bar and home bar always draw on top
        renderStatusBar(g);
        renderHomeBar(g);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        // Phone bezel
        g.fill(leftPos, topPos, leftPos + W, topPos + H, C_BEZEL);
        // Phone screen background
        g.fill(leftPos + SCR_X, topPos + SCR_Y,
               leftPos + SCR_X + SCR_W, topPos + SCR_Y + SCR_H, C_SCREEN_BG);

        if (currentApp == PhoneApp.HOME) {
            renderHomeContent(g, mx, my);
        } else {
            renderMusicPlayerBg(g);
        }
    }

    private void renderStatusBar(GuiGraphics g) {
        int lx = leftPos + SCR_X;
        int ty = topPos + SCR_Y;
        g.fill(lx, ty, lx + SCR_W, ty + STATUS_H, C_STATUS_BG);

        // Current time (left side)
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"));
        g.drawString(font, time, lx + 5, ty + 2, C_WHITE, false);

        // Signal + battery indicator (right side)
        String indicator = "\u25CF\u25CF\u25CF \u2588";
        g.drawString(font, indicator, lx + SCR_W - font.width(indicator) - 4, ty + 2, 0xFF88FF88, false);
    }

    private void renderHomeBar(GuiGraphics g) {
        int lx       = leftPos + SCR_X;
        int homeBarY = topPos + SCR_Y + SCR_H - HOME_BAR_H;
        g.fill(lx, homeBarY, lx + SCR_W, homeBarY + HOME_BAR_H, C_HOME_BAR_BG);
        // Pill indicator
        int cx = lx + SCR_W / 2;
        g.fill(cx - 22, homeBarY + 4, cx + 22, homeBarY + 6, C_PILL);
    }

    private void renderHomeContent(GuiGraphics g, int mx, int my) {
        int lx          = leftPos + SCR_X;
        int contentTop  = topPos + CONTENT_Y;
        int contentBot  = topPos + CONTENT_BOTTOM;

        // Background
        g.fill(lx, contentTop, lx + SCR_W, contentBot, C_HOME_BG);

        // PearPhone branding
        g.drawCenteredString(font, "PearPhone", lx + SCR_W / 2, contentTop + 8, C_BRAND);

        // App icons
        drawAppIcon(g, mx, my, 0, 0, C_APP_MUSIC_BG, 0xFF22CC55, "\u266B", "Pear Music", true);
        drawAppIcon(g, mx, my, 0, 1, C_APP_EMPTY_BG, 0xFF555566, "\u2699", "Settings",   false);
        drawAppIcon(g, mx, my, 0, 2, C_APP_EMPTY_BG, 0xFF555566, "?",      "Soon",        false);
        drawAppIcon(g, mx, my, 1, 0, C_APP_EMPTY_BG, 0xFF555566, "?",      "Soon",        false);
        drawAppIcon(g, mx, my, 1, 1, C_APP_EMPTY_BG, 0xFF555566, "?",      "Soon",        false);
        drawAppIcon(g, mx, my, 1, 2, C_APP_EMPTY_BG, 0xFF555566, "?",      "Soon",        false);
    }

    private void drawAppIcon(GuiGraphics g, int mx, int my, int row, int col,
                             int bgColor, int glyphColor, String glyph, String label, boolean active) {
        int ix = iconX[row][col];
        int iy = iconY[row][col];
        boolean hovered = active && mx >= ix && mx < ix + ICON_SIZE && my >= iy && my < iy + ICON_SIZE;

        // Icon background (brightened on hover)
        int bg = hovered ? brighten(bgColor) : bgColor;
        g.fill(ix, iy, ix + ICON_SIZE, iy + ICON_SIZE, bg);

        // Border (1px)
        g.fill(ix,                 iy,                ix + ICON_SIZE, iy + 1,              C_APP_BORDER);
        g.fill(ix,                 iy + ICON_SIZE - 1, ix + ICON_SIZE, iy + ICON_SIZE,     C_APP_BORDER);
        g.fill(ix,                 iy,                ix + 1,          iy + ICON_SIZE,     C_APP_BORDER);
        g.fill(ix + ICON_SIZE - 1, iy,                ix + ICON_SIZE,  iy + ICON_SIZE,     C_APP_BORDER);

        // Glyph centered inside icon
        int gx = ix + (ICON_SIZE - font.width(glyph)) / 2;
        int gy = iy + (ICON_SIZE - 8) / 2;
        g.drawString(font, glyph, gx, gy, glyphColor, false);

        // Label below icon
        int labelColor = active ? C_APP_LABEL_ACTIVE : C_APP_LABEL;
        g.drawCenteredString(font, label, ix + ICON_SIZE / 2, iy + ICON_SIZE + 2, labelColor);
    }

    private static int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 30);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + 30);
        int b = Math.min(255, (color & 0xFF) + 30);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private void renderMusicPlayerBg(GuiGraphics g) {
        int lx = mpLeft();
        int ty = topPos;
        int mw = mpWidth();

        int contentTop = ty + CONTENT_Y;
        int contentBot = ty + CONTENT_BOTTOM;

        // Full content background
        g.fill(lx, contentTop, lx + mw, contentBot, 0xFF111118);

        // Title bar (back button area + title)
        g.fill(lx, ty + MP_Y_BACK, lx + mw, ty + MP_Y_SEP1, C_PANEL);

        // Now-playing section
        g.fill(lx, ty + MP_Y_SEP1 + 1, lx + mw, ty + MP_Y_SEP2, C_NP_BG);

        // Controls section
        g.fill(lx, ty + MP_Y_SEP2 + 1, lx + mw, ty + MP_Y_SEP3, 0xFF1C1C28);

        // Volume section
        g.fill(lx, ty + MP_Y_SEP3 + 1, lx + mw, ty + MP_Y_SEP4, 0xFF1C1C28);

        // Playlist section
        g.fill(lx, ty + MP_Y_SEP4 + 1, lx + mw, ty + MP_Y_SEP5, C_ITEM_EVEN);

        // URL input area
        g.fill(lx, ty + MP_Y_SEP5 + 1, lx + mw, contentBot, 0xFF181820);

        // Separators
        mpSep(g, lx, ty + MP_Y_SEP1, mw);
        mpSep(g, lx, ty + MP_Y_SEP2, mw);
        mpSep(g, lx, ty + MP_Y_SEP3, mw);
        mpSep(g, lx, ty + MP_Y_SEP4, mw);
        mpSep(g, lx, ty + MP_Y_SEP5, mw);

        drawProgressBar(g, lx, ty, mw);
        drawPlaylistRows(g, lx, ty, mw);
    }

    private void mpSep(GuiGraphics g, int lx, int absY, int mw) {
        g.fill(lx + 3, absY, lx + mw - 3, absY + 1, C_SEP);
    }

    private void drawProgressBar(GuiGraphics g, int lx, int ty, int mw) {
        PlaylistManager pl = playlist();
        TrackInfo cur = pl.getCurrentTrack();
        int barX = lx + MP_PX;
        int barY = ty + MP_Y_PROGRESS;
        int barW = mw - MP_PX * 2 - 50;
        int barH = 4;

        g.fill(barX, barY, barX + barW, barY + barH, C_PROG_BG);
        g.fill(barX, barY, barX + barW, barY + 1, 0xFF555555);
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFF555555);

        int duration = cur != null ? cur.getDurationSeconds() : -1;
        double pos = pl.getPositionSeconds();
        double ratio;
        if      (seekPreviewRatio >= 0)         ratio = seekPreviewRatio;
        else if (duration > 0 && pos > 0)       ratio = Math.min(1.0, pos / duration);
        else                                    return;

        int fillW = (int)(ratio * (barW - 2));
        int fillC = seekPreviewRatio >= 0 ? 0xFF55BBFF : pl.isPaused() ? C_PROG_PAUSED : C_PROG_FILL;
        if (fillW > 0) g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, fillC);

        int headX = barX + 1 + fillW;
        if (seekPreviewRatio >= 0) g.fill(headX - 2, barY - 1, headX + 3, barY + barH + 1, 0xFFFFFFFF);
        else                       g.fill(headX - 1, barY,     headX + 2, barY + barH,      0xFFCCCCDD);
    }

    private void drawPlaylistRows(GuiGraphics g, int lx, int ty, int mw) {
        PlaylistManager pl = playlist();
        List<TrackInfo> tracks = pl.getDisplayTracks();
        int curIdx = pl.getCurrentDisplayIndex();

        for (int i = 0; i < MP_ITEMS_VIS; i++) {
            int idx = scrollOffset + i;
            int iy  = ty + MP_Y_PL_LIST + i * MP_ITEM_H;

            int bg;
            if      (idx == curIdx)      bg = C_ITEM_ACTIVE;
            else if (idx == hoveredRow)  bg = C_ITEM_HOVER;
            else                         bg = (i % 2 == 0) ? C_ITEM_EVEN : C_ITEM_ODD;

            g.fill(lx + 1, iy, lx + mw - 1, iy + MP_ITEM_H, bg);
            if (i > 0) g.fill(lx + MP_PX, iy, lx + mw - MP_PX, iy + 1, 0xFF2A2A3A);

            if (idx < tracks.size()) {
                if (idx == curIdx && pl.isPlaying()) {
                    g.fill(lx + 2, iy + 4, lx + 5, iy + MP_ITEM_H - 4, C_GREEN);
                } else if (idx == curIdx && pl.isPaused()) {
                    g.fill(lx + 2, iy + 4, lx + 4, iy + MP_ITEM_H - 4, C_YELLOW);
                    g.fill(lx + 5, iy + 4, lx + 7, iy + MP_ITEM_H - 4, C_YELLOW);
                }
            }
        }
    }

    private void drawMusicPlayerText(GuiGraphics g) {
        int lx = mpLeft();
        int ty = topPos;
        int mw = mpWidth();
        PlaylistManager pl = playlist();

        // App title
        g.drawCenteredString(font, "PEAR MUSIC", lx + mw / 2, ty + MP_Y_TITLE, C_TITLE);

        // NOW PLAYING label + status dot
        g.drawString(font, "NOW PLAYING", lx + MP_PX, ty + MP_Y_NP_LABEL, C_HEADER, false);
        String dot = "\u25CF";
        int dotColor = pl.isPlaying() ? C_GREEN : pl.isPaused() ? C_YELLOW : C_DIM;
        g.drawString(font, dot, lx + mw - MP_PX - font.width(dot), ty + MP_Y_NP_LABEL, dotColor, false);

        // Current track name
        TrackInfo cur = pl.getCurrentTrack();
        String name = cur != null ? cur.getTitle() : "No track selected";
        g.drawString(font, truncate(name, mw - MP_PX * 2 - 4), lx + MP_PX, ty + MP_Y_TRACK, C_WHITE, false);

        // Time display (shows seek preview while dragging)
        double displayPos = (seekPreviewRatio >= 0 && cur != null && cur.getDurationSeconds() > 0)
                ? seekPreviewRatio * cur.getDurationSeconds()
                : pl.getPositionSeconds();
        String timeStr = formatTime(displayPos) + " / " + (cur != null ? cur.getFormattedDuration() : "--:--");
        int timeColor = seekPreviewRatio >= 0 ? 0xFF55BBFF : C_DIM;
        g.drawString(font, timeStr, lx + mw - MP_PX - font.width(timeStr), ty + MP_Y_PROGRESS, timeColor, false);

        // Playlist header + count
        g.drawString(font, "PLAYLIST  (" + pl.getTracks().size() + ")", lx + MP_PX, ty + MP_Y_PL_LABEL, C_HEADER, false);

        // Playlist row text
        List<TrackInfo> tracks = pl.getDisplayTracks();
        int curDisplayIdx = pl.getCurrentDisplayIndex();
        int durCol = mw - MP_PX - 36;
        int delCol = mw - MP_PX - 5;
        int nameW  = durCol - MP_PX - 8;

        for (int i = 0; i < MP_ITEMS_VIS; i++) {
            int idx = scrollOffset + i;
            if (idx >= tracks.size()) break;
            TrackInfo t = tracks.get(idx);
            int iy    = ty + MP_Y_PL_LIST + i * MP_ITEM_H;
            int textY = iy + (MP_ITEM_H - 8) / 2;

            int nameColor = idx == curDisplayIdx ? C_WHITE : C_TEXT;
            if (t.getStatus() == TrackInfo.Status.LOADING) nameColor = C_DIM;
            if (t.getStatus() == TrackInfo.Status.ERROR)   nameColor = C_RED;
            g.drawString(font, truncate(t.getTitle(), nameW), lx + MP_PX + 8, textY, nameColor, false);

            String dur = t.getStatus() == TrackInfo.Status.LOADING ? "\u2026" : t.getFormattedDuration();
            g.drawString(font, dur, lx + durCol, textY, idx == curDisplayIdx ? C_GREEN : C_DIM, false);

            if (idx == hoveredRow) {
                g.drawString(font, "\u00D7", lx + delCol, textY, 0xFFAA4444, false);
            }
        }

        // Status message when idle
        if (!pl.isPlaying() && !pl.isPaused()) {
            String status = pl.getStatusMessage();
            if (!status.equals("Idle")) {
                g.drawString(font, truncate(status, mw - MP_PX * 2),
                        lx + MP_PX, ty + MP_Y_TRACK + 10, status.startsWith("Error") ? C_RED : C_DIM, false);
            }
        }
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (currentApp == PhoneApp.HOME) {
            if (button == 0 && isOnIcon(mx, my, 0, 0)) {
                switchTo(PhoneApp.PEAR_MUSIC);
                return true;
            }
            // Other icons: disabled placeholder, no action
        } else { // PEAR_MUSIC
            if (button == 0 && isOnProgressBar(mx, my)) {
                PlaylistManager pl = playlist();
                TrackInfo cur = pl.getCurrentTrack();
                if (cur != null && cur.getDurationSeconds() > 0 && (pl.isPlaying() || pl.isPaused())) {
                    progressDragging = true;
                    seekPreviewRatio = progressRatioAt(mx);
                    return true;
                }
            }
            if (isInPlaylistArea(mx, my)) {
                int displayIdx = rowAt((int) my);
                if (displayIdx >= 0) {
                    PlaylistManager pl = playlist();
                    if (displayIdx < pl.getDisplayTracks().size()) {
                        int trackIdx = pl.displayIndexToTrackIndex(displayIdx);
                        int delX = mpLeft() + mpWidth() - MP_PX - 5;
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
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (currentApp == PhoneApp.PEAR_MUSIC && isInPlaylistArea(mx, my)) {
            scroll((int) -Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (button == 0 && progressDragging) {
            seekPreviewRatio = progressRatioAt(mx);
            return true;
        }
        if (button == 0 && volumeSlider != null && getFocused() == volumeSlider && isDragging()) {
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
        if (currentApp == PhoneApp.PEAR_MUSIC && urlInput != null && urlInput.isFocused()
                && (key == 257 || key == 335)) {
            addUrl();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isStandalone() {
        return menu.getAudioPlayer() == null;
    }

    private PlaylistManager playlist() {
        if (isStandalone()) {
            if (standalonePlaylist == null) {
                standalonePlaylist = new PlaylistManager();
                PlaylistPersistence.load(standalonePlaylist);
            }
            return standalonePlaylist;
        }
        AudioPlayerBlockEntity be = menu.getAudioPlayer();
        return be.getPlaylist();
    }

    private void addUrl() {
        if (urlInput == null) return;
        String url = urlInput.getValue().trim();
        if (!url.isEmpty()) {
            playlist().addTrack(url);
            urlInput.setValue("");
        }
    }

    private void scroll(int delta) {
        int max = Math.max(0, playlist().getTracks().size() - MP_ITEMS_VIS);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
    }

    private void clampScroll() {
        int max = Math.max(0, playlist().getTracks().size() - MP_ITEMS_VIS);
        if (scrollOffset > max) scrollOffset = max;
    }

    private void updateHoveredRow(int my) {
        hoveredRow = isInPlaylistArea(mouse_x_cache, my) ? rowAt(my) : -1;
    }

    private boolean isOnIcon(double mx, double my, int row, int col) {
        return mx >= iconX[row][col] && mx < iconX[row][col] + ICON_SIZE
            && my >= iconY[row][col] && my < iconY[row][col] + ICON_SIZE;
    }

    private boolean isOnProgressBar(double mx, double my) {
        int barX = mpLeft() + MP_PX;
        int barY = topPos + MP_Y_PROGRESS;
        int barW = mpWidth() - MP_PX * 2 - 50;
        return mx >= barX && mx <= barX + barW && my >= barY - 3 && my <= barY + 7;
    }

    private double progressRatioAt(double mx) {
        int barX = mpLeft() + MP_PX;
        int barW = mpWidth() - MP_PX * 2 - 50;
        return Math.max(0.0, Math.min(1.0, (mx - barX - 1.0) / Math.max(1, barW - 2)));
    }

    private boolean isInPlaylistArea(double mx, double my) {
        int lx = mpLeft();
        int mw = mpWidth();
        return mx >= lx + 1 && mx < lx + mw - 1
            && my >= topPos + MP_Y_PL_LIST && my < topPos + MP_Y_PL_END;
    }

    private int rowAt(int my) {
        int localY = my - topPos - MP_Y_PL_LIST;
        if (localY < 0) return -1;
        int row = localY / MP_ITEM_H;
        return (row < MP_ITEMS_VIS) ? scrollOffset + row : -1;
    }

    private void updateScrollVisibility() {
        if (btnScrollUp == null || btnScrollDown == null) return;
        boolean needScroll = playlist().getTracks().size() > MP_ITEMS_VIS;
        btnScrollUp.visible   = needScroll;
        btnScrollDown.visible = needScroll;
    }

    private void updateToggleButtonLabels() {
        PlaylistManager pl = playlist();
        if (btnShuffle != null) {
            boolean on = pl.getShuffleMode() == PlaylistManager.ShuffleMode.ON;
            btnShuffle.setMessage(Component.literal("\u21CC").withStyle(s -> s.withColor(on ? 0x55FF55 : 0x999999)));
        }
        if (btnRepeat != null) {
            Component msg = switch (pl.getRepeatMode()) {
                case NONE -> Component.literal("\u21BA").withStyle(s -> s.withColor(0x999999));
                case ALL  -> Component.literal("\u21BB").withStyle(s -> s.withColor(0x55FF55));
                case ONE  -> Component.literal("\u21BB1").withStyle(s -> s.withColor(0xFFFF55));
            };
            btnRepeat.setMessage(msg);
        }
        if (btnListen != null) {
            Component msg = switch (pl.getListenMode()) {
                case HEADPHONES -> Component.literal("\u266A").withStyle(s -> s.withColor(0x55FF55));
                case SPEAKER    -> Component.literal("\u266B").withStyle(s -> s.withColor(0x55BBFF));
                case MUTE       -> Component.literal("\u00D7").withStyle(s -> s.withColor(0xFF5555));
            };
            btnListen.setMessage(msg);
        }
        if (volumeSlider != null) volumeSlider.active = true;
    }

    private void syncSpeakerMode() {
        if (!isStandalone()) return;
        PlaylistManager pl = playlist();
        boolean speakerActive = pl.getListenMode() == PlaylistManager.ListenMode.SPEAKER && pl.isPlaying();
        TrackInfo cur = pl.getCurrentTrack();
        String url = (speakerActive && cur != null) ? cur.getUrl() : null;
        if (java.util.Objects.equals(url, lastSentSpeakerUrl)) return;
        lastSentSpeakerUrl = url;
        if (url != null) {
            PacketDistributor.sendToServer(new SpeakerBroadcastPacket(url, pl.getVolume(), true));
        } else {
            PacketDistributor.sendToServer(new SpeakerBroadcastPacket("", 0, false));
        }
    }

    private String truncate(String text, int maxPixels) {
        if (font.width(text) <= maxPixels) return text;
        while (!text.isEmpty() && font.width(text + "\u2026") > maxPixels)
            text = text.substring(0, text.length() - 1);
        return text + "\u2026";
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
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, h).build());
    }

    @Override
    public void onClose() {
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
