package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;

public class NBTTagList extends NBTBase {

    public final List<NBTBase> values = new ArrayList<NBTBase>();

    public void appendTag(NBTBase value) {
        values.add(value);
    }
}
