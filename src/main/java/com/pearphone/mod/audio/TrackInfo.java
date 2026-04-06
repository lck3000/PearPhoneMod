package com.pearphone.mod.audio;

/** One playlist entry. Fields are volatile so the UI thread sees background updates. */
public class TrackInfo {

    public enum Status { LOADING, READY, ERROR }

    private final String url;
    private volatile String title;
    private volatile int durationSeconds = -1;  // -1 = unknown
    private volatile Status status = Status.LOADING;

    public TrackInfo(String url) {
        this.url = url;
        // Use a trimmed URL fragment as placeholder until real title arrives
        int slash = url.lastIndexOf('/');
        this.title = (slash >= 0 && slash < url.length() - 1)
                ? url.substring(slash + 1)
                : url;
        if (this.title.length() > 40) this.title = this.title.substring(0, 40) + "…";
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getUrl()              { return url; }
    public String getTitle()            { return title; }
    public int    getDurationSeconds()  { return durationSeconds; }
    public Status getStatus()           { return status; }

    /** Human-readable duration string, e.g. "3:47" or "--:--" if unknown. */
    public String getFormattedDuration() {
        if (durationSeconds < 0) return "--:--";
        return String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }

    // ── Setters (called from background metadata thread) ─────────────────────

    public void setTitle(String title)              { this.title = title; }
    public void setDurationSeconds(int seconds)     { this.durationSeconds = seconds; }
    public void setStatus(Status status)            { this.status = status; }
}
