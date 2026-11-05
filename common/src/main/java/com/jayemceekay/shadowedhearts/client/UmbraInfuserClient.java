package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.client.render.block.UmbraInfuserRenderer;
import com.jayemceekay.shadowedhearts.client.gui.UmbraInfuserScreen;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModMenus;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.renderer.RenderType;

public final class UmbraInfuserClient {
    private UmbraInfuserClient() {}

    /** Fabric client initialization runs after registry population. NeoForge uses RegisterMenuScreensEvent. */
    public static void registerScreen() {
        MenuRegistry.registerScreenFactory(ModMenus.UMBRA_INFUSER.get(), UmbraInfuserScreen::new);
    }

    /** Called after registry population on each loader, never in the NeoForge mod constructor. */
    public static void registerRenderer() {
        // The machine frame and the restoration tank expansion have window cutouts.
        RenderTypeRegistry.register(RenderType.cutout(), ModBlocks.UMBRA_INFUSER.get());
        RenderTypeRegistry.register(RenderType.cutout(), ModBlocks.UMBRA_INFUSER_TANK.get());
        BlockEntityRendererRegistry.register(ModBlockEntities.UMBRA_INFUSER_BE.get(), UmbraInfuserRenderer::new);
    }
}
