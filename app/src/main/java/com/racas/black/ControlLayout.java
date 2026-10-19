package com.racas.black;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Shared by the real editor and the native-format integration tests.
final class ControlLayout {
    static final String[] IDS={"fire","fire_left","aim","mode","melee","use","grenade","reload","crouch","weapon","health","map","pause","gyro","back","suppressor","confirm","edit","stick"};
    static final String[] LABELS={"Tiro","Tiro E","Mira","Modo","Corpo","Usar","Granada","Recarregar","Agachar","Arma","Vida","Objetivos","Pausa","Giro","Voltar","Silenciador","ENTER","Editar","Analógico"};
    static final String[] PRESET_IDS={"default","three","four"};
    static final float[][][] PRESETS={{{0.924f, 0.66f, 0.095f},{0.059f, 0.40f, 0.065f},{0.969f, 0.40f, 0.065f},{0.91f, 0.40f, 0.045f},{0.843f, 0.56f, 0.05f},{0.789f, 0.40f, 0.055f},{0.784f, 0.70f, 0.05f},{0.838f, 0.84f, 0.055f},{0.964f, 0.88f, 0.05f},{0.50f, 0.90f, 0.05f},{0.559f, 0.90f, 0.045f},{0.473f, 0.07f, 0.04f},{0.527f, 0.07f, 0.04f},{0.419f, 0.07f, 0.04f},{0.036f, 0.08f, 0.045f},{0.70f, 0.56f, 0.042f},{0.89f, 0.73f, 0.075f},{0.585f, 0.07f, 0.035f},{0.108f, 0.70f, 0.12f}},{{0.90f, 0.68f, 0.08f},{0.07f, 0.32f, 0.085f},{0.955f, 0.42f, 0.06f},{0.875f, 0.42f, 0.042f},{0.80f, 0.60f, 0.045f},{0.765f, 0.40f, 0.05f},{0.745f, 0.76f, 0.045f},{0.84f, 0.87f, 0.05f},{0.955f, 0.88f, 0.05f},{0.50f, 0.90f, 0.05f},{0.559f, 0.90f, 0.045f},{0.473f, 0.07f, 0.04f},{0.527f, 0.07f, 0.04f},{0.419f, 0.07f, 0.04f},{0.036f, 0.08f, 0.045f},{0.69f, 0.56f, 0.042f},{0.89f, 0.73f, 0.075f},{0.585f, 0.07f, 0.035f},{0.13f, 0.72f, 0.12f}},{{0.90f, 0.70f, 0.075f},{0.07f, 0.32f, 0.085f},{0.93f, 0.30f, 0.07f},{0.97f, 0.55f, 0.04f},{0.78f, 0.55f, 0.045f},{0.73f, 0.38f, 0.05f},{0.72f, 0.75f, 0.045f},{0.80f, 0.87f, 0.05f},{0.83f, 0.24f, 0.05f},{0.50f, 0.90f, 0.05f},{0.559f, 0.90f, 0.045f},{0.473f, 0.07f, 0.04f},{0.527f, 0.07f, 0.04f},{0.419f, 0.07f, 0.04f},{0.036f, 0.08f, 0.045f},{0.655f, 0.55f, 0.042f},{0.89f, 0.73f, 0.075f},{0.585f, 0.07f, 0.035f},{0.13f, 0.72f, 0.12f}}};
    final float[][] places=new float[IDS.length][3];
    final boolean[] hidden=new boolean[IDS.length];
    int preset;
    float scale=1,opacity=.85f;
    ControlLayout(File file) throws IOException {
        LauncherSettings s=new LauncherSettings(file);
        String name=s.raw("preset","default");
        if(name.length()>1&&((name.startsWith("\"")&&name.endsWith("\""))||(name.startsWith("'")&&name.endsWith("'"))))name=name.substring(1,name.length()-1);
        for(int i=0;i<3;i++)if(name.equals(PRESET_IDS[i]))preset=i;
        scale=clamp(s.number("scale",1),.5f,1.5f);opacity=clamp(s.number("opacity",.85f),.2f,1);
        setPreset(preset);
        String text=file.exists()?new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8):"";
        for(int i=0;i<IDS.length;i++){
            Matcher m=Pattern.compile("(?m)^\\[controls\\."+IDS[i]+"\\]\\s*\n([^\\[]*)").matcher(text);
            if(!m.find())continue;
            String block=m.group(1);
            for(int j=0;j<3;j++){
                Matcher v=Pattern.compile("(?m)^"+new String[]{"x","y","r"}[j]+"\\s*=\\s*([0-9.eE+-]+)").matcher(block);
                if(v.find())try{places[i][j]=clamp(Float.parseFloat(v.group(1)),j==2?.02f:0,j==2?.25f:1);}catch(NumberFormatException ignored){}
            }
            hidden[i]=!required(i)&&Pattern.compile("(?m)^hidden\\s*=\\s*true").matcher(block).find();
        }
    }
    static boolean required(int i){return i==14||i==16||i==17||i==IDS.length-1;}
    static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
    void setPreset(int n){
        if(n<0||n>=PRESETS.length)throw new IllegalArgumentException("Invalid preset");
        preset=n;
        for(int i=0;i<IDS.length;i++){System.arraycopy(PRESETS[n][i],0,places[i],0,3);hidden[i]=false;}
    }
    void save(File file) throws IOException{
        StringBuilder s=new StringBuilder(String.format(Locale.US,"preset = \"%s\"\nopacity = %.4f\nscale = %.4f\n",PRESET_IDS[preset],opacity,scale));
        for(int i=0;i<IDS.length;i++)s.append(String.format(Locale.US,"\n[controls.%s]\nx = %.6f\ny = %.6f\nr = %.6f\nhidden = %s\n",IDS[i],places[i][0],places[i][1],places[i][2],hidden[i]));
        File temp=new File(file.getPath()+".editor.tmp");
        Files.write(temp.toPath(),s.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
