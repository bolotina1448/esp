package com.blockhit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class BlockHitConfig {

    private final File file;

    public boolean enabled = true;
    /** Начинать блок самому, если зажал ПКМ уже во время копания (в 1.8 так нельзя). */
    public boolean autoBlock = true;

    public BlockHitConfig(File file) {
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
            autoBlock = bool(p, "autoBlock", autoBlock);
        } catch (Exception e) {
            System.err.println("[BlockHitMining] Failed to load config: " + e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("autoBlock", String.valueOf(autoBlock));
        OutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new FileOutputStream(file);
            p.store(out, "BlockHit Mining config");
        } catch (Exception e) {
            System.err.println("[BlockHitMining] Failed to save config: " + e);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String s = p.getProperty(key);
        return s == null ? def : Boolean.parseBoolean(s.trim());
    }
}
