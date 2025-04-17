package org.aponywhite.oshimonitorapp.service;

import oshi.SystemInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiskInfoService {

    private final SystemInfo si = new SystemInfo();

    public List<Map<String, Object>> getDiskInfo() {
        OperatingSystem os = si.getOperatingSystem();
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();

        List<Map<String, Object>> result = new ArrayList<>();

        for (OSFileStore fs : fileStores) {
            Map<String, Object> disk = new HashMap<>();
            long total = fs.getTotalSpace();
            long usable = fs.getUsableSpace();
            long used = total - usable;

            disk.put("name", fs.getName());
            disk.put("mount", fs.getMount());
            disk.put("type", fs.getType());
            disk.put("totalGB", formatGB(total));
            disk.put("usedGB", formatGB(used));
            disk.put("freeGB", formatGB(usable));
            disk.put("usagePercent", total == 0 ? 0.0 : String.format("%.2f", (used * 100.0) / total));

            result.add(disk);
        }

        return result;
    }

    private double formatGB(long bytes) {
        return Math.round((bytes / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
    }
}