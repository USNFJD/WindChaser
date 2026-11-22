package cn.hydrogen.launcher;
import android.content.*;
import java.util.*;
/** Restores internal caches as well as exported user settings after synthetic fixtures. */
final class TestPreferences implements java.io.Closeable {
    private final SharedPreferences prefs;private final Map<String,?> values;
    TestPreferences(Context context){prefs=new Config(context).p;values=new HashMap<>(prefs.getAll());}
    @SuppressWarnings("unchecked") public void close(){SharedPreferences.Editor editor=prefs.edit().clear();for(Map.Entry<String,?> saved:values.entrySet()){Object value=saved.getValue();String key=saved.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);}editor.commit();}
}
