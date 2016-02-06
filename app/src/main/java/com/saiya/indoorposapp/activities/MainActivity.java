package com.saiya.indoorposapp.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.saiya.indoorposapp.R;
import com.saiya.indoorposapp.bean.SceneInfo;
import com.saiya.indoorposapp.bean.WifiFingerprint;
import com.saiya.indoorposapp.exceptions.UnauthorizedException;
import com.saiya.indoorposapp.fragments.PositioningFragment;
import com.saiya.indoorposapp.fragments.SettingsFragment;
import com.saiya.indoorposapp.fragments.UpdateFPFragment;
import com.saiya.indoorposapp.fragments.UpdateMapFragment;
import com.saiya.indoorposapp.tools.ActivityCollector;
import com.saiya.indoorposapp.tools.HttpUtils;
import com.saiya.indoorposapp.tools.PreferencessHelper;
import com.saiya.indoorposapp.ui.BottomTabView;
import com.saiya.indoorposapp.ui.MyViewPager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 主Activity,包含定位,更新指纹,更新地图,设置四个页面Fragment
 */
public class MainActivity extends FragmentActivity implements OnPageChangeListener, OnClickListener, SensorEventListener{

    /** 账户过期的消息代号 */
    public static final int UNAUTHORIZED = -1;
    /** 网络错误的消息代号 */
    public static final int NETWORK_ERROR = 0;
    /** 更新指纹数据成功的消息代号 */
    public static final int UPDATE_FP_SUCCEED = 1;
    /** 更新地图成功的消息代号 */
    public static final int UPDATE_MAP_SUCCEED = 2;
    /** 下载地图成功的消息代号 */
    public static final int DOWNLOAD_MAP_SUCCEED = 3;

    //用于显示Fragment
    private MyViewPager vp_main_pager;

    private FragmentPagerAdapter mAdapter;
    private List<BottomTabView> mTabIndicator = new ArrayList<>();
    private List<Fragment> mTabs = new ArrayList<>();

    /** 传感器管理器 */
    private SensorManager mSensorManager;
    /** WiFi管理器 */
    private WifiManager mWifiManager;
    /** 磁场强度,float[0]为Y方向,float[1]为Z方向 */
    private float[] mGeomagneticRSS;
    /** 用户设置信息帮助类 */
    private PreferencessHelper mPreferences;
    /** 处理异步更新UI的Handler实例 */
    private MyHandler myHandler;

    public PreferencessHelper getPreferences() {
        return mPreferences;
    }

    public MyViewPager getMyViewPager() {
        return vp_main_pager;
    }

    public MyHandler getMyHandler() {
        return myHandler;
    }

    public Fragment getFragment(int which) {
        return mTabs.get(which);
    }

    //用于在子线程更新UI的MyHandler类
    public static class MyHandler extends Handler {

        private WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        //处理更新指纹数据时返回的消息
        @Override
        public void handleMessage(Message msg) {
            if(mActivity.get() == null)
                return;
            switch (msg.what) {
                case MainActivity.UNAUTHORIZED:
                    Intent intent = new Intent("com.saiya.indoorposapp.FORCE_OFFLINE");
                    mActivity.get().sendBroadcast(intent);
                    break;
                case MainActivity.NETWORK_ERROR:
                    Toast.makeText(mActivity.get(), R.string.activity_common_unexpectedError, Toast.LENGTH_SHORT).show();
                    break;
                case MainActivity.UPDATE_FP_SUCCEED:
                    Toast.makeText(mActivity.get(), R.string.fragment_updateFP_updateSucceed, Toast
                            .LENGTH_SHORT).show();
                    break;
                case MainActivity.UPDATE_MAP_SUCCEED:
                    Toast.makeText(mActivity.get(), R.string.fragment_updateMap_updateSucceed, Toast
                            .LENGTH_SHORT).show();
                    break;
                case MainActivity.DOWNLOAD_MAP_SUCCEED:
                    Toast.makeText(mActivity.get(), R.string.fragment_positioning_downloadMapSucceed, Toast
                            .LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }

    }

    /**
     * 选择场景的异步任务类
     */
    public static class ChooseSceneTask extends AsyncTask<Void, Void, String[]> {

        private ProgressDialog mProgressDialog;
        private MainActivity mActivity;
        private List<SceneInfo> mSceneList;

        public ChooseSceneTask(MainActivity activity) {
            mActivity = activity;
        }

        @Override
        protected void onPreExecute() {
            //创建一个进度条对话框
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage(mActivity.getString(R.string.fragment_updateFP_gettingList));
            mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();
        }

        @Override
        protected String[] doInBackground(Void... params) {
            try {
                mSceneList = HttpUtils.getSceneList();
                if(mSceneList == null || mSceneList.size() == 0) {
                    Message msg = new Message();
                    msg.what = NETWORK_ERROR;
                    mActivity.getMyHandler().sendMessage(msg);
                }
                else {
                    String[] sceneListArray = new String[mSceneList.size()];
                    for(int i = 0; i < mSceneList.size(); ++i) {
                        sceneListArray[i] = mSceneList.get(i).getSceneName();
                    }
                    return sceneListArray;
                }
            } catch (UnauthorizedException e) {
                e.printStackTrace();
                Message msg = new Message();
                msg.what = UNAUTHORIZED;
                mActivity.getMyHandler().sendMessage(msg);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            mProgressDialog.dismiss();
            if(strings == null)
                return;
            DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {

                /** 记录被选择的单选项序号,初始化为-1,表示未选择 */
                private int mSelectedWhich = -1;

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //若点确定按钮,做对应的操作
                    if(which == AlertDialog.BUTTON_POSITIVE) {
                        if(mSelectedWhich != -1)
                            onChooseScene(mSceneList.get(mSelectedWhich).getSceneName(), mSceneList.get(mSelectedWhich).getScale());
                    }
                    //若点击单选项,仅改变mSelectedWhich的值
                    else
                        mSelectedWhich = which;
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.fragment_updateMap_chooseSceneName);
            builder.setSingleChoiceItems(strings, -1, onClickListener);
            builder.setPositiveButton(R.string.fragment_settings_confirm, onClickListener);
            builder.show();
        }

        protected void onChooseScene(String sceneName, float mapScale) {

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        /** 当前用户的用户名 */
        String username = getIntent().getStringExtra("username");
        vp_main_pager = (MyViewPager) findViewById(R.id.vp_main_pager);
        initDatas();
        vp_main_pager.setAdapter(mAdapter);
        vp_main_pager.addOnPageChangeListener(this);
        mGeomagneticRSS = new float[2];
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        myHandler = new MyHandler(this);
        mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        mPreferences = new PreferencessHelper(username, this);
        ActivityCollector.addActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor
                .TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor
                .TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityCollector.removeActivity(this);
    }

    private void initDatas() {
        mTabs.add(new PositioningFragment());
        mTabs.add(new UpdateFPFragment());
        mTabs.add(new UpdateMapFragment());
        mTabs.add(new SettingsFragment());

        mAdapter = new FragmentPagerAdapter(getSupportFragmentManager()) {

            @Override
            public int getCount() {
                return mTabs.size();
            }

            @Override
            public Fragment getItem(int arg0) {
                return mTabs.get(arg0);
            }
        };

        initTabIndicator();

    }

    private void initTabIndicator()
    {
        BottomTabView btv_main_positioning = (BottomTabView) findViewById(R.id.btv_main_positioning);
        BottomTabView btn_main_undatefingerprint = (BottomTabView) findViewById(R.id.btv_main_updatefingerprint);
        BottomTabView btv_main_updatemap = (BottomTabView) findViewById(R.id.btv_main_updatemap);
        BottomTabView btv_main_settings = (BottomTabView) findViewById(R.id.btv_main_settings);

        mTabIndicator.add(btv_main_positioning);
        mTabIndicator.add(btn_main_undatefingerprint);
        mTabIndicator.add(btv_main_updatemap);
        mTabIndicator.add(btv_main_settings);

        btv_main_positioning.setOnClickListener(this);
        btn_main_undatefingerprint.setOnClickListener(this);
        btv_main_updatemap.setOnClickListener(this);
        btv_main_settings.setOnClickListener(this);

        btv_main_positioning.setIconAlpha(1.0f);
    }

    @Override
    public void onPageSelected(int arg0)
    {

    }

    @Override
    public void onPageScrolled(int position, float positionOffset,
                               int positionOffsetPixels)
    {

        if (positionOffset > 0)
        {
            BottomTabView left = mTabIndicator.get(position);
            BottomTabView right = mTabIndicator.get(position + 1);
            left.setIconAlpha(1 - positionOffset);
            right.setIconAlpha(positionOffset);
        }

    }

    @Override
    public void onPageScrollStateChanged(int state)
    {

    }

    /**
     * 重置其他的Tab
     */
    private void resetOtherTabs()
    {
        for (int i = 0; i < mTabIndicator.size(); i++)
        {
            mTabIndicator.get(i).setIconAlpha(0);
        }
    }

    /** 存储旋转矩阵的值 */
    private float[] r = new float[9];
    /** 存储地磁指纹的Y方向值和Z方向值 */
    private float[] result = new float[2];
    /** 用于存储加速度传感器的值 */
    private float[] avalues = new float[3];
    /** 用于存储地磁传感器的值 */
    private float[] mvalues = new float[3];

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                avalues[0] = event.values[0];
                avalues[1] = event.values[1];
                avalues[2] = event.values[2];
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mvalues[0] = event.values[0];
                mvalues[1] = event.values[1];
                mvalues[2] = event.values[2];
                break;
            default:
                break;
        }
        if (SensorManager.getRotationMatrix(r, null, avalues, mvalues)) {
            result[0] = r[3] * mvalues[0] + r[4] * mvalues[1] + r[5] * mvalues[2];
            result[1] = r[6] * mvalues[0] + r[7] * mvalues[1] + r[8] * mvalues[2];
        }
        MainActivity.this.setGeomagneticRSS(result[0], result[1]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onClick(View v)
    {

        resetOtherTabs();
        switch (v.getId())
        {
            case R.id.btv_main_positioning:
                mTabIndicator.get(0).setIconAlpha(1.0f);
                vp_main_pager.setCurrentItem(0, false);
                break;
            case R.id.btv_main_updatefingerprint:
                mTabIndicator.get(1).setIconAlpha(1.0f);
                vp_main_pager.setCurrentItem(1, false);
                break;
            case R.id.btv_main_updatemap:
                mTabIndicator.get(2).setIconAlpha(1.0f);
                vp_main_pager.setCurrentItem(2, false);
                break;
            case R.id.btv_main_settings:
                mTabIndicator.get(3).setIconAlpha(1.0f);
                vp_main_pager.setCurrentItem(3, false);
            default:
                break;
        }

    }

    /**
     * 获取信号强度最强的前n个WiFi信息
     * @param n 指定获取WiFi信息的最大个数,
     * @return 返回Bundle,其中mac字段存储了MAC地址,rssi字段存储了RSSI数据,都为用逗号拼接的String.若WiFi未打开返回null.
     */
    public String[] getWifiScanResult(int n) {
        if(mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            Toast.makeText(this, R.string.activity_main_wifiDisabled, Toast.LENGTH_SHORT).show();
            return null;
        }
        mWifiManager.startScan();
        List<ScanResult> scanResultList = mWifiManager.getScanResults();
        Collections.sort(scanResultList, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                if (lhs.level > rhs.level)
                    return -1;
                else if (lhs.level < rhs.level)
                    return 1;
                else
                    return 0;
            }
        });
        StringBuilder mac = new StringBuilder();
        StringBuilder rssi = new StringBuilder();
        for(int i = 0; i < n && i < scanResultList.size(); ++i) {
            mac.append(scanResultList.get(i).BSSID).append(",");
            rssi.append(scanResultList.get(i).level).append(",");
        }
        mac.deleteCharAt(mac.length() - 1);
        rssi.deleteCharAt(rssi.length() - 1);
        return new String[]{mac.toString(), rssi.toString()};
    }

    /**
     * 获取所有WiFi信号信息,String为MAC地址,Float为信号强度
     * @return 返回存储了WiFi信号信息的Map
     */
    public List<WifiFingerprint> getWifiScanResult() {
        if(mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            Toast.makeText(this, R.string.activity_main_wifiDisabled, Toast.LENGTH_SHORT).show();
            return null;
        }
        mWifiManager.startScan();
        List<ScanResult> scanResultList = mWifiManager.getScanResults();
        List<WifiFingerprint> result = new ArrayList<>();
        for(ScanResult scanResult : scanResultList)
            result.add(new WifiFingerprint(scanResult.BSSID, (float) scanResult.level));
        return result;
    }

    public float[] getGeomagneticRSS() {
        return mGeomagneticRSS;
    }

    public void setGeomagneticRSS(float geomagnetic_y, float geomagnetic_z) {
        mGeomagneticRSS[0] = geomagnetic_y;
        mGeomagneticRSS[1] = geomagnetic_z;
    }

}