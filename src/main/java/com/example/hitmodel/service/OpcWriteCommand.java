package com.example.hitmodel.service;

public class OpcWriteCommand {

    private final String tag;
    private final Object value;

    private OpcWriteCommand(String tag, Object value) {
        this.tag = tag;
        this.value = value;
    }

    public String getTag() {
        return tag;
    }

    public Object getValue() {
        return value;
    }

    public static OpcWriteCommand parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new BadFormatException("写入命令为空");
        }

        String[] parts = line.trim().split(",", 3);
        if (parts.length < 2) {
            throw new BadFormatException("写入命令格式应为 tag,value,type");
        }

        String tag = parts[0].trim();
        String valueText = parts[1].trim();
        String type = parts.length == 3 ? parts[2].trim().toLowerCase() : "double";

        if (tag.isEmpty() || valueText.isEmpty()) {
            throw new BadFormatException("tag 或 value 为空");
        }

        Object value;
        switch (type) {
            case "float":
            case "real":
                value = Float.parseFloat(valueText);
                break;
            case "int":
                value = Integer.parseInt(valueText);
                break;
            case "bool":
            case "boolean":
                value = parseBool(valueText);
                break;
            default:
                value = Double.parseDouble(valueText);
                break;
        }

        return new OpcWriteCommand(tag, value);
    }

    private static boolean parseBool(String valueText) {
        String normalized = valueText.trim().toLowerCase();
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "on".equals(normalized)
                || "yes".equals(normalized);
    }

    public static class BadFormatException extends IllegalArgumentException {
        public BadFormatException(String message) {
            super(message);
        }
    }
}
