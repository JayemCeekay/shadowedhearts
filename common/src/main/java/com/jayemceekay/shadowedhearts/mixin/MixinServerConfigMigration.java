package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.config.ConfigMigrationBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/** Migrates only the world being opened, before either loader reads its server configs. */
@Mixin(MinecraftServer.class)
public abstract class MixinServerConfigMigration {
    @Shadow
    public abstract Path getWorldPath(LevelResource resource);

    @Inject(method = "runServer", at = @At("HEAD"))
    private void shadowedhearts$migrateWorldConfigs(CallbackInfo ci) {
        ConfigMigrationBootstrap.beforeServerStart(getWorldPath(LevelResource.ROOT).resolve("serverconfig"));
    }
}
