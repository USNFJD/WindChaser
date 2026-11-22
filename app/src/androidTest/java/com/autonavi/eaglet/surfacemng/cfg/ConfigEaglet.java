package com.autonavi.eaglet.surfacemng.cfg;
/** Test-only render flag substitute. No native map library is loaded. */
public final class ConfigEaglet {
    public static final Flag enablePauseRender=new Flag();
    public static final class Flag {public int d;public void b(int value){d=value;}}
}
