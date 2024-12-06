package me.polymorphicagent.crystalspotlights;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CommandManager implements CommandExecutor {

    //test stick item
    private final ItemStack test;

    //plugin reference
    private final CrystalSpotlights plugin;

    public CommandManager(CrystalSpotlights plugin, ItemStack test) {
        this.test = test;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

        Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
        if (sender instanceof Player player){
            if (!player.isOp()){
                player.sendMessage("§cYou must be operator to run this command!");
                return true;
            }
            if (test == null) {
                player.sendMessage("§cTest item is not defined!");
                return true;
            }
            if (args.length == 0) {
                player.getInventory().addItem(test);
                player.sendMessage("§aYou have been given a test stick!");
            }
            else {
                for (Player p : players) {
                    if (args[0].equals(p.getName())) {
                        p.getInventory().addItem(test);
                        p.sendMessage("§aYou have been given a test stick!");
                        return true;
                    }
                }
                player.sendMessage("§cNo such online player §6\""+args[0]+"\"§c found");
            }
        }
        else if(!sender.isOp()){
            sender.sendMessage("§cInsufficient permissions. Must be operator.");
            return true;
        }
        else if (args.length == 0){
            sender.sendMessage("§cUsage: /teststick <playername>");
            return true;
        }
        else {
            for (Player p : players) {
                if (args[0].equals(p.getName())) {
                    p.getInventory().addItem(test);
                    p.sendMessage("§aYou have been given a test stick!");
                    sender.sendMessage("§aGave §6"+p.getName()+" §aa test stick!");
                    return true;
                }
            }
            sender.sendMessage("§cNo such online player §6\""+args[0]+"\"§c found");
        }
        return true;
    }
}
