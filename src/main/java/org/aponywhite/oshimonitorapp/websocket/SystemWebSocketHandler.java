package org.aponywhite.oshimonitorapp.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.aponywhite.oshimonitorapp.service.*;
import org.aponywhite.oshimonitorapp.service.alert.CpuAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import oshi.software.os.OSProcess;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SystemWebSocketHandler extends TextWebSocketHandler {

    private final ProcessMetricsService processService;
    private final CpuLoadMetricsService cpuLoadService;
    private final MemoryLoadMetricsService memoryService;
    private final CpuAlertService cpuAlertService;
    private final PortMonitorKillService portMonitorKillService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Logger log = LoggerFactory.getLogger(SystemWebSocketHandler.class);

    private final Map<String, Boolean> pendingConfirmations = new ConcurrentHashMap<>();
    private final Map<String, Integer> missedHeartbeats = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHeartbeatReply = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        String sessionId = session.getId();
        pendingConfirmations.put(sessionId, false);
        missedHeartbeats.put(sessionId, 0);
        lastHeartbeatReply.put(sessionId, System.currentTimeMillis());

        try {
            Map<String, Object> confirm = new HashMap<>();
            confirm.put("type", "creat");
            confirm.put("message", "success");
            session.sendMessage(new TextMessage(mapper.writeValueAsString(confirm)));

            Map<String, Object> alarmMsg = new HashMap<>();
            alarmMsg.put("type", "alarm");
            alarmMsg.put("message", "success");
            alarmMsg.put("data", cpuAlertService.startupSuccessMessage());
            session.sendMessage(new TextMessage(mapper.writeValueAsString(alarmMsg)));
        } catch (Exception e) {
            log.error("初始化连接失败", e);
        }

        scheduler.schedule(() -> {
            if (!Boolean.TRUE.equals(pendingConfirmations.get(sessionId))) {
                try {
                    Map<String, Object> error = new HashMap<>();
                    error.put("type", "error");
                    error.put("message", "success");
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(error)));
                } catch (Exception e) {
                    log.error("发送 error 消息失败", e);
                }
            }
        }, 3, TimeUnit.SECONDS);

        if (sessions.size() == 1) {
            startBroadcasting();
            startHeartbeatChecker();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = mapper.readValue(
                message.getPayload(),
                new TypeReference<Map<String, Object>>() {}
        );
        String type = (String) msg.get("type");
        String sessionId = session.getId();

        if ("creat".equalsIgnoreCase(type)) {
            pendingConfirmations.put(sessionId, true);
        } else if ("heartbeat".equalsIgnoreCase(type)) {
            missedHeartbeats.put(sessionId, 0);
            lastHeartbeatReply.put(sessionId, System.currentTimeMillis());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(session);
        pendingConfirmations.remove(sessionId);
        missedHeartbeats.remove(sessionId);
        lastHeartbeatReply.remove(sessionId);
    }

    private void startHeartbeatChecker() {
        scheduler.scheduleAtFixedRate(() -> {
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) continue;

                String sessionId = session.getId();
                int missed = missedHeartbeats.getOrDefault(sessionId, 0);
                long lastReply = lastHeartbeatReply.getOrDefault(sessionId, 0L);
                long now = System.currentTimeMillis();

                if (now - lastReply > 10000 || missed >= 3) {
                    try {
                        session.close(CloseStatus.PROTOCOL_ERROR);
                    } catch (Exception e) {
                        log.error("强制关闭失败", e);
                    }
                    continue;
                }

                try {
                    Map<String, Object> beat = new HashMap<>();
                    beat.put("type", "heartbeat");
                    beat.put("message", "success");
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(beat)));
                    missedHeartbeats.put(sessionId, missed + 1);
                } catch (Exception e) {
                    log.error("Heartbeat 发送失败", e);
                }
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void startBroadcasting() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));

                double[] coreLoads = cpuLoadService.getCpuLoad();
                List<Double> cpuParts = Arrays.stream(coreLoads)
                        .map(d -> Math.round(d * 10000.0) / 100.0)
                        .boxed()
                        .collect(Collectors.toList());

                double totalCpu = cpuLoadService.getTotalCpuUsagePercent();
                double[] mem = memoryService.getMemoryUsage();
                double memUsage = mem[2];

                List<Map<String, Object>> procList = processService.getProcessCpuAndMemory();

                List<Map<String, Object>> topCpu = procList.stream()
                        .filter(p -> Double.parseDouble(p.get("cpuUsage").toString()) > 10.0)
                        .sorted((a, b) -> Double.compare(
                                Double.parseDouble(b.get("cpuUsage").toString()),
                                Double.parseDouble(a.get("cpuUsage").toString())
                        ))
                        .limit(10)
                        .map(p -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("process", p.get("name"));
                            item.put("value", p.get("cpuUsage"));
                            return item;
                        }).collect(Collectors.toList());

                List<Map<String, Object>> topMem = procList.stream()
                        .filter(p -> Double.parseDouble(p.get("memoryMB").toString()) > 10)
                        .sorted((a, b) -> Double.compare(
                                Double.parseDouble(b.get("memoryMB").toString()),
                                Double.parseDouble(a.get("memoryMB").toString())
                        ))
                        .limit(10)
                        .map(p -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("process", p.get("name"));
                            item.put("value", p.get("memoryMB"));
                            return item;
                        }).collect(Collectors.toList());

                Map<String, Object> cpuMap = new HashMap<>();
                cpuMap.put("CPU_usage", String.format("%.0f", totalCpu));
                cpuMap.put("CPU_part", cpuParts);
                cpuMap.put("CPU_list", topCpu);

                Map<String, Object> memMap = new HashMap<>();
                memMap.put("Memory_usage", String.format("%.0f", memUsage));
                memMap.put("Memory_list", topMem);

                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("time", time);
                dataMap.put("CPU", cpuMap);
                dataMap.put("Memory", memMap);

                Map<String, Object> bfrMap = new HashMap<>();
                try {
                    Integer bfrPid = portMonitorKillService.getPidByPort(10011);
                    if (bfrPid != null) {
                        OSProcess oldProc = portMonitorKillService.getProcessByPid(bfrPid);
                        Thread.sleep(1000);
                        OSProcess newProc = portMonitorKillService.getProcessByPid(bfrPid);

                        if (oldProc != null && newProc != null) {
                            double bfrCpu = newProc.getProcessCpuLoadBetweenTicks(oldProc) * 100;
                            double bfrMem = newProc.getResidentSetSize() / 1024.0 / 1024.0;
                            bfrMap.put("BFR_CPU", String.format("%.0f", bfrCpu));
                            bfrMap.put("BFR_Memory", String.format("%.0f", bfrMem));
                        }
                    }
                } catch (Exception e) {
                    log.error("采集 BFR 程序资源失败", e);
                    bfrMap.put("BFR_CPU", "0");
                    bfrMap.put("BFR_Memory", "0");
                }
                dataMap.put("BFR", bfrMap);

                Map<String, Object> message = new HashMap<>();
                message.put("type", "update");
                message.put("message", "success");
                message.put("data", dataMap);

                String json = mapper.writeValueAsString(message);
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }

                cpuAlertService.checkCpuAlert().ifPresent(alert -> {
                    try {
                        Map<String, Object> alarmMsg = new HashMap<>();
                        alarmMsg.put("type", "alarm");
                        alarmMsg.put("message", "success");
                        alarmMsg.put("data", alert);
                        String alarmJson = mapper.writeValueAsString(alarmMsg);
                        for (WebSocketSession session : sessions) {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(alarmJson));
                            }
                        }
                    } catch (Exception e) {
                        log.error("告警推送失败", e);
                    }
                });

            } catch (Exception e) {
                log.error("WebSocket 推送异常", e);
            }
        }, 0, 2, TimeUnit.SECONDS);
    }
}
