// Exercise the real SDL event -> touch state -> pad/mouse path without a game image or Android device.
// Only the surrounding window, options and panel are stand-ins; touch.cpp itself is compiled unchanged.
#include <cstdlib>
#include <iostream>

#include "../native/touch.cpp"

namespace {
bool foreground = true;
bool panel = false;
int checks = 0;
const char* editor_fixture = nullptr;

void Check(bool ok, const char* what, int line) {
    ++checks;
    if (!ok) {
        std::cerr << "line " << line << ": " << what << '\n';
        std::exit(1);
    }
}
#define CHECK(x) Check(bool(x), #x, __LINE__)
bool Near(float a, float b, float tolerance = 0.01f) { return std::fabs(a - b) < tolerance; }
}

namespace host {
bool Foreground() { return foreground; }
void WindowPixels(int& w, int& h) { w = 2400; h = 1080; }
}
namespace app {
bool MenuOpen() { return panel; }
bool Capturing() { return false; }
void ToggleMenu() { panel = !panel; }
void Save() {}
std::filesystem::path ExeDir() { return std::filesystem::temp_directory_path() / "black-controls-tests"; }
}
namespace gyro {
void NextMode() {}
}
namespace game {
Options& Opt() {
    static Options options;
    return options;
}
}

namespace {
void Reset() {
    foreground = true;
    panel = false;
    input::g_menu = false;
    input::g_keyboard = false;
    input::g_touch = true;
    input::g_take_ms = input::NowMs();
    game::Opt() = {};
    touch::g_editing = false;
    touch::g_loaded = true;
    touch::g_layout = touch::kTwoFinger;
    touch::g_scale = 1;
    touch::SyncContext();
    touch::CancelFingers();
    for (auto& key : input::g_down) key = false;
}

void Finger(Uint32 type, SDL_FingerID id, float x, float y, SDL_TouchID device = 1) {
    SDL_Event e{};
    e.type = type;
    e.tfinger.touchID = device;
    e.tfinger.fingerID = id;
    e.tfinger.x = x;
    e.tfinger.y = y;
    touch::OnEvent(e);
}
void Down(SDL_FingerID id, float x, float y) { Finger(SDL_EVENT_FINGER_DOWN, id, x, y); }
void Move(SDL_FingerID id, float x, float y) { Finger(SDL_EVENT_FINGER_MOTION, id, x, y); }
void Up(SDL_FingerID id, float x, float y) { Finger(SDL_EVENT_FINGER_UP, id, x, y); }
input::Pad Poll() {
    input::Pad p;
    touch::Apply(0, p);
    return p;
}

void MovementOwnership() {
    Reset();
    Down(1, .10f, .70f);
    Move(1, .16f, .65f);
    auto moving = Poll();
    CHECK(moving.lx > 10000 && moving.ly > 0);
    Down(2, .25f, .70f);
    Move(2, .02f, .95f);
    auto other = Poll();
    CHECK(other.lx == moving.lx && other.ly == moving.ly);
    Finger(SDL_EVENT_FINGER_CANCELED, 1, .16f, .65f);
    CHECK(Poll().lx == 0 && Poll().ly == 0);
    Move(2, .30f, .40f);
    CHECK(Poll().lx == 0);
    Up(2, .30f, .40f);
    Down(3, .10f, .70f);
    Move(3, .101f, .701f);
    CHECK(Poll().lx == 0 && Poll().ly == 0);
    Move(3, .40f, .70f);
    CHECK(Poll().lx == 32767);
    input::Pad controller;
    controller.rx = 12345;
    controller.analog[2] = 255;
    touch::Apply(1, controller);
    CHECK(controller.rx == 12345 && controller.analog[2] == 255 && controller.lx == 0);
}

void PreciseLook() {
    Reset();
    Down(1, .60f, .45f);
    for (int i = 1; i <= 100; ++i) Move(1, .60f + .0001f * i, .45f);
    float dx = 0, dy = 0;
    CHECK(input::TakeMouse(dx, dy));
    CHECK(Near(dx, 18.0f) && Near(dy, 0));
    CHECK(!input::TakeMouse(dx, dy));
    Reset();
    Down(1, .60f, .45f);
    Move(1, .61f, .45f);
    CHECK(input::TakeMouse(dx, dy) && Near(dx, 18.0f));
    game::Opt().touch_sensitivity = 2;
    game::Opt().invert_mouse = true;
    Move(1, .62f, .46f);
    CHECK(input::TakeMouse(dx, dy));
    CHECK(Near(dx, 36.0f) && Near(dy, -16.2f));
}

void MoveAimAndFire() {
    Reset();
    Down(1, .10f, .70f);
    Move(1, .16f, .70f);
    Down(2, .60f, .35f);
    Down(3, .924f, .66f);
    Move(3, .94f, .67f);
    Move(2, .62f, .35f);
    auto p = Poll();
    CHECK(p.lx > 0 && p.analog[7] == 255);
    float dx = 0, dy = 0;
    CHECK(input::TakeMouse(dx, dy) && Near(dx, 36.0f) && Near(dy, 0));
    Down(4, .059f, .40f);
    Move(4, .12f, .44f);
    CHECK(!input::TakeMouse(dx, dy));
    Up(3, .94f, .67f);
    CHECK(Poll().analog[7] == 255);
    Up(4, .12f, .44f);
    CHECK(Poll().analog[7] == 0);

    Reset();
    input::g_keyboard = true;
    Down(1, .969f, .40f);
    Move(1, .965f, .42f);
    CHECK((Poll().buttons & (1 << 7)) != 0);
    CHECK(!input::KeyboardActive() && input::TouchActive());
    CHECK(input::TakeMouse(dx, dy) && dx < 0 && dy > 0);
    // an index finger holding ADS does not block the thumb's free-look area
    Down(2, .60f, .45f);
    Move(1, .96f, .43f);
    Move(2, .62f, .45f);
    CHECK(input::TakeMouse(dx, dy) && Near(dx, 36.0f) && Near(dy, 0));

    Reset();
    Down(1, .60f, .45f);
    Down(2, .924f, .66f);
    Move(2, .94f, .66f);
    CHECK(!input::TakeMouse(dx, dy));
    Up(1, .60f, .45f);
    Move(2, .95f, .66f);
    CHECK(input::TakeMouse(dx, dy) && Near(dx, 18.0f));
}

void TapsAndCancellation() {
    Reset();
    Down(1, .838f, .84f);
    Up(1, .838f, .84f);
    CHECK(Poll().analog[0] == 255);
    CHECK(Poll().analog[0] == 0);
    Down(2, .838f, .84f);
    Finger(SDL_EVENT_FINGER_CANCELED, 2, .838f, .84f);
    CHECK(Poll().analog[0] == 0);
    input::SetMenu(true);
    Down(3, .60f, .50f);
    Finger(SDL_EVENT_FINGER_CANCELED, 3, .60f, .50f);
    CHECK(touch::g_cursor_tap_until == 0 && !touch::g_cursor_down);
    Down(4, .60f, .50f);
    Up(4, .601f, .50f);
    CHECK(touch::g_cursor_tap_until > input::NowMs());
    CHECK(Near(touch::g_cursor_x, .601f * 2400));
}

void LifecycleAndMenus() {
    Reset();
    Down(1, .924f, .66f);
    Down(2, .10f, .70f);
    Move(2, .16f, .70f);
    input::g_down['W'] = true;
    SDL_Event event{};
    event.type = SDL_EVENT_WINDOW_FOCUS_LOST;
    touch::OnEvent(event);
    CHECK(Poll().analog[7] == 0 && Poll().lx == 0 && !input::g_down['W']);
    CHECK(touch::g_fingers.empty());
    Down(1, .924f, .66f);
    input::SetMenu(true);
    CHECK(Poll().analog[7] == 0);
    Up(1, .924f, .66f);
    CHECK(touch::g_cursor_tap_until == 0);
    input::SetMenu(false);
    Move(1, .95f, .66f);
    CHECK(Poll().analog[7] == 0);
    float dx = 0, dy = 0;
    CHECK(!input::TakeMouse(dx, dy));
    Down(3, .924f, .66f);
    panel = true;
    CHECK(Poll().analog[7] == 0);
    panel = false;
    CHECK(Poll().analog[7] == 0);
    foreground = false;
    Down(4, .924f, .66f);
    CHECK(Poll().analog[7] == 0);
}

void MenuStickAndConfirm() {
    Reset();
    input::g_settle = false;
    Down(9, .6f, .5f);
    CHECK(input::LeftButton());  // tapping a film still skips it
    Up(9, .6f, .5f);
    input::SetMenu(true);
    // A previous direct tap must not keep overriding stick navigation with a cursor.
    Down(1, .6f, .5f);
    Up(1, .6f, .5f);
    CHECK(touch::g_cursor_touch);
    Down(2, .108f, .70f);
    Move(2, .108f, .58f);
    CHECK(Poll().ly > 20000);
    CHECK(!touch::g_cursor_touch && touch::g_cursor_tap_until == 0);
    CHECK(!input::LeftButton());
    Up(2, .108f, .58f);
    CHECK(Poll().ly == 0);
    // ENTER is the same analog A/confirm input the game already reads.
    Down(3, .89f, .73f);
    Up(3, .89f, .73f);
    CHECK(Poll().analog[0] == 255);
    CHECK(Poll().analog[0] == 0);
    Down(4, .89f, .73f);
    input::SetMenu(false);
    CHECK(Poll().analog[0] == 0);
    Reset();
    input::SetMenu(true);
    Down(5, .6f, .5f);
    Up(5, .6f, .5f);
    CHECK(touch::g_cursor_tap_until > input::NowMs());
}

void LauncherLayoutResume() {
    Reset();
    CHECK(editor_fixture != nullptr);
    const auto dir=app::ExeDir();
    std::filesystem::create_directories(dir);
    const auto file=dir / "layout.toml";
    touch::SaveLayout();
    touch::g_loaded=false;
    touch::EnsureLayout();
    CHECK(Near(touch::g_layout[0].x,.924f));
    // The actual Java editor has written a new layout while the native session is paused.
    Down(1,.924f,.66f);
    CHECK(Poll().analog[7]==255);
    SDL_Event background{};background.type=SDL_EVENT_WILL_ENTER_BACKGROUND;
    touch::OnEvent(background);
    std::filesystem::copy_file(editor_fixture,file,std::filesystem::copy_options::overwrite_existing);
    std::ofstream(dir / "black.toml") << "mouse_sensitivity = 2.3\ntouch_sensitivity = 1.7\n";
    SDL_Event resume{};resume.type=SDL_EVENT_DID_ENTER_FOREGROUND;
    touch::OnEvent(resume);
    Poll();
    CHECK(Near(touch::g_layout[0].x,.42f));
    CHECK(Near(touch::g_layout[0].y,.52f));
    CHECK(Near(touch::g_layout[0].r,.07f));
    CHECK(touch::g_layout[2].hidden);
    CHECK(Near(touch::g_opacity,.9f)&&Near(touch::g_scale,1.2f));
    CHECK(Near(game::Opt().mouse_sensitivity,2.3f)&&Near(game::Opt().touch_sensitivity,1.7f));
    CHECK(Poll().analog[7]==0); // old fingers never stay held across reload
    Down(2,.42f,.52f);
    CHECK(Poll().analog[7]==255); // the edited location is the real hitbox
    Up(2,.42f,.52f);
    Down(3,.924f,.66f);
    CHECK(Poll().analog[7]==0); // the old location no longer fires
    Up(3,.924f,.66f);
    std::filesystem::remove(file);
    std::filesystem::remove(dir / "black.toml");
}

void InGameEditorShortcut() {
    Reset();
    std::filesystem::create_directories(app::ExeDir());
    touch::SaveLayout();
    Down(10,.585f,.07f);
    CHECK(touch::g_editing);
    CHECK(Poll().analog[7]==0);
    Up(10,.585f,.07f);
    Down(1,.924f,.66f);
    CHECK(touch::g_selected==0);
    Move(1,.80f,.55f);
    Up(1,.80f,.55f);
    CHECK(Near(touch::g_layout[0].x,.80f));
    // Brief focus loss must not discard unsaved edits when the disk file is unchanged.
    SDL_Event focus{};focus.type=SDL_EVENT_WINDOW_FOCUS_GAINED;
    touch::OnEvent(focus);
    touch::EnsureLayout();
    CHECK(touch::g_editing&&Near(touch::g_layout[0].x,.80f));
    float save[4];touch::BarAt(0,0,save,touch::kBarDone);
    Down(2,(save[0]+save[2])/4800.0f,(save[1]+save[3])/2160.0f);
    CHECK(!touch::g_editing);
    touch::g_loaded=false;
    touch::EnsureLayout();
    CHECK(Near(touch::g_layout[0].x,.80f));
    std::filesystem::copy_file(app::ExeDir()/"layout.toml",std::string(editor_fixture)+".native",std::filesystem::copy_options::overwrite_existing);
    std::filesystem::remove(app::ExeDir()/"layout.toml");
}

void FingerIdentityAndHitAreas() {
    Reset();
    Finger(SDL_EVENT_FINGER_DOWN, 7, .10f, .70f, 1);
    Finger(SDL_EVENT_FINGER_DOWN, 7, .60f, .45f, 2);
    CHECK(touch::g_fingers.size() == 2);
    Finger(SDL_EVENT_FINGER_MOTION, 7, .16f, .70f, 1);
    Finger(SDL_EVENT_FINGER_MOTION, 7, .62f, .45f, 2);
    CHECK(Poll().lx > 0);
    float dx = 0, dy = 0;
    CHECK(input::TakeMouse(dx, dy) && Near(dx, 36.0f));
    Finger(SDL_EVENT_FINGER_CANCELED, 7, .16f, .70f, 1);
    CHECK(touch::g_fingers.size() == 1 && Poll().lx == 0);
    Reset();
    Down(1, .924f, .66f);
    Down(1, .924f, .66f);
    CHECK(touch::g_fingers.size() == 1);
    Up(1, .924f, .66f);
    Poll();
    CHECK(Poll().analog[7] == 0);
    touch::g_layout[0] = {.80f, .50f, .10f};
    touch::g_layout[2] = {.84f, .50f, .05f};
    CHECK(touch::HitButton(.84f * 2400, .50f * 1080) == 2);
    Reset();
    touch::OpenEditor();
    Down(1, .50f, .70f);
    CHECK(touch::g_selected == -1);
    CHECK(touch::g_fingers.back().role == touch::Role::None);
}
}

int main(int argc,char** argv) {
    if(argc>1)editor_fixture=argv[1];
    MovementOwnership();
    PreciseLook();
    MoveAimAndFire();
    TapsAndCancellation();
    LifecycleAndMenus();
    FingerIdentityAndHitAreas();
    MenuStickAndConfirm();
    LauncherLayoutResume();
    InGameEditorShortcut();
    std::cout << checks << " touch-control regression checks passed\n";
}
