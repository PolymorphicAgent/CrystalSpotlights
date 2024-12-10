package me.polymorphicagent.crystalspotlights;

import de.tr7zw.changeme.nbtapi.NBT;
import me.polymorphicagent.crystalspotlights.utils.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class CrystalSpotlights extends JavaPlugin implements Listener {

    /// This is the spotlight crystal Item definition
    public static ItemStack spotlightCrystal;

    /// This is the test stick Item definition
    public static ItemStack testStick;

    /// Stores the last player who used a spotlight crystal item
    /// Used to identify which end crystals that spawn are spotlight crystals
    public Player lastSpotlightPlacer = null;

    /// Keeps track of all tasks (i.e. particle, redstone) associated with any crystals
    public final MultiMap<EnderCrystal, BukkitRunnable> crystalTasks = new MultiMap<>();

    /// Keeps track of what open guis are associated with a spotlight crystal
    /// For identification and reference gaining purposes
    public final Map<CrystalSettingsHolder, EnderCrystal> crystalGui = new HashMap<>();

    /// Keeps track of all spotlight crystals that are placed down
    public final ArrayList<EnderCrystal> crystals = new ArrayList<>();

    /// This method gets called when the plugin is enabled
    @Override
    public void onEnable() {

        //initialize the NBT API
        this.getLogger().log(Level.INFO, "Initializing NBT-API (for custom player head data)...");
        if (!NBT.preloadApi()) {
            getLogger().warning("NBT-API wasn't initialized properly, disabling the plugin");
            Bukkit.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        //check if config.yml doesn't exist, and if not, create it
        saveDefaultConfig();

        //register the event logic defined in the EventListeners class
        Bukkit.getServer().getPluginManager().registerEvents(new EventListeners(this), this);

        //give the Utils class a reference to the plugin
        Utils.plugin = this;

        //initialize the custom item for spotlight crystals
        spotlightCrystal = new ItemStack(Material.END_CRYSTAL, 1);

        //initialize a custom test item for debugging purposes
        testStick = new ItemStack(Material.STICK, 1);

        //change name for aesthetics and add a tag for identification
        ItemMeta cMeta = spotlightCrystal.getItemMeta();
        cMeta.displayName(Component.text("§6Spotlight Crystal"));
        cMeta.getPersistentDataContainer().set(new NamespacedKey(this, "crystal"), PersistentDataType.STRING, "spotlight");
        cMeta.lore(Arrays.asList(
                Component.text("§9§lThis is no regular end crystal!"),
                Component.text("§aPlace it to find out!"),
                Component.text("§c§lPlugin created by Polymorphic Agent")
        ));
        spotlightCrystal.setItemMeta(cMeta);

        //change name + add enchantment for aesthetics and add a tag for identification
        ItemMeta stickMeta = testStick.getItemMeta();
        stickMeta.displayName(Component.text("§9§l§nTest Stick"));
        testStick.setItemMeta(stickMeta);
        testStick.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 1);
        NBT.modify(testStick, nbt -> {
            nbt.setString("stick", "test");
        });

        //set the command used to give the test stick
        Objects.requireNonNull(getCommand("teststick")).setExecutor(new CommandManager(this, testStick));

        //create the custom crafting recipe for spotlight crystals
        NamespacedKey key = new NamespacedKey(this, "spotlight_crystal");
        ShapedRecipe recipe = new ShapedRecipe(key, spotlightCrystal);

        //set the shape + ingredients of the recipe + register it
        recipe.shape("GGG", "GEG", "RHR");
        recipe.setIngredient('G', Material.GLASS);
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('E', Material.ENDER_EYE);
        recipe.setIngredient('H', Material.GHAST_TEAR);
        getServer().addRecipe(recipe);

        //load all of our crystals from config into 'crystals' ArrayList
        Utils.loadCrystals();

        //start all the tasks defined in each crystal's persistent data
        this.getLogger().log(Level.INFO, "Igniting crystals...");
        for(EnderCrystal crystal : crystals){
            Utils.updateCrystalState(crystal);
        }

        //because intellij is stupid imma just randomly call this method here :)
        Utils.calculatePercentage(2, 1, 10);
    }

    /// This method gets called when the plugin is disabled
    @Override
    public void onDisable() {
        //save the positions of all spotlight crystals to the config file
        Utils.saveCrystals();

        //stop all tasks associated with all crystals (i.e. particle, redstone)
        for(BukkitRunnable b : crystalTasks.entrySet()){
            b.cancel();
        }
    }

}