package cn.hydrogen.launcher;

/** Playback observations only. Native looping remains the normal path. */
final class VideoLoopGuard {
    static final long CHECK_MS = 1100;
    static final long STALL_MS = 3300;
    static final long RESTART_TIMEOUT_MS = 3300;
    static final int NONE = 0, RESTART = 1, RECREATE = 2;
    private int lastPosition = -1;
    private long lastFrames = -1, progressAt, restartAt = -1;
    private boolean progress;

    void reset(long now) {
        lastPosition = -1;
        lastFrames = -1;
        progressAt = now;
        restartAt = -1;
        progress = false;
    }

    boolean recovering() { return restartAt >= 0; }
    boolean madeProgress() { return progress; }

    void restarting(long now) {
        reset(now);
        restartAt = now;
    }

    int inspect(long now, int position, int duration, boolean playing, long frames) {
        // The first post-seek position is a baseline, not proof that playback resumed.
        progress = playing && ((lastPosition >= 0 && position >= 0 && position != lastPosition)
                || (lastFrames >= 0 && frames != lastFrames));
        lastPosition = position;
        lastFrames = frames;
        if (recovering()) {
            if (progress && playing) {
                restartAt = -1;
                progressAt = now;
            } else if (now - restartAt >= RESTART_TIMEOUT_MS) {
                return RECREATE;
            }
            return NONE;
        }
        if (progress) progressAt = now;
        if (duration <= 0 || now - progressAt < STALL_MS) return NONE;
        long tail = Math.max(100, Math.min(750, duration / 5));
        boolean nearEnd = position >= Math.max(0, duration - tail);
        // A decoder can claim it is playing while stuck at EOF. A longer mid-file
        // stall is also recoverable for these local, non-streaming backgrounds.
        if (!playing || nearEnd || now - progressAt >= STALL_MS * 3) return RESTART;
        return NONE;
    }
}
