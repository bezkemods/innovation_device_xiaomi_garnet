package org.lineageos.settings.logcatviewer;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.lineageos.settings.R;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogcatAdapter extends ArrayAdapter<LogEntry> {
    private final LayoutInflater inflater;
    private String highlightFilter = "";
    private boolean isDarkTheme;
    
    // Log level colors
    private static final int COLOR_VERBOSE = 0xFF808080;  // Gray
    private static final int COLOR_DEBUG = 0xFF0000FF;    // Blue
    private static final int COLOR_INFO = 0xFF00AA00;     // Green
    private static final int COLOR_WARN = 0xFFFF8800;     // Orange
    private static final int COLOR_ERROR = 0xFFFF0000;    // Red
    private static final int COLOR_FATAL = 0xFF8B0000;    // Dark Red
    
    private static final int COLOR_VERBOSE_BG = 0xFF404040;
    private static final int COLOR_DEBUG_BG = 0xFF000040;
    private static final int COLOR_INFO_BG = 0xFF004000;
    private static final int COLOR_WARN_BG = 0xFF404000;
    private static final int COLOR_ERROR_BG = 0xFF400000;
    
    public LogcatAdapter(Context context, List<LogEntry> logs) {
        super(context, R.layout.logcat_item, logs);
        this.inflater = LayoutInflater.from(context);
        this.isDarkTheme = isDarkTheme(context);
    }
    
    public void setHighlightFilter(String filter) {
        this.highlightFilter = filter != null ? filter.toLowerCase(Locale.US) : "";
        notifyDataSetChanged();
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.logcat_item, parent, false);
            holder = new ViewHolder();
            holder.logText = convertView.findViewById(R.id.log_text);
            holder.levelIndicator = convertView.findViewById(R.id.level_indicator);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        LogEntry entry = getItem(position);
        if (entry != null) {
            setupLogText(holder, entry);
            setupLevelIndicator(holder, entry.level);
        }
        
        return convertView;
    }
    
    private void setupLogText(ViewHolder holder, LogEntry entry) {
        String logText = entry.getFormattedText();
        
        if (highlightFilter.isEmpty()) {
            holder.logText.setText(logText);
        } else {
            SpannableString spannable = new SpannableString(logText);
            highlightText(spannable, logText.toLowerCase(Locale.US), highlightFilter);
            holder.logText.setText(spannable);
        }
        
        // Set text color based on log level
        int textColor = getLogLevelColor(entry.level);
        holder.logText.setTextColor(textColor);
    }
    
    private void setupLevelIndicator(ViewHolder holder, char level) {
        int color = getLogLevelColor(level);
        holder.levelIndicator.setBackgroundColor(color);
    }
    
    private void highlightText(SpannableString spannable, String text, String filter) {
        int index = text.indexOf(filter);
        while (index >= 0) {
            int endIndex = index + filter.length();
            spannable.setSpan(
                new BackgroundColorSpan(Color.YELLOW),
                index, endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            spannable.setSpan(
                new ForegroundColorSpan(Color.BLACK),
                index, endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            index = text.indexOf(filter, endIndex);
        }
    }
    
    private int getLogLevelColor(char level) {
        switch (level) {
            case 'V': return COLOR_VERBOSE;
            case 'D': return COLOR_DEBUG;
            case 'I': return COLOR_INFO;
            case 'W': return COLOR_WARN;
            case 'E': return COLOR_ERROR;
            case 'F': return COLOR_FATAL;
            default: return isDarkTheme ? Color.WHITE : Color.BLACK;
        }
    }
    
    private boolean isDarkTheme(Context context) {
        // Simple dark theme detection
        int nightModeFlags = context.getResources().getConfiguration().uiMode 
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    
    private static class ViewHolder {
        TextView logText;
        View levelIndicator;
    }
}
