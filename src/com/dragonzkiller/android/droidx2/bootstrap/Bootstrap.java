package com.dragonzkiller.android.droidx2.bootstrap;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.dragonzkiller.android.droidx2.bootstrap.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;

public class Bootstrap extends Activity {

	private static final String TAG = "DX2B/Bootstrap";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		unzipAssets();

		final CheckBox rebootOption = (CheckBox) findViewById(R.id.checkBoxRecoveryOption);

		final String filesDir = getFilesDir().getAbsolutePath();
		try {
			boolean setCheck = readSettingsFile(filesDir);
			rebootOption.setChecked(setCheck);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}

		Button flash = (Button) findViewById(R.id.flash);
		flash.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				String scndinit = filesDir + "/2nd-init";
				String taskset = filesDir + "/taskset";
				String busybox = filesDir + "/busybox";
				String hijack = filesDir + "/hijack";
				String hijackKillBoot = filesDir + "/hijack.killboot";
				String motboot = filesDir + "/mot_boot_mode.bin";
				String updatebinary = filesDir + "/update-binary";
				String recoveryzip = filesDir + "/update-recovery.zip";
				String adbd = filesDir + "/adbd";
				String initrc = filesDir + "/init.rc";

				StringBuilder command = new StringBuilder();

				ROMBootstrapSettings mySettings = new ROMBootstrapSettings();
				
				if (mySettings.installHijack())
				{
					Log.d(TAG, "Installing hijack");
					command.append(busybox + " mount -oremount,rw /system ; ");
					command.append(busybox + " cp -f " + motboot
							+ " /system/bin/mot_boot_mode.bin ; ");
					command.append(busybox + " cp -f " + hijack
							+ " /system/bin/hijack ; ");
					command.append(busybox + " cp -f " + hijackKillBoot
							+ " /system/bin/hijack.killboot ; ");
					command.append("cd /system/bin ; rm mot_boot_mode ; ln -s hijack mot_boot_mode ; ");
					command.append("mkdir -p /system/etc/rootfs ; cp -f " + initrc + " /system/etc/rootfs/init.rc ; ");
					command.append(busybox + " cp -f " + scndinit + " /system/xbin/2nd-init ; ");
					command.append(busybox + " cp -f " + taskset + " /system/xbin/taskset ; ");
					command.append(busybox + " mount -oremount,ro /system ; ");
				}

				// if(settings.installRecovery()) {
				if (rebootOption.isChecked()) {
					Log.d(TAG, "Installing recovery");
					command.append(busybox
							+ " mount -oremount,rw /preinstall ; ");
					// command.append(busybox +
					// " mkdir -p /preinstall/recovery ; ");
					command.append(busybox + " cp " + updatebinary
							+ " /preinstall/update-binary ; ");
					command.append(busybox + " cp -f " + recoveryzip
							+ " /preinstall/update-recovery.zip ; ");
					command.append(busybox + " cp -f " + hijack
							+ " /preinstall/hijack ; ");
					command.append(busybox + " cp -f " + hijackKillBoot
							+ "/preinstall/hijack.killboot ; ");
					command.append(busybox + " cp -f " + motboot
							+ " /preinstall/mot_boot_mode.bin ; ");
					command.append(busybox
							+ " mount -oremount,ro /preinstall ; ");
				} else {
					Log.d(TAG, "Installing normal mode");
					command.append(busybox + " mount -oremount,rw /system ; ");
					command.append(busybox + " cp -f " + motboot
							+ " /system/bin/mot_boot_mode.bin ; ");
					command.append(busybox + " cp -f " + hijack
							+ " /system/bin/hijack ; ");
					command.append(busybox + " cp -f " + hijackKillBoot
							+ " /system/bin/hijack.killboot ; ");
					command.append("cd /system/bin ; rm mot_boot_mode ; ln -s mot_boot_mode.bin mot_boot_mode ; ");
					command.append(busybox + " mount -oremount,ro /system ; ");
				}
				
				if(mySettings.restartAdb()) {
					Log.d(TAG, "Restarting ADB as Root");
					command.append(busybox + " mount -orw,remount / ; ");
					command.append("mv /sbin/adbd /sbin/adbd.old ; ");
					command.append(busybox + " cp -f " + adbd + " /sbin/adbd ; ");
					command.append(busybox + " mount -oro,remount / ; ");
					command.append(busybox + " kill $(ps | " + busybox
							+ " grep adbd) ;");
	
					// prevent recovery and charge mode from booting here
					Log.d(TAG, "Removing charging_mode trigger");
					command.append("rm /data/.charging_mode ; ");
					Log.d(TAG, "Removing recovery_mode trigger");
					command.append("rm /data/.recovery_mode ; ");
				}
				
				try {
					writeSettingsFile(filesDir, rebootOption.isChecked());
				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
				}

				AlertDialog.Builder builder = new Builder(Bootstrap.this);
				builder.setPositiveButton(android.R.string.ok, null);
				try {
					Helper.runSuCommand(Bootstrap.this, command.toString());
					builder.setTitle("Success!");
					builder.setMessage("Make sure you're plugged into a wall outlet and press \"Reboot\" to reboot into your new mode.");
				} catch (Exception e) {
					builder.setTitle("Failure");
					builder.setMessage(e.getMessage());
					e.printStackTrace();
				}
				builder.create().show();
			}
		});

		Button reboot = (Button) findViewById(R.id.reboot);
		reboot.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				StringBuilder command = new StringBuilder();
				command.append("echo 1 > /data/.recovery_mode ; "); // this SHOULD fire and call our 2nd-init upon boot
				command.append("stop ssmgrd ; ");
				command.append("sync ; /system/bin/sleep 10 ; /system/bin/reboot ; setprop Drmdll.asfdrm libnvdrm.so ;"); // without the -p for reboot
				try {
					Helper.runSuCommand(Bootstrap.this, command.toString());
				} catch (Exception e) {
					AlertDialog.Builder builder = new Builder(Bootstrap.this);
					builder.setPositiveButton(android.R.string.ok, null);
					builder.setTitle("Failure");
					builder.setMessage(e.getMessage());
					e.printStackTrace();
				}
			}
		});
	}
	
	void writeSettingsFile(String filesDir, boolean checkState) throws IOException
	{
		// In case something happened to the file
		File nde = new File(filesDir + "/DroidX2RebootOpt.cfg");
		nde.createNewFile();
		
		FileOutputStream os = new FileOutputStream(filesDir + "/DroidX2RebootOpt.cfg");
		os.write((new String()).getBytes());	// Empty it here
		
		DataOutputStream dos = new DataOutputStream(os);
		dos.writeBoolean(checkState);
		
		dos.close();
		os.close();
	}
	
	boolean readSettingsFile(String filesDir) throws IOException
	{
		File nde = new File(filesDir + "/DroidX2RebootOpt.cfg");
		if(!nde.exists())
			return false;
		else {
			FileInputStream is = new FileInputStream(filesDir + "/DroidX2RebootOpt.cfg");
			DataInputStream dis = new DataInputStream(is);
			
			boolean isInRecovery = dis.readBoolean();
			
			dis.close();
			
			return isInRecovery;
		}
	}

	final static String LOGTAG = "DroidX2Bootstrap";
	final static String ZIP_FILTER = "assets";

	void unzipAssets() {
		String apkPath = getPackageCodePath();
		String mAppRoot = getFilesDir().toString();
		try {
			File zipFile = new File(apkPath);
			long zipLastModified = zipFile.lastModified();
			ZipFile zip = new ZipFile(apkPath);
			Vector<ZipEntry> files = getAssets(zip);
			int zipFilterLength = ZIP_FILTER.length();

			Enumeration<?> entries = files.elements();
			while (entries.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) entries.nextElement();
				String path = entry.getName().substring(zipFilterLength);
				File outputFile = new File(mAppRoot, path);
				outputFile.getParentFile().mkdirs();

				if (outputFile.exists()
						&& entry.getSize() == outputFile.length()
						&& zipLastModified < outputFile.lastModified())
					continue;
				FileOutputStream fos = new FileOutputStream(outputFile);
				copyStreams(zip.getInputStream(entry), fos);
				Runtime.getRuntime().exec(
						"chmod 755 " + outputFile.getAbsolutePath());
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Error: " + e.getMessage());
		}
	}

	static final int BUFSIZE = 5192;

	void copyStreams(InputStream is, FileOutputStream fos) {
		BufferedOutputStream os = null;
		try {
			byte data[] = new byte[BUFSIZE];
			int count;
			os = new BufferedOutputStream(fos, BUFSIZE);
			while ((count = is.read(data, 0, BUFSIZE)) != -1) {
				os.write(data, 0, count);
			}
			os.flush();
		} catch (IOException e) {
			Log.e(LOGTAG, "Exception while copying: " + e);
		} finally {
			try {
				if (os != null) {
					os.close();
				}
			} catch (IOException e2) {
				Log.e(LOGTAG, "Exception while closing the stream: " + e2);
			}
		}
	}

	public Vector<ZipEntry> getAssets(ZipFile zip) {
		Vector<ZipEntry> list = new Vector<ZipEntry>();
		Enumeration<?> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = (ZipEntry) entries.nextElement();
			if (entry.getName().startsWith(ZIP_FILTER)) {
				list.add(entry);
			}
		}
		return list;
	}
}
