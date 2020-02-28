package com.github.arboriginal.Chair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Slab.Type;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Consumer;
import org.spigotmc.event.entity.EntityDismountEvent;

public class Plugin extends JavaPlugin implements Listener {
    private HashMap<String, Double> ahp;
    private Set<Material>           trap, flat;

    private boolean dbf, oeh;
    private int     mba, mdb;

    private List<String> asb;

    private HashMap<UUID, Location> chairs;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("chair-reload")) return super.onCommand(sender, command, label, args);
        reloadConfig();
        sender.sendMessage("§7[§6Chair§7] Configuration reloaded.");
        return true;
    }

    @Override
    public void onDisable() {
        chairs.keySet().forEach(i -> del(i));
        super.onDisable();
    }

    @Override
    public void onEnable() {
        super.onEnable();

        trap = Tag.TRAPDOORS.getValues();
        flat = new HashSet<Material>() {
                 private static final long serialVersionUID = 1L;
                 {
                     addAll(Tag.CARPETS.getValues());
                     addAll(Tag.RAILS.getValues());
                     addAll(Tag.WOODEN_PRESSURE_PLATES.getValues());
                     add(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
                     add(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
                     add(Material.STONE_PRESSURE_PLATE);
                 }
             };

        reloadConfig();
        chairs = new HashMap<UUID, Location>();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        saveDefaultConfig();
        FileConfiguration c = getConfig();
        c.options().copyDefaults(true);
        dbf = c.getBoolean("dirBlocksFace");
        oeh = c.getBoolean("onlyEmptyHand");
        asb = c.getStringList("allowedBlocks");
        mba = c.getInt("minBlocksAbove");
        mdb = (int) Math.pow(c.getInt("maxDistToBlock"), 2) + 1;
        ahp = getHeightAdjustments(c.getConfigurationSection("heightAdjustments").getValues(false),
                c.getDefaults().getConfigurationSection("heightAdjustments").getValues(false));
        saveConfig();
    }

    @EventHandler(ignoreCancelled = true)
    private void onBlockBreak(BlockBreakEvent e) {
        Entity a = occupied(sitLocation(e.getBlock()));
        if (a != null) del(a.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    private void onEntityDismount(EntityDismountEvent e) {
        if (e.getEntityType() != EntityType.PLAYER || e.getDismounted().getType() != EntityType.ARMOR_STAND) return;
        UUID i = e.getDismounted().getUniqueId();
        del(i);
        Player   p = (Player) e.getEntity();
        Location l = chairs.remove(i);
        if (l != null) new BukkitRunnable() {
            @Override
            public void run() { // @formatter:off
                Block b = p.getLocation().getBlock(), d = l.getBlock();
                if ((!b.isPassable() || !b.getRelative(BlockFace.UP).isPassable())
                  && (d.isPassable() &&  d.getRelative(BlockFace.UP).isPassable()))
                    p.teleport(l, TeleportCause.UNKNOWN);
            } // @formatter:on
        }.runTask(this);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || (oeh && e.getItem() != null)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("chair.use")) return;
        Block    b = e.getClickedBlock();
        Location l = sitLocation(b);
        if (occupied(l) == null) add(b, l, p);
    }

    private void add(Block b, Location l, Player p) {
        Material t = b.getType();
        if (!valid(b, t)) return;
        Location pl = p.getLocation();
        if (l.distanceSquared(pl) > mdb) return;
        BlockData bd = b.getBlockData();
        chairs.put(b.getWorld().spawn(sitPlayerLocation(b, bd, t, l), ArmorStand.class, new Consumer<ArmorStand>() {
            @Override
            public void accept(ArmorStand a) {
                a.setVisible(false);
                a.setMarker(true);
                if (dbf && bd instanceof Directional) p.teleport(p.getLocation().setDirection(((Directional) bd)
                        .getFacing().getOppositeFace().getDirection()), TeleportCause.UNKNOWN);
                a.addPassenger(p);
            }
        }).getUniqueId(), pl);
    }

    private void del(UUID i) {
        Entity e = Bukkit.getEntity(i);
        if (e.isValid()) e.remove();
    }

    private HashMap<String, Double> getHeightAdjustments(Map<String, Object> map, Map<String, Object> dVals) {
        HashMap<String, Double> vals = new HashMap<String, Double>();
        dVals.forEach((k, v) -> {
            Object uv = map.get(k);
            vals.put(k, (Double) ((uv != null && uv instanceof Double) ? uv : v));
        });
        return vals;
    }

    private Entity occupied(Location l) {
        for (Entity e : l.getWorld().getNearbyEntities(l, 1, 1, 1))
            if (e.getType() == EntityType.ARMOR_STAND && chairs.containsKey(e.getUniqueId())) return e;
        return null;
    }

    private Location sitLocation(Block b) {
        return b.getLocation().add(0.5, 0, 0.5);
    }

    private Location sitPlayerLocation(Block b, BlockData bd, Material t, Location l) {
        if (t == Material.CAKE) return l.add(0, ahp.get("cake"), 0);
        if (t == Material.DAYLIGHT_DETECTOR) return l.add(0, ahp.get("detector"), 0);
        if (flat.contains(t)) return l.add(0, ahp.get("flats"), 0);
        if (trap.contains(t)) return l.add(0, ahp.get("traps"), 0);
        return l.add(0, ahp.get(((bd instanceof Bisected && ((Bisected) bd).getHalf() == Half.BOTTOM)
                || (bd instanceof Slab && ((Slab) bd).getType() == Type.BOTTOM)) ? "slabs" : "default"), 0);
    }

    private boolean valid(Block b, Material t) {
        if (!asb.contains(t.name()) || (trap.contains(t) && !((TrapDoor) b.getBlockData()).isOpen())) return false;
        if (mba > 0) for (int i = 1; i <= mba; i++) if (!b.getRelative(BlockFace.UP, i).isPassable()) return false;
        return true;
    }
}
