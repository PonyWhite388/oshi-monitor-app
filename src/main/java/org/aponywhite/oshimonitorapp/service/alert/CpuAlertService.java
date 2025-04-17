package org.aponywhite.oshimonitorapp.service.alert;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CpuAlertService {

    private static final Logger log = LoggerFactory.getLogger(CpuAlertService.class);
    private static final double ALERT_THRESHOLD = 90.0;

    private final SystemInfo si = new SystemInfo();

    public Optional<Map<String, Object>> checkCpuAlert() {
        CentralProcessor processor = si.getHardware().getProcessor();

        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double usage = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;

        Map<String, Object> alarm = new HashMap<>();
        alarm.put("message", String.format("当前 CPU 使用率：%.2f%%", usage));

        if (usage >= ALERT_THRESHOLD) {
            alarm.put("type", "warning");
            log.warn("CPU 使用率过高！{}", alarm.get("message"));
            return Optional.of(alarm);
        }

        // 只记录 info，不推送到前端
        log.info("当前 CPU 正常，使用率：{}%", String.format("%.2f", usage));
        return Optional.empty();

    }

    public Map<String, Object> startupSuccessMessage() {
        Map<String, Object> alarm = new HashMap<>();
        alarm.put("type", "success");
        alarm.put("message", "服务启动成功，系统监控已就绪。");
        log.info("{}", alarm.get("message"));
        return alarm;
    }
}
