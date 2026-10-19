package com.racas.black;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

// Uses the native layout.toml schema, screen fractions and preset coordinates.
final class ControlEditorView extends View {
    private static final String[] IDS=ControlLayout.IDS;
    private static final String[] LABELS=ControlLayout.LABELS;
    private final File file;
    private final ControlLayout model;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] places;
    private final boolean[] hidden;
    private int selected=-1,pointer=-1;
    private float offsetX,offsetY;
    Consumer<String> onSelection;
    ControlEditorView(Context c, File file) throws IOException {
        super(c);this.file=file;
        model=new ControlLayout(file);places=model.places;hidden=model.hidden;
        setContentDescription("Editor: arraste um controle e use mais ou menos para redimensionar");
    }
    private static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
    int currentPreset(){return model.preset;}
    void preset(int n){
        model.setPreset(n);selected=-1;announce();invalidate();
    }
    private void announce(){if(onSelection!=null)onSelection.accept(selected<0?"Toque num botão e arraste":LABELS[selected]+(hidden[selected]?" (oculto)":""));}
    void resize(float factor){if(selected>=0){places[selected][2]=clamp(places[selected][2]*factor,.02f,.25f);invalidate();}}
    void toggleHidden(){
        if(selected>=0&&ControlLayout.required(selected)){if(onSelection!=null)onSelection.accept("Analógico, Voltar, ENTER e chave ficam disponíveis");return;}
        if(selected>=0){hidden[selected]=!hidden[selected];announce();invalidate();}
    }
    void save() throws IOException{model.save(file);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(Color.rgb(18,23,28));
        pen.setColor(Color.rgb(31,39,46));pen.setStrokeWidth(1);
        for(int i=1;i<8;i++)c.drawLine(w*i/8,0,w*i/8,h,pen);
        for(int i=1;i<5;i++)c.drawLine(0,h*i/5,w,h*i/5,pen);
        pen.setTextAlign(Paint.Align.CENTER);pen.setTextSize(h*.055f);pen.setColor(Color.rgb(75,85,94));
        c.drawText("PRÉVIA DO LAYOUT",w/2,h*.5f,pen);
        pen.setTextSize(h*.032f);c.drawText("As posições são aplicadas ao iniciar o jogo",w/2,h*.56f,pen);
        for(int i=0;i<IDS.length;i++){
            float x=places[i][0]*w,y=places[i][1]*h,r=places[i][2]*model.scale*h;
            pen.setStyle(Paint.Style.FILL);pen.setColor(i==selected?Color.rgb(111,82,37):Color.rgb(37,44,51));pen.setAlpha(hidden[i]?70:Math.round(model.opacity*255));c.drawCircle(x,y,r,pen);
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(i==selected?3:1.5f);pen.setColor(i==selected?LauncherActivity.GOLD:Color.rgb(146,159,168));pen.setAlpha(hidden[i]?90:255);c.drawCircle(x,y,r,pen);
            if(i==IDS.length-1){c.drawCircle(x,y,r*.42f,pen);c.drawLine(x-r*.7f,y,x+r*.7f,y,pen);c.drawLine(x,y-r*.7f,x,y+r*.7f,pen);}
            pen.setStyle(Paint.Style.FILL);pen.setColor(LauncherActivity.WHITE);pen.setTextSize(Math.min(r*.5f,h*.04f));
            if(i!=IDS.length-1)c.drawText(LABELS[i],x,y-(pen.ascent()+pen.descent())/2,pen);
            if(hidden[i]){pen.setColor(Color.rgb(236,130,100));pen.setStrokeWidth(2);c.drawLine(x-r*.65f,y-r*.65f,x+r*.65f,y+r*.65f,pen);}
        }
        pen.setAlpha(255);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            float x=e.getX(),y=e.getY(),best=Float.MAX_VALUE;selected=-1;
            for(int i=0;i<IDS.length;i++){
                float dx=x-places[i][0]*getWidth(),dy=y-places[i][1]*getHeight(),r=places[i][2]*model.scale*getHeight();
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
