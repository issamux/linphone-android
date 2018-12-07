package org.linphone;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

public class LinphoneApplication extends Application {

    static Context applicationContext;

    @Override
    public void onCreate() {
        super.onCreate();
        applicationContext = getApplicationContext();
        if (BuildConfig.DEBUG) {
            enableStrictMode();
        }
    }

    private void enableStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectAll()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
    }


    public static Context getContext() {
        return applicationContext;
    }
}
