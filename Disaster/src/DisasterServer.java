import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// ============================================================
//  Smart Disaster Alert & Resource Management System — Backend
//  Pure Java HTTP Server (no frameworks)
// ============================================================

class AlertLog {
    public final String id, recipient, role, message, timestamp;
    public AlertLog(String id, String recipient, String role, String message) {
        this.id = id; this.recipient = recipient; this.role = role; this.message = message;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    public String toJson() {
        return String.format(
                "{\"id\":\"%s\",\"recipient\":\"%s\",\"role\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                id, esc(recipient), role, esc(message), timestamp);
    }
    private String esc(String s) { return s.replace("\"", "\\\""); }
}

class ServiceLog {
    public final String service, action, timestamp;
    public ServiceLog(String service, String action) {
        this.service = service; this.action = action;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    public String toJson() {
        return String.format("{\"service\":\"%s\",\"action\":\"%s\",\"timestamp\":\"%s\"}",
                service, esc(action), timestamp);
    }
    private String esc(String s) { return s.replace("\"", "\\\""); }
}

class DisasterRecord {
    public final String id, type, location, severity, status, createdAt;
    public final List<AlertLog> alerts;
    public final List<ServiceLog> services;
    public DisasterRecord(String id, String type, String location, String severity,
                          String status, List<AlertLog> alerts, List<ServiceLog> services) {
        this.id = id; this.type = type; this.location = location;
        this.severity = severity; this.status = status;
        this.alerts = alerts; this.services = services;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    public String toJson() {
        String a = alerts.stream().map(AlertLog::toJson).collect(Collectors.joining(","));
        String s = services.stream().map(ServiceLog::toJson).collect(Collectors.joining(","));
        return String.format(
                "{\"id\":\"%s\",\"type\":\"%s\",\"location\":\"%s\",\"severity\":\"%s\"," +
                        "\"status\":\"%s\",\"createdAt\":\"%s\",\"alerts\":[%s],\"services\":[%s]}",
                id, type, esc(location), severity, status, createdAt, a, s);
    }
    private String esc(String s) { return s.replace("\"", "\\\""); }
}

// ── OBSERVER ─────────────────────────────────────────────────

interface AlertObserver {
    AlertLog notify(String type, String location, String severity, String alertId);
}

class Citizen implements AlertObserver {
    private final String name;
    Citizen(String name) { this.name = name; }
    public AlertLog notify(String type, String location, String severity, String id) {
        return new AlertLog(id, name, "CITIZEN",
                name + " — " + type + " emergency at " + location + " [" + severity + "]. Evacuate immediately.");
    }
}

class Authority implements AlertObserver {
    private final String dept;
    Authority(String dept) { this.dept = dept; }
    public AlertLog notify(String type, String location, String severity, String id) {
        return new AlertLog(id, dept, "AUTHORITY",
                dept + " — " + type + " at " + location + ", severity " + severity + ". Mobilise resources.");
    }
}

// ── FACTORY ──────────────────────────────────────────────────

abstract class Disaster {
    protected final String type, location, severity;
    private final List<AlertObserver> observers = new ArrayList<>();
    Disaster(String type, String location, String severity) {
        this.type = type; this.location = location; this.severity = severity;
    }
    void register(AlertObserver o) { observers.add(o); }
    List<AlertLog> sendAlerts() {
        List<AlertLog> logs = new ArrayList<>();
        int i = 1;
        for (AlertObserver o : observers)
            logs.add(o.notify(type, location, severity, "ALERT-" + System.currentTimeMillis() + "-" + i++));
        return logs;
    }
}

class FireDisaster       extends Disaster { FireDisaster(String l, String s)       { super("Fire", l, s); } }
class FloodDisaster      extends Disaster { FloodDisaster(String l, String s)      { super("Flood", l, s); } }
class EarthquakeDisaster extends Disaster { EarthquakeDisaster(String l, String s) { super("Earthquake", l, s); } }
class CycloneDisaster    extends Disaster { CycloneDisaster(String l, String s)    { super("Cyclone", l, s); } }

class DisasterFactory {
    private static final AtomicInteger counter = new AtomicInteger(1000);
    static Disaster create(String type, String location, String severity) {
        switch (type.toLowerCase()) {
            case "fire":       return new FireDisaster(location, severity);
            case "flood":      return new FloodDisaster(location, severity);
            case "earthquake": return new EarthquakeDisaster(location, severity);
            case "cyclone":    return new CycloneDisaster(location, severity);
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
    static String nextId() { return "DIS-" + counter.getAndIncrement(); }
}

// ── FACADE ───────────────────────────────────────────────────

class EmergencyFacade {
    List<ServiceLog> handleEmergency(String type, String location, String severity) {
        List<ServiceLog> logs = new ArrayList<>();
        logs.add(new ServiceLog("Police",   "Units dispatched to " + location + " (Priority: " + severity + ")."));
        int units = severity.equals("CRITICAL") ? 6 : severity.equals("HIGH") ? 4 : 2;
        logs.add(new ServiceLog("Hospital", units + " ambulances deployed to " + location + "."));
        logs.add(new ServiceLog("Rescue",   type + " rescue team deployed to " + location + "."));
        if (severity.equals("HIGH") || severity.equals("CRITICAL"))
            logs.add(new ServiceLog("NGO Relief", "Relief camp established near " + location + "."));
        return logs;
    }
}

// ── STORE ────────────────────────────────────────────────────

class DisasterStore {
    private static final List<DisasterRecord> records = new CopyOnWriteArrayList<>();

    static void add(DisasterRecord r) { records.add(0, r); }
    static List<DisasterRecord> all() { return Collections.unmodifiableList(records); }
    static Optional<DisasterRecord> findById(String id) {
        return records.stream().filter(r -> r.id.equals(id)).findFirst();
    }
    static String allJson() {
        return "[" + records.stream().map(DisasterRecord::toJson).collect(Collectors.joining(",")) + "]";
    }

    static {
        EmergencyFacade facade = new EmergencyFacade();

        Disaster d1 = DisasterFactory.create("flood", "Sector 7, Mumbai", "HIGH");
        d1.register(new Citizen("Aisha Sharma"));
        d1.register(new Citizen("Rohan Mehta"));
        d1.register(new Authority("Mumbai Disaster Mgmt Cell"));
        records.add(new DisasterRecord(DisasterFactory.nextId(), d1.type, d1.location, d1.severity,
                "ACTIVE", d1.sendAlerts(), facade.handleEmergency(d1.type, d1.location, d1.severity)));

        Disaster d2 = DisasterFactory.create("earthquake", "Nashik, Maharashtra", "CRITICAL");
        d2.register(new Citizen("Priya Nair"));
        d2.register(new Authority("Maharashtra Civil Defense"));
        d2.register(new Authority("NDRF HQ"));
        records.add(new DisasterRecord(DisasterFactory.nextId(), d2.type, d2.location, d2.severity,
                "ACTIVE", d2.sendAlerts(), facade.handleEmergency(d2.type, d2.location, d2.severity)));
    }
}

// ── JSON UTIL ────────────────────────────────────────────────

class Json {
    static String get(String body, String key) {
        String p = "\"" + key + "\"";
        int ki = body.indexOf(p);
        if (ki < 0) return "";
        int colon = body.indexOf(":", ki + p.length());
        if (colon < 0) return "";
        int start = colon + 1;
        while (start < body.length() && body.charAt(start) == ' ') start++;
        if (body.charAt(start) == '"') {
            int end = body.indexOf("\"", start + 1);
            return end < 0 ? "" : body.substring(start + 1, end);
        }
        int end = start;
        while (end < body.length() && ",}]".indexOf(body.charAt(end)) < 0) end++;
        return body.substring(start, end).trim();
    }
}

// ── CORS HELPER ──────────────────────────────────────────────
// Single method — called once per response. No Filter used to avoid double-headers.

class Cors {
    static void set(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin",  "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }
}

// ── HANDLERS ────────────────────────────────────────────────

class DisastersHandler implements HttpHandler {
    private final EmergencyFacade facade = new EmergencyFacade();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Cors.set(ex);
        String method = ex.getRequestMethod();
        String path   = ex.getRequestURI().getPath();

        if ("OPTIONS".equalsIgnoreCase(method)) { ex.sendResponseHeaders(204, -1); return; }

        if ("GET".equals(method) && "/api/disasters".equals(path)) {
            send(ex, 200, DisasterStore.allJson()); return;
        }

        if ("GET".equals(method) && path.startsWith("/api/disasters/")) {
            String id = path.substring("/api/disasters/".length());
            Optional<DisasterRecord> r = DisasterStore.findById(id);
            if (r.isPresent()) send(ex, 200, r.get().toJson());
            else               send(ex, 404, "{\"error\":\"Not found\"}");
            return;
        }

        if ("POST".equals(method) && "/api/disasters".equals(path)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                String type     = Json.get(body, "type");
                String location = Json.get(body, "location");
                String severity = Json.get(body, "severity");

                if (type.isEmpty() || location.isEmpty() || severity.isEmpty()) {
                    send(ex, 400, "{\"error\":\"type, location and severity required\"}"); return;
                }

                Disaster disaster = DisasterFactory.create(type, location, severity);
                disaster.register(new Citizen("Local Resident 1"));
                disaster.register(new Citizen("Local Resident 2"));
                disaster.register(new Authority("District Collector Office"));
                disaster.register(new Authority("State Emergency Ops Center"));

                List<ServiceLog> services = facade.handleEmergency(disaster.type, disaster.location, disaster.severity);
                List<AlertLog>   alerts   = disaster.sendAlerts();

                DisasterRecord record = new DisasterRecord(
                        DisasterFactory.nextId(), disaster.type, disaster.location,
                        disaster.severity, "ACTIVE", alerts, services);
                DisasterStore.add(record);
                send(ex, 201, record.toJson());

            } catch (IllegalArgumentException e) {
                send(ex, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
            return;
        }

        send(ex, 404, "{\"error\":\"Not found\"}");
    }

    private void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }
}

class StatsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Cors.set(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }

        List<DisasterRecord> all = DisasterStore.all();
        long fire  = all.stream().filter(r -> "Fire".equals(r.type)).count();
        long flood = all.stream().filter(r -> "Flood".equals(r.type)).count();
        long eq    = all.stream().filter(r -> "Earthquake".equals(r.type)).count();
        long cyc   = all.stream().filter(r -> "Cyclone".equals(r.type)).count();
        long crit  = all.stream().filter(r -> "CRITICAL".equals(r.severity)).count();
        long high  = all.stream().filter(r -> "HIGH".equals(r.severity)).count();
        long med   = all.stream().filter(r -> "MEDIUM".equals(r.severity)).count();
        long totalAlerts   = all.stream().mapToLong(r -> r.alerts.size()).sum();
        long totalServices = all.stream().mapToLong(r -> r.services.size()).sum();

        String json = String.format(
                "{\"total\":%d,\"byType\":{\"fire\":%d,\"flood\":%d,\"earthquake\":%d,\"cyclone\":%d}," +
                        "\"bySeverity\":{\"critical\":%d,\"high\":%d,\"medium\":%d}," +
                        "\"totalAlerts\":%d,\"totalServiceActions\":%d}",
                all.size(), fire, flood, eq, cyc, crit, high, med, totalAlerts, totalServices);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }
}

// ── MAIN ─────────────────────────────────────────────────────

public class DisasterServer {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/disasters", new DisastersHandler());
        server.createContext("/api/stats",     new StatsHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  Disaster Management Server — Port " + port + "  ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  GET  /api/disasters       — list all    ║");
        System.out.println("║  GET  /api/disasters/:id   — get one     ║");
        System.out.println("║  POST /api/disasters       — create new  ║");
        System.out.println("║  GET  /api/stats           — summary     ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }
}