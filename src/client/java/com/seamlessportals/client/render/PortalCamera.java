package com.seamlessportals.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Virtual camera state used to calculate a portal view without mutating the
 * game's primary camera.
 */
public final class PortalCamera {
    private Vec3 position = Vec3.ZERO;
    private float yaw;
    private float pitch;

    public void setPortalCamera(Vec3 position, float yaw, float pitch) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Vec3 getPosition() {
        return position;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Matrix4f calculateProjectionMatrix(float fov, float aspectRatio, float nearPlane, float farPlane) {
        return new Matrix4f().setPerspective((float) Math.toRadians(fov), aspectRatio, nearPlane, farPlane);
    }
}
