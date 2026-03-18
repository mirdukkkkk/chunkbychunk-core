package uk.mirdukkkkk.chunkbychunk.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import uk.mirdukkkkk.chunkbychunk.common.util.ChangeDimensionHelper;

@Mixin(Entity.class)
public abstract class EntityChangeDimensionMixin {

    @ModifyVariable(method = "changeDimension", at = @At("HEAD"), argsOnly = true)
    private DimensionTransition substituteTransition(DimensionTransition transition) {
        DimensionTransition customInfo = ChangeDimensionHelper.getPortalInfo();

        if (customInfo != null) {
            return customInfo;
        }

        return transition;
    }
}