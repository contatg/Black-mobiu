package com.racas.black;
import java.nio.file.Files;
import java.nio.file.Path;

public class LauncherSettingsTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("black-launcher-test");
        Path file=dir.resolve("black.toml");
        String tail="[keys]\nmouse_sensitivity = \"MOUSE1\"\n[controls.stick]\nx = 0.108\ny = 0.70\nhidden = false\n";
        Files.writeString(file,"# existing options\nfov = 80\nmouse_sensitivity = 2.0\n"+tail);
        LauncherSettings settings=new LauncherSettings(file.toFile());
        settings.put("mouse_sensitivity",3.5);
        settings.put("touch_sensitivity",1.8);
        settings.save();
        String actual=Files.readString(file);
        if(!actual.endsWith(tail))throw new AssertionError("Nested tables or bindings changed");
        LauncherSettings loaded=new LauncherSettings(file.toFile());
        if(loaded.number("mouse_sensitivity",1)!=3.5f || loaded.number("touch_sensitivity",1)!=1.8f || loaded.number("fov",70)!=80)throw new AssertionError(actual);
        Path layout=dir.resolve("layout.toml");Files.writeString(layout,tail);
        loaded=new LauncherSettings(layout.toFile());
        if(loaded.number("mouse_sensitivity",1)!=1)throw new AssertionError("Read nested binding as root setting");
        loaded.put("opacity",.75);loaded.save();
        if(!Files.readString(layout).endsWith(tail))throw new AssertionError("Layout lost");
        System.out.println("Launcher settings preserve native options, key bindings and custom layouts");
        Files.delete(file);Files.delete(layout);Files.delete(dir);
    }
}
