package com.visualmod.module.modules.hud;

import com.visualmod.module.Module;
import com.visualmod.module.settings.Configurable;
import com.visualmod.module.settings.ModeSetting;
import com.visualmod.module.settings.Setting;
import com.visualmod.module.settings.SliderSetting;
import com.visualmod.util.HudStyle;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class MiniMap extends Module implements Configurable {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private static final int SIZE = 140, RADIUS = 62;
    private static final long REFRESH_MS = 3000;

    private int[] cachedColors = new int[SIZE * SIZE];
    private long lastScan = 0;
    private int cachedPx, cachedPz;

    public static boolean showMobs = true;
    public static boolean showNamesOnTab = true;
    public static float renderScale = 1.0f;

    public MiniMap() {
        super("MiniMap", "Mini map showing area around player", Category.HUD);
    }

    @Override
    public List<Setting> getSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new ModeSetting("Show Mobs", new String[]{"OFF", "ON"}, () -> showMobs ? 1 : 0, i -> showMobs = (i == 1)));
        list.add(new ModeSetting("Names on TAB", new String[]{"OFF", "ON"}, () -> showNamesOnTab ? 1 : 0, i -> showNamesOnTab = (i == 1)));
        list.add(new SliderSetting("Scale", 0.5f, 2.0f, 0.1f, () -> renderScale, v -> renderScale = (float) v, "x"));
        return list;
    }

    public void render(DrawContext ctx, int x, int y) {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        World w = mc.world;
        int px = (int) mc.player.getX(), py = (int) mc.player.getY(), pz = (int) mc.player.getZ();

        int scaledSize = (int) (SIZE * renderScale);
        HudStyle.drawPanelRounded(ctx, x, y, scaledSize, scaledSize, 1f, HudStyle.ACCENT, HudStyle.FillStyle.GLASS, 10f);

        long now = System.currentTimeMillis();
        if (now - lastScan > REFRESH_MS) {
            rescan(w, px, py, pz);
            lastScan = now;
        }

        float sc = (float) scaledSize / (RADIUS * 2 * renderScale);
        int ps = Math.max(1, (int) sc);
        
        int dxCenter = px - cachedPx, dzCenter = pz - cachedPz;
        
        for (int bx2 = -RADIUS; bx2 < RADIUS; bx2++) {
            for (int bz = -RADIUS; bz < RADIUS; bz++) {
                int cacheX = bx2 + dxCenter + RADIUS;
                int cacheZ = bz + dzCenter + RADIUS;
                if (cacheX < 0 || cacheX >= RADIUS * 2 || cacheZ < 0 || cacheZ >= RADIUS * 2) continue;
                int color = cachedColors[cacheX * RADIUS * 2 + cacheZ];
                if (color == 0) continue;
                int sx = x + (int) ((bx2 + RADIUS) * sc * renderScale), sy = y + (int) ((bz + RADIUS) * sc * renderScale);
                ctx.fill(sx, sy, sx + ps, sy + ps, color);
            }
        }

        boolean tabPressed = false;

        for (PlayerEntity p : w.getPlayers()) {
            if (p == mc.player) continue;
            if (p.isInvisible()) continue;
            double dx = p.getX() - px, dz = p.getZ() - pz;
            if (Math.abs(dx) > RADIUS || Math.abs(dz) > RADIUS) continue;
            int sx = x + (int) ((dx + RADIUS) * sc * renderScale), sy = y + (int) ((dz + RADIUS) * sc * renderScale);
            
            ctx.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFFFF4444);
            
            if (tabPressed && showNamesOnTab) {
                String name = p.getName().getString();
                ctx.drawTextWithShadow(mc.textRenderer, name, sx + 4, sy - 10, 0xFFFFFFFF);
                
                ctx.fill(sx + 4, sy + 4, sx + 12, sy + 12, 0xFFDDAA88);
                ctx.fill(sx + 6, sy + 6, sx + 7, sy + 7, 0xFF222222);
                ctx.fill(sx + 9, sy + 6, sx + 10, sy + 7, 0xFF222222);
            }
        }

        int cx = x + scaledSize / 2, cy = y + scaledSize / 2;
        float yaw = mc.player.getYaw(), rad = (float) Math.toRadians(yaw);
        int ax = cx + (int) (Math.sin(rad) * 8 * renderScale), az = cy - (int) (Math.cos(rad) * 8 * renderScale);
        ctx.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);
        ctx.fill(ax, az, ax + 2, az + 2, HudStyle.ACCENT);

        BlockPos bp = mc.player.getBlockPos();
        ctx.drawCenteredTextWithShadow(mc.textRenderer, bp.getX() + ", " + bp.getY() + ", " + bp.getZ(),
            x + scaledSize / 2, y + scaledSize + 3, 0xFFAAAAAA);
    }

    private int getMobColor(Entity entity) {
        if (entity.getType() == EntityType.ZOMBIE) return 0xFF555555;
        if (entity.getType() == EntityType.SKELETON) return 0xFFAAAAAA;
        if (entity.getType() == EntityType.CREEPER) return 0xFF00AA00;
        if (entity.getType() == EntityType.SPIDER) return 0xFF330066;
        if (entity.getType() == EntityType.ENDERMAN) return 0xFF330066;
        if (entity.getType() == EntityType.PIGLIN) return 0xFFAA6600;
        if (entity.getType() == EntityType.VILLAGER) return 0xFF884422;
        return 0xFF884444;
    }

    private void rescan(World w, int px, int py, int pz) {
        cachedPx = px;
        cachedPz = pz;
        int worldTop = w.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, px, pz);

        for (int bx2 = -RADIUS; bx2 < RADIUS; bx2++) {
            for (int bz = -RADIUS; bz < RADIUS; bz++) {
                int wx = px + bx2, wz = pz + bz;
                BlockState st = Blocks.AIR.getDefaultState();
                int startY = Math.min(py + 16, worldTop - 1);
                for (int by2 = startY; by2 >= py - 16; by2--) {
                    BlockState s = w.getBlockState(new BlockPos(wx, by2, wz));
                    if (!s.isAir() && s.isOpaque()) {
                        st = s;
                        break;
                    }
                }
                int idx = (bx2 + RADIUS) * RADIUS * 2 + (bz + RADIUS);
                cachedColors[idx] = st.isAir() ? 0 : getColor(st);
            }
        }
    }

    private int getColor(BlockState s) {
        var b = s.getBlock();
        if (b == Blocks.GRASS_BLOCK || b == Blocks.SHORT_GRASS) return 0xFF4A7C3F;
        if (b == Blocks.DIRT || b == Blocks.COARSE_DIRT) return 0xFF7A5C3A;
        if (b == Blocks.STONE || b == Blocks.COBBLESTONE) return 0xFF888888;
        if (b == Blocks.SAND || b == Blocks.SANDSTONE) return 0xFFD4C97A;
        if (b == Blocks.WATER) return 0xFF2255BB;
        if (b == Blocks.LAVA) return 0xFFCC4400;
        if (b == Blocks.SNOW || b == Blocks.SNOW_BLOCK) return 0xFFDDEEFF;
        if (b == Blocks.OAK_LEAVES || b == Blocks.BIRCH_LEAVES || b == Blocks.SPRUCE_LEAVES) return 0xFF336622;
        if (b == Blocks.OAK_LOG || b == Blocks.BIRCH_LOG || b == Blocks.SPRUCE_LOG) return 0xFF5D3A1A;
        if (b == Blocks.GRAVEL) return 0xFF777766;
        if (b == Blocks.BEDROCK) return 0xFF333333;
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return 0xFF00CCCC;
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE) return 0xFFDDAA00;
        if (b == Blocks.NETHERRACK) return 0xFF882222;
        return 0xFF666666;
    }

    public void render(DrawContext ctx) {
        render(ctx, 10, 10);
    }
}
