package com.example.mockproject.view.main;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mockproject.databinding.ActivityMainBinding;
import com.example.mockproject.R;
import com.example.mockproject.service.ClearMediaPlayerListener;
import com.example.mockproject.service.RunMedia;
import com.example.mockproject.service.Services;
import com.example.mockproject.utils.Utils;
import com.example.mockproject.view.main.fragmentelement.song.element.allsong.NowPlayingFragment;
import com.example.mockproject.view.main.fragmentelement.song.element.allsong.NowPlayingFragment.FragmentCallback;
import com.example.mockproject.view.main.fragmentelement.song.element.allsong.Song;
import com.example.mockproject.view.main.fragmentelement.song.element.allsong.SongOnClickListener;
import com.example.mockproject.view.main.fragmentelement.song.element.allsong.SongViewModel;
import com.example.mockproject.view.main.fragmentelement.song.element.allsong.SongsAdapter;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.tankery.lib.circularseekbar.CircularSeekBar;

public class MainActivity extends AppCompatActivity implements OpenNavListener, FragmentCallback {
    private static final String TAG = "MainActivity";
    static SongsAdapter songsAdapter;
    private boolean inService = false;
    public static Handler handler;
    public static int currentSong = -1;
    SongViewModel songViewModel;
    public static List<Song> songList = new ArrayList<>();
    public static Services services;
    private ActivityMainBinding mBinding;

    private MainActivityViewModel mainActivityViewModel;
    NavHostFragment navHostFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        songViewModel = new ViewModelProvider(this).get(SongViewModel.class);

        mainActivityViewModel = new ViewModelProvider(this)
                .get(MainActivityViewModel.class);
//        Log.d(TAG, "onCreate: " + songList);
        //Setup ViewPager
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this);
        mBinding.viewPager.setAdapter(viewPagerAdapter);

        //layout bottom control
        mBinding.playPause.setOnClickListener(v -> services.play_pause());
        mBinding.previous.setOnClickListener(v -> services.previous());
        mBinding.next.setOnClickListener(v -> services.next());


        mBinding.layoutBottomControl.setVisibility(View.GONE);
        mBinding.seekbarDuration.setVisibility(View.GONE);
        mBinding.circleSeekbar.setVisibility(View.GONE);

        //Create navHost and add BottomNav to navController
        navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainerView);
        NavController navController = Objects.requireNonNull(navHostFragment).getNavController();
//         navController = navHostFragment.getNavController();
        setUpBottomNav(navController);

        //check permission
        boolean hasPermission = hasPermission();
        if (hasPermission) {
            songList = songViewModel.getSongList(this);
            songsAdapter = new SongsAdapter(Song.diffCallback, songOnClickListener);
            songsAdapter.submitList(songList);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }
        setUpViewPager();
        setUpRcvNav();
        openNowPlaying();
        seekbarChangeListener();
        seekBarCircleChangeListener();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        Log.i(TAG, "onRequestPermissionsResult: ");
        songList = songViewModel.getSongList(this);
    }

    private void seekbarChangeListener() {
        mBinding.seekbarDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    services.getMediaPlayer().seekTo(progress);
                    services.swipeSeekbar(1);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }
    private void seekBarCircleChangeListener(){
        mBinding.circleSeekbar.setOnSeekBarChangeListener(new CircularSeekBar.OnCircularSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@Nullable CircularSeekBar circularSeekBar, float v, boolean b) {
                if(b){
                    services.getMediaPlayer().seekTo(Math.round(v));
                    services.swipeSeekbar(1);
                }
            }

            @Override
            public void onStopTrackingTouch(@Nullable CircularSeekBar circularSeekBar) {

            }

            @Override
            public void onStartTrackingTouch(@Nullable CircularSeekBar circularSeekBar) {

            }
        });
    }

    private final ServiceConnection mNewConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            services = ((Services.MusicBinder) service).getService();
            services.connectToUI(MainActivity.this);
            inService = true;
            if (services.getMediaPlayer() != null) {
                transferNowPlaying();
            } else {
                Song song = songList.get(currentSong);
                Log.d(TAG, "onServiceConnected: " + currentSong);
                services.updateSong(song);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            services = null;
            handler = null;
        }
    };

    public final ClearMediaPlayerListener onClearMediaPlayer = isSuccess -> {
        if (isSuccess) {
            if (songsAdapter.getPreviousSong() != -1) {
                songList.get(songsAdapter.getPreviousSong()).setPlaying(false);
            }
            mBinding.layoutBottomControl.setVisibility(View.GONE);
            songList.get(currentSong).setPlaying(false);
            unbindService(mNewConnection);
            inService = false;
            currentSong = -1;
            handler = null;
        }
    };
    public final SongOnClickListener songOnClickListener = position -> {
        if (hasPermission()) {
            if (inService) {
                currentSong = position;
                Song song = songList.get(currentSong);
                Log.d(TAG, ": onClick" + currentSong);
                services.updateSong(song);

            } else {
                Intent intent = new Intent(this, Services.class);
                startService(intent);
                bindService(intent, mNewConnection, BIND_AUTO_CREATE);
                currentSong = position;
                Log.d(TAG, ": onClick" + currentSong);

            }
        }

    };

    public final RunMedia onRunMediaPlayer = isSuccess -> {
        if (isSuccess) {
            if (songsAdapter.getCurrentSong() != -1) {
                songList.get(songsAdapter.getCurrentSong()).setPlaying(false);
            }
            transferNowPlaying();
        }
    };

    private void updateSeekBar() {
        if (services != null & services.getMediaPlayer() != null) {
            int curPosition = services.getMediaPlayer().getCurrentPosition();
            if (mBinding != null) {
                mBinding.seekbarDuration.setProgress(curPosition);
            }
            Runnable runnable = this::updateSeekBar;
            if (handler != null) {
                handler.postDelayed(runnable, 999);
            }
        }
    }
    private void updatecircleSeekBar() {
        if (services != null & services.getMediaPlayer() != null) {
            int curPosition = services.getMediaPlayer().getCurrentPosition();
            if (mBinding != null) {
                mBinding.circleSeekbar.setProgress(curPosition);
            }
            Runnable runnable = this::updatecircleSeekBar;
            if (handler != null) {
                handler.postDelayed(runnable, 999);
            }
        }
    }


    private void transferNowPlaying() {
        mBinding.viewPager.setCurrentItem(R.id.nowPlayingFragment);
//        mBinding.bottomNav.setSelectedItemId(R.id.nowPlayingFragment);
        songList.get(currentSong).setPlaying(true);
        Song current = songList.get(currentSong);
        boolean statusCurrentSong = current.isPlaying();
        Log.d(TAG, "MediaPlayerUI: " + statusCurrentSong);
        if (statusCurrentSong == true) {
            mBinding.layoutBottomControl.setVisibility(View.GONE);
            mBinding.seekbarDuration.setVisibility(View.GONE);
            mBinding.circleSeekbar.setVisibility(View.VISIBLE);
        }
        if (services.getMediaPlayer().isPlaying()) {
            mBinding.playPause
                    .setImageResource(R.drawable.ic_baseline_pause_circle_outline_24_white);
        } else {
            mBinding.playPause
                    .setImageResource(R.drawable.ic_baseline_play_circle_outline_24_white);
        }
        mBinding.imgBottomControl.setImageBitmap(current.getImg());

        Log.d(TAG, "runMedia: " + current);

        mBinding.seekbarDuration.setMax(services.getMediaPlayer().getDuration());
        mBinding.circleSeekbar.setMax(services.getMediaPlayer().getDuration());

        mainActivityViewModel.sendData(current);
        handler = new Handler();
        updateSeekBar();
        updatecircleSeekBar();
    }

    private boolean hasPermission() {
        return (ContextCompat
                .checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void setUpRcvNav() {

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this);

        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(this, layoutManager.VERTICAL);
        mBinding.rcvMenu.addItemDecoration(dividerItemDecoration);

        mainActivityViewModel
                .getListMenuModelLiveData()
                .observe(this, new Observer<List<MenuModel>>() {
                    @Override
                    public void onChanged(List<MenuModel> menuModelList) {
                        Log.d(TAG, "onChanged: " + menuModelList);
                        mBinding.rcvMenu.setLayoutManager(layoutManager);
                        MenuAdapter menuAdapter = new MenuAdapter(menuModelList);
                        mBinding.rcvMenu.setAdapter(menuAdapter);
                    }
                });
    }

    private void MediaPlayerUI() {
        boolean statusCurrentSong = songList
                .get(currentSong)
                .isPlaying();
        Log.d(TAG, "MediaPlayerUI: " + statusCurrentSong);
        if (statusCurrentSong == true) {
            mBinding.layoutBottomControl.setVisibility(View.GONE);
        }
        songsAdapter.List(songList, currentSong);
        Song currentSong = songList.get(songsAdapter.getCurrentSong());
        Log.d(TAG, "MediaPlayerUI: " + currentSong);
        if (services.getMediaPlayer().isPlaying()) {
            mBinding.playPause
                    .setImageResource(R.drawable.ic_baseline_pause_circle_outline_24_white);
        } else {
            mBinding.playPause
                    .setImageResource(R.drawable.ic_baseline_play_circle_outline_24_white);
        }
        mBinding.imgBottomControl.setImageBitmap(currentSong.getImg());
        handler = new Handler();
    }

//    public void restoreMusic() {
//        if (Services.serviceRunning) {
//            Intent intent = new Intent(MainActivity.this, Services.class);
//            startService(intent);
//            bindService(intent, mNewConnection, BIND_AUTO_CREATE);
//        }
//    }

    private void setUpBottomNav(NavController navController) {
        NavigationUI.setupWithNavController(mBinding.bottomNav, navController);
        mBinding.bottomNav
                .setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.homeFragment:
                                mBinding.viewPager.setCurrentItem(0);
                                mBinding.circleSeekbar.setVisibility(View.GONE);
                                break;
                            case R.id.songFragment:
                                mBinding.viewPager.setCurrentItem(1);
                                mBinding.circleSeekbar.setVisibility(View.GONE);

                                break;
                            case R.id.settingFragment:
                                mBinding.viewPager.setCurrentItem(2);
                                mBinding.circleSeekbar.setVisibility(View.GONE);
                                break;
                            case R.id.nowPlayingFragment:
                                mBinding.layoutBottomControl.setVisibility(View.GONE);
                                break;
                        }
                        return true;
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (mBinding.drawerlayout.isDrawerOpen(GravityCompat.START)) {
            mBinding.drawerlayout.closeDrawers();
        } else {
            super.onBackPressed();
        }
    }

    //set Item in ViewPager2
    private void setUpViewPager() {
        mBinding.viewPager.setUserInputEnabled(false);
        mBinding.viewPager
                .registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        switch (position) {
                            case 0:
                                mBinding.bottomNav.getMenu().findItem(R.id.homeFragment).setChecked(true);
                                break;
                            case 1:
                                mBinding.bottomNav.getMenu().findItem(R.id.songFragment).setChecked(true);
                                break;
                            case 2:
                                mBinding.bottomNav.getMenu().findItem(R.id.settingFragment).setChecked(true);
                                break;
                        }
                    }
                });
    }

    private void openNowPlaying() {
        mBinding.imgBottomControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBinding.viewPager.setCurrentItem(R.id.nowPlayingFragment);
                mBinding.bottomNav.setSelectedItemId(R.id.nowPlayingFragment);

            }
        });
    }

    @Override
    public void navListener() {
        if (mBinding.drawerlayout.isDrawerOpen(GravityCompat.START)) {
            mBinding.drawerlayout.close();
        } else {
            mBinding.drawerlayout.openDrawer(GravityCompat.START);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding = null;
        handler = null;
        services.disconnect();
        songsAdapter.clear();
        songsAdapter = null;
    }
    @Override
    public void changeFragment(int id) {
        mBinding.viewPager.setCurrentItem(R.id.songFragment);
        mBinding.bottomNav.setSelectedItemId(R.id.songFragment);
        mBinding.circleSeekbar.setVisibility(View.GONE);
        mBinding.seekbarDuration.setVisibility(View.VISIBLE);
        mBinding.layoutBottomControl.setVisibility(View.VISIBLE);
    }
}