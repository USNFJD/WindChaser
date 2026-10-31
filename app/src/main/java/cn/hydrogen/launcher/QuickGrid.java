package cn.hydrogen.launcher;

import java.util.*;
import org.json.*;

/** Five rows, two columns. Empty app slots yield space when a widget is enlarged. */
final class QuickGrid {
    static final int ROWS=5,COLUMNS=2;
    static final String[] IDS={"clock","phone","shortcut0","shortcut1","apps","settings"};
    static final class Cell {
        final String id;final int row,column,rows,columns;
        Cell(String id,int row,int column,int rows,int columns){this.id=id;this.row=row;this.column=column;this.rows=rows;this.columns=columns;}
    }
    static JSONObject parse(String value)throws JSONException{
        JSONObject object=value.isEmpty()?new JSONObject():new JSONObject(value);
        Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();if(!Arrays.asList(IDS).contains(key))throw new JSONException("未知快捷控件");JSONObject size=object.getJSONObject(key);int rows=integer(size,"rows"),columns=integer(size,"columns");if(rows<1||rows>ROWS||columns<1||columns>COLUMNS)throw new JSONException("快捷区尺寸超出范围");}return object;
    }
    private static int integer(JSONObject value,String key)throws JSONException{Object number=value.get(key);if(!(number instanceof Number)||((Number)number).doubleValue()!=((Number)number).intValue())throw new JSONException("快捷区行列数需要整数");return ((Number)number).intValue();}
    static List<Cell> cells(Config config){try{List<Cell> layout=plan(parse(config.s("quickLayout","")),config);if(layout!=null)return layout;}catch(JSONException ignored){}try{return plan(new JSONObject(),config);}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    static String resize(Config config,String id,int rows,int columns)throws JSONException{
        JSONObject values=parse(config.s("quickLayout",""));values.put(id,new JSONObject().put("rows",rows).put("columns",columns));parse(values.toString());
        if(plan(values,config)==null)throw new JSONException("空间不足，请先缩小其他控件或清空快捷应用");return values.toString();
    }
    private static List<Cell> plan(JSONObject sizes,Config config)throws JSONException{
        for(int keep=3;keep>=0;keep--){
            boolean[][] used=new boolean[ROWS][COLUMNS];ArrayList<Cell> result=new ArrayList<>();boolean fit=true;
            for(String id:IDS){
                if(id.startsWith("shortcut")){int slot=id.endsWith("0")?0:1;if(config.s("quickSlot"+slot,"").isEmpty()&&(keep&(1<<slot))==0)continue;}
                JSONObject size=sizes.optJSONObject(id);int rows=size==null?(id.equals("clock")?2:1):size.getInt("rows"),columns=size==null?(id.equals("clock")||id.equals("phone")?2:1):size.getInt("columns");
                Cell found=null;for(int r=0;r+rows<=ROWS&&found==null;r++)for(int c=0;c+columns<=COLUMNS&&found==null;c++){
                    boolean free=true;for(int y=r;y<r+rows;y++)for(int x=c;x<c+columns;x++)if(used[y][x])free=false;
                    if(free){found=new Cell(id,r,c,rows,columns);for(int y=r;y<r+rows;y++)for(int x=c;x<c+columns;x++)used[y][x]=true;}
                }
                if(found==null){fit=false;break;}result.add(found);
            }
            if(fit)return result;
        }return null;
    }
}
