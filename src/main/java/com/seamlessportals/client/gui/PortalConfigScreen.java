package com.seamlessportals.client.gui;

import com.seamlessportals.client.SeamlessPortalsClient;
import com.seamlessportals.client.config.PortalConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class PortalConfigScreen extends Screen {
    private final Screen parent;

    public PortalConfigScreen(Screen parent) {
        super(Text.literal("Seamless Portals Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height / 4;
        
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Enabled: " + SeamlessPortalsClient.getConfig().enabled),
            button -> {
                SeamlessPortalsClient.getConfig().enabled = !SeamlessPortalsClient.getConfig().enabled;
                button.setMessage(Text.literal("Enabled: " + SeamlessPortalsClient.getConfig().enabled));
            }
        ).dimensions(width / 2 - 100, y, 200, 20).build());

        y += 25;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Preview Mode: " + SeamlessPortalsClient.getConfig().previewMode),
            button -> {
                PortalConfig.PreviewMode current = SeamlessPortalsClient.getConfig().previewMode;
                PortalConfig.PreviewMode next = PortalConfig.PreviewMode.values()[(current.ordinal() + 1) % PortalConfig.PreviewMode.values().length];
                SeamlessPortalsClient.getConfig().previewMode = next;
                button.setMessage(Text.literal("Preview Mode: " + next));
            }
        ).dimensions(width / 2 - 100, y, 200, 20).build());

        y += 50;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Done"),
            button -> this.client.setScreen(parent)
        ).dimensions(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
