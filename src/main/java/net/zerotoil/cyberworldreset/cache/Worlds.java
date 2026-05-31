package net.zerotoil.cyberworldreset.cache;

import net.zerotoil.cyberworldreset.CyberWorldReset;
import net.zerotoil.cyberworldreset.objects.WorldObject;
import net.zerotoil.cyberworldreset.utilities.RegionFileUtils.RegionCoordinate;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Worlds {

    private CyberWorldReset main;
    private HashMap<String, WorldObject> worlds = new HashMap<>();
    private Configuration config;

    public Worlds(CyberWorldReset main) {

        this.main = main;
        config = main.files().getConfig("worlds");

    }

    public void loadWorlds(boolean newWorlds) {

        long startTime = System.currentTimeMillis();

        if (!worlds.isEmpty() && !newWorlds) worlds.clear();

        if (!config.getConfigurationSection("worlds").getKeys(false).isEmpty()) {

            main.logger("&bLoading world configurations...");

            for (String worldName : config.getConfigurationSection("worlds").getKeys(false)) {

                // skips already loaded worlds
                if (worlds.containsKey(worldName) && newWorlds) continue;

                // is it an actual world?
                if (!main.worldUtils().isWorld(worldName)) {

                    main.logger(main.langUtils().getLang(
                            "&cThe world &7'" + worldName + "'&c is not an existing world! Disabling this world.",
                            "&cEl mundo &7'" + worldName + "'&c no es un mundo existente. Deshabiltando el mundo.",
                            "&cМир &7'"+ worldName + "'&c не существует! Этот мир не будет использоваться."));
                    continue;

                }

                // if default world
                if (worldName.equalsIgnoreCase(main.worldUtils().getLevelName())) {

                    main.logger(main.langUtils().getLang(
                            "&cThe world &7'" + worldName + "'&c is a default world! Disabling this world.",
                            "&cEl mundo &7'" + worldName + "'&c es un mundo predeterminado. Deshabiltando el mundo.",
                            "&cМир &7'" + worldName + "'&c - это мир по умолчанию! Этот мир не будет использоваться."));
                    continue;

                }

                // makes a new world object
                WorldObject worldObject = new WorldObject(main, worldName);
                String settings = worldName + ".settings.";

                // intervals/times
                if (isSet(settings + "time")) {

                    worldObject.setTime(config.getStringList("worlds." + settings + "time"));

                }

                // reset message
                if (isSet(settings + "message")) {

                    worldObject.setMessage(main.langUtils().convertList(config, "worlds." + worldName + ".settings.message"));

                }

                // pre-converted seed
                if (isSet(settings + "seed")) {
                    String path = "worlds." + settings + "seed";
                    if (config.isLong(path) || config.isInt(path)) {
                        worldObject.setSeed(config.getLong(path) + "");
                    } else {
                        worldObject.setSeed(config.getString(path));
                    }
                }

                // generator
                if (isSet(settings + "generator")) {
                    worldObject.setGenerator(config.getString("worlds." + settings + "generator"));
                }

                // environment (world type)
                if (isSet(settings + "environment")) {
                    worldObject.setEnvironment(config.getString("worlds." + settings + "environment"));
                }

                if (isSet(settings + "safe-world.enabled")) {

                    if (config.getBoolean("worlds." + settings + "safe-world.enabled")) {

                        worldObject.setSafeWorldEnabled(true);

                        // safe world name
                        if (isSet(settings + "safe-world.world")) {
                            worldObject.setSafeWorld(config.getString("worlds." + settings + "safe-world.world"));
                        } else {
                            worldObject.setSafeWorldEnabled(false);
                        }
                        if (isSet(settings + "safe-world.delay")) {
                            worldObject.setSafeWorldDelay(config.getLong("worlds." + settings + "safe-world.delay"));
                        } else {
                            worldObject.setSafeWorldDelay(-1);
                        }
                        if (isSet(settings + "safe-world.spawn")) {
                            worldObject.setSafeWorldSpawn(config.getString("worlds." + settings + "safe-world.spawn"));
                        } else {
                            worldObject.setSafeWorldSpawn("default");
                        }

                    }

                }

                if (isSet(settings + "warning.enabled")) {

                    if (config.getBoolean("worlds." + settings + "warning.enabled")) {

                        worldObject.setWarningEnabled(true);

                        if (isSet(settings + "warning.message")) worldObject.setWarningMessage(main.langUtils().convertList(config, "worlds." + settings + "warning.message"));

                        if (isSet(settings + "warning.time")) worldObject.setWarningTime(config.getLongList("worlds." + settings + "warning.time"));

                        if (isSet(settings + "warning.title.title")) worldObject.setWarningTitle(config.getString("worlds." + settings + "warning.title.title"));

                        if (isSet(settings + "warning.title.sub-title")) worldObject.setWarningSubtitle(config.getString("worlds." + settings + "warning.title.sub-title"));

                        if (isSet(settings + "warning.title.fade")) worldObject.setWarningTitleFade(config.getIntegerList("worlds." + settings + "warning.title.fade"));
                    }

                }

                if (isSet(settings + "commands")) {
                    worldObject.setCommands(main.langUtils().convertList(config, "worlds." + settings + "commands"));
                }

                if (isSet(settings + "commands")) {
                    List<String> commands = main.langUtils().convertList(config, "worlds." + settings + "commands");
                    List<String> initialCommands = new ArrayList<>();

                    for (String s : commands) {
                        while (s.charAt(0) == ' ') s = s.substring(1);
                        if (s.toLowerCase().startsWith("[initial")) {
                            initialCommands.add(s.replace("[initial]", ""));
                        }
                    }

                    worldObject.setCommands(commands);
                    worldObject.setInitialCommands(initialCommands);

                }

                if (isSet(settings + "time")) {
                    worldObject.setTime(main.langUtils().convertList(config, "worlds." + settings + "time"));
                }

                if (isSet(worldName + ".last-saved")) {
                    worldObject.setLastSaved(config.getBoolean("worlds." + worldName + ".last-saved"));
                }

                if (isSet(worldName + ".enabled")) {
                    worldObject.setEnabled(config.getBoolean("worlds." + worldName + ".enabled"));
                }

                worlds.put(worldName, worldObject);
                getWorld(worldName).loadTimedResets();
                loadTimedRegionGroupResets(worldName);

                main.logger("&7Loaded world &e'" + worldName + "'&7.");

            }

            main.logger("&7Loaded &e" + worlds.size() + "&7 worlds in &a" + (System.currentTimeMillis() - startTime) + "ms&7.");
            main.logger("");

        }

    }

    private void loadTimedRegionGroupResets(String worldName) {
        String path = "worlds." + worldName + ".settings.mca-region-groups";
        ConfigurationSection groups = config.getConfigurationSection(path);
        if (groups == null) return;

        for (String groupName : groups.getKeys(false)) {
            if (!config.getBoolean(path + "." + groupName + ".enabled", false)) continue;

            List<RegionCoordinate> regions = new ArrayList<>();
            for (String regionKey : config.getStringList(path + "." + groupName + ".regions")) {
                RegionCoordinate region = parseRegionKey(worldName, regionKey);
                if (region != null) regions.add(region);
            }

            if (regions.isEmpty()) {
                main.logger("&cMCA region group '" + groupName + "' for world '" + worldName + "' has no valid regions.");
                continue;
            }

            List<String> times = config.getStringList(path + "." + groupName + ".time");
            String warningPath = path + "." + groupName + ".warning";
            boolean warningEnabled = config.getBoolean(warningPath + ".enabled", false);
            List<String> warningMessage = new ArrayList<>();
            warningMessage.add("&cWarning: resetting MCA group {group} ({regions}) in {time}.");
            if (config.isSet(warningPath + ".message"))
                warningMessage = main.langUtils().convertList(config, warningPath + ".message");
            List<Long> warningTime = config.getLongList(warningPath + ".time");
            String warningTitle = null;
            String warningSubtitle = null;
            List<Integer> warningTitleFade = null;
            if (config.isSet(warningPath + ".title.title")) warningTitle = config.getString(warningPath + ".title.title");
            if (config.isSet(warningPath + ".title.sub-title")) warningSubtitle = config.getString(warningPath + ".title.sub-title");
            if (config.isSet(warningPath + ".title.fade")) warningTitleFade = config.getIntegerList(warningPath + ".title.fade");

            getWorld(worldName).loadTimedRegionGroupResets(groupName, regions, times, warningEnabled, warningMessage,
                    warningTime, warningTitle, warningSubtitle, warningTitleFade);
            main.logger("&7Loaded MCA region group timer &e'" + groupName + "' &7for world &e'" + worldName + "'&7.");
        }
    }

    private RegionCoordinate parseRegionKey(String worldName, String regionKey) {
        String[] split = regionKey.split("_");
        if (split.length != 2) {
            main.logger("&cInvalid MCA region key '" + regionKey + "' for world '" + worldName + "'. Use <regionX>_<regionZ>.");
            return null;
        }

        try {
            return new RegionCoordinate(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
        } catch (NumberFormatException e) {
            main.logger("&cInvalid MCA region key '" + regionKey + "' for world '" + worldName + "'. Use numeric region coordinates.");
            return null;
        }
    }

    public void createWorld(String worldName, Player sender) {

        ConfigurationSection cS = config.getConfigurationSection("worlds");
        String s = worldName + ".settings.";
        String safe = s + "safe-world.";
        String warn = s + "warning.";
        cS.set(worldName + ".enabled", false);
        cS.set(worldName + ".last-saved", false);
        cS.set(s + "time", new String[0]);
        cS.set(s + "message", "World {world} has been reset!");
        cS.set(s + "seed", "DEFAULT");
        cS.set(s + "mca-region-groups", new HashMap<>());
        cS.set(safe + "enabled", false);
        cS.set(safe + "world", "");
        cS.set(safe + "delay", 5);
        cS.set(safe + "spawn", "DEFAULT");
        cS.set(warn + "enabled", false);
        cS.set(warn + "warning", "&cWarning: resetting the world {world} in {time}.");
        cS.set(warn + "time", new int[]{300, 60, 30, 10, 3, 2, 1});
        cS.set(s + "commands", new String[0]);
        try {
            config.set("worlds", cS);
            main.files().get("worlds").saveConfig();
            loadWorlds(true);
            main.lang().getMsg("setup-created").send(sender, true, new String[]{"world"}, new String[]{worldName});
        } catch (Exception e) {
            e.printStackTrace();
            main.lang().getMsg("world-create-failed").send(sender, true, new String[]{"world"}, new String[]{worldName});
        }

    }

    public void cancelTimers() {
        if (worlds.isEmpty()) return;
        for (WorldObject worldObject : worlds.values()) {
            worldObject.cancelTimers();
        }
    }

    public boolean isWorldResetting() {
        if (worlds.isEmpty()) return false;
        for (WorldObject worldObject : worlds.values()) {
            if (worldObject.isResetting()) return true;
        }
        return false;
    }

    private boolean isSet(String location) {
        return config.isSet("worlds." + location);
    }

    public WorldObject getWorld(String name) {
        return worlds.get(name);
    }
    public HashMap<String, WorldObject> getWorlds() {
        return worlds;
    }

}
