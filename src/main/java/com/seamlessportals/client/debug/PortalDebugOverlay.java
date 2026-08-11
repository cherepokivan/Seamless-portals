package com.seamlessportals.client.debug;

import com.seamlessportals.client.SeamlessPortalsClient;
import com.seamlessportals.client.portal.PortalData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class PortalDebugOverlay {
    public static void render(DrawContext context, MinecraftClient client) {
        if (!SeamlessPortalsClient.getConfig().enabled) return;
        
        List<PortalData> portals = SeamlessPortalsClient.getPortalManager().getVisiblePortals();
        if (portals == null) return;

        int y = 10;
        context.drawTextWithShadow(client.textRenderer, "§6Seamless Portals Debug", 10, y, 0xFFFFFF);
        y += 10;
        
        context.drawTextWithShadow(client.textRenderer, "Current Dim: " + client.world.getRegistryKey().getValue(), 10, y, 0xFFFFFF);
        y += 10;

        int count = 1;
        for (PortalData portal : portals) {
            context.drawTextWithShadow(client.textRenderer, "§bPortal #" + count, 10, y, 0xFFFFFF);
            y += 10;
            context.drawTextWithShadow(client.textRenderer, "  Dest Dim: " + portal.destination.dimension.getValue(), 10, y, 0xFFFFFF);
            y += 10;
            context.drawTextWithShadow(client.textRenderer, "  Pos: " + portal.pos.toShortString(), 10, y, 0xFFFFFF);
            y += 10;
            context.drawTextWithShadow(client.textRenderer, "  Axis: " + portal.axis, 10, y, 0xFFFFFF);
            y += 10;
            
            Vec3d center = portal.geometry.getCenter();
            double dist = client.gameRenderer.getCamera().getPos().distanceTo(center);
            context.drawTextWithShadow(client.textRenderer, String.format("  Distance: %.2f", dist), 10, y, 0xFFFFFF);
            y += 10;
            
            count++;
        }
    }
}
