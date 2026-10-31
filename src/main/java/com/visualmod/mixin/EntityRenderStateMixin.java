package com.visualmod.mixin;

import com.visualmod.util.IEntityRenderStateHolder;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IEntityRenderStateHolder {
    private Entity visuals$renderEntity;

    @Override
    public Entity visuals$getRenderEntity() { return visuals$renderEntity; }

    @Override
    public void visuals$setRenderEntity(Entity entity) { this.visuals$renderEntity = entity; }
}
