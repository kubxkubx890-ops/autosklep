package pl.autosklep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    /** Komenda otwierajaca sklep (bez ukosnika). */
    public String command = "sklep";
    /** Ile slotow (z 36) musi byc miedzia, zeby uznac ekwipunek za pelny. */
    public int minCopperSlots = 36;
    /** Przerwa miedzy kliknieciami w tickach (0 = jak najszybciej). */
    public int clickDelay = 0;
    /** Ile razy kliknac w miedz przed zamknieciem sklepu. */
    public int copperClicks = 1;
    /** Max czas oczekiwania na menu w tickach (20 = 1 s). */
    public int timeoutTicks = 60;
    /** Przerwa po zamknieciu sklepu w tickach. */
    public int cooldownTicks = 20;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("autosklep.json");
    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) instance = c;
            } else {
                save();
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception ignored) {}
    }
}
