package org.aponywhite.oshimonitorapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/BFR")
public class ScriptExecController {

    private final Logger log = LoggerFactory.getLogger(ScriptExecController.class);

    private static final List<String> ALLOWED_SCRIPTS = Arrays.asList("start.sh", "stopdotnet.sh");
    private static final String SCRIPT_BASE_PATH = "./BSMesWare/NET5.0/BFR_V3.0_NET5.0/";

    /**
     * RESTful 风格执行脚本接口：
     * 例：GET /BFR/exec/start.sh
     */
    @GetMapping("/exec/{script}")
    public String executeScript(@PathVariable("script") String scriptName) {
        return executeScriptInternal(scriptName);
    }

    /**
     * 重启脚本接口：GET /BFR/restart
     */
    @GetMapping("/restart")
    public String restartScript() {
        StringBuilder logResult = new StringBuilder();

        // Step 1: 停止服务
        logResult.append("[1] 正在执行 stopdotnet.sh...\n");
        String stopResult = executeScriptInternal("stopdotnet.sh");
        logResult.append(stopResult).append("\n");

        // Step 2: 等待 2 秒
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 3: 启动服务
        logResult.append("[2] 正在执行 start.sh...\n");
        String startResult = executeScriptInternal("start.sh");
        logResult.append(startResult);

        return logResult.toString();
    }

    /**
     * 通用内部方法：执行脚本逻辑封装
     */
    private String executeScriptInternal(String scriptName) {
        String scriptPath = SCRIPT_BASE_PATH + scriptName;
        File scriptFile = new File(scriptPath);

        // 白名单校验
        if (!ALLOWED_SCRIPTS.contains(scriptName)) {
            String msg = "不允许执行此脚本：" + scriptName;
            log.warn(msg);
            return msg;
        }

        // 文件存在检查
        if (!scriptFile.exists()) {
            String msg = "脚本文件不存在：" + scriptPath;
            log.error(msg);
            return msg;
        }

        // 可执行权限检查
        if (!scriptFile.canExecute()) {
            String msg = "脚本没有执行权限，请执行 chmod +x " + scriptPath;
            log.error(msg);
            return msg;
        }

        try {
            Process process = Runtime.getRuntime().exec("bash " + scriptPath);

            BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdOut.readLine()) != null) {
                output.append(line).append("\n");
            }

            BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errors = new StringBuilder();
            while ((line = stdErr.readLine()) != null) {
                errors.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            log.info("{} 执行结束，exitCode = {}", scriptName, exitCode);

            if (exitCode == 0) {
                return "脚本 " + scriptName + " 执行成功：\n" + output;
            } else {
                return "脚本 " + scriptName + " 执行失败（exitCode=" + exitCode + "）：\n" + errors;
            }

        } catch (Exception e) {
            String msg = "脚本 " + scriptName + " 执行异常：" + e.getMessage();
            log.error(msg, e);
            return msg;
        }
    }
}
