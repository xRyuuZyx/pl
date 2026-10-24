package id.roleplay.jobs;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class JobManager {
    private final RoleplayJobs plugin;
    private final File file;
    private final Map<UUID, Job> jobs = new HashMap<>();
    private final Map<String, Location> deliveries = new LinkedHashMap<>();
    private final Map<String, Location> registers = new LinkedHashMap<>();

    public JobManager(RoleplayJobs plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        jobs.clear(); deliveries.clear(); registers.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection js = data.getConfigurationSection("jobs");
        if (js != null) for (String k : js.getKeys(false)) {
            Job j = Job.from(js.getString(k));
            if (j != null) jobs.put(UUID.fromString(k), j);
        }
        readLocations(data.getConfigurationSection("deliveries"), deliveries);
        readLocations(data.getConfigurationSection("registers"), registers);
    }

    private void readLocations(ConfigurationSection s, Map<String, Location> target) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            Location l = s.getLocation(k);
            if (l != null) target.put(k, l);
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        jobs.forEach((id, j) -> data.set("jobs." + id, j.key()));
        deliveries.forEach((n, l) -> data.set("deliveries." + n, l));
        registers.forEach((n, l) -> data.set("registers." + n, l));
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan data.yml: " + e.getMessage());
        }
    }

    public Job getJob(UUID id) { return jobs.get(id); }

    public boolean hasJob(UUID id, Job job) { return jobs.get(id) == job; }

    public void setJob(UUID id, Job job) {
        plugin.courier().cancel(id, true);
        plugin.cashier().clear(id);
        if (job == null) jobs.remove(id); else jobs.put(id, job);
        save();
    }

    public Map<String, Location> deliveries() { return deliveries; }
    public Map<String, Location> registers() { return registers; }
}
