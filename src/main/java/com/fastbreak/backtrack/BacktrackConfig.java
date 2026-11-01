package com.fastbreak.backtrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class BacktrackConfig {

    public enum Mode {
        /**
         * Задерживает ВСЕ входящие пакеты одинаково (включая transaction/keepalive).
         * Для Polar это выглядит как обычный пинг повыше — позиции и транзакции не расходятся.
         */
        POLAR("Polar"),
        /**
         * Классический backtrack: задерживает только движение сущностей (S14/S18/S19).
         * Сильнее эффект, но transaction-пакеты идут без задержки — Polar это видит.
         */
        ENTITY("Entity");

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

    public boolean enabled = false;
    public Mode mode = Mode.POLAR;
    /** На сколько мс придерживать пакеты. */
    public int delay = 120;
    /** Включаться, только если цель дальше minRange... */
    public float minRange = 2.5f;
    /** ...и ближе maxRange блоков. */
    public float maxRange = 6.0f;
    /** Максимум непрерывной задержки, потом сброс очереди (чтобы не было скачка пинга/таймаута). */
    public int maxHold = 500;
    /** Пауза после сброса, мс. */
    public int cooldown = 250;
    /** Сколько мс после последнего удара считается "в бою". */
    public int combatWindow = 1000;
    public boolean hud = true;

    public BacktrackConfig(File file) {
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
            enabled = Boolean.parseBoolean(p.getProperty("enabled", String.valueOf(enabled)));
            Mode m = Mode.parse(p.getProperty("mode"));
            if (m != null) mode = m;
            delay = (int) clamp(num(p, "delay", delay), 0, 1000);
            minRange = clamp(num(p, "minRange", minRange), 0f, 6f);
            maxRange = clamp(num(p, "maxRange", maxRange), 1f, 10f);
            maxHold = (int) clamp(num(p, "maxHold", maxHold), 50, 5000);
            cooldown = (int) clamp(num(p, "cooldown", cooldown), 0, 5000);
            combatWindow = (int) clamp(num(p, "combatWindow", combatWindow), 100, 10000);
            hud = Boolean.parseBoolean(p.getProperty("hud", String.valueOf(hud)));
        } catch (Exception e) {
            System.err.println("[Backtrack] Failed to load config: " + e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("mode", mode.name());
        p.setProperty("delay", String.valueOf(delay));
        p.setProperty("minRange", String.valueOf(minRange));
        p.setProperty("maxRange", String.valueOf(maxRange));
        p.setProperty("maxHold", String.valueOf(maxHold));
        p.setProperty("cooldown", String.valueOf(cooldown));
        p.setProperty("combatWindow", String.valueOf(combatWindow));
        p.setProperty("hud", String.valueOf(hud));
        OutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new FileOutputStream(file);
            p.store(out, "Backtrack config (modes: POLAR, ENTITY)");
        } catch (Exception e) {
            System.err.println("[Backtrack] Failed to save config: " + e);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    private static float num(Properties p, String key, float def) {
        String s = p.getProperty(key);
        if (s == null) return def;
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
