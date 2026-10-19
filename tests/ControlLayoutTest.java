package com.racas.black;
import java.nio.file.Files;
import java.nio.file.Path;

public class ControlLayoutTest {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        if(args.length>1&&args[1].equals("native")) {
            ControlLayout nativeSaved=new ControlLayout(file.toFile());
            if(nativeSaved.preset!=2||Math.abs(nativeSaved.places[0][0]-.8f)>.001f||Math.abs(nativeSaved.places[0][1]-.55f)>.001f)throw new AssertionError("Native saved position is not shown by launcher");
            System.out.println("Launcher reopens the actual native editor output");
            return;
        }
        Files.deleteIfExists(file);
        ControlLayout layout=new ControlLayout(file.toFile());
        layout.setPreset(2);
        layout.places[0]=new float[]{.42f,.52f,.07f};
        layout.hidden[2]=true;
        layout.opacity=.6f;layout.scale=1.2f;
        layout.save(file.toFile());
        ControlLayout loaded=new ControlLayout(file.toFile());
        if(loaded.preset!=2||loaded.places[0][0]!=.42f||loaded.places[0][1]!=.52f||loaded.places[0][2]!=.07f||!loaded.hidden[2])throw new AssertionError("Editor changes were lost on reopen");
        LauncherSettings appearance=new LauncherSettings(file.toFile());
        appearance.put("opacity",.9);appearance.save();
        loaded=new ControlLayout(file.toFile());
        if(loaded.opacity!=.9f||loaded.scale!=1.2f||loaded.places[0][0]!=.42f||!loaded.hidden[2])throw new AssertionError("Appearance update discarded custom positions");
        loaded.save(file.toFile());
        System.out.println("Real editor layout saved and reopened: moved/resized/hidden controls and appearance persist");
    }
}
