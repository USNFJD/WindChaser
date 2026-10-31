package cn.hydrogen.launcher;
import android.app.*;
import android.content.*;
import android.view.*;
/** Styles before the first frame, while preserving caller validation/show listeners. */
final class ThemeDialog {
    static final class Builder extends AlertDialog.Builder {
        private final Context owner;private int itemCount=-1,heightDp;private View contentPanel;private float widthFraction=1f;private final java.util.WeakHashMap<AlertDialog,Integer> requestedWidths=new java.util.WeakHashMap<>();
        Builder(Context c){super(c);owner=c;}
        Builder widthFraction(float value){widthFraction=value;return this;}
        Builder heightDp(int value){heightDp=value;return this;}
        Builder panel(View value){contentPanel=value;return this;}
        @Override public Builder setItems(CharSequence[] items,DialogInterface.OnClickListener listener){itemCount=items==null?0:items.length;super.setItems(items,listener);return this;}
        @Override public Builder setSingleChoiceItems(CharSequence[] items,int checked,DialogInterface.OnClickListener listener){itemCount=items==null?0:items.length;super.setSingleChoiceItems(items,checked,listener);return this;}
        private void shown(AlertDialog dialog){
            if(!dialog.isShowing()||dialog.getWindow()==null)return;
            // Custom panels replace platform alert chrome before their first draw.
            // The decor/window identity remains unchanged for map focus tracking.
            Window window=dialog.getWindow();
            if(contentPanel!=null&&contentPanel.getParent()==null)window.setContentView(contentPanel);
            ThemeStyle.dialogWindow(dialog,contentPanel!=null);ThemeStyle.watchDialog(dialog);window.setWindowAnimations(0);
            Integer width=requestedWidths.get(dialog);if(width!=null&&window.getAttributes().width!=width){WindowManager.LayoutParams p=window.getAttributes();p.width=width;window.setAttributes(p);}
            // AlertDialog.onStart may restore WRAP_CONTENT; keep explicit panel heights stable.
            if(heightDp>0){int height=Math.min(Ui.dp(owner,heightDp),Math.round(owner.getResources().getDisplayMetrics().heightPixels*.62f));if(window.getAttributes().height!=height){WindowManager.LayoutParams p=window.getAttributes();p.height=height;window.setAttributes(p);}}
            if(owner instanceof Activity)window.getDecorView().setSystemUiVisibility(((Activity)owner).getWindow().getDecorView().getSystemUiVisibility());
            if(owner instanceof MainActivity)((MainActivity)owner).mapDialogShown(dialog);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
        @Override public AlertDialog show(){
            AlertDialog dialog=create();
            MainActivity host=owner instanceof MainActivity?(MainActivity)owner:null;if(host!=null)host.mapDialogShowing(dialog);
            try{dialog.show();shown(dialog);}catch(RuntimeException error){if(host!=null)host.mapDialogFailed(dialog);throw error;}
            return dialog;
        }
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();Window window=dialog.getWindow();
            if(window!=null){window.setWindowAnimations(0);if(owner instanceof Activity)window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_FULLSCREEN);}
            if(itemCount>=0)ThemeStyle.prepareMenu(dialog,itemCount);
            if(window!=null&&widthFraction!=1f){WindowManager.LayoutParams p=window.getAttributes();p.width=ThemeStyle.dialogWidth(owner,p.width,widthFraction);requestedWidths.put(dialog,p.width);window.setAttributes(p);}
            if(window!=null&&heightDp>0){WindowManager.LayoutParams p=window.getAttributes();
                p.height=Math.min(Ui.dp(owner,heightDp),Math.round(owner.getResources().getDisplayMetrics().heightPixels*.62f));window.setAttributes(p);}
            if(owner instanceof MainActivity)((MainActivity)owner).prepareMapDialog(dialog);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->shown(dialog));return dialog;
        }
    }
}
