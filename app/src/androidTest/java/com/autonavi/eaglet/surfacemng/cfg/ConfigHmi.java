package com.autonavi.eaglet.surfacemng.cfg;
import cn.windchaser.mapbridge.MapCardBridge;
/** TEST ONLY. Models the independent PluginDummy HMI foreground properties. */
public final class ConfigHmi {
    public static final Flag CONFIG_u_forebackState=new Flag();
    public static final Flag CONFIG_u_wndFocusState=new Flag();
    public static final class Flag {
        private String value="0";
        public String a(){return value;}
        public void a(String next){value=MapCardBridge.hmiValue(this,next);}
    }
}
