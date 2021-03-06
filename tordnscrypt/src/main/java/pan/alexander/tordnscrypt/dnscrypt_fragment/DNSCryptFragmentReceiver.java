package pan.alexander.tordnscrypt.dnscrypt_fragment;

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class DNSCryptFragmentReceiver extends BroadcastReceiver {

    private DNSCryptFragmentView view;
    private DNSCryptFragmentPresenterCallbacks presenter;

    private String dnscryptPath;
    private String busyboxPath;

    public DNSCryptFragmentReceiver(DNSCryptFragmentView view, DNSCryptFragmentPresenter presenter) {
        this.view = view;
        this.presenter = presenter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (view == null || presenter == null) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        PathVars pathVars = PathVars.getInstance(context);
        dnscryptPath = pathVars.getDNSCryptPath();
        busyboxPath = pathVars.getBusyboxPath();


        if (intent != null) {
            final String action = intent.getAction();
            if (action == null || action.equals("") || ((intent.getIntExtra("Mark", 0) !=
                    RootExecService.DNSCryptRunFragmentMark) &&
                    !action.equals(TOP_BROADCAST))) return;
            Log.i(LOG_TAG, "DNSCryptRunFragment onReceive");

            if (action.equals(RootExecService.COMMAND_RESULT)) {

                view.setDNSCryptProgressBarIndeterminate(false);

                view.setDNSCryptStartButtonEnabled(true);

                RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                if (comResult != null && comResult.getCommands().size() == 0) {
                    presenter.setDnsCryptSomethingWrong();
                    modulesStatus.setDnsCryptState(FAULT);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                if (comResult != null) {
                    for (String com : comResult.getCommands()) {
                        Log.i(LOG_TAG, com);
                        sb.append(com).append((char) 10);
                    }
                }

                if (sb.toString().contains("DNSCrypt_version")) {
                    String[] strArr = sb.toString().split("DNSCrypt_version");
                    if (strArr.length > 1 && strArr[1].trim().matches("\\d+\\.\\d+\\.\\d+")) {
                        DNSCryptVersion = strArr[1].trim();
                        new PrefManager(context).setStrPref("DNSCryptVersion", DNSCryptVersion);

                        if (!modulesStatus.isUseModulesWithRoot()) {

                            if (!presenter.isSavedDNSStatusRunning(context)) {
                                view.setDNSCryptLogViewText();
                            }

                            presenter.refreshDNSCryptState(context);
                        }
                    }
                }

                if (sb.toString().toLowerCase().contains(dnscryptPath.toLowerCase())
                        && sb.toString().contains("checkDNSRunning")) {

                    presenter.setDnsCryptRunning();
                    presenter.saveDNSStatusRunning(context, true);
                    modulesStatus.setDnsCryptState(RUNNING);
                    presenter.displayLog(5);

                } else if (!sb.toString().toLowerCase().contains(dnscryptPath.toLowerCase())
                        && sb.toString().contains("checkDNSRunning")) {
                    if (modulesStatus.getDnsCryptState() == STOPPED) {
                        presenter.saveDNSStatusRunning(context, false);
                    }
                    presenter.stopDisplayLog();
                    presenter.setDnsCryptStopped();
                    modulesStatus.setDnsCryptState(STOPPED);
                    presenter.refreshDNSCryptState(context);
                } else if (sb.toString().contains("Something went wrong!")) {
                    presenter.setDnsCryptSomethingWrong();
                    modulesStatus.setDnsCryptState(FAULT);
                }

            } else if (action.equals(TOP_BROADCAST)) {
                if (TOP_BROADCAST.contains("TOP_BROADCAST")) {
                    Log.i(LOG_TAG, "DNSCryptRunFragment onReceive TOP_BROADCAST");

                    checkDNSVersionWithRoot(context);
                }

                FragmentManager fragmentManager = view.getFragmentFragmentManager();

                CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
                    try {
                        Verifier verifier = new Verifier(context);
                        String appSignAlt = verifier.getApkSignature();
                        if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {

                            if (fragmentManager != null) {
                                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                        context, context.getText(R.string.verifier_error).toString(), "15");
                                if (notificationHelper != null) {
                                    notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                                }
                            }
                        }

                    } catch (Exception e) {
                        if (fragmentManager != null) {
                            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                    context, context.getText(R.string.verifier_error).toString(), "18");
                            if (notificationHelper != null) {
                                notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                            }
                        }
                        Log.e(LOG_TAG, "DNSCryptRunFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                                Arrays.toString(e.getStackTrace()));
                    }
                });

            }
        }
    }

    private void checkDNSVersionWithRoot(Context context) {

        if (presenter.isDNSCryptInstalled(context)) {

            List<String> commandsCheck = new ArrayList<>(Arrays.asList(
                    busyboxPath + "pgrep -l /libdnscrypt-proxy.so 2> /dev/null",
                    busyboxPath + "echo 'checkDNSRunning' 2> /dev/null",
                    busyboxPath + "echo 'DNSCrypt_version' 2> /dev/null",
                    dnscryptPath + " --version 2> /dev/null"
            ));
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(context, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.DNSCryptRunFragmentMark);
            RootExecService.performAction(context, intent);

            view.setDNSCryptProgressBarIndeterminate(true);
        }
    }
}
