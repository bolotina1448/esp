package com.safewalk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class SafeWalkConfig {

    public enum Mode {
        /** У края сам жмёт шифт на пару тиков и отпускает, как только поставил блок. */
        SNEAK("Sneak"),
        /** Держит у края без приседа (пакет приседа не уходит). */
        SILENT("Silent");

        public final String display;

        Mode(String display) {
            this.display = display;
        }

        public static Mode parse(String s) {
            if (s == null) return null;
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(s.trim())) return m;
            }
            return null;
        }
    }

    private final File file;

    public Mode mode = Mode.SNEAK;
    /** Сколько тиков минимум держать шифт у края (случайно между min и max). 1 тик = 0.05 с. */
    public int minTicks = 2;
    public int maxTicks = 3;

    public boolean enabled = true;
    /** Работать только когда в руке блок. */
    public boolean onlyBlocks = true;
    /** Работать только когда смотришь на блок, на котором стоишь. */
    public boolean onlyLooking = true;
    public boolean hud = true;

    public SafeWalkConfig(File file) {
        this.file = file;
    }

    public void load() {
        if (!file.exists()) {
            save();
            return;
        }
        Properties p = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            p.load(in);
            enabled = bool(p, "enabled", enabled);
            onlyBlocks = bool(p, "onlyBlocks", onlyBlocks);
            onlyLooking = bool(p, "onlyLooking", onlyLooking);
            hud = bool(p, "hud", hud);
            Mode m = Mode.parse(p.getProperty("mode"));
            if (m != null) mode = m;
            minTicks = integer(p, "minTicks", minTicks, 0, 20);
            maxTicks = integer(p, "maxTicks", maxTicks, minTicks, 20);
        } catch (Exception e) {
            System.err.println("[SafeWalk] Failed to load config: " + e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("onlyBlocks", String.valueOf(onlyBlocks));
        p.setProperty("onlyLooking", String.valueOf(onlyLooking));
        p.setProperty("hud", String.valueOf(hud));
        p.setProperty("mode", mode.name());
        p.setProperty("minTicks", String.valueOf(minTicks));
        p.setProperty("maxTicks", String.valueOf(maxTicks));
        OutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new FileOutputStream(file);
            p.store(out, "SafeWalk config");
        } catch (Exception e) {
            System.err.println("[SafeWalk] Failed to save config: " + e);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    private static int integer(Properties p, String key, int def, int min, int max) {
        String s = p.getProperty(key);
        if (s == null) return def;
        try {
            int v = Integer.parseInt(s.trim());
            return v < min ? min : (v > max ? max : v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String s = p.getProperty(key);
        return s == null ? def : Boolean.parseBoolean(s.trim());
    }
}
