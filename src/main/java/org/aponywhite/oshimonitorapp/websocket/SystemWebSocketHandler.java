package org.aponywhite.oshimonitorapp.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import oshi.software.os.OSProcess;
import org.aponywhite.oshimonitorapp.service.*;
import org.aponywhite.oshimonitorapp.service.alert.CpuAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SystemWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper;
    private final CpuLoadMetricsService cpuService;
    private final MemoryLoadMetricsService memoryService;
    private final ProcessMetricsService processService;
    private final PortMonitorKillService portService;
    private final CpuAlertService cpuAlertService;

    private final Logger log = LoggerFactory.getLogger(SystemWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final Map<String, Long> heartbeats = new ConcurrentHashMap<>();
    private final Map<String, Boolean> confirmedConnections = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private volatile List<Map<String, Object>> cachedProcessList = new ArrayList<>();
    private volatile Map<String, Object> lastBfrData = new HashMap<>();

    @PostConstruct
    public void init() {
        schedule(this::sendUpdates, 0, 2);
        schedule(this::checkHeartbeats, 0, 3);
        schedule(this::sampleProcessList, 0, 2);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.add(session);
        heartbeats.put(sessionId, System.currentTimeMillis());
        confirmedConnections.put(sessionId, false);

        send(session, message("creat", "success"));
        send(session, message("alarm", "success", cpuAlertService.startupSuccessMessage()));

        scheduler.schedule(() -> {
            if (!confirmedConnections.getOrDefault(sessionId, false)) {
                send(session, message("error", "未确认连接"));
                try {
                    session.close(CloseStatus.PROTOCOL_ERROR);
                } catch (Exception e) {
                    log.warn("关闭未确认连接失败：{}", e.getMessage());
                }
            }
        }, 3, TimeUnit.SECONDS);

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = mapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
        String type = (String) msg.get("type");
        String sessionId = session.getId();

        if ("creat".equalsIgnoreCase(type)) {
            confirmedConnections.put(sessionId, true);
        } else if ("heartbeat".equalsIgnoreCase(type)) {
            heartbeats.put(sessionId, System.currentTimeMillis());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(session);
        heartbeats.remove(sessionId);
        confirmedConnections.remove(sessionId);
    }

    private void sendUpdates() {
        Map<String, Object> cpu = collectCpuInfo();
        Map<String, Object> mem = collectMemInfo();
        Map<String, Object> bfr = lastBfrData;

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        dataMap.put("CPU", cpu);
        dataMap.put("Memory", mem);
        dataMap.put("BFR", bfr);

        sendToAll(message("update", "success", dataMap));

        cpuAlertService.checkCpuAlert()
                .ifPresent(alert -> sendToAll(message("alarm", "success", alert)));

    }

    private Map<String, Object> collectCpuInfo() {
        double[] cores = cpuService.getCpuLoad();
        double total = cpuService.getTotalCpuUsagePercent();

        List<Double> parts = new ArrayList<>();
        for (double core : cores) {
            parts.add(Math.round(core * 10000.0) / 100.0);
        }

        List<Map<String, Object>> top = cachedProcessList.stream()
                .filter(p -> Double.parseDouble(p.get("cpuUsage").toString()) > 10)
                .sorted( new Comparator<Map<String, Object>>() {
                    public int compare(Map<String, Object> a, Map<String, Object> b) {
                        return Double.compare(
                                Double.parseDouble(b.get("cpuUsage").toString()),
                                Double.parseDouble(a.get("cpuUsage").toString()));
                    }
                })
                .limit(10)
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("process", p.get("name"));
                    m.put("value", p.get("cpuUsage"));
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> cpuMap = new HashMap<>();
        cpuMap.put("CPU_usage", (int) total);
        cpuMap.put("CPU_part", parts);
        cpuMap.put("CPU_list", top);
        return cpuMap;
    }

    private Map<String, Object> collectMemInfo() {
        double[] mem = memoryService.getMemoryUsage();
        double percent = mem[2];

        List<Map<String, Object>> top = cachedProcessList.stream()
                .filter(p -> Double.parseDouble(p.get("memoryMB").toString()) > 10)
                .sorted(new Comparator<Map<String, Object>>() {
                    public int compare(Map<String, Object> a, Map<String, Object> b) {
                        return Double.compare(
                                Double.parseDouble(b.get("memoryMB").toString()),
                                Double.parseDouble(a.get("memoryMB").toString()));
                    }
                })
                .limit(10)
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("process", p.get("name"));
                    m.put("value", p.get("memoryMB"));
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> memMap = new HashMap<>();
        memMap.put("Memory_usage", (int) percent);
        memMap.put("Memory_list", top);
        return memMap;
    }

    private void sampleProcessList() {
        try {
            cachedProcessList = processService.getProcessCpuAndMemory();

            Integer pid = portService.getPidByPort(10011);
            if (pid != null) {
                OSProcess oldProc = portService.getProcessByPid(pid);
                Thread.sleep(1000);
                OSProcess newProc = portService.getProcessByPid(pid);

                Map<String, Object> bfr = new HashMap<>();
                bfr.put("BFR_CPU", String.format("%.0f", newProc.getProcessCpuLoadBetweenTicks(oldProc) * 100));
                bfr.put("BFR_Memory", String.format("%.0f", newProc.getResidentSetSize() / 1024.0 / 1024.0));
                lastBfrData = bfr;
            }
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("BFR_CPU", "0");
            fallback.put("BFR_Memory", "0");
            lastBfrData = fallback;
        }
    }

    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        for (WebSocketSession session : sessions) {
            String sessionId = session.getId();
            long last = heartbeats.getOrDefault(sessionId, 0L);

            if (now - last > 10000) {
                try {
                    session.close(CloseStatus.PROTOCOL_ERROR);
                } catch (Exception ignored) {}
                sessions.remove(session);
                heartbeats.remove(sessionId);
                confirmedConnections.remove(sessionId);
            } else {
                send(session, message("heartbeat", "success"));
            }
        }
    }

    private void sendToAll(Map<String, Object> msg) {
        String json;
        try {
            json = mapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("序列化失败", e);
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception ignored) {}
            }
        }
    }

    private void send(WebSocketSession session, Map<String, Object> msg) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("发送失败", e);
        }
    }

    private void schedule(final Runnable r, int delay, int period) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                r.run();
            } catch (Exception e) {
                log.error("定时任务异常", e);
            }
        }, delay, period, TimeUnit.SECONDS);

    }

    private Map<String, Object> message(String type, String msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("message", msg);
        return map;
    }

    private Map<String, Object> message(String type, String msg, Object data) {
        Map<String, Object> map = message(type, msg);
        map.put("data", data);
        return map;
    }
}
