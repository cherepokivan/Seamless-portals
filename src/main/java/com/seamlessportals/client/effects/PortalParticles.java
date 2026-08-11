package com.seamlessportals.client.effects;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

public class PortalParticles {
    public void spawn(World world, BlockPos pos, float density) {
        if (world.random.nextFloat() < density) {
            world.addParticle(ParticleTypes.PORTAL, 
                pos.getX() + world.random.nextDouble(), 
                pos.getY() + world.random.nextDouble(), 
                pos.getZ() + world.random.nextDouble(), 
                0, 0, 0);
        }
    }
}
