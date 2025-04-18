package org.aponywhite.oshimonitorapp.service;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

@Service
public class CpuLoadMetricsService {

    private final SystemInfo si = new SystemInfo();
    public double[] getCpuLoad() {
        HardwareAbstractionLayer hal = si.getHardware();

        CentralProcessor processor = hal.getProcessor();

        double[] CpuLoad = processor.getProcessorCpuLoad(1500);

        return CpuLoad;
    }

    public double getTotalCpuUsagePercent() {
        CentralProcessor processor = si.getHardware().getProcessor();


        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(1000); // 等 1 秒获取平均负载
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);

        return cpuLoad * 100.0;


    }


}
