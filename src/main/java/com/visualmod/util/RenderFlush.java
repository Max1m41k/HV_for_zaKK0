package com.visualmod.util;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Сброс только своего слоя. Голый {@code Immediate.draw()} сбрасывает вообще все
 * буферы, включая слои Sodium/Iris, — отсюда «пропавшая» геометрия и смешивание
 * не в том порядке. Здесь всегда указан конкретный слой.
 */
public final class RenderFlush {

    private RenderFlush() {}

    public static void flush(VertexConsumerProvider vcp, RenderLayer layer) {
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }
}
