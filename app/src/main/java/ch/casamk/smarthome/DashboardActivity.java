package ch.casamk.smarthome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CasaMK v0.2 - dashboard-first native Android client.
 * Home Assistant is the single source of truth for real-world device state.
 * Dashboard preferences are stored locally; no secrets are committed to GitHub.
 */
public final class DashboardActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SecureConfig config;
    private DashboardPrefs prefs;
    private DemoRepository demo;
    private HomeRepository remote;
    private HomeLive live;
    private List<HaEntity> entities=new ArrayList<>();
    private LinearLayout root,body,navbar;
    private ScrollView scroll;
    private boolean demoMode=true;
    private boolean editMode=false;
    private boolean pending=false;
    private String view="home", selected="", query="", connection="DEMO";
    private String detailId="";
    private final Runnable autoSync=new Runnable(){
        @Override public void run() {
            if(!demoMode && !pending) refresh(false);
            handler.postDelayed(this,45000);
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        CasaUi.applyTheme(this);
        config=new SecureConfig(this);
        prefs=new DashboardPrefs(this);
        demo=new DemoRepository();
        entities=demo.entities();
        buildShell();
        render();
        if(config.configured())refresh(true);
        String requested=getIntent().getStringExtra("open_entity");
        if(requested!=null&&!requested.isEmpty()){
            detailId=requested;view="entity";render();
        } else if("plan".equals(Appearance.start(this))){
            openPlan();
        }
    }
    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(autoSync);handler.postDelayed(autoSync,45000);
        attachEvents();
    }
    @Override protected void onPause(){
        handler.removeCallbacks(autoSync);
        if(live!=null){live.close();live=null;}
        super.onPause();
    }
    private void attachEvents(){
        if(live!=null){live.close();live=null;}
        if(!config.configured())return;
        try{
            live=new HomeLive(config.url(),config.token(),new HomeLive.Listener(){
                @Override public void onState(HaEntity state){
                    runOnUiThread(()->{
                        if(demoMode)return;
                        boolean replaced=false;
                        for(int i=0;i<entities.size();i++)if(entities.get(i).id.equals(state.id)){
                            entities.set(i,state);replaced=true;break;
                        }
                        if(!replaced)entities.add(state);
                        connection="ONLINE · Live";
                        render();
                    });
                }
                @Override public void onRemoved(String entityId){
                    runOnUiThread(()->{
                        if(demoMode)return;
                        entities.removeIf(e->e.id.equals(entityId));render();
                    });
                }
                @Override public void onConnected(boolean online){
                    if(!online)runOnUiThread(()->{
                        if(!demoMode){connection="Status wird abgefragt";render();}
                    });
                }
            });
        }catch(Exception ex){ /* REST polling remains available */ }
    }
    @Override protected void onDestroy(){handler.removeCallbacks(autoSync);io.shutdownNow();super.onDestroy();}

    private int d(int px){return CasaUi.dp(this,px);}
    private LinearLayout col(){return CasaUi.column(this);}
    private LinearLayout row(){return CasaUi.row(this);}
    private TextView t(String text,int size,int color,boolean bold){return CasaUi.label(this,text,size,color,bold);}
    private LinearLayout.LayoutParams p(int width,int height,int marginTop){return CasaUi.lp(this,width,height,marginTop);}
    private void space(LinearLayout l,int px){CasaUi.gap(this,l,px);}
    private void nav(String screen,String value){
        if("plan".equals(screen)){openPlan();return;}
        view=screen;selected=value;editMode=false;render();scroll.smoothScrollTo(0,0);
    }
    private void openPlan(){startActivity(new Intent(this,FloorPlanActivity.class));}
    private void buildShell(){
        root=col();root.setBackgroundColor(CasaUi.BG);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body=col();body.setPadding(d(18),d(22),d(18),d(30));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        navbar=row();navbar.setBackground(CasaUi.outline(this,CasaUi.BG,CasaUi.LINE,0));
        navbar.setPadding(d(8),d(7),d(8),d(12));
        root.addView(navbar,new LinearLayout.LayoutParams(-1,d(78)));
        // Android 15 enforces edge-to-edge for apps targeting API 35.
        // Keep the dashboard and navigation dock clear of system bars.
        if(android.os.Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                        android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                root.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        setContentView(root);
    }
    private void render(){
        if(body==null)return;
        body.removeAllViews();
        switch(view){
            case "rooms": showRoomsPage();break;
            case "devices": showDevicesPage();break;
            case "category": showCategoryPage(selected);break;
            case "room": showRoomPage(selected);break;
            case "entity": showEntityPage(detailId);break;
            case "settings": showSettingsPage();break;
            default:showHome();
        }
        renderNav();
    }
    private void renderNav(){
        navbar.removeAllViews();
        navItem("⌂","Zuhause","home");
        navItem("▣","Grundriss","plan");
        navItem("▦","Räume","rooms");
        navItem("☷","Geräte","devices");
        navItem("⚙","Mehr","settings");
    }
    private void navItem(String icon,String name,String target){
        boolean active=view.equals(target)||("home".equals(target)&&"home".equals(view));
        LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setPadding(d(4),d(1),d(4),0);
        TextView glyph=t(icon,25,active?CasaUi.TEAL:CasaUi.MUTED,true);
        glyph.setGravity(Gravity.CENTER);
        TextView label=t(name,11,active?CasaUi.WHITE:CasaUi.MUTED,active);
        label.setGravity(Gravity.CENTER);
        item.addView(glyph);item.addView(label);
        navbar.addView(item,new LinearLayout.LayoutParams(0,-1,1f));
        item.setOnClickListener(v->nav(target,""));
    }
    private void section(String title,String caption){
        LinearLayout heading=row();
        TextView titleView=t(title,21,CasaUi.WHITE,true);
        heading.addView(titleView,new LinearLayout.LayoutParams(0,-2,1f));
        if(caption!=null)heading.addView(t(caption,12,CasaUi.MUTED,false));
        body.addView(heading,p(-1,-2,23));
    }
    private void topline(String title,String subtitle,boolean back){
        LinearLayout line=row();
        if(back){
            Button b=CasaUi.pill(this,"‹",false,()->nav("home",""));
            line.addView(b,p(45,44,0));spaceInline(line,8);
        }
        LinearLayout names=col();
        names.addView(t(title,28,CasaUi.WHITE,true));
        names.addView(t(subtitle,12,CasaUi.MUTED,false),p(-1,-2,2));
        line.addView(names,new LinearLayout.LayoutParams(0,-2,1f));
        body.addView(line);
    }
    private void spaceInline(LinearLayout row,int px){
        row.addView(new View(this),new LinearLayout.LayoutParams(d(px),1));
    }
    private void showHome(){
        LinearLayout h=row();
        LinearLayout titles=col();
        titles.addView(t("CASAMK  /  NORDMAZEDONIEN",10,CasaUi.TEAL,true));
        String greeting;
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting=hour<11?"Guten Morgen":hour<18?"Guten Tag":"Guten Abend";
        titles.addView(t(greeting,27,CasaUi.WHITE,true),p(-1,-2,5));
        h.addView(titles,new LinearLayout.LayoutParams(0,-2,1f));
        Button edit=CasaUi.pill(this,editMode?"✓ Fertig":"✎ Bearbeiten",false,()->{editMode=!editMode;render();});
        h.addView(edit,p(-2,40,0));body.addView(h);
        space(body,18);
        hero();
        if("combined".equals(Appearance.start(this)))showPlanPreview();
        if(editMode){
            LinearLayout hint=panel(CasaUi.PANEL);
            hint.addView(t("Startbildschirm anpassen",16,CasaUi.WHITE,true));
            hint.addView(t("☆ Kachel anheften · ↑ ↓ Reihenfolge ändern. Änderungen werden automatisch gespeichert.",13,CasaUi.MUTED,false),p(-1,-2,7));
            space(body,10);
        }
        List<String> pinned=pinnedItems();
        if(!pinned.isEmpty()){
            section("Deine Favoriten",pinned.size()+" angeheftet");
            tileGrid(pinned,"pin");
        }
        section("Räume","Anpassen & öffnen");
        tileGrid(roomKeys(),"room");
        section("Steuerung","Nach Funktion");
        tileGrid(groupKeys(),"group");
        if(pinned.isEmpty()){
            TextView tip=t("Tipp: Tippe auf ☆, um Räume, Funktionen oder Geräte oben anzuheften.",12,CasaUi.MUTED,false);
            body.addView(tip,p(-1,-2,13));
        }
    }
    private void showPlanPreview(){
        section("Hausgrundriss","Interaktiver 2D-Plan");
        LinearLayout preview=panel(CasaUi.PANEL);
        FloorPlanStore plans=new FloorPlanStore(this);
        FloorPlanStore.Floor floor=plans.at(0);
        Bitmap image=plans.getBitmap(floor);
        if(image!=null){
            ImageView thumbnail=new ImageView(this);
            thumbnail.setImageBitmap(image);
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            thumbnail.setBackgroundColor(Appearance.oled(this)?0xFF161A1E:0xFFFFFFFF);
            preview.addView(thumbnail,p(-1,150,4));
        }else{
            preview.addView(t("Noch kein Plan importiert. Lege deinen Bauplan an und positioniere Geräte direkt darin.",
                13,CasaUi.MUTED,false),p(-1,-2,9));
        }
        Button open=CasaUi.pill(this,"Grundriss öffnen →",true,this::openPlan);
        preview.addView(open,p(-1,44,12));
    }
    private void hero(){
        LinearLayout hero=col();
        hero.setPadding(d(20),d(19),d(20),d(20));
        hero.setBackground(Appearance.oled(this)
            ?CasaUi.gradient(this,0xFF153F3B,0xFF17202A,24)
            :CasaUi.gradient(this,0xFFEAF8F3,0xFFEAF1FC,24));
        LinearLayout top=row();
        LinearLayout left=col();
        left.addView(t("MEIN ZUHAUSE",11,CasaUi.TEAL,true));
        left.addView(t(demoMode?"Entdecke dein Smart Home":"Dein Haus im Überblick",19,CasaUi.WHITE,true),p(-1,-2,7));
        top.addView(left,new LinearLayout.LayoutParams(0,-2,1f));
        TextView status=t(demoMode?"● DEMO":pending?"● VERBINDEN":"● "+connection,10,
                demoMode?CasaUi.GOLD:CasaUi.TEAL,true);
        status.setPadding(d(10),d(7),d(10),d(7));
        status.setBackground(CasaUi.rounded(this,CasaUi.PANEL,16));
        top.addView(status);
        hero.addView(top);
        int lights=0,lit=0, climate=0;
        for(HaEntity e:entities){
            if("light".equals(e.domain)){lights++;if("on".equals(e.state))lit++;}
            if("climate".equals(e.domain))climate++;
        }
        LinearLayout metrics=row();space(hero,22);
        metric(metrics,String.valueOf(lit),"Lichter an",CasaUi.GOLD);
        metric(metrics,String.valueOf(climate),"Klimazonen",CasaUi.TEAL);
        metric(metrics,String.valueOf(entities.size()),"Geräte",CasaUi.WHITE);
        hero.addView(metrics);
        LinearLayout actions=row();space(hero,18);
        addAction(actions,"Alle Lichter aus",()->allLightsOff(),true);
        addAction(actions,"Heizung öffnen",()->nav("category",ModuleRegistry.HEATING),false);
        hero.addView(actions);
        body.addView(hero);
    }
    private void metric(LinearLayout host,String value,String subtitle,int color){
        LinearLayout block=col();
        block.addView(t(value,26,color,true));
        block.addView(t(subtitle,12,CasaUi.WHITE,false),p(-1,-2,1));
        host.addView(block,new LinearLayout.LayoutParams(0,-2,1f));
    }
    private void addAction(LinearLayout host,String title,Runnable callback,boolean primary){
        Button b=CasaUi.pill(this,title,primary,callback);
        LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,d(43),1f);
        x.setMargins(d(3),0,d(3),0);host.addView(b,x);
    }
    private List<String> roomKeys(){
        List<String> rooms=prefs.rooms();
        for(HaEntity e:entities){
            String name=prefs.room(e,demoMode);
            if(!rooms.contains(name))rooms.add(name);
        }
        List<String> ordered=prefs.ordered("room",rooms);
        List<String> result=new ArrayList<>();
        for(String name:ordered)if(!prefs.hidden("room:"+name))result.add("room:"+name);
        return result;
    }
    private List<String> groupKeys(){
        List<String> groups=Arrays.asList(ModuleRegistry.LIGHT,ModuleRegistry.HEATING,
                ModuleRegistry.SECURITY,ModuleRegistry.CAMERAS,ModuleRegistry.SENSORS,ModuleRegistry.MORE);
        List<String> result=new ArrayList<>();
        for(String name:prefs.ordered("group",groups)){
            if(!prefs.hidden("group:"+name))result.add("group:"+name);
        }
        return result;
    }
    private List<String> pinnedItems(){
        List<String> ids=new ArrayList<>();
        for(String key:roomKeys())if(prefs.isFavorite(key))ids.add(key);
        for(String key:groupKeys())if(prefs.isFavorite(key))ids.add(key);
        for(HaEntity e:entities)if(prefs.isFavorite("entity:"+e.id))ids.add("entity:"+e.id);
        return prefs.ordered("pin",ids);
    }
    private void tileGrid(List<String> ids,String section){
        for(int i=0;i<ids.size();i+=2){
            LinearLayout r=row();r.setGravity(Gravity.TOP);
            for(int x=i;x<i+2;x++){
                if(x<ids.size()){
                    LinearLayout tile=tile(ids.get(x),section,ids);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
                    lp.setMargins(d(4),d(4),d(4),d(4));
                    r.addView(tile,lp);
                }else{
                    r.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
                }
            }
            body.addView(r);
        }
    }
    private LinearLayout tile(String id,String section,List<String> items){
        String kind=id.substring(0,id.indexOf(':'));
        String name=id.substring(id.indexOf(':')+1);
        int color=kind.equals("room")?CasaUi.BLUE:kind.equals("group")?groupColor(name):CasaUi.GOLD;
        String title=kind.equals("entity")?findName(name):name;
        String icon=kind.equals("room")?roomIcon(name):kind.equals("group")?groupIcon(name):"◈";
        String subtitle=kind.equals("room")?roomSubtitle(name):kind.equals("group")?groupSubtitle(name):stateFor(name);
        LinearLayout panel=col();
        panel.setMinimumHeight(d(editMode?167:140));
        panel.setPadding(d(13),d(13),d(12),d(12));
        panel.setBackground(CasaUi.outline(this,CasaUi.PANEL,CasaUi.LINE,20));
        LinearLayout upper=row();
        TextView symbol=t(icon,25,color,true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(CasaUi.rounded(this,CasaUi.PANEL_ALT,13));
        upper.addView(symbol,p(42,42,0));
        upper.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
        TextView star=t(prefs.isFavorite(id)?"★":"☆",25,prefs.isFavorite(id)?CasaUi.GOLD:CasaUi.MUTED,true);
        star.setPadding(d(7),0,d(2),d(8));
        upper.addView(star);
        star.setOnClickListener(v->{prefs.setFavorite(id,!prefs.isFavorite(id));render();});
        panel.addView(upper);
        TextView main=t(title,16,CasaUi.WHITE,true);main.setMaxLines(2);
        panel.addView(main,p(-1,-2,10));
        TextView sub=t(subtitle,12,CasaUi.MUTED,false);sub.setMaxLines(2);
        panel.addView(sub,p(-1,-2,3));
        if(editMode){
            LinearLayout moves=row();
            String key=kind.equals("room")?"room":kind.equals("group")?"group":"pin";
            String value=kind.equals("entity")?id:name;
            List<String> source=new ArrayList<>();
            for(String v:items)source.add(v.substring(v.indexOf(':')+1));
            if("pin".equals(section)){
                key="pin";value=id;source=new ArrayList<>(items);
            }
            final String orderKey=key, target=value;
            final List<String> orderItems=source;
            addAction(moves,"↑",()->{prefs.move(orderKey,orderItems,target,-1);render();},false);
            addAction(moves,"↓",()->{prefs.move(orderKey,orderItems,target,1);render();},false);
            panel.addView(moves,p(-1,38,8));
        }
        panel.setOnClickListener(v->{
            if(editMode)return;
            if(kind.equals("room"))nav("room",name);
            else if(kind.equals("group"))nav("category",name);
            else entity(name);
        });
        panel.setOnLongClickListener(v->{editMode=true;view="home";render();return true;});
        return panel;
    }
    private String findName(String id){HaEntity e=find(id);return e==null?id:e.name;}
    private String stateFor(String id){HaEntity e=find(id);return e==null?"Unbekannt":displayState(e);}
    private String roomIcon(String room){
        String r=room.toLowerCase(Locale.ROOT);
        if(r.contains("küch"))return "♨";
        if(r.contains("schlaf"))return "☾";
        if(r.contains("bad"))return "◈";
        if(r.contains("garten")||r.contains("aussen"))return "☘";
        if(r.contains("eingang")||r.contains("flur"))return "⌑";
        return "⌂";
    }
    private String groupIcon(String group){
        if(group.equals(ModuleRegistry.LIGHT))return "☀";
        if(group.equals(ModuleRegistry.HEATING))return "♨";
        if(group.equals(ModuleRegistry.SECURITY))return "⌑";
        if(group.equals(ModuleRegistry.CAMERAS))return "◎";
        if(group.equals(ModuleRegistry.SENSORS))return "◈";
        return "◇";
    }
    private int groupColor(String group){
        if(group.equals(ModuleRegistry.LIGHT))return CasaUi.GOLD;
        if(group.equals(ModuleRegistry.HEATING))return CasaUi.TEAL;
        if(group.equals(ModuleRegistry.SECURITY))return CasaUi.BLUE;
        if(group.equals(ModuleRegistry.CAMERAS))return CasaUi.RED;
        return CasaUi.MUTED;
    }
    private String roomSubtitle(String name){
        int total=0,on=0;
        for(HaEntity e:entities)if(name.equals(prefs.room(e,demoMode))){
            total++;if("light".equals(e.domain)&&"on".equals(e.state))on++;
        }
        return total==0?"Noch keine Geräte":total+" Geräte · "+on+" Lichter an";
    }
    private String groupSubtitle(String name){
        int count=0,active=0;
        for(HaEntity e:entities)if(name.equals(ModuleRegistry.category(e.domain))){
            count++;
            if("on".equals(e.state)||"heat".equals(e.state)||"cool".equals(e.state))active++;
        }
        return count==0?"Noch nichts verbunden":count+" Geräte · "+active+" aktiv";
    }
    private void showRoomsPage(){
        topline("Deine Räume","Alles nach Zimmer organisiert",false);
        section("Raumübersicht",prefs.rooms().size()+" angelegt");
        tileGrid(roomKeys(),"room");
        Button add=CasaUi.pill(this,"＋ Neuen Raum anlegen",true,this::newRoom);
        body.addView(add,p(-1,48,18));
        body.addView(t("Tippe auf ein Gerät, um es einem Raum zuzuordnen.",13,CasaUi.MUTED,false),p(-1,-2,11));
    }
    private void showCategoryPage(String name){
        topline(name,"Alle Geräte nach Funktion",true);
        List<HaEntity> filtered=new ArrayList<>();
        for(HaEntity e:entities)if(name.equals(ModuleRegistry.category(e.domain)))filtered.add(e);
        listDevices(filtered);
    }
    private void showRoomPage(String roomName){
        topline(roomName,"Raumsteuerung · "+roomSubtitle(roomName),true);
        LinearLayout h=row();
        addAction(h,"✎ Umbenennen",()->renameRoom(roomName),false);
        addAction(h,"＋ Geräte zuordnen",this::assignmentPicker,false);
        body.addView(h,p(-1,44,18));
        List<HaEntity> filtered=new ArrayList<>();
        for(HaEntity e:entities)if(roomName.equals(prefs.room(e,demoMode)))filtered.add(e);
        listDevices(filtered);
    }
    private void showDevicesPage(){
        topline("Alle Geräte",entities.size()+" Geräte · Suche und Favoriten",false);
        LinearLayout r=row();
        addAction(r,"⌕ Suchen",this::searchDialog,false);
        addAction(r,"↻ Aktualisieren",()->{if(demoMode){demo=new DemoRepository();entities=demo.entities();render();}else refresh(true);},false);
        body.addView(r,p(-1,45,16));
        if(!query.trim().isEmpty()){
            body.addView(t("Filter: "+query+"   ×",13,CasaUi.TEAL,true),p(-1,-2,11));
            body.getChildAt(body.getChildCount()-1).setOnClickListener(v->{query="";render();});
        }
        List<HaEntity> filtered=new ArrayList<>();
        for(HaEntity e:entities)if(query.isEmpty()||e.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                ||e.id.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))filtered.add(e);
        listDevices(filtered);
    }
    private void listDevices(List<HaEntity> data){
        if(data.isEmpty()){
            LinearLayout panel=panel(CasaUi.PANEL);
            panel.addView(t("Hier sind noch keine Geräte",17,CasaUi.WHITE,true));
            panel.addView(t("Verbinde Home Assistant oder weise Geräte diesem Raum zu.",13,CasaUi.MUTED,false),p(-1,-2,8));
            return;
        }
        for(HaEntity e:data){
            LinearLayout item=row();
            item.setPadding(d(13),d(13),d(12),d(13));
            item.setBackground(CasaUi.outline(this,CasaUi.PANEL,CasaUi.LINE,17));
            LinearLayout.LayoutParams ip=p(-1,-2,9);body.addView(item,ip);
            String icon=groupIcon(ModuleRegistry.category(e.domain));
            TextView badge=t(icon,23,groupColor(ModuleRegistry.category(e.domain)),true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(CasaUi.rounded(this,CasaUi.PANEL_ALT,13));
            item.addView(badge,p(49,49,0));
            spaceInline(item,11);
            LinearLayout labels=col();
            TextView title=t(e.name,15,CasaUi.WHITE,true);
            title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(title);
            labels.addView(t(displayState(e),12,CasaUi.MUTED,false),p(-1,-2,4));
            item.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
            if(("light".equals(e.domain)||"switch".equals(e.domain))&&!e.isUnavailable()){
                TextView power=t("on".equals(e.state)?"●":"○",27,"on".equals(e.state)?CasaUi.TEAL:CasaUi.MUTED,true);
                power.setPadding(d(8),d(3),d(8),d(3));
                item.addView(power);
                power.setOnClickListener(v->act(e,"on".equals(e.state)?"turn_off":"turn_on",null));
            }else{
                item.addView(t("›",27,CasaUi.MUTED,false));
            }
            item.setOnClickListener(v->entity(e.id));
        }
    }
    private void entity(String id){detailId=id;view="entity";editMode=false;render();scroll.smoothScrollTo(0,0);}
    private HaEntity find(String id){for(HaEntity e:entities)if(e.id.equals(id))return e;return null;}
    private String displayState(HaEntity e){
        if(e.isUnavailable())return "Nicht verfügbar";
        switch(e.state){
            case "on":return "Ein";
            case "off":return "Aus";
            case "locked":return "Verriegelt";
            case "unlocked":return "Entriegelt";
            case "heat":return "Heizen";
            case "cool":return "Kühlen";
            case "auto":return "Automatik";
            case "idle":return "Bereit";
            case "open":return "Offen";
            case "closed":return "Geschlossen";
            default:return e.state+" "+e.attributes.optString("unit_of_measurement","");
        }
    }
    private LinearLayout panel(int color){
        LinearLayout panel=col();panel.setPadding(d(17),d(16),d(17),d(16));
        panel.setBackground(CasaUi.outline(this,color,CasaUi.LINE,20));
        body.addView(panel,p(-1,-2,12));return panel;
    }
    private void showEntityPage(String id){
        HaEntity e=find(id);
        if(e==null){topline("Gerät nicht gefunden","Bitte aktualisieren",true);return;}
        topline(e.name,ModuleRegistry.category(e.domain)+" · "+e.id,true);
        LinearLayout status=panel(CasaUi.PANEL);
        LinearLayout top=row();
        LinearLayout statusText=col();
        statusText.addView(t("AKTUELLER STATUS",11,CasaUi.TEAL,true));
        statusText.addView(t(displayState(e),29,CasaUi.WHITE,true),p(-1,-2,8));
        top.addView(statusText,new LinearLayout.LayoutParams(0,-2,1f));
        TextView star=t(prefs.isFavorite("entity:"+id)?"★":"☆",30,CasaUi.GOLD,true);
        star.setPadding(d(10),0,d(4),d(7));
        star.setOnClickListener(v->{prefs.setFavorite("entity:"+id,!prefs.isFavorite("entity:"+id));render();});
        top.addView(star);status.addView(top);
        status.addView(t("Raum: "+prefs.room(e,demoMode),13,CasaUi.MUTED,false),p(-1,-2,10));
        Button assign=CasaUi.pill(this,"Raum zuordnen",false,()->assignRoom(e));
        status.addView(assign,p(-1,42,12));

        if(e.isUnavailable()){
            LinearLayout note=panel(CasaUi.PANEL);note.addView(t("Dieses Gerät ist aktuell nicht erreichbar.",14,CasaUi.MUTED,false));return;
        }
        switch(e.domain){
            case "light":lightControls(e);break;
            case "switch":simpleSwitch(e);break;
            case "climate":climateControls(e);break;
            case "fan":simpleSwitch(e);break;
            case "lock":lockControls(e);break;
            case "cover":coverControls(e);break;
            case "camera":cameraControls(e);break;
            case "scene":sceneControls(e);break;
            case "button":buttonControls(e);break;
            case "media_player":mediaControls(e);break;
            default:
                LinearLayout readOnly=panel(CasaUi.PANEL);
                readOnly.addView(t("Gerätedetails",17,CasaUi.WHITE,true));
                readOnly.addView(t("Status: "+displayState(e),14,CasaUi.MUTED,false),p(-1,-2,10));
        }
        body.addView(t(demoMode?"DEMO · Aktionen steuern nur Beispieldaten":
                "LIVE · Befehle werden an Home Assistant gesendet",11,CasaUi.MUTED,false),p(-1,-2,21));
    }
    private void simpleSwitch(HaEntity e){
        LinearLayout block=panel(CasaUi.PANEL);
        block.addView(t("Steuerung",19,CasaUi.WHITE,true));
        LinearLayout actions=row();
        addAction(actions,"Einschalten",()->act(e,"turn_on",null),true);
        addAction(actions,"Ausschalten",()->act(e,"turn_off",null),false);
        block.addView(actions,p(-1,45,14));
    }
    private void lightControls(HaEntity e){
        simpleSwitch(e);
        if(e.attributes.has("brightness")){
            LinearLayout block=panel(CasaUi.PANEL);
            block.addView(t("Helligkeit",19,CasaUi.WHITE,true));
            int brightness=e.attributes.optInt("brightness",128);
            block.addView(t(Math.round(brightness*100f/255)+" %",22,CasaUi.GOLD,true),p(-1,-2,8));
            SeekBar slider=new SeekBar(this);
            slider.setMax(255);slider.setProgress(brightness);
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(CasaUi.GOLD));
            block.addView(slider,p(-1,45,9));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar seekBar){}
                public void onProgressChanged(SeekBar seekBar,int progress,boolean user){}
                public void onStopTrackingTouch(SeekBar seekBar){
                    JSONObject payload=json("brightness",Math.max(1,seekBar.getProgress()));
                    act(e,"turn_on",payload);
                }
            });
        }
    }
    private void climateControls(HaEntity e){
        LinearLayout climate=panel(CasaUi.PANEL);
        climate.addView(t("Raumklima",19,CasaUi.WHITE,true));
        climate.addView(t("IST-TEMPERATUR",11,CasaUi.MUTED,true),p(-1,-2,17));
        String actual=e.attributes.optString("current_temperature","–");
        climate.addView(t(actual+"°",35,CasaUi.TEAL,true),p(-1,-2,4));
        climate.addView(t("SOLL-TEMPERATUR",11,CasaUi.MUTED,true),p(-1,-2,16));
        String target=e.attributes.optString("temperature","–");
        climate.addView(t(target+"°C",34,CasaUi.WHITE,true),p(-1,-2,4));
        LinearLayout controls=row();
        addAction(controls,"− 0,5°",()->adjustTemperature(e,-0.5),false);
        addAction(controls,"＋ 0,5°",()->adjustTemperature(e,0.5),true);
        climate.addView(controls,p(-1,48,10));
        JSONArray hvac=e.attributes.optJSONArray("hvac_modes");
        if(hvac!=null&&hvac.length()>0){
            LinearLayout modes=panel(CasaUi.PANEL);
            modes.addView(t("Betriebsart",19,CasaUi.WHITE,true));
            rowButtons(modes,hvac,"set_hvac_mode","hvac_mode",e);
        }
        JSONArray fans=e.attributes.optJSONArray("fan_modes");
        if(fans!=null&&fans.length()>0){
            LinearLayout speeds=panel(CasaUi.PANEL);
            speeds.addView(t("Fan-Coil · Lüfterstufe",19,CasaUi.WHITE,true));
            rowButtons(speeds,fans,"set_fan_mode","fan_mode",e);
        }
        LinearLayout help=panel(CasaUi.PANEL);
        help.addView(t("Hinweis zur Heizungsanlage",14,CasaUi.WHITE,true));
        help.addView(t("Fussbodenheizung reagiert verzögert. Heiz- und Kühlfreigaben sowie Frost- und Taupunktschutz müssen in der Anlagenregelung bleiben.",12,CasaUi.MUTED,false),p(-1,-2,8));
    }
    private void rowButtons(LinearLayout panel,JSONArray values,String command,String key,HaEntity e){
        LinearLayout r=row();int count=0;
        for(int i=0;i<values.length();i++){
            String m=values.optString(i,"");
            if(m.isEmpty())continue;
            if(count>0&&count%3==0){panel.addView(r,p(-1,42,9));r=row();}
            final String value=m;
            addAction(r,translated(value),()->act(e,command,json(key,value)),value.equals(e.state)
                    ||value.equals(e.attributes.optString("fan_mode","")));
            count++;
        }
        if(r.getChildCount()>0)panel.addView(r,p(-1,42,9));
    }
    private String translated(String v){
        switch(v){
            case "off":return "Aus";
            case "heat":return "Heizen";
            case "cool":return "Kühlen";
            case "auto":return "Auto";
            case "fan_only":return "Lüften";
            case "low":return "Niedrig";
            case "medium":return "Mittel";
            case "high":return "Hoch";
            default:return v;
        }
    }
    private void adjustTemperature(HaEntity e,double step){
        double val=e.attributes.optDouble("temperature",22);
        double min=e.attributes.optDouble("min_temp",16),max=e.attributes.optDouble("max_temp",28);
        double next=Math.min(max,Math.max(min,Math.round((val+step)*2)/2d));
        act(e,"set_temperature",json("temperature",next));
    }
    private void lockControls(HaEntity e){
        LinearLayout p=panel(CasaUi.PANEL);
        p.addView(t("Türsicherheit",19,CasaUi.WHITE,true));
        p.addView(t("Für das Entriegeln ist eine biometrische oder Gerätesperren-Bestätigung erforderlich.",13,CasaUi.MUTED,false),CasaUi.lp(this,-1,-2,9));
        LinearLayout r=row();
        addAction(r,"Verriegeln",()->act(e,"lock",null),false);
        addAction(r,"Entriegeln",()->biometricUnlock(e),true);
        p.addView(r,CasaUi.lp(this,-1,46,15));
    }
    private void biometricUnlock(HaEntity e){
        try{
            BiometricPrompt prompt=new BiometricPrompt.Builder(this)
                .setTitle("Haustür entriegeln")
                .setSubtitle("Bestätige die Entriegelung von "+e.name)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
            prompt.authenticate(new CancellationSignal(),getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback(){
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
                        act(e,"unlock",null);
                    }
                    @Override public void onAuthenticationError(int code,CharSequence error){
                        message("Entriegelung nicht durchgeführt: "+error);
                    }
                });
        }catch(Exception ex){message("Bitte sichere Bildschirmsperre aktivieren.");}
    }
    private void coverControls(HaEntity e){
        LinearLayout p=panel(CasaUi.PANEL);p.addView(t("Rollladen / Beschattung",19,CasaUi.WHITE,true));
        LinearLayout r=row();
        addAction(r,"Öffnen",()->act(e,"open_cover",null),true);
        addAction(r,"Stoppen",()->act(e,"stop_cover",null),false);
        addAction(r,"Schliessen",()->act(e,"close_cover",null),false);
        p.addView(r,CasaUi.lp(this,-1,46,12));
    }
    private void cameraControls(HaEntity e){
        LinearLayout p=panel(CasaUi.PANEL);
        p.addView(t("Kamera",19,CasaUi.WHITE,true));
        p.addView(t("Standbild sicher über Home Assistant abrufen. Livestream wird später ergänzt.",13,CasaUi.MUTED,false),CasaUi.lp(this,-1,-2,9));
        Button b=CasaUi.pill(this,"Kamerabild laden",true,()->showCamera(e));
        p.addView(b,CasaUi.lp(this,-1,45,12));
    }
    private void sceneControls(HaEntity e){
        LinearLayout p=panel(CasaUi.PANEL);p.addView(t("Szene aktivieren",19,CasaUi.WHITE,true));
        Button b=CasaUi.pill(this,"Szene starten",true,()->act(e,"turn_on",null));
        p.addView(b,CasaUi.lp(this,-1,45,12));
    }
    private void buttonControls(HaEntity e){
        LinearLayout p=panel(CasaUi.PANEL);
        Button b=CasaUi.pill(this,"Aktion ausführen",true,()->act(e,"press",null));
        p.addView(b,CasaUi.lp(this,-1,45,12));
    }
    private void mediaControls(HaEntity e){
        LinearLayout p=panel(CasaUi.PANEL);p.addView(t("Medien",19,CasaUi.WHITE,true));
        Button b=CasaUi.pill(this,"Wiedergabe / Pause",true,()->act(e,"media_play_pause",null));
        p.addView(b,CasaUi.lp(this,-1,45,12));
    }
    private void showCamera(HaEntity e){
        if(demoMode){
            new AlertDialog.Builder(this).setTitle(e.name)
                .setMessage("Demo: Es sind noch keine echten Kameras verbunden.")
                .setPositiveButton("OK",null).show();
            return;
        }
        if(pending)return;
        pending=true;
        io.execute(()->{
            try{
                Bitmap image=remote.snapshot(e);
                runOnUiThread(()->{
                    pending=false;
                    ImageView iv=new ImageView(this);
                    iv.setImageBitmap(image);iv.setAdjustViewBounds(true);
                    new AlertDialog.Builder(this).setTitle(e.name).setView(iv)
                        .setPositiveButton("Schliessen",null).show();
                });
            }catch(Exception err){runOnUiThread(()->{pending=false;message("Kamera: "+err.getMessage());});}
        });
    }
    private static JSONObject json(String key,Object value){
        JSONObject o=new JSONObject();try{o.put(key,value);}catch(Exception ignored){}return o;
    }
    private void act(HaEntity e,String command,JSONObject params){
        if(pending)return;
        if(demoMode){
            try{
                demo.service(e,command,params);
                render();
                message("Demo: "+e.name+" · "+translated(command));
            }catch(Exception ex){message(ex.getMessage());}
            return;
        }
        if(remote==null){message("Keine Verbindung zu Home Assistant");return;}
        pending=true;
        io.execute(()->{
            try{
                remote.service(e,command,params);
                List<HaEntity> next=remote.entities();
                runOnUiThread(()->{
                    entities=next;pending=false;connection="ONLINE";render();
                });
            }catch(Exception ex){
                runOnUiThread(()->{pending=false;message("Befehl fehlgeschlagen: "+ex.getMessage());render();});
            }
        });
    }
    private void allLightsOff(){
        List<HaEntity> lights=new ArrayList<>();
        for(HaEntity e:entities)if("light".equals(e.domain)&&"on".equals(e.state))lights.add(e);
        if(lights.isEmpty()){message("Es sind keine Lichter eingeschaltet.");return;}
        new AlertDialog.Builder(this).setTitle("Alle Lichter ausschalten?")
            .setMessage(lights.size()+" eingeschaltete Lichter werden ausgeschaltet.")
            .setNegativeButton("Abbrechen",null)
            .setPositiveButton("Ausschalten",(dlg,w)->{
                if(demoMode){
                    for(HaEntity e:lights)try{demo.service(e,"turn_off",null);}catch(Exception ignored){}
                    render();return;
                }
                if(pending||remote==null)return;
                pending=true;
                io.execute(()->{
                    int errors=0;
                    for(HaEntity e:lights)try{remote.service(e,"turn_off",null);}catch(Exception ex){errors++;}
                    final int failed=errors;
                    try{
                        List<HaEntity> next=remote.entities();
                        runOnUiThread(()->{entities=next;pending=false;render();
                            if(failed>0)message(failed+" Lichter konnten nicht geschaltet werden.");});
                    }catch(Exception ex){runOnUiThread(()->{pending=false;message(ex.getMessage());render();});}
                });
            }).show();
    }
    private void refresh(boolean showError){
        if(pending||!config.configured())return;
        pending=true;connection="VERBINDE";
        if(showError)render();
        io.execute(()->{
            try{
                HomeRepository repo=new HomeRepository(config.url(),config.token());
                List<HaEntity> next=repo.entities();
                runOnUiThread(()->{
                    remote=repo;entities=next;demoMode=false;pending=false;connection="ONLINE";render();
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    pending=false;connection="OFFLINE";
                    if(showError)message("Verbindung fehlgeschlagen: "+ex.getMessage());
                    render();
                });
            }
        });
    }
    private void searchDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(query);
        input.setHint("Gerätename oder Entität suchen");
        new AlertDialog.Builder(this).setTitle("Geräte durchsuchen").setView(input)
            .setNegativeButton("Abbrechen",null)
            .setNeutralButton("Zurücksetzen",(dialog,w)->{query="";render();})
            .setPositiveButton("Suchen",(dialog,w)->{query=input.getText().toString().trim();render();}).show();
    }
    private void newRoom(){
        EditText input=new EditText(this);
        input.setHint("z. B. Gästezimmer");
        new AlertDialog.Builder(this).setTitle("Neuen Raum erstellen").setView(input)
            .setNegativeButton("Abbrechen",null)
            .setPositiveButton("Erstellen",(dialog,w)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()){message("Bitte einen Raumnamen eingeben.");return;}
                prefs.addRoom(name);render();
            }).show();
    }
    private void renameRoom(String old){
        EditText input=new EditText(this);input.setText(old);input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Raum umbenennen").setView(input)
            .setNegativeButton("Abbrechen",null)
            .setPositiveButton("Speichern",(dialog,w)->{
                String value=input.getText().toString().trim();
                if(!prefs.renameRoom(old,value)){message("Raumname bereits vorhanden oder ungültig");return;}
                selected=value;render();
            }).show();
    }
    private void assignmentPicker(){
        List<String> items=new ArrayList<>();
        for(HaEntity e:entities)items.add(e.name);
        new AlertDialog.Builder(this).setTitle("Gerät auswählen")
            .setItems(items.toArray(new String[0]),(dialog,index)->assignRoom(entities.get(index)))
            .setNegativeButton("Abbrechen",null).show();
    }
    private void assignRoom(HaEntity entity){
        List<String> choices=prefs.rooms();choices.add("Nicht zugeordnet");
        int selected=choices.indexOf(prefs.room(entity,demoMode));
        new AlertDialog.Builder(this).setTitle("Raum für "+entity.name)
            .setSingleChoiceItems(choices.toArray(new String[0]),selected,(dialog,which)->{
                prefs.assignRoom(entity.id,choices.get(which));dialog.dismiss();render();
            }).setNegativeButton("Abbrechen",null)
            .setNeutralButton("＋ Neuer Raum",(dialog,w)->newRoom()).show();
    }
    private void changeTheme(boolean oled){
        Appearance.setTheme(this,oled);
        recreate();
    }
    private void showSettingsPage(){
        topline("Einstellungen","CasaMK · Haus Mazedonien",false);
        section("Verbindung",null);
        LinearLayout box=panel(CasaUi.PANEL);
        box.addView(t(demoMode?"Demo-Modus":"Home Assistant",20,CasaUi.WHITE,true));
        box.addView(t(demoMode?"Alle Aktionen sind Simulationen. Es werden keine echten Geräte gesteuert.":
                "Verbindung über verschlüsselte HTTPS API. Status: "+connection,13,CasaUi.MUTED,false),p(-1,-2,8));
        LinearLayout buttons=row();
        addAction(buttons,"Verbinden",this::connectDialog,true);
        addAction(buttons,"Demo starten",()->{
            demo=new DemoRepository();entities=demo.entities();
            demoMode=true;connection="DEMO";render();
        },false);
        box.addView(buttons,p(-1,44,15));
        section("Design & Startansicht",null);
        LinearLayout design=panel(CasaUi.PANEL);
        design.addView(t("Farbschema",18,CasaUi.WHITE,true));
        design.addView(t("Wähle reines Weiss oder echtes OLED-Schwarz. Alle Ansichten verwenden dieselbe Auswahl.",
            13,CasaUi.MUTED,false),p(-1,-2,6));
        LinearLayout themeRow=row();
        addAction(themeRow,"☀ Vollweiss",()->changeTheme(false),!Appearance.oled(this));
        addAction(themeRow,"● OLED Schwarz",()->changeTheme(true),Appearance.oled(this));
        design.addView(themeRow,p(-1,47,11));
        design.addView(t("Standardansicht beim Öffnen",18,CasaUi.WHITE,true),p(-1,-2,18));
        String chosen=Appearance.start(this);
        for(String option:new String[]{"combined","tiles","plan"}){
            String label="combined".equals(option)?"Kacheln + Planvorschau":
                "tiles".equals(option)?"Nur Kacheln":"Direkt zum Grundriss";
            Button choice=CasaUi.pill(this,(chosen.equals(option)?"✓  ":"○  ")+label,
                chosen.equals(option),()->{
                    Appearance.setStart(this,option);render();
                });
            design.addView(choice,p(-1,43,7));
        }
        Button launchPlan=CasaUi.pill(this,"Grundriss bearbeiten",false,this::openPlan);
        design.addView(launchPlan,p(-1,43,9));
        section("Organisation",null);
        LinearLayout options=panel(CasaUi.PANEL);
        options.addView(t("Räume und Favoriten",19,CasaUi.WHITE,true));
        options.addView(t("Neue Räume erstellen, Geräte zuweisen und Kacheln auf dem Startbildschirm sortieren.",13,CasaUi.MUTED,false),p(-1,-2,8));
        Button room=CasaUi.pill(this,"Räume verwalten",false,()->nav("rooms",""));
        options.addView(room,p(-1,45,12));
        Button edit=CasaUi.pill(this,"Startbildschirm bearbeiten",false,()->{view="home";editMode=true;render();});
        options.addView(edit,p(-1,45,9));
        Button reset=CasaUi.pill(this,"Kachelsortierung zurücksetzen",false,()->{
            new AlertDialog.Builder(this).setTitle("Startbildschirm zurücksetzen?")
                .setMessage("Favoriten und Kachelanordnung werden zurückgesetzt. Geräte-Zuordnungen bleiben erhalten.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Zurücksetzen",(dialog,w)->{
                    prefs.resetHome();render();
                }).show();
        });
        options.addView(reset,p(-1,45,9));
        section("Sicherheit",null);
        LinearLayout security=panel(CasaUi.PANEL);
        security.addView(t("Zugangsdaten",18,CasaUi.WHITE,true));
        security.addView(t("Der Home-Assistant-Token wird verschlüsselt im Android Keystore gespeichert. Keine offenen Routerports verwenden; sicherer HTTPS-Zugriff ist Pflicht.",13,CasaUi.MUTED,false),p(-1,-2,7));
        Button delete=CasaUi.pill(this,"Verbindung entfernen",false,()->{
            new AlertDialog.Builder(this).setTitle("Verbindung löschen?")
                .setNegativeButton("Abbrechen",null)
                .setPositiveButton("Löschen",(dialog,w)->{
                    config.clear();remote=null;demoMode=true;demo=new DemoRepository();entities=demo.entities();
                    connection="DEMO";render();
                }).show();
        });
        security.addView(delete,p(-1,43,12));
        body.addView(t("CasaMK 0.3 · Grundriss-Beta · Kein Ersatz für Sicherheits- und Anlagenregelungen.",11,CasaUi.MUTED,false),p(-1,-2,20));
    }
    private void connectDialog(){
        LinearLayout content=col();content.setPadding(d(17),0,d(17),0);
        EditText host=new EditText(this);
        host.setHint("https://dein-homeassistant.example");
        host.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        host.setSingleLine(true);host.setText(config.url());
        content.addView(host);
        EditText token=new EditText(this);
        token.setHint("Long-lived Access Token");
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSingleLine(true);content.addView(token);
        new AlertDialog.Builder(this).setTitle("Home Assistant verbinden")
            .setMessage("Verwende eine sichere HTTPS-Adresse, z. B. über Home Assistant Cloud oder einen abgesicherten VPN-Zugang.")
            .setView(content).setNegativeButton("Abbrechen",null)
            .setPositiveButton("Verbinden",(dialog,w)->{
                try{
                    String value=token.getText().toString().trim();
                    if(value.isEmpty())value=config.token();
                    config.save(host.getText().toString(),value);
                    attachEvents();
                    refresh(true);
                }catch(Exception ex){message(ex.getMessage());}
            }).show();
    }
    private void message(String m){Toast.makeText(this,m==null?"Unbekannter Fehler":m,Toast.LENGTH_LONG).show();}
}
