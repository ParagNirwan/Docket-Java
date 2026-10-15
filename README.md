# Docket

Full-stack application with PostgreSQL, Spring Boot (Java 21), and React + Vite (TypeScript).

## 🚀 One-Click Quick Start (Docker)

All services (**PostgreSQL**, **Backend API Server**, and **Frontend**) run in Docker with automated startup ordering and health checks.

### Options to Start:
1. **VS Code (1 Shortcut):** Press `Ctrl + Shift + B` (runs the default task: `🚀 Start All (Docker Compose)`).
2. **Windows Explorer (1 Double-Click):** Double-click [`start.bat`](./start.bat).
3. **NPM / Terminal (1 Command):**
   ```bash
   npm start
   ```
   *(or `docker compose up --build`)*
4. **Docker Desktop:** Click the **Play** button on the `docket` container group.

---

## 🌐 Running Services

| Service | URL | Notes |
| :--- | :--- | :--- |
| **Frontend** | [http://localhost:5173](http://localhost:5173) | React + Vite with HMR hot reloading |
| **Backend API** | [http://localhost:8080](http://localhost:8080) | Spring Boot REST API |
| **Actuator Health** | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Healthcheck endpoint |
| **PostgreSQL** | `localhost:5433` (db: `docketdb`) | User: `docket`, Pass: `docket_dev_password` |

---

## 🛑 Stop Services

```bash
docker compose down
```
*(Or in VS Code: Run Task -> `🛑 Stop All (Docker Compose)`)*

# Docket-Java
