package org.aponywhite.oshimonitorapp.controller;

import org.aponywhite.oshimonitorapp.common.R;
import org.aponywhite.oshimonitorapp.service.CpuLoadMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RequestMapping("/metrics")
@RestController
public class CpuLoadController {

    @Autowired
    private CpuLoadMetricsService cpuloadMetricsService;


    @GetMapping("/CpuLoad")
    public R<double[]> getCpuLoad() {
        double[] cpuLoad = cpuloadMetricsService.getCpuLoad();

        return R.ok(cpuLoad);
    }

    @GetMapping("/CpuUsage")
    public R<Double> getCpuUsagePercent() {
        double usagePercent = cpuloadMetricsService.getTotalCpuUsagePercent();
        return R.ok(usagePercent);
    }

}
