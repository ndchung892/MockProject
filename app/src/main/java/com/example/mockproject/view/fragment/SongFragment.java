package com.example.mockproject.view.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.mockproject.OpenNavListener;
import com.example.mockproject.R;
import com.example.mockproject.databinding.FragmentSongBinding;


public class SongFragment extends Fragment {
    private FragmentSongBinding mBinding;
    private OpenNavListener openNavListener;

    public SongFragment() {
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentSongBinding.inflate(inflater, container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUpToolBar();
    }

    private void setUpToolBar() {
            openNavListener = (OpenNavListener) requireActivity();
            mBinding.toolbarSong.setNavigationOnClickListener(v -> {
                openNavListener.navListener();
            });

    }
}