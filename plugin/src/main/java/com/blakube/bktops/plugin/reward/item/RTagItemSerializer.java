package com.blakube.bktops.plugin.reward.item;

import com.saicone.rtag.item.ItemTagStream;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RTagItemSerializer {

    @NotNull
    public String serialize(@NotNull ItemStack item) {
        return ItemTagStream.INSTANCE.listToBase64(List.of(item));
    }

    @NotNull
    public ItemStack deserialize(@NotNull String itemData) {
        Object[] rawItems = ItemTagStream.INSTANCE.fromBase64(itemData);
        if (rawItems.length == 0 || !(rawItems[0] instanceof ItemStack itemStack)) {
            throw new IllegalArgumentException("Serialized reward is not an ItemStack");
        }
        return itemStack;
    }
}
