
#include <jni.h>
#include <android/log.h>
#include <ucontext.h>
#include <pthread.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdint.h>

#include "main.h"
#include "game/game.h"
#include "game/RW/RenderWare.h"
#include "net/netgame.h"
#include "chatwindow.h"
#include "playertags.h"
#include "keyboard.h"
#include "CSettings.h"
#include "java_systems/HUD.h"
#include "CLoader.h"
#include "crashlytics.h"

#include <netdb.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include "str_obfuscator.hpp"

uintptr_t g_libGTASA = 0;
uintptr_t g_libSAMP = 0;
char* g_pszStorage = nullptr;
char* g_pszRootStorage = nullptr;

#include "CLocalisation.h"
#include "java_systems/HUD.h"
#include "java_systems/Inventory.h"
#include "util/CStackTrace.h"

void CrashLog(const char* fmt, ...);
bool g_bIsTestMode = false;
CNetGame *pNetGame = nullptr;

CGUI *pGUI = nullptr;

void InstallSpecialHooks();
void InitRenderWareFunctions();
void ApplyInGamePatches();
void ApplyPatches_level0();

extern uint16_t g_usLastProcessedModelIndexAutomobile;
extern int g_iLastProcessedModelIndexAutoEnt;

extern int g_iLastProcessedSkinCollision;
extern int g_iLastProcessedEntityCollision;

extern char g_bufRenderQueueCommand[200];
extern uintptr_t g_dwRenderQueueOffset;
extern char lastFile[123];
extern int g_iLastRenderedObject;
extern int lastNvEvent;
extern CVector lastPos;
char g_iLastBlock[123];
char streamimgState[123];

void PrintBuildCrashInfo()
{
	std::time_t currentTime = std::time(nullptr);
	std::tm* timeInfo = std::localtime(&currentTime);

	CrashLog("Crash time: %d:%d:%d %d:%d:%d", timeInfo->tm_mday, timeInfo->tm_mon, timeInfo->tm_year, timeInfo->tm_hour, timeInfo->tm_min, timeInfo->tm_sec);
	CrashLog("Build times: %s %s. Type: %s", __TIME__, __DATE__, (VER_x32 ? "x32" : "x64"));
	CrashLog("Last processed auto and entity: %d %d", g_usLastProcessedModelIndexAutomobile, g_iLastProcessedModelIndexAutoEnt);
	CrashLog("Last processed skin and entity: %d %d", g_iLastProcessedSkinCollision, g_iLastProcessedEntityCollision);
	CrashLog("Last rendered object: %d", g_iLastRenderedObject);
	CrashLog("Last texture: %s", g_iLastBlock);
	CrashLog("Last file: %s", lastFile);
	CrashLog("Last pos: %.2f, %.2f, %.2f", lastPos.x, lastPos.y, lastPos.z);
	CrashLog("Last nvEvent: %d", lastNvEvent);
    CrashLog("%s", streamimgState);

//	CrashLog("libGTASA base address: 0x%X", g_libGTASA);
//	CrashLog("libsamp base address: 0x%X", g_libSAMP);
}

#include <sstream>
#include "vendor/bass/bass.h"
#include "util/CJavaWrapper.h"
#include "CDebugInfo.h"
#include "voice/Plugin.h"

void InitInMenu()
{
    CGame::Init();
    CGame::InitInMenu();

	pGUI = new CGUI();
	CKeyBoard::init();
	CPlayerTags::Init();
}

#include <unistd.h> // system api
#include <sys/mman.h>
#include <cassert> // assert()
#include <dlfcn.h> // dlopen


void InitInGame()
{
	static bool bGameInited = false;
	static bool bNetworkInited = false;
	if (!bGameInited)
	{
		CGame::InitInGame();
		CGame::SetMaxStats();

		Voice::CVoicePlugin::OnPluginLoad();
		Voice::CVoicePlugin::OnSampLoad();
		// voice
		Log("[dbg:samp:load] : module loading...");

		for (const auto& loadCallback : Samp::loadCallbacks) {
			if (loadCallback != nullptr) {
				loadCallback();
			}
		}

		Samp::loadStatus = true;

		// voice
		for (const auto& deviceInitCallback : CVoiceRender::deviceInitCallbacks) {
			if (deviceInitCallback != nullptr) {
				deviceInitCallback();
			}
		}

		Log("[dbg:samp:load] : module loaded");

		g_pJavaWrapper->hideLoadingScreen();

		CHUD::toggleServerLogo(true);
		bGameInited = true;

		return;
	}

	if (!bNetworkInited)
	{
        pNetGame = new CNetGame(
                CSettings::m_Settings.szIp,
                CSettings::m_Settings.port,
                CSettings::m_Settings.szNickName,
                CSettings::m_Settings.szPassword
        );

		bNetworkInited = true;
		Log("InitInGame() end");
		return;
	}
}

#include "CDebugInfo.h"
#include "java_systems/SnapShotsWrapper.h"

void MainLoop()
{
	if(CGame::bIsGameExiting)return;

	InitInGame();

	if(pNetGame) pNetGame->Process();
}

static int g_nativeCrashFd = -1;

static size_t SafeLen(const char* s, size_t maxLen)
{
    if (!s) return 0;
    size_t n = 0;
    while (n < maxLen && s[n] != '\0') ++n;
    return n;
}

static void CrashWrite(const char* s)
{
    if (g_nativeCrashFd < 0 || !s) return;
    const size_t len = SafeLen(s, 1024);
    if (len > 0) {
        (void)write(g_nativeCrashFd, s, len);
    }
}

static void CrashWriteHex(uintptr_t value)
{
    if (g_nativeCrashFd < 0) return;

    char buf[2 + sizeof(uintptr_t) * 2 + 2]{};
    static const char hex[] = "0123456789ABCDEF";

    buf[0] = '0';
    buf[1] = 'x';

    const int digits = static_cast<int>(sizeof(uintptr_t) * 2);
    for (int i = 0; i < digits; ++i) {
        const int shift = (digits - 1 - i) * 4;
        buf[2 + i] = hex[(value >> shift) & 0xF];
    }

    buf[2 + digits] = '\n';
    (void)write(g_nativeCrashFd, buf, static_cast<size_t>(3 + digits));
}

static void CrashWriteFieldHex(const char* name, uintptr_t value)
{
    CrashWrite(name);
    CrashWriteHex(value);
}

static const char* CrashSignalName(int signum)
{
    switch (signum) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS:  return "SIGBUS";
        case SIGFPE:  return "SIGFPE";
        case SIGILL:  return "SIGILL";
        default:      return "UNKNOWN";
    }
}

static void NativeCrashSignalHandler(int signum, siginfo_t* info, void* contextPtr)
{
    ucontext_t* context = reinterpret_cast<ucontext_t*>(contextPtr);

    uintptr_t pc = 0;
    uintptr_t lr = 0;

#if defined(__aarch64__)
    if (context) {
        pc = static_cast<uintptr_t>(context->uc_mcontext.pc);
        lr = static_cast<uintptr_t>(context->uc_mcontext.regs[30]);
    }
#elif defined(__arm__)
    if (context) {
        pc = static_cast<uintptr_t>(context->uc_mcontext.arm_pc);
        lr = static_cast<uintptr_t>(context->uc_mcontext.arm_lr);
    }
#endif

    CrashWrite("\n=== NATIVE CRASH ===\nsignal=");
    CrashWrite(CrashSignalName(signum));
    CrashWrite("\n");

    CrashWriteFieldHex("fault=", info ? reinterpret_cast<uintptr_t>(info->si_addr) : 0);
    CrashWriteFieldHex("pc=", pc);
    CrashWriteFieldHex("lr=", lr);
    CrashWriteFieldHex("libGTASA=", g_libGTASA);
    CrashWriteFieldHex("libmultiplayer=", g_libSAMP);

    if (pc >= g_libGTASA && g_libGTASA != 0) {
        CrashWriteFieldHex("pc_minus_libGTASA=", pc - g_libGTASA);
    }

    if (pc >= g_libSAMP && g_libSAMP != 0) {
        CrashWriteFieldHex("pc_minus_libmultiplayer=", pc - g_libSAMP);
    }

    CrashWrite("lastFile=");
    if (SafeLen(lastFile, sizeof(lastFile)) > 0) {
        (void)write(g_nativeCrashFd, lastFile, SafeLen(lastFile, sizeof(lastFile)));
    }
    CrashWrite("\n");

    CrashWrite("lastTexture=");
    if (SafeLen(g_iLastBlock, sizeof(g_iLastBlock)) > 0) {
        (void)write(g_nativeCrashFd, g_iLastBlock, SafeLen(g_iLastBlock, sizeof(g_iLastBlock)));
    }
    CrashWrite("\n=== END NATIVE CRASH ===\n");

    if (g_nativeCrashFd >= 0) {
        (void)fsync(g_nativeCrashFd);
    }

    // Diagnostic build: terminate immediately after preserving the crash context.
    _exit(128 + signum);
}

static void InstallNativeCrashTracer()
{
    const char* path =
        "/storage/emulated/0/Android/data/com.russia.game/files/native_crash_trace.txt";

    g_nativeCrashFd = open(
        path,
        O_CREAT | O_WRONLY | O_TRUNC | O_CLOEXEC,
        0600
    );

    if (g_nativeCrashFd >= 0) {
        CrashWrite("BetaTester native crash tracer ready\n");
    }

    struct sigaction action{};
    action.sa_sigaction = NativeCrashSignalHandler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_SIGINFO;

    sigaction(SIGSEGV, &action, nullptr);
    sigaction(SIGABRT, &action, nullptr);
    sigaction(SIGBUS,  &action, nullptr);
    sigaction(SIGFPE,  &action, nullptr);
    sigaction(SIGILL,  &action, nullptr);
}

extern "C"
{
	JavaVM* javaVM = nullptr;
	JavaVM* alcGetJavaVM(void) {
		return javaVM;
	}
}

#include "CFPSFix.h"
#include "util/patch.h"
#include "CLoader.h"

CFPSFix g_fps;

extern "C"
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	javaVM = vm;

	g_libGTASA = CUtil::FindLib("libGTASA.so");

	if(g_libGTASA == 0)
	{
		Log("ERROR: libGTASA.so address not found!");
		return 0;
	}
	Log("libGTASA.so image base address: 0x%X", g_libGTASA);

	g_libSAMP = CUtil::FindLib("libmultiplayer.so");
	if(g_libSAMP == 0)
	{
		Log("ERROR: libsamp.so address not found!");
		return 0;
	}
	Log("libsamp.so image base address: 0x%X", g_libSAMP);

	CLoader::initJavaClasses(vm);

	CHook::InitHookStuff();

	InstallSpecialHooks();
	InitRenderWareFunctions();

	ApplyPatches_level0();

	CLoader::loadBassLib();

	InstallNativeCrashTracer();

	return JNI_VERSION_1_6;
}

void LogLong(const char* message)
{
    const size_t MAX_LOG_SIZE = 1000;
    size_t messageLength = strlen(message);

    for (size_t i = 0; i < messageLength; i += MAX_LOG_SIZE)
    {
        char buffer[MAX_LOG_SIZE + 1];
        size_t length = ((i + MAX_LOG_SIZE) < messageLength) ? MAX_LOG_SIZE : (messageLength - i);
        strncpy(buffer, &message[i], length);
        buffer[length] = '\0';
        __android_log_print(ANDROID_LOG_DEBUG, "tag", "%s", buffer);
    }
}

void Log(const char *fmt, ...)
{
	static char buffer[512] {};

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	firebase::crashlytics::Log(buffer);
	__android_log_write(ANDROID_LOG_INFO, "AXL", buffer);

#if USE_FILE_LOG
	//if(pDebug) pDebug->AddMessage(buffer);
	static FILE* flLog = nullptr;

	if(flLog == nullptr && g_pszStorage != nullptr)
	{
		sprintf(buffer, "%slog.txt", g_pszStorage);
		flLog = fopen(buffer, "ab");
	}

	if(flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);
#endif
}

void CrashLog(const char* fmt, ...)
{
	static char buffer[512] {};
	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

#ifdef NDEBUG
	firebase::crashlytics::Log(buffer);
#endif
	__android_log_write(ANDROID_LOG_FATAL, "AXL", buffer);

#if USE_FILE_LOG
	static FILE* flLog = nullptr;

	if (flLog == nullptr && g_pszStorage != nullptr)
	{
		sprintf(buffer, "%scrash_log.txt", g_pszStorage);
		flLog = fopen(buffer, "ab");
	}

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);
#endif
}

uint32_t GetTickCount()
{
    return CTimer::m_snTimeInMillisecondsNonClipped;
//	struct timeval tv;
//	gettimeofday(&tv, nullptr);
//	return (tv.tv_sec*1000+tv.tv_usec/1000);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_russia_game_core_Samp_initSAMP(JNIEnv *env, jobject thiz, jfloat maxFps, jstring directory) {
    Log("Initializing SAMP..");

    const char *dirChars = env->GetStringUTFChars(directory, nullptr);
    if (g_pszRootStorage != nullptr) {
        delete[] g_pszRootStorage;
    }
    g_pszRootStorage = new char[strlen(dirChars) + 1];
    strcpy(g_pszRootStorage, dirChars);

    env->ReleaseStringUTFChars(directory, dirChars);

	CSettings::maxFps = (int)maxFps;
    g_pJavaWrapper = new CJavaWrapper(env, thiz);
}
extern bool ProcessLocalCommands(const char str[]);

extern "C"
JNIEXPORT void JNICALL
Java_com_russia_game_core_Samp_00024Companion_sendCommand(JNIEnv *env, jobject clazz, jstring command) {
	const char *_command = env->GetStringUTFChars(command, nullptr);

	if(!ProcessLocalCommands(_command))
		pNetGame->SendChatCommand(_command);

	env->ReleaseStringUTFChars(command, _command);
}
