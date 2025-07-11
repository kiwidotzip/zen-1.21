package meowing.zen.mixins;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Fixes console spam - from https://github.com/Synnerz/devonian/blob/main/src/main/java/com/github/synnerz/devonian/mixin/YggdrasilMinecraftSessionServiceMixin.java
@Mixin(YggdrasilMinecraftSessionService.class)
public class MixinYggdrasil {
    @Redirect(method = "getPropertySignatureState", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/properties/Property;hasSignature()Z"), remap = false)
    private boolean devonian$hasSignature(Property instance) {
        boolean hasSig = instance.hasSignature();
        try {
            if (hasSig && instance.signature().isEmpty()) return false;
        } catch (Throwable e) {
            System.err.println("[Zen] Error in MixinYggdrasil: " + e);
        }

        return hasSig;
    }
}