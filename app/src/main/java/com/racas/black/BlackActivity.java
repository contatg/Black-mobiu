package com.racas.black;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import java.io.File;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

// SDL's activity running libmain.so. The game goes in the Black folder on the phone's storage
// (/storage/emulated/0/Black). Reading it needs all files access, a special access Android grants on a settings page
// of its own, never among the app's ordinary permissions. This asks for it and says where the game goes, each time
// the app comes back without them; the game itself waits and starts as soon as it finds the files.
// The launcher checks updates; the in-game update action opens the same update screen.
public class BlackActivity extends SDLActivity {
    static final String SHARED = "Black";

    private AlertDialog dialog;  // the one up, at most one at a time
    private boolean notNow;      // the player turned the access down this run
    private boolean resumed;
    private UpdateChecker.Release update;  // a newer release not shown yet
    private final GameImporter importer = new GameImporter(this);
    private boolean importing;  // the file picker or the copy is up: nothing else is asked meanwhile
    private boolean checking;   // a check asked from the menu is under way

    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL3", "main"};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (View v : new View[] {mSurface, mLayout})
            if (v != null) v.setOnCapturedPointerListener(BlackActivity::onCapturedPointer);
        File own = getExternalFilesDir(null);
        if (own != null) new File(own, "assets").mkdirs();

    }

    // A controller some phones' game services pass on as a virtual device (MIUI's took a DualShock 4 on a Redmi Note
    // 8 Pro): SDL sees no controller in it and drops its input. Its buttons and sticks go to the game as port 0's
    // here (native/input.cpp); a controller SDL has goes its own way as before.
    private static native void nativeLooseKey(int keyCode, boolean down);
    private static native void nativeLooseAxes(float lx, float ly, float rx, float ry, float lt, float rt, float hx,
                                               float hy);
    private static native void nativeLooseRelease();

    private static boolean loose(InputEvent e, int sources) {
        return !mBrokenLibraries && (e.getSource() & sources) != 0
                && !SDLControllerManager.isDeviceSDLJoystick(e.getDeviceId());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean pad = KeyEvent.isGamepadButton(e.getKeyCode()) || (e.getKeyCode() >= KeyEvent.KEYCODE_DPAD_UP
                && e.getKeyCode() <= KeyEvent.KEYCODE_DPAD_RIGHT);
        if (pad && loose(e, InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) {
            if (e.getRepeatCount() == 0) nativeLooseKey(e.getKeyCode(), e.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (loose(e, InputDevice.SOURCE_JOYSTICK) && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // the right stick on Z and RZ, the triggers on LTRIGGER and RTRIGGER or BRAKE and GAS, as Android has them
            nativeLooseAxes(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
                    e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ),
                    Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)),
                    Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)),
                    e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y));
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    // The mouse while the game holds it (SDL's relative mode, Android's pointer capture). SDL's own handler passes on
    // only moves with no button held and the wheel: clicks and the moves while firing were lost. Each sample of a
    // batch is a move of its own, as SDL's C side takes them.
    private static boolean onCapturedPointer(View view, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                for (int h = 0; h < e.getHistorySize(); h++)
                    onNativeMouse(0, MotionEvent.ACTION_MOVE, e.getHistoricalX(h), e.getHistoricalY(h), true);
                onNativeMouse(0, MotionEvent.ACTION_MOVE, e.getX(), e.getY(), true);
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                onNativeMouse(e.getButtonState(), MotionEvent.ACTION_DOWN, 0, 0, true);
                return true;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                onNativeMouse(e.getButtonState(), MotionEvent.ACTION_UP, 0, 0, true);
                return true;
            case MotionEvent.ACTION_SCROLL:
                onNativeMouse(0, MotionEvent.ACTION_SCROLL, e.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        e.getAxisValue(MotionEvent.AXIS_VSCROLL), false);
                return true;
            default:
                // the down and up a press brings with it: the button events above carry it
                return true;
        }
    }

    // CHECK FOR UPDATES in the options menu, from the game's thread: the answer always shows, up to date or offline
    public void checkForUpdates() {
        runOnUiThread(() -> startActivity(new Intent(this,UpdateActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        askForGame();
        tellOfUpdate();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    // SDL's older immersive flags don't always come back after a dialog (MIUI on Android 11 kept the status bar):
    // the bars are hidden again each time the game has the focus, a swipe shows them for a moment
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
        else if (!mBrokenLibraries) nativeLooseRelease();  // its ups go elsewhere while the game is not in front
    }

    private void hideSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void askForGame() {
        if (importing || (dialog != null && dialog.isShowing())) return;
        File own = getExternalFilesDir(null);
        if (own != null && hasGame(new File(own, "assets"))) return;
        if (!Environment.isExternalStorageManager()) {
            if (!notNow) askAllFilesAccess();
            return;
        }
        File shared = new File(Environment.getExternalStorageDirectory(), SHARED);
        shared.mkdirs();
        if (!hasGame(shared) && !hasGame(new File(shared, "assets"))) tellWhereTheGameGoes();
    }

    // the newer release, once the app is in front with no other dialog up; shown once a run
    private void tellOfUpdate() {
        if (update == null || importing || !resumed || isFinishing() || (dialog != null && dialog.isShowing())) return;
        UpdateChecker.Release r = update;
        update = null;
        String installed = "";
        try {
            installed = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            // only for the message
        }
        StringBuilder msg = new StringBuilder(r.name + " is available");
        if (!installed.isEmpty()) msg.append(" (you have ").append(installed).append(")");
        msg.append('.');
        if (!r.changes.isEmpty()) msg.append("\n\nWhat's new:\n").append(r.changes);
        msg.append("\n\nInstall it over this one: your settings and saves stay.");
        dialog = new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage(msg)
                .setPositiveButton("Download", (d, w) -> open(r.apk != null ? r.apk : r.page))
                .setNeutralButton("Skip this version", (d, w) -> UpdateChecker.skip(this, r.tag))
                .setNegativeButton("Later", null)
                .show();
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            // no browser: nothing to open it with
        }
    }

    // default.xbe or a disc image in the folder
    private static boolean hasGame(File dir) {
        if (new File(dir, "default.xbe").exists()) return true;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files)
            if (f.getName().toLowerCase().endsWith(".iso")) return true;
        return false;
    }

    private void askAllFilesAccess() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("Storage access")
                .setMessage("Black Android reads the game from the Black folder on your phone's storage:\n\n"
                        + "/storage/emulated/0/" + SHARED + "\n\n"
                        + "On the next screen turn on \"Allow access to manage all files\" for Black Android, then come back.")
                .setCancelable(false)
                .setPositiveButton("Allow access", (d, w) -> openAccessSettings())
                .setNegativeButton("Not now", (d, w) -> notNow = true)
                .setNeutralButton("Import game", (d, w) -> startImport())
                .setOnDismissListener(d -> tellOfUpdate())
                .show();
    }

    private void tellWhereTheGameGoes() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("Game files")
                .setMessage("Copy the game into the Black folder on your phone's storage:\n\n"
                        + "/storage/emulated/0/" + SHARED + "\n\n"
                        + "Its disc image (.iso), or default.xbe with its folders. The game starts as soon as they "
                        + "are there.\n\n"
                        + "Or import the disc image from your Downloads, a cloud drive or a USB stick.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Import game", (d, w) -> startImport())
                .setOnDismissListener(d -> tellOfUpdate())
                .show();
    }

    // the disc image the player picks, copied where the game looks: the Black folder with all files access, else the
    // app's own assets folder, which needs none
    private void startImport() {
        importing = importer.pick();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != GameImporter.REQUEST) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            importing = false;  // the picker was closed: onResume asks again
            return;
        }
        File dir = Environment.isExternalStorageManager()
                ? new File(Environment.getExternalStorageDirectory(), SHARED)
                : new File(getExternalFilesDir(null), "assets");
        importer.copy(uri, dir, ok -> {
            importing = false;
            if (!ok) askForGame();
            tellOfUpdate();
        });
    }

    // the app's own page of the access, or the list of every app's where a phone has no such page
    private void openAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException e2) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }
}
