package com.emotionagent.common;

public final class MaskUtils {
    private MaskUtils() {}

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@", 2);
        String name = parts[0];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + parts[1];
        }
        return name.substring(0, 2) + "***@" + parts[1];
    }

    /**
     * 仅保留长度与元数据描述，绝不输出私人文本正文。
     */
    public static String safeTextMeta(String label, String text) {
        int len = text == null ? 0 : text.length();
        return label + "(len=" + len + ")";
    }
}
