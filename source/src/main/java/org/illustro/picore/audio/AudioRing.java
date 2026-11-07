package org.illustro.picore.audio;

/**
 * A fixed-capacity mono PCM ring buffer bridging a live audio producer (ffmpeg reader, sidecar stream,
 * etc.) and the Simple Voice Chat pump (consumer). On overflow the oldest samples are dropped — that
 * bounds latency: live audio must never lag behind its picture, so we'd rather skip a little than
 * accumulate a growing delay.
 *
 * <p>Generalized into PiAudio after this class turned up byte-for-byte identical in both ArcadeCab
 * and VideoWall.
 */
public final class AudioRing {

    private final short[] buf;
    private int head;
    private int tail;
    private int size;

    public AudioRing(int capacity) {
        this.buf = new short[capacity];
    }

    public synchronized void write(short[] data, int len) {
        for (int i = 0; i < len; i++) {
            if (size == buf.length) {                 // full → drop oldest
                head = (head + 1) % buf.length;
                size--;
            }
            buf[tail] = data[i];
            tail = (tail + 1) % buf.length;
            size++;
        }
    }

    /** Fill {@code out} with the next samples; zero-pad on underrun (keeps the stream alive). */
    public synchronized void read(short[] out) {
        int n = Math.min(out.length, size);
        for (int i = 0; i < n; i++) {
            out[i] = buf[head];
            head = (head + 1) % buf.length;
            size--;
        }
        for (int i = n; i < out.length; i++) out[i] = 0;
    }

    public synchronized int available() {
        return size;
    }

    /** Discard up to {@code n} of the oldest samples (used to pull audio back to real time). */
    public synchronized void drop(int n) {
        int d = Math.min(n, size);
        head = (head + d) % buf.length;
        size -= d;
    }
}
