package ch.casamk.smarthome;

import android.graphics.Bitmap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Offline raster-to-plan helper, intentionally conservative:
 * dark long orthogonal runs suggest walls; bounded light areas suggest rooms.
 * It never claims architectural accuracy. User may edit/reject all suggestions.
 */
public final class PlanConverter {
    public static final class Result {
        public final JSONArray walls,rooms;
        public final String notes;
        Result(JSONArray walls,JSONArray rooms,String notes){this.walls=walls;this.rooms=rooms;this.notes=notes;}
    }
    private static final class Segment {
        int x0,y0,x1,y1;
        Segment(int a,int b,int c,int d){x0=a;y0=b;x1=c;y1=d;}
    }
    private PlanConverter(){}
    public static Result analyze(Bitmap original,int darkness){
        if(original==null)return new Result(new JSONArray(),new JSONArray(),"Bild fehlt");
        final int w=Math.max(64,Math.min(320,
            Math.round(560f*original.getWidth()/Math.max(1,original.getHeight()))));
        final int h=Math.max(60,Math.min(560,
            Math.round(original.getHeight()*w/(float)Math.max(1,original.getWidth()))));
        Bitmap bmp=Bitmap.createScaledBitmap(original,w,h,true);
        int[] pixels=new int[w*h];bmp.getPixels(pixels,0,w,0,0,w,h);
        boolean[] dark=new boolean[w*h];
        int black=0;
        for(int i=0;i<dark.length;i++){
            int c=pixels[i],r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            int l=(r*299+g*587+b*114)/1000;
            dark[i]=l<darkness && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<95;
            if(dark[i])black++;
        }
        // Long straight strokes: min 8 pixels; seal small door gaps <=5 pixels.
        boolean[] walls=new boolean[w*h];
        ArrayList<Segment> lines=new ArrayList<>();
        for(int y=2;y<h-2;y++){
            int x=0;
            while(x<w){
                while(x<w&&!dark[y*w+x])x++;
                int start=x;
                while(x<w){
                    if(dark[y*w+x]){x++;continue;}
                    int gap=x;
                    while(x<w&&!dark[y*w+x]&&x-gap<=5)x++;
                    if(x<w&&dark[y*w+x]&&x-gap<=5)continue;
                    x=gap;break;
                }
                if(x-start>=10){
                    lines.add(new Segment(start,y,x-1,y));
                    for(int xx=start;xx<x;xx++)walls[y*w+xx]=true;
                }
                if(x==start)x++;
            }
        }
        for(int x=2;x<w-2;x++){
            int y=0;
            while(y<h){
                while(y<h&&!dark[y*w+x])y++;
                int start=y;
                while(y<h){
                    if(dark[y*w+x]){y++;continue;}
                    int gap=y;
                    while(y<h&&!dark[y*w+x]&&y-gap<=5)y++;
                    if(y<h&&dark[y*w+x]&&y-gap<=5)continue;
                    y=gap;break;
                }
                if(y-start>=10){
                    lines.add(new Segment(x,start,x,y-1));
                    for(int yy=start;yy<y;yy++)walls[yy*w+x]=true;
                }
                if(y==start)y++;
            }
        }
        // Add thickness and bridge aligned components, so small gaps do not
        // merge every room into one enormous flood-fill region.
        boolean[] barrier=new boolean[w*h];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(walls[y*w+x]){
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)barrier[(y+dy)*w+x+dx]=true;
        }
        JSONArray vectorWalls=new JSONArray();
        // Coalesce adjacent horizontal scanlines and vertical runs.
        int count=0;
        for(Segment seg:lines){
            if(count>=500)break;
            if(seg.y0==seg.y1 && seg.y0%3!=0)continue;
            if(seg.x0==seg.x1 && seg.x0%3!=0)continue;
            try{
                JSONObject obj=new JSONObject();
                obj.put("x1",seg.x0/(double)w);obj.put("y1",seg.y0/(double)h);
                obj.put("x2",seg.x1/(double)w);obj.put("y2",seg.y1/(double)h);
                vectorWalls.put(obj);count++;
            }catch(Exception ignored){}
        }
        int[] seen=new int[w*h],queue=new int[w*h];
        List<int[]> regions=new ArrayList<>();
        int minArea=Math.max(120,w*h/320);
        int maxArea=w*h*45/100;
        for(int y=4;y<h-4;y++)for(int x=4;x<w-4;x++){
            int start=y*w+x;
            if(barrier[start]||seen[start]!=0)continue;
            int head=0,tail=0;
            queue[tail++]=start;seen[start]=1;
            int minX=x,maxX=x,minY=y,maxY=y;
            boolean touchesBorder=false;
            while(head<tail){
                int v=queue[head++],vx=v%w,vy=v/w;
                if(vx<=2||vx>=w-3||vy<=2||vy>=h-3)touchesBorder=true;
                if(vx<minX)minX=vx;if(vx>maxX)maxX=vx;
                if(vy<minY)minY=vy;if(vy>maxY)maxY=vy;
                if(vx>0){int n=v-1;if(!barrier[n]&&seen[n]==0){seen[n]=1;queue[tail++]=n;}}
                if(vx<w-1){int n=v+1;if(!barrier[n]&&seen[n]==0){seen[n]=1;queue[tail++]=n;}}
                if(vy>0){int n=v-w;if(!barrier[n]&&seen[n]==0){seen[n]=1;queue[tail++]=n;}}
                if(vy<h-1){int n=v+w;if(!barrier[n]&&seen[n]==0){seen[n]=1;queue[tail++]=n;}}
            }
            int bounds=(maxX-minX+1)*(maxY-minY+1);
            if(!touchesBorder&&tail>=minArea&&tail<=maxArea && tail>=bounds*0.42f
                && maxX-minX>15&&maxY-minY>15){
                regions.add(new int[]{minX,minY,maxX,maxY,tail});
            }
        }
        regions.sort((a,b)->Integer.compare(b[4],a[4]));
        JSONArray suggestedRooms=new JSONArray();
        int index=0;
        for(int[] r:regions){
            if(index>=24)break;
            JSONObject room=new JSONObject();JSONArray points=new JSONArray();
            double inset=2;
            addPoint(points,(r[0]+inset)/w,(r[1]+inset)/h);
            addPoint(points,(r[2]-inset)/w,(r[1]+inset)/h);
            addPoint(points,(r[2]-inset)/w,(r[3]-inset)/h);
            addPoint(points,(r[0]+inset)/w,(r[3]-inset)/h);
            try{
                room.put("id","detected_"+index);room.put("name","Raum "+(index+1));
                room.put("points",points);room.put("auto",true);
                suggestedRooms.put(room);index++;
            }catch(Exception ignored){}
        }
        return new Result(vectorWalls,suggestedRooms,
          suggestedRooms.length()+" Raumvorschläge · "+vectorWalls.length()+" Wandsegmente. Bitte mit dem Originalplan vergleichen.");
    }
    private static void addPoint(JSONArray points,double x,double y){
        JSONArray point=new JSONArray();point.put(x);point.put(y);points.put(point);
    }
}
