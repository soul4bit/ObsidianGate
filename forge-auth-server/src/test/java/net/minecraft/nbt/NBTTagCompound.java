package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

public class NBTTagCompound extends NBTBase {

    public final Map<String, Object> values = new HashMap<String, Object>();

    public void setString(String key, String value) {
        values.put(key, value);
    }

    public void setTag(String key, NBTBase value) {
        values.put(key, value);
    }
}
