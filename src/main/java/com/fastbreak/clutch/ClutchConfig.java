package com.fastbreak.clutch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class ClutchConfig {

    public enum Mode {
        /** Срабатывает только когда падаешь (сбили в войд / с высоты) — ставит блок под ноги. */
        CLUTCH("Clutch"),
        /** Обычный скаффолд: ставит блок под ноги всегда, когда под ними пусто (бридж). Клатч тоже работает. */
        SCAFFOLD("Scaffold");

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
    public Mode mode = Mode.CLUTCH;

    /** Скорость поворота (°/тик) в обычном скаффолде. */
    public float minSpeed = 35f;
    public float maxSpeed = 60f;
    /** Скорость поворота (°/тик) во время клатча (как BlockClutch... в Augustus — быстрее). */
    public float clutchMinSpeed = 80f;
    public float clutchMaxSpeed = 140f;

    /** Клатчить, если под тобой пусто на столько блоков вниз. */
    public int fallBlocks = 3;
    /** Клатчить только над войдом (под тобой вообще нет блоков). */
    public boolean voidOnly = false;
    /** Радиус доп. поиска блоков (ExtraBlockSearch). 0 — только прямо под ногами. */
    public int search = 2;
    /** Дистанция до грани, на которую можно ставить. */
    public float reach = 4.5f;

    /** Переключаться на блоки в хотбаре (самый большой стак). */
    public boolean autoSwitch = true;
    /** Возвращать прежний слот после клатча. */
    public boolean switchBack = true;
    /** Не махать рукой на клиенте (пакет взмаха всё равно уходит). */
    public boolean noSwing = false;
    /** Задержка между постановками блоков, тиков. */
    public int placeDelay = 0;
    /** Сколько тиков держать поворот после постановки, прежде чем плавно вернуть камеру. */
    public int keepTicks = 3;

    public boolean hud = true;

    public ClutchConfig(File file) {
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
            Mode m = Mode.parse(p.getProperty("mode"));
            if (m != null) mode = m;
            minSpeed = clamp(num(p, "minSpeed", minSpeed), 1f, 180f);
            maxSpeed = clamp(num(p, "maxSpeed", maxSpeed), minSpeed, 180f);
            clutchMinSpeed = clamp(num(p, "clutchMinSpeed", clutchMinSpeed), 1f, 180f);
            clutchMaxSpeed = clamp(num(p, "clutchMaxSpeed", clutchMaxSpeed), clutchMinSpeed, 180f);
            fallBlocks = (int) clamp(num(p, "fallBlocks", fallBlocks), 1, 30);
            voidOnly = bool(p, "voidOnly", voidOnly);
            search = (int) clamp(num(p, "search", search), 0, 3);
            reach = clamp(num(p, "reach", reach), 3f, 6f);
            autoSwitch = bool(p, "autoSwitch", autoSwitch);
            switchBack = bool(p, "switchBack", switchBack);
            noSwing = bool(p, "noSwing", noSwing);
            placeDelay = (int) clamp(num(p, "placeDelay", placeDelay), 0, 10);
            keepTicks = (int) clamp(num(p, "keepTicks", keepTicks), 0, 20);
            hud = bool(p, "hud", hud);
        } catch (Exception e) {
            System.err.println("[Clutch] Failed to load config: " + e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("mode", mode.name());
        p.setProperty("minSpeed", String.valueOf(minSpeed));
        p.setProperty("maxSpeed", String.valueOf(maxSpeed));
        p.setProperty("clutchMinSpeed", String.valueOf(clutchMinSpeed));
        p.setProperty("clutchMaxSpeed", String.valueOf(clutchMaxSpeed));
        p.setProperty("fallBlocks", String.valueOf(fallBlocks));
        p.setProperty("voidOnly", String.valueOf(voidOnly));
        p.setProperty("search", String.valueOf(search));
        p.setProperty("reach", String.valueOf(reach));
        p.setProperty("autoSwitch", String.valueOf(autoSwitch));
        p.setProperty("switchBack", String.valueOf(switchBack));
        p.setProperty("noSwing", String.valueOf(noSwing));
        p.setProperty("placeDelay", String.valueOf(placeDelay));
        p.setProperty("keepTicks", String.valueOf(keepTicks));
        p.setProperty("hud", String.valueOf(hud));
        OutputStream out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new FileOutputStream(file);
            p.store(out, "Clutch / Scaffold config (modes: CLUTCH, SCAFFOLD)");
        } catch (Exception e) {
            System.err.println("[Clutch] Failed to save config: " + e);
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String s = p.getProperty(key);
        return s == null ? def : Boolean.parseBoolean(s.trim());
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
