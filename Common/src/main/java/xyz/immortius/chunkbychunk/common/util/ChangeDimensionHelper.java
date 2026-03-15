package xyz.immortius.chunkbychunk.common.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public final class ChangeDimensionHelper {
    private static DimensionTransition portalInfo;

    private ChangeDimensionHelper() {}

    public static DimensionTransition getPortalInfo() {
        return portalInfo;
    }

    public static Entity changeDimension(Entity entity, ServerLevel level, Vec3 pos, Vec3 speed, float yRot, float xRot) {
        portalInfo = new DimensionTransition(level, pos, speed, yRot, xRot, DimensionTransition.DO_NOTHING);
        try {
            return entity.changeDimension(portalInfo);
        } finally {
            portalInfo = null;
        }
    }
}