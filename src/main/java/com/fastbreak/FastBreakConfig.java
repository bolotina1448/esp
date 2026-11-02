package com.fastbreak;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Properties;

public class FastBreakConfig {

    public enum Mode {
        /** Осторожный пресет под Polar: ~20% быстрее ванилы, рандом порога, задержка между блоками. */
        POLAR("Polar"),
        /** Ломает блок, как только прогресс дошёл до threshold (0.0 – 1.0). */
        THRESHOLD("Threshold"),
        /** Каждый тик добавляет прогресс * (multiplier - 1). */
        MULTIPLIER("Multiplier"),
        /** Самый быстрый вариант, который принимает ванильный/Spigot/Paper сервер: 70% времени, без задержки. */
        MAX("Max"),
        /** Мгновенно: STOP_DESTROY в тот же тик, что и START. Работает только там, где сервер не проверяет время. */
        INSTANT("Instant");

        public final String display;

        Mode(String display) {
            this.display = display;
        }

        public static Mode parse(String s) {
            if (s == null) return null;
            if (s.trim().equalsIgnoreCase("blatant")) return INSTANT;
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(s.trim())) return m;
            }
            return null;
        }
    }

    // Пресет POLAR (фиксированный, не зависит от threshold/jitter/delay в конфиге)
    public static final float POLAR_THRESHOLD = 0.80f;
    public static final float POLAR_JITTER = 0.04f;
    public static final int POLAR_HIT_DELAY = 3;

    /** Порог, с которого сервер 1.8.9 сразу принимает STOP_DESTROY_BLOCK (ItemInWorldManager.blockRemoving). */
    public static final float SERVER_ACCEPT = 0.70f;

    private final File file;

    public boolean enabled = false;
    public Mode mode = Mode.POLAR;
    /** Для THRESHOLD: доля ванильного времени ломания, после которой блок ломается. */
    public float threshold = 0.70f;
    /** Для MULTIPLIER: множитель скорости. */
    public float multiplier = 1.4f;
    /** Задержка между блоками в тиках (ванила = 5). Для THRESHOLD и MULTIPLIER. */
    public int hitDelay = 1;
    /** Случайный разброс порога ±jitter, чтобы время ломания не было одинаковым. */
    public float jitter = 0.03f;
    /** Не ускорять, если игрок в воздухе (меньше шансов на флаг). */
    public boolean onlyOnGround = true;
    public boolean hud = true;
    /** Ускорять только подходящим инструментом и только блоки из его списка (ToolRules). */
    public boolean toolsOnly = true;
    /** INSTANT: через сколько тиков после начала ломания слать STOP (0 = в тот же тик, 1 тик = 0.05 с). */
    public int instantDelay = 1;
    public final ToolRules tools = new ToolRules();

    public FastBreakConfig(File file) {
        this.file = file;
    }

    public float effectiveThreshold() {
        switch (mode) {
            case POLAR: return POLAR_THRESHOLD;
            case MAX: return SERVER_ACCEPT;
            case INSTANT: return 0f;
            default: return threshold;
        }
    }

    public float effectiveJitter() {
        switch (mode) {
            case POLAR: return POLAR_JITTER;
            case MAX:
            case INSTANT: return 0f;
            default: return jitter;
        }
    }

    public int effectiveHitDelay() {
        switch (mode) {
            case POLAR: return POLAR_HIT_DELAY;
            case MAX:
            case INSTANT: return 0;
            default: return hitDelay;
        }
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
            threshold = clamp(parseFloat(p.getProperty("threshold"), threshold), 0.05f, 1f);
            multiplier = clamp(parseFloat(p.getProperty("multiplier"), multiplier), 1f, 5f);
            hitDelay = (int) clamp(parseFloat(p.getProperty("hitDelay"), hitDelay), 0, 5);
            jitter = clamp(parseFloat(p.getProperty("jitter"), jitter), 0f, 0.2f);
            onlyOnGround = Boolean.parseBoolean(p.getProperty("onlyOnGround", String.valueOf(onlyOnGround)));
            hud = Boolean.parseBoolean(p.getProperty("hud", String.valueOf(hud)));
            toolsOnly = Boolean.parseBoolean(p.getProperty("toolsOnly", String.valueOf(toolsOnly)));
            instantDelay = (int) clamp(parseFloat(p.getProperty("instantDelay"), instantDelay), 0, 10);
            tools.load(p);
        } catch (Exception e) {
            System.err.println("[FastBreak] Failed to load config: " + e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("mode", mode.name());
        p.setProperty("threshold", fmt(threshold));
        p.setProperty("multiplier", fmt(multiplier));
        p.setProperty("hitDelay", String.valueOf(hitDelay));
        p.setProperty("jitter", fmt(jitter));
        p.setProperty("onlyOnGround", String.valueOf(onlyOnGround));
        p.setProperty("hud", String.valueOf(hud));
        p.setProperty("toolsOnly", String.valueOf(toolsOnly));
        p.setProperty("instantDelay", String.valueOf(instantDelay));
        tools.save(p);
        OutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new FileOutputStream(file);
            p.store(out, "FastBreak config (modes: POLAR, THRESHOLD, MULTIPLIER, MAX, INSTANT)");
        } catch (Exception e) {
            System.err.println("[FastBreak] Failed to save config: " + e);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    static float parseFloat(String s, float def) {
        if (s == null) return def;
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
