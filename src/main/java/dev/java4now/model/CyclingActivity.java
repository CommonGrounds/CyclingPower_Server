package dev.java4now.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CyclingActivity {
    @JsonProperty("records")
    private List<RecordData> records;

    @JsonProperty("session")
    private SessionData session;

    @JsonProperty("deviceInfo")
    private DeviceInfo deviceInfo;

    public static class RecordData {
        @JsonProperty("timestamp")
        private long timestamp; // Seconds since UTC 1989-12-31 00:00:00

        @JsonProperty("latitude")
        private double latitude; // Degrees

        @JsonProperty("longitude")
        private double longitude; // Degrees

        @JsonProperty("speed")
        private float speed; // m/s

        @JsonProperty("power")
        private int power; // watts

        @JsonProperty("cadence")
        private short cadence; // rpm

        @JsonProperty("altitude")
        private float altitude; // meters

        @JsonProperty("grade")
        private float grade; // percent

        @JsonProperty("distance")
        private float distance; // meters

        @JsonProperty("calories")
        private int calories; // kcal

        @JsonProperty("temperature")
        private int temperature; // kcal

        // Getters and setters (remove batteryStatus-related methods)
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public float getSpeed() { return speed; }
        public void setSpeed(float speed) { this.speed = speed; }
        public int getPower() { return power; }
        public void setPower(int power) { this.power = power; }
        public short getCadence() { return cadence; }
        public void setCadence(short cadence) { this.cadence = cadence; }
        public float getAltitude() { return altitude; }
        public void setAltitude(float altitude) { this.altitude = altitude; }
        public float getGrade() { return grade; }
        public void setGrade(float grade) { this.grade = grade; }
        public float getDistance() { return distance; }
        public void setDistance(float distance) { this.distance = distance; }
        public int getCalories() { return calories; }
        public void setCalories(int calories) { this.calories = calories; }

        public int getTemperature() {
            return temperature;
        }

        public void setTemperature(int temperature) {
            this.temperature = temperature;
        }
    }

    public static class SessionData {
        @JsonProperty("startTime")
        private long startTime; // Seconds since UTC 1989-12-31 00:00:00

        @JsonProperty("totalElapsedTime")
        private float totalElapsedTime; // Seconds

        @JsonProperty("totalMovingTime")
        private float totalMovingTime; // Seconds

        @JsonProperty("totalDistance")
        private float totalDistance; // Meters

        @JsonProperty("totalCalories")
        private int totalCalories; // kcal

        @JsonProperty("avgCadence")
        private float avgCadence; // rpm

        @JsonProperty("avgPower")
        private float avgPower; // watts

        @JsonProperty("avgSpeed")
        private float avgSpeed; // m/s

        @JsonProperty("maxAltitude")
        private float maxAltitude; // meters

        @JsonProperty("minAltitude")
        private float minAltitude; // meters

        // Getters and setters
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public float getTotalElapsedTime() { return totalElapsedTime; }
        public void setTotalElapsedTime(float totalElapsedTime) { this.totalElapsedTime = totalElapsedTime; }
        public float getTotalDistance() { return totalDistance; }
        public void setTotalDistance(float totalDistance) { this.totalDistance = totalDistance; }
        public int getTotalCalories() { return totalCalories; }
        public void setTotalCalories(int totalCalories) { this.totalCalories = totalCalories; }
        public float getAvgCadence() { return avgCadence; }
        public void setAvgCadence(float avgCadence) { this.avgCadence = avgCadence; }
        public float getAvgPower() { return avgPower; }
        public void setAvgPower(float avgPower) { this.avgPower = avgPower; }
        public float getAvgSpeed() { return avgSpeed; }
        public void setAvgSpeed(float avgSpeed) { this.avgSpeed = avgSpeed; }
        public float getMaxAltitude() { return maxAltitude; }
        public void setMaxAltitude(float maxAltitude) { this.maxAltitude = maxAltitude; }
        public float getMinAltitude() { return minAltitude; }
        public void setMinAltitude(float minAltitude) { this.minAltitude = minAltitude; }

        public float getTotalMovingTime() {
            return totalMovingTime;
        }

        public void setTotalMovingTime(float totalMovingTime) {
            this.totalMovingTime = totalMovingTime;
        }
    }

    public static class DeviceInfo {
        @JsonProperty("timestamp")
        private long timestamp; // Seconds since UTC 1989-12-31 00:00:00

        @JsonProperty("batteryLevel")
        private String batteryLevel; // e.g., "GOOD", "LOW", etc.

        @JsonProperty("productName")
        private String productName;

        // Getters and setters
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getBatteryLevel() { return batteryLevel; }
        public void setBatteryLevel(String batteryLevel) { this.batteryLevel = batteryLevel; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
    }

    // Getters and setters
    public List<RecordData> getRecords() { return records; }
    public void setRecords(List<RecordData> records) { this.records = records; }
    public SessionData getSession() { return session; }
    public void setSession(SessionData session) { this.session = session; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(DeviceInfo deviceInfo) { this.deviceInfo = deviceInfo; }
}