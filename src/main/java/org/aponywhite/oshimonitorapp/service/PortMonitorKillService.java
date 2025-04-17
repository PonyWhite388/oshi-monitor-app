package org.aponywhite.oshimonitorapp.service;

import javax.annotation.PostConstruct;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PortMonitorKillService {

    private static final Logger log = LoggerFactory.getLogger(PortMonitorKillService.class);
    private static final int PORT = 10011;
    private static final int STABLE_SECONDS = 3600;
    private static final double STABILITY_THRESHOLD = 0.5;

    private final SystemInfo systemInfo = new SystemInfo();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private final List<Double> cpuHistory = new LinkedList<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::monitorCpuStability, 0, 1, TimeUnit.SECONDS);
    }

    public Integer getPidByPort(int port) {
        try {
            String cmd = "bash -c \"netstat -nlp | grep :" + port + " | awk '{print $7}' | cut -d'/' -f1\"";
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String pidStr = reader.readLine();
            if (pidStr != null && !pidStr.trim().isEmpty()) {
                return Integer.parseInt(pidStr.trim());
            }
        } catch (Exception e) {
            log.error("无法获取 PID: {}", e.getMessage());
        }
        return null;
    }

    public OSProcess getProcessByPid(int pid) {
        return os.getProcess(pid);
    }

    private void monitorCpuStability() {
        try {
            Integer pid = getPidByPort(PORT);
            if (pid == null) {
                cpuHistory.clear();
                return;
            }

            OSProcess oldProc = os.getProcess(pid);
            Thread.sleep(2000);
            OSProcess newProc = os.getProcess(pid);

            if (oldProc == null || newProc == null) {
                cpuHistory.clear();
                return;
            }

            double cpu = newProc.getProcessCpuLoadBetweenTicks(oldProc) * 100;
            cpu = Math.round(cpu * 100.0) / 100.0;

            cpuHistory.add(cpu);
            if (cpuHistory.size() > STABLE_SECONDS) {
                cpuHistory.remove(0);
            }

            if (cpuHistory.size() == STABLE_SECONDS) {
                double max = cpuHistory.stream().mapToDouble(d -> d).max().orElse(0);
                double min = cpuHistory.stream().mapToDouble(d -> d).min().orElse(0);

                if ((max - min) < STABILITY_THRESHOLD) {
                    log.warn("CPU 占用稳定 {} 秒 ➜ 执行 stopdotnet.sh", STABLE_SECONDS);
                    executeStopScript();
                    cpuHistory.clear();
                }
            }

        } catch (Exception e) {
            log.error("监控执行失败: {}", e.getMessage());
        }
    }

    private void executeStopScript() {
        try {
            Process stop = Runtime.getRuntime().exec("bash ./stopdotnet.sh");
            int exitCode = stop.waitFor();
            if (exitCode == 0) {
                log.info("stopdotnet.sh 执行成功");
            } else {
                log.error("stopdotnet.sh 执行失败，退出码: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("执行脚本失败: {}", e.getMessage());
        }
    }
}
