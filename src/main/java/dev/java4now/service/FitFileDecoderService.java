package dev.java4now.service;

import dev.java4now.model.CyclingActivity;
import com.garmin.fit.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FitFileDecoderService {

    private static final String JSON_DIR = "json/"; // Directory to save JSON files on server

    public CyclingActivity decodeFitFile(File fitFile) throws FitRuntimeException, IOException {
        List<CyclingActivity.RecordData> records = new ArrayList<>();
        AtomicReference<SessionMesg> session = new AtomicReference<>();
        AtomicReference<DeviceInfoMesg> deviceInfo = new AtomicReference<>();

        // Use Decode to read the FIT file
        Decode decode = new Decode();
        try (FileInputStream fis = new FileInputStream(fitFile)) {
            // Check if the file is a valid FIT file
            if (!decode.checkFileIntegrity(fis)) {
                throw new FitRuntimeException("Invalid FIT file: " + fitFile.getName());
            }

            // Reset the input stream for reading
            fis.getChannel().position(0);
            MesgBroadcaster broadcaster = new MesgBroadcaster();
            broadcaster.addListener((MesgListener) (mesg) -> {
                if (mesg.getNum() == MesgNum.RECORD) {
                    RecordMesg recordMesg = new RecordMesg(mesg);
                    CyclingActivity.RecordData record = new CyclingActivity.RecordData();

                    // Convert timestamp (FIT uses DateTime, but store as seconds for simplicity)
                    DateTime timestamp = recordMesg.getTimestamp();
                    record.setTimestamp(timestamp != null ? timestamp.getTimestamp() : 0);

                    // Latitude and longitude (convert semicircles to degrees: value / (2^31 / 180))
                    Integer lat = recordMesg.getPositionLat();
                    Integer lon = recordMesg.getPositionLong();
                    record.setLatitude(lat != null ? lat / (Math.pow(2, 31) / 180.0) : 0.0);
                    record.setLongitude(lon != null ? lon / (Math.pow(2, 31) / 180.0) : 0.0);

                    // Other fields (removed batteryStatus)
                    record.setSpeed(recordMesg.getSpeed() != null ? recordMesg.getSpeed() : 0.0f); // m/s
                    record.setPower(recordMesg.getPower() != null ? recordMesg.getPower() : 0); // watts
                    record.setCadence(recordMesg.getCadence() != null ? recordMesg.getCadence() : 0); // rpm
                    record.setAltitude(recordMesg.getAltitude() != null ? recordMesg.getAltitude() : 0.0f); // meters
                    record.setGrade(recordMesg.getGrade() != null ? recordMesg.getGrade() : 0.0f); // percent
                    record.setDistance(recordMesg.getDistance() != null ? recordMesg.getDistance() : 0.0f); // meters
                    record.setCalories(recordMesg.getCalories() != null ? recordMesg.getCalories() : 0); // kcal
                    record.setTemperature(recordMesg.getTemperature() != null ? recordMesg.getTemperature() : 0);

                    records.add(record);
                } else if (mesg.getNum() == MesgNum.SESSION) {
                    session.set(new SessionMesg(mesg));
                } else if (mesg.getNum() == MesgNum.DEVICE_INFO) {
                    deviceInfo.set(new DeviceInfoMesg(mesg));
                }
            });

            // Decode the file
            decode.read(fis, broadcaster);
        }

        // Create CyclingActivity object
        CyclingActivity activity = new CyclingActivity();
        activity.setRecords(records);

        // Populate session data and calculate aggregates
        if (session.get() != null) {
            CyclingActivity.SessionData sessionData = new CyclingActivity.SessionData();

            DateTime startTime = session.get().getStartTime();
            sessionData.setStartTime(startTime != null ? startTime.getTimestamp() : 0);

            sessionData.setTotalElapsedTime(session.get().getTotalElapsedTime() != null ? session.get().getTotalElapsedTime() : 0.0f);
            sessionData.setMovingTime(session.get().getTotalMovingTime() != null ? session.get().getTotalMovingTime() : 0.0f);
            sessionData.setTotalDistance(session.get().getTotalDistance() != null ? session.get().getTotalDistance() : 0.0f);
            sessionData.setTotalCalories(session.get().getTotalCalories() != null ? session.get().getTotalCalories() : 0);

            // Calculate aggregates from records
            float sumCadence = 0, sumPower = 0, sumSpeed = 0;
            float maxAltitude = Float.MIN_VALUE, minAltitude = Float.MAX_VALUE;
            int recordCount = records.size();

            for (CyclingActivity.RecordData record : records) {
                sumCadence += record.getCadence();
                sumPower += record.getPower();
                sumSpeed += record.getSpeed();

                maxAltitude = Math.max(maxAltitude, record.getAltitude());
                minAltitude = Math.min(minAltitude, record.getAltitude());
            }

            sessionData.setAvgCadence(recordCount > 0 ? sumCadence / recordCount : 0);
            sessionData.setAvgPower(recordCount > 0 ? sumPower / recordCount : 0);
            sessionData.setAvgSpeed(recordCount > 0 ? sumSpeed / recordCount : 0);
            sessionData.setMaxAltitude(maxAltitude != Float.MIN_VALUE ? maxAltitude : 0);
            sessionData.setMinAltitude(minAltitude != Float.MAX_VALUE ? minAltitude : 0);

            activity.setSession(sessionData);
        }

        // Populate device info
        if (deviceInfo.get() != null) {
            CyclingActivity.DeviceInfo deviceInfoData = new CyclingActivity.DeviceInfo();

            DateTime timestamp = deviceInfo.get().getTimestamp();
            deviceInfoData.setTimestamp(timestamp != null ? timestamp.getTimestamp() : 0);

            Short batteryLevel = deviceInfo.get().getBatteryStatus();
            System.out.println(batteryLevel);
            deviceInfoData.setBatteryLevel(batteryLevel != null ? String.valueOf(batteryLevel) : "UNKNOWN");

            String productName = deviceInfo.get().getProductName();
            deviceInfoData.setProductName(productName != null ? productName : "Unknown Device");

            activity.setDeviceInfo(deviceInfoData);
        }

        // Save as JSON
        saveAsJson(activity, fitFile.getName().replace(".fit", ".json"));

        return activity;
    }


    private void saveAsJson(CyclingActivity activity, String fileName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Path jsonPath = Paths.get(JSON_DIR, fileName);
        if (!Files.exists(jsonPath.getParent())) {
            Files.createDirectories(jsonPath.getParent());
        }
        objectMapper.writeValue(jsonPath.toFile(), activity);
        System.out.println("JSON file saved to: " + jsonPath.toAbsolutePath());
    }
}