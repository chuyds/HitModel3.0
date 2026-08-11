package com.opc.write;

public class TagWriteTask {
    public String tag;
    public Object value;
    public String type;
    public long periodMs;
    public int maxCount;
    public int counter = 0;

    public TagWriteTask(String tag, Object value, String type, long periodMs, int maxCount) {
        this.tag = tag;
        this.value = value;
        this.type = type;
        this.periodMs = periodMs;
        this.maxCount = maxCount;
    }

    public boolean shouldContinue() {
        return maxCount < 0 || counter < maxCount;
    }
}
