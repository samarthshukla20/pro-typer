package com.samarthshukla.protyper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        MaterialButton btnSingleWordMode = view.findViewById(R.id.btnSingleWordMode);
        MaterialButton btnParagraphMode = view.findViewById(R.id.btnParagraphMode);
        MaterialButton btnMultiplayerMode = view.findViewById(R.id.btnMultiplayerMode);

        // Talk to our Master Shell (MainActivity) to use its animations and ad systems
        MainActivity mainActivity = (MainActivity) requireActivity();

        mainActivity.applySquishAnimation(btnSingleWordMode);
        mainActivity.applySquishAnimation(btnParagraphMode);
        mainActivity.applySquishAnimation(btnMultiplayerMode);

        btnSingleWordMode.setOnClickListener(v -> mainActivity.showAdThenStart(DifficultyActivity.class));
        btnParagraphMode.setOnClickListener(v -> mainActivity.showAdThenStart(ParagraphActivity.class));
        btnMultiplayerMode.setOnClickListener(v -> mainActivity.showAdThenStart(MultiplayerLobbyActivity.class));

        return view;
    }
}