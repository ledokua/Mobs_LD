package net.ledok.mobs_ld.mob.behaviour;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Identifies the resource bytes used to produce a bake, independent of the enabled resource packs. */
public final class HitTrackSource {
    private HitTrackSource() {
    }

    public static JsonObject fingerprints(InputStream geometry, InputStream animation) throws IOException {
        JsonObject source = new JsonObject();
        source.addProperty("geometry_sha256", sha256(geometry));
        source.addProperty("animation_sha256", sha256(animation));
        return source;
    }

    private static String sha256(InputStream resource) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = resource.readAllBytes();
            // Normalize CRLF -> LF so asset fingerprints match across Windows and Unix git checkouts.
            byte[] normalized = new byte[raw.length];
            int out = 0;
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == '\r' && i + 1 < raw.length && raw[i + 1] == '\n') {
                    continue;
                }
                normalized[out++] = raw[i];
            }
            digest.update(normalized, 0, out);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java must support SHA-256", e);
        }
    }
}
