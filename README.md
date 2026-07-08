# Wafer Robot Simulator — Java Prototype

A Java 17 prototype of a semiconductor wafer handling robot communication system. A **Controller** terminal sends robot commands to a **Simulator** over TCP sockets using a custom ASCII frame protocol.

---

## Architecture Overview

```
WaferRobotController (TCP Server)          WaferRobotSimulator (TCP Client)
────────────────────────────────────────────────────────────────────────────
Port 5000 (Command Connection)
  ──── CMD frame ────────────────────────►
  ◄─── ACK / NAK frame ──────────────────

Port 5001 (Event Connection)
  ◄─── EVT frame (after 4s processing) ──
  ◄─── STAT frame (every 5s periodic) ───
```

- **Controller** is the TCP Server — starts first, waits for the Simulator to connect
- **Simulator** is the TCP Client — connects to the Controller on startup
- Two separate socket connections are used: one for commands, one for events

---

## Project Structure

```
wafer-robot-simulator/
├── pom.xml                                     # Maven build file (Java 17)
├── config/
│   ├── app.properties                          # TCP ports and intervals
│   ├── robot.cfg                               # Command and hardware rules
│   └── error_codes.csv                         # Error and completion codes
└── src/main/java/com/waferrobot/
    ├── config/         AppConfig
    ├── protocol/       RobotFrame, ChecksumCalculator, FrameBuilder, FrameParser
    ├── connection/     SocketConnection
    ├── registry/       ParameterRule, CommandSignature, ParsedCommand,
    │                   PayloadParser, CommandRegistry, StatusCodeRegistry
    ├── controller/     ClientFrameDispatcher, CommandSender,
    │                   StatListener, WaferRobotController
    └── simulator/      CommandWorker, StatusBroadcaster,
                        SimulatorFrameDispatcher, WaferRobotSimulator
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java JDK | 17 or higher |
| Maven | 3.6 or higher |

Verify your setup:
```bash
java -version
mvn -version
```

---

## Build

Clone the repository and build from the `wafer-robot-simulator` directory:

```bash
git clone https://github.com/sivaprasadkappani/RobotSimulatorJava.git
cd RobotSimulatorJava/wafer-robot-simulator
mvn clean package
```

This produces two executable JARs in the `target/` folder:

```
target/WaferRobotController.jar
target/WaferRobotSimulator.jar
```

---

## Running the Application

> **Important:** Both JARs must be run from the `wafer-robot-simulator/` directory so they can find the `config/` files.

Open **two separate terminal windows**, both pointing to the `wafer-robot-simulator/` directory.

### Step 1 — Start the Controller (Terminal 1)

The Controller starts first because it is the TCP Server. It will wait for the Simulator to connect.

```bash
java -jar target/WaferRobotController.jar
```

Expected output:
```
=== Wafer Robot Controller ===
Waiting for Simulator to connect...
[SocketConnection] Listening on port 5000 ...
[SocketConnection] Listening on port 5001 ...
```

### Step 2 — Start the Simulator (Terminal 2)

Once the Controller is listening, start the Simulator. It connects to both ports automatically.

```bash
java -jar target/WaferRobotSimulator.jar
```

Expected output:
```
=== Wafer Robot Simulator ===
[SocketConnection] Connecting to localhost:5000 ...
[SocketConnection] Connected to localhost:5000
[SocketConnection] Connecting to localhost:5001 ...
[SocketConnection] Connected to localhost:5001
[Simulator] Ready. Waiting for commands from Controller...
```

At this point the Controller will print:
```
[Controller] Ready. Type a command payload and press Enter.
[Controller] Example: PICK FROM=LPA1 ARM=LOWER
[Controller] Type 'exit' to quit.

>
```

### Step 3 — Send Commands from the Controller

Type robot commands at the `>` prompt in the Controller terminal.

---

## Supported Commands

Commands follow the format: `ACTION PARAM1=VALUE1 PARAM2=VALUE2`

| Command | Required Params | Optional Params | Example |
|---|---|---|---|
| `PICK`   | `FROM`          | `ARM`, `SLOTS`         | `PICK FROM=LPA1 ARM=LOWER` |
| `PLACE`  | `TO`            | `ARM`, `SLOTS`         | `PLACE TO=PROCESS ARM=LOWER` |
| `MOVE`   | `ARM`           | `R`, `T`, `Z`, `W`    | `MOVE ARM=UPPER R=100 T=45 Z=50 W=0` |
| `HOME`   | —               | —                      | `HOME` |
| `CONFIG` | `DW`            | —                      | `CONFIG DW=1` |

### Valid Parameter Values

| Parameter | Allowed Values |
|---|---|
| `FROM` / `TO` | `LPA1`, `LPA2`, `ALIGNER`, `PROCESS` |
| `ARM`         | `UPPER`, `LOWER` |
| `R`           | 0 – 500 (mm) |
| `T`           | 0 – 360 (degrees) |
| `Z`           | 0 – 300 (mm) |
| `W`           | -180 – 180 (degrees) |

> **Note:** `ALIGNER` and `PROCESS` stations only allow `ARM=LOWER`.

---

## Example Session

**Controller terminal:**
```
> PICK FROM=LPA1 ARM=LOWER
[Controller] Sending CMD #100: PICK FROM=LPA1 ARM=LOWER
[Controller] ACK received — Command #100 accepted by Simulator. Processing started.

> PICK FROM=ALIGNER ARM=UPPER
[Controller] Sending CMD #101: PICK FROM=ALIGNER ARM=UPPER
[Controller] NAK received — Command #101 REJECTED. Reason: 116 (ARM_STATION_MISMATCH)

> MOVE ARM=LOWER R=999
[Controller] Sending CMD #102: MOVE ARM=LOWER R=999
[Controller] NAK received — Command #102 REJECTED. Reason: 115 (INVALID_PARAM_VALUE)
```

After ~4 seconds, the EVT completion message appears:
```
[Controller] EVT received — Command #100 COMPLETE. Status: 200 (PICK_COMPLETE)
```

Every 5 seconds, a periodic status message appears:
```
[Controller] STAT received — Simulator status: 205 (PROCESSING_COMPLETE)
```

To exit, type:
```
> exit
```

---

## Configuration

All configuration lives in the `config/` folder.

### `app.properties` — TCP and timing settings

```properties
controller.host=localhost
controller.command.port=5000
controller.event.port=5001
stat.interval.ms=5000
```

### `robot.cfg` — Command and hardware validation rules

Four sections define the validation rules:
- `[COMMANDS]` — allowed actions and their parameter rules
- `[HARDWARE]` — allowed string values for each parameter
- `[NUMERIC_LIMITS]` — min/max ranges for numeric parameters
- `[STATION_ARMS]` — which arms are allowed at each station

### `error_codes.csv` — Status and error codes

| Code Range | Purpose |
|---|---|
| 100 – 120 | Error codes — returned in NAK frame payloads |
| 200 – 205 | Completion codes — returned in EVT frame payloads |

---

## Protocol

Frames are ASCII messages in this format:

```
<SOH>MESSAGE_TYPE|SEQUENCE_ID|PAYLOAD|CHECKSUM<CR><LF>
```

| Frame | Direction | Description |
|---|---|---|
| `CMD`  | Controller → Simulator | Robot command request |
| `ACK`  | Simulator → Controller | Command accepted |
| `NAK`  | Simulator → Controller | Command rejected (payload = error code) |
| `EVT`  | Simulator → Controller | Command execution complete (payload = completion code) |
| `STAT` | Simulator → Controller | Periodic status broadcast |

The checksum is an XOR of all core data characters, expressed as 2-character uppercase hex.

---

## Thread Model

| Thread | Application | Type | Role |
|---|---|---|---|
| Main | Controller | Regular | Reads stdin, spawns CommandSender per command |
| CommandSender | Controller | Regular (per command) | Sends CMD, waits for ACK/NAK |
| StatListener | Controller | Daemon | Receives EVT and STAT frames |
| Main | Simulator | Regular | Receives CMD frames, dispatches |
| CommandWorker | Simulator | Regular (per command) | Simulates 4s processing, sends EVT |
| StatusBroadcaster | Simulator | Daemon | Sends STAT every 5 seconds |
