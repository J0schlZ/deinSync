package bz.dcr.deinsync;

import bz.dcr.bedrock.spigot.BedRockPlugin;
import bz.dcr.dccore.commons.db.Mongo;
import bz.dcr.dccore.commons.db.codec.UUIDCodec;
import bz.dcr.dccore.commons.db.codec.UUIDCodecProvider;
import bz.dcr.dccore.db.codec.ItemStackCodec;
import bz.dcr.dccore.db.codec.ItemStackCodecProvider;
import bz.dcr.dccore.db.codec.PotionEffectCodec;
import bz.dcr.dccore.db.codec.PotionEffectCodecProvider;
import bz.dcr.deinsync.cmd.DeinSyncCommand;
import bz.dcr.deinsync.config.ConfigKey;
import bz.dcr.deinsync.db.codec.PlayerInventoryCodec;
import bz.dcr.deinsync.db.codec.PlayerInventoryCodecProvider;
import bz.dcr.deinsync.db.codec.PlayerProfileCodecProvider;
import bz.dcr.deinsync.listener.JoinListener;
import bz.dcr.deinsync.listener.QuitListener;
import bz.dcr.deinsync.sync.PersistenceManager;
import bz.dcr.deinsync.sync.SyncManager;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class DeinSyncPlugin extends JavaPlugin {

    private Mongo mongoDB;
    private SyncManager syncManager;
    private PersistenceManager persistenceManager;
    private BedRockPlugin bedRock;


    @Override
    public void onEnable() {
        loadBedRock();
        loadConfig();
        setupDatabase();

        syncManager = new SyncManager(this);
        syncManager.initSubscribers();
        persistenceManager = new PersistenceManager(this);
        persistenceManager.addWorkers(getConfig().getInt(ConfigKey.DEINSYNC_SAVE_WORKER_THREADS));

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new QuitListener(this), this);

        getCommand("deinsync").setExecutor(new DeinSyncCommand(this));
    }

    @Override
    public void onDisable() {
        // Save profiles of all remaining players
        Bukkit.getOnlinePlayers().forEach(player -> getSyncManager().savePlayer(player));

        // Save remaining profiles and wait for workers to finish
        if(persistenceManager != null) {
            persistenceManager.close();
        }

        // Disconnect from database
        if(mongoDB != null) {
            mongoDB.disconnect();
        }
    }


    private void loadBedRock() {
        final Plugin bedRockPlugin = getServer().getPluginManager().getPlugin("bedRock");

        // bedRock is not installed
        if(bedRockPlugin == null) {
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().warning("! Could not find bedRock! Disabling deinSync... !");
            getLogger().warning("! INVENTORIES WILL NOT BE SYNCHRONIZED          !");
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bedRock = (BedRockPlugin) bedRockPlugin;
    }

    private void loadConfig() {
        getConfig().addDefault(ConfigKey.DEINSYNC_SERVER_ID, "server_" + getServer().getPort());
        getConfig().addDefault(ConfigKey.DEINSYNC_SERVER_GROUP, "main");
        getConfig().addDefault(ConfigKey.MONGODB_URI, "mongodb://127.0.0.1:27017/" + getName().toLowerCase());
        getConfig().addDefault(ConfigKey.DEINSYNC_SAVE_WORKER_THREADS, 2);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void setupDatabase() {
        final CodecRegistry customRegistry = CodecRegistries.fromProviders(
                new UUIDCodecProvider(),
                new ItemStackCodecProvider(),
                new PotionEffectCodecProvider(),
                new PlayerInventoryCodecProvider(new ItemStackCodec()),
                new PlayerProfileCodecProvider(new UUIDCodec(), new PlayerInventoryCodec(new ItemStackCodec()), new PotionEffectCodec())
        );
        final CodecRegistry registry = CodecRegistries.fromRegistries(MongoClient.getDefaultCodecRegistry(), customRegistry);

        final MongoClientURI uri = new MongoClientURI(
                getConfig().getString(ConfigKey.MONGODB_URI),
                MongoClientOptions.builder().codecRegistry(registry)
        );

        try {
            mongoDB = new Mongo(uri, uri.getDatabase());
            mongoDB.connect();
        } catch (Exception ex) {
            ex.printStackTrace();
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().warning("! Could not connect to MongoDB! Disabling deinSync... !");
            getLogger().warning("! INVENTORIES WILL NOT BE SYNCHRONIZED                !");
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getServer().getPluginManager().disablePlugin(this);
        }
    }


    public BedRockPlugin getBedRock() {
        return bedRock;
    }

    public Mongo getMongo() {
        return mongoDB;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public PersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

}
