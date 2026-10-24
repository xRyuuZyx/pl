package id.roleplay.jobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class CashierService {
    private record Invoice(UUID cashier, double amount, String note, long expiresMs) {}

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final RoleplayJobs plugin;
    private final Set<UUID> onDuty = new HashSet<>();
    private final Map<UUID, Invoice> invoices = new HashMap<>(); // key = pelanggan

    public CashierService(RoleplayJobs plugin) { this.plugin = plugin; }

    public void clear(UUID id) {
        onDuty.remove(id);
        invoices.remove(id);
        invoices.values().removeIf(i -> i.cashier().equals(id));
    }

    private boolean nearRegister(Player p) {
        if (!plugin.getConfig().getBoolean("cashier.require-register", true)) return true;
        Map<String, Location> regs = plugin.jobs().registers();
        if (regs.isEmpty()) return true;
        double r = plugin.getConfig().getDouble("cashier.register-radius", 5.0);
        for (Location l : regs.values()) {
            if (l.getWorld().equals(p.getWorld()) && l.distance(p.getLocation()) <= r) return true;
        }
        return false;
    }

    public void toggleDuty(Player p) {
        if (!plugin.jobs().hasJob(p.getUniqueId(), Job.KASIR)) {
            plugin.send(p, "<red>Kamu bukan Kasir. Ambil job dengan <white>/job join kasir</white>.");
            return;
        }
        if (onDuty.remove(p.getUniqueId())) {
            plugin.send(p, "<yellow>Kamu selesai bertugas sebagai kasir.");
            return;
        }
        if (!nearRegister(p)) {
            plugin.send(p, "<red>Kamu harus berada dekat mesin kasir untuk mulai bertugas.");
            return;
        }
        onDuty.add(p.getUniqueId());
        plugin.send(p, "<green>Kamu sekarang bertugas sebagai kasir. Pakai <white>/kasir tagih <pemain> <jumlah> [catatan]</white>.");
    }

    public void bill(Player cashier, String targetName, String amountStr, String note) {
        if (!onDuty.contains(cashier.getUniqueId())) {
            plugin.send(cashier, "<red>Kamu belum bertugas. Pakai <white>/kasir duty</white> dulu.");
            return;
        }
        if (!nearRegister(cashier)) {
            plugin.send(cashier, "<red>Kamu harus dekat mesin kasir.");
            return;
        }
        Player customer = Bukkit.getPlayerExact(targetName);
        if (customer == null || customer.equals(cashier)) {
            plugin.send(cashier, "<red>Pelanggan tidak ditemukan.");
            return;
        }
        double maxDist = plugin.getConfig().getDouble("cashier.customer-max-distance", 8.0);
        if (!customer.getWorld().equals(cashier.getWorld()) || customer.getLocation().distance(cashier.getLocation()) > maxDist) {
            plugin.send(cashier, "<red>Pelanggan terlalu jauh dari kasir.");
            return;
        }
        double amount;
        try { amount = Double.parseDouble(amountStr); }
        catch (NumberFormatException e) { plugin.send(cashier, "<red>Jumlah harus berupa angka."); return; }
        if (amount <= 0 || amount > plugin.getConfig().getDouble("cashier.max-invoice", 100000)) {
            plugin.send(cashier, "<red>Jumlah tidak valid.");
            return;
        }
        long exp = System.currentTimeMillis() + plugin.getConfig().getLong("cashier.invoice-expire-seconds", 60) * 1000;
        String safeNote = note.isBlank() ? "Pembelian" : note;
        invoices.put(customer.getUniqueId(), new Invoice(cashier.getUniqueId(), amount, safeNote, exp));

        Component msg = MM.deserialize(plugin.getConfig().getString("prefix", "")
                        + "<yellow>" + MM.escapeTags(cashier.getName()) + "</yellow> menagih kamu <gold>" + plugin.fmt(amount)
                        + "</gold> untuk <white>" + MM.escapeTags(safeNote) + "</white> ")
                .append(MM.deserialize("<green><bold>[BAYAR]</bold></green>").clickEvent(ClickEvent.runCommand("/kasir bayar")))
                .append(Component.text(" "))
                .append(MM.deserialize("<red><bold>[TOLAK]</bold></red>").clickEvent(ClickEvent.runCommand("/kasir tolak")));
        customer.sendMessage(msg);
        plugin.send(cashier, "<green>Tagihan dikirim ke <yellow>" + MM.escapeTags(customer.getName()) + "</yellow>.");
    }

    private Invoice takeValid(Player customer) {
        Invoice inv = invoices.remove(customer.getUniqueId());
        if (inv == null) { plugin.send(customer, "<red>Kamu tidak punya tagihan."); return null; }
        if (System.currentTimeMillis() > inv.expiresMs()) { plugin.send(customer, "<red>Tagihan sudah kedaluwarsa."); return null; }
        return inv;
    }

    public void pay(Player customer) {
        Invoice inv = takeValid(customer);
        if (inv == null) return;
        var eco = plugin.economy();
        if (!eco.has(customer, inv.amount())) {
            plugin.send(customer, "<red>Uangmu tidak cukup (" + plugin.fmt(inv.amount()) + ").");
            return;
        }
        eco.withdrawPlayer(customer, inv.amount());

        double earn = inv.amount() * plugin.getConfig().getDouble("cashier.commission-percent", 10) / 100.0
                + plugin.getConfig().getDouble("cashier.salary-per-transaction", 25);
        OfflinePlayer cashier = Bukkit.getOfflinePlayer(inv.cashier());
        eco.depositPlayer(cashier, earn);

        plugin.send(customer, "<green>Pembayaran <gold>" + plugin.fmt(inv.amount()) + "</gold> berhasil. Terima kasih!");
        Player cp = cashier.getPlayer();
        if (cp != null) plugin.send(cp, "<green>" + MM.escapeTags(customer.getName()) + " membayar <gold>"
                + plugin.fmt(inv.amount()) + "</gold>. Gaji/komisimu: <gold>" + plugin.fmt(earn) + "</gold>.");
    }

    public void decline(Player customer) {
        Invoice inv = invoices.remove(customer.getUniqueId());
        if (inv == null) { plugin.send(customer, "<red>Kamu tidak punya tagihan."); return; }
        plugin.send(customer, "<yellow>Tagihan ditolak.");
        Player cp = Bukkit.getPlayer(inv.cashier());
        if (cp != null) plugin.send(cp, "<yellow>" + MM.escapeTags(customer.getName()) + " menolak tagihan.");
    }
}
