# Cycle Power Backend

**Cycle Power Backend** is the central data processing engine of the Cycle Power ecosystem. It acts as a bridge between the native mobile application and the web-based analytics dashboard, handling file storage, data transformation, and persistent storage.

---

## ⚙️ Core Responsibilities

* **FIT File Processing:** Ingests binary `.fit` files (standard cycling format) uploaded from the mobile app and parses them into structured JSON for web consumption.
* **RESTful API:** Provides secure endpoints for data synchronization between the GluonFX mobile client and the WebFX frontend.
* **Persistent Storage:** Manages a lightweight yet robust **SQLite** database to store user sessions, ride metrics, and historical data.
* **Data Transformation:** Converts raw sensor data into optimized formats for real-time charting and long-term analytics.

---

## 🛠 Tech Stack

* **Java 17+**
* **Spring Boot 3.x:** Core framework for the REST API and service layer.
* **SQLite:** Efficient, file-based relational database for seamless deployment.
* **Spring Data JPA:** For clean and maintainable database abstraction.
* **FIT SDK:** Integrated logic for parsing Garmin/ANT+ standard fitness files.

---

## 📂 System Flow

1. **Upload:** Mobile app (GluonFX) sends a `.fit` file via a POST request.
2. **Process:** The server parses the binary data, extracts GPS, Cadence, and Power metrics.
3. **Store:** Metadata and session summaries are saved in **SQLite**.
4. **Serve:** The Web Dashboard (WebFX) fetches the processed JSON to render interactive charts.

---

## 🚀 Getting Started

### Prerequisites

* JDK 17 or higher
* Maven

### Installation & Run

1. Clone the repository:
```bash
git clone https://github.com/CommonGrounds/CyclingPower_Backend.git

```


2. Run the application:
```bash
mvn spring-boot:run

```



The server will start on `http://localhost:8080` by default.

---

## 🌐 Deployment

The production API is currently hosted on **Render.com**.

> **API Base URL:** `https://cyclingpower-server-1.onrender.com`
> *(Note: As it is on a free tier, the first request may take up to 30 seconds to wake up the instance.)*

---

## 🔗 Part of the Ecosystem

* **[Cycle Power Mobile](https://github.com/CommonGrounds/CyclingPower_Mobile):** Native mobile recording app.
* **[Cycle Power Web](https://github.com/CommonGrounds/CyclingPower):** WebFX-based visualization dashboard.