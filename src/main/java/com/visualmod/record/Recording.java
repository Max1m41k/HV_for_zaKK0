package com.visualmod.record;

import java.util.ArrayList;
import java.util.List;

public final class Recording {

    public static final class Frame {
        public double x, y, z;
        public float yaw, pitch;
    }

    /** Момент подтверждённого килла в секундах от начала клипа. */
    public static final class Mark {
        public float t;
        public String label = "";
    }

    public String title = "rec";
    public String world = "";
    public long startedAt;
    public int hz = 20;
    public final List<Frame> frames = new ArrayList<>();
    public final List<Mark> marks = new ArrayList<>();

    public float duration() {
        return frames.size() < 2 ? 0f : (frames.size() - 1) / (float) Math.max(1, hz);
    }

    public void sample(float t, double[] outPos, float[] outRot) {
        int last = frames.size() - 1;
        if (last < 0) return;
        float scaled = Math.max(0f, Math.min(last, t * Math.max(1, hz)));
        int i = (int) scaled;
        float frac = scaled - i;
        Frame a = frames.get(i);
        Frame b = frames.get(Math.min(last, i + 1));

        outPos[0] = a.x + (b.x - a.x) * frac;
        outPos[1] = a.y + (b.y - a.y) * frac;
        outPos[2] = a.z + (b.z - a.z) * frac;

        outRot[0] = lerpAngle(a.yaw, b.yaw, frac);
        outRot[1] = a.pitch + (b.pitch - a.pitch) * frac;
    }

    static float lerpAngle(float from, float to, float t) {
        float d = ((to - from) % 360f + 540f) % 360f - 180f;
        return from + d * t;
    }
}
