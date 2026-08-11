package com.seamlessportals.client.render;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class PortalCamera extends Camera {
    public void setPortalCamera(Vec3d pos, float yaw, float pitch) {
        this.setPos(pos.x, pos.y, pos.z);
        this.setRotation(yaw, pitch);
    }

    public Matrix4f calculateProjectionMatrix(float fov, float aspectRatio, float nearPlane, float farPlane) {
        return new Matrix4f().setPerspective((float) Math.toRadians(fov), aspectRatio, nearPlane, farPlane);
    }

    public void setRotation(float yaw, float pitch) {
        // In 26.2, we use JOML for rotations
        super.setRotation(yaw, pitch);
    }
}
