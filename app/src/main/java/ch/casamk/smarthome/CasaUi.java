package ch.casamk.smarthome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class CasaUi {
    static final int BG=Color.rgb(12,19,32);
    static final int PANEL=Color.rgb(24,36,54);
    static final int PANEL_ALT=Color.rgb(31,46,66);
    static final int WHITE=Color.rgb(246,249,255);
    static final int MUTED=Color.rgb(157,177,198);
    static final int TEAL=Color.rgb(95,225,197);
    static final int GOLD=Color.rgb(255,199,112);
    static final int BLUE=Color.rgb(127,165,255);
    static final int RED=Color.rgb(252,143,138);
    static final int LINE=Color.rgb(42,58,77);
    private CasaUi(){}
    static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
    static GradientDrawable rounded(Activity a,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;
    }
    static GradientDrawable outline(Activity a,int bg,int border,int radius){
        GradientDrawable d=rounded(a,bg,radius);d.setStroke(dp(a,1),border);return d;
    }
    static GradientDrawable gradient(Activity a,int start,int end,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(a,radius));return d;
    }
    static LinearLayout column(Activity a){
        LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    static LinearLayout row(Activity a){
        LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    static TextView label(Activity a,String s,int size,int color,boolean bold){
        TextView v=new TextView(a);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        v.setIncludeFontPadding(true);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return v;
    }
    static TextView title(Activity a,String s,int size){return label(a,s,size,WHITE,true);}
    static TextView muted(Activity a,String s,int size){return label(a,s,size,MUTED,false);}
    static void pad(Activity a,View v,int x,int y){v.setPadding(dp(a,x),dp(a,y),dp(a,x),dp(a,y));}
    static LinearLayout.LayoutParams lp(Activity a,int width,int height,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(a,width),height<0?height:dp(a,height));
        p.topMargin=dp(a,top);return p;
    }
    static Button pill(Activity a,String s,boolean primary,Runnable callback){
        Button b=new Button(a);
        b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setMinWidth(0);b.setMinimumWidth(0);
        b.setTextColor(primary?BG:WHITE);
        b.setBackgroundTintList(null);
        b.setBackground(rounded(a,primary?TEAL:PANEL_ALT,13));
        b.setPadding(dp(a,13),dp(a,1),dp(a,13),dp(a,1));
        b.setOnClickListener(v->callback.run());
        return b;
    }
    static TextView icon(Activity a,String unicode,int color,int size){
        TextView t=label(a,unicode,size,color,true);t.setGravity(Gravity.CENTER);return t;
    }
    static void gap(Activity a,LinearLayout host,int h){
        View space=new View(a);host.addView(space,new LinearLayout.LayoutParams(1,dp(a,h)));
    }
}
