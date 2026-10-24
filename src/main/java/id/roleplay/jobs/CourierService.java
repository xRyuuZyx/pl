package id.roleplay.jobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CourierService {
    private record Delivery(String name, Location target, Location origin, long startMs) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final RoleplayJobs plugin;
    private final NamespacedKey packageKey;
    private final Map<UUID, Delivery> active = new HashMap<>();

    public CourierService(RoleplayJobs plugin) {
        this.plugin = plugin;
        this.packageKey = new NamespacedKey(plugin, "paket");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public boolean isActive(UUID id) { return active.containsKey(id); }

    public void start(Player p) {
        if (!plugin.jobs().hasJob(p.getUniqueId(), Job.KURIR)) {
            plugin.send(p, "<red>Kamu bukan Kurir. Ambil job dengan <white>/job join kurir</white>.");
            return;
        }
        if (active.containsKey(p.getUniqueId())) {
            plugin.send(p, "<red>Kamu masih punya paket yang belum diantar. Pakai <white>/kurir batal</white> untuk membatalkan.");
            return;
        }
        Map<String, Location> points = plugin.jobs().deliveries();
        if (points.isEmpty()) {
            plugin.send(p, "<red>Belum ada titik pengantaran. Minta admin pakai <white>/rpjobs setdelivery <nama></white>.");
            return;
        }
        List<Map.Entry<String, Location>> list = new ArrayList<>(points.entrySet());
        Map.Entry<String, Location> pick = list.get(new Random().nextInt(list.size()));

        Delivery d = new Delivery(pick.getKey(), pick.getValue(), p.getLocation().clone(), System.currentTimeMillis());
        active.put(p.getUniqueId(), d);
        p.getInventory().addItem(createPackage(pick.getKey()));
        if (p.getWorld().equals(d.target().getWorld())) p.setCompassTarget(d.target());
        plugin.send(p, "<green>Paket diambil! Antar ke <yellow>" + MiniMessage.miniMessage().escapeTags(pick.getKey())
                + "</yellow>. Kompasmu menunjuk ke tujuan.");
    }

    private ItemStack createPackage(String dest) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<gold>📦 Paket").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MM.deserialize("<gray>Tujuan: <yellow>" + MM.escapeTags(dest)).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(packageKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void removePackage(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.hasItemMeta()
                    && it.getItemMeta().getPersistentDataContainer().has(packageKey, PersistentDataType.BYTE)) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    private boolean hasPackage(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.hasItemMeta()
                    && it.getItemMeta().getPersistentDataContainer().has(packageKey, PersistentDataType.BYTE)) return true;
        }
        return false;
    }

    /** Batalkan pengantaran. */
    public void cancel(UUID id, boolean removeItem) {
        Delivery d = active.remove(id);
        if (d == null) return;
        Player p = Bukkit.getPlayer(id);
        if (p != null && removeItem) removePackage(p);
    }

    private void tick() {
        double radius = plugin.getConfig().getDouble("courier.delivery-radius", 3.0);
        for (Map.Entry<UUID, Delivery> e : new ArrayList<>(active.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { active.remove(e.getKey()); continue; }
            Delivery d = e.getValue();

            if (!hasPackage(p)) {
                active.remove(e.getKey());
                plugin.send(p, "<red>Paket hilang, pengantaran dibatalkan.");
                continue;
            }
            if (!p.getWorld().equals(d.target().getWorld())) {
                p.sendActionBar(MM.deserialize("<yellow>Tujuan ada di dunia lain"));
                continue;
            }
            double dist = p.getLocation().distance(d.target());
            p.sendActionBar(MM.deserialize("<gold>📦 " + MM.escapeTags(d.name()) + " <gray>- <white>" + (int) dist + " blok"));
            if (dist <= radius) complete(p, d);
        }
    }

    private void complete(Player p, Delivery d) {
        active.remove(p.getUniqueId());
        removePackage(p);

        double base = plugin.getConfig().getDouble("courier.pay-base");
        double perBlock = plugin.getConfig().getDouble("courier.pay-per-block");
        double distance = d.origin().getWorld().equals(d.target().getWorld()) ? d.origin().distance(d.target()) : 0;
        double pay = base + distance * perBlock;

        long elapsed = (System.currentTimeMillis() - d.startMs()) / 1000;
        boolean bonus = elapsed <= plugin.getConfig().getLong("courier.max-time-seconds");
        if (bonus) pay += plugin.getConfig().getDouble("courier.time-bonus");

        plugin.economy().depositPlayer(p, pay);
        plugin.send(p, "<green>Paket sampai di <yellow>" + MM.escapeTags(d.name()) + "</yellow>! Kamu dibayar <gold>"
                + plugin.fmt(pay) + "</gold>" + (bonus ? " <gray>(termasuk bonus cepat)" : "") + ".");
    }
}
