# DARMS — Disaster Alert & Resource Management System

A full-stack Java project demonstrating three core Design Patterns through a real working application — a disaster management dashboard with a REST API backend and a live browser frontend.

Built with **pure Java** (no frameworks) and **vanilla HTML/JS** (no build tools). Zero dependencies.

---

## What it demonstrates

| Pattern | Where | What it does |
|---|---|---|
| **Factory Method** | `DisasterFactory.create()` | Creates the correct disaster subclass (Fire/Flood/Earthquake/Cyclone) without the caller using `new` directly |
| **Observer** | `Disaster` + `Citizen` / `Authority` | Registered observers are automatically notified when a disaster is reported |
| **Facade** | `EmergencyFacade.handleEmergency()` | One method call coordinates Police, Hospital, Rescue Team, and NGO Relief behind the scenes |

---

## Project structure

```
src/
├── DisasterServer.java     ← Full backend: HTTP server + all patterns + REST API
└── index.html              ← Frontend dashboard (standalone, no build step)
```

Everything lives in two files. No packages, no Maven, no Gradle.

---

## Prerequisites

- Java 11 or higher (tested on OpenJDK 17, 21, 25)
- Python 3 (only to serve the HTML file — optional)
- A browser (Chrome / Firefox / Edge)

---

## Running the project

### Step 1 — Start the backend

**From terminal:**
```bash
javac DisasterServer.java
java DisasterServer
```

**From IntelliJ IDEA:**
1. Open `DisasterServer.java`
2. Click the green ▶ icon next to `public static void main`
3. Wait for the startup banner in the Run panel

You should see:
```
╔══════════════════════════════════════════╗
║  Disaster Management Server — Port 8080  ║
╠══════════════════════════════════════════╣
║  GET  /api/disasters       — list all    ║
║  GET  /api/disasters/:id   — get one     ║
║  POST /api/disasters       — create new  ║
║  GET  /api/stats           — summary     ║
╚══════════════════════════════════════════╝
```

### Step 2 — Serve the frontend

Open a second terminal in the `src/` folder and run:
```bash
python -m http.server 3000
```

Then open your browser and go to:
```
http://localhost:3000/index.html
```

The top-right corner of the dashboard should show a green **LIVE** dot once it connects to the backend.

> **Note:** You can also open `index.html` directly by double-clicking it. It will work in **Demo Mode** (with sample data) even without the backend running.

---

## REST API

Base URL: `http://localhost:8080`

### Get all incidents
```
GET /api/disasters
```
Returns a JSON array of all `DisasterRecord` objects.

### Get a single incident
```
GET /api/disasters/{id}
```
Example: `GET /api/disasters/DIS-1000`

### Report a new disaster
```
POST /api/disasters
Content-Type: application/json

{
  "type":     "flood",        // fire | flood | earthquake | cyclone
  "location": "Sector 7, Mumbai",
  "severity": "HIGH"          // LOW | MEDIUM | HIGH | CRITICAL
}
```
Triggers the full pattern chain:
- **Factory** creates the correct `Disaster` subclass
- **Observer** notifies all registered Citizens and Authorities
- **Facade** dispatches Police, Hospital, Rescue Team (+ NGO if HIGH or CRITICAL)

Returns `201 Created` with the full `DisasterRecord` including all alerts and service logs.

### Get summary stats
```
GET /api/stats
```
Returns counts by disaster type, severity, total alerts sent, and total service actions taken.

---

## Sample API response

```json
{
  "id": "DIS-1000",
  "type": "Flood",
  "location": "Sector 7, Mumbai",
  "severity": "HIGH",
  "status": "ACTIVE",
  "createdAt": "2026-04-12 15:29:53",
  "alerts": [
    {
      "id": "ALERT-...",
      "recipient": "Aisha Sharma",
      "role": "CITIZEN",
      "message": "Aisha Sharma — Flood emergency at Sector 7, Mumbai [HIGH]. Evacuate immediately.",
      "timestamp": "15:29:53"
    }
  ],
  "services": [
    {
      "service": "Police",
      "action": "Units dispatched to Sector 7, Mumbai (Priority: HIGH).",
      "timestamp": "15:29:53"
    }
  ]
}
```

---

## How the patterns connect

```
POST /api/disasters
  │
  ├── DisasterFactory.create(type, location, severity)   ← FACTORY METHOD
  │     └── returns FireDisaster / FloodDisaster / etc.
  │
  ├── disaster.register(new Citizen(...))                ← OBSERVER (register)
  ├── disaster.register(new Authority(...))
  │
  ├── facade.handleEmergency(...)                        ← FACADE
  │     ├── PoliceService.dispatch()
  │     ├── HospitalService.respond()
  │     ├── RescueTeam.deploy()
  │     └── NGORelief.mobilise()        ← only if HIGH / CRITICAL
  │
  ├── disaster.sendAlerts()                              ← OBSERVER (notify)
  │     ├── Citizen.notify()   → AlertLog
  │     └── Authority.notify() → AlertLog
  │
  └── DisasterStore.add(record)         ← saved in memory, returned as JSON
```

---

## Frontend features

- Live incident sidebar with severity-coded indicators
- Detail panel showing Observer alerts (Citizen / Authority) and Facade service logs side by side
- Dispatch form to POST new disasters from the UI
- REST API reference tab with syntax-highlighted examples
- **Demo mode** — works fully offline with sample data if the backend is not running
- Auto-refreshes every 10 seconds when connected to the live backend

---

## Troubleshooting

**Dashboard shows DEMO MODE after starting the server**

Open `http://localhost:8080/api/disasters` in a new browser tab. If you see JSON, the backend is running fine. The issue is a CORS mismatch — make sure you are accessing the frontend via `http://localhost:3000/index.html` and **not** as a `file://` path.

**IntelliJ compile error about `com.sun.net.httpserver`**

Go to Run → Edit Configurations → Modify options → Add VM options, and paste:
```
--add-exports java.base/com.sun.net.httpserver=ALL-UNNAMED
```

**Port 8080 already in use**

Find and kill the process using the port:
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# macOS / Linux
lsof -i :8080
kill -9 <PID>
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend language | Java (pure, no frameworks) |
| HTTP server | `com.sun.net.httpserver` (built into JDK) |
| Data storage | In-memory `CopyOnWriteArrayList` |
| JSON | Hand-built string serialisation |
| Frontend | Vanilla HTML + CSS + JavaScript |
| Fonts | IBM Plex Mono, IBM Plex Sans, Bebas Neue (Google Fonts) |
| Static file server | Python `http.server` |

---

## Design patterns — quick reference

### Factory Method (Creational)
Defines an interface for creating an object but lets subclasses decide which class to instantiate. Here, `DisasterFactory.create()` is the factory method — it returns a `Disaster` without the caller knowing which concrete class was constructed.

### Observer (Behavioural)
Defines a one-to-many dependency so that when one object changes state, all dependents are notified automatically. The `Disaster` is the subject; `Citizen` and `Authority` are observers. Neither knows about the other.

### Facade (Structural)
Provides a simplified interface to a complex subsystem. `EmergencyFacade.handleEmergency()` is the single entry point that internally coordinates four independent service classes.

---

## Author

Built as a Design Patterns assignment project — 5th Semester, CS Engineering.
