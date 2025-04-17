package org.aponywhite.oshimonitorapp.service;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OperatingSystem.ProcessSorting;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcessMetricsService {

    private final SystemInfo systemInfo = new SystemInfo();

    public List<Map<String, Object>> getProcessCpuAndMemory() {
        OperatingSystem os = systemInfo.getOperatingSystem();

        // 第一次采样（按 PID 排序）
        List<OSProcess> prevList = os.getProcesses(null, ProcessSorting.PID_ASC, 0);
        Map<Integer, OSProcess> prevMap = new HashMap<>();
        for (OSProcess p : prevList) {
            prevMap.put(p.getProcessID(), p);
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 第二次采样（按 CPU 使用率排序）
        List<OSProcess> newList = os.getProcesses(null, ProcessSorting.CPU_DESC, 0);
        List<Map<String, Object>> result = new ArrayList<>();

        for (OSProcess proc : newList) {
            int pid = proc.getProcessID();
            if (pid == 0) continue; // 跳过 Idle

            OSProcess oldProc = prevMap.get(pid);
            if (oldProc == null) continue;

            double cpu_Load = proc.getProcessCpuLoadBetweenTicks(oldProc) * 100;
            if (Double.isNaN(cpu_Load) || cpu_Load < 0) cpu_Load = 0;

            long memoryBytes = proc.getResidentSetSize();
            double memoryMB = memoryBytes / 1024.0 / 1024.0;

            Map<String, Object> map = new HashMap<>();
            map.put("pid", pid);
            map.put("name", proc.getName());
            map.put("cpuUsage", String.format("%.2f", cpu_Load));
            map.put("memoryMB", String.format("%.2f", memoryMB));
            result.add(map);

            if (result.size() >= 10) break;
        }

        return result;
    }
}
