package com.visualmod.util;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Совместимость с Iris. Класс намеренно не ссылается на типы Iris — мод обязан
 * запускаться и без него, поэтому всё через рефлексию и один раз закэшировано.
 */
public final class IrisCompat {

    private static volatile boolean irisLoaded;
    private static volatile boolean probed;
    private static volatile Object api;
    private static volatile Method mShaderPack, mShadowMap, mEntityShadow;

    /** Пак включён/выключается между кадрами — переспрашиваем не чаще раза в секунду. */
    private static final long REPROBE_NANOS = 1_000_000_000L;
    private static long nextProbeAt;
    private static volatile boolean shaderActive;
    private static volatile boolean shadowPass;

    private IrisCompat() {}

    public static boolean installed() {
        tick();
        return irisLoaded;
    }

    /** true, когда сейчас активен шейдер-пак (а не просто стоит мод). */
    public static boolean shaderActive() {
        tick();
        return shaderActive;
    }

    /**
     * Iris рисует карту теней отдельным проходом, в котором наша мир-геометрия
     * попала бы в depth-текстуру вместо экрана. В таком проходе рисовать нельзя.
     */
    public static boolean skipWorldPass() {
        tick();
        return shaderActive && shadowPass;
    }

    private static void tick() {
        if (!probed) {
            probe();
            return;
        }
        long now = System.nanoTime();
        if (now - nextProbeAt < 0) return;
        nextProbeAt = now + REPROBE_NANOS;
        refreshFlags();
    }

    private static synchronized void probe() {
        if (probed) return;
        irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
        if (irisLoaded) {
            try {
                Class<?> cls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = cls.getMethod("getInstance").invoke(null);
                mShaderPack = find(cls, "isShaderPackInUse");
                mShadowMap = find(cls, "isRenderingShadowMap");
                mEntityShadow = find(cls, "isCurrentlyRenderingEntityShadow");
            } catch (Throwable t) {
                api = null;
                Diag.once("IrisCompat.probe", t);
            }
        }
        probed = true;
        refreshFlags();
        nextProbeAt = System.nanoTime() + REPROBE_NANOS;
    }

    private static Method find(Class<?> cls, String name) {
        try {
            return cls.getMethod(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void refreshFlags() {
        Object a = api;
        shaderActive = read(a, mShaderPack);
        shadowPass = read(a, mShadowMap) || read(a, mEntityShadow);
    }

    private static boolean read(Object target, Method m) {
        if (target == null || m == null) return false;
        try {
            return Boolean.TRUE.equals(m.invoke(target));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Для отладки: что вообще увидели. */
    public static String diag() {
        tick();
        if (!irisLoaded) return "Iris не установлен";
        return "Iris: шейдер-пак " + (shaderActive ? "активен" : "выключен")
                + ", проход теней " + (shadowPass ? "идёт" : "нет");
    }
}
