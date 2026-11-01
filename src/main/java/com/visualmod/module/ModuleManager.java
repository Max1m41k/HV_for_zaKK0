package com.visualmod.module;

import com.visualmod.module.modules.combat.*;
import com.visualmod.module.modules.movement.*;
import com.visualmod.module.modules.render.*;
import com.visualmod.module.modules.render.victory.*;
import com.visualmod.module.modules.player.*;
import com.visualmod.module.modules.util.*;
import com.visualmod.module.modules.hud.*;
import com.visualmod.record.*;
import com.visualmod.module.modules.cosmetics.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        modules.add(new Sprint());
        modules.add(new Fullbright());
        modules.add(new ToggleSneak());
        modules.add(new AutoWalk());
        modules.add(new AutoJump());
        modules.add(new GhostTrail());
        modules.add(new MotionBlur());

        modules.add(new NoRender());
        modules.add(new Zoom());
        modules.add(new SwingAnimation());
        modules.add(new ItemPhysics());

        modules.add(new NoFog());
        modules.add(new SoftFade());
        modules.add(new AutoReduce());
        modules.add(new EventNotification());
        modules.add(new Prediction());
        modules.add(new HolyItems());
        modules.add(new Trails());
        modules.add(new CustomSword());
        modules.add(new CustomWings());
        modules.add(new CustomArmor());
        modules.add(new CosmeticReaction());
        modules.add(new BackSword());
        modules.add(new com.visualmod.module.modules.cosmetics.CustomTotem());
        modules.add(new com.visualmod.module.modules.cosmetics.CustomShield());
        modules.add(new NametagStyle());
        modules.add(new KillEffect());
        modules.add(new LowHealthVignette());

        modules.add(new CustomSound());
        modules.add(new NetworkOptimizer());
        modules.add(new FakePlayer());
        modules.add(new Friends());
        modules.add(new ComboMarker());
        modules.add(new Aurora());
        modules.add(new SnowFall());
        modules.add(new CompassBar());
        modules.add(new TargetRing());
        modules.add(new Fireflies());
        modules.add(new JumpCircles());
        modules.add(new DamageParticles());
        modules.add(new ImpactShards());
        modules.add(new AnimeFocus());
        modules.add(new PrivateHUD());
        modules.add(new FreeLook());
        modules.add(new BlockOverlay());
        modules.add(new HitColor());
        modules.add(new AutoShift());
        modules.add(new CustomSky());
        modules.add(new VictoryAnimationEngine());
        modules.add(new Killsound());
        modules.add(new Recorder());
        modules.add(new HighlightReel());

        modules.add(new HitSplash());
        modules.add(new SwordAura());
        modules.add(new CriticalEffect());
        modules.add(new VulnerabilityPulse());
        modules.add(new SoulHarvest());
        modules.add(new KillStreakBanner());
        modules.add(new GroundSlash());
        modules.add(new ShockwaveOnHit());
        modules.add(new ElytraCinema());
        modules.add(new PortalCinema());
        modules.add(new BladeElements());
        modules.add(new BladeMorph());
        modules.add(new ClashSparks());
        modules.add(new Finisher());
        modules.add(new CritHeat());
        modules.add(new MaceSlam());
        modules.add(new ReachGhost());
        modules.add(new SparksTrail());
        modules.add(new ChannelLink());
        modules.add(new AirCut());
        modules.add(new StrikeRune());
        modules.add(new CritStar());
        modules.add(new ExecuteMark());
        modules.add(new DuelBoard());
        modules.add(new DuelRing());

        modules.add(new StepEffect());
        modules.add(new Strafe());
        modules.add(new BunnyHop());

        modules.add(new AutoRespawn());
        modules.add(new RespawnAnimation());

        modules.add(new Cooldown());

        modules.add(new ViewModelChanger());
        modules.add(new CustomCrosshair());
        modules.add(new ChinaHat());
        modules.add(new CustomCursor());
        modules.add(new TntTimer());
        modules.add(new Optimizer());
        modules.add(new NameProtect());
        modules.add(new TntRadius());

        modules.add(new Keystrokes());

        modules.add(new CpsHUD());
        modules.add(new TotemAnimation());
        modules.add(new InterfaceHUD());
        modules.add(new BindsHUD());
        modules.add(new ArmorStatus());
        modules.add(new DurabilityWarn());
        modules.add(new SupplyWarn());
        modules.add(new PotionStatus());
        modules.add(new ScoreboardEdit());
        modules.add(new ChatAnimations());
        modules.add(new HotbarHUD());
        modules.add(new ClickGUI());
        modules.add(new BlurGUI());
        modules.add(new MiniMap());
        modules.add(new TargetHUD());
        modules.add(new MusicOverlay());
        modules.add(new TotemCounter());
        modules.add(new GUIAnimations());
        modules.add(new InventoryPreview());
        modules.add(new TabGUI());
        modules.add(new CustomHitbox());
        modules.add(new ItemScroller());
        modules.add(new AspectRatio());
        modules.add(new NoResource());
        modules.add(new GuiStyle());
        modules.add(new com.visualmod.module.modules.util.SafeMode());
        modules.add(new com.visualmod.module.modules.util.ColumnSettings());
        modules.add(new InventoryCheckModule());
        modules.add(new CustomCrossbow());
        modules.add(new ChatRules());
        modules.add(new Language());
        modules.add(new HWRules());

        modules.add(new Shaders());
        modules.add(new Atmosphere());
        modules.add(new GlassItem());
        modules.add(new CustomBossbar());
        modules.add(new MusicDrag());
        modules.add(new BreakProgressRing());
        modules.add(new DeathCard());
        modules.add(new ChatPlus());
        modules.add(new ChatTools());
        modules.add(new InventoryTags());
        modules.add(new QuickCompare());
        modules.add(new CosmeticBloom());
        modules.add(new Scene());
        modules.add(new TotemBurst());
        modules.add(new SwapFlash());
        modules.add(new HitConfirm());
        modules.add(new KillFrame());
        modules.add(new FaithfulTextures());
        modules.add(new DeathWaypoint());
        modules.add(new ServerProfiles());

        applyLicenseTier();
    }

    private static final String[] BETA_BLOCKED = {
            "CustomCrossbow", "KillEffect", "SwordAura", "GhostTrail",
            "StepEffect", "BunnyHop", "ComboMarker", "Fireflies", "TntRadius",
            "Shaders", "NetworkOptimizer", "FakePlayer",
            "InterfaceHUD", "MiniMap", "JumpCircles", "BlockOverlay", "VictoryAnimation",
            "TargetRing", "ToggleSneak", "Killsound", "Recorder"
    };

    private final java.util.Set<Module> betaHidden = new java.util.HashSet<>();

    public void applyLicenseTier() {
        boolean beta = com.visualmod.LockScreen.isBetaLicense();
        com.visualmod.util.RuntimePacks.beginBatch();
        try {
            for (String name : BETA_BLOCKED) {
                Module m = getModuleByName(name);
                if (m == null) continue;
                if (beta) {
                    if (!m.isHidden()) betaHidden.add(m);
                    m.setHidden(true);
                } else if (betaHidden.remove(m)) {
                    m.setHidden(false);
                }
            }
        } finally {
            com.visualmod.util.RuntimePacks.endBatch();
        }
    }

    private final java.util.Set<Module> liteHidden = new java.util.HashSet<>();

    /**
     * Список от сервера. {@code null} — списка ещё нет, это fail-closed: скрываем всё, что можно скрыть.
     * Пустой список — сервер ответил «ничего не запрещено».
     */
    public int applyLiteBlocklist(java.util.List<String> blocked) {
        com.visualmod.util.RuntimePacks.beginBatch();
        try {
            for (Module m : new java.util.ArrayList<>(liteHidden)) {
                if (!betaHidden.contains(m)) m.setHidden(false);
            }
            liteHidden.clear();
            if (blocked == null) {
                for (Module m : modules) {
                    if (m.isPermanent() || m.isHidden()) continue;
                    m.setHidden(true);
                    liteHidden.add(m);
                }
                return liteHidden.size();
            }
            for (String name : blocked) {
                Module m = getModuleByName(name);
                if (m == null || m.isPermanent()) continue;
                m.setHidden(true);
                liteHidden.add(m);
            }
            return liteHidden.size();
        } finally {
            com.visualmod.util.RuntimePacks.endBatch();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {        return (T) modules.stream().filter(m -> m.getClass() == clazz).findFirst().orElse(null);
    }

    public Module getModuleByName(String name) {
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled() && !m.isHidden()) m.onTick();
        }
    }

    public List<Module> getModules() { return modules; }

    public List<Module> getVisibleModules() {
        return modules.stream().filter(m -> !m.isHidden()).collect(Collectors.toList());
    }

    public List<Module> getByCategory(Module.Category c) {
        return modules.stream().filter(m -> m.getCategory() == c && !m.isHidden()).collect(Collectors.toList());
    }
}
