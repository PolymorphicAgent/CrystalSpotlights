package me.polymorphicagent.crystalspotlights;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.event.EventHandler;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

//TODO: organize into classes based on convention rather than have everything in one class
public class CrystalSpotlights extends JavaPlugin implements Listener {

    /// This is the spotlight crystal Item definition
    public static ItemStack spotlightCrystal;

    /// This is the test stick Item definition
    public static ItemStack testStick;

    /// Stores the last player who used a spotlight crystal item
    /// Used to identify which end crystals that spawn are spotlight crystals
    private Player lastSpotlightPlacer = null;

    /// Keeps track of all tasks (i.e. particle, redstone) associated with any crystals
    private final MultiMap<EnderCrystal, BukkitRunnable> crystalTasks = new MultiMap<>();

    /// Keeps track of what open guis are associated with a spotlight crystal
    /// For identification and reference gaining purposes
    private final Map<CrystalSettingsHolder, EnderCrystal> crystalGui = new HashMap<>();

    /// Keeps track of all spotlight crystals that are placed down
    private final ArrayList<EnderCrystal> crystals = new ArrayList<>();

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

        //register the event logic defined in the @EventListener methods below
        Bukkit.getServer().getPluginManager().registerEvents(this, this);

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
        testStick.addUnsafeEnchantment(Enchantment.PROTECTION_FALL, 1);
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
        loadCrystals();

        //start all the tasks defined in each crystal's persistent data
        this.getLogger().log(Level.INFO, "Igniting crystals...");
        for(EnderCrystal crystal : crystals){
            updateCrystalState(crystal);
        }

        //because intellij is stupid imma just randomly call this method here :)
        calculatePercentage(2, 1, 10);
    }

    /// This method gets called when the plugin is disabled
    @Override
    public void onDisable() {
        //save the positions of all spotlight crystals to the config file
        saveCrystals();

        //stop all tasks associated with all crystals (i.e. particle, redstone)
        for(BukkitRunnable b : crystalTasks.entrySet()){
            b.cancel();
        }
    }

    /// Save where all crystals in the world are alongside each world save
    @EventHandler
    public void onAutoSave(WorldSaveEvent event){
        //write crystal coordinates to config file
        saveCrystals();
    }

    /// Starts a task specific to each crystal's settings. Adds to the 'crystalTasks' MultiMap
    /// Tasks will be stopped and re-started every time that there is a persistent data update
    public void startCrystalTask(@NotNull EnderCrystal crystal) {
        //get the beam start-point (the crystal's position)
        Location loc = crystal.getLocation().add(0, 1.5, 0);

        //check if the crystal is in static beam mode
        if (getCrystalPersistentDataString(crystal, "sweep").equals("Static")) {
            //calculate where the beam endpoint should be based off the crystal's angle setting
            ThreeDimensionalAngle angle = new ThreeDimensionalAngle(getCrystalPersistentDataIntArray(crystal, "angle"), this);
            angle.calculateRayEndpoint(new double[]{loc.getX(), loc.getY(), loc.getZ()});
            Location targetLoc = new Location(loc.getWorld(), angle.getEndpoint()[0], angle.getEndpoint()[1] + 1.5, angle.getEndpoint()[2]);

            //if the beam is set to none, use the default end crystal beam, and if not, use particles
            if(!getCrystalPersistentDataString(crystal, "color").equals("None")){
                //make sure the default beam is deactivated
                crystal.setBeamTarget(null);

                //add the task to the global task list to keep track of it
                crystalTasks.put(crystal,
                        new BukkitRunnable() {
                            @Override
                            public void run() {

                                //check if any players are within 50 blocks of the crystal
                                boolean playersNearby = crystal.getWorld().getPlayers().stream()
                                        .anyMatch(player -> player.getLocation().distance(crystal.getLocation()) < 300);

                                //if not, the crystal isn't loaded
                                if(!playersNearby)
                                    return;

                                //check if crystal is still powered
                                if (!getCrystalPersistentDataBoolean(crystal, "state"))
                                    return;

                                //continuously spawn the particles between beam start point and calculated endpoint
                                Color color = getColorFromName(getCrystalPersistentDataString(crystal, "color"));
                                if (color != null)
                                    spawnBeamParticles(loc, targetLoc, color);

                            }
                            @Override
                            public void cancel(){
                                //just in case, deactivate the default beam
                                crystal.setBeamTarget(null);

                                //call the super method to actually cancel the task
                                super.cancel();
                            }
                        }
                );

                //actually start the task, since it is a static beam, it doesn't have run every tick
                crystalTasks.getLast(crystal).runTaskTimer(this, 0, 5);
            }
            //use the default beam
            else {
                //add the task to the global task list to keep track of it
                crystalTasks.put(crystal,
                        new BukkitRunnable() {
                            @Override
                            public void run() {

                                //check if any players are within 50 blocks of the crystal
                                boolean playersNearby = crystal.getWorld().getPlayers().stream()
                                        .anyMatch(player -> player.getLocation().distance(crystal.getLocation()) < 300);

                                //if not, it isn't loaded
                                if(!playersNearby)
                                    return;

                                //if the crystal is powered, activate the default beam
                                if(getCrystalPersistentDataBoolean(crystal, "state"))
                                    crystal.setBeamTarget(targetLoc);

                                //otherwise, deactivate the default beam
                                else
                                    crystal.setBeamTarget(null);
                            }
                            @Override
                            public void cancel(){
                                //just in case, deactivate the default beam
                                crystal.setBeamTarget(null);

                                //call the super method to actually cancel the task
                                super.cancel();
                            }
                        }
                );

                //actually start the task, since it is a static beam, it doesn't have run every tick
                crystalTasks.getLast(crystal).runTaskTimer(this, 0, 5);
            }
        }
        //crystal is in dynamic mode!
        else {
            //set up the angles
            ThreeDimensionalAngle angleA =
                    new ThreeDimensionalAngle(
                            getCrystalPersistentDataIntArray(crystal, "bounds")[0]/2.0,
                            getCrystalPersistentDataIntArray(crystal, "bounds")[1],
                            this
                    );
            ThreeDimensionalAngle angleB =
                    new ThreeDimensionalAngle(
                            getCrystalPersistentDataIntArray(crystal, "bounds")[0]/2.0,
                            flipAngle(getCrystalPersistentDataIntArray(crystal, "bounds")[1]),
                            this
                    );

            //perform the raytracing calculations for each sweeping bound
            angleA.calculateRayEndpoint(new double[]{loc.getX(), loc.getY(), loc.getZ()});
            angleB.calculateRayEndpoint(new double[]{loc.getX(), loc.getY(), loc.getZ()});

            //create location instances based off the results
            Location boundA = new Location(loc.getWorld(), angleA.getEndpoint()[0], angleA.getEndpoint()[1], angleA.getEndpoint()[2]);
            Location boundB = new Location(loc.getWorld(), angleB.getEndpoint()[0], angleB.getEndpoint()[1], angleB.getEndpoint()[2]);

            //get the speed from the crystal's persistent data container
            double speed = getCrystalPersistentDataDouble(crystal, "speed");

            //if color is set to none, use the default beam
            if(getCrystalPersistentDataString(crystal, "color").equals("None")){

                //make sure the default beam is deactivated
                crystal.setBeamTarget(null);

                //add the task to the global task list to keep track of it
                crystalTasks.put(crystal, new BukkitRunnable() {

                    //tracks the progress of the sine wave, no need to worry about hitting double.max here :)
                    private double t = 0.0;

                    //allows for the beam to be reset to an initial state, for synchronization across multiple beams
                    private boolean firstRun = true;

                    @Override
                    public void run() {

                        //if the crystal was *just* activated, reset the beam to its initial position
                        if(firstRun)
                            t = 0.0;

                        //otherwise, continue as normal
                        firstRun = false;

                        //check if any players are within 50 blocks of the crystal
                        boolean playersNearby = crystal.getWorld().getPlayers().stream()
                                .anyMatch(player -> player.getLocation().distance(crystal.getLocation()) < 300);

                        //otherwise, it isn't loaded
                        if(!playersNearby)
                            return;

                        //if the crystal is powered
                        if(getCrystalPersistentDataBoolean(crystal, "state")) {

                            //increment t by the speed factor
                            t += speed;

                            //sine function oscillates smoothly between -1 and 1
                            double sineValue = Math.sin(t);

                            //map sineValue from [-1, 1] to [0, 1] for interpolation
                            double factor = (sineValue + 1) / 2.0;

                            //calculate the new target location using the factor
                            double x = boundA.getX() + (boundB.getX() - boundA.getX()) * factor;
                            double y = boundA.getY() + (boundB.getY() - boundA.getY()) * factor;
                            double z = boundA.getZ() + (boundB.getZ() - boundA.getZ()) * factor;

                            //update the beam target location
                            crystal.setBeamTarget(new Location(loc.getWorld(), x, y, z));
                        }
                        //if the crystal isn't powered
                        else
                            //deactivate the default beam
                            crystal.setBeamTarget(null);
                    }
                    @Override
                    public void cancel(){

                        //just in case, deactivate the default beam
                        crystal.setBeamTarget(null);

                        //reset where the beam starts
                        firstRun = true;

                        //call the super method to actually cancel the task
                        super.cancel();
                    }
                });

                //actually start the task, since it is a dynamic beam, run it every tick
                crystalTasks.getLast(crystal).runTaskTimer(this, 0L, 1L);
            }
            //otherwise, use particle beams
            else {

                //make sure default beam is deactivated
                crystal.setBeamTarget(null);

                //add the task to the global task list to keep track of it
                crystalTasks.put(crystal, new BukkitRunnable() {

                    //tracks the progress of the sine wave, no need to worry about hitting double.max here :)
                    private double t = 0.0;

                    //allows for the beam to be reset to an initial state, for synchronization across multiple beams
                    private boolean firstRun = true;

                    @Override
                    public void run() {

                        //if the crystal was *just* activated, reset the beam to its initial position
                        if(firstRun)
                            t = 0.0;

                        //otherwise, continue as normal
                        firstRun = false;

                        //check if any players are within 50 blocks of the crystal
                        boolean playersNearby = crystal.getWorld().getPlayers().stream()
                                .anyMatch(player -> player.getLocation().distance(crystal.getLocation()) < 300);

                        //otherwise, it isn't loaded
                        if(!playersNearby)
                            return;

                        //if the crystal is powered
                        if(getCrystalPersistentDataBoolean(crystal, "state")) {

                            //make sure default beam is deactivated
                            crystal.setBeamTarget(null);

                            //increment t by the speed factor
                            t += speed;

                            //sine function oscillates smoothly between -1 and 1
                            double sineValue = Math.sin(t);

                            //map sineValue from [-1, 1] to [0, 1] for interpolation
                            double factor = (sineValue + 1) / 2.0;

                            //calculate the new target location using the factor
                            double x = boundA.getX() + (boundB.getX() - boundA.getX()) * factor;
                            double y = boundA.getY() + (boundB.getY() - boundA.getY()) * factor;
                            double z = boundA.getZ() + (boundB.getZ() - boundA.getZ()) * factor;

                            //spawn the particles
                            Color color = getColorFromName(getCrystalPersistentDataString(crystal, "color"));
                            if (color != null)
                                spawnBeamParticles(loc, new Location(loc.getWorld(), x, y, z), color);
                        }
                        //otherwise, not powered
                        else
                            //deactivate the default beam
                            crystal.setBeamTarget(null);
                    }
                    @Override
                    public void cancel(){

                        //just in case, deactivate the default beam
                        crystal.setBeamTarget(null);

                        //reset where the beam starts
                        firstRun = true;

                        //call the super method to actually cancel the task
                        super.cancel();
                    }
                });

                //actually start the task, since it is a dynamic beam, run it every tick
                crystalTasks.getLast(crystal).runTaskTimer(this, 0L, 1L);
            }
        }
    }

    /// Starts a redstone task for the specified crystal. Adds to 'crystalTasks' MultiMap
    /// Tasks will be stopped and re-started every time that there is a persistent data update
    public void startRedstoneTask(@NotNull EnderCrystal crystal) {

        //add the task to the global task list to keep track of it
        crystalTasks.put(crystal, new BukkitRunnable() {
            @Override
            public void run() {

                //check power state and update crystal persistent data
                setCrystalPersistentDataBoolean(crystal, "state", isPowered(crystal));
            }
        });

        //actually start the task, run it every 2 ticks
        crystalTasks.getLast(crystal).runTaskTimer(this, 0L, 2L);
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
                String s = itemInHand.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this, "crystal"), PersistentDataType.STRING);
                if (s != null && s.equals("spotlight")) {

                    //if so, store the player who interacted with the custom item
                    lastSpotlightPlacer = player;
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
            if (lastSpotlightPlacer != null) {

                //customize the appearance
                crystal.setShowingBottom(false);

                //set the default color and sweep mode + a tag that identifies it as a spotlight crystal
                setCrystalPersistentDataString(crystal, "crystal", "Spotlight");
                setCrystalPersistentDataString(crystal, "color", "None");
                setCrystalPersistentDataString(crystal, "sweep", "Static");
                setCrystalPersistentDataIntArray(crystal, "angle", new int[]{0, 0});


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
                setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{90, 0});

                //eventually gets passed on as the FREQUENCY (b value) of the sin/cos function
                setCrystalPersistentDataDouble(crystal, "speed", 0.0);

                //set the beam state based on the redstone state of the block below the crystal
                setCrystalPersistentDataBoolean(crystal, "state", isPowered(crystal));

                //actually update the crystal based on the above persistent data
                updateCrystalState(crystal);

                //add the crystal to our global arraylist for persistence across restarts
                crystals.add(crystal);

                //reset lastPlacer after the crystal is spawned
                lastSpotlightPlacer = null;
            }
        }
    }

    /// Detects when a spotlight crystal explodes, stops/deletes all associated tasks
    @EventHandler
    public void onCrystalExplode(@NotNull EntityExplodeEvent event){

        //check if the entity is a spotlight crystal
        if(event.getEntity() instanceof EnderCrystal crystal &&
                getCrystalPersistentDataString(crystal, "crystal") != null &&
                getCrystalPersistentDataString(crystal, "crystal").equals("Spotlight")){

            //if a task (or tasks) already exists for this crystal, shut it/them down and remove it/them
            for(BukkitRunnable r : crystalTasks.get(crystal))
                r.cancel();

            //remove the crystal from our task list since it doesn't have tasks anymore
            crystalTasks.remove(crystal);

            //remove the crystal from our global list because it doesn't exist anymore
            crystals.remove(crystal);
        }
    }

    /// Detects when a spotlight crystal explodes, stops/deletes all associated tasks (this deals with if another explosion causes this crystal to explode)
    @EventHandler
    public void onCrystalDamaged(@NotNull EntityDamageEvent event) {

        //check if the entity is a spotlight crystal
        if(event.getEntity() instanceof EnderCrystal crystal &&
                getCrystalPersistentDataString(crystal, "crystal") != null &&
                getCrystalPersistentDataString(crystal, "crystal").equals("Spotlight")){

            //if a task (or tasks) already exists for this crystal, shut it/them down and remove it/them
            for(BukkitRunnable r : crystalTasks.get(crystal))
                r.cancel();

            //remove the crystal from our task list since it doesn't have tasks anymore
            crystalTasks.remove(crystal);

            //remove the crystal from our global list because it doesn't exist anymore
            crystals.remove(crystal);
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
        if (crystalGui.containsKey((CrystalSettingsHolder) event.getInventory().getHolder()))
            crystal = crystalGui.get((CrystalSettingsHolder) event.getInventory().getHolder());
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
                resetToDefaults(event, crystal);

                //create, name, and set the glass pane item
                ItemStack glassPane = new ItemStack(paneMaterial);
                ItemMeta glassPaneMeta = glassPane.getItemMeta();
                glassPaneMeta.displayName(Component.text(formatDyeString(dyeName)+" Beam§a (SELECTED)"));
                glassPane.setItemMeta(glassPaneMeta);
                event.getClickedInventory().setItem(event.getSlot(), glassPane);

                //store the selection in the crystal's persistent storage (trim formatting codes, hence substring 6)
                setCrystalPersistentDataString(crystal, "color", formatDyeString(dyeName).substring(6));
            }

            //play a sound
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

            //send a message
            player.sendMessage(Component.text("§a§lSet the color to "+formatDyeString(dyeName)));

            //update the crystal's beam state
            updateCrystalState(crystal);
        }

        //check if the clicked item is a barrier
        if(clickedItem.getType() == Material.BARRIER){

            //disallow player obtaining the item
            event.setCancelled(true);

            //reset the inventory
            resetToDefaults(event, crystal);

            //create, name, and set the new item
            ItemStack defaultColor = new ItemStack(Material.STRUCTURE_VOID);
            ItemMeta defaultMeta = defaultColor.getItemMeta();
            defaultMeta.displayName(Component.text("Default Beam§a (SELECTED)"));
            defaultColor.setItemMeta(defaultMeta);
            event.getClickedInventory().setItem(event.getSlot(), defaultColor);

            //update crystal's persistent data
            setCrystalPersistentDataString(crystal, "color", "None");

            //play a sound
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

            //send a message
            player.sendMessage(Component.text("§a§lSet the beam to §f§l§nDefault"));

            //update the crystal's beam state
            updateCrystalState(crystal);
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
            setCrystalPersistentDataString(crystal, "sweep", "Static");

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
            updateStaticArrowItems(crystal, event.getClickedInventory());

            //update the crystal's beam state
            updateCrystalState(crystal);
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
            setCrystalPersistentDataString(crystal, "sweep", "Dynamic");

            //replace the spectral arrow with an arrow and rename it
            ItemStack staticArrow = new ItemStack(Material.ARROW);
            ItemMeta staticArrowMeta = staticArrow.getItemMeta();
            staticArrowMeta.displayName(Component.text("§cStatic Beam (Doesn't Move)"));
            staticArrow.setItemMeta(staticArrowMeta);
            event.getClickedInventory().setItem(18, staticArrow);

            //create and set the speed changer arrows
            ItemStack dwnRedArrow = getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTM4NTJiZjYxNmYzMWVkNjdjMzdkZTRiMGJhYTJjNWY4ZDhmY2E4MmU3MmRiY2FmY2JhNjY5NTZhODFjNCJ9fX0=");
            ItemStack upRedArrow = getCustomHead("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmQ5Mjg3NjE2MzQzZDgzM2U5ZTczMTcxNTljYWEyY2IzZTU5NzQ1MTEzOTYyYzEzNzkwNTJjZTQ3ODg4NGZhIn19fQ==");

            event.getClickedInventory().setItem(25, dwnRedArrow);
            event.getClickedInventory().setItem(26, upRedArrow);

            //if speed is set to 0, set it to 0.1
            if(getCrystalPersistentDataDouble(crystal, "speed") == 0)
                setCrystalPersistentDataDouble(crystal, "speed", 0.1);

            //update dynamic arrow item lore
            updateDynamicArrowItems(crystal, event.getClickedInventory());

            //update the crystal's beam state
            updateCrystalState(crystal);
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
                int currentVertAngle = getCrystalPersistentDataIntArray(crystal, "angle")[0];
                int currentHorAngle = getCrystalPersistentDataIntArray(crystal, "angle")[1];

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
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle+30, currentHorAngle});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{0, 0});

                        //update the crystal's state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle-30, currentHorAngle});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, 0});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees clockwise!"));
                    }

                    //otherwise, add 30 degrees to the angle
                    else {

                        //update crystal persistent data
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, currentHorAngle + 30});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, 330});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees counterclockwise!"));
                    }
                    //otherwise, subtract 30 degrees from the angle
                    else {

                        //update the crystal's persistent data
                        setCrystalPersistentDataIntArray(crystal, "angle", new int[]{currentVertAngle, currentHorAngle - 30});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateStaticArrowItems(crystal, event.getInventory());

                        //send a message and sound to the player
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a§lRotated beam by 30 degrees counterclockwise!"));
                    }
                }
            }

            //crystal is in dynamic mode, so the arrows mean different things
            else {

                //index 0 = angle between bounds, index 1 = rotation angle about y-axis
                int[] currentBounds = getCrystalPersistentDataIntArray(crystal, "bounds");
                double speed = getCrystalPersistentDataDouble(crystal, "speed");

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
                        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0]+30, currentBounds[1]});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateDynamicArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0]-30, currentBounds[1]});

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateDynamicArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], 0});

                    //otherwise add 30 degrees to angle
                    else
                        //update the crystal's persistent data
                        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], currentBounds[1]+30});

                    //update the crystal's beam state
                    updateCrystalState(crystal);

                    //update item lore
                    updateDynamicArrowItems(crystal, event.getInventory());

                    //send a message and sound to the player
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
                    player.sendMessage(Component.text("§a§lRotated beam bounds 30 degrees clockwise!"));
                }

                //check if the clicked arrow is a left arrow
                else if(event.getSlot() == 24){

                    //if the angle is 0, decrease to 330
                    if(currentBounds[1] == 0)
                        //update the crystal's persistent data
                        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], 330});

                    //otherwise, subtract 30 degrees from angle
                    else
                        //update the crystal's persistent data
                        setCrystalPersistentDataIntArray(crystal, "bounds", new int[]{currentBounds[0], currentBounds[1] - 30});

                    //update the crystal's beam state
                    updateCrystalState(crystal);

                    //update item lore
                    updateDynamicArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataDouble(crystal, "speed", speed/2);

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateDynamicArrowItems(crystal, event.getInventory());

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
                        setCrystalPersistentDataDouble(crystal, "speed", speed*2);

                        //update the crystal's beam state
                        updateCrystalState(crystal);

                        //update item lore
                        updateDynamicArrowItems(crystal, event.getInventory());

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
            resetCrystal(crystal, event.getClickedInventory());

            //send a message and sound to the player
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
            player.sendMessage(Component.text("§c§lReset Crystal to Defaults!"));
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
                getCrystalPersistentDataString(crystal, "crystal") != null &&
                getCrystalPersistentDataString(crystal, "crystal").equals("Spotlight"))
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
                    player.sendMessage(Component.text(" §1§acrystal: §r§6" + getCrystalPersistentDataString(crystal, "crystal")));
                    player.sendMessage(Component.text(" §1§acolor: §r§6" + getCrystalPersistentDataString(crystal, "color")));
                    player.sendMessage(Component.text(" §1§asweep: §r§6" + getCrystalPersistentDataString(crystal, "sweep")));
                    player.sendMessage(Component.text(" §1§aangle: §r§6" + Arrays.toString(getCrystalPersistentDataIntArray(crystal, "angle"))));
                    player.sendMessage(Component.text(" §1§abounds: §r§6" + Arrays.toString(getCrystalPersistentDataIntArray(crystal, "bounds"))));
                    player.sendMessage(Component.text(" §1§aspeed: §r§6" + getCrystalPersistentDataDouble(crystal, "speed")));
                    player.sendMessage(Component.text(" §1§astate: §r§6" + getCrystalPersistentDataBoolean(crystal, "state")));
                    player.sendMessage(Component.text(" §1§aNumber of tasks: §r§6" + crystalTasks.get(crystal).size()));
                    player.sendMessage(Component.text("§9§l---------------------------------"));
                    return;
                }
            }

            //create GUI for various color and angle settings, making sure to identify it with a CrystalSettingsHolder
            CrystalSettingsHolder guiHolder = new CrystalSettingsHolder(null);
            Inventory gui = Bukkit.createInventory(guiHolder, 27, Component.text("Spotlight Settings"));
            guiHolder.setInventory(gui);

            //add the gui to our global list
            crystalGui.put(guiHolder, crystal);

            //get what color is selected within the crystal's persistent data
            String selectedColor = getCrystalPersistentDataString(crystal, "color");

            //get what sweep mode is selected within the crystal's persistent data
            String selectedSweepMode = getCrystalPersistentDataString(crystal, "sweep");

            //get what the current beam angle is set to
            int[] angle = getCrystalPersistentDataIntArray(crystal, "angle");

            //this is where items for color choices are added - if a color is selected, a glass pane is added

            //white beam color
            ItemStack whiteColor;
            if (selectedColor.equals("White")) {
                whiteColor = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
                ItemMeta whiteMeta = whiteColor.getItemMeta();
                whiteMeta.displayName(Component.text(formatDyeString("WHITE_DYE")+" Beam§a (SELECTED)"));
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
                grayMeta.displayName(Component.text(formatDyeString("GRAY_DYE")+" Beam§a (SELECTED)"));
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
                blackMeta.displayName(Component.text(formatDyeString("BLACK_DYE")+" Beam§a (SELECTED)"));
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
                brownMeta.displayName(Component.text(formatDyeString("BROWN_DYE")+" Beam§a (SELECTED)"));
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
                redMeta.displayName(Component.text(formatDyeString("RED_DYE")+" Beam§a (SELECTED)"));
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
                orangeMeta.displayName(Component.text(formatDyeString("ORANGE_DYE")+" Beam§a (SELECTED)"));
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
                yellowMeta.displayName(Component.text(formatDyeString("YELLOW_DYE")+" Beam§a (SELECTED)"));
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
                limeMeta.displayName(Component.text(formatDyeString("LIME_DYE")+" Beam§a (SELECTED)"));
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
                greenMeta.displayName(Component.text(formatDyeString("GREEN_DYE")+" Beam§a (SELECTED)"));
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
                cyanMeta.displayName(Component.text(formatDyeString("CYAN_DYE")+" Beam§a (SELECTED)"));
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
                lightBlueMeta.displayName(Component.text(formatDyeString("LIGHT_BLUE_DYE")+" Beam§a (SELECTED)"));
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
                blueMeta.displayName(Component.text(formatDyeString("Blue")+" Beam§a (SELECTED)"));
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
                purpleMeta.displayName(Component.text(formatDyeString("PURPLE_DYE")+" Beam§a (SELECTED)"));
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
                magentaMeta.displayName(Component.text(formatDyeString("MAGENTA_DYE")+" Beam§a (SELECTED)"));
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
                pinkMeta.displayName(Component.text(formatDyeString("PINK_DYE")+" Beam§a (SELECTED)"));
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
            ItemStack dwnArrow = getCustomHead(dwnTexture);
            ItemStack upArrow = getCustomHead(upTexture);
            ItemStack rightArrow = getCustomHead(rightTexture);
            ItemStack leftArrow = getCustomHead(leftTexture);

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
                int vertAngle = angle[0];
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
                int[] bounds = getCrystalPersistentDataIntArray(crystal, "bounds");
                int t = bounds[0];
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
                    getCrystalPersistentDataString(crystal, "crystal") != null &&
                    getCrystalPersistentDataString(crystal, "crystal").equals("Spotlight"))
            {
                //update the crystal's beam accordingly
                updateCrystalState(crystal);
            }
        }
    }

    /// Formats a string dye name properly
    public String formatDyeString(@NotNull String input) {

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
    private void resetToDefaults(@NotNull InventoryClickEvent event, EnderCrystal crystal) {

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
        if (selectedSweepMode.equals("Static")) {

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
            int vertAngle = angle[0];
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
            int t = bounds[0];
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
    public String getCrystalPersistentDataString(@NotNull EnderCrystal crystal, String key) {
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(this, key);

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
    public int[] getCrystalPersistentDataIntArray(@NotNull EnderCrystal crystal, String key) {
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(this, key);

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
    public double getCrystalPersistentDataDouble(@NotNull EnderCrystal crystal, String key){
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(this, key);

        //get the PersistentDataContainer of the EnderCrystal
        PersistentDataContainer dataContainer = crystal.getPersistentDataContainer();

        //check if the PersistentDataContainer contains the specified key and return the value if it exists
        if (dataContainer.has(namespacedKey, PersistentDataType.DOUBLE))
            return Objects.requireNonNull(dataContainer.get(namespacedKey, PersistentDataType.DOUBLE));
        else {
            this.getLogger().log(Level.SEVERE, "§l§cNONEXISTENT PERSISTENT DATA KEY! (gCPDD)");
            return 0;
        }
    }
    /// Gets the persistent data of the specified crystal as a boolean
    public boolean getCrystalPersistentDataBoolean(@NotNull EnderCrystal crystal, String key){
        //create a NamespacedKey using the provided key string
        NamespacedKey namespacedKey = new NamespacedKey(this, key);

        //get the PersistentDataContainer of the EnderCrystal
        PersistentDataContainer dataContainer = crystal.getPersistentDataContainer();

        //check if the PersistentDataContainer contains the specified key and return the value if it exists
        if (dataContainer.has(namespacedKey, PersistentDataType.BOOLEAN))
            return Objects.requireNonNull(dataContainer.get(namespacedKey, PersistentDataType.BOOLEAN));
        else {
            this.getLogger().log(Level.SEVERE, "§l§cNONEXISTENT PERSISTENT DATA KEY! (gCPDB)");
            return false;
        }
    }

    /// Sets the persistent data of the specified crystal as a String
    public void setCrystalPersistentDataString(@NotNull EnderCrystal crystal, String key, String value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(this, key),
                PersistentDataType.STRING,
                value
        );
    }
    /// Sets the persistent data of the specified crystal as an int Array
    public void setCrystalPersistentDataIntArray(@NotNull EnderCrystal crystal, String key, int @NotNull [] value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(this, key),
                PersistentDataType.INTEGER_ARRAY,
                new int[]{value[0], value[1]}
        );
    }
    /// Sets the persistent data of the specified crystal as a double
    public void setCrystalPersistentDataDouble(@NotNull EnderCrystal crystal, String key, double value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(this, key),
                PersistentDataType.DOUBLE,
                value
        );
    }
    /// Sets the persistent data of the specified crystal as a boolean
    public void setCrystalPersistentDataBoolean(@NotNull EnderCrystal crystal, String key, boolean value){
        crystal.getPersistentDataContainer().set(
                new NamespacedKey(this, key),
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
    public void updateStaticArrowItems(EnderCrystal crystal, @NotNull Inventory gui){

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
        int vertAngle = angle[0];
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
    public void updateDynamicArrowItems(EnderCrystal crystal, @NotNull Inventory gui){
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
        int t = bounds[0];
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
    public void updateCrystalState(EnderCrystal crystal){

        //if a task (or tasks) already exists for this crystal, shut it/them down
        for(BukkitRunnable r : crystalTasks.get(crystal))
            r.cancel();

        //remove the crystal from the global task list since it no longer has tasks
        crystalTasks.remove(crystal);

        //start the beam task - the function will take care of mode, color, etc.
        startCrystalTask(crystal);

        //start the redstone task - checks constantly for redstone changes
        startRedstoneTask(crystal);
    }

    /// Spawns particles between two points
    private void spawnBeamParticles(@NotNull Location start, Location end, Color color) {

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
    private Color getColorFromName(@NotNull String colorName) {
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
    public boolean isPowered(@NotNull EnderCrystal crystal) {
        return isBlockPowered(crystal.getLocation().subtract(0, 1, 0));
    }

    /// Checks if the block at the specified location is powered, either directly or indirectly
    public boolean isBlockPowered(Location location) {

        //check if the location is valid
        if (location == null) return false;

        //get the block at the location
        Block block = location.getBlock();

        //return if the block is powered directly, indirectly, or both
        return (block.isBlockPowered() || block.isBlockIndirectlyPowered()) || (block.isBlockPowered() && block.isBlockIndirectlyPowered());
    }

    /// Saves all crystal positions to config (to allow them to persist across restarts)
    private void saveCrystals(){

        //clear the section for crystals in config to start fresh
        getConfig().set("crystals", null);

        //save each crystal's location
        for (int i = 0; i < crystals.size(); i++) {
            EnderCrystal crystal = crystals.get(i);
            Location loc = crystal.getLocation();
            String path = "crystals." + i;

            //save the location components
            getConfig().set(path + ".world", loc.getWorld().getName());
            getConfig().set(path + ".x", loc.getX());
            getConfig().set(path + ".y", loc.getY());
            getConfig().set(path + ".z", loc.getZ());
        }

        //write to the config file
        saveConfig();
    }

    /// Loads crystal positions from config (to allow them to persist across restarts)
    private void loadCrystals(){

        //just in case anything is loaded, dump that into the config file before processing
        saveDefaultConfig();

        //check if the config file has crystals saved
        if (getConfig().contains("crystals")) {

            //be overly polite...
            this.getLogger().log(Level.INFO, "Crystal config present!");

            //iterate through and load each saved crystal's data
            for (String key : Objects.requireNonNull(getConfig().getConfigurationSection("crystals")).getKeys(false)) {
                String path = "crystals." + key;

                //grab the saved coordinates
                World world = getServer().getWorld(Objects.requireNonNull(getConfig().getString(path + ".world")));
                double x = getConfig().getDouble(path + ".x");
                double y = getConfig().getDouble(path + ".y");
                double z = getConfig().getDouble(path + ".z");

                //be overly polite again...
                this.getLogger().log(Level.INFO, "Loading crystal "+key+" at "+x+", "+y+", "+z);

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
                                getCrystalPersistentDataString(crystal, "crystal").equals("Spotlight"))
                        {
                            //add the crystal to the global list
                            crystals.add(crystal);
                        }
                    }

                    //unload chunks so they don't stay loaded perpetually
                    if (loc.getWorld().isChunkLoaded(loc.getChunk()))
                        loc.getWorld().unloadChunk(loc.getChunk());
                }
            }

            //should I stop being this polite?!?!?
            this.getLogger().log(Level.INFO, "Loaded all crystals!");
        }
    }

    /// Flips the given angle 180 degrees while keeping it positive and not 360
    private int flipAngle(int a){

        //dummy if you can't understand this simple logic I'm sorry for you honestly
        if(a == 180)
            return 0;
        else if(a > 180)
            return a - 180;
        else return a + 180;
    }

    /// Calculates the percentage n lies between min and max
    private @NotNull String calculatePercentage(double n, double max, double min) {

        //so basically if the caller is stupid
        if (max == min) {
            this.getLogger().log(Level.SEVERE, "MINMAX EQUALITY ERROR: "+max+", "+n+", RETURNING 0.00%");
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
    private void resetCrystal(@NotNull EnderCrystal crystal, @NotNull Inventory gui) {

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