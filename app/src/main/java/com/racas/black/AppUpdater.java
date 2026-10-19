package com.racas.black;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

// Downloads survive Activity/process recreation. Game files and settings are never touched.
final class AppUpdater {
    static final class Progress {int status;long bytes,total;String error;}
    private final Context app;
    private final DownloadManager manager;
    private final SharedPreferences prefs;
    AppUpdater(Context context){
        app=context.getApplicationContext();manager=(DownloadManager)app.getSystemService(Context.DOWNLOAD_SERVICE);
        prefs=app.getSharedPreferences("update_download",Context.MODE_PRIVATE);
        String tag=prefs.getString("tag","");
        if(!tag.isEmpty()&&ReleaseVersion.compare(tag,UpdateChecker.installedVersion(app))<=0)cancel();
    }
    long id(){return prefs.getLong("id",-1);}
    String tag(){return prefs.getString("tag","");}
    void start(UpdateChecker.Release release) throws IOException {
        if(release.apk==null||!release.apk.startsWith("https://github.com/"+UpdateChecker.REPO+"/releases/download/"))throw new IOException("Esta release ainda não tem um APK disponível.");
        if(manager==null)throw new IOException("O gerenciador de downloads está indisponível neste aparelho.");
        File directory=app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(directory==null)throw new IOException("O armazenamento não está disponível.");
        cancel();String name="black-update-"+System.currentTimeMillis()+".apk";
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(release.apk))
                .setTitle("Atualização do Black")
                .setDescription(release.tag)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app,Environment.DIRECTORY_DOWNLOADS,name);
        long download=manager.enqueue(request);
        prefs.edit().putLong("id",download).putString("file",name).putString("tag",release.tag)
                .putString("digest",release.sha256==null?"":release.sha256).putLong("size",release.size).apply();
    }
    Progress progress(){
        Progress p=new Progress();if(id()<0)return p;
        if(manager==null){p.status=DownloadManager.STATUS_FAILED;p.error="Gerenciador de downloads indisponível.";return p;}
        try(Cursor c=manager.query(new DownloadManager.Query().setFilterById(id()))){
            if(c==null||!c.moveToFirst()){p.status=DownloadManager.STATUS_FAILED;p.error="O download não está mais disponível.";return p;}
            p.status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            p.bytes=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            p.total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if(p.status==DownloadManager.STATUS_FAILED)p.error="Não foi possível baixar o APK. Confira a conexão e o espaço disponível.";
        }catch(Exception e){p.status=DownloadManager.STATUS_FAILED;p.error="Não foi possível consultar o download.";}
        return p;
    }
    void cancel(){
        long id=id();if(manager!=null&&id>=0)try{manager.remove(id);}catch(RuntimeException ignored){}
        String name=prefs.getString("file","");File directory=app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(directory!=null&&!name.isEmpty()&&!name.contains("/"))new File(directory,name).delete();
        prefs.edit().clear().apply();
    }
    private File file() throws IOException {
        File directory=app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);String name=prefs.getString("file","");
        if(directory==null||name.isEmpty()||name.contains("/"))throw new IOException("Arquivo de atualização não encontrado.");
        return new File(directory,name);
    }
    private static Set<String> signers(PackageInfo p) throws IOException {
        if(p.signingInfo==null)throw new IOException("Não foi possível conferir a assinatura do APK.");
        Set<String> result=new HashSet<>();
        for(Signature s:p.signingInfo.getApkContentsSigners())result.add(s.toCharsString());return result;
    }
    void verify() throws Exception {
        File file=file();if(!file.isFile()||file.length()==0)throw new IOException("O download está incompleto.");
        long expected=prefs.getLong("size",0);if(expected>0&&file.length()!=expected)throw new IOException("O tamanho do APK não corresponde à release. Baixe novamente.");
        String digest=prefs.getString("digest","");
        if(digest.startsWith("sha256:")){
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            try(FileInputStream in=new FileInputStream(file)){byte[] block=new byte[64*1024];for(int n;(n=in.read(block))!=-1;)hash.update(block,0,n);}
            StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!actual.toString().equalsIgnoreCase(digest.substring(7)))throw new IOException("O arquivo não passou na verificação. Baixe novamente.");
        }
        PackageManager pm=app.getPackageManager();PackageInfo installed=pm.getPackageInfo(app.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null||!app.getPackageName().equals(archive.packageName))throw new IOException("O APK desta release pertence a outra edição do Black.");
        if(archive.getLongVersionCode()<=installed.getLongVersionCode())throw new IOException("Esta versão já está instalada ou é anterior à atual.");
        if(!signers(installed).equals(signers(archive)))throw new IOException("A assinatura deste APK é diferente da edição instalada.");
    }
    Intent installIntent() throws IOException {
        Uri uri=manager==null?null:manager.getUriForDownloadedFile(id());
        if(uri==null)throw new IOException("Não foi possível abrir o APK baixado.");
        return new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
