package net.minecraft.item;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTBase;

public class ItemStack {

    public final Item item;
    public final int count;
    public final Map<String, NBTBase> tags = new HashMap<String, NBTBase>();

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    public void setTagInfo(String key, NBTBase tag) {
        tags.put(key, tag);
    }
}
