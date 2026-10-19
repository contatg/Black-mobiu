package com.racas.black;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Edits only root keys. Native key bindings and custom control tables remain intact.
final class LauncherSettings {
    private final File file;
    private String text;
    LauncherSettings(File file) throws IOException {
        this.file = file;
        text = file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
    }
    private int rootEnd() {
        Matcher m = Pattern.compile("(?m)^\\s*\\[").matcher(text);
        return m.find() ? m.start() : text.length();
    }
    String raw(String key, String fallback) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*([^\\r\\n#]+)").matcher(text.substring(0, rootEnd()));
        return m.find() ? m.group(1).trim() : fallback;
    }
    float number(String key, float fallback) {
        try { return Float.parseFloat(raw(key, "")); } catch (NumberFormatException e) { return fallback; }
    }
    boolean flag(String key, boolean fallback) {
        String s = raw(key, "");
        return s.equals("true") || (!s.equals("false") && fallback);
    }
    void put(String key, Object value) {
        int end = rootEnd();
        String root = text.substring(0, end);
        Pattern p = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=[^\\r\\n]*");
        Matcher m = p.matcher(root);
        String line = key + " = " + value;
        root = m.find() ? m.replaceFirst(Matcher.quoteReplacement(line)) : root + (root.endsWith("\n") || root.isEmpty() ? "" : "\n") + line + "\n";
        text = root + text.substring(end);
    }
    void save() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Pasta indisponível");
        File temp = new File(file.getPath() + ".launcher.tmp");
        Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
