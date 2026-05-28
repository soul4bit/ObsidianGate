package net.minecraft.entity.player;

import java.util.UUID;

public class EntityPlayerMP {
    public final UUID id;
    public final String name;
    public final int dimension;
    public final double posX;
    public final double posY;
    public final double posZ;
    public Object inventory;
    private final boolean operator;

    public EntityPlayerMP(UUID id, String name, int dimension, double x, double y, double z) {
        this(id, name, dimension, x, y, z, false);
    }

    public EntityPlayerMP(UUID id, String name, int dimension, double x, double y, double z, boolean operator) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.operator = operator;
    }

    public UUID getUniqueID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean canUseCommand(int level, String command) {
        return operator;
    }
}
