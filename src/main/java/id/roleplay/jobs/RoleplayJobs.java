package id.roleplay.jobs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class RoleplayJobs extends JavaPlugin implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private Economy economy;
    private JobManager jobs;
    private CourierService courier;
    private CashierService cashier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Tidak ada plugin ekonomi (Vault + EssentialsX/dll). Plugin dimatikan.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();

        jobs = new JobManager(this);
        courier = new CourierService(this);
        cashier = new CashierService(this);

        Commands cmds = new Commands(this);
        for (String name : new String[]{"job", "kurir", "kasir", "rpjobs"}) {
            getCommand(name).setExecutor(cmds);
            getCommand(name).setTabCompleter(cmds);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (jobs != null) jobs.save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        courier.cancel(e.getPlayer().getUniqueId(), true);
        cashier.clear(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        courier.cancel(e.getPlayer().getUniqueId(), false);
    }

    public Economy economy() { return economy; }
    public JobManager jobs() { return jobs; }
    public CourierService courier() { return courier; }
    public CashierService cashier() { return cashier; }

    public void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(getConfig().getString("prefix", "") + miniMessage));
    }

    public String fmt(double amount) {
        return economy.format(amount);
    }
}
