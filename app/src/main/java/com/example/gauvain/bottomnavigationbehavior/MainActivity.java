package com.example.gauvain.bottomnavigationbehavior;

import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView mBottomNav;
    BottomNavigationBehavior bottomNavBehavior;
    FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBottomNav = (BottomNavigationView) findViewById(R.id.bottom_navigation);

        fab = (FloatingActionButton) findViewById(R.id.fab);

        bottomNavBehavior = BottomNavigationBehavior.from(mBottomNav);
        bottomNavBehavior.setHideable(true);
        //Callback if you want trigger on bottomNavBehavior
        bottomNavBehavior.setBottomNavigationCallback(new BottomNavigationBehavior.BottomNavigationCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomNav, @BottomNavigationBehavior.State int newState) {
                switch (newState) {
                    case BottomNavigationBehavior.STATE_HIDDEN:
                        break;
                    case BottomNavigationBehavior.STATE_EXPANDED:
                        break;
                    case BottomNavigationBehavior.STATE_SETTLING:
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomNav, float slideOffset) {
            }
        });



        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
}
