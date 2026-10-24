package id.roleplay.jobs;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class Commands implements CommandExecutor, TabCompleter {
    private final RoleplayJobs plugin;

    public Commands(RoleplayJobs plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "job" -> job(s, a);
            case "kurir" -> kurir(s, a);
            case "kasir" -> kasir(s, a);
            case "rpjobs" -> admin(s, a);
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.send(s, "<red>Perintah ini hanya untuk pemain.");
        return null;
    }

    // ---------- /job ----------
    private void job(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        String sub = a.length > 0 ? a[0].toLowerCase() : "info";
        switch (sub) {
            case "list" -> {
                StringBuilder sb = new StringBuilder("<yellow>Job tersedia:</yellow>");
                for (Job j : Job.values()) sb.append("\n <gray>-</gray> <white>").append(j.key()).append("</white> (").append(j.display()).append(")");
                plugin.send(p, sb.toString());
            }
            case "join" -> {
                Job j = a.length > 1 ? Job.from(a[1]) : null;
                if (j == null) { plugin.send(p, "<red>Job tidak ditemukan. Lihat <white>/job list</white>."); return; }
                plugin.jobs().setJob(p.getUniqueId(), j);
                plugin.send(p, "<green>Kamu sekarang bekerja sebagai <yellow>" + j.display() + "</yellow>.");
            }
            case "leave" -> {
                if (plugin.jobs().getJob(p.getUniqueId()) == null) { plugin.send(p, "<red>Kamu tidak punya job."); return; }
                plugin.jobs().setJob(p.getUniqueId(), null);
                plugin.send(p, "<yellow>Kamu keluar dari job.");
            }
            default -> {
                Job j = plugin.jobs().getJob(p.getUniqueId());
                plugin.send(p, j == null ? "<gray>Kamu belum punya job. <white>/job list" : "<gray>Job kamu: <yellow>" + j.display());
            }
        }
    }

    // ---------- /kurir ----------
    private void kurir(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "ambil" -> plugin.courier().start(p);
            case "batal" -> {
                if (!plugin.courier().isActive(p.getUniqueId())) { plugin.send(p, "<red>Tidak ada pengantaran aktif."); return; }
                plugin.courier().cancel(p.getUniqueId(), true);
                plugin.send(p, "<yellow>Pengantaran dibatalkan.");
            }
            default -> plugin.send(p, "<gray>Gunakan <white>/kurir ambil</white> atau <white>/kurir batal</white>.");
        }
    }

    // ---------- /kasir ----------
    private void kasir(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "duty" -> plugin.cashier().toggleDuty(p);
            case "tagih" -> {
                if (!plugin.jobs().hasJob(p.getUniqueId(), Job.KASIR)) { plugin.send(p, "<red>Kamu bukan Kasir."); return; }
                if (a.length < 3) { plugin.send(p, "<gray>Pakai: <white>/kasir tagih <pemain> <jumlah> [catatan]"); return; }
                String note = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : "";
                plugin.cashier().bill(p, a[1], a[2], note);
            }
            case "bayar" -> plugin.cashier().pay(p);
            case "tolak" -> plugin.cashier().decline(p);
            default -> plugin.send(p, "<gray>Gunakan <white>/kasir duty</white>, <white>/kasir tagih</white>, <white>/kasir bayar</white>, <white>/kasir tolak</white>.");
        }
    }

    // ---------- /rpjobs (admin) ----------
    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("rpjobs.admin")) { plugin.send(s, "<red>Tidak punya izin."); return; }
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "setdelivery", "setkasir" -> {
                Player p = player(s);
                if (p == null) return;
                if (a.length < 2) { plugin.send(p, "<red>Sebutkan nama titik."); return; }
                Location l = p.getLocation().toBlockLocation().add(0.5, 0, 0.5);
                boolean del = sub.equals("setdelivery");
                (del ? plugin.jobs().deliveries() : plugin.jobs().registers()).put(a[1].toLowerCase(), l);
                plugin.jobs().save();
                plugin.send(p, "<green>" + (del ? "Titik pengantaran" : "Mesin kasir") + " <yellow>" + a[1].toLowerCase() + "</yellow> disimpan.");
            }
            case "deldelivery", "delkasir" -> {
                if (a.length < 2) { plugin.send(s, "<red>Sebutkan nama titik."); return; }
                boolean del = sub.equals("deldelivery");
                Location removed = (del ? plugin.jobs().deliveries() : plugin.jobs().registers()).remove(a[1].toLowerCase());
                plugin.jobs().save();
                plugin.send(s, removed != null ? "<green>Dihapus." : "<red>Nama tidak ditemukan.");
            }
            case "list" -> {
                plugin.send(s, "<yellow>Titik pengantaran:</yellow> <white>" + String.join(", ", plugin.jobs().deliveries().keySet())
                        + "\n<yellow>Mesin kasir:</yellow> <white>" + String.join(", ", plugin.jobs().registers().keySet()));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.jobs().load();
                plugin.send(s, "<green>Config & data dimuat ulang.");
            }
            default -> plugin.send(s, "<gray>/rpjobs <setdelivery|deldelivery|setkasir|delkasir|list|reload>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String cmd = c.getName().toLowerCase();
        if (a.length == 1) {
            switch (cmd) {
                case "job" -> out.addAll(List.of("join", "leave", "info", "list"));
                case "kurir" -> out.addAll(List.of("ambil", "batal"));
                case "kasir" -> out.addAll(List.of("duty", "tagih", "bayar", "tolak"));
                case "rpjobs" -> out.addAll(List.of("setdelivery", "deldelivery", "setkasir", "delkasir", "list", "reload"));
            }
        } else if (a.length == 2) {
            if (cmd.equals("job") && a[0].equalsIgnoreCase("join")) for (Job j : Job.values()) out.add(j.key());
            if (cmd.equals("kasir") && a[0].equalsIgnoreCase("tagih")) plugin.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
            if (cmd.equals("rpjobs") && a[0].equalsIgnoreCase("deldelivery")) out.addAll(plugin.jobs().deliveries().keySet());
            if (cmd.equals("rpjobs") && a[0].equalsIgnoreCase("delkasir")) out.addAll(plugin.jobs().registers().keySet());
        }
        String last = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
