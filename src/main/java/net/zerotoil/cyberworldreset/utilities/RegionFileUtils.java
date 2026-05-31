package net.zerotoil.cyberworldreset.utilities;

import net.zerotoil.cyberworldreset.CyberWorldReset;
import net.zerotoil.cyberworldreset.objects.WorldObject;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RegionFileUtils {

    private static final String[] REGION_FOLDERS = {"region", "entities", "poi"};

    private final CyberWorldReset main;
    private boolean mcaOperationRunning;
    private final Map<String, List<UUID>> returnPlayers = new HashMap<>();

    public RegionFileUtils(CyberWorldReset main) {
        this.main = main;
    }

    public static class RegionCoordinate {
        private final int x;
        private final int z;

        public RegionCoordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }
    }

    public boolean saveRegion(Player sender, String worldName, int regionX, int regionZ) {
        String operationKey = regionOperationKey(worldName, null, Collections.singletonList(new RegionCoordinate(regionX, regionZ)));
        if (mcaOperationRunning) {
            main.lang().getMsg("region-already-resetting").send(sender, true, regionPlaceholders(), regionValues(worldName, regionX, regionZ));
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            main.lang().getMsg("world-not-exist").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return false;
        }

        if (worldName.equalsIgnoreCase(main.worldUtils().getLevelName())) {
            main.lang().getMsg("default-world-fail").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return false;
        }

        WorldObject setup = main.worlds().getWorld(worldName);
        if (!preparePlayers(sender, setup, world, operationKey)) return false;

        main.lang().getMsg("saving-region").send(sender, true, regionPlaceholders(), regionValues(worldName, regionX, regionZ));
        mcaOperationRunning = true;
        world.save();
        File worldFolder = world.getWorldFolder();
        World.Environment environment = world.getEnvironment();
        String generator = setup == null ? null : setup.getGenerator();

        if (!Bukkit.unloadWorld(world, false)) {
            finishRegionOperationFailure(sender, worldName, Collections.singletonList(new RegionCoordinate(regionX, regionZ)), operationKey, setup, environment, generator, "region-save-failed");
            main.lang().getMsg("unload-failed").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
            File backupFolder = null;
            try {
                backupFolder = createBackupFolder(worldName, regionX, regionZ);
                main.logger("&7Saving MCA backup to &e" + backupFolder.getPath() + "&7.");
                int savedFiles = copyRegionFiles(worldFolder, backupFolder, regionX, regionZ);
                if (savedFiles == 0) FileUtils.deleteQuietly(backupFolder);
                Bukkit.getScheduler().runTask(main, () -> {
                    if (savedFiles == 0) {
                        finishRegionOperationCleanup(worldName, operationKey, setup, environment, generator);
                        main.lang().getMsg("region-save-empty").send(sender, true, regionPlaceholders(), regionValues(worldName, regionX, regionZ));
                    } else {
                        finishRegionOperationCleanup(worldName, operationKey, setup, environment, generator);
                        main.lang().getMsg("region-save-success").send(sender, true, regionPlaceholders(), regionValues(worldName, regionX, regionZ));
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(main, () ->
                        finishRegionOperationFailure(sender, worldName, Collections.singletonList(new RegionCoordinate(regionX, regionZ)), operationKey, setup, environment, generator, "region-save-failed"));
            }
        });
        return true;
    }

    public boolean resetRegion(Player sender, String worldName, int regionX, int regionZ) {
        return resetRegions(sender, worldName, Collections.singletonList(new RegionCoordinate(regionX, regionZ)), null);
    }

    public boolean resetRegions(Player sender, String worldName, List<RegionCoordinate> regions, String groupName) {
        if (regions == null || regions.isEmpty()) return false;

        String operationKey = regionOperationKey(worldName, groupName, regions);
        if (mcaOperationRunning) {
            main.lang().getMsg("region-already-resetting").send(sender, true, regionPlaceholders(), regionValues(worldName, regions.get(0).getX(), regions.get(0).getZ()));
            return false;
        }

        Map<RegionCoordinate, File> latestBackups = new HashMap<>();
        for (RegionCoordinate region : regions) {
            File latestBackup = getLatestBackup(worldName, region.getX(), region.getZ());
            if (latestBackup == null) {
                main.lang().getMsg("region-no-saves").send(sender, true, regionPlaceholders(), regionValues(worldName, region.getX(), region.getZ()));
                return false;
            }
            latestBackups.put(region, latestBackup);
        }

        World currentWorld = Bukkit.getWorld(worldName);
        if (currentWorld == null) {
            main.lang().getMsg("world-not-exist").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return false;
        }

        if (worldName.equalsIgnoreCase(main.worldUtils().getLevelName())) {
            main.lang().getMsg("default-world-fail").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return false;
        }

        WorldObject setup = main.worlds().getWorld(worldName);
        if (!preparePlayers(sender, setup, currentWorld, operationKey)) return false;

        mcaOperationRunning = true;
        File worldFolder = currentWorld.getWorldFolder();
        World.Environment environment = currentWorld.getEnvironment();
        String generator = setup == null ? null : setup.getGenerator();
        currentWorld.save();

        if (!Bukkit.unloadWorld(currentWorld, false)) {
            finishRegionOperationFailure(sender, worldName, regions, operationKey, setup, environment, generator, "region-reset-failed");
            main.lang().getMsg("unload-failed").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(main, () -> {
            try {
                for (RegionCoordinate region : regions) {
                    File latestBackup = latestBackups.get(region);
                    main.logger("&7Restoring MCA backup from &e" + latestBackup.getPath() + "&7.");
                    restoreRegionFiles(worldFolder, latestBackup, region.getX(), region.getZ());
                }
                Bukkit.getScheduler().runTask(main, () -> finishResetSuccess(sender, worldName, regions, operationKey, setup, environment, generator));
            } catch (IOException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(main, () -> finishRegionOperationFailure(sender, worldName, regions, operationKey, setup, environment, generator, "region-reset-failed"));
            }
        });
        return true;
    }

    public boolean isResetting() {
        return mcaOperationRunning;
    }

    private boolean preparePlayers(Player sender, WorldObject setup, World currentWorld, String key) {
        if (setup == null || !setup.isSafeWorldEnabled()) {
            main.onJoin().setServerOpen(false);
            for (Player player : new ArrayList<>(currentWorld.getPlayers())) {
                player.kickPlayer(main.lang().getMsg("kick-reason").toString(false).replace("{world}", currentWorld.getName()));
            }
            return true;
        }

        World safeWorld = Bukkit.getWorld(setup.getSafeWorld());
        if (safeWorld == null || safeWorld.getName().equalsIgnoreCase(currentWorld.getName())) {
            main.lang().getMsg("invalid-safeworld").send(sender, true,
                    new String[]{"world", "safeWorld"}, new String[]{currentWorld.getName(), setup.getSafeWorld()});
            return false;
        }

        List<UUID> players = new ArrayList<>();
        returnPlayers.put(key, players);
        for (Player player : new ArrayList<>(currentWorld.getPlayers())) {
            players.add(player.getUniqueId());
            main.lang().getMsg("teleporting-safe-world").send(player, true,
                    new String[]{"world", "safeWorld"}, new String[]{currentWorld.getName(), safeWorld.getName()});
            if (!setup.getSafeWorldSpawn().equalsIgnoreCase("default")) {
                player.teleport(main.worldUtils().getLocationFromString(safeWorld.getName(), setup.getSafeWorldSpawn()));
            } else {
                player.teleport(safeWorld.getSpawnLocation());
            }
            main.lang().getMsg("teleported-safe-world").send(player, true,
                    new String[]{"world", "safeWorld"}, new String[]{currentWorld.getName(), safeWorld.getName()});
        }
        main.onWorldChange().addClosedWorld(currentWorld.getName());
        return true;
    }

    private void finishResetSuccess(Player sender, String worldName, List<RegionCoordinate> regions, String operationKey,
                                    WorldObject setup, World.Environment environment, String generator) {
        WorldCreator creator = createWorldCreator(worldName, environment, generator);
        World world = creator.createWorld();
        if (world == null) {
            finishRegionOperationFailure(sender, worldName, regions, operationKey, setup, environment, generator, "region-reset-failed");
            main.lang().getMsg("world-create-failed").send(sender, true, new String[]{"world"}, new String[]{worldName});
            return;
        }

        main.onWorldChange().removeClosedWorld(worldName);
        if (!main.onJoin().isServerOpen()) main.onJoin().setServerOpen(true);
        for (RegionCoordinate region : regions) {
            main.lang().getMsg("region-reset-success").send(sender, true, regionPlaceholders(), regionValues(worldName, region.getX(), region.getZ()));
        }

        if (setup != null && setup.isSafeWorldEnabled() && setup.getSafeWorldDelay() != -1) {
            Bukkit.getScheduler().runTaskLater(main, () -> teleportPlayersBack(world, operationKey), 20L * setup.getSafeWorldDelay());
        } else {
            returnPlayers.remove(operationKey);
        }

        mcaOperationRunning = false;
    }

    private void finishRegionOperationFailure(Player sender, String worldName, List<RegionCoordinate> regions, String operationKey,
                                              WorldObject setup, World.Environment environment, String generator, String messageKey) {
        finishRegionOperationCleanup(worldName, operationKey, setup, environment, generator);
        for (RegionCoordinate region : regions) {
            main.lang().getMsg(messageKey).send(sender, true, regionPlaceholders(), regionValues(worldName, region.getX(), region.getZ()));
        }
    }

    private void finishRegionOperationCleanup(String worldName, String key, WorldObject setup,
                                              World.Environment environment, String generator) {
        if (Bukkit.getWorld(worldName) == null) {
            createWorldCreator(worldName, environment, generator).createWorld();
        }
        main.onWorldChange().removeClosedWorld(worldName);
        if (!main.onJoin().isServerOpen()) main.onJoin().setServerOpen(true);
        World world = Bukkit.getWorld(worldName);
        if (setup != null && setup.isSafeWorldEnabled() && setup.getSafeWorldDelay() != -1 && world != null) {
            Bukkit.getScheduler().runTaskLater(main, () -> teleportPlayersBack(world, key), 20L * setup.getSafeWorldDelay());
        } else {
            returnPlayers.remove(key);
        }
        mcaOperationRunning = false;
    }

    private WorldCreator createWorldCreator(String worldName, World.Environment environment, String generator) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        if (generator != null && !generator.equalsIgnoreCase("default")) {
            try {
                creator.generator(generator);
            } catch (Exception e) {
                main.logger("&cFailed to set the generator " + generator + ". Please check the name. Using default generator.");
            }
        }
        return creator;
    }

    private void teleportPlayersBack(World world, String key) {
        List<UUID> players = returnPlayers.remove(key);
        if (players == null) return;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            main.lang().getMsg("teleporting-back").send(player, true, new String[]{"world", "safeWorld"}, new String[]{world.getName(), ""});
            player.teleport(world.getSpawnLocation());
            main.lang().getMsg("teleported-back").send(player, true, new String[]{"world", "safeWorld"}, new String[]{world.getName(), ""});
        }
    }

    private int copyRegionFiles(File worldFolder, File backupFolder, int regionX, int regionZ) throws IOException {
        int copied = 0;
        for (String folder : REGION_FOLDERS) {
            File source = regionFile(worldFolder, folder, regionX, regionZ);
            if (!source.exists()) continue;
            File target = new File(new File(backupFolder, folder), source.getName());
            FileUtils.forceMkdirParent(target);
            FileUtils.copyFile(source, target);
            copied++;
        }
        return copied;
    }

    private void restoreRegionFiles(File worldFolder, File backupFolder, int regionX, int regionZ) throws IOException {
        for (String folder : REGION_FOLDERS) {
            File target = regionFile(worldFolder, folder, regionX, regionZ);
            File source = regionFile(backupFolder, folder, regionX, regionZ);
            if (target.exists()) FileUtils.deleteQuietly(target);
            if (!source.exists()) continue;
            FileUtils.forceMkdirParent(target);
            FileUtils.copyFile(source, target);
        }
    }

    private File createBackupFolder(String worldName, int regionX, int regionZ) throws IOException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        File folder = new File(getRegionBackupRoot(worldName, regionX, regionZ), dtf.format(LocalDateTime.now()));
        FileUtils.forceMkdir(folder);
        return folder;
    }

    private File getLatestBackup(String worldName, int regionX, int regionZ) {
        File root = getRegionBackupRoot(worldName, regionX, regionZ);
        File[] files = root.listFiles(File::isDirectory);
        if (files == null || files.length == 0) return null;
        File latest = files[0];
        for (File file : files) {
            if (file.getName().compareTo(latest.getName()) > 0) latest = file;
        }
        return latest;
    }

    private File getRegionBackupRoot(String worldName, int regionX, int regionZ) {
        return new File(main.getDataFolder(), "saved_regions" + File.separator + worldName + File.separator + regionKey("", regionX, regionZ));
    }

    private File regionFile(File worldFolder, String folder, int regionX, int regionZ) {
        return new File(new File(worldFolder, folder), regionFileName(regionX, regionZ));
    }

    private String regionFileName(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + ".mca";
    }

    private String regionKey(String worldName, int regionX, int regionZ) {
        if (worldName == null || worldName.equals("")) return regionX + "_" + regionZ;
        return worldName + "_" + regionX + "_" + regionZ;
    }

    private String regionOperationKey(String worldName, String groupName, List<RegionCoordinate> regions) {
        if (groupName != null && !groupName.equals("")) return worldName + "_mca_group_" + groupName;
        RegionCoordinate first = regions.get(0);
        return regionKey(worldName, first.getX(), first.getZ());
    }

    private String[] regionPlaceholders() {
        return new String[]{"world", "regionX", "regionZ", "file"};
    }

    private String[] regionValues(String worldName, int regionX, int regionZ) {
        return new String[]{worldName, String.valueOf(regionX), String.valueOf(regionZ), regionFileName(regionX, regionZ)};
    }

}
