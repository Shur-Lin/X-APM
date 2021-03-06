package github.tornaco.xposedmoduletest.xposed.submodules;

import android.os.Build;
import android.util.Log;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import github.tornaco.xposedmoduletest.BuildConfig;
import github.tornaco.xposedmoduletest.xposed.app.XAPMManager;
import github.tornaco.xposedmoduletest.xposed.util.XposedLog;

/**
 * Created by guohao4 on 2017/10/31.
 * Email: Tornaco@163.com
 */
// CM FIX https://github.com/CyanogenMod/android_frameworks_base/commit/0ba4c710c6f8805175bde2dbd85d7d8788a15ee0
class ActiveServiceForegroundNotificationCancellationSubModule extends AndroidSubModule {

    @Override
    public int needMinSdk() {
        return Build.VERSION_CODES.N_MR1;
    }

    @Override
    public void handleLoadingPackage(String pkg, XC_LoadPackage.LoadPackageParam lpparam) {
        hookCancelForegroundNotificationLocked(lpparam);
    }

    private void hookCancelForegroundNotificationLocked(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedLog.verbose("hookCancelForegroundNotificationLocked...");
            Set unHooks = hookMethod(lpparam, "cancelForegroundNotificationLocked");
            XposedLog.verbose("hookCancelForegroundNotificationLocked-1 OK:" + unHooks);
            // Fail and try "cancelForegroudNotificationLocked"
            SubModuleStatus status = unhooksToStatus(unHooks);
            if (status == SubModuleStatus.ERROR) {
                unHooks = hookMethod(lpparam, "cancelForegroudNotificationLocked");
                XposedLog.verbose("hookCancelForegroundNotificationLocked-2 OK:" + unHooks);
            }
            setStatus(unhooksToStatus(unHooks));
        } catch (Exception e) {
            XposedLog.verbose("Fail hookCancelForegroundNotificationLocked: " + Log.getStackTraceString(e));
            setStatus(SubModuleStatus.ERROR);
            setErrorMessage(Log.getStackTraceString(e));
        }
    }

    private Set hookMethod(XC_LoadPackage.LoadPackageParam lpparam, String name) {
        Class ams = XposedHelpers.findClass("com.android.server.am.ActiveServices",
                lpparam.classLoader);
        // There is a mistake on N source code, maybe the developer is a Chinese?
        // It's funny.
        return XposedBridge.hookAllMethods(ams,
                name,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param)
                            throws Throwable {
                        super.beforeHookedMethod(param);
                        boolean opt = XAPMManager.get().isServiceAvailable() && XAPMManager.get()
                                .isOptFeatureEnabled(XAPMManager.OPT.FOREGROUND_NOTIFICATION.name());
                        if (opt) {
                            // Always allow to cancel, do not check active services.
                            Object serviceRecordObject = param.args[0];
                            if (serviceRecordObject != null) {
                                try {
                                    XposedHelpers.callMethod(serviceRecordObject, "cancelNotification");
                                    param.setResult(null);
                                    if (BuildConfig.DEBUG) {
                                        String pkgName = (String) XposedHelpers.getObjectField(serviceRecordObject, "packageName");
                                        XposedLog.verbose("cancelNotification hooked for: " + pkgName);
                                    }
                                } catch (Throwable e) {
                                    XposedLog.wtf("Fail invoke cancelNotification: " + Log.getStackTraceString(e));
                                }
                            }
                        }
                    }
                });
    }
}
