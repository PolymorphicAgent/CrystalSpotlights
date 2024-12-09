package me.polymorphicagent.crystalspotlights.utils;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import me.polymorphicagent.crystalspotlights.CrystalSpotlights;
import me.polymorphicagent.crystalspotlights.TaskRunner;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class Utils {

    public static CrystalSpotlights plugin;

    /// Formats a string dye name properly
    public static @NotNull String formatDyeString(@NotNull String input) {

        //remove the "_DYE" part
        String wordPart = input.replace("_DYE", "");

        //split the words by underscore
        String[] words = wordPart.split("_");

        //convert to lowercase and then capitalize the first letter
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
                result.append(" ");
            }
        }

        //append color codes for formatting
        result = switch (result.toString().trim()) {
            case "Gray" -> new StringBuilder("§8§l§n" + result);
            case "Black" -> new StringBuilder("§0§l§n" + result);
            case "Brown" -> new StringBuilder("§4§l§n" + result);
            case "Red" -> new StringBuilder("§c§l§n" + result);
            case "Orange" -> new StringBuilder("§6§l§n" + result);
            case "Yellow" -> new StringBuilder("§e§l§n" + result);
            case "Lime" -> new StringBuilder("§a§l§n" + result);
            case "Green" -> new StringBuilder("§2§l§n" + result);
            case "Cyan" -> new StringBuilder("§3§l§n" + result);
            case "Light Blue" -> new StringBuilder("§b§l§n" + result);
            case "Blue" -> new StringBuilder("§9§l§n" + result);
            case "Purple", "Magenta" -> new StringBuilder("§5§l§n" + result);
            case "Pink" -> new StringBuilder("§d§l§n" + result);
            default -> new StringBuilder("§f§l§n" + result);
        };

        //trim any trailing spaces
        return result.toString().trim();
    }

    /// Resets all items to their defaults in the custom inventory
    public static void resetToDefaults(@NotNull InventoryClickEvent event, EnderCrystal crystal) {

        //get a reference to the inventory
        Inventory gui = event.getInventory();

        //completely clear the inventory
        gui.clear();

        //add the items back

        //white beam color
        ItemStack whiteColor = new ItemStack(Material.WHITE_DYE);
        ItemMeta whiteMeta = whiteColor.getItemMeta();
        whiteMeta.displayName(Component.text("§fWhite Beam"));
        whiteColor.setItemMeta(whiteMeta);

        //gray beam color
        ItemStack grayColor = new ItemStack(Material.GRAY_DYE);
        ItemMeta grayMeta = grayColor.getItemMeta();
        grayMeta.displayName(Component.text("§8Gray Beam"));
        grayColor.setItemMeta(grayMeta);

        //black beam color
        ItemStack blackColor = new ItemStack(Material.BLACK_DYE);
        ItemMeta blackMeta = blackColor.getItemMeta();
        blackMeta.displayName(Component.text("§0Black Beam"));
        blackColor.setItemMeta(blackMeta);

        //brown beam color
        ItemStack brownColor = new ItemStack(Material.BROWN_DYE);
        ItemMeta brownMeta = brownColor.getItemMeta();
        brownMeta.displayName(Component.text("§4Brown Beam"));
        brownColor.setItemMeta(brownMeta);

        //red beam color
        ItemStack redColor = new ItemStack(Material.RED_DYE);
        ItemMeta redMeta = redColor.getItemMeta();
        redMeta.displayName(Component.text("§cRed Beam"));
        redColor.setItemMeta(redMeta);

        //orange beam color
        ItemStack orangeColor = new ItemStack(Material.ORANGE_DYE);
        ItemMeta orangeMeta = orangeColor.getItemMeta();
        orangeMeta.displayName(Component.text("§6Orange Beam"));
        orangeColor.setItemMeta(orangeMeta);

        //yellow beam color
        ItemStack yellowColor = new ItemStack(Material.YELLOW_DYE);
        ItemMeta yellowMeta = yellowColor.getItemMeta();
        yellowMeta.displayName(Component.text("§eYellow Beam"));
        yellowColor.setItemMeta(yellowMeta);

        //lime beam color
        ItemStack limeColor = new ItemStack(Material.LIME_DYE);
        ItemMeta limeMeta = limeColor.getItemMeta();
        limeMeta.displayName(Component.text("§aLime Beam"));
        limeColor.setItemMeta(limeMeta);

        //green beam color
        ItemStack greenColor = new ItemStack(Material.GREEN_DYE);
        ItemMeta greenMeta = greenColor.getItemMeta();
        greenMeta.displayName(Component.text("§2Green Beam"));
        greenColor.setItemMeta(greenMeta);

        //cyan beam color
        ItemStack cyanColor = new ItemStack(Material.CYAN_DYE);
        ItemMeta cyanMeta = cyanColor.getItemMeta();
        cyanMeta.displayName(Component.text("§3Cyan Beam"));
        cyanColor.setItemMeta(cyanMeta);

        //light blue beam color
        ItemStack lightBlueColor = new ItemStack(Material.LIGHT_BLUE_DYE);
        ItemMeta lightBlueMeta = lightBlueColor.getItemMeta();
        lightBlueMeta.displayName(Component.text("§bLight Blue Beam"));
        lightBlueColor.setItemMeta(lightBlueMeta);

        //blue beam color
        ItemStack blueColor = new ItemStack(Material.BLUE_DYE);
        ItemMeta blueMeta = blueColor.getItemMeta();
        blueMeta.displayName(Component.text("§9Blue Beam"));
        blueColor.setItemMeta(blueMeta);

        //purple beam color
        ItemStack purpleColor = new ItemStack(Material.PURPLE_DYE);
        ItemMeta purpleMeta = purpleColor.getItemMeta();
        purpleMeta.displayName(Component.text("§5Purple Beam"));
        purpleColor.setItemMeta(purpleMeta);

        //magenta beam color
        ItemStack magentaColor = new ItemStack(Material.MAGENTA_DYE);
        ItemMeta magentaMeta = magentaColor.getItemMeta();
        magentaMeta.displayName(Component.text("§5Magenta Beam"));
        magentaColor.setItemMeta(magentaMeta);

        //pink beam color
        ItemStack pinkColor = new ItemStack(Material.PINK_DYE);
        ItemMeta pinkMeta = pinkColor.getItemMeta();
        pinkMeta.displayName(Component.text("§dPink Beam"));
        pinkColor.setItemMeta(pinkMeta);

        //add the items to the gui
        gui.addItem(whiteColor);
        gui.addItem(grayColor);
        gui.addItem(blackColor);
        gui.addItem(brownColor);
        gui.addItem(redColor);
        gui.addItem(orangeColor);
        gui.addItem(yellowColor);
        gui.addItem(limeColor);
        gui.addItem(greenColor);
        gui.addItem(cyanColor);
        gui.addItem(lightBlueColor);
        gui.addItem(blueColor);
        gui.addItem(purpleColor);
        gui.addItem(magentaColor);
        gui.addItem(pinkColor);

        //default beam color (uses the default end crystal beam)
        ItemStack defaultColor = new ItemStack(Material.BARRIER);
        ItemMeta defaultMeta = defaultColor.getItemMeta();
        defaultMeta.displayName(Component.text("Default Beam"));
        defaultColor.setItemMeta(defaultMeta);

        //add the item to the gui
        gui.addItem(defaultColor);

        //get the selected sweep mode from the crystal's persistent data container
        String selectedSweepMode = getCrystalPersistentDataString(crystal, "sweep");

        //get the selected angle from the crystal's persistent data container
        int[] angle = getCrystalPersistentDataIntArray(crystal, "angle");

        //create items for sweep MODE selection
        ItemStack staticArrow;
        ItemStack dynamicCompass;

        //add arrows for altering angle/sweep bounds, named appropriately

        //define the textures
        String dwnTexture   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=";
        String upTexture    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNjYmY5ODgzZGQzNTlmZGYyMzg1YzkwYTQ1OWQ3Mzc3NjUzODJlYzQxMTdiMDQ4OTVhYzRkYzRiNjBmYyJ9fX0=";
        String rightTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjljYjRkZDIxMjU5YzBkNzVhYTMxNWZmMzg5YzNjZWY3NTJiZTM5NDkzMzgxNjRiYWM4NGE5NmUifX19";
        String leftTexture  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyYTdkNzU1YzYzMzg4ZmYwMTc1MmQ1ZjQ0MTlmYzY0NSJ9fX0=";

        //create the player head items
        ItemStack dwnArrow = getCustomHead(dwnTexture);
        ItemStack upArrow = getCustomHead(upTexture);
        ItemStack rightArrow = getCustomHead(rightTexture);
        ItemStack leftArrow = getCustomHead(leftTexture);

        //get the item metas
        ItemMeta dwnMeta = dwnArrow.getItemMeta();
        ItemMeta upMeta = upArrow.getItemMeta();
        ItemMeta rightMeta = rightArrow.getItemMeta();
        ItemMeta leftMeta = leftArrow.getItemMeta();

        //check if the crystal is set to static mode
        if (Objects.requireNonNull(selectedSweepMode).equals("Static")) {

            //create and name items appropriately
            staticArrow = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta staticArrowMeta = staticArrow.getItemMeta();
            staticArrowMeta.displayName(Component.text("§c§l§nStatic Beam (Doesn't Move)§2 (SELECTED)"));
            staticArrow.setItemMeta(staticArrowMeta);

            dynamicCompass = new ItemStack(Material.COMPASS);
            ItemMeta dynamicCompassMeta = dynamicCompass.getItemMeta();
            dynamicCompassMeta.displayName(Component.text("§aDynamic Beam (Moves)"));
            dynamicCompass.setItemMeta(dynamicCompassMeta);

            dwnMeta.displayName(Component.text("§c§lLower Beam"));
            upMeta.displayName(Component.text("§a§lRaise Beam"));
            rightMeta.displayName(Component.text("§a§lRotate Beam Clockwise"));
            leftMeta.displayName(Component.text("§c§lRotate Beam Counterclockwise"));

            //determine color of angles (lime for positive, red for 0)
            int vertAngle = Objects.requireNonNull(angle)[0];
            String vertColor;
            if(vertAngle > 0)
                vertColor = "§a";
            else vertColor = "§c";

            int horAngle = angle[1];
            String horColor;
            if(horAngle > 0)
                horColor = "§a";
            else
                horColor = "§c";

            //set the item lore with the correct colors
            dwnMeta.lore(Arrays.asList(
                    Component.text("§7Increases angle between beam and y-axis"),
                    Component.text("§9Increments by 30 degrees per click"),
                    Component.text("§6Is currently set to: "+vertColor+"§l"+vertAngle)
            ));
            upMeta.lore(Arrays.asList(
                    Component.text("§7Decreases angle between beam and y-axis"),
                    Component.text("§9Increments by 30 degrees per click"),
                    Component.text("§6Is currently set to: "+vertColor+"§l"+vertAngle)
            ));
            rightMeta.lore(Arrays.asList(
                    Component.text("§7Increases angle between beam and x-axis"),
                    Component.text("§9Increments by 30 degrees per click"),
                    Component.text("§6Is currently set to: "+horColor+"§l"+horAngle)
            ));
            leftMeta.lore(Arrays.asList(
                    Component.text("§7Decreases angle between beam and x-axis"),
                    Component.text("§9Increments by 30 degrees per click"),
                    Component.text("§6Is currently set to: "+horColor+"§l"+horAngle)
            ));

            //actually apply the item metas
            dwnArrow.setItemMeta(dwnMeta);
            upArrow.setItemMeta(upMeta);
            rightArrow.setItemMeta(rightMeta);
            leftArrow.setItemMeta(leftMeta);

            //add the arrows to the gui
            gui.setItem(21, dwnArrow);
            gui.setItem(22, upArrow);
            gui.setItem(23, rightArrow);
            gui.setItem(24, leftArrow);
        }

        //crystal is in dynamic mode
        else {
            staticArrow = new ItemStack(Material.ARROW);
            ItemMeta staticArrowMeta = staticArrow.getItemMeta();
            staticArrowMeta.displayName(Component.text("§cStatic Beam (Doesn't Move)"));
            staticArrow.setItemMeta(staticArrowMeta);

            dynamicCompass = new ItemStack(Material.CLOCK);
            ItemMeta dynamicCompassMeta = dynamicCompass.getItemMeta();
            dynamicCompassMeta.displayName(Component.text("§a§l§nDynamic Beam (Moves)§2 (SELECTED)"));
            dynamicCompass.setItemMeta(dynamicCompassMeta);

            //add arrows for altering angle bounds and speed
            dwnMeta.displayName(Component.text("§a§lWiden Beam Travel"));
            upMeta.displayName(Component.text("§c§lShorten Beam Travel"));
            rightMeta.displayName(Component.text("§a§lRotate Travel Clockwise"));
            leftMeta.displayName(Component.text("§c§lRotate Travel Counterclockwise"));

            //get the sweep bound angles from the crystal's persistent data
            int[] bounds = getCrystalPersistentDataIntArray(crystal, "bounds");
            int t = Objects.requireNonNull(bounds)[0];
            int r = bounds[1];

            //update the item lore according to the above angles
            dwnMeta.lore(Arrays.asList(
                    Component.text("§7Increases angle between beam travel bounds"),
                    Component.text("§9Increments by 30 degrees per click"),
                    Component.text("§6Is currently set to: §a§l"+t)
            ));
            upMeta.lore(Arrays.asList(
                    Component.text("§7Decreases angle between beam travel bounds"),
                    Component.text("§9Decrements by 30 degrees per click"),
                    Component.text("§6Is currently set to: §a§l"+t)
            ));
            rightMeta.lore(Arrays.asList(
                    Component.text("§7Rotates beam sweep bounds clockwise"),
                    Component.text("§9Increments by 30 degrees per click"),
                    Component.text("§6Is currently set to: §a§l"+r)
            ));
            leftMeta.lore(Arrays.asList(
                    Component.text("§7Rotates beam sweep bounds Counterclockwise"),
                    Component.text("§9Decrements by 30 degrees per click"),
                    Component.text("§6Is currently set to: §a§l"+r)
            ));

            //actually apply the item metas
            dwnArrow.setItemMeta(dwnMeta);
            upArrow.setItemMeta(upMeta);
            rightArrow.setItemMeta(rightMeta);
            leftArrow.setItemMeta(leftMeta);

            //add the arrows to the gui
            gui.setItem(21, dwnArrow);
            gui.setItem(22, upArrow);
            gui.setItem(23, rightArrow);
            gui.setItem(24, leftArrow);

            //next, add arrows for changing the sweeping speed

            //define red arrow textures
            String dwnRedTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTM4NTJiZjYxNmYzMWVkNjdjMzdkZTRiMGJhYTJjNWY4ZDhmY2E4MmU3MmRiY2FmY2JhNjY5NTZhODFjNCJ9fX0=";
            String upRedTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmQ5Mjg3NjE2MzQzZDgzM2U5ZTczMTcxNTljYWEyY2IzZTU5NzQ1MTEzOTYyYzEzNzkwNTJjZTQ3ODg4NGZhIn19fQ==";

            //create the items
            ItemStack dwnRedArrow = getCustomHead(dwnRedTexture);
            ItemStack upRedArrow = getCustomHead(upRedTexture);

            //get the item metas
            ItemMeta dwnRedMeta = dwnRedArrow.getItemMeta();
            ItemMeta upRedMeta = upRedArrow.getItemMeta();

            //set the names
            dwnRedMeta.displayName(Component.text("§c§l§nDecrease Sweeping Speed"));
            upRedMeta.displayName(Component.text("§a§l§nIncrease Sweeping Speed"));

            //get the current speed for the crystal from its persistent data
            double speed = getCrystalPersistentDataDouble(crystal, "speed");

            //set the item lore with calculated speed percentages
            dwnRedMeta.lore(Arrays.asList(
                    Component.text("§7Decreases the frequency passed to sin()"),
                    Component.text("§9Halves frequency per click"),
                    Component.text("§6Is currently set to: §a§l"+calculatePercentage(speed, 0.8, 0.025))
            ));
            upRedMeta.lore(Arrays.asList(
                    Component.text("§7Increases the frequency passed to sin()"),
                    Component.text("§9Doubles frequency per click"),
                    Component.text("§6Is currently set to: §a§l"+calculatePercentage(speed, 0.8, 0.025))
            ));

            //actually set the item metas
            dwnRedArrow.setItemMeta(dwnRedMeta);
            upRedArrow.setItemMeta(upRedMeta);

            //add the arrows to the gui
            gui.setItem(25, dwnRedArrow);
            gui.setItem(26, upRedArrow);

        }

        //add the arrow and compass to the gui
        gui.setItem(18, staticArrow);
        gui.setItem(19, dynamicCompass);

        //create an item for resetting the crystal's settings to defaults
        ItemStack reset = new ItemStack(Material.REDSTONE_TORCH);

        //add the reset item to slot 17 of the gui
        gui.setItem(17, reset);

        //get the item meta
        ItemMeta resetMeta = reset.getItemMeta();

        //set the name of the item
        resetMeta.displayName(Component.text("§eReset All Values to Default"));

        //set the item meta
        Objects.requireNonNull(gui.getItem(17)).setItemMeta(resetMeta);

        //set the item lore
        Objects.requireNonNull(gui.getItem(17)).lore(Arrays.asList(
                Component.text("§cWarning!"),
                Component.text("§9Cannot be undone!"),
                Component.text("§6Use with caution.")
        ));
    }

    /// Gets the persistent data of the specified crystal as a String
    public static @Nullable String getCrystalPersistentDataString(@NotNull EnderCrystal crystal, String key) {
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

        //get the PersistentDataContainer of the crystal
        PersistentDataContainer dataContainer = crystal.getPersistentDataContainer();

        //check if the PersistentDataContainer contains the specified key and return the value if it exists
        if (dataContainer.has(namespacedKey, PersistentDataType.STRING))
            return dataContainer.get(namespacedKey, PersistentDataType.STRING);
        else
            //return null if the key does not exist
            return null;
    }
    /// Gets the persistent data of the specified crystal as an int Array
    public static int @Nullable [] getCrystalPersistentDataIntArray(@NotNull EnderCrystal crystal, String key) {
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

        //get the PersistentDataContainer of the EnderCrystal
        PersistentDataContainer dataContainer = crystal.getPersistentDataContainer();

        //check if the PersistentDataContainer contains the specified key and return the value if it exists
        if (dataContainer.has(namespacedKey, PersistentDataType.INTEGER_ARRAY))
            return dataContainer.get(namespacedKey, PersistentDataType.INTEGER_ARRAY);
        else
            //return null if the key does not exist
            return null;
    }
    /// Gets the persistent data of the specified crystal as a double
    public static double getCrystalPersistentDataDouble(@NotNull EnderCrystal crystal, String key){
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

        //get the PersistentDataContainer of the EnderCrystal
        PersistentDataContainer dataContainer = crystal.getPersistentDataContainer();

        //check if the PersistentDataContainer contains the specified key and return the value if it exists
        if (dataContainer.has(namespacedKey, PersistentDataType.DOUBLE))
            return Objects.requireNonNull(dataContainer.get(namespacedKey, PersistentDataType.DOUBLE));
        else {
            plugin.getLogger().log(Level.SEVERE, "§l§cNONEXISTENT PERSISTENT DATA KEY! (gCPDD)");
            return 0;
        }
    }
    /// Gets the persistent data of the specified crystal as a boolean
    public static boolean getCrystalPersistentDataBoolean(@NotNull EnderCrystal crystal, String key){
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

        //get the PersistentDataContainer of the EnderCrystal
        PersistentDataContainer dataContainer = crystal.getPersistentDataContainer();

        //check if the PersistentDataContainer contains the specified key and return the value if it exists
        if (dataContainer.has(namespacedKey, PersistentDataType.BOOLEAN))
            return Objects.requireNonNull(dataContainer.get(namespacedKey, PersistentDataType.BOOLEAN));
        else {
            plugin.getLogger().log(Level.SEVERE, "§l§cNONEXISTENT PERSISTENT DATA KEY! (gCPDB)");
            return false;
        }
    }

    /// Sets the persistent data of the specified crystal as a String
    public static void setCrystalPersistentDataString(@NotNull EnderCrystal crystal, String key, String value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(plugin, key),
                PersistentDataType.STRING,
                value
        );
    }
    /// Sets the persistent data of the specified crystal as an int Array
    public static void setCrystalPersistentDataIntArray(@NotNull EnderCrystal crystal, String key, int @NotNull [] value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(plugin, key),
                PersistentDataType.INTEGER_ARRAY,
                new int[]{value[0], value[1]}
        );
    }
    /// Sets the persistent data of the specified crystal as a double
    public static void setCrystalPersistentDataDouble(@NotNull EnderCrystal crystal, String key, double value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(plugin, key),
                PersistentDataType.DOUBLE,
                value
        );
    }
    /// Sets the persistent data of the specified crystal as a boolean
    public static void setCrystalPersistentDataBoolean(@NotNull EnderCrystal crystal, String key, boolean value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(plugin, key),
                PersistentDataType.BOOLEAN,
                value
        );
    }

    /// Creates a custom player head with the given texture
    public static @NotNull ItemStack getCustomHead(String texture) {

        //create the player head item
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);

        //for Minecraft 1.20.4 and below
        NBT.modify(head, nbt -> {

            //create nbt owner compound
            ReadWriteNBT skullOwnerCompound = nbt.getOrCreateCompound("SkullOwner");

            /// The owner UUID. Note that skulls with the same UUID but different textures will misbehave and only one texture will load.
            /// They will share the texture. To avoid this limitation, it is recommended to use a random UUID.
            skullOwnerCompound.setUUID("Id", UUID.randomUUID());

            //apply the nbt compound
            skullOwnerCompound.getOrCreateCompound("Properties")
                    .getCompoundList("textures")
                    .addCompound()
                    .setString("Value", texture);
        });

        //return the finished item
        return head;
    }

    /// Updates the item lore of arrow items for a static beam
    public static void updateStaticArrowItems(EnderCrystal crystal, @NotNull Inventory gui){

        //rename the arrows
        ItemMeta t1 = Objects.requireNonNull(gui.getItem(21)).getItemMeta();
        t1.displayName(Component.text("§c§lLower Beam"));
        Objects.requireNonNull(gui.getItem(21)).setItemMeta(t1);
        ItemMeta t2 = Objects.requireNonNull(gui.getItem(22)).getItemMeta();
        t2.displayName(Component.text("§a§lRaise Beam"));
        Objects.requireNonNull(gui.getItem(22)).setItemMeta(t2);
        ItemMeta t3 = Objects.requireNonNull(gui.getItem(23)).getItemMeta();
        t3.displayName(Component.text("§a§lRotate Beam Counterclockwise"));
        Objects.requireNonNull(gui.getItem(23)).setItemMeta(t3);
        ItemMeta t4 = Objects.requireNonNull(gui.getItem(24)).getItemMeta();
        t4.displayName(Component.text("§c§lRotate Beam Clockwise"));
        Objects.requireNonNull(gui.getItem(24)).setItemMeta(t4);

        //get the current angle from the crystal's persistent data container
        int[] angle = getCrystalPersistentDataIntArray(crystal, "angle");

        //determine color of angles (lime for positive, red for 0)
        int vertAngle = Objects.requireNonNull(angle)[0];
        String vertColor;
        if(vertAngle > 0)
            vertColor = "§a";
        else vertColor = "§c";

        int horAngle = angle[1];
        String horColor;
        if(horAngle > 0)
            horColor = "§a";
        else
            horColor = "§c";

        //update down arrow (21) lore
        Objects.requireNonNull(gui.getItem(21)).lore(Arrays.asList(
                Component.text("§7Increases angle between beam and y-axis"),
                Component.text("§9Increments by 30 degrees per click"),
                Component.text("§6Is currently set to: "+vertColor+"§l"+vertAngle)
        ));

        //update up arrow (22) lore
        Objects.requireNonNull(gui.getItem(22)).lore(Arrays.asList(
                Component.text("§7Decreases angle between beam and y-axis"),
                Component.text("§9Decrements by 30 degrees per click"),
                Component.text("§6Is currently set to: "+vertColor+"§l"+vertAngle)
        ));

        //update right arrow (23) lore
        Objects.requireNonNull(gui.getItem(23)).lore(Arrays.asList(
                Component.text("§7Increases angle between beam and x-axis"),
                Component.text("§9Increments by 30 degrees per click"),
                Component.text("§6Is currently set to: "+horColor+"§l"+horAngle)
        ));

        //update left arrow (24) lore
        Objects.requireNonNull(gui.getItem(24)).lore(Arrays.asList(
                Component.text("§7Decreases angle between beam and x-axis"),
                Component.text("§9Decrements by 30 degrees per click"),
                Component.text("§6Is currently set to: "+horColor+"§l"+horAngle)
        ));
    }

    /// Updates the item lore of arrow items for a dynamic beam
    public static void updateDynamicArrowItems(EnderCrystal crystal, @NotNull Inventory gui){
        //update black arrow names
        ItemMeta t1 = Objects.requireNonNull(gui.getItem(21)).getItemMeta();
        t1.displayName(Component.text("§a§lWiden Beam Travel"));
        Objects.requireNonNull(gui.getItem(21)).setItemMeta(t1);
        ItemMeta t2 = Objects.requireNonNull(gui.getItem(22)).getItemMeta();
        t2.displayName(Component.text("§c§lShorten Beam Travel"));
        Objects.requireNonNull(gui.getItem(22)).setItemMeta(t2);
        ItemMeta t3 = Objects.requireNonNull(gui.getItem(23)).getItemMeta();
        t3.displayName(Component.text("§a§lRotate Travel Clockwise"));
        Objects.requireNonNull(gui.getItem(23)).setItemMeta(t3);
        ItemMeta t4 = Objects.requireNonNull(gui.getItem(24)).getItemMeta();
        t4.displayName(Component.text("§c§lRotate Travel Counterclockwise"));
        Objects.requireNonNull(gui.getItem(24)).setItemMeta(t4);

        //get the bound angles from the crystal's persistent data container
        int[] bounds = getCrystalPersistentDataIntArray(crystal, "bounds");
        int t = Objects.requireNonNull(bounds)[0];
        int r = bounds[1];

        //update the item lore
        Objects.requireNonNull(gui.getItem(21)).lore(Arrays.asList(
                Component.text("§7Increases angle between beam travel bounds"),
                Component.text("§9Increments by 30 degrees per click"),
                Component.text("§6Is currently set to: §a§l"+t)
        ));
        Objects.requireNonNull(gui.getItem(22)).lore(Arrays.asList(
                Component.text("§7Decreases angle between beam travel bounds"),
                Component.text("§9Decrements by 30 degrees per click"),
                Component.text("§6Is currently set to: §a§l"+t)
        ));
        Objects.requireNonNull(gui.getItem(23)).lore(Arrays.asList(
                Component.text("§7Rotates beam sweep bounds clockwise"),
                Component.text("§9Increments by 30 degrees per click"),
                Component.text("§6Is currently set to: §a§l"+r)
        ));
        Objects.requireNonNull(gui.getItem(24)).lore(Arrays.asList(
                Component.text("§7Rotates beam sweep bounds Counterclockwise"),
                Component.text("§9Decrements by 30 degrees per click"),
                Component.text("§6Is currently set to: §a§l"+r)
        ));

        //update red arrow names
        ItemMeta t5 = Objects.requireNonNull(gui.getItem(25)).getItemMeta();
        t5.displayName(Component.text("§c§l§nDecrease Sweeping Speed"));
        Objects.requireNonNull(gui.getItem(25)).setItemMeta(t5);
        ItemMeta t6 = Objects.requireNonNull(gui.getItem(26)).getItemMeta();
        t6.displayName(Component.text("§a§l§nIncrease Sweeping Speed"));
        Objects.requireNonNull(gui.getItem(26)).setItemMeta(t6);

        //get the current speed for the crystal from the crystal's persistent data container
        double speed = getCrystalPersistentDataDouble(crystal, "speed");

        //set the lore accordingly
        Objects.requireNonNull(gui.getItem(25)).lore(Arrays.asList(
                Component.text("§7Decreases the frequency passed to sin()"),
                Component.text("§9Halves frequency per click"),
                Component.text("§6Is currently set to: §a§l"+calculatePercentage(speed, 0.8, 0.025))
        ));
        Objects.requireNonNull(gui.getItem(26)).lore(Arrays.asList(
                Component.text("§7Increases the frequency passed to sin()"),
                Component.text("§9Doubles frequency per click"),
                Component.text("§6Is currently set to: §a§l"+calculatePercentage(speed, 0.8, 0.025))
        ));
    }

    /// Updates a crystal's beam based on the persistent data (static, dynamic, color)
    public static void updateCrystalState(EnderCrystal crystal){

        //if a task (or tasks) already exists for this crystal, shut it/them down
        for(BukkitRunnable r : plugin.crystalTasks.get(crystal))
            r.cancel();

        //remove the crystal from the global task list since it no longer has tasks
        plugin.crystalTasks.remove(crystal);

        //start the beam task - the function will take care of mode, color, etc.
        TaskRunner.startCrystalTask(crystal);

        //start the redstone task - checks constantly for redstone changes
        TaskRunner.startRedstoneTask(crystal);
    }

    /// Spawns particles between two points
    public static void spawnBeamParticles(@NotNull Location start, Location end, Color color) {

        //calculate the distance using an expensive method :grimace:
        double distance = start.distance(end);

        //calculate the particle count
        int particleCount = (int) (distance * 3);

        //spawn the particles
        for (int i = 0; i < particleCount; i++) {
            double ratio = (double) i / particleCount;
            double x = start.getX() + (end.getX() - start.getX()) * ratio;
            double y = start.getY() + (end.getY() - start.getY()) * ratio;
            double z = start.getZ() + (end.getZ() - start.getZ()) * ratio;

            Location particleLoc = new Location(start.getWorld(), x, y, z);

            //check if the block at the particle location is solid
            if (particleLoc.getBlock().getType().isSolid())
                //stop the beam if it encounters a solid block
                break;

            //actually spawn the particles
            start.getWorld().spawnParticle(
                    Particle.REDSTONE,
                    particleLoc,
                    10,//particle count at each spawn point
                    0.3, 0.3, 0.3,//offset for beam width
                    new Particle.DustOptions(color, 1.5f)//size for beam effect
            );
        }
    }

    /// Converts a color name to its corresponding RGB Color
    public static Color getColorFromName(@NotNull String colorName) {
        return switch (colorName.toUpperCase()) {
            case "GRAY" -> Color.GRAY;
            case "BLACK" -> Color.BLACK;
            case "BROWN" -> Color.fromRGB(150, 75, 0);
            case "RED" -> Color.RED;
            case "ORANGE" -> Color.ORANGE;
            case "YELLOW" -> Color.fromRGB(255, 222, 89);
            case "LIME" -> Color.LIME;
            case "GREEN" -> Color.GREEN;
            case "CYAN" -> Color.fromRGB(0, 255, 255);
            case "LIGHT BLUE" -> Color.fromRGB(125,245, 245);
            case "BLUE" -> Color.BLUE;
            case "MAGENTA" -> Color.FUCHSIA;
            case "PURPLE" -> Color.PURPLE;
            case "PINK" -> Color.fromRGB(255, 115, 170);
            default -> Color.fromRGB(255, 255, 255);
        };
    }

    /// Checks if the block below the crystal is powered directly or indirectly
    public static boolean isPowered(@NotNull EnderCrystal crystal) {
        return isBlockPowered(crystal.getLocation().subtract(0, 1, 0));
    }

    /// Checks if the block at the specified location is powered, either directly or indirectly
    public static boolean isBlockPowered(Location location) {

        //check if the location is valid
        if (location == null) return false;

        //get the block at the location
        Block block = location.getBlock();

        //return if the block is powered directly, indirectly, or both
        return (block.isBlockPowered() || block.isBlockIndirectlyPowered()) || (block.isBlockPowered() && block.isBlockIndirectlyPowered());
    }

    /// Saves all crystal positions to config (to allow them to persist across restarts)
    public static void saveCrystals(){

        //clear the section for crystals in config to start fresh
        plugin.getConfig().set("crystals", null);

        //save each crystal's location
        for (int i = 0; i < plugin.crystals.size(); i++) {
            EnderCrystal crystal = plugin.crystals.get(i);
            Location loc = crystal.getLocation();
            String path = "crystals." + i;

            //save the location components
            plugin.getConfig().set(path + ".world", loc.getWorld().getName());
            plugin.getConfig().set(path + ".x", loc.getX());
            plugin.getConfig().set(path + ".y", loc.getY());
            plugin.getConfig().set(path + ".z", loc.getZ());
        }

        //write to the config file
        plugin.saveConfig();
    }

    /// Loads crystal positions from config (to allow them to persist across restarts)
    public static void loadCrystals(){

        //just in case anything is loaded, dump that into the config file before processing
        plugin.saveDefaultConfig();

        //check if the config file has crystals saved
        if (plugin.getConfig().contains("crystals")) {

            //be overly polite...
            plugin.getLogger().log(Level.INFO, "Crystal config present!");

            //iterate through and load each saved crystal's data
            for (String key : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("crystals")).getKeys(false)) {
                String path = "crystals." + key;

                //grab the saved coordinates
                World world = plugin.getServer().getWorld(Objects.requireNonNull(plugin.getConfig().getString(path + ".world")));
                double x = plugin.getConfig().getDouble(path + ".x");
                double y = plugin.getConfig().getDouble(path + ".y");
                double z = plugin.getConfig().getDouble(path + ".z");

                //be overly polite again...
                plugin.getLogger().log(Level.INFO, "Loading crystal "+key+" at "+x+", "+y+", "+z);

                //make sure the world is loaded
                if (world != null) {
                    Location loc = new Location(world, x, y, z);

                    //force load chunks at the location to make the crystal accessible
                    if (!loc.getWorld().isChunkLoaded(loc.getChunk()))
                        loc.getWorld().loadChunk(loc.getChunk());

                    //iterate over entities at the save coordinates
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.5, 1, .5)) {

                        //check if it's a spotlight crystal
                        if (entity instanceof EnderCrystal crystal &&
                                getCrystalPersistentDataString(crystal, "crystal") != null &&
                                Objects.equals(getCrystalPersistentDataString(crystal, "crystal"), "Spotlight"))
                        {
                            //add the crystal to the global list
                            plugin.crystals.add(crystal);
                        }
                    }

                    //unload chunks so they don't stay loaded perpetually
                    if (loc.getWorld().isChunkLoaded(loc.getChunk()))
                        loc.getWorld().unloadChunk(loc.getChunk());
                }
            }

            //should I stop being this polite?!?!?
            plugin.getLogger().log(Level.INFO, "Loaded all crystals!");
        }
    }

    /// Flips the given angle 180 degrees while keeping it positive and not 360
    public static int flipAngle(int a){

        //dummy if you can't understand this simple logic I'm sorry for you honestly
        if(a == 180)
            return 0;
        else if(a > 180)
            return a - 180;
        else return a + 180;
    }

    /// Calculates the percentage n lies between min and max
    public static @NotNull String calculatePercentage(double n, double max, double min) {

        //so basically if the caller is stupid
        if (max == min) {
            plugin.getLogger().log(Level.SEVERE, "MINMAX EQUALITY ERROR: "+max+", "+n+", RETURNING 0.00%");
            return "0.00%";
        }

        //calculate the percentage
        double percentage = ((n - min) / (max - min)) * 100;

        //clamp the percentage between 0 and 100
        percentage = Math.max(0, Math.min(100, percentage));

        //format and return the result
        return String.format("%.2f%%", percentage);
    }

    /// Resets a spotlight crystal's persistent data to defaults
    /// @param crystal The crystal to reset, passed to updateCrystalState()
    public static void resetCrystal(@NotNull EnderCrystal crystal, @NotNull Inventory gui) {

        //assume the crystal is a spotlight crystal (and that the caller isn't an idiot)

        //set the default color and sweep mode
        setCrystalPersistentDataString(crystal, "color", "None");
        setCrystalPersistentDataString(crystal, "sweep", "Static");
        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{0, 0});
        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{90, 0});
        setCrystalPersistentDataDouble(crystal, "speed", 0.0);
        setCrystalPersistentDataBoolean(crystal, "state", isPowered(crystal));

        //actually update the crystal based on the above persistent data
        updateCrystalState(crystal);

        //close the gui to refresh items
        gui.close();
    }

}
