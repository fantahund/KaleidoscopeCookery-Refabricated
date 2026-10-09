package de.emrion.kaleidoscopeCookery.station.trash;

import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Keyframe-Animationen des Mülleimers (TrashCanAnimation im Mod) und ihre Auswertung wie KeyframeAnimations#animate.
 * Werte in Modell-Einheiten: Position in Pixeln (posVec), Rotation in Grad (degreeVec).
 */
public enum TrashCanAnimation {

    PUT(0.5833f, Map.of(
            "bone2", channels(
                    List.of(lin(0, -0, 0, 0), lin(0.125f, -12.5f, 0, 0), cat(0.2083f, -12.5f, 0, 0), cat(0.3333f, 7.5f, 0, 0),
                            cat(0.4167f, -7.5f, 0, 0), cat(0.5f, 2.5f, 0, 0), cat(0.5417f, -2.5f, 0, 0), cat(0.5833f, 0, 0, 0)),
                    List.of(lin(0, 0, 0, 0), lin(0.125f, 0, 2, 0), cat(0.2083f, 0, 0, 0))))),
    WITHDRAW(0.5f, Map.of(
            "bone2", channels(
                    List.of(lin(0, 0, 0, 0), lin(0.125f, -7.5f, 0, 0), lin(0.25f, 5, 0, 0), lin(0.375f, -2.5f, 0, 0), lin(0.5f, 0, 0, 0)),
                    List.of(lin(0, 0, 0, 0), cat(0.125f, 0, 1, 0), cat(0.25f, 0, 0, 0), cat(0.375f, 0, 0, 0), cat(0.5f, 0, 0, 0))))),
    PLAYER1(1.0f, Map.of(
            "bone2", channels(List.of(lin(0, -10, 0, 0)), List.of(lin(0, 0, 0.5f, 0))),
            "eye", channels(List.of(), List.of(lin(0, 0, 4, 0))),
            "bone3", channels(List.of(), List.of(lin(0.375f, 0, 0, 0), lin(0.4573f, 0, 0, 0), lin(0.4583f, 0, -2, 0),
                    lin(0.5823f, 0, -2, 0), lin(0.5833f, 0, 0, 0))))),
    PLAYER2(2.75f, Map.of(
            "bone2", channels(List.of(lin(0, -10, 0, 0)), List.of(lin(0, 0, 0.5f, 0))),
            "eye", channels(List.of(), List.of(lin(0, 0, 4, 0))),
            "bone3", channels(List.of(), List.of(lin(0.1667f, 0, 0, 0), lin(0.375f, -1, 0, 0), lin(1.0417f, -1, 0, 0),
                    lin(1.4167f, 1, 0, 0), lin(2.4583f, 1, 0, 0), lin(2.6667f, 0, 0, 0))))),
    ENTER(0.6667f, Map.of(
            "bone", channels(List.of(cat(0, 0, 0, 0), cat(0.125f, 0, 5, 0), cat(0.2083f, 0, -5, 0), cat(0.2917f, 0, 5, 0), cat(0.375f, 0, 0, 0)),
                    List.of()),
            "bone2", channels(
                    List.of(cat(0, 0, 0, 0), cat(0.125f, 0, 0, 10), cat(0.25f, 0, 0, -5), cat(0.375f, 0, 0, 2.5f), cat(0.5f, 0, 0, 0)),
                    List.of(cat(0, 0, 0, 0), cat(0.125f, 0, 5, 0), cat(0.2917f, 0, 0, 0)))));

    record Keyframe(float time, Vector3f value, boolean catmullRom) {
    }

    record Channels(List<Keyframe> rotation, List<Keyframe> position) {
    }

    private final float length;
    private final Map<String, Channels> parts;

    TrashCanAnimation(float length, Map<String, Channels> parts) {
        this.length = length;
        this.parts = parts;
    }

    private static Keyframe lin(float time, float x, float y, float z) {
        return new Keyframe(time, new Vector3f(x, y, z), false);
    }

    private static Keyframe cat(float time, float x, float y, float z) {
        return new Keyframe(time, new Vector3f(x, y, z), true);
    }

    private static Channels channels(List<Keyframe> rotation, List<Keyframe> position) {
        return new Channels(rotation, position);
    }

    public float length() {
        return length;
    }

    /**
     * Rotation (Grad) und Position (Pixel) eines Teils zur Zeit elapsed (Sekunden) auf rotation/position addieren.
     */
    public void apply(String part, float elapsed, Vector3f rotation, Vector3f position) {
        Channels channels = parts.get(part);
        if (channels == null) {
            return;
        }
        rotation.add(sample(channels.rotation(), elapsed));
        position.add(sample(channels.position(), elapsed));
    }

    private static Vector3f sample(List<Keyframe> keyframes, float elapsed) {
        if (keyframes.isEmpty()) {
            return new Vector3f();
        }
        // Erster Keyframe, dessen Zeit >= elapsed ist, davor der Startpunkt
        int search = keyframes.size();
        for (int k = 0; k < keyframes.size(); k++) {
            if (elapsed <= keyframes.get(k).time()) {
                search = k;
                break;
            }
        }
        int i = Math.max(0, search - 1);
        int j = Math.min(keyframes.size() - 1, i + 1);
        Keyframe a = keyframes.get(i);
        Keyframe b = keyframes.get(j);
        float t = j != i ? Math.clamp((elapsed - a.time()) / (b.time() - a.time()), 0, 1) : 0;
        if (!b.catmullRom()) {
            return new Vector3f(a.value()).lerp(b.value(), t);
        }
        Vector3f p0 = keyframes.get(Math.max(0, i - 1)).value();
        Vector3f p3 = keyframes.get(Math.min(keyframes.size() - 1, j + 1)).value();
        return new Vector3f(catmullRom(t, p0.x, a.value().x, b.value().x, p3.x), catmullRom(t, p0.y, a.value().y, b.value().y, p3.y),
                catmullRom(t, p0.z, a.value().z, b.value().z, p3.z));
    }

    /**
     * Mth#catmullrom
     */
    private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        return 0.5f * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t);
    }
}
