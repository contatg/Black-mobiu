package com.racas.black;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Uses the native layout.toml schema, screen fractions and preset coordinates.
final class ControlEditorView extends View {
    private static final String[] IDS={"fire","fire_left","aim","mode","melee","use","grenade","reload","crouch","weapon","health","map","pause","gyro","back","suppressor","confirm","stick"};
    private static final String[] LABELS={"Tiro","Tiro E","Mira","Modo","Corpo","Usar","Granada","Recarregar","Agachar","Arma","Vida","Objetivos","Pausa","Giro","Voltar","Silenciador","ENTER","Analógico"};
    private static final String[] PRESET_IDS={"default","three","four"};
    private static final float[][][] PRESETS={{{0.924f, 0.66f, 0.095f},{0.059f, 0.40f, 0.065f},{0.969f, 0.40f, 0.065f},{0.91f, 0.40f, 0.045f},{0.843f, 0.56f, 0.05f},{0.789f, 0.40f, 0.055f},{0.784f, 0.70f, 0.05f},{0.838f, 0.84f, 0.055f},{0.964f, 0.88f, 0.05f},{0.50f, 0.90f, 0.05f},{0.559f, 0.90f, 0.045f},{0.473f, 0.07f, 0.04f},{0.527f, 0.07f, 0.04f},{0.419f, 0.07f, 0.04f},{0.036f, 0.08f, 0.045f},{0.70f, 0.56f, 0.042f},{0.89f, 0.73f, 0.075f},{0.108f, 0.70f, 0.12f}},{{0.90f, 0.68f, 0.08f},{0.07f, 0.32f, 0.085f},{0.955f, 0.42f, 0.06f},{0.875f, 0.42f, 0.042f},{0.80f, 0.60f, 0.045f},{0.765f, 0.40f, 0.05f},{0.745f, 0.76f, 0.045f},{0.84f, 0.87f, 0.05f},{0.955f, 0.88f, 0.05f},{0.50f, 0.90f, 0.05f},{0.559f, 0.90f, 0.045f},{0.473f, 0.07f, 0.04f},{0.527f, 0.07f, 0.04f},{0.419f, 0.07f, 0.04f},{0.036f, 0.08f, 0.045f},{0.69f, 0.56f, 0.042f},{0.89f, 0.73f, 0.075f},{0.13f, 0.72f, 0.12f}},{{0.90f, 0.70f, 0.075f},{0.07f, 0.32f, 0.085f},{0.93f, 0.30f, 0.07f},{0.97f, 0.55f, 0.04f},{0.78f, 0.55f, 0.045f},{0.73f, 0.38f, 0.05f},{0.72f, 0.75f, 0.045f},{0.80f, 0.87f, 0.05f},{0.83f, 0.24f, 0.05f},{0.50f, 0.90f, 0.05f},{0.559f, 0.90f, 0.045f},{0.473f, 0.07f, 0.04f},{0.527f, 0.07f, 0.04f},{0.419f, 0.07f, 0.04f},{0.036f, 0.08f, 0.045f},{0.655f, 0.55f, 0.042f},{0.89f, 0.73f, 0.075f},{0.13f, 0.72f, 0.12f}}};
    private final File file;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] places=new float[IDS.length][3];
    private final boolean[] hidden=new boolean[IDS.length];
    private int preset,selected=-1,pointer=-1;
    private float scale=1,opacity=.85f,offsetX,offsetY;
    Consumer<String> onSelection;
    ControlEditorView(Context c, File file) throws IOException {
        super(c);this.file=file;
        setContentDescription("Editor: arraste um controle e use mais ou menos para redimensionar");
        LauncherSettings s=new LauncherSettings(file);
        String name=s.raw("preset","\"default\"");
        for(int i=0;i<3;i++)if(name.equals("\""+PRESET_IDS[i]+"\""))preset=i;
        scale=clamp(s.number("scale",1),.5f,1.5f);opacity=clamp(s.number("opacity",.85f),.2f,1);
        preset(preset);
        String text=file.exists()?new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8):"";
        for(int i=0;i<IDS.length;i++){
            Matcher m=Pattern.compile("(?m)^\\[controls\\."+IDS[i]+"\\]\\s*\n([^\\[]*)").matcher(text);
            if(!m.find())continue;
            String block=m.group(1);
            for(int j=0;j<3;j++){
                Matcher v=Pattern.compile("(?m)^"+new String[]{"x","y","r"}[j]+"\\s*=\\s*([0-9.eE+-]+)").matcher(block);
                if(v.find())try{places[i][j]=clamp(Float.parseFloat(v.group(1)),j==2?.02f:0,j==2?.25f:1);}catch(NumberFormatException ignored){}
            }
            hidden[i]=i!=17&&i!=14&&i!=16&&Pattern.compile("(?m)^hidden\\s*=\\s*true").matcher(block).find();
        }
    }
    private static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
    void preset(int n){
        preset=n;
        for(int i=0;i<IDS.length;i++){System.arraycopy(PRESETS[n][i],0,places[i],0,3);hidden[i]=false;}
        selected=-1;announce();invalidate();
    }
    private void announce(){if(onSelection!=null)onSelection.accept(selected<0?"Toque num botão e arraste":LABELS[selected]+(hidden[selected]?" (oculto)":""));}
    void resize(float factor){if(selected>=0){places[selected][2]=clamp(places[selected][2]*factor,.02f,.25f);invalidate();}}
    void toggleHidden(){
        if(selected==17||selected==14||selected==16){if(onSelection!=null)onSelection.accept("Analógico, Voltar e ENTER ficam disponíveis");return;}
        if(selected>=0){hidden[selected]=!hidden[selected];announce();invalidate();}
    }
    void save() throws IOException{
        StringBuilder s=new StringBuilder(String.format(Locale.US,"preset = \"%s\"\nopacity = %.4f\nscale = %.4f\n",PRESET_IDS[preset],opacity,scale));
        for(int i=0;i<IDS.length;i++)s.append(String.format(Locale.US,"\n[controls.%s]\nx = %.6f\ny = %.6f\nr = %.6f\nhidden = %s\n",IDS[i],places[i][0],places[i][1],places[i][2],hidden[i]));
        File temp=new File(file.getPath()+".editor.tmp");
        Files.write(temp.toPath(),s.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(Color.rgb(18,23,28));
        pen.setColor(Color.rgb(31,39,46));pen.setStrokeWidth(1);
        for(int i=1;i<8;i++)c.drawLine(w*i/8,0,w*i/8,h,pen);
        for(int i=1;i<5;i++)c.drawLine(0,h*i/5,w,h*i/5,pen);
        pen.setTextAlign(Paint.Align.CENTER);pen.setTextSize(h*.055f);pen.setColor(Color.rgb(75,85,94));
        c.drawText("PRÉVIA DO LAYOUT",w/2,h*.5f,pen);
        pen.setTextSize(h*.032f);c.drawText("As posições são aplicadas ao iniciar o jogo",w/2,h*.56f,pen);
        for(int i=0;i<IDS.length;i++){
            float x=places[i][0]*w,y=places[i][1]*h,r=places[i][2]*scale*h;
            pen.setStyle(Paint.Style.FILL);pen.setColor(i==selected?Color.rgb(111,82,37):Color.rgb(37,44,51));pen.setAlpha(hidden[i]?70:Math.round(opacity*255));c.drawCircle(x,y,r,pen);
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(i==selected?3:1.5f);pen.setColor(i==selected?LauncherActivity.GOLD:Color.rgb(146,159,168));pen.setAlpha(hidden[i]?90:255);c.drawCircle(x,y,r,pen);
            if(i==17){c.drawCircle(x,y,r*.42f,pen);c.drawLine(x-r*.7f,y,x+r*.7f,y,pen);c.drawLine(x,y-r*.7f,x,y+r*.7f,pen);}
            pen.setStyle(Paint.Style.FILL);pen.setColor(LauncherActivity.WHITE);pen.setTextSize(Math.min(r*.5f,h*.04f));
            if(i!=17)c.drawText(LABELS[i],x,y-(pen.ascent()+pen.descent())/2,pen);
            if(hidden[i]){pen.setColor(Color.rgb(236,130,100));pen.setStrokeWidth(2);c.drawLine(x-r*.65f,y-r*.65f,x+r*.65f,y+r*.65f,pen);}
        }
        pen.setAlpha(255);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            float x=e.getX(),y=e.getY(),best=Float.MAX_VALUE;selected=-1;
            for(int i=0;i<IDS.length;i++){
                float dx=x-places[i][0]*getWidth(),dy=y-places[i][1]*getHeight(),r=places[i][2]*scale*getHeight();
                float d=(dx*dx+dy*dy)/(r*r);
                if(d<=1.5f&&d<best){best=d;selected=i;}
            }
            if(selected>=0){pointer=e.getPointerId(0);offsetX=x-places[selected][0]*getWidth();offsetY=y-places[selected][1]*getHeight();}
            announce();invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE&&selected>=0){
            int index=e.findPointerIndex(pointer);if(index<0)return true;
            places[selected][0]=clamp((e.getX(index)-offsetX)/getWidth(),0,1);
            places[selected][1]=clamp((e.getY(index)-offsetY)/getHeight(),0,1);invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP){pointer=-1;performClick();return true;}
        if(action==MotionEvent.ACTION_CANCEL){pointer=-1;return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
