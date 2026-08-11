package com.opc.write;

import java.util.*;

public class OPCWriteRunner {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(OPCWriteRunner.class);

    public static void main(String[] args) {

        try {
            Properties cfg = ConfigLoader.loadProperties("config/opc.properties");
            List<String[]> rows = ConfigLoader.loadTagTasks("config/write-tags.txt");

            long globalInterval = Long.parseLong(cfg.getProperty("write.interval", "1000"));
            long roundDelay = Long.parseLong(cfg.getProperty("write.round.delay", "5000"));
            int globalRepeat = Integer.parseInt(cfg.getProperty("write.repeat", "1"));

            OPCWriteClient client = new OPCWriteClient(
                    cfg.getProperty("opc.host"),
                    cfg.getProperty("opc.user"),
                    cfg.getProperty("opc.password"),
                    cfg.getProperty("opc.domain"),
                    cfg.getProperty("opc.clsid")
            );
            client.connect();

            List<TagWriteTask> tasks = new ArrayList<>();

            for (String[] row : rows) {
                String tag = row[0].trim();
                String val = row[1].trim();
                String type = row[2].trim().toLowerCase();

                Object value;
                switch (type) {
                    case "double":
                        value = Double.parseDouble(val);
                        break;
                    case "float":
                        value = Float.parseFloat(val);
                        break;
                    case "bool":
                    case "boolean":
                        value = Boolean.parseBoolean(val);
                        break;
                    default:
                        log.warn("未知类型：{}，默认按double解析", type);
                        value = Double.parseDouble(val);
                }

                long period = (row.length > 3 && !row[3].isEmpty())
                        ? Long.parseLong(row[3])
                        : globalInterval;
                int count = (row.length > 4 && !row[4].isEmpty())
                        ? Integer.parseInt(row[4])
                        : globalRepeat;

                tasks.add(new TagWriteTask(tag, value, type, period, count));
            }

            log.info("开始执行OPC写入任务，共 {} 条指令", tasks.size());

            while (true) {
                boolean hasMore = false;
                for (TagWriteTask t : tasks) {
                    if (t.shouldContinue()) {
                        hasMore = true;
                        client.write(t.tag, t.value);
                        t.counter++;
                        Thread.sleep(t.periodMs);
                    }
                }
                if (!hasMore) break;
                Thread.sleep(roundDelay);
            }

            log.info("全部写入任务已完成");
            client.disconnect();

        } catch (Exception e) {
            log.error("OPC写入任务失败", e);
        }
    }
}
