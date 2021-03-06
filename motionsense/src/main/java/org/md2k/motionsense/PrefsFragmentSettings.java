package org.md2k.motionsense;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.scan.ScanResult;
import com.polidea.rxandroidble.scan.ScanSettings;

import org.md2k.datakitapi.source.METADATA;
import org.md2k.datakitapi.source.platform.Platform;
import org.md2k.datakitapi.source.platform.PlatformType;
import org.md2k.mcerebrum.commons.dialog.Dialog;
import org.md2k.motionsense.configuration.ConfigurationManager;
import org.md2k.motionsense.device.motionsense.MotionSense;
import org.md2k.motionsense.device.motionsense_hrv.MotionSenseHRV;
import org.md2k.motionsense.device.motionsense_hrv_plus.MotionSenseHRVPlus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import rx.Observer;
import rx.Subscription;

/**
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p/>
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * <p/>
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
public class PrefsFragmentSettings extends PreferenceFragment {
    Subscription scanSubscription;
    HashMap<String, BluetoothDevice> devices;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        devices=new HashMap<>();
        addPreferencesFromResource(R.xml.pref_settings);
        setPreferenceScreenConfigured();
    }

    @Override
    public void onResume() {
        scan();
        super.onResume();
    }

    void scan() {
        RxBleClient rxBleClient = MyApplication.getRxBleClient(getActivity());
        scanSubscription = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        ).subscribe(new Observer<ScanResult>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(getActivity(), "!!! ERROR !!! e=" + e.toString(), Toast.LENGTH_LONG).show();
                getActivity().finish();
            }

            @Override
            public void onNext(ScanResult scanResult) {
                String name = scanResult.getScanRecord().getDeviceName();
                List<ParcelUuid> p = scanResult.getScanRecord().getServiceUuids();
                if (p == null || p.size() != 1 || name == null) return;
                if (!(MotionSense.is(name, p.get(0).toString()) || MotionSenseHRV.is(name, p.get(0).toString()) || MotionSenseHRVPlus.is(name, p.get(0).toString())))
                    return;
                if(!devices.containsKey(scanResult.getBleDevice().getMacAddress()))
                    devices.put(scanResult.getBleDevice().getMacAddress(), scanResult.getBleDevice().getBluetoothDevice());
                if (ConfigurationManager.isConfigured(scanResult.getBleDevice().getMacAddress()))
                    return;
                if (MotionSense.is(name, p.get(0).toString()))
                    addToPreferenceScreenAvailable(PlatformType.MOTION_SENSE, scanResult.getBleDevice().getMacAddress());
                else if (MotionSenseHRV.is(name, p.get(0).toString()))
                    addToPreferenceScreenAvailable(PlatformType.MOTION_SENSE_HRV, scanResult.getBleDevice().getMacAddress());
                else
                    addToPreferenceScreenAvailable(PlatformType.MOTION_SENSE_HRV_PLUS, scanResult.getBleDevice().getMacAddress());
            }
        });
    }


    void setPreferenceScreenConfigured() {
        PreferenceCategory category = (PreferenceCategory) findPreference("key_device_configured");
        category.removeAll();
        ArrayList<Platform> platforms = ConfigurationManager.getPlatforms();
        for (int i = 0; i < platforms.size(); i++) {
            Preference preference = new Preference(getActivity());
            preference.setKey(platforms.get(i).getMetadata().get(METADATA.DEVICE_ID));
            preference.setTitle(platforms.get(i).getId());
            preference.setSummary(platforms.get(i).getType() + " (" + platforms.get(i).getMetadata().get(METADATA.DEVICE_ID) + ")");
            if (platforms.get(i).getType().equals(PlatformType.MOTION_SENSE_HRV_PLUS))
                preference.setIcon(R.drawable.ic_watch_plus);
            else if (platforms.get(i).getType().equals(PlatformType.MOTION_SENSE_HRV))
                preference.setIcon(R.drawable.ic_watch_heart);
            else
                preference.setIcon(R.drawable.ic_watch);
            preference.setOnPreferenceClickListener(preferenceListenerConfigured());
            category.addPreference(preference);
        }
    }

    void addToPreferenceScreenAvailable(String type, String deviceId) {
        final PreferenceCategory category = (PreferenceCategory) findPreference("key_device_available");
        for (int i = 0; i < category.getPreferenceCount(); i++)
            if (category.getPreference(i).getKey().equals(deviceId))
                return;
        ListPreference listPreference = new ListPreference(getActivity());
        listPreference.setEntryValues(R.array.wrist_entryValues_extended);
        listPreference.setEntries(R.array.wrist_entries_extended);

        if (ConfigurationManager.hasDefault()) {
            String[] s = ConfigurationManager.getPlatformIdFromDefault();
            if (s != null && s.length != 0) {
                listPreference.setEntryValues(s);
                listPreference.setEntries(s);
            }
        }
        listPreference.setKey(deviceId);
        listPreference.setTitle(deviceId);
        listPreference.setSummary(type);
        switch (type) {
            case PlatformType.MOTION_SENSE_HRV:
                listPreference.setIcon(R.drawable.ic_watch_heart);
                break;
            case PlatformType.MOTION_SENSE_HRV_PLUS:
                listPreference.setIcon(R.drawable.ic_watch_plus);
                break;
            default:
                listPreference.setIcon(R.drawable.ic_watch);
                break;
        }
        listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (ConfigurationManager.isConfigured(newValue.toString(), preference.getKey()))
                Toast.makeText(getActivity(), "Device: " + preference.getKey() + "and/or Placement:" + newValue.toString() + " already configured", Toast.LENGTH_LONG).show();
            else {
                ConfigurationManager.addPlatform(getActivity(), preference.getSummary().toString(), newValue.toString(), preference.getKey());
                if(devices.containsKey(preference.getKey()))
                    BLEPair.pairDevice(getActivity(), devices.get(preference.getKey()));
                setPreferenceScreenConfigured();
                category.removePreference(preference);
            }
            return false;
        });
        category.addPreference(listPreference);
    }

    private Preference.OnPreferenceClickListener preferenceListenerConfigured() {
        return preference -> {
            final String deviceId = preference.getKey();
            Dialog.simple(getActivity(), "Delete Device", "Delete Device (" + preference.getTitle() + ")?", "Delete", "Cancel", value -> {
                if ("Delete".equals(value)) {
                    ConfigurationManager.deleteDevice(deviceId);
                    if(devices.containsKey(deviceId))
                        BLEPair.unpairDevice(getActivity(), devices.get(deviceId));
                    setPreferenceScreenConfigured();
                }
            }).show();
            return true;
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        assert v != null;
        ListView lv = (ListView) v.findViewById(android.R.id.list);
        lv.setPadding(0, 0, 0, 0);
        return v;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                getActivity().finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        if (scanSubscription != null && !scanSubscription.isUnsubscribed())
            scanSubscription.unsubscribe();
        super.onPause();
    }
}
