package ru.kazantsev.gallery.activity;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import net.vrallev.android.cat.Cat;

import ru.kazantsev.gallery.fragment.LogoFragment;
import ru.kazantsev.template.activity.BaseActivity;
import ru.kazantsev.gallery.R;
import ru.kazantsev.gallery.fragment.GalleryFragment;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends BaseActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disableNavigationBar = true;
        super.onCreate(savedInstanceState);
        replaceFragment(LogoFragment.class);
        setTitle(getResString(R.string.app_name));
    }

    @Override
    protected void handleIntent(Intent intent) {
        
    }

}
