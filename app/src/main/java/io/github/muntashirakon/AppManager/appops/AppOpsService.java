/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.appops;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.os.RemoteException;

import com.android.internal.app.IAppOpsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.reflector.ReflectUtils;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

@SuppressLint("DefaultLocale")
public class AppOpsService {
    private final IAppOpsService appOpsService;

    public AppOpsService() {
        Context context = AppManager.getContext();
        if (!PermissionUtils.hasAppOpsPermission(context)) {
            try {
                PackageManagerCompat.grantPermission(context.getPackageName(), PermissionUtils.PERMISSION_GET_APP_OPS_STATS, Users.getCurrentUserHandle());
            } catch (RemoteException e) {
                throw new RuntimeException("Couldn't connect to appOpsService locally", e);
            }
        }
        // Local/remote services are handled automatically
        this.appOpsService = IAppOpsService.Stub.asInterface(ProxyBinder.getService(Context.APP_OPS_SERVICE));
    }

    /**
     * Get the mode of operation of the given package or uid.
     *
     * @param op          One of the OP_*
     * @param uid         User ID for the package(s)
     * @param packageName Name of the package
     * @return One of the MODE_*
     */
    public int checkOperation(int op, int uid, String packageName) throws RemoteException {
        return appOpsService.checkOperation(op, uid, packageName);
    }

    public List<PackageOps> getOpsForPackage(int uid, String packageName, @Nullable int[] ops)
            throws RemoteException {
        // Check using uid mode and package mode, override ops in package mode from uid mode
        List<OpEntry> opEntries = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addAllRelevantOpEntriesWithNoOverride(opEntries, appOpsService.getUidOps(uid, ops));
        }
        addAllRelevantOpEntriesWithNoOverride(opEntries, appOpsService.getOpsForPackage(uid, packageName, ops));
        return Collections.singletonList(new PackageOps(packageName, uid, opEntries));
    }

    @NonNull
    public List<PackageOps> getPackagesForOps(int[] ops) throws RemoteException {
        List<Parcelable> opsForPackage = appOpsService.getPackagesForOps(ops);
        List<PackageOps> packageOpsList = new ArrayList<>();
        if (opsForPackage != null) {
            for (Object o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                packageOpsList.add(packageOps);
            }
        }
        return packageOpsList;
    }

    public void setMode(int op, int uid, String packageName, int mode) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Set UID mode
            appOpsService.setUidMode(op, uid, mode);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android 11 (R) doesn't support setting package mode
            appOpsService.setMode(op, uid, packageName, mode);
        }
    }

    public void resetAllModes(int reqUserId, @NonNull String reqPackageName) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            appOpsService.resetAllModes(reqUserId, reqPackageName);
        }
    }

    private void addAllRelevantOpEntriesWithNoOverride(final List<OpEntry> opEntries,
                                                       @Nullable final List<Parcelable> opsForPackage) {
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                for (OpEntry opEntry : packageOps.getOps()) {
                    if (!opEntries.contains(opEntry)) {
                        opEntries.add(opEntry);
                    }
                }
            }
        }
    }
}
