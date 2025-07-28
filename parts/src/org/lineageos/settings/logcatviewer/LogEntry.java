package org.lineageos.settings.logcatviewer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogEntry {
    private static final Pattern LOGCAT_PATTERN = Pattern.compile(
        "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+([^:]+):\\s*(.*)$"
    );
    
    public final String timestamp;
    public final String pid;
    public final String tid;
    public final char level;
    public final String tag;
    public final String message;
    public final String rawLine;
    
    private LogEntry(String timestamp, String pid, String tid, char level, String tag, String message, String rawLine) {
        this.timestamp = timestamp;
        this.pid = pid;
        this.tid = tid;
        this.level = level;
        this.tag = tag;
        this.message = message;
        this.rawLine = rawLine;
    }
    
    public static LogEntry parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = LOGCAT_PATTERN.matcher(line);
        if (matcher.matches()) {
            return new LogEntry(
                matcher.group(1),  // timestamp
                matcher.group(2),  // pid
                matcher.group(3),  // tid
                matcher.group(4).charAt(0),  // level
                matcher.group(5),  // tag
                matcher.group(6),  // message
                line
            );
        } else {
            // Fallback for non-standard log lines
            char level = extractLevel(line);
            return new LogEntry("", "", "", level, "", line, line);
        }
    }
    
    private static char extractLevel(String line) {
        if (line.contains(" E ") || line.contains("/E")) return 'E';
        if (line.contains(" W ") || line.contains("/W")) return 'W';
        if (line.contains(" I ") || line.contains("/I")) return 'I';
        if (line.contains(" D ") || line.contains("/D")) return 'D';
        if (line.contains(" V ") || line.contains("/V")) return 'V';
        return 'V'; // Default to verbose
    }
    
    public String getFormattedText() {
        if (timestamp.isEmpty()) {
            return rawLine;
        }
        return String.format("%s %s %s %c %s: %s", 
            timestamp, pid, tid, level, tag, message);
    }
    
    public boolean matchesFilter(String filter, char minLevel) {
        if (filter != null && !filter.isEmpty()) {
            String filterLower = filter.toLowerCase();
            String searchText = (tag + " " + message).toLowerCase();
            if (!searchText.contains(filterLower)) {
                return false;
            }
        }
        
        return isLevelVisible(level, minLevel);
    }
    
    private boolean isLevelVisible(char logLevel, char minLevel) {
        int logLevelPriority = getLevelPriority(logLevel);
        int minLevelPriority = getLevelPriority(minLevel);
        return logLevelPriority >= minLevelPriority;
    }
    
    private int getLevelPriority(char level) {
        switch (level) {
            case 'V': return 1;
            case 'D': return 2;
            case 'I': return 3;
            case 'W': return 4;
            case 'E': return 5;
            case 'F': return 6;
            default: return 1;
        }
    }
}
