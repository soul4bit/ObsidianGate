package net.minecraft.item;

public class Item {

    public final String id;

    private Item(String id) {
        this.id = id;
    }

    public static Item getByNameOrId(String id) {
        return id == null || id.trim().isEmpty() ? null : new Item(id);
    }
}
