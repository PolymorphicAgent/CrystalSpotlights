package me.polymorphicagent.crystalspotlights;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class CrystalSettingsHolder implements InventoryHolder {
    private Inventory inventory;

    public CrystalSettingsHolder(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }
}
