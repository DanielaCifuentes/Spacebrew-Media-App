package com.example.spacebrewproject;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.daasuu.epf.EPlayerView;
import com.daasuu.epf.filter.GlContrastFilter;
import com.daasuu.epf.filter.GlFilter;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.jraf.android.alibglitch.GlitchEffect;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import spacebrew.Spacebrew;

public class MainActivity extends AppCompatActivity {

    private String ip = "192.168.43.251";

    private String server; //properties for spacebrew
    private String name; //properties for spacebrew
    private String description; //properties for spacebrew

    private Random rnd; // random number generator

    private SimpleExoPlayer player;
    private EPlayerView mainPlayerView;
    private Button skipBtn;
    private SeekBar controlSkBar;
    private List<String> sourceList = Arrays.asList(
            "file:///android_asset/1.mp4",
            "file:///android_asset/2.mp4",
            "file:///android_asset/3.mp4",
            "file:///android_asset/4.mp4",
            "file:///android_asset/5.mp4");

    private int sourceIdx = 0;
    private long id = System.currentTimeMillis();
    private Activity act = this;

    private SpacebrewCallbacks sketch;
    private Spacebrew sb;

    private float contrast_val = 0.5f;
    private final GlContrastFilter contrast = new GlContrastFilter();


    /** Run this on application start*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); // Force fullscreen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // force keep-awake
        setContentView(R.layout.activity_main);

        Log.d("DEVICE_ID", id + "");

        rnd = new Random();

        server = "ws://" + ip + ":9000";
        name = "Skip Button " + id;
        description = "Client that sends and receives boolean messages. Background turns yellow when message received.";

        sketch = new SpacebrewCallbacks(this); // Spacebrew requires a processing sketch to run
        sb = new Spacebrew(sketch);

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

        applyFilter(contrast);

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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GlitchEffect.showGlitch(act);
                    }
                });


            }
        });


        // add a listener to the layout seek bar that interacts with spacebrew.
        controlSkBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // Store raw position
            int progressChangedValue = 127;
            int epsilon = 10;
            boolean hasChanged = false;
            int idleInst = 1000;


            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > progressChangedValue + epsilon || progress < progressChangedValue - epsilon) {
                    progressChangedValue = progress;
                    hasChanged = true;
                    //float brightness_value = progressChangedValue/255f*10 - 5f;
                   // changeBrightness(brightness_value, 150);
                    Log.d("SEEKBARSTATE", progressChangedValue + "");
                    sb.send("device_publisher", id + ":seekbar_changed:progress=" + progressChangedValue);

                }

                // Value hasn't changed. Reset to idle state
                if (hasChanged && progress == progressChangedValue) {
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
        Log.d("RCVD_COMMAND", command[1]);



        long rcvdId = Long.valueOf(command[0]);


        if (rcvdId != id) {
            // Ignore all messages that do not concern you
            Log.d("WRONG_ID", command[1]);
            return;
        }

        Map<String, String> args = new HashMap<String, String>();

        if (command.length == 3) {

            String[] argument_lists = command[2].split(",");
            for (int i = 0; i < argument_lists.length; i++) {
                String raw_arg = argument_lists[i];
                if (raw_arg.contains("=")) {
                    String[] arg = raw_arg.split("=");
                    args.put(arg[0], arg[1]);
                }
            }
        }

        Log.d("RCVD_ARGS", String.valueOf(args));

        switch(command[1]) {
            case "unknown_command":
                // The backend couldn't understand the command. Respond visually
                addGlitch();
                break;

            case "ping":
                // A ping was received, send a ping back
                Log.d("PING", rcvdId + "");
                sb.send("device_publisher", id + ":ping");
                break;

            case "change_video_speed":
                float speed = Float.parseFloat(args.get("speed"));
                changeVideoSpeed(speed);
                break;

            case "apply_contrast":
                float contrast_value = Float.parseFloat(args.get("value"));
                float apply_time = Float.parseFloat(args.get("time"));
                applyFilter(contrast);
                changeContrast(contrast_value, apply_time);
                break;

            default:
                // The command received was not recognized. Log this situation
                Log.d("UNRECOGNIZED_CMD", command[1] + "");
        }

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

        addGlitch();
    }

    private void changeVideoSpeed(float speed) {
        PlaybackParameters param = new PlaybackParameters(speed);
        player.setPlaybackParameters(param);
    }

    private void addGlitch() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GlitchEffect.showGlitch(act);
            }
        });
    }

    void applyFilter(GlFilter filter) {
        mainPlayerView.setGlFilter(filter);
    }

    void changeContrast(final double final_contrast, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = final_contrast - contrast_val;
                double amount_step = change_amount / time_ms;
                float contrast_value = contrast_val;
                for (int i = 0; i < time_ms; i++) {
                    contrast_value += amount_step;
                    Log.d("CONTRAST_VAL", contrast_value + "");
                    contrast.setContrast(contrast_value);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                contrast_val = (float) final_contrast;
                contrast.setContrast(contrast_val);
            }
        };
        thread.start();
    }
}
