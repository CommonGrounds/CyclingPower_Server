package dev.java4now;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GitService {

    private static final String GIT_REPO_DIR = "/app";
    private static final AtomicInteger retryCounter = new AtomicInteger(0);
    private static final int MAX_RETRIES = 5; // Stop after 5 failed pushes to avoid loops

    public void commitChanges(String message) {
        try {
            if (!Files.exists(Paths.get(GIT_REPO_DIR + "/.git"))) {
                System.err.println("Git repository not found");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(new File(GIT_REPO_DIR));
            pb.redirectErrorStream(true);

            // Git add
            pb.command("git", "add", "cycling_power.db", "json/*", "images/*");
            Process p = pb.start();
            String addOutput = readProcessOutput(p);
            int addExit = p.waitFor();
            if (addExit != 0) {
                System.err.println("Git add failed: " + addOutput);
                return;
            }

            // Git commit
            pb.command("git", "commit", "-m", message);
            p = pb.start();
            String commitOutput = readProcessOutput(p);
            int commitExit = p.waitFor();
            if (commitExit != 0) {
                // If nothing to commit, that's fine (no changes)
                if (commitOutput.contains("nothing to commit")) {
                    System.out.println("No changes to commit");
                } else {
                    System.err.println("Git commit failed: " + commitOutput);
                }
                return;
            }

            System.out.println("Changes committed locally: " + message);
            // Reset retry counter on successful commit
            retryCounter.set(0);
        } catch (IOException | InterruptedException e) {
            System.err.println("Commit failed: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes (300,000 ms); adjust as needed
    public void retryPush() {
        if (retryCounter.get() >= MAX_RETRIES) {
            System.err.println("Max retries reached; skipping push. Manual intervention needed.");
            return;
        }

        String gitToken = System.getenv("GIT_TOKEN");
        if (gitToken == null || gitToken.isEmpty()) {
            System.err.println("GIT_TOKEN not set; skipping push");
            return;
        }

        try {
            // First, check if there are uncommitted changes and commit them
            if (hasUncommittedChanges()) {
                commitChanges("Automated retry commit for pending changes");
            }

            // Now push
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(new File(GIT_REPO_DIR));
            pb.redirectErrorStream(true);
            pb.command("git", "push", "https://x:" + gitToken + "@github.com/CommonGrounds/CyclingPower_Server.git", "main");
            Process p = pb.start();
            String pushOutput = readProcessOutput(p);
            int pushExit = p.waitFor();

            if (pushExit == 0) {
                System.out.println("Push succeeded on retry: " + pushOutput);
                retryCounter.set(0); // Reset on success
                // Optional: Broadcast via WebSocket if needed
                // webSocketHandler.broadcast("Push retry succeeded");
            } else {
                retryCounter.incrementAndGet();
                System.err.println("Push failed (retry " + retryCounter.get() + "/" + MAX_RETRIES + "): " + pushOutput);
            }
        } catch (IOException | InterruptedException e) {
            retryCounter.incrementAndGet();
            System.err.println("Push retry failed: " + e.getMessage());
        }
    }

    private boolean hasUncommittedChanges() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain");
        pb.directory(new File(GIT_REPO_DIR));
        Process p = pb.start();
        String statusOutput = readProcessOutput(p);
        p.waitFor();
        return !statusOutput.trim().isEmpty();
    }

    private String readProcessOutput(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }
}