package com.visualmod.module.modules.player;

import com.mojang.authlib.GameProfile;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.module.modules.combat.HitColor;
import com.visualmod.module.modules.render.DamageParticles;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class FakePlayer extends Module implements Configurable {

    public static double hitDamage = 5;
    public static boolean autoRespawn = true;
    public static double respawnDelay = 3.5;
    public static boolean mySkin = false;

    private static OtherClientPlayerEntity fake;
    private static ClientWorld fakeWorld;
    private static int fakeId;
    private static long deadAt = 0;
    private static double sx, sy, sz;
    private static float syaw;
    private static final Random RNG = new Random();

    public FakePlayer() { super("FakePlayer", "Local dummy with 20 HP for testing effects", Category.PLAYER); }

    @Override
    public List<Setting> getSettings() {
        List<Setting> l = new ArrayList<>();
        l.add(new SliderSetting("Hit Damage", 1, 20, 1, () -> hitDamage, v -> hitDamage = v));
        l.add(new ModeSetting("Auto Respawn", new String[]{"OFF", "ON"}, () -> autoRespawn ? 1 : 0, i -> autoRespawn = (i == 1)));
        l.add(new SliderSetting("Respawn Delay", 2, 10, 0.5, () -> respawnDelay, v -> respawnDelay = v, "s"));
        l.add(new ModeSetting("Skin", new String[]{"DEFAULT", "MINE"}, () -> mySkin ? 1 : 0, i -> mySkin = (i == 1)));
        return l;
    }

    private static boolean want = false;
    private static long nextSpawnAt = 0;

    @Override public void onEnable() { if (!allowed()) return; want = true; nextSpawnAt = 0; spawn(true); }
    @Override public void onDisable() { want = false; remove(); }

    private boolean allowed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.isInSingleplayer()) return true;
        remove();
        want = false;
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.of("§d[HolyVisuals] §cFakePlayer работает только в одиночной игре."), false);
        }
        setEnabled(false);
        return false;    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!mc.isInSingleplayer()) { allowed(); return; }
        long now = System.currentTimeMillis();
        if (fake != null && fakeWorld != mc.world) { fake = null; fakeWorld = null; }
        if (fake == null) {
            if (want && now >= nextSpawnAt) spawn(sy == 0);
            return;
        }
        if (fake.isDead()) {
            if (deadAt == 0) deadAt = now;
            if (now - deadAt > respawnDelay * 1000) {
                remove();
                if (autoRespawn) nextSpawnAt = now; else want = false;
            }
        } else if (fake.getHealth() < 20f && fake.hurtTime == 0) {
            fake.setHealth(Math.min(20f, fake.getHealth() + 0.05f));
        }
    }

    private static void spawn(boolean atPlayer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return;
        remove();
        deadAt = 0;
        if (atPlayer || sy == 0) {
            Vec3d look = Vec3d.fromPolar(0, mc.player.getYaw());
            sx = mc.player.getX() + look.x * 3;
            sz = mc.player.getZ() + look.z * 3;
            sy = mc.player.getY();
            syaw = mc.player.getYaw() + 180f;
        }
        GameProfile gp = new GameProfile(UUID.randomUUID(), "Dummy");
        if (mySkin) { try { gp.properties().putAll(mc.player.getGameProfile().properties()); } catch (Throwable ignored) { } }

        fake = new OtherClientPlayerEntity(w, gp);
        fake.refreshPositionAndAngles(sx, sy, sz, syaw, 0f);
        fake.setHealth(20f);
        fake.setNoGravity(true);
        fake.setVelocity(Vec3d.ZERO);
        fake.setBodyYaw(syaw);
        fake.setHeadYaw(syaw);

        fake.noClip = true;
        fake.setInvisible(false);

        fakeId = -(900000 + RNG.nextInt(90000));
        fake.setId(fakeId);
        w.addEntity(fake);
        fakeWorld = w;
    }

    private static void remove() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (fake != null && mc.world != null && fakeWorld == mc.world) {
            try { mc.world.removeEntity(fakeId, Entity.RemovalReason.DISCARDED); } catch (Throwable ignored) { }
        }
        fake = null; fakeWorld = null; deadAt = 0;
    }

    public static boolean isFake(Entity e) { return fake != null && e == fake; }

    public static boolean onAttack(Entity target) {
        if (!isFake(target)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || fake.isDead()) return true;
        mc.player.swingHand(Hand.MAIN_HAND);
        float hp = fake.getHealth() - (float) hitDamage;
        fake.setHealth(Math.max(0f, hp));
        fake.hurtTime = 10; fake.maxHurtTime = 10;
        try {
            HitColor.onHit(fake);
            if (VisualsMod.moduleManager != null) DamageParticles.onHit(VisualsMod.moduleManager.getModule(DamageParticles.class), fake);
        } catch (Throwable ignored) { }
        if (mc.getSoundManager() != null) mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_PLAYER_HURT, 1.0f));
        if (fake.getHealth() <= 0f) {
            com.visualmod.module.modules.render.victory.VictoryAnimationEngine.onKill(fake);
            com.visualmod.module.modules.render.Killsound.onKill(fake);
        }
        return true;
    }
}
