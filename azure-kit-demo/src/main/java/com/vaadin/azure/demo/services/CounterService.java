package com.vaadin.azure.demo.services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

@Service
public class CounterService implements HasLogger {

    private final File file;

    public CounterService() {
        this.file = new File("counter.txt");
    }

    public synchronized String incrementAndGet() {
        try {
            if (!file.exists()) {
                FileUtils.write(file, "0", StandardCharsets.UTF_8);
            }
            String oldCount = FileUtils.readFileToString(file,
                    StandardCharsets.UTF_8);
            String newCount = String.valueOf(Integer.parseInt(oldCount) + 1);
            FileUtils.writeStringToFile(file, newCount, StandardCharsets.UTF_8);
            getLogger().info("Counter set to " + newCount);
            return newCount;
        } catch (IOException e) {
            getLogger().error("Unable to read/update counter file", e);
            return "error";
        }
    }
}
