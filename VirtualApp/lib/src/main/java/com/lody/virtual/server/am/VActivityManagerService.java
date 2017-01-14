package com.lody.virtual.server.am;

import android.app.ActivityManager;
import android.app.IServiceConnection;
import android.app.IStopUserCallback;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import com.lody.virtual.os.VUserInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;

import com.lody.virtual.client.IVClient;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.env.SpecialComponentList;
import com.lody.virtual.client.ipc.ProviderCall;
import com.lody.virtual.client.stub.StubManifest;
import com.lody.virtual.helper.compat.ActivityManagerCompat;
import com.lody.virtual.helper.compat.BundleCompat;
import com.lody.virtual.helper.compat.IApplicationThreadCompat;
import com.lody.virtual.helper.proto.AppSetting;
import com.lody.virtual.helper.proto.AppTaskInfo;
import com.lody.virtual.helper.proto.PendingIntentData;
import com.lody.virtual.helper.proto.VParceledListSlice;
import com.lody.virtual.helper.utils.ComponentUtils;
import com.lody.virtual.helper.utils.VLog;
import com.lody.virtual.helper.utils.collection.ArrayMap;
import com.lody.virtual.helper.utils.collection.SparseArray;
import com.lody.virtual.os.VBinder;
import com.lody.virtual.os.VUserHandle;
import com.lody.virtual.os.VUserManager;
import com.lody.virtual.server.pm.VAppManagerService;
import com.lody.virtual.server.pm.VPackageManagerService;
import com.lody.virtual.server.secondary.BinderDelegateService;
import com.lody.virtual.service.IActivityManager;
import com.lody.virtual.service.interfaces.IProcessObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;

import mirror.android.app.ApplicationThreadNative;
import mirror.android.app.IApplicationThreadICSMR1;
import mirror.android.app.IApplicationThreadJBMR1;
import mirror.android.app.IApplicationThreadKitkat;
import mirror.android.content.res.CompatibilityInfo;

import static android.os.Process.killProcess;
import static com.lody.virtual.os.VUserHandle.getUserId;

/**
 * @author Lody
 *
 */
public class VActivityManagerService extends IActivityManager.Stub {

	private static final boolean BROADCAST_NOT_STARTED_PKG = false;

	private static final AtomicReference<VActivityManagerService> sService = new AtomicReference<>();
	private static final String TAG = VActivityManagerService.class.getSimpleName();
	private final SparseArray<ProcessRecord> mPidsSelfLocked = new SparseArray<ProcessRecord>();
	private final ActivityStack mMainStack = new ActivityStack(this);
	private final List<ServiceRecord> mHistory = new ArrayList<ServiceRecord>();
	private final ProcessMap<ProcessRecord> mProcessNames = new ProcessMap<ProcessRecord>();
	private ActivityManager am = (ActivityManager) VirtualCore.get().getContext()
			.getSystemService(Context.ACTIVITY_SERVICE);
	private final VPendingIntents mPendingIntents = new VPendingIntents();

	public static VActivityManagerService get() {
		return sService.get();
	}

	public static void systemReady(Context context) {
		new VActivityManagerService().onCreate(context);
	}

	private static ServiceInfo resolveServiceInfo(Intent service, int userId) {
		if (service != null) {
			ServiceInfo serviceInfo = VirtualCore.get().resolveServiceInfo(service, userId);
			if (serviceInfo != null) {
				return serviceInfo;
			}
		}
		return null;
	}

	public void onCreate(Context context) {
		AttributeCache.init(context);
		PackageManager pm = context.getPackageManager();
//		PackageInfo packageInfo = null;
//		try {
//			packageInfo = pm.getPackageInfo(context.getPackageName(),
//					PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA);
//		} catch (PackageManager.NameNotFoundException e) {
//			e.printStackTrace();
//		}
//
//		if (packageInfo == null) {
//			throw new RuntimeException("Unable to found PackageInfo : " + context.getPackageName());
//		}
		sService.set(this);

	}


	@Override
	public int startActivity(Intent intent, ActivityInfo info, IBinder resultTo, Bundle options, String resultWho, int requestCode, int userId) {
		synchronized (this) {
			return mMainStack.startActivityLocked(userId, intent, info, resultTo, options, resultWho, requestCode);
		}
	}

	@Override
	public PendingIntentData getPendingIntent(IBinder binder) {
		return mPendingIntents.getPendingIntent(binder);
	}

	@Override
	public void addPendingIntent(IBinder binder, String creator) {
		mPendingIntents.addPendingIntent(binder, creator);
	}

	@Override
	public void removePendingIntent(IBinder binder) {
		mPendingIntents.removePendingIntent(binder);
	}

	@Override
	public int getSystemPid() {
		return VirtualCore.get().myUid();
	}

	@Override
	public void onActivityCreated(ComponentName component, ComponentName caller, IBinder token, Intent intent, String affinity, int taskId, int launchMode, int flags) {
		int pid = Binder.getCallingPid();
		ProcessRecord targetApp = findProcessLocked(pid);
		if (targetApp != null) {
			mMainStack.onActivityCreated(targetApp, component, caller, token, intent, affinity, taskId, launchMode, flags);
		}
	}

	@Override
	public void onActivityResumed(int userId, IBinder token) {
		mMainStack.onActivityResumed(userId, token);
	}

	@Override
	public boolean onActivityDestroyed(int userId, IBinder token) {
		return mMainStack.onActivityDestroyed(userId, token);
	}

	@Override
	public AppTaskInfo getTaskInfo(int taskId) {
		return mMainStack.getTaskInfo(taskId);
	}

	@Override
	public String getPackageForToken(int userId, IBinder token) {
		return mMainStack.getPackageForToken(userId, token);
	}

	@Override
	public ComponentName getActivityClassForToken(int userId, IBinder token) {
		return mMainStack.getActivityClassForToken(userId, token);
	}


	public void processDead(ProcessRecord record) {
		synchronized (mHistory) {
			ListIterator<ServiceRecord> iterator = mHistory.listIterator();
			while (iterator.hasNext()) {
				ServiceRecord r = iterator.next();
				if (r.process.pid == record.pid) {
					iterator.remove();
				}
			}
			mMainStack.processDied(record);
		}
	}


	@Override
	public IBinder acquireProviderClient(int userId, ProviderInfo info) {
		ProcessRecord callerApp;
		VLog.d(TAG, "acquireProviderClient " + info.authority);
		synchronized (mPidsSelfLocked) {
			callerApp  = findProcessLocked(VBinder.getCallingPid());
		}
		if (callerApp == null) {
			throw new SecurityException("Who are you?");
		}
		String processName = info.processName;
		ProcessRecord r;
		synchronized (this) {
			r = startProcessIfNeedLocked(processName, userId, info.packageName);
		}
		if (r != null && r.client.asBinder().isBinderAlive()) {
			try {
				return r.client.acquireProviderClient(info);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		VLog.logbug(TAG, "acquireProviderClient return null : " + info.authority);
		return null;
	}

	@Override
	public ComponentName getCallingActivity(int userId, IBinder token) {
		return mMainStack.getCallingActivity(userId, token);
	}

	@Override
	public String getCallingPackage(int userId, IBinder token) {
		return mMainStack.getCallingPackage(userId, token);
	}


	@Override
	public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
		try {
			return super.onTransact(code, data, reply, flags);
		} catch (Throwable e) {
			e.printStackTrace();
			throw e;
		}
	}

	private void addRecord(ServiceRecord r) {
		mHistory.add(r);
	}

	private ServiceRecord findRecordLocked(int userId, ServiceInfo serviceInfo) {
		synchronized (mHistory) {
			for (ServiceRecord r : mHistory) {
				if (r.process.userId == userId && ComponentUtils.isSameComponent(serviceInfo, r.serviceInfo)) {
					return r;
				}
			}
			return null;
		}
	}

	private ServiceRecord findRecordLocked(IServiceConnection connection) {
		synchronized (mHistory) {
			for (ServiceRecord r : mHistory) {
				if (r.containConnection(connection)) {
					return r;
				}
			}
			return null;
		}
	}

	@Override
	public ComponentName startService(IBinder caller, Intent service, String resolvedType, int userId) {
		synchronized (this) {
			return startServiceCommon(service, true, userId);
		}
	}

	private ComponentName startServiceCommon(Intent service,
											 boolean scheduleServiceArgs, int userId) {
		ServiceInfo serviceInfo = resolveServiceInfo(service, userId);
		if (serviceInfo == null) {
			return null;
		}
		ProcessRecord targetApp = startProcessIfNeedLocked(ComponentUtils.getProcessName(serviceInfo),
				userId,
				serviceInfo.packageName);

		if (targetApp == null) {
			VLog.e(TAG, "Unable to start new Process for : " + ComponentUtils.toComponentName(serviceInfo));
			return null;
		}
		IInterface appThread = targetApp.appThread;
		ServiceRecord r = findRecordLocked(userId, serviceInfo);
		if (r == null) {
			r = new ServiceRecord();
			r.startId = 0;
			r.activeSince = SystemClock.elapsedRealtime();
			r.process = targetApp;
			r.serviceInfo = serviceInfo;
			try {
				IApplicationThreadCompat.scheduleCreateService(appThread, r, r.serviceInfo, 0);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			addRecord(r);
		}
		r.lastActivityTime = SystemClock.uptimeMillis();
		if (scheduleServiceArgs) {
			r.startId++;
			boolean taskRemoved = serviceInfo.applicationInfo != null
					&& serviceInfo.applicationInfo.targetSdkVersion < Build.VERSION_CODES.ECLAIR;
			try {
				IApplicationThreadCompat.scheduleServiceArgs(appThread, r, taskRemoved, r.startId, 0, service);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		return ComponentUtils.toComponentName(serviceInfo);
	}

	@Override
	public int stopService(IBinder caller, Intent service, String resolvedType, int userId) {
		synchronized (this) {
			ServiceInfo serviceInfo = resolveServiceInfo(service, userId);
			if (serviceInfo == null) {
				return 0;
			}
			ServiceRecord r = findRecordLocked(userId, serviceInfo);
			if (r == null) {
				return 0;
			}
			if (r.getClientCount() <= 0) {
				try {
					IApplicationThreadCompat.scheduleStopService(r.process.appThread, r);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					mHistory.remove(r);
				}
			}
			return 1;
		}
	}

	@Override
	public boolean stopServiceToken(ComponentName className, IBinder token, int startId, int userId) {
		synchronized (this) {
			//ServiceRecord r = (ServiceRecord) token;
			ServiceRecord r = null;
			if(className != null) {
				Intent service = new Intent().setComponent(className);
				ServiceInfo si = resolveServiceInfo(service, userId);
				if (si != null) {
					r = findRecordLocked(userId, si);
				}
			}

			try {
				if (r == null) {
					r = (ServiceRecord) token;
				}
			}catch (Exception e) {
				VLog.logbug("VAMS", VLog.getStackTraceString(e));
			}
			if (r != null && r.startId == startId) {
				try {
					IApplicationThreadCompat.scheduleStopService(r.process.appThread, r);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					mHistory.remove(r);
				}
				return true;
			}
			return false;
		}
	}

	@Override
	public void setServiceForeground(ComponentName className, IBinder token, int id, Notification notification,
									 boolean keepNotification, int userId) {

	}

	@Override
	public int bindService(IBinder caller, IBinder token, Intent service, String resolvedType,
						   IServiceConnection connection, int flags, int userId) {
		synchronized (this) {
			ServiceInfo serviceInfo = resolveServiceInfo(service, userId);
			if (serviceInfo == null) {
				return 0;
			}
			ServiceRecord r = findRecordLocked(userId, serviceInfo);
			boolean firstLaunch = r == null;
			if (firstLaunch) {
				if ((flags & Context.BIND_AUTO_CREATE) != 0) {
					startServiceCommon(service, false, userId);
					r = findRecordLocked(userId, serviceInfo);
				}
			}
			if (r == null) {
				return 0;
			}
			ServiceRecord.IntentBindRecord boundRecord = r.peekBinding(service);

			if (boundRecord != null && boundRecord.binder != null && boundRecord.binder.isBinderAlive()) {
				if (boundRecord.doRebind) {
					try {
						IApplicationThreadCompat.scheduleBindService(r.process.appThread, r, service, true, 0);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
				ComponentName componentName = new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
				connectService(connection, componentName, boundRecord);
			} else {
				try {
					IApplicationThreadCompat.scheduleBindService(r.process.appThread, r, service, false, 0);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			r.lastActivityTime = SystemClock.uptimeMillis();
			r.addToBoundIntent(service, connection);
			return 1;
		}
	}


	@Override
	public boolean unbindService(IServiceConnection connection, int userId) {
		synchronized (this) {
			ServiceRecord r = findRecordLocked(connection);
			if (r == null) {
				return false;
			}

			for (ServiceRecord.IntentBindRecord bindRecord : r.bindings) {
				if (!bindRecord.containConnection(connection)) {
					continue;
				}
				bindRecord.removeConnection(connection);
				try {
					IApplicationThreadCompat.scheduleUnbindService(r.process.appThread, r, bindRecord.intent);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			if (r.startId <= 0 && r.getConnectionCount() <= 0) {
				try {
					IApplicationThreadCompat.scheduleStopService(r.process.appThread, r);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					mHistory.remove(r);
				}
			}
			return true;
		}
	}

	@Override
	public void unbindFinished(IBinder token, Intent service, boolean doRebind, int userId) {
		synchronized (this) {
			ServiceRecord r = (ServiceRecord) token;
			if (r != null) {
				ServiceRecord.IntentBindRecord boundRecord = r.peekBinding(service);
				if (boundRecord != null) {
					boundRecord.doRebind = doRebind;
				}
			}
		}
	}


	@Override
	public boolean isVAServiceToken(IBinder token) {
		return token instanceof ServiceRecord;
	}


	@Override
	public void serviceDoneExecuting(IBinder token, int type, int startId, int res, int userId) {
		synchronized (this) {
			ServiceRecord r = (ServiceRecord) token;
			if (r == null) {
				return;
			}
			if (ActivityManagerCompat.SERVICE_DONE_EXECUTING_STOP == type) {
				mHistory.remove(r);
			}
		}
	}

	@Override
	public IBinder peekService(Intent service, String resolvedType, int userId) {
		synchronized (this) {
			ServiceInfo serviceInfo = resolveServiceInfo(service, userId);
			if (serviceInfo == null) {
				return null;
			}
			ServiceRecord r = findRecordLocked(userId, serviceInfo);
			if (r != null) {
				ServiceRecord.IntentBindRecord boundRecord = r.peekBinding(service);
				if (boundRecord != null) {
					return boundRecord.binder;
				}
			}
			return null;
		}
	}

	@Override
	public void publishService(IBinder token, Intent intent, IBinder service, int userId) {
		synchronized (this) {
			ServiceRecord r = (ServiceRecord) token;
			if (r != null) {
				ServiceRecord.IntentBindRecord boundRecord = r.peekBinding(intent);
				if (boundRecord != null) {
					boundRecord.binder = service;
					for (IServiceConnection conn : boundRecord.connections) {
						ComponentName component = ComponentUtils.toComponentName(r.serviceInfo);
						connectService(conn, component, boundRecord);
					}
				}
			}
		}
	}

	private void connectService(IServiceConnection conn, ComponentName component, ServiceRecord.IntentBindRecord r) {
		try {
			BinderDelegateService delegateService = new BinderDelegateService(component, r.binder);
			conn.connected(component, delegateService);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public VParceledListSlice<ActivityManager.RunningServiceInfo> getServices(int maxNum, int flags, int userId) {
		synchronized (mHistory) {
			int cnt = 0;
			int size = mHistory.size() > maxNum ? maxNum: mHistory.size();
			List<ActivityManager.RunningServiceInfo> services = new ArrayList<>(mHistory.size());
			for (ServiceRecord r : mHistory) {
//				if (r.process.userId != userId) {
//					continue;
//				}
				if (cnt++ >= size) break;
				ActivityManager.RunningServiceInfo info = new ActivityManager.RunningServiceInfo();
				info.uid = r.process.vuid;
//				info.uid = Process.myUid();
				info.pid = r.process.pid;
				ProcessRecord processRecord = findProcessLocked(r.process.pid);
				if (processRecord != null) {
					info.process = processRecord.processName;
					info.clientPackage = processRecord.info.packageName;
				}
				info.activeSince = r.activeSince;
				info.lastActivityTime = r.lastActivityTime;
				info.clientCount = r.getClientCount();
				info.service = ComponentUtils.toComponentName(r.serviceInfo);
				info.started = r.startId > 0;
				services.add(info);
			}
			return new VParceledListSlice<>(services);
		}
	}

	@Override
	public void processRestarted(String packageName, String processName, int userId) {
		int callingPid = getCallingPid();
		int appId = VAppManagerService.get().getAppId(packageName);
		int uid = VUserHandle.getUid(userId, appId);
		synchronized (this) {
			ProcessRecord app = findProcessLocked(callingPid);
			if (app == null) {
				ApplicationInfo appInfo = VPackageManagerService.get().getApplicationInfo(packageName, 0, userId);
				if (appInfo == null) {
					return;
				}
				appInfo.flags |= ApplicationInfo.FLAG_HAS_CODE;
				String stubProcessName = getProcessName(callingPid);
				int vpid = parseVPid(stubProcessName);
				if (vpid != -1) {
					performStartProcessLocked(uid, vpid, appInfo, processName);
				}
			}
		}
	}

	private int parseVPid(String stubProcessName) {
		String prefix = VirtualCore.get().getHostPkg() + ":p";
		if (stubProcessName != null && stubProcessName.startsWith(prefix)) {
			try {
				return Integer.parseInt(stubProcessName.substring(prefix.length()));
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		return -1;
	}


	private String getProcessName(int pid) {
		for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
			if (info.pid == pid) {
				return info.processName;
			}
		}
		return null;
	}


	private void attachClient(int pid, final IBinder clientBinder) {
		final IVClient client = IVClient.Stub.asInterface(clientBinder);
		if (client == null) {
			killProcess(pid);
			return;
		}
		IInterface thread = null;
		try {
			thread = ApplicationThreadNative.asInterface.call(client.getAppThread());
		} catch (RemoteException e) {
			// process has dead
		}
		if (thread == null) {
			killProcess(pid);
			return;
		}
		ProcessRecord app = null;
		try {
			IBinder token = client.getToken();
			if (token instanceof ProcessRecord) {
				app = (ProcessRecord) token;
			}
		} catch (RemoteException e) {
			// process has dead
		}
		if (app == null) {
			killProcess(pid);
			return;
		}
		try {
			final ProcessRecord record = app;
			clientBinder.linkToDeath(new DeathRecipient() {
				@Override
				public void binderDied() {
					clientBinder.unlinkToDeath(this, 0);
					onProcessDead(record);
				}
			}, 0);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		app.client = client;
		app.appThread = thread;
		app.pid = pid;
		synchronized (mProcessNames) {
			mProcessNames.put(app.processName, app.vuid, app);
			mPidsSelfLocked.put(app.pid, app);
		}
//		try {
//			client.bindApplication(app.info.packageName, app.processName);
//		} catch (RemoteException e) {
//			e.printStackTrace();
//		}
	}

	private void onProcessDead(ProcessRecord record) {
		VLog.d(TAG, "Process %s died.", record.processName);
		mProcessNames.remove(record.processName, record.vuid);
		mPidsSelfLocked.remove(record.pid);
		processDead(record);
		record.lock.open();
	}

	@Override
	public int getFreeStubCount() {
		return StubManifest.STUB_COUNT - mPidsSelfLocked.size();
	}

	@Override
	public int initProcess(String packageName, String processName, int userId) {
		synchronized (this) {
			ProcessRecord r = startProcessIfNeedLocked(processName, userId, packageName);
			return  r != null ? r.vpid : -1;
		}
	}

	public ProcessRecord startProcessIfNeedLocked(String processName, int userId, String packageName) {
		if (VActivityManagerService.get().getFreeStubCount() < 3) {
			// run GC
			killAllApps();
		}
		ApplicationInfo info = VPackageManagerService.get().getApplicationInfo(packageName, 0, userId);
		AppSetting setting = VAppManagerService.get().findAppInfo(info.packageName);
		int uid = VUserHandle.getUid(userId, setting.appId);
		ProcessRecord app = mProcessNames.get(processName, uid);
		if (app != null && app.client.asBinder().isBinderAlive()) {
			return app;
		}
		int vpid = queryFreeVPidForProcess();
		if (vpid == -1) {
			return null;
		}
		app = performStartProcessLocked(uid, vpid, info, processName);
		if (app != null) {
			app.pkgList.add(info.packageName);
		}
		return app;
	}


	@Override
	public int getUidByPid(int pid) {
		synchronized (mPidsSelfLocked) {
			ProcessRecord r = findProcessLocked(pid);
			if (r != null) {
				return r.vuid;
			}
		}
		return Process.myUid();
	}

	private ProcessRecord performStartProcessLocked(int vuid, int vpid, ApplicationInfo info, String processName) {
		ProcessRecord app = new ProcessRecord(info, processName, vuid, vpid);
		Bundle extras = new Bundle();
		BundleCompat.putBinder(extras, "_VA_|_binder_", app);
		extras.putInt( "_VA_|_vuid_", vuid);
		extras.putString("_VA_|_process_", processName);
		extras.putString("_VA_|_pkg_", info.packageName);
		Bundle res = ProviderCall.call(StubManifest.getStubAuthority(vpid), "_VA_|_init_process_", null, extras);
		if (res == null) {
			return null;
		}
		int pid = res.getInt("_VA_|_pid_");
		IBinder clientBinder = BundleCompat.getBinder(res, "_VA_|_client_");
		attachClient(pid, clientBinder);
		return app;
	}

	private int queryFreeVPidForProcess() {
		for (int vpid = 0; vpid < StubManifest.STUB_COUNT; vpid++) {
			int N = mPidsSelfLocked.size();
			boolean using = false;
			while (N-- > 0) {
				ProcessRecord r = mPidsSelfLocked.valueAt(N);
				if (r.vpid == vpid) {
					using = true;
					break;
				}
			}
			if (using) {
				continue;
			}
			return vpid;
		}
		return -1;
	}

	@Override
	public boolean isAppProcess(String processName) {
		return parseVPid(processName) != -1;
	}

	@Override
	public boolean isAppPid(int pid) {
		synchronized (mPidsSelfLocked) {
			return findProcessLocked(pid) != null;
		}
	}

	@Override
	public String getAppProcessName(int pid) {
		synchronized (mPidsSelfLocked) {
			ProcessRecord r = mPidsSelfLocked.get(pid);
			if (r != null) {
				return r.processName;
			}
		}
		return null;
	}

	@Override
	public List<String> getProcessPkgList(int pid) {
		synchronized (mPidsSelfLocked) {
			ProcessRecord r = mPidsSelfLocked.get(pid);
			if (r != null) {
				return new ArrayList<String>(r.pkgList);
			}
		}
		return null;
	}

	@Override
	public void killAllApps() {
		synchronized (mPidsSelfLocked) {
			for (int i = 0; i < mPidsSelfLocked.size(); i++) {
				ProcessRecord r = mPidsSelfLocked.valueAt(i);
				killProcess(r.pid);
			}
		}
	}

	@Override
	public void killAppByPkg(final String pkg, int userId) {
		synchronized (mProcessNames) {
			ArrayMap<String, SparseArray<ProcessRecord>> map = mProcessNames.getMap();
			int N = map.size();
			while (N-- > 0) {
				SparseArray<ProcessRecord> uids = map.valueAt(N);
				for (int i = 0; i < uids.size(); i++) {
					ProcessRecord r = uids.valueAt(i);
					if (userId != VUserHandle.USER_ALL) {
                        if (r.userId != userId) {
							continue;
						}
					}
					if (r.pkgList.contains(pkg)) {
						killProcess(r.pid);
					}
				}
			}
		}
	}

	@Override
	public boolean isAppRunning(String packageName, int userId) {
		boolean running = false;
		synchronized (mPidsSelfLocked) {
			int N = mPidsSelfLocked.size();
			while (N-- > 0) {
				ProcessRecord r = mPidsSelfLocked.valueAt(N);
				if (r.userId == userId && r.info.packageName.equals(packageName)) {
					running = true;
					break;
				}
			}
			return running;
		}
	}

	@Override
	public void killApplicationProcess(final String processName, int uid) {
		synchronized (mProcessNames) {
			ProcessRecord r = mProcessNames.get(processName, uid);
			if (r != null) {
				killProcess(r.pid);
			}
		}
	}

	@Override
	public void dump() {

	}

	@Override
	public void registerProcessObserver(IProcessObserver observer) {

	}

	@Override
	public void unregisterProcessObserver(IProcessObserver observer) {

	}

	@Override
	public String getInitialPackage(int pid) {
		synchronized (mPidsSelfLocked) {
			ProcessRecord r = mPidsSelfLocked.get(pid);
			if (r != null) {
				return r.info.packageName;
			}
			return null;
		}
	}

	@Override
	public void handleApplicationCrash() {
		// Nothing
	}

	@Override
	public void appDoneExecuting() {
		synchronized (mPidsSelfLocked) {
			ProcessRecord r = mPidsSelfLocked.get(VBinder.getCallingPid());
			if (r != null) {
				r.doneExecuting = true;
				r.lock.open();
			}
		}
	}


	/**
	 * Should guard by {@link VActivityManagerService#mPidsSelfLocked}
	 * @param pid pid
	 */
	public ProcessRecord findProcessLocked(int pid) {
		return mPidsSelfLocked.get(pid);
	}

	/**
	 * Should guard by {@link VActivityManagerService#mProcessNames}
	 * @param uid vuid
	 */
	public ProcessRecord findProcessLocked(String processName, int uid) {
		return mProcessNames.get(processName, uid);
	}

	public int stopUser(int userHandle, IStopUserCallback.Stub stub) {
		synchronized (mPidsSelfLocked) {
			int N = mPidsSelfLocked.size();
			while (N-- > 0) {
				ProcessRecord r = mPidsSelfLocked.valueAt(N);
                if (r.userId == userHandle) {
					killProcess(r.pid);
				}
			}
		}
		try {
			stub.userStopped(userHandle);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		return 0;
	}

	public void sendOrderedBroadcastAsUser(Intent intent, VUserHandle user, String receiverPermission,
										   BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
										   String initialData, Bundle initialExtras) {
		Context context = VirtualCore.get().getContext();
		intent.putExtra("_VA_|_user_id_", user.getIdentifier());
		// TODO: checkPermission
		context.sendOrderedBroadcast(intent, null/* permission */, resultReceiver, scheduler, initialCode, initialData,
				initialExtras);
	}

	public void sendBroadcastAsUser(Intent intent, VUserHandle user) {
		Context context = VirtualCore.get().getContext();
		intent.putExtra("_VA_|_user_id_", user.getIdentifier());
		context.sendBroadcast(intent);
	}

	public boolean bindServiceAsUser(Intent service, ServiceConnection connection, int flags, VUserHandle user) {
		service = new Intent(service);
		service.putExtra("_VA_|_user_id_", user.getIdentifier());
		return VirtualCore.get().getContext().bindService(service, connection, flags);
	}

	public void sendBroadcastAsUser(Intent intent, VUserHandle user, String permission) {
		Context context = VirtualCore.get().getContext();
		intent.putExtra("_VA_|_user_id_", user.getIdentifier());
		// TODO: checkPermission
		context.sendBroadcast(intent);
	}

	boolean handleStaticBroadcast(int appId, ActivityInfo info, Intent intent, BroadcastReceiver receiver,
								  BroadcastReceiver.PendingResult result) {
		// Maybe send from System
		int userId = intent.getIntExtra("_VA_|_user_id_", VUserHandle.USER_ALL);
		ComponentName component = intent.getParcelableExtra("_VA_|_component_");
		Intent realIntent = intent.getParcelableExtra("_VA_|_intent_");
		if (component != null) {
			if (!ComponentUtils.toComponentName(info).equals(component)) {
				return false;
			}
		}
		if (realIntent == null) {
			realIntent = intent;
		}
		VLog.d(TAG, "handleStaticBroadcast realintent:　" + realIntent.toString());
		String originAction = SpecialComponentList.unprotectAction(realIntent.getAction());
		if (originAction != null) {
			realIntent.setAction(originAction);
		}
		VLog.d(TAG, "handleStaticBroadcast unprotected realintent:　" + realIntent.toString());
		if (userId >= 0) {
			int uid = VUserHandle.getUid(userId, appId);
			if(!handleStaticBroadcastAsUser(uid, info, realIntent, receiver, result)) {
				VLog.d(TAG, "handleStaticBroadcastAsUser ret false");
				return false;
			}
		} else if (userId == VUserHandle.USER_ALL) {
			List<VUserInfo> userList = VUserManager.get().getUsers(false);
			for (VUserInfo userInfo : userList) {
				int uid = VUserHandle.getUid(userInfo.id, appId);
				if(!handleStaticBroadcastAsUser(uid, info, realIntent, receiver, result)) {
					VLog.d(TAG, "handleStaticBroadcastAsUser USER_ALL ret false");
					return false;
				}
			}
		} else {
			VLog.w(TAG, "Unknown User for receive the broadcast : #%d.", userId);
			return false;
		}
		return true;
	}


	private boolean handleStaticBroadcastAsUser(int uid, ActivityInfo info, Intent intent, BroadcastReceiver receiver,
											 BroadcastReceiver.PendingResult result) {
		synchronized (this) {
			ProcessRecord r = findProcessLocked(info.processName, uid);
			if (BROADCAST_NOT_STARTED_PKG && r == null) {
				VLog.d(TAG, "startProcess for " + intent.toString());
				r = startProcessIfNeedLocked(info.processName, getUserId(uid), info.packageName);
			}
			if (r != null && r.appThread != null) {
				VLog.d(TAG, "performReceive " + intent.toString());
				performScheduleReceiver(r.appThread, getUserId(uid), info, intent, receiver.isOrderedBroadcast(),
						result);
				return true;
			} else {
				VLog.logbug(TAG, "Not schedule receiver for not started process: " + intent.toString());
				return false;
			}
		}
	}

	private void performScheduleReceiver(IInterface thread, int sendingUser, ActivityInfo info, Intent intent,
										 boolean sync, BroadcastReceiver.PendingResult result) {

		ComponentName componentName = ComponentUtils.toComponentName(info);
		VLog.d(TAG, "E performScheduleReceiver");
		if (intent.getComponent() != null && !componentName.equals(intent.getComponent())) {
			VLog.logbug(TAG, "intent cmp name error " + intent.toString());
			//result.finish();
			return;
		}
		if (intent.getComponent() == null) {
			intent.setComponent(componentName);
		}
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				IApplicationThreadKitkat.scheduleReceiver.call(thread, intent, info,
						CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO.get(), result.getResultCode(), result.getResultData(),
						result.getResultExtras(false), sync, sendingUser, 0);
			} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				IApplicationThreadJBMR1.scheduleReceiver.call(thread, intent, info,
						CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO.get(), result.getResultCode(), result.getResultData(),
						result.getResultExtras(false), sync, sendingUser);
			} else {
				IApplicationThreadICSMR1.scheduleReceiver.call(thread, intent, info,
						CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO.get(), result.getResultCode(), result.getResultData(),
						result.getResultExtras(false), sync);
			}
		} catch (Throwable e) {
//			if (result != null) {
//				result.finish();
//			}
		}
		VLog.d(TAG, "X performScheduleReceiver");
	}
}