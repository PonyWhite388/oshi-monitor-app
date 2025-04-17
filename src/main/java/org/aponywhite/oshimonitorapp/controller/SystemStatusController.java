package org.aponywhite.oshimonitorapp.controller;

import lombok.RequiredArgsConstructor;
import org.aponywhite.oshimonitorapp.service.DiskInfoService;
import org.aponywhite.oshimonitorapp.service.PortMonitorKillService;
import org.aponywhite.oshimonitorapp.service.alert.CpuAlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import oshi.software.os.OSProcess;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor")
public class SystemStatusController {

    private final CpuAlertService cpuAlertService;
    private final PortMonitorKillService portMonitorKillService;
    private final DiskInfoService diskInfoService;

    @GetMapping("/getInfoAll")
    public Map<String, Object> getSystemOverview() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();

        // 告警模拟值（后续可接数据库）
        data.put("alarmsTotal", "21");
        data.put("alarmsUnAccAll", "3");
        data.put("alarmsUnAccToday", "2");
        data.put("alarmsAcc", "18");

        //  BFR 状态（判断监听 10011 端口程序资源）
        try {
            Integer pid = portMonitorKillService.getPidByPort(10011);
            if (pid != null) {
                OSProcess oldProc = portMonitorKillService.getProcessByPid(pid);
                Thread.sleep(1000);
                OSProcess newProc = portMonitorKillService.getProcessByPid(pid);

                if (oldProc != null && newProc != null) {
                    double cpu = newProc.getProcessCpuLoadBetweenTicks(oldProc) * 100;
                    double mem = newProc.getResidentSetSize() / 1024.0 / 1024.0;

                    boolean bfrOverload = cpu >= 80 || mem >= 200;
                    data.put("BFR_state", bfrOverload ? "异常" : "正常");
                } else {
                    data.put("BFR_state", "未知");
                }
            } else {
                data.put("BFR_state", "未监听");
            }
        } catch (Exception e) {
            data.put("BFR_state", "异常");
        }

        //  CPU 状态（是否进入告警）
        boolean cpuWarning = cpuAlertService.checkCpuAlert().isPresent();
        data.put("CPU_state", cpuWarning ? "异常" : "正常");

        //  磁盘使用树列表
        data.put("DiskUsageTreeList", diskInfoService.getDiskInfo());

        //  封装最终响应结构
        response.put("code", "200");
        response.put("message", "success");
        response.put("data", data);
        response.put("type", "string");

        return response;
    }
}
