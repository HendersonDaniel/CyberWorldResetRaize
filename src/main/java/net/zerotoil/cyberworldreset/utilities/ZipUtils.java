package net.zerotoil.cyberworldreset.utilities;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ExcludeFileFilter;
import net.lingala.zip4j.model.ZipParameters;
import net.zerotoil.cyberworldreset.CyberWorldReset;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ZipUtils {

    private final CyberWorldReset main;

    public ZipUtils(CyberWorldReset main) {
        this.main = main;
    }

    public File getLastModified(String world) {
        File directory = new File(main.getDataFolder(),"saved_worlds");
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) return null;

        List<File> targetFiles = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile() || file.getName().length() <= 29) continue;
            if (file.getName().substring(0, file.getName().length() - 29).equalsIgnoreCase(world))
                targetFiles.add(file);
        }

        if (targetFiles.isEmpty()) return null;
        File lastModifiedFile = targetFiles.get(0);
        for (int i = 1; i < targetFiles.size(); i++)
            if (lastModifiedFile.lastModified() < targetFiles.get(i).lastModified())
                lastModifiedFile = targetFiles.get(i);

        return lastModifiedFile;
    }

    public void zip(String world) throws IOException {
        zip(world, new File(Bukkit.getWorldContainer(), world));
    }

    public void zip(String world, File worldFolder) throws IOException {
        ArrayList<File> filesToExclude = new ArrayList<>();

        filesToExclude.add(new File(worldFolder, "session.lock"));
        filesToExclude.add(new File(worldFolder, "uid.dat"));

        ExcludeFileFilter exclude = filesToExclude::contains;
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setExcludeFileFilter(exclude);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        String date = world + "_save_" + dtf.format(now).replace(" ", "_")
                .replace("/", "-").replace(":", "-");
        ZipFile zipFile = new ZipFile(main.getDataFolder() + File.separator + "saved_worlds" + File.separator + date + ".zip");
        zipFile.addFolder(worldFolder, zipParameters);
    }

    public void unZip(File zipFile) throws IOException {
        new ZipFile(zipFile).extractAll(String.valueOf(Bukkit.getWorldContainer()));
    }

    public void unZip(File zipFile, File targetFolder) throws IOException {
        File tempFolder = Files.createTempDirectory(main.getDataFolder().toPath(), targetFolder.getName() + "-restore-").toFile();
        try {
            new ZipFile(zipFile).extractAll(tempFolder.getAbsolutePath());
            File restoredRoot = getRestoredRoot(tempFolder);
            if (targetFolder.exists()) FileUtils.deleteDirectory(targetFolder);
            File parent = targetFolder.getParentFile();
            if (parent != null) FileUtils.forceMkdir(parent);
            if (restoredRoot.equals(tempFolder)) {
                FileUtils.forceMkdir(targetFolder);
                File[] files = tempFolder.listFiles(file -> !file.getName().equals("__MACOSX"));
                if (files != null) {
                    for (File file : files) FileUtils.moveToDirectory(file, targetFolder, true);
                }
            } else {
                FileUtils.moveDirectory(restoredRoot, targetFolder);
            }
        } finally {
            FileUtils.deleteQuietly(tempFolder);
        }
    }

    private File getRestoredRoot(File tempFolder) {
        File[] files = tempFolder.listFiles(file -> !file.getName().equals("__MACOSX"));
        if (files != null && files.length == 1 && files[0].isDirectory()) return files[0];
        return tempFolder;
    }
}
