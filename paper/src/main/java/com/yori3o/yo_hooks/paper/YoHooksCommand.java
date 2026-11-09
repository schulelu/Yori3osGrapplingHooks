package com.yori3o.yo_hooks.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** /yohooks give &lt;player&gt; &lt;item&gt; [count] and /yohooks reload (instead of the mod's creative tab and config screen). */
final class YoHooksCommand implements BasicCommand {

    private final YoHooks plugin;

    YoHooksCommand(YoHooks plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender to = source.getSender();
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            to.sendMessage(Component.text("YoHooks: config reloaded", NamedTextColor.GREEN));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            ItemStack item = plugin.items().byId(args[2].toLowerCase(Locale.ROOT).replaceFirst("^yo_hooks:", ""));
            int count = args.length > 3 ? parse(args[3]) : 1;
            if (target == null || item == null || count < 1) {
                to.sendMessage(Component.text("usage: /yohooks give <player> <item> [count]", NamedTextColor.RED));
                return;
            }
            item.setAmount(Math.min(count, item.getMaxStackSize()));
            target.getInventory().addItem(item).values().forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
            to.sendMessage(Component.text("Gave " + item.getAmount() + " " + args[2] + " to " + target.getName(), NamedTextColor.GREEN));
            return;
        }
        to.sendMessage(Component.text("usage: /yohooks give <player> <item> [count] | /yohooks reload", NamedTextColor.RED));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            out.addAll(List.of("give", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Tier t : Tier.values()) {
                out.add(t.hookId());
                out.add(t.headId());
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }

    @Override
    public @Nullable String permission() {
        return "yohooks.admin";
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
