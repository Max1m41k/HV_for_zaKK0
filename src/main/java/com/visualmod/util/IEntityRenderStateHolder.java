package com.visualmod.util;

import net.minecraft.entity.Entity;

/** Даёт доступ к сущности из рендер-стейта, чтобы нарисовать ник/ХП/пинг над головой. */
public interface IEntityRenderStateHolder {
    Entity visuals$getRenderEntity();
    void visuals$setRenderEntity(Entity entity);
}
