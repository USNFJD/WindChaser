package cn.windchaser.motion;

import android.view.Choreographer;

/** The same uptime-based frame clock and curve in the host and companion APKs. */
public final class CardTimeline implements Choreographer.FrameCallback {
    public interface Frame { void draw(float progress, boolean finished); }
    public static final int VERSION = 1;
    public static final long DURATION_MS = 160, LEAD_MS = 96;
    private Choreographer clock;
    private Frame listener;
    private long start, duration;
    public boolean running() { return listener != null; }
    public void start(long startUptime, long durationMs, Frame callback) {
        stop();
        start = startUptime; duration = durationMs; listener = callback;
        clock = Choreographer.getInstance(); clock.postFrameCallback(this);
    }
    public void stop() {
        if (clock != null) clock.removeFrameCallback(this);
        listener = null; clock = null;
    }
    public static float progress(long frameMillis, long startMillis, long durationMs) {
        if (frameMillis <= startMillis) return 0;
        if (durationMs <= 0 || frameMillis - startMillis >= durationMs) return 1;
        float linear = (float)(frameMillis - startMillis) / durationMs;
        return 1 - (1 - linear) * (1 - linear);
    }
    @Override public void doFrame(long frameTimeNanos) {
        Frame current = listener;
        if (current == null) return;
        float value = progress(frameTimeNanos / 1000000L, start, duration);
        boolean finished = value >= 1;
        if (finished) { listener = null; clock = null; }
        current.draw(value, finished);
        if (!finished && listener == current && clock != null) clock.postFrameCallback(this);
    }
}
