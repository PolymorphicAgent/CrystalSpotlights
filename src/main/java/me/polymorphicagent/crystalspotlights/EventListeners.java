package me.polymorphicagent.crystalspotlights;

import de.tr7zw.changeme.nbtapi.NBT;
import me.polymorphicagent.crystalspotlights.utils.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventListeners implements Listener {

    private final CrystalSpotlights plugin;

    public EventListeners(CrystalSpotlights plugin){
        this.plugin = plugin;
    }

    /// Save where all crystals in the world are alongside each world save
    @EventHandler
    public void onAutoSave(WorldSaveEvent event){
        //write crystal coordinates to config file
        Utils.saveCrystals();
    }

    /// Logic for checking if a spotlight crystal was placed
    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {

        //gain references to the player and action tied to this event
        Player player = event.getPlayer();
        Action action = event.getAction();

        //check if the player right-clicks a block or in the air w/ a spotlight crystal
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {

            //get the item used in the event
            ItemStack itemInHand = event.getItem();

            //check if the item is valid
            if (itemInHand != null && itemInHand.hasItemMeta()) {

                //check if the item in question is a spotlight crystal
                String s = itemInHand.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "crystal"), PersistentDataType.STRING);
                if (s != null && s.equals("spotlight")) {

                    //if so, store the player who interacted with the custom item
                    plugin.lastSpotlightPlacer = player;
                }
            }
        }

        //check if the player right-clicks in the air w/ a test stick
        if(action == Action.RIGHT_CLICK_AIR &&
                event.getItem() != null &&
                event.getItem().getType() == Material.STICK)
        {
            //verify that the item is a test stick (check the nbt tag)
            AtomicBoolean isTestStick = new AtomicBoolean(false);
            NBT.get(Objects.requireNonNull(event.getItem()), nbt ->{
                if(nbt.getString("stick").equals("test"))
                    isTestStick.set(true);
            });

            //if it is a test stick, send a message to the player
            if(isTestStick.get())
                player.sendMessage(Component.text("§9DEBUGGER STICK: RIGHT CLICK ON A SPOTLIGHT CRYSTAL"));
        }
    }

    /// Logic for identifying which crystals that spawn are spotlight crystals, and setting the default persistent data
    @EventHandler
    public void onEntitySpawn(@NotNull EntitySpawnEvent event) {

        //check if the entity spawned is an end crystal
        if (event.getEntity() instanceof EnderCrystal crystal) {

            //check if the last player interacted using the spotlight crystal item
            if (plugin.lastSpotlightPlacer != null) {

                //customize the appearance
                crystal.setShowingBottom(false);

                //set the default color and sweep mode + a tag that identifies it as a spotlight crystal
                Utils.setCrystalPersistentDataString(crystal, "crystal", "Spotlight");
                Utils.setCrystalPersistentDataString(crystal, "color", "None");
                Utils.setCrystalPersistentDataString(crystal, "sweep", "Static");
                Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{0, 0});


                //TODO: IN FUTURE UPDATE
                // bounds defines 4 angles from the + y-axis, 2 in the +/- x directions,
                // and two in the +/- z directions, respectively, to be used as bounds for where
                // the beam will sweep to. Endpoints will eventually be calculated based off these
                // angles, and those points will be interpolated between during the sweep animation.
                // Each angle can exist on the interval [0, 90].
                // setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{0, 0, 0, 0});

                //NOTE: bounds and speed are used for the dynamic beam ONLY!!
                //for now, bounds will only define 2 angles, one on [0, 180], and one on [0, 360].
                //The first angle is in between the two imaginary rays that are the bounds for the
                //sweep animation, which is centered on the vertical. Essentially, each bound is HALF
                //the angle away from the vertical. The second angle is the rotation in the xz plane.
                Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{90, 0});

                //eventually gets passed on as the FREQUENCY (b value) of the sin/cos function
                Utils.setCrystalPersistentDataDouble(crystal, "speed", 0.0);

                //set the beam state based on the redstone state of the block below the crystal
                Utils.setCrystalPersistentDataBoolean(crystal, "state", Utils.isPowered(crystal));

                //actually update the crystal based on the above persistent data
                Utils.updateCrystalState(crystal);

                //add the crystal to our global arraylist for persistence across restarts
                plugin.crystals.add(crystal);

                //reset lastPlacer after the crystal is spawned
                plugin.lastSpotlightPlacer = null;
            }
        }
    }

    /// Detects when a spotlight crystal explodes, stops/deletes all associated tasks
    @EventHandler
    public void onCrystalExplode(@NotNull EntityExplodeEvent event){

        //check if the entity is a spotlight crystal
        if(event.getEntity() instanceof EnderCrystal crystal &&
                Utils.getCrystalPersistentDataString(crystal, "crystal") != null &&
                Objects.equals(Utils.getCrystalPersistentDataString(crystal, "crystal"), "Spotlight")){

            //if a task (or tasks) already exists for this crystal, shut it/them down and remove it/them
            for(BukkitRunnable r : plugin.crystalTasks.get(crystal))
                r.cancel();

            //remove the crystal from our task list since it doesn't have tasks anymore
            plugin.crystalTasks.remove(crystal);

            //remove the crystal from our global list because it doesn't exist anymore
            plugin.crystals.remove(crystal);
        }
    }

    /// Detects when a spotlight crystal explodes, stops/deletes all associated tasks (this deals with if another explosion causes this crystal to explode)
    @EventHandler
    public void onCrystalDamaged(@NotNull EntityDamageEvent event) {

        //check if the entity is a spotlight crystal
        if(event.getEntity() instanceof EnderCrystal crystal &&
                Utils.getCrystalPersistentDataString(crystal, "crystal") != null &&
                Objects.equals(Utils.getCrystalPersistentDataString(crystal, "crystal"), "Spotlight")){

            //if a task (or tasks) already exists for this crystal, shut it/them down and remove it/them
            for(BukkitRunnable r : plugin.crystalTasks.get(crystal))
                r.cancel();

            //remove the crystal from our task list since it doesn't have tasks anymore
            plugin.crystalTasks.remove(crystal);

            //remove the crystal from our global list because it doesn't exist anymore
            plugin.crystals.remove(crystal);
        }
    }

    /// Handles player interaction with the EnderCrystal entity (opens gui)
    @EventHandler
    public void onPlayerInteractAtEntity(@NotNull PlayerInteractAtEntityEvent event) {

        //gain a reference to the player, entity, and item involved in this event
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();

        //check if the entity is a spotlight crystal
        if (entity instanceof EnderCrystal crystal &&
                Utils.getCrystalPersistentDataString(crystal, "crystal") != null &&
                Objects.equals(Utils.getCrystalPersistentDataString(crystal, "crystal"), "Spotlight"))
        {

            //check if a test stick was used
            if(item.getAmount() != 0){

                //check the item nbt
                AtomicBoolean isTestStick = new AtomicBoolean(false);
                NBT.get(Objects.requireNonNull(item), nbt -> {
                    if (nbt.getString("stick").equals("test"))
                        isTestStick.set(true);
                });

                //if it is in fact a test stick
                if (isTestStick.get()) {

                    //send a butt ton of useful debug information to the player (of course, it is nicely formatted)
                    player.sendMessage(Component.text("§9§l---------------------------------"));
                    player.sendMessage(Component.text("§c§l              Persistent Data               "));
                    player.sendMessage(Component.text(" §1§acrystal: §r§6" + Utils.getCrystalPersistentDataString(crystal, "crystal")));
                    player.sendMessage(Component.text(" §1§acolor: §r§6" + Utils.getCrystalPersistentDataString(crystal, "color")));
                    player.sendMessage(Component.text(" §1§asweep: §r§6" + Utils.getCrystalPersistentDataString(crystal, "sweep")));
                    player.sendMessage(Component.text(" §1§aangle: §r§6" + Arrays.toString(Utils.getCrystalPersistentDataIntArray(crystal, "angle"))));
                    player.sendMessage(Component.text(" §1§abounds: §r§6" + Arrays.toString(Utils.getCrystalPersistentDataIntArray(crystal, "bounds"))));
                    player.sendMessage(Component.text(" §1§aspeed: §r§6" + Utils.getCrystalPersistentDataDouble(crystal, "speed")));
                    player.sendMessage(Component.text(" §1§astate: §r§6" + Utils.getCrystalPersistentDataBoolean(crystal, "state")));
                    player.sendMessage(Component.text(" §1§aNumber of tasks: §r§6" + plugin.crystalTasks.get(crystal).size()));
                    player.sendMessage(Component.text("§9§l---------------------------------"));
                    return;
                }
            }

            //create GUI for various color and angle settings, making sure to identify it with a CrystalSettingsHolder
            CrystalSettingsHolder guiHolder = new CrystalSettingsHolder(null);
            Inventory gui = Bukkit.createInventory(guiHolder, 27, Component.text("Spotlight Settings"));
            guiHolder.setInventory(gui);

            //add the gui to our global list
            plugin.crystalGui.put(guiHolder, crystal);

            //get what color is selected within the crystal's persistent data
            String selectedColor = Objects.requireNonNull(Utils.getCrystalPersistentDataString(crystal, "color"));

            //get what sweep mode is selected within the crystal's persistent data
            String selectedSweepMode = Objects.requireNonNull(Utils.getCrystalPersistentDataString(crystal, "sweep"));

            //get what the current beam angle is set to
            int[] angle = Utils.getCrystalPersistentDataIntArray(crystal, "angle");

            //this is where items for color choices are added - if a color is selected, a glass pane is added

            //white beam color
            ItemStack whiteColor;
            if (selectedColor.equals("White")) {
                whiteColor = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
                ItemMeta whiteMeta = whiteColor.getItemMeta();
                whiteMeta.displayName(Component.text(Utils.formatDyeString("WHITE_DYE")+" Beam§a (SELECTED)"));
                whiteColor.setItemMeta(whiteMeta);
            }
            else {
                whiteColor = new ItemStack(Material.WHITE_DYE);
                ItemMeta whiteMeta = whiteColor.getItemMeta();
                whiteMeta.displayName(Component.text("§fWhite Beam"));
                whiteColor.setItemMeta(whiteMeta);
            }

            //gray beam color
            ItemStack grayColor;
            if (selectedColor.equals("Gray")) {
                grayColor = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta grayMeta = grayColor.getItemMeta();
                grayMeta.displayName(Component.text(Utils.formatDyeString("GRAY_DYE")+" Beam§a (SELECTED)"));
                grayColor.setItemMeta(grayMeta);
            }
            else {
                grayColor = new ItemStack(Material.GRAY_DYE);
                ItemMeta grayMeta = grayColor.getItemMeta();
                grayMeta.displayName(Component.text("§8Gray Beam"));
                grayColor.setItemMeta(grayMeta);
            }

            //black beam color
            ItemStack blackColor;
            if (selectedColor.equals("Black")) {
                blackColor = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta blackMeta = blackColor.getItemMeta();
                blackMeta.displayName(Component.text(Utils.formatDyeString("BLACK_DYE")+" Beam§a (SELECTED)"));
                blackColor.setItemMeta(blackMeta);
            }
            else {
                blackColor = new ItemStack(Material.BLACK_DYE);
                ItemMeta blackMeta = blackColor.getItemMeta();
                blackMeta.displayName(Component.text("§0Black Beam"));
                blackColor.setItemMeta(blackMeta);
            }

            //brown beam color
            ItemStack brownColor;
            if(selectedColor.equals("Brown")) {
                brownColor = new ItemStack(Material.BROWN_STAINED_GLASS_PANE);
                ItemMeta brownMeta = brownColor.getItemMeta();
                brownMeta.displayName(Component.text(Utils.formatDyeString("BROWN_DYE")+" Beam§a (SELECTED)"));
                brownColor.setItemMeta(brownMeta);
            }
            else {
                brownColor = new ItemStack(Material.BROWN_DYE);
                ItemMeta brownMeta = brownColor.getItemMeta();
                brownMeta.displayName(Component.text("§4Brown Beam"));
                brownColor.setItemMeta(brownMeta);
            }

            //red beam color
            ItemStack redColor;
            if (selectedColor.equals("Red")) {
                redColor = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta redMeta = redColor.getItemMeta();
                redMeta.displayName(Component.text(Utils.formatDyeString("RED_DYE")+" Beam§a (SELECTED)"));
                redColor.setItemMeta(redMeta);
            }
            else {
                redColor = new ItemStack(Material.RED_DYE);
                ItemMeta redMeta = redColor.getItemMeta();
                redMeta.displayName(Component.text("§cRed Beam"));
                redColor.setItemMeta(redMeta);
            }

            //orange beam color
            ItemStack orangeColor;
            if (selectedColor.equals("Orange")) {
                orangeColor = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
                ItemMeta orangeMeta = orangeColor.getItemMeta();
                orangeMeta.displayName(Component.text(Utils.formatDyeString("ORANGE_DYE")+" Beam§a (SELECTED)"));
                orangeColor.setItemMeta(orangeMeta);
            }
            else {
                orangeColor = new ItemStack(Material.ORANGE_DYE);
                ItemMeta orangeMeta = orangeColor.getItemMeta();
                orangeMeta.displayName(Component.text("§6Orange Beam"));
                orangeColor.setItemMeta(orangeMeta);
            }

            //yellow beam color
            ItemStack yellowColor;
            if(selectedColor.equals("Yellow")) {
                yellowColor = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                ItemMeta yellowMeta = yellowColor.getItemMeta();
                yellowMeta.displayName(Component.text(Utils.formatDyeString("YELLOW_DYE")+" Beam§a (SELECTED)"));
                yellowColor.setItemMeta(yellowMeta);
            }
            else {
                yellowColor = new ItemStack(Material.YELLOW_DYE);
                ItemMeta yellowMeta = yellowColor.getItemMeta();
                yellowMeta.displayName(Component.text("§eYellow Beam"));
                yellowColor.setItemMeta(yellowMeta);
            }

            //lime beam color
            ItemStack limeColor;
            if (selectedColor.equals("Lime")) {
                limeColor = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta limeMeta = limeColor.getItemMeta();
                limeMeta.displayName(Component.text(Utils.formatDyeString("LIME_DYE")+" Beam§a (SELECTED)"));
                limeColor.setItemMeta(limeMeta);
            }
            else {
                limeColor = new ItemStack(Material.LIME_DYE);
                ItemMeta limeMeta = limeColor.getItemMeta();
                limeMeta.displayName(Component.text("§aLime Beam"));
                limeColor.setItemMeta(limeMeta);
            }

            //green beam color
            ItemStack greenColor;
            if (selectedColor.equals("Green")) {
                greenColor = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
                ItemMeta greenMeta = greenColor.getItemMeta();
                greenMeta.displayName(Component.text(Utils.formatDyeString("GREEN_DYE")+" Beam§a (SELECTED)"));
                greenColor.setItemMeta(greenMeta);
            }
            else {
                greenColor = new ItemStack(Material.GREEN_DYE);
                ItemMeta greenMeta = greenColor.getItemMeta();
                greenMeta.displayName(Component.text("§2Green Beam"));
                greenColor.setItemMeta(greenMeta);
            }

            //cyan beam color
            ItemStack cyanColor;
            if (selectedColor.equals("Cyan")) {
                cyanColor = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
                ItemMeta cyanMeta = cyanColor.getItemMeta();
                cyanMeta.displayName(Component.text(Utils.formatDyeString("CYAN_DYE")+" Beam§a (SELECTED)"));
                cyanColor.setItemMeta(cyanMeta);
            }
            else {
                cyanColor = new ItemStack(Material.CYAN_DYE);
                ItemMeta cyanMeta = cyanColor.getItemMeta();
                cyanMeta.displayName(Component.text("§3Cyan Beam"));
                cyanColor.setItemMeta(cyanMeta);
            }

            //light blue beam color
            ItemStack lightBlueColor;
            if (selectedColor.equals("Light Blue")) {
                lightBlueColor = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                ItemMeta lightBlueMeta = lightBlueColor.getItemMeta();
                lightBlueMeta.displayName(Component.text(Utils.formatDyeString("LIGHT_BLUE_DYE")+" Beam§a (SELECTED)"));
                lightBlueColor.setItemMeta(lightBlueMeta);
            }
            else {
                lightBlueColor = new ItemStack(Material.LIGHT_BLUE_DYE);
                ItemMeta lightBlueMeta = lightBlueColor.getItemMeta();
                lightBlueMeta.displayName(Component.text("§bLight Blue Beam"));
                lightBlueColor.setItemMeta(lightBlueMeta);
            }

            //blue beam color
            ItemStack blueColor;
            if (selectedColor.equals("Blue")) {
                blueColor = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
                ItemMeta blueMeta = blueColor.getItemMeta();
                blueMeta.displayName(Component.text(Utils.formatDyeString("Blue")+" Beam§a (SELECTED)"));
                blueColor.setItemMeta(blueMeta);
            }
            else {
                blueColor = new ItemStack(Material.BLUE_DYE);
                ItemMeta blueMeta = blueColor.getItemMeta();
                blueMeta.displayName(Component.text("§9Blue Beam"));
                blueColor.setItemMeta(blueMeta);
            }

            //purple beam color
            ItemStack purpleColor;
            if (selectedColor.equals("Purple")) {
                purpleColor = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
                ItemMeta purpleMeta = purpleColor.getItemMeta();
                purpleMeta.displayName(Component.text(Utils.formatDyeString("PURPLE_DYE")+" Beam§a (SELECTED)"));
                purpleColor.setItemMeta(purpleMeta);
            }
            else {
                purpleColor = new ItemStack(Material.PURPLE_DYE);
                ItemMeta purpleMeta = purpleColor.getItemMeta();
                purpleMeta.displayName(Component.text("§5Purple Beam"));
                purpleColor.setItemMeta(purpleMeta);
            }

            //magenta beam color
            ItemStack magentaColor;
            if (selectedColor.equals("Magenta")) {
                magentaColor = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
                ItemMeta magentaMeta = magentaColor.getItemMeta();
                magentaMeta.displayName(Component.text(Utils.formatDyeString("MAGENTA_DYE")+" Beam§a (SELECTED)"));
                magentaColor.setItemMeta(magentaMeta);
            }
            else {
                magentaColor = new ItemStack(Material.MAGENTA_DYE);
                ItemMeta magentaMeta = magentaColor.getItemMeta();
                magentaMeta.displayName(Component.text("§5Magenta Beam"));
                magentaColor.setItemMeta(magentaMeta);
            }

            //pink beam color
            ItemStack pinkColor;
            if (selectedColor.equals("Pink")) {
                pinkColor = new ItemStack(Material.PINK_STAINED_GLASS_PANE);
                ItemMeta pinkMeta = pinkColor.getItemMeta();
                pinkMeta.displayName(Component.text(Utils.formatDyeString("PINK_DYE")+" Beam§a (SELECTED)"));
                pinkColor.setItemMeta(pinkMeta);
            }
            else {
                pinkColor = new ItemStack(Material.PINK_DYE);
                ItemMeta pinkMeta = pinkColor.getItemMeta();
                pinkMeta.displayName(Component.text("§dPink Beam"));
                pinkColor.setItemMeta(pinkMeta);
            }

            //add all the items to the gui
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
            ItemStack defaultColor;
            if (selectedColor.equals("None")) {
                defaultColor = new ItemStack(Material.STRUCTURE_VOID);
                ItemMeta defaultMeta = defaultColor.getItemMeta();
                defaultMeta.displayName(Component.text("Default Beam§a (SELECTED)"));
                defaultColor.setItemMeta(defaultMeta);
            }
            else {
                defaultColor = new ItemStack(Material.BARRIER);
                ItemMeta defaultMeta = defaultColor.getItemMeta();
                defaultMeta.displayName(Component.text("Default Beam"));
                defaultColor.setItemMeta(defaultMeta);
            }

            //add that to the gui
            gui.addItem(defaultColor);

            //items for sweep MODE selection
            ItemStack staticArrow;
            ItemStack dynamicCompass;

            //add arrows for altering angle/sweep bounds, named appropriately

            //define the textures
            String dwnTexture   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0=";
            String upTexture    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNjYmY5ODgzZGQzNTlmZGYyMzg1YzkwYTQ1OWQ3Mzc3NjUzODJlYzQxMTdiMDQ4OTVhYzRkYzRiNjBmYyJ9fX0=";
            String rightTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjljYjRkZDIxMjU5YzBkNzVhYTMxNWZmMzg5YzNjZWY3NTJiZTM5NDkzMzgxNjRiYWM4NGE5NmUifX19";
            String leftTexture  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyYTdkNzU1YzYzMzg4ZmYwMTc1MmQ1ZjQ0MTlmYzY0NSJ9fX0=";

            //create the custom player head items
            ItemStack dwnArrow = Utils.getCustomHead(dwnTexture);
            ItemStack upArrow = Utils.getCustomHead(upTexture);
            ItemStack rightArrow = Utils.getCustomHead(rightTexture);
            ItemStack leftArrow = Utils.getCustomHead(leftTexture);

            //get the item metas
            ItemMeta dwnMeta = dwnArrow.getItemMeta();
            ItemMeta upMeta = upArrow.getItemMeta();
            ItemMeta rightMeta = rightArrow.getItemMeta();
            ItemMeta leftMeta = leftArrow.getItemMeta();

            //check if the crystal is in static mode
            if (selectedSweepMode.equals("Static")) {

                //set each item and item name accordingly
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

                //set the item lore with the appropriate colors
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

            //the crystal is in dynamic beam mode
            else {

                //set each item and item name accordingly
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
                int[] bounds = Utils.getCrystalPersistentDataIntArray(crystal, "bounds");
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
                ItemStack dwnRedArrow = Utils.getCustomHead(dwnRedTexture);
                ItemStack upRedArrow = Utils.getCustomHead(upRedTexture);

                //get the item metas
                ItemMeta dwnRedMeta = dwnRedArrow.getItemMeta();
                ItemMeta upRedMeta = upRedArrow.getItemMeta();

                //set the names
                dwnRedMeta.displayName(Component.text("§c§l§nDecrease Sweeping Speed"));
                upRedMeta.displayName(Component.text("§a§l§nIncrease Sweeping Speed"));

                //get the current speed for the crystal from its persistent data
                double speed = Utils.getCrystalPersistentDataDouble(crystal, "speed");

                //set the item lore with calculated speed percentages
                dwnRedMeta.lore(Arrays.asList(
                        Component.text("§7Decreases the frequency passed to sin()"),
                        Component.text("§9Halves frequency per click"),
                        Component.text("§6Is currently set to: §a§l"+Utils.calculatePercentage(speed, 0.8, 0.025))
                ));
                upRedMeta.lore(Arrays.asList(
                        Component.text("§7Increases the frequency passed to sin()"),
                        Component.text("§9Doubles frequency per click"),
                        Component.text("§6Is currently set to: §a§l"+Utils.calculatePercentage(speed, 0.8, 0.025))
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

            //actually open the gui to the player
            player.openInventory(gui);
        }
    }

    /// Updates crystal "state" persistent data tag on a redstone change.
    /// Redundant because a task already exists that is constantly updating this.
    @EventHandler
    public void onRedstoneChange(@NotNull BlockRedstoneEvent event) {

        //detect nearby crystals and verify they are spotlight crystals
        Location loc = event.getBlock().getLocation();
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 2, 1)) {

            //check if the entity is a spotlight crystal
            if (entity instanceof EnderCrystal crystal &&
                    Utils.getCrystalPersistentDataString(crystal, "crystal") != null &&
                    Objects.equals(Utils.getCrystalPersistentDataString(crystal, "crystal"), "Spotlight"))
            {
                //update the crystal's beam accordingly
                Utils.updateCrystalState(crystal);
            }
        }
    }

    /// Logic for handling clicks in the custom gui
    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {

        //check if the inventory is the custom one
        if (!(event.getInventory().getHolder() instanceof CrystalSettingsHolder))
            return;

        //gain a reference to the EnderCrystal tied to this inventory
        EnderCrystal crystal;
        if (plugin.crystalGui.containsKey((CrystalSettingsHolder) event.getInventory().getHolder()))
            crystal = plugin.crystalGui.get((CrystalSettingsHolder) event.getInventory().getHolder());
        else return;

        //make sure that the click is within the custom inventory and not in the player’s own inventory
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.CHEST)
            return;

        //get the clicked item and make sure it's valid and not air
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        //gain a reference to the player for messaging and dinging
        Player player = (Player) event.getWhoClicked();

        //check if item is a glass pane
        if (clickedItem.getType().toString().endsWith("_STAINED_GLASS_PANE"))
            //you can't grab the glass pane, that would be a PANE-full bug!
            event.setCancelled(true);

        //check if the item is a dye
        if (clickedItem.getType().toString().endsWith("_DYE")) {

            //prevent the player from obtaining the dye
            event.setCancelled(true);

            //replace the dye with a glass pane of the same color
            Material paneMaterial = Material.getMaterial(clickedItem.getType().toString().replace("_DYE", "_STAINED_GLASS_PANE"));
            String dyeName = Objects.requireNonNull(Material.getMaterial(clickedItem.getType().toString())).toString();
            if (paneMaterial != null) {

                //reset all glass panes to dyes first
                Utils.resetToDefaults(event, crystal);

                //create, name, and set the glass pane item
                ItemStack glassPane = new ItemStack(paneMaterial);
                ItemMeta glassPaneMeta = glassPane.getItemMeta();
                glassPaneMeta.displayName(Component.text(Utils.formatDyeString(dyeName)+" Beam§a (SELECTED)"));
                glassPane.setItemMeta(glassPaneMeta);
                event.getClickedInventory().setItem(event.getSlot(), glassPane);

                //store the selection in the crystal's persistent storage (trim formatting codes, hence substring 6)
                Utils.setCrystalPersistentDataString(crystal, "color", Utils.formatDyeString(dyeName).substring(6));
            }

            //play a sound
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

            //send a message
            player.sendMessage(Component.text("§a§lSet the color to "+Utils.formatDyeString(dyeName)));

            //update the crystal's beam state
            Utils.updateCrystalState(crystal);
        }

        //check if the clicked item is a barrier
        if(clickedItem.getType() == Material.BARRIER){

            //disallow player obtaining the item
            event.setCancelled(true);

            //reset the inventory
            Utils.resetToDefaults(event, crystal);

            //create, name, and set the new item
            ItemStack defaultColor = new ItemStack(Material.STRUCTURE_VOID);
            ItemMeta defaultMeta = defaultColor.getItemMeta();
            defaultMeta.displayName(Component.text("Default Beam§a (SELECTED)"));
            defaultColor.setItemMeta(defaultMeta);
            event.getClickedInventory().setItem(event.getSlot(), defaultColor);

            //update crystal's persistent data
            Utils.setCrystalPersistentDataString(crystal, "color", "None");

            //play a sound
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

            //send a message
            player.sendMessage(Component.text("§a§lSet the beam to §f§l§nDefault"));

            //update the crystal's beam state
            Utils.updateCrystalState(crystal);
        }

        //check if the item is a structure void (means default is already selected, so just ignore)
        if (clickedItem.getType() == Material.STRUCTURE_VOID)
            //disallow player obtaining the item
            event.setCancelled(true);

        //check if the item is an arrow
        if (clickedItem.getType().toString().equals("ARROW")) {

            //disallow player obtaining the item
            event.setCancelled(true);

            //replace the arrow with a spectral arrow and rename it
            ItemStack spectralArrow = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta spectralArrowMeta = spectralArrow.getItemMeta();
            spectralArrowMeta.displayName(Component.text("§c§l§nStatic Beam (Doesn't Move)§2 (SELECTED)"));
            spectralArrow.setItemMeta(spectralArrowMeta);
            event.getClickedInventory().setItem(event.getSlot(), spectralArrow);

            //store selection in the crystal's persistent storage
            Utils.setCrystalPersistentDataString(crystal, "sweep", "Static");

            //replace the clock with a compass and rename it
            ItemStack dynamicCompass = new ItemStack(Material.COMPASS);
            ItemMeta dynamicCompassMeta = dynamicCompass.getItemMeta();
            dynamicCompassMeta.displayName(Component.text("§aDynamic Beam (Moves)"));
            dynamicCompass.setItemMeta(dynamicCompassMeta);
            event.getClickedInventory().setItem(19, dynamicCompass);

            //remove the speed arrows (from the dynamic gui)
            event.getClickedInventory().setItem(25, new ItemStack(Material.AIR));
            event.getClickedInventory().setItem(26, new ItemStack(Material.AIR));

            //update static arrow item lore
            Utils.updateStaticArrowItems(crystal, event.getClickedInventory());

            //update the crystal's beam state
            Utils.updateCrystalState(crystal);

            //dummy
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
        }

        //check if the clicked item is a compass
        if (clickedItem.getType().toString().equals("COMPASS")) {

            //disallow player obtaining the item
            event.setCancelled(true);

            //replace the compass with a clock and rename it
            ItemStack clock = new ItemStack(Material.CLOCK);
            ItemMeta clockMeta = clock.getItemMeta();
            clockMeta.displayName(Component.text("§a§l§nDynamic Beam (Moves)§2 (SELECTED)"));
            clock.setItemMeta(clockMeta);
            event.getClickedInventory().setItem(event.getSlot(), clock);

            //store selection in the crystal's persistent storage
            Utils.setCrystalPersistentDataString(crystal, "sweep", "Dynamic");

            //replace the spectral arrow with an arrow and rename it
            ItemStack staticArrow = new ItemStack(Material.ARROW);
            ItemMeta staticArrowMeta = staticArrow.getItemMeta();
            staticArrowMeta.displayName(Component.text("§cStatic Beam (Doesn't Move)"));
            staticArrow.setItemMeta(staticArrowMeta);
            event.getClickedInventory().setItem(18, staticArrow);

            //create and set the speed changer arrows
            ItemStack dwnRedArrow = Utils.getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTM4NTJiZjYxNmYzMWVkNjdjMzdkZTRiMGJhYTJjNWY4ZDhmY2E4MmU3MmRiY2FmY2JhNjY5NTZhODFjNCJ9fX0=");
            ItemStack upRedArrow = Utils.getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmQ5Mjg3NjE2MzQzZDgzM2U5ZTczMTcxNTljYWEyY2IzZTU5NzQ1MTEzOTYyYzEzNzkwNTJjZTQ3ODg4NGZhIn19fQ==");

            event.getClickedInventory().setItem(25, dwnRedArrow);
            event.getClickedInventory().setItem(26, upRedArrow);

            //if speed is set to 0, set it to 0.1
            if(Utils.getCrystalPersistentDataDouble(crystal, "speed") == 0)
                Utils.setCrystalPersistentDataDouble(crystal, "speed", 0.1);

            //update dynamic arrow item lore
            Utils.updateDynamicArrowItems(crystal, event.getClickedInventory());

            //update the crystal's beam state
            Utils.updateCrystalState(crystal);

            //dummy
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
        }

        //check if the item is a clock - already selected, so do nothing
        if (clickedItem.getType().toString().equals("CLOCK"))
            //disallow player obtaining the item
            event.setCancelled(true);

        //check if the item is a spectral arrow - already selected, so do nothing
        if (clickedItem.getType().toString().equals("SPECTRAL_ARROW"))
            //disallow player obtaining the item
            event.setCancelled(true);

        //check if the item is a player head - it's a quantity modifier that must be identified and acted upon
        if (clickedItem.getType().toString().equals("PLAYER_HEAD")) {

            //disallow player obtaining the item
            event.setCancelled(true);

            //check if we are in static mode
            if(event.getClickedInventory().getItem(18) != null &&
                    Objects.requireNonNull(event.getClickedInventory().getItem(18)).getType().toString().equals("SPECTRAL_ARROW")){

                //get the current angles the beam is at
                int currentVertAngle = Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "angle"))[0];
                int currentHorAngle = Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "angle"))[1];

                //check if clicked arrow was a down arrow
                if(event.getSlot() == 21){

                    //check if the beam has reached a bound (xz plane)
                    if(currentVertAngle == 90) {
                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't lower beam further!"));
                    }

                    //otherwise add 30 degrees to the angle of the beam
                    else {

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle+30, currentHorAngle});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lLowered beam by 30 degrees!"));
                    }
                }
                //check if clicked arrow is an up arrow
                if(event.getSlot() == 22){

                    //check if the beam will be vertical as a result of this change
                    if(currentVertAngle - 30 == 0){

                        //set the angle to 0, 0 (update the crystal's persistent data)
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{0, 0});

                        //update the crystal's state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRaised beam by 30 degrees!"));
                    }

                    //check if the beam has reached a bound (y-axis)
                    if(currentVertAngle == 0){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't raise beam further!"));
                    }

                    //otherwise subtract 30 degrees from angle of the beam
                    else {

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle-30, currentHorAngle});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRaised beam by 30 degrees!"));
                    }
                }

                //check if the clicked arrow is a right arrow
                if(event.getSlot() == 23){

                    //if the beam is vertical, prevent rotation
                    if(currentVertAngle == 0){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't rotate beam while vertical!"));
                    }

                    //if the angle is 330, set to 0
                    else if(currentHorAngle == 330){

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, 0});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees clockwise!"));
                    }

                    //otherwise, add 30 degrees to the angle
                    else {

                        //update crystal persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, currentHorAngle + 30});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees clockwise!"));
                    }
                }

                //check if the clicked arrow is a left arrow
                if(event.getSlot() == 24) {

                    //if the beam is vertical, prevent rotation
                    if(currentVertAngle == 0){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't rotate beam while vertical!"));
                    }

                    //if the angle is 0, decrease to 330
                    else if(currentHorAngle == 0){

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, 330});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees counterclockwise!"));
                    }
                    //otherwise, subtract 30 degrees from the angle
                    else {

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, currentHorAngle - 30});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees counterclockwise!"));
                    }
                }
            }

            //crystal is in dynamic mode, so the arrows mean different things
            else {

                //index 0 = angle between bounds, index 1 = rotation angle about y-axis
                int[] currentBounds = Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "bounds"));
                double speed = Utils.getCrystalPersistentDataDouble(crystal, "speed");

                //check if the clicked arrow is a down arrow
                if(event.getSlot() == 21){

                    //check if bounds have reached a lower (world) limit (or upper limit angle-wise)
                    if(currentBounds[0] + 30 == 180){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't lower beam bounds further!"));
                    }

                    //otherwise add 30 to the angle
                    else{

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0]+30, currentBounds[1]});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateDynamicArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lLowered beam bounds 15 degrees each!"));
                    }
                }

                //check if the clicked arrow is an up arrow
                else if(event.getSlot() == 22){

                    //check if bounds have reached an upper (world) limit (y-axis) or the lower limit angle-wise
                    if(currentBounds[0] - 30 == 0){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't raise beam bounds further!"));
                    }

                    //otherwise subtract 30 degrees from the angle
                    else{

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0]-30, currentBounds[1]});

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateDynamicArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRaised beam bounds 15 degrees each!"));
                    }
                }

                //check if the clicked arrow is a right arrow
                else if(event.getSlot() == 23){

                    //if the angle is 330, increase to 0
                    if(currentBounds[1] == 330)
                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], 0});

                        //otherwise add 30 degrees to angle
                    else
                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], currentBounds[1]+30});

                    //update the crystal's beam state
                    Utils.updateCrystalState(crystal);

                    //update item lore
                    Utils.updateDynamicArrowItems(crystal, event.getInventory());

                    //send a message and sound to the player
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    player.sendMessage(Component.text("§a§lRotated beam bounds 30 degrees clockwise!"));
                }

                //check if the clicked arrow is a left arrow
                else if(event.getSlot() == 24){

                    //if the angle is 0, decrease to 330
                    if(currentBounds[1] == 0)
                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], 330});

                        //otherwise, subtract 30 degrees from angle
                    else
                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], currentBounds[1] - 30});

                    //update the crystal's beam state
                    Utils.updateCrystalState(crystal);

                    //update item lore
                    Utils.updateDynamicArrowItems(crystal, event.getInventory());

                    //send a message and sound to the player
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    player.sendMessage(Component.text("§a§lRotated beam bounds 30 degrees counterclockwise!"));
                }

                //check if the clicked arrow is a down red arrow
                else if(event.getSlot() == 25){

                    //check if beam has reached a lower bound (0.025)
                    if(speed == 0.025){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't decrease beam speed further!"));
                    }

                    //otherwise cut the beam travel speed in half
                    else {

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataDouble(crystal, "speed", speed/2);

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateDynamicArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.sendMessage(Component.text("§a§lCut beam speed in half!"));
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    }
                }

                //check if the clicked arrow is an up red arrow
                else if(event.getSlot() == 26){

                    //check if beam has reached an upper bound (0.8)
                    if(speed == 0.8){

                        //send error message and sound to player
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c§lCan't increase beam speed further!"));
                    }

                    //otherwise double the beam travel speed
                    else {

                        //update the crystal's persistent data
                        Utils.setCrystalPersistentDataDouble(crystal, "speed", speed*2);

                        //update the crystal's beam state
                        Utils.updateCrystalState(crystal);

                        //update item lore
                        Utils.updateDynamicArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.sendMessage(Component.text("§a§lDoubled beam speed!"));
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    }
                }
            }

        }

        //check if the item is a redstone torch
        if(clickedItem.getType() == Material.REDSTONE_TORCH){

            //prevent the user from obtaining the item
            event.setCancelled(true);

            //reset the crystal to its defaults
            Utils.resetCrystal(crystal, event.getClickedInventory());

            //send a message and sound to the player
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
            player.sendMessage(Component.text("§c§lReset Crystal to Defaults!"));
        }
    }

}
