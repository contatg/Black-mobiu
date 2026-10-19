package com.racas.black;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

// No SDL or native game code is loaded until the player presses Jogar.
public final class LauncherActivity extends Activity {
    static final int BG = Color.rgb(11, 13, 16), PANEL = Color.rgb(24, 27, 31);
    static final int GOLD = Color.rgb(239, 184, 90), WHITE = Color.rgb(242, 241, 233), MUTED = Color.rgb(157, 165, 171);
    private LinearLayout content;
    private FrameLayout root;
    private LauncherSettings game, layout;
    private File own;
    private int page;
    private boolean editing, importing;
    private GameImporter importer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        own = getExternalFilesDir(null);
        if (own == null) own = getFilesDir();
        importer = new GameImporter(this);
        try {
            loadSettings();
            page = state != null ? state.getInt("page", 0) : 0;
            showPage(page);
        } catch (IOException e) {
            new AlertDialog.Builder(this).setTitle("Não foi possível ler as configurações")
                    .setMessage(e.getMessage()).setPositiveButton("Fechar", (d, w) -> finish()).show();
        }
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (game != null && !editing && !importing) {
            try { loadSettings(); showPage(page); }
            catch (IOException e) { Toast.makeText(this,"Não foi possível reler as configurações.",Toast.LENGTH_LONG).show(); }
        }
    }
    private void loadSettings() throws IOException {
        File file=new File(own,"black.toml");
        game=new LauncherSettings(file);
        if(!file.exists())game.put("render_percent",67);
        layout=new LauncherSettings(new File(own,"layout.toml"));
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        b.putInt("page", page);
        super.onSaveInstanceState(b);
    }
    @Override public void onBackPressed() {
        if (editing) {
            choices("Sair do editor?", "Salve o layout para usar as alterações no jogo.",
                    new String[]{"Continuar editando", "Descartar alterações"},
                    new String[]{"Voltar para ajustar os botões", "Manter o último layout salvo"}, -1,
                    new Runnable[]{()->{}, ()->{editing=false;showPage(1);}});
        } else if (page != 0) showPage(0);
        else super.onBackPressed();
    }
    int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        return t;
    }
    private TextView title(String s, int size) {
        TextView t = label(s, size, WHITE); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable b = new GradientDrawable(); b.setColor(color); b.setCornerRadius(dp(radius)); return b;
    }
    private Button button(String s, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : WHITE);
        b.setBackgroundTintList(null);
        b.setBackground(shape(primary ? GOLD : Color.rgb(39, 43, 48), 12));
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout card() {
        LinearLayout box = column(); box.setPadding(dp(20),dp(16),dp(20),dp(16)); box.setBackground(shape(PANEL,16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(12); content.addView(box,p); return box;
    }
    private void gap(LinearLayout v, int size) { View g = new View(this); v.addView(g, new LinearLayout.LayoutParams(1,dp(size))); }
    private void heading(String eyebrow, String name, String description) {
        TextView tag = label(eyebrow, 10, GOLD); tag.setLetterSpacing(.18f); content.addView(tag);
        gap(content,6); content.addView(title(name,28));
        TextView text = label(description,12,MUTED); text.setPadding(0,dp(5),0,dp(18)); content.addView(text);
    }
    private void showPage(int tab) {
        page = tab;
        root = new FrameLayout(this); root.setBackgroundColor(BG); setContentView(root);
        LinearLayout shell = new LinearLayout(this); shell.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(shell);
        LinearLayout rail = column(); rail.setPadding(dp(10),dp(10),dp(12),dp(10));
        rail.setBackground(shape(Color.rgb(17,20,24),16)); shell.addView(rail,new LinearLayout.LayoutParams(dp(148),-1));
        TextView brand = title("BLACK",26); brand.setLetterSpacing(.12f); rail.addView(brand);
        TextView sub = label("MOBILE  /  ARM64",9,GOLD); sub.setLetterSpacing(.12f); rail.addView(sub); gap(rail,28);
        String[] tabs = {"01   Início", "02   Controles", "03   Mira"};
        for (int i=0;i<tabs.length;i++) {
            final int ix=i;
            Button b=button(tabs[i],false,()->showPage(ix));
            b.setTextSize(12); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            if (i==tab) { b.setTextColor(GOLD); b.setBackground(shape(Color.rgb(47,40,28),10)); }
            else b.setBackground(shape(Color.TRANSPARENT,10));
            rail.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        rail.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        rail.addView(label("PRÉVIA DO LAUNCHER",9,MUTED));
        TextView version = label("v"+UpdateChecker.installedVersion(this),9,MUTED); version.setPadding(0,dp(6),0,0); rail.addView(version);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,1); sp.leftMargin=dp(20); shell.addView(scroll,sp);
        content=column(); content.setPadding(0,dp(8),dp(8),dp(12)); scroll.addView(content);
        if(tab==0) home(); else if(tab==1) controls(); else sensitivity();
    }
    private void home() {
        heading("PRONTO PARA A MISSÃO", "Pronto para entrar.", "Prepare seus controles e entre no jogo quando quiser.");
        LinearLayout hero=card();
        TextView tag=label("BLACK  /  ANDROID",10,GOLD); tag.setLetterSpacing(.2f); hero.addView(tag); gap(hero,6);
        TextView name=title("BLACK",56); name.setLetterSpacing(.14f); hero.addView(name);
        hero.addView(label("Acerte o toque. Domine a mira.",14,MUTED)); gap(hero,20);
        boolean ready=hasGame();
        hero.addView(label(ready ? "●  Arquivos encontrados" : "○  Adicione os arquivos do jogo",12,ready?Color.rgb(133,205,164):GOLD));
        hero.addView(label(ready ? "Tudo pronto para iniciar." : "Use uma ISO ou default.xbe com as pastas do jogo.",11,MUTED)); gap(hero,14);
        hero.addView(button(ready?"Jogar agora   →":"Adicionar jogo",true,()->{if(hasGame())launch();else addGame();}),new LinearLayout.LayoutParams(-1,dp(48)));
        gap(content,2);
        LinearLayout shortcuts=new LinearLayout(this); content.addView(shortcuts);
        Button a=button("Personalizar controles",false,()->showPage(1)); shortcuts.addView(a,new LinearLayout.LayoutParams(0,dp(46),1));
        Button b=button("Velocidade da mira",false,()->showPage(2)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(46),1); bp.leftMargin=dp(10); shortcuts.addView(b,bp);
        gap(content,12);
        TextView info=label("Gráficos, resolução e filtro anisotrópico: ajuste no menu de opções dentro do jogo.",11,MUTED); content.addView(info);
        gap(content,10); content.addView(button("Gerenciar arquivos do jogo",false,this::addGame));
    }
    private void sensitivity() {
        heading("AJUSTE FINO", "Sua mira, no seu ritmo.", "As alterações são salvas e aplicadas ao iniciar o jogo.");
        LinearLayout mouse=card(); mouse.addView(title("Mouse",18));
        mouse.addView(label("Velocidade do mouse conectado por USB ou Bluetooth.",12,MUTED));
        slider(mouse,game,"mouse_sensitivity",1,1,50,10,"×");
        LinearLayout touch=card(); touch.addView(title("Tela de toque",18));
        touch.addView(label("Sensibilidade ao deslizar o dedo para mover a câmera.",12,MUTED));
        slider(touch,game,"touch_sensitivity",game.number("mouse_sensitivity",1),1,50,10,"×");
        content.addView(button("Restaurar sensibilidade padrão",false,()->{
            game.put("mouse_sensitivity",1.0); game.put("touch_sensitivity",1.0);
            if(save(game))showPage(2);
        }));
    }
    private void controls() {
        heading("FEITO PARA SUAS MÃOS", "Monte seu controle.", "Arraste os botões, ajuste o tamanho e escolha onde cada ação fica.");
        LinearLayout box=card(); box.addView(title("Layout de toque",18)); gap(box,8);
        box.addView(label("Edite posições, tamanhos e visibilidade antes de iniciar o jogo.",12,MUTED)); gap(box,12);
        box.addView(button("Abrir editor de controles   →",true,this::openEditor),new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout style=card(); style.addView(title("Aparência dos botões",18));
        style.addView(label("Opacidade",12,MUTED)); slider(style,layout,"opacity",.85f,20,100,100,"%");
        style.addView(label("Tamanho geral",12,MUTED)); slider(style,layout,"scale",1,50,150,100,"%");
        content.addView(label("Controles físicos continuam funcionando automaticamente. As opções gráficas ficam no menu do jogo.",11,MUTED));
    }
    private void slider(LinearLayout box, LauncherSettings settings, String key, float fallback, int min, int max, int divisor, String unit) {
        TextView value=label("",14,GOLD); value.setGravity(Gravity.RIGHT); box.addView(value);
        SeekBar bar=new SeekBar(this); bar.setMax(max-min);
        int n=Math.max(min,Math.min(max,Math.round(settings.number(key,fallback)*divisor)));
        bar.setProgress(n-min); bar.setProgressTintList(ColorStateList.valueOf(GOLD)); bar.setThumbTintList(ColorStateList.valueOf(GOLD));
        Runnable display=()->{int x=bar.getProgress()+min;value.setText(unit.equals("%")?x+"%":String.format(Locale.US,"%.1f ×",x/(float)divisor));};
        display.run(); box.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int p,boolean user){display.run();}
            public void onStopTrackingTouch(SeekBar b){settings.put(key,(b.getProgress()+min)/(double)divisor);save(settings);}
        });
    }
    private boolean save(LauncherSettings s) {
        try{s.save();return true;}catch(IOException e){Toast.makeText(this,"Não foi possível salvar: "+e.getMessage(),Toast.LENGTH_LONG).show();return false;}
    }
    private void openEditor() {
        editing=true;
        try {
            ControlEditorView editor=new ControlEditorView(this,new File(own,"layout.toml"));
            root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
            LinearLayout vertical=column();root.addView(vertical);
            LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(10),dp(6),dp(10),dp(6));
            vertical.addView(bar,new LinearLayout.LayoutParams(-1,dp(54)));
            TextView title=title("Editor de controles",16);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            bar.addView(button("Presets",false,()->choices("Escolha seu layout", "Use como ponto de partida. Depois, arraste os botões e toque em Salvar.",
                    new String[]{"2 dedos", "3 dedos", "4 dedos"},
                    new String[]{"Tradicional · os dois polegares", "Claw · indicador esquerdo para atirar", "Claw · dois indicadores e dois polegares"}, editor.currentPreset(),
                    new Runnable[]{()->editor.preset(0),()->editor.preset(1),()->editor.preset(2)})));
            bar.addView(button("Cancelar",false,()->onBackPressed()));
            bar.addView(button("Salvar",true,()->{
                try {editor.save();layout=new LauncherSettings(new File(own,"layout.toml"));editing=false;showPage(1);
                    Toast.makeText(this,"Layout salvo. Será aplicado ao entrar ou voltar ao jogo.",Toast.LENGTH_SHORT).show();}
                catch(IOException e){Toast.makeText(this,"Não foi possível salvar o layout.",Toast.LENGTH_LONG).show();}
            }));
            vertical.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(12),0,dp(12),0);
            TextView selected=label("Toque num botão e arraste",12,GOLD);footer.addView(selected,new LinearLayout.LayoutParams(0,-2,1));
            footer.addView(button("−",false,()->editor.resize(.9f)));footer.addView(button("+",false,()->editor.resize(1.1f)));
            footer.addView(button("Exibir / ocultar",false,editor::toggleHidden));
            vertical.addView(footer,new LinearLayout.LayoutParams(-1,dp(50)));
            editor.onSelection=name->selected.setText(name);
        } catch(IOException e){editing=false;showPage(1);Toast.makeText(this,"Não foi possível abrir o layout.",Toast.LENGTH_LONG).show();}
    }
    private boolean hasGame() {
        return containsGame(new File(own,"assets")) || (Environment.isExternalStorageManager() && containsGame(new File(Environment.getExternalStorageDirectory(),BlackActivity.SHARED)));
    }
    private boolean containsGame(File dir) {
        File[] files=dir.listFiles();if(files==null)return false;
        for(File f:files)if(f.isFile()&&(f.getName().equalsIgnoreCase("default.xbe")||f.getName().toLowerCase(Locale.ROOT).endsWith(".iso")))return true;
        return false;
    }
    // Card selectors avoid the platform's old, cramped three-button bar.
    private void choices(String name, String description, String[] names, String[] descriptions, int selected, Runnable[] actions) {
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=column();panel.setPadding(dp(24),dp(22),dp(24),dp(18));
        GradientDrawable background=shape(PANEL,24);background.setStroke(dp(1),Color.rgb(52,59,67));panel.setBackground(background);
        TextView tag=label("BLACK  /  CONFIGURAR",10,GOLD);tag.setLetterSpacing(.16f);panel.addView(tag);gap(panel,8);
        panel.addView(title(name,23));
        TextView body=label(description,13,MUTED);body.setPadding(0,dp(8),0,dp(16));panel.addView(body);
        for(int i=0;i<names.length;i++){
            final int index=i;boolean active=i==selected;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(13),dp(16),dp(13));
            GradientDrawable card=shape(active?Color.rgb(55,45,28):Color.rgb(32,37,44),14);
            card.setStroke(dp(1),active?GOLD:Color.rgb(53,60,69));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30EFB85A),card,shape(Color.WHITE,14)));
            TextView indicator=label(active?"●":"○",20,active?GOLD:MUTED);row.addView(indicator,new LinearLayout.LayoutParams(dp(35),-2));
            LinearLayout words=column();words.addView(title(names[i],15));
            TextView detail=label(descriptions[i],12,MUTED);detail.setPadding(0,dp(3),0,0);words.addView(detail);
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=label("›",25,GOLD);row.addView(arrow);
            row.setFocusable(true);row.setClickable(true);row.setContentDescription(names[i]+". "+descriptions[i]);
            row.setOnClickListener(v->{dialog.dismiss();actions[index].run();});
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);panel.addView(row,rp);
        }
        Button back=button("Voltar",false,dialog::dismiss);back.setBackground(shape(Color.TRANSPARENT,12));
        panel.addView(back,new LinearLayout.LayoutParams(-1,dp(42)));
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);scroll.setFillViewport(false);
        dialog.setContentView(scroll);dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width=Math.min(dp(600),Math.round(getResources().getDisplayMetrics().widthPixels*.87f));
            int maxHeight=Math.round(getResources().getDisplayMetrics().heightPixels*.91f);
            scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST));
            dialog.getWindow().setLayout(width,Math.min(scroll.getMeasuredHeight(),maxHeight));
            dialog.getWindow().setDimAmount(.68f);
        }
    }
    private void addGame() {
        choices("Adicione seu jogo", "Escolha como acessar os arquivos da sua cópia do Black.",
                new String[]{"Importar uma ISO", "Usar a pasta Black"},
                new String[]{"Escolher no celular, pendrive ou nuvem", "/storage/emulated/0/Black · autorizar acesso"}, -1,
                new Runnable[]{()->{importing=importer.pick();}, ()->{
                    try {startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}
                    catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
                }});
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=GameImporter.REQUEST)return;
        Uri uri=result==RESULT_OK&&data!=null?data.getData():null;
        if(uri==null){importing=false;showPage(0);return;}
        importer.copy(uri,new File(own,"assets"),ok->{importing=false;if(!isFinishing()&&!isDestroyed())showPage(0);});
    }
    private void launch() {
        startActivity(new Intent(this,BlackActivity.class));
        // Native input reloads the launcher files when this session resumes.
        finish();
    }
}
