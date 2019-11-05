package pan.alexander.tordnscrypt.utils;
/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class ModulesStarterService extends Service {
    PathVars pathVars;
    String dnscryptPath;
    String busyboxPath;
    String appDataDir;
    String torPath;
    String itpdPath;
    Handler mHandler;
    public static final String actionStartDnsCrypt = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    public static final String actionStartTor = "pan.alexander.tordnscrypt.action.START_TOR";
    public static final String actionStartITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    public static final String actionDismissNotification= "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public final String ANDROID_CHANNEL_ID = "InviZible";
    private NotificationManager notificationManager;
    public static final int DEFAULT_NOTIFICATION_ID = 101;
    private static PowerManager.WakeLock wakeLock = null;

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    private Timer checkModulesThreadsTimer;
    private ModulesStatus modulesStatus;

    public ModulesStarterService() {
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    @Override
    public void onCreate() {
        super.onCreate();

        pathVars = new PathVars(getApplicationContext());
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        modulesStatus = ModulesStatus.getInstance();

        if (!modulesStatus.isUseModulesWithRoot()) {
            checkModulesThreadsTimer = new Timer();
            checkModulesThreadsTimer.schedule(task, 1, 1000);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPreferences.getBoolean("swWakelock", false)) {
            final String TAG = "AudioMix";
            if (wakeLock == null) {
                wakeLock = ((PowerManager) Objects.requireNonNull(getSystemService(Context.POWER_SERVICE))).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                wakeLock.acquire();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mHandler = new Handler();

        String action = intent.getAction();

        boolean showNotification = intent.getBooleanExtra("showNotification",true);

        if (action == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }


        if (showNotification) {
            sendNotification(getText(R.string.notification_text).toString(),getString(R.string.app_name),getText(R.string.notification_text).toString());
        }


        switch (action) {
            case actionStartDnsCrypt:
                try {
                    dnsCryptThread = new Thread(startDNSCrypt);
                    dnsCryptThread.setDaemon(false);
                    try {
                        //new experiment
                        dnsCryptThread.setPriority(Thread.NORM_PRIORITY);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    dnsCryptThread.start();

                    if (modulesStatus.isUseModulesWithRoot()) {
                        stopService(startId);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "DnsCrypt was unable to startRefreshModulesStatus: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }

                break;
            case actionStartTor:
                try {
                    torThread = new Thread(startTor);
                    torThread.setDaemon(false);
                    try {
                        //new experiment
                        torThread.setPriority(Thread.NORM_PRIORITY);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    torThread.start();

                    if (modulesStatus.isUseModulesWithRoot()) {
                        stopService(startId);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Tor was unable to startRefreshModulesStatus: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }

                break;
            case actionStartITPD:
                try {
                    itpdThread = new Thread(startITPD);
                    itpdThread.setDaemon(false);
                    try {
                        //new experiment
                        itpdThread.setPriority(Thread.NORM_PRIORITY);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    itpdThread.start();

                    if (modulesStatus.isUseModulesWithRoot()) {
                        stopService(startId);
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "I2PD was unable to startRefreshModulesStatus: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }

                break;

            case actionDismissNotification:
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
                stopSelf(startId);
                break;
        }

        return START_REDELIVER_INTENT;

    }

    private void sendNotification(String Ticker, String Title, String Text) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel
                    (ANDROID_CHANNEL_ID, "NOTIFICATION_CHANNEL_INVIZIBLE", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("Protect InviZible Pro");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);
        }

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(R.drawable.ic_visibility_off_white_24dp)
                //.setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.large))   // большая картинка
                .setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setWhen(System.currentTimeMillis())
                //new experiment
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true);

        Notification notification = builder.build();

        startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null) {
            wakeLock.release();
        }

        if (checkModulesThreadsTimer != null) {
            checkModulesThreadsTimer.purge();
            checkModulesThreadsTimer.cancel();
            checkModulesThreadsTimer = null;
        }
        super.onDestroy();
    }

    private Runnable startDNSCrypt = new Runnable() {
        @Override
        public void run() {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            String dnsCmdString;
            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {
                dnsCmdString = busyboxPath+ "nohup " + dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &";
                shellResult = Shell.SU.run(dnsCmdString);
            } else {
                dnsCmdString = dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
                shellResult = Shell.run(dnsCmdString);
            }

            if (!shellResult.isSuccessful()) {
                Log.e(LOG_TAG,"Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ModulesStarterService.this,"Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };

    private Runnable startTor = new Runnable() {
        @Override
        public void run() {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String torCmdString;
            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {
                correctTorConfRunAsDaemon(true);
                torCmdString = torPath + " -f " + appDataDir + "/app_data/tor/tor.conf";
                shellResult = Shell.SU.run(torCmdString);
            } else {
                correctTorConfRunAsDaemon(false);
                torCmdString = torPath+" -f "+appDataDir+"/app_data/tor/tor.conf";
                shellResult = Shell.run(torCmdString);
            }

            if (!shellResult.isSuccessful()) {
                Log.e(LOG_TAG,"Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ModulesStarterService.this,"Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };

    private Runnable startITPD = new Runnable() {
        @Override
        public void run() {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String itpdCmdString;

            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {
                correctITPDConfRunAsDaemon(true);
                itpdCmdString = itpdPath + " --conf " + appDataDir + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir + "/i2pd_data &";
                shellResult = Shell.SU.run(itpdCmdString);
            } else {
                correctITPDConfRunAsDaemon(false);
                itpdCmdString = itpdPath+" --conf "+appDataDir+"/app_data/i2pd/i2pd.conf --datadir "+appDataDir+"/i2pd_data";
                shellResult = Shell.run(itpdCmdString);
            }

            if (!shellResult.isSuccessful()) {
                Log.e(LOG_TAG,"Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ModulesStarterService.this,"Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };

    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            if (modulesStatus == null) {
                return;
            }

            if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
                if (modulesStatus.getDnsCryptState() == STOPPED || modulesStatus.getDnsCryptState() == RESTARTED) {
                    modulesStatus.setDnsCryptState(ModuleState.RUNNING);
                }
            } else {
                if (modulesStatus.getDnsCryptState() == RUNNING || modulesStatus.getDnsCryptState() == RESTARTED) {
                    modulesStatus.setDnsCryptState(STOPPED);
                }
            }

            if (torThread != null && torThread.isAlive()) {
                if (modulesStatus.getTorState() == STOPPED || modulesStatus.getTorState() == RESTARTED) {
                    modulesStatus.setTorState(ModuleState.RUNNING);
                }
            } else {
                if (modulesStatus.getTorState() == RUNNING || modulesStatus.getTorState() == RESTARTED) {
                    modulesStatus.setTorState(STOPPED);
                }
            }

            if (itpdThread != null && itpdThread.isAlive()) {
                if (modulesStatus.getItpdState() == STOPPED || modulesStatus.getItpdState() == RESTARTED) {
                    modulesStatus.setItpdState(ModuleState.RUNNING);
                }
            } else {
                if (modulesStatus.getItpdState() == RUNNING || modulesStatus.getItpdState() == RESTARTED) {
                    modulesStatus.setItpdState(STOPPED);
                }
            }

            Log.i(LOG_TAG, "DNSCrypt is " + modulesStatus.getDnsCryptState() +
                    " Tor is " + modulesStatus.getTorState() + " I2P is " + modulesStatus.getItpdState());
        }
    };

    private void stopService(int startID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            stopForeground(true);
        }

        stopSelf(startID);
    }

    private void correctTorConfRunAsDaemon(boolean runAsDaemon) {
        String path = appDataDir+"/app_data/tor/tor.conf";
        List<String> lines = FileOperations.readTextFileSynchronous(getApplicationContext(), path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("RunAsDaemon")) {
                if (runAsDaemon && lines.get(i).contains("0")) {
                    lines.set(i, "RunAsDaemon 1");
                    FileOperations.writeTextFileSynchronous(getApplicationContext(), path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("1")) {
                    lines.set(i, "RunAsDaemon 0");
                    FileOperations.writeTextFileSynchronous(getApplicationContext(), path, lines);
                }
                return;
            }
        }
    }

    private void correctITPDConfRunAsDaemon(boolean runAsDaemon) {
        String path = appDataDir+"/app_data/i2pd/i2pd.conf";
        List<String> lines = FileOperations.readTextFileSynchronous(getApplicationContext(), path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("daemon")) {
                if (runAsDaemon && lines.get(i).contains("false")) {
                    lines.set(i, "daemon = true");
                    FileOperations.writeTextFileSynchronous(getApplicationContext(), path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("true")) {
                    lines.set(i, "daemon = false");
                    FileOperations.writeTextFileSynchronous(getApplicationContext(), path, lines);
                }
                return;
            }
        }
    }
}