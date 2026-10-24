package id.roleplay.jobs;

public enum Job {
    KURIR("kurir", "Kurir Paket"),
    KASIR("kasir", "Kasir");
    // Tambah job baru di sini, lalu buat service-nya.

    private final String key;
    private final String display;

    Job(String key, String display) { this.key = key; this.display = display; }

    public String key() { return key; }
    public String display() { return display; }

    public static Job from(String input) {
        if (input == null) return null;
        for (Job j : values()) {
            if (j.key.equalsIgnoreCase(input) || j.name().equalsIgnoreCase(input)) return j;
        }
        return null;
    }
}
