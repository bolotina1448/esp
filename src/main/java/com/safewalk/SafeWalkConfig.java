package com.safewalk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class SafeWalkConfig {

    private final File file;

    public boolean enabled = true;
    /** Работать только когда в руке блок. */
    public boolean onlyBlocks = true;
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
            hud = bool(p, "hud", hud);
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
        p.setProperty("hud", String.valueOf(hud));
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

    private static boolean bool(Properties p, String key, boolean def) {
        String s = p.getProperty(key);
        return s == null ? def : Boolean.parseBoolean(s.trim());
    }
}
