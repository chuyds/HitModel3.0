package com.opc.write;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigLoader {

    public static Properties loadProperties(String file) throws Exception {
        Properties prop = new Properties();
        try (InputStreamReader in = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            prop.load(in);
        }
        return prop;
    }

    public static List<String[]> loadTagTasks(String file) throws Exception {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                list.add(line.split(",", -1));
            }
        }
        return list;
    }
}
