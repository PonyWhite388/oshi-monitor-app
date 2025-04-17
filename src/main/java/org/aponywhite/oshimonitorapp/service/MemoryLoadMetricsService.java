package org.aponywhite.oshimonitorapp.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

@Service
public class MemoryLoadMetricsService {

    private final SystemInfo si = new SystemInfo();

    public double[] getMemoryUsage() {
        HardwareAbstractionLayer hal = si.getHardware();
        GlobalMemory memory = hal.getMemory();

        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;

        double usedMB = used / 1024.0 / 1024.0;
        double totalMB = total / 1024.0 / 1024.0;
        double percent = (used * 100.0) / total;

        return new double[] {usedMB, totalMB, percent};

    }
}
