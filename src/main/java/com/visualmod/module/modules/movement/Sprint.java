package com.visualmod.module.modules.movement;
import com.visualmod.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
public class Sprint extends Module {
    public Sprint(){super("Sprint","Always sprint",Category.MOVEMENT);}
    @Override public void onTick(){MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null)return;
        ClientPlayerEntity p=mc.player;
        // Версия 1: просто «всегда бежать». Добавлен только сброс флага при посадке:
        // спринт пассажира — из-за него сервер стопорил лодку и выбрасывал игрока.
        if(p.getVehicle()!=null){ if(p.isSprinting()) p.setSprinting(false); return; }
        // Не спорим с ванилью: присед и щит сами гасят спринт, а перестановка флага
        // каждый тик даёт спам пакетов, за который сервер стопорит уже на ногах.
        if(p.isSneaking()||p.isBlocking()) return;
        if(!p.isSprinting()&&p.forwardSpeed>0)p.setSprinting(true);}
}
