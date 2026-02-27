package pl.empedrops;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EmpeDrops extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Long> individualTurboDrop = new HashMap<>();
    private long globalTurboDropUntil = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("drop").setExecutor(this);
        getCommand("turbodrop").setExecutor(this);
        getCommand("empedrops").setExecutor(this); // Nowa komenda do reloadu
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("EmpeDrops zostal wlaczony!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("empedrops")) {
            if (!sender.hasPermission("empedrops.admin")) {
                sender.sendMessage(color("&cNie masz uprawnien!"));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(color("&a&lEmpeDrops &8» &7Konfiguracja zostala przeladowana!"));
                return true;
            }
            sender.sendMessage(color("&cPoprawne uzycie: /empedrops reload"));
            return true;
        }

        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (label.equalsIgnoreCase("drop")) {
            openGUI(p);
            return true;
        }

        if (label.equalsIgnoreCase("turbodrop")) {
            if (!p.hasPermission("empedrops.admin")) {
                p.sendMessage(color("&cNie masz uprawnien!"));
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(color("&cPoprawne uzycie: /turbodrop <nick/all> <minuty>"));
                return true;
            }

            long durationMs = Long.parseLong(args[1]) * 60000L;
            long expireTime = System.currentTimeMillis() + durationMs;

            if (args[0].equalsIgnoreCase("all")) {
                globalTurboDropUntil = expireTime;
                Bukkit.broadcastMessage(color("&8&l&m---------------------------------------"));
                Bukkit.broadcastMessage(color("  &6&lEVENT: &a&lTURBODROP (x2) &6&lAKTYWNY!"));
                Bukkit.broadcastMessage(color("  &7Wszyscy gracze otrzymali bonus na: &e" + args[1] + " min"));
                Bukkit.broadcastMessage(color("&8&l&m---------------------------------------"));
            } else {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage(color("&cGracz jest offline!"));
                    return true;
                }
                individualTurboDrop.put(target.getUniqueId(), expireTime);
                target.sendMessage(color("&6&l✪ &aIndywidualny TurboDrop (x2) na " + args[1] + " min!"));
            }
            return true;
        }
        return true;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.STONE) return;
        Player p = e.getPlayer();
        if (p.getGameMode().name().equals("CREATIVE")) return;

        ItemStack item = p.getItemInHand();
        int fortune = (item != null && item.containsEnchantment(Enchantment.LOOT_BONUS_BLOCKS)) ? item.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS) : 0;
        if (fortune > 3) fortune = 3;

        ConfigurationSection drops = getConfig().getConfigurationSection("drops");
        for (String key : drops.getKeys(false)) {
            List<Double> chances = drops.getDoubleList(key + ".chances");
            double chance = (fortune < chances.size()) ? chances.get(fortune) : chances.get(0);

            if (isTurboActive(p)) {
                chance *= getConfig().getDouble("settings.turbo-multiplier");
            }

            if (new Random().nextDouble() * 100 <= chance) {
                Material m = Material.valueOf(drops.getString(key + ".material"));
                p.getInventory().addItem(new ItemStack(m, 1));
                p.giveExp(drops.getInt(key + ".exp"));
                p.sendMessage(color(getConfig().getString("settings.stone-message").replace("{ITEM}", drops.getString(key + ".name"))));
            }
        }
    }

    private boolean isTurboActive(Player p) {
        return globalTurboDropUntil > System.currentTimeMillis() ||
                (individualTurboDrop.containsKey(p.getUniqueId()) && individualTurboDrop.get(p.getUniqueId()) > System.currentTimeMillis());
    }

    private void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, color(getConfig().getString("settings.inventory-title")));
        ItemStack glass = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,37,38,39,41,42,43,44}) inv.setItem(i, glass);

        ConfigurationSection drops = getConfig().getConfigurationSection("drops");
        for (String key : drops.getKeys(false)) {
            inv.setItem(drops.getInt(key + ".slot"), createDropItem(
                    Material.valueOf(drops.getString(key + ".material")),
                    drops.getString(key + ".name"),
                    drops.getDoubleList(key + ".chances")
            ));
        }
        inv.setItem(40, createStatusItem(p));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory() != null && e.getInventory().getName().equals(color(getConfig().getString("settings.inventory-title")))) e.setCancelled(true);
    }

    private ItemStack createDropItem(Material m, String name, List<Double> c) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(name));
        mt.setLore(Arrays.asList(
                color("&8&m-------------------------"),
                color("&7Szansa podstawowa: &e" + c.get(0) + "%"),
                color("&7Fortuna 1: &e" + c.get(1) + "%"),
                color("&7Fortuna 2: &e" + c.get(2) + "%"),
                color("&7Fortuna 3: &e" + c.get(3) + "%"),
                color(" "),
                color("&6TurboDrop zwieksza szanse x2!"),
                color("&8&m-------------------------")
        ));
        i.setItemMeta(mt);
        return i;
    }

    private ItemStack createStatusItem(Player p) {
        ItemStack i = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color("&6&lTWOJ STATUS"));
        List<String> lore = new ArrayList<>();
        if (globalTurboDropUntil > System.currentTimeMillis()) lore.add(color("&7Event: &a&lAKTYWNY"));
        if (individualTurboDrop.containsKey(p.getUniqueId()) && individualTurboDrop.get(p.getUniqueId()) > System.currentTimeMillis()) lore.add(color("&7Osobisty: &a&lAKTYWNY"));
        if (lore.isEmpty()) lore.add(color("&7TurboDrop: &c&lBRAK"));
        mt.setLore(lore);
        i.setItemMeta(mt);
        return i;
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}