package cn.hydrogen.launcher;

import android.view.*;

/** Measures spanning tiles against the actual height, without fixed pixel row heights. */
final class QuickPanel extends ViewGroup {
    private final int gap;
    QuickPanel(MainActivity activity){super(activity);gap=activity.u(10);setContentDescription("五行两列快捷区");}
    static final class TileParams extends ViewGroup.LayoutParams {
        final QuickGrid.Cell cell;
        TileParams(QuickGrid.Cell cell){super(0,0);this.cell=cell;}
    }
    private int edge(int size,int index,int count){return Math.round((size+gap)*index/(float)count);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);
        for(int n=0;n<getChildCount();n++){View view=getChildAt(n);QuickGrid.Cell cell=((TileParams)view.getLayoutParams()).cell;int w=edge(width,cell.column+cell.columns,QuickGrid.COLUMNS)-edge(width,cell.column,QuickGrid.COLUMNS)-gap,h=edge(height,cell.row+cell.rows,QuickGrid.ROWS)-edge(height,cell.row,QuickGrid.ROWS)-gap;view.measure(MeasureSpec.makeMeasureSpec(Math.max(0,w),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,h),MeasureSpec.EXACTLY));}
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){for(int n=0;n<getChildCount();n++){View view=getChildAt(n);QuickGrid.Cell cell=((TileParams)view.getLayoutParams()).cell;int x=edge(getWidth(),cell.column,QuickGrid.COLUMNS),y=edge(getHeight(),cell.row,QuickGrid.ROWS);view.layout(x,y,x+view.getMeasuredWidth(),y+view.getMeasuredHeight());}}
}
