package ru.kazantsev.gallery.fragment;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import butterknife.BindView;
import butterknife.OnClick;
import ru.kazantsev.gallery.R;
import ru.kazantsev.template.activity.BaseActivity;
import ru.kazantsev.template.fragments.BaseFragment;


public class LogoFragment extends BaseFragment {


    public static LogoFragment show(BaseFragment fragment) {
        return show(fragment, LogoFragment.class);
    }


    @BindView(R.id.logo_start)
    Button start;
    @BindView(R.id.logo_exit)
    Button exit;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_logo, container, false);
        this.bind(rootView);
        return rootView;
    }

    @OnClick(R.id.logo_start)
    public void onClickStart(View v) {
        getBaseActivity().doActionWithPermission(Manifest.permission.READ_EXTERNAL_STORAGE, new BaseActivity.PermissionAction() {
            @Override
            public void doAction(boolean b) {
                if(b){
                    GalleryFragment.show(LogoFragment.this);
                }
            }
        });
    }

    @OnClick(R.id.logo_exit)
    public void onClickExit(View v) {
         getBaseActivity().finish();
    }
}
