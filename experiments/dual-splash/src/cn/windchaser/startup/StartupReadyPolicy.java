package cn.windchaser.startup;

/** Track the original artwork only; the native render cover is not a startup screen. */
final class StartupReadyPolicy {
    private boolean observed,visible,ended;
    void created(){observed=false;visible=false;ended=false;}
    void added(){observed=true;visible=true;ended=false;}
    void removed(){observed=true;visible=false;ended=true;}
    boolean artworkEnded(){return ended;}
    void resumed(){if(!observed){observed=true;visible=false;}}
    boolean visible(){return visible;}
    boolean ready(boolean actualSplashVisible,boolean renderCoverVisible){return observed&&!actualSplashVisible;}
}
