package me.polymorphicagent.crystalspotlights;

import me.polymorphicagent.crystalspotlights.utils.Utils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TaskRunner {

    /// Starts a task specific to each crystal's settings. Adds to the 'crystalTasks' MultiMap
    /// Tasks will be stopped and re-started every time that there is a persistent data update
    public static void startCrystalTask(@NotNull EnderCrystal crystal) {
        //get the beam start-point (the crystal's position)
        Location loc = crystal.getLocation().add(0, 1.5, 0);

        //check if the crystal is in static beam mode
        if (Objects.equals(Utils.getCrystalPersistentDataString(crystal, "sweep"), "Static")) {
            //calculate where the beam endpoint should be based off the crystal's angle setting
            ThreeDimensionalAngle angle = new ThreeDimensionalAngle(Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "angle")), Utils.plugin);
            angle.calculateRayEndpoint(new double[]{loc.getX(), loc.getY(), loc.getZ()});
            Location targetLoc = new Location(loc.getWorld(), angle.getEndpoint()[0], angle.getEndpoint()[1] + 1.5, angle.getEndpoint()[2]);

            //if the beam is set to none, use the default end crystal beam, and if not, use particles
            if(!Objects.equals(Utils.getCrystalPersistentDataString(crystal, "color"), "None")){
                //make sure the default beam is deactivated
                crystal.setBeamTarget(null);

                //add the task to the global task list to keep track of it
                Utils.plugin.crystalTasks.put(crystal,
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
                                if (!Utils.getCrystalPersistentDataBoolean(crystal, "state"))
                                    return;

                                //continuously spawn the particles between beam start point and calculated endpoint
                                Color color = Utils.getColorFromName(Objects.requireNonNull(Utils.getCrystalPersistentDataString(crystal, "color")));
                                if (color != null)
                                    Utils.spawnBeamParticles(loc, targetLoc, color);

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
                Utils.plugin.crystalTasks.getLast(crystal).runTaskTimer(Utils.plugin, 0, 5);
            }
            //use the default beam
            else {
                //add the task to the global task list to keep track of it
                Utils.plugin.crystalTasks.put(crystal,
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
                                if(Utils.getCrystalPersistentDataBoolean(crystal, "state"))
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
                Utils.plugin.crystalTasks.getLast(crystal).runTaskTimer(Utils.plugin, 0, 5);
            }
        }
        //crystal is in dynamic mode!
        else {
            //set up the angles
            ThreeDimensionalAngle angleA =
                    new ThreeDimensionalAngle(
                            Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "bounds"))[0]/2.0,
                            Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "bounds"))[1],
                            Utils.plugin
                    );
            ThreeDimensionalAngle angleB =
                    new ThreeDimensionalAngle(
                            Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "bounds"))[0]/2.0,
                            Utils.flipAngle(Objects.requireNonNull(Utils.getCrystalPersistentDataIntArray(crystal, "bounds"))[1]),
                            Utils.plugin
                    );

            //perform the raytracing calculations for each sweeping bound
            angleA.calculateRayEndpoint(new double[]{loc.getX(), loc.getY(), loc.getZ()});
            angleB.calculateRayEndpoint(new double[]{loc.getX(), loc.getY(), loc.getZ()});

            //create location instances based off the results
            Location boundA = new Location(loc.getWorld(), angleA.getEndpoint()[0], angleA.getEndpoint()[1], angleA.getEndpoint()[2]);
            Location boundB = new Location(loc.getWorld(), angleB.getEndpoint()[0], angleB.getEndpoint()[1], angleB.getEndpoint()[2]);

            //get the speed from the crystal's persistent data container
            double speed = Utils.getCrystalPersistentDataDouble(crystal, "speed");

            //if color is set to none, use the default beam
            if(Objects.equals(Utils.getCrystalPersistentDataString(crystal, "color"), "None")){

                //make sure the default beam is deactivated
                crystal.setBeamTarget(null);

                //add the task to the global task list to keep track of it
                Utils.plugin.crystalTasks.put(crystal, new BukkitRunnable() {

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
                        if(Utils.getCrystalPersistentDataBoolean(crystal, "state")) {

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
                Utils.plugin.crystalTasks.getLast(crystal).runTaskTimer(Utils.plugin, 0L, 1L);
            }
            //otherwise, use particle beams
            else {

                //make sure default beam is deactivated
                crystal.setBeamTarget(null);

                //add the task to the global task list to keep track of it
                Utils.plugin.crystalTasks.put(crystal, new BukkitRunnable() {

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
                        if(Utils.getCrystalPersistentDataBoolean(crystal, "state")) {

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
                            Color color = Utils.getColorFromName(Objects.requireNonNull(Utils.getCrystalPersistentDataString(crystal, "color")));
                            if (color != null)
                                Utils.spawnBeamParticles(loc, new Location(loc.getWorld(), x, y, z), color);
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
                Utils.plugin.crystalTasks.getLast(crystal).runTaskTimer(Utils.plugin, 0L, 1L);
            }
        }
    }

    /// Starts a redstone task for the specified crystal. Adds to 'crystalTasks' MultiMap
    /// Tasks will be stopped and re-started every time that there is a persistent data update
    public static void startRedstoneTask(@NotNull EnderCrystal crystal) {

        //add the task to the global task list to keep track of it
        Utils.plugin.crystalTasks.put(crystal, new BukkitRunnable() {
            @Override
            public void run() {

                //check power state and update crystal persistent data
                Utils.setCrystalPersistentDataBoolean(crystal, "state", Utils.isPowered(crystal));
            }
        });

        //actually start the task, run it every 2 ticks
        Utils.plugin.crystalTasks.getLast(crystal).runTaskTimer(Utils.plugin, 0L, 2L);
    }
}
