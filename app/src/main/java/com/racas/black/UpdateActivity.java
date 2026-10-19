package com.racas.black;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

// One update surface for the launcher and the existing in-game update action.
public final class UpdateActivity extends Activity {
    static final String ABOUT="about";
    private enum Phase { CHECKING,CURRENT,AVAILABLE,DOWNLOADING,VERIFYING,READY,ERROR }
    private Phase phase=Phase.CHECKING;
    private boolean about,resumed,checking,verifying,verificationFailed,waitingPermission,installAfterVerify;
    private long verifiedId=-1;
    private String error="";
    private AppUpdater updater;
    private UpdateChecker.Release release;
    private LinearLayout content;
    private TextView downloadText;
    private ProgressBar downloadBar;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(!resumed)return;pollDownload();main.postDelayed(this,900);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);about=state!=null?state.getBoolean(ABOUT):getIntent().getBooleanExtra(ABOUT,false);
        waitingPermission=state!=null&&state.getBoolean("install_permission");
        updater=new AppUpdater(this);
        if(updater.id()>=0)phase=Phase.DOWNLOADING;
        render();if(updater.id()<0)check();
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;main.removeCallbacks(poll);main.post(poll);
        if(waitingPermission){waitingPermission=false;if(getPackageManager().canRequestPackageInstalls())install();}
    }
    @Override protected void onPause(){resumed=false;main.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacks(poll);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean(ABOUT,about);state.putBoolean("install_permission",waitingPermission);super.onSaveInstanceState(state);}
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);if(!focus)return;
        WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private TextView title(String s,int size){TextView t=text(s,size,LauncherActivity.WHITE);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void gap(LinearLayout box,int size){box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private Button button(String name,boolean primary,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?LauncherActivity.BG:LauncherActivity.WHITE);b.setBackgroundTintList(null);
        b.setBackground(shape(primary?LauncherActivity.GOLD:Color.rgb(37,43,50),12));b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));
        b.setPadding(dp(14),dp(6),dp(14),dp(6));b.setOnClickListener(v->action.run());return b;
    }
    private LinearLayout card(){
        LinearLayout c=column();c.setBackground(shape(LauncherActivity.PANEL,18));c.setPadding(dp(20),dp(18),dp(20),dp(18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);content.addView(c,p);return c;
    }
    private void action(LinearLayout box,String s,boolean primary,Runnable task){gap(box,12);box.addView(button(s,primary,task),new LinearLayout.LayoutParams(-1,dp(46)));}
    private void render(){
        if(isFinishing()||isDestroyed())return;
        LinearLayout shell=new LinearLayout(this);shell.setBackgroundColor(LauncherActivity.BG);shell.setPadding(dp(12),dp(12),dp(12),dp(12));setContentView(shell);
        LinearLayout rail=column();rail.setBackground(shape(Color.rgb(17,20,24),16));rail.setPadding(dp(12),dp(14),dp(12),dp(12));shell.addView(rail,new LinearLayout.LayoutParams(dp(156),-1));
        TextView brand=title("BLACK",26);brand.setLetterSpacing(.12f);rail.addView(brand);rail.addView(text("CENTRAL DO APP",9,LauncherActivity.GOLD));gap(rail,22);
        Button updates=button("Atualizações",!about,()->{about=false;render();});rail.addView(updates,new LinearLayout.LayoutParams(-1,dp(44)));gap(rail,8);
        rail.addView(button("Sobre",about,()->{about=true;render();}),new LinearLayout.LayoutParams(-1,dp(44)));
        rail.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        rail.addView(button("← Voltar",false,this::finish),new LinearLayout.LayoutParams(-1,dp(42)));gap(rail,8);
        rail.addView(text("v"+UpdateChecker.installedVersion(this),9,LauncherActivity.MUTED));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.leftMargin=dp(20);shell.addView(scroll,p);
        content=column();content.setPadding(0,dp(8),dp(8),dp(8));scroll.addView(content);
        TextView tag=text(about?"PROJETO E CRÉDITOS":"DIRETO DO GITHUB",10,LauncherActivity.GOLD);tag.setLetterSpacing(.16f);content.addView(tag);gap(content,6);
        content.addView(title(about?"Sobre o Black":"Seu Black atualizado.",28));gap(content,14);
        downloadText=null;downloadBar=null;
        if(about)aboutPage();else updatesPage();
    }
    private void aboutPage(){
        LinearLayout info=card();info.addView(title("Black para Android",21));gap(info,7);
        info.addView(text("Versão "+UpdateChecker.installedVersion(this)+"  ·  ARM64  ·  Vulkan",13,LauncherActivity.GOLD));gap(info,10);
        info.addView(text("Port baseado no Black-Android. Este fork reúne o launcher, a personalização dos controles e as atualizações pelo GitHub.",13,LauncherActivity.MUTED));
        action(info,"Ver atualizações",true,()->{about=false;render();if(updater.id()<0)check();});
        LinearLayout credits=card();credits.addView(title("Créditos",18));gap(credits,9);
        credits.addView(text("Projeto original: RanggaCasper / Black-Android\nRecompilação: TGP482 / XboxXBE-Recomp\nTecnologias: SDL3, Vulkan, glslang, toml++ e Dear ImGui\nFork e publicações: contatg / Black-mobiu",13,LauncherActivity.MUTED));
        action(credits,"Código e releases no GitHub   ↗",false,()->open("https://github.com/"+UpdateChecker.REPO));
        LinearLayout note=card();note.addView(text("Projeto não oficial. Black pertence aos seus respectivos titulares. O aplicativo não inclui os arquivos do jogo.",12,LauncherActivity.MUTED));
    }
    private void updatesPage(){
        LinearLayout box=card();
        String heading=phase==Phase.CHECKING?"Buscando atualização…":phase==Phase.CURRENT?"Nenhuma atualização disponível":phase==Phase.AVAILABLE?"Uma nova versão está disponível":phase==Phase.DOWNLOADING?"Baixando atualização":phase==Phase.VERIFYING?"Preparando a instalação…":phase==Phase.READY?"Pronto para instalar":"Não foi possível atualizar";
        box.addView(title(heading,21));gap(box,8);
        box.addView(text("Instalada: "+UpdateChecker.installedVersion(this),12,LauncherActivity.MUTED));
        if(release!=null){gap(box,4);box.addView(text("Disponível: "+release.tag,13,LauncherActivity.GOLD));}
        else if(updater.id()>=0){gap(box,4);box.addView(text(updater.tag(),13,LauncherActivity.GOLD));}
        if(phase==Phase.CHECKING||phase==Phase.VERIFYING){ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminateTintList(ColorStateList.valueOf(LauncherActivity.GOLD));box.addView(spinner,new LinearLayout.LayoutParams(dp(34),dp(34)));}
        if(phase==Phase.CURRENT){gap(box,8);box.addView(text("Continue jogando. As novas versões aparecerão aqui quando forem publicadas.",13,LauncherActivity.MUTED));action(box,"Verificar novamente",true,this::check);}
        if(phase==Phase.AVAILABLE){
            if(release.changes!=null&&!release.changes.isEmpty()){gap(box,14);box.addView(title("Novidades desta versão",15));gap(box,6);box.addView(text(release.changes,13,LauncherActivity.MUTED));}
            action(box,release.apk==null?"Abrir release no GitHub":"Baixar atualização"+(release.size>0?"  ·  "+mb(release.size):""),true,()->{if(release.apk==null)open(release.page);else download();});
        }
        if(phase==Phase.DOWNLOADING){
            gap(box,16);downloadBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);downloadBar.setMax(1000);downloadBar.setProgressTintList(ColorStateList.valueOf(LauncherActivity.GOLD));box.addView(downloadBar,new LinearLayout.LayoutParams(-1,dp(12)));
            downloadText=text("Iniciando download…",13,LauncherActivity.GOLD);gap(box,8);box.addView(downloadText);
            gap(box,8);box.addView(text("Você pode voltar. O download continua em segundo plano.",12,LauncherActivity.MUTED));action(box,"Cancelar download",false,this::discard);
        }
        if(phase==Phase.READY){gap(box,12);box.addView(text("O Android pedirá a confirmação da instalação. Seus controles e saves permanecem.",13,LauncherActivity.MUTED));action(box,getPackageManager().canRequestPackageInstalls()?"Instalar atualização":"Permitir e instalar atualização",true,this::install);action(box,"Descartar download",false,this::discard);}
        if(phase==Phase.ERROR){gap(box,10);box.addView(text(error,13,LauncherActivity.MUTED));action(box,"Tentar novamente",true,()->{updater.cancel();verifiedId=-1;verificationFailed=false;check();});}
        LinearLayout automatic=card();Switch enabled=new Switch(this);enabled.setText("Buscar ao abrir o launcher");enabled.setTextColor(LauncherActivity.WHITE);enabled.setTextSize(13);enabled.setChecked(UpdateChecker.automatic(this));enabled.setThumbTintList(ColorStateList.valueOf(LauncherActivity.GOLD));enabled.setOnCheckedChangeListener((v,on)->UpdateChecker.automatic(this,on));automatic.addView(enabled);
        gap(automatic,7);automatic.addView(text("As atualizações são consultadas automaticamente. Você escolhe quando baixar e instalar.",12,LauncherActivity.MUTED));
        LinearLayout source=card();source.addView(text("Fonte: "+UpdateChecker.REPO,12,LauncherActivity.MUTED));action(source,"Ver releases no GitHub   ↗",false,()->open("https://github.com/"+UpdateChecker.REPO+"/releases"));
        if(phase==Phase.DOWNLOADING)showProgress(updater.progress());
    }
    private void check(){
        if(checking)return;if(updater.id()>=0){phase=Phase.DOWNLOADING;render();return;}
        checking=true;phase=Phase.CHECKING;error="";render();
        UpdateChecker.check(this,true,(found,failed)->{
            checking=false;if(isFinishing()||isDestroyed())return;
            release=found;phase=failed?Phase.ERROR:found!=null?Phase.AVAILABLE:Phase.CURRENT;
            if(failed)error="Não foi possível consultar o GitHub. Confira a conexão e tente novamente.";
            render();
        });
    }
    private void download(){
        try{updater.start(release);verifiedId=-1;verificationFailed=false;phase=Phase.DOWNLOADING;render();pollDownload();}
        catch(Exception e){failure(e.getMessage());}
    }
    private void discard(){updater.cancel();verifiedId=-1;verificationFailed=false;phase=release==null?Phase.CURRENT:Phase.AVAILABLE;render();}
    private static String mb(long n){return String.format(Locale.getDefault(),"%.1f MB",n/1048576.0);}
    private void showProgress(AppUpdater.Progress p){
        if(downloadBar==null||downloadText==null)return;
        downloadBar.setIndeterminate(p.total<=0);
        if(p.total>0)downloadBar.setProgress((int)Math.min(1000,p.bytes*1000/p.total));
        downloadText.setText(p.status==DownloadManager.STATUS_PAUSED?"Aguardando conexão…":p.total>0?Math.min(100,p.bytes*100/p.total)+"%  ·  "+mb(p.bytes)+" de "+mb(p.total):mb(Math.max(0,p.bytes))+" baixados");
    }
    private void pollDownload(){
        if(updater==null||updater.id()<0)return;AppUpdater.Progress p=updater.progress();
        if(p.status==DownloadManager.STATUS_SUCCESSFUL){if(verifiedId!=updater.id()&&!verifying&&!verificationFailed)verify();}
        else if(p.status==DownloadManager.STATUS_FAILED){if(phase!=Phase.ERROR)failure(p.error);}
        else{if(phase!=Phase.DOWNLOADING){phase=Phase.DOWNLOADING;render();}showProgress(p);}
    }
    private void verify(){
        verifying=true;phase=Phase.VERIFYING;render();long id=updater.id();
        new Thread(()->{
            String problem=null;try{updater.verify();}catch(Exception e){problem=e.getMessage();}
            final String result=problem;
            main.post(()->{
                verifying=false;if(isFinishing()||isDestroyed()||id!=updater.id())return;
                if(result!=null){verificationFailed=true;failure(result);return;}
                verifiedId=id;phase=Phase.READY;render();
                if(installAfterVerify){installAfterVerify=false;install();}
            });
        },"BlackUpdateVerify").start();
    }
    private void install(){
        if(updater.id()<0){phase=Phase.CURRENT;render();return;}
        if(verifiedId!=updater.id()){installAfterVerify=true;if(!verifying)verify();return;}
        if(!getPackageManager().canRequestPackageInstalls()){
            try{waitingPermission=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}
            catch(ActivityNotFoundException e){waitingPermission=false;failure("Abra as configurações do Android para permitir a instalação de atualizações deste aplicativo.");}
            return;
        }
        try{startActivity(updater.installIntent());}catch(Exception e){failure("Não foi possível abrir o instalador do Android. Você pode baixar o APK na página da release.");}
    }
    private void failure(String message){phase=Phase.ERROR;error=message==null?"Tente novamente em instantes.":message;render();}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(this,"Nenhum navegador disponível.",Toast.LENGTH_SHORT).show();}}
}
