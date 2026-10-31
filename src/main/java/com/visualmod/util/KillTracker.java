package com.visualmod.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Общий детект «мы убили то, по чему били». Клиент не получает подтверждение авторства
 * килла, поэтому считаем киллом смерть цели, которую ударили менее WINDOW мс назад.
 */
public final class KillTracker {

    /** Слушатель подтверждённого килла. Регистрируется один раз из статического блока модуля. */
    public interface Hook {
        void onKill(Entity victim, double x, double y, double z, float height);
    }

    private static final class Pending {
        final Entity entity;
        double x, y, z;
        long hitAt;
        Pending(Entity e) {
            entity = e;
            x = e.getX(); y = e.getY(); z = e.getZ();
            hitAt = System.currentTimeMillis();
        }
    }

    /** Цель должна умереть в этом окне, иначе засчитаем обычный промах. */
    private static final long WINDOW = 6000;
    private static final List<Hook> HOOKS = new ArrayList<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private KillTracker() {}

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    public static void register(Hook hook) {
        if (hook == null) return;
        synchronized (HOOKS) {
            if (!HOOKS.contains(hook)) HOOKS.add(hook);
        }
    }

    public static void onAttack(Entity target) {
        if (!(target instanceof LivingEntity)) return;
        long now = System.currentTimeMillis();
        synchronized (PENDING) {
            for (Pending p : PENDING) {
                if (p.entity == target) { p.hitAt = now; return; }
            }
            PENDING.add(new Pending(target));
            while (PENDING.size() > 16) PENDING.remove(0);
        }
    }

    private static void tick(MinecraftClient mc) {
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            if (mc.world == null) { PENDING.clear(); return; }
            long now = System.currentTimeMillis();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                Entity e = p.entity;
                LivingEntity le = e instanceof LivingEntity l ? l : null;
                boolean dead = e.isRemoved() || (le != null && le.getHealth() <= 0f);
                if (dead) {
                    it.remove();
                    fire(p);
                    continue;
                }
                if (e.isAlive()) { p.x = e.getX(); p.y = e.getY(); p.z = e.getZ(); }
                if (now - p.hitAt > WINDOW) it.remove();
            }
        }
    }

    private static void fire(Pending p) {
        float h = p.entity instanceof LivingEntity le ? le.getHeight() : 1.8f;
        List<Hook> copy;
        synchronized (HOOKS) { copy = new ArrayList<>(HOOKS); }
        for (Hook hook : copy) {
            try {
                hook.onKill(p.entity, p.x, p.y, p.z, h);
            } catch (Throwable t) {
                Diag.once("KillTracker", t);
            }
        }
    }
}
