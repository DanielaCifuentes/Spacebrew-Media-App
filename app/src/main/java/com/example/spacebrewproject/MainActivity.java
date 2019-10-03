package com.example.spacebrewproject;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.daasuu.epf.EPlayerView;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.jraf.android.alibglitch.GlitchEffect;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import spacebrew.Spacebrew;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity {

    private String server; //properties for spacebrew
    private String name; //properties for spacebrew
    private String description; //properties for spacebrew

    private Random rnd; // random number generator

    private SimpleExoPlayer player;
    private EPlayerView mainPlayerView;
    private Button skipBtn;
    private SeekBar controlSkBar;
    private List<String> sourceList = Arrays.asList("http://192.168.0.7:7777/01.mp4", "file:///android_asset/video.mp4");
    private int sourceIdx = 0;
    private long id = java.time.Instant.now().getEpochSecond();
    private Activity act = this;

    /** Run this on application start*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); // Force fullscreen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // force keep-awake
        setContentView(R.layout.activity_main);

        Log.d("DEVICE_ID", id + "");

        rnd = new Random();

        server = "ws://192.168.0.7:9000";
        name = "Skip Button " + id;
        description = "Client that sends and receives boolean messages. Background turns yellow when message received.";

        SpacebrewCallbacks sketch = new SpacebrewCallbacks(this); // Spacebrew requires a processing sketch to run
        final Spacebrew sb = new Spacebrew(sketch);

        skipBtn = (Button)findViewById(R.id.skipBtn);
        controlSkBar = (SeekBar) findViewById(R.id.controlSkBar);

        player = ExoPlayerFactory.newSimpleInstance(this.getApplicationContext());
        mainPlayerView = (EPlayerView)findViewById(R.id.mainPlayerView);

        // Add sub-pub interactions to spacebrew
        sb.addPublish( "device_publisher", "string", "" );
        sb.addSubscribe( "device_subscribe", "string", "" );

        // connect to the server specified previously
        sb.connect(server, name, description);

        // Attach the ExoPlayer to the PlayerView
        mainPlayerView.setSimpleExoPlayer(player);

        final DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, getPackageName()));
        final MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(sourceList.get(sourceIdx)));

        player.prepare(videoSource);
        player.setPlayWhenReady(true);
        mainPlayerView.onResume();

        player.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == 4) {
                    // If video finishes then run this
                    sb.send( "device_publisher", id + ":video_finished");
                    goToNextVideo();
                }
                //Log.d("PLAYERSTATE", playWhenReady + "" + playbackState);

            }
        });


        // add a listener to the layout button that interacts with spacebrew.
        /** This runs every time the user clicks the button */
        skipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Send a message to the spacebrew server indication the button has been pressed
                sb.send( "device_publisher", id + ":skip_button_pressed");
                goToNextVideo();
            }
        });


        // add a listener to the layout seek bar that interacts with spacebrew.
        controlSkBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // Store raw position
            int progressChangedValue = 127;
            int epsilon = 10;
            boolean hasChanged = false;


            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > progressChangedValue + epsilon || progress < progressChangedValue - epsilon) {
                    progressChangedValue = progress;
                    hasChanged = true;
                    Log.d("SEEKBARSTATE", progressChangedValue + "");
                    sb.send("device_publisher", id + ":seekbar_changed:progress=" + progressChangedValue);
                }

                // Value hasn't changed. Reset to idle state
                if (progress == progressChangedValue) {
                    hasChanged = false;
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

    /** This function runs when a string message is received from spacebrew. */
    public void onStringMessage( String name, String value ){

        String[] command = value.split(":");

        int rcvdId = Integer.parseInt(command[0]);

        if (rcvdId != id) {
            // Ignore all messages that do not concern you
            return;
        }

        if (command[1].equals("unknown_command")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    GlitchEffect.showGlitch(act);
                }
            });
        }



        //runOnUiThread(new Runnable() {
        //    @Override
        //    public void run() {
        //        if (rnd.nextInt(2) == 1) {
        //            mainPlayerView.setGlFilter(new GlSwirlFilter());
        //        }
        //        else {
        //            mainPlayerView.setGlFilter(new GlVignetteFilter());
        //        }
        //        GlitchEffect.showGlitch(act);
        //    }
        //});


        //runOnUiThread(new Runnable() {
        //    @Override
        //    public void run() {
        //        mainPlayerView.setBackgroundColor(color);
        //    }
        //});
    }

    private void goToNextVideo() {
        sourceIdx = (sourceIdx + 1) % sourceList.size();
        final DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, getPackageName()));
        Log.d("SOURCEIDX", sourceIdx + "");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MediaSource nextVideoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(sourceList.get(sourceIdx)));
                player.prepare(nextVideoSource);
            }
        });
    }
}
