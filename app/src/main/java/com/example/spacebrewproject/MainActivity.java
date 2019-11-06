package com.example.spacebrewproject;

import android.app.Activity;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.daasuu.epf.EPlayerView;
import com.daasuu.epf.filter.GlBrightnessFilter;
import com.daasuu.epf.filter.GlCGAColorspaceFilter;
import com.daasuu.epf.filter.GlContrastFilter;
import com.daasuu.epf.filter.GlExposureFilter;
import com.daasuu.epf.filter.GlFilter;
import com.daasuu.epf.filter.GlHalftoneFilter;
import com.daasuu.epf.filter.GlRGBFilter;
import com.daasuu.epf.filter.GlSharpenFilter;
import com.daasuu.epf.filter.GlSolarizeFilter;
import com.daasuu.epf.filter.GlZoomBlurFilter;
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

    private float brightness_val = 0f;
    private final GlBrightnessFilter brightness = new GlBrightnessFilter();

    private float exposure_val = 1f;
    private final GlExposureFilter exposure = new GlExposureFilter();

    private float blur_val = 1f;
    private final GlZoomBlurFilter blur = new GlZoomBlurFilter();
    //private PointF blurCenter = new PointF(0.5f, 0.5f);

    private float rgb_val = 1f;
    private final GlRGBFilter RGB = new GlRGBFilter();

    private float halftone_val = 0.00000000001F;
    private final GlHalftoneFilter halftone = new GlHalftoneFilter();
    //private float aspectRatio = 1f;

    private float solarize_val = 0.5F;
    private final GlSolarizeFilter solarize = new GlSolarizeFilter();


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

        //applyFilter(solarize);

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
            int epsilon = 1;
            boolean hasChanged = false;
            int idleInst = 1000;


            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress > progressChangedValue + epsilon || progress < progressChangedValue - epsilon) {
                    progressChangedValue = progress;
                    hasChanged = true;
                    //float solarize_value = progressChangedValue / 255f ;
                    //changeSolarize(solarize_value, 500);
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
                float contrast_time = Float.parseFloat(args.get("time"));
                applyFilter(contrast);
                changeContrast(contrast_value, contrast_time);
                break;

            case "apply_brightness":
                float brightness_value = Float.parseFloat(args.get("value"));
                float brightness_time = Float.parseFloat(args.get("time"));
                applyFilter(brightness);
                changeBrightness(brightness_value, brightness_time);
                break;

            case "apply_exposure":
                float exposure_value = Float.parseFloat(args.get("value"));
                float exposure_time = Float.parseFloat(args.get("time"));
                applyFilter(exposure);
                changeExposure(exposure_value, exposure_time);
                break;

            case "apply_blur": /*TODO RECUERDA LO DEL CENTRO*/
                float blur_value = Float.parseFloat(args.get("value"));
                float blur_time = Float.parseFloat(args.get("time"));
                applyFilter(blur);
                changeBlur(blur_value, blur_time);
                break;

            case "apply_rgb":
                float rgb_value = Float.parseFloat(args.get("value"));
                float rgb_time = Float.parseFloat(args.get("time"));
                applyFilter(RGB);
                changeRgb(rgb_value, rgb_time);
                break;

            case "apply_halftone": /*TODO RECUERDA LO DEL DIAMETRO*/
                float halftone_value = Float.parseFloat(args.get("value"));
                float halftone_time = Float.parseFloat(args.get("time"));
                applyFilter(halftone);
                changeHalftone(halftone_value, halftone_time);
                break;

            case "apply_solarize":
                float solarize_value = Float.parseFloat(args.get("value"));
                float solarize_time = Float.parseFloat(args.get("time"));
                applyFilter(solarize);
                changeSolarize(solarize_value, solarize_time);
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
    void changeBrightness(final double final_brightness, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = final_brightness - brightness_val;
                double amount_step = change_amount / time_ms;
                float brightness_value = brightness_val;
                for (int i = 0; i < time_ms; i++) {
                    brightness_value += amount_step;
                    Log.d("BRIGHTNESS_VAL", brightness_value + "");
                    brightness.setBrightness(brightness_value);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                brightness_val = (float) final_brightness;
                brightness.setBrightness(brightness_val);
            }
        };
        thread.start();
    }

    void changeExposure(final double final_exposure, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = final_exposure - exposure_val;
                double amount_step = change_amount / time_ms;
                float exposure_value = exposure_val;
                for (int i = 0; i < time_ms; i++) {
                    exposure_value += amount_step;
                    Log.d("EXPOSURE_VAL", exposure_value + "");
                    exposure.setExposure(exposure_value);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                exposure_val = (float) final_exposure;
                exposure.setExposure(exposure_val);
            }
        };
        thread.start();
    }

    void changeBlur(final double final_blur, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = final_blur - blur_val;
                double amount_step = change_amount / time_ms;
                float blur_value = blur_val;
                for (int i = 0; i < time_ms; i++) {
                    blur_value += amount_step;
                    Log.d("BLUR_VAL", blur_value + "");
                    blur.setBlurSize(blur_value);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                blur_val = (float) final_blur;
                blur.setBlurSize(blur_val);
            }
        };
        thread.start();
    }

    void changeRgb(final double rgb_range, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = rgb_range - rgb_val;
                float red;
                float green;
                float blue;

                double amount_step = change_amount / time_ms;
                float rgb_value = rgb_val;
                for (int i = 0; i < time_ms; i++) {
                    rgb_value += amount_step;
                    Log.d("RGB_VAL", rgb_value + "");

                    red = (Math.abs(rgb_value - 1.5f) - 0.5f);
                    red = Math.max(Math.min(red,1) ,0);
                    green = (-Math.abs(rgb_value - 1f) + 1f);
                    green = Math.max(Math.min(green,1) ,0);
                    blue = (-Math.abs(rgb_value - 2f) + 1f);
                    blue = Math.max(Math.min(blue,1) ,0);

                    RGB.setRed(red);
                    RGB.setGreen(green);
                    RGB.setBlue(blue);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                rgb_val = (float) rgb_range;
            }
        };
        thread.start();
    }
    void changeHalftone(final double final_halftone, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = final_halftone - halftone_val;
                double amount_step = change_amount / time_ms;
                float halftone_value = halftone_val;
                for (int i = 0; i < time_ms; i++) {
                    halftone_value += amount_step;
                    Log.d("HALFTONE_VAL", halftone_value + "");
                    halftone.setFractionalWidthOfAPixel(halftone_value);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                halftone_val = (float) final_halftone;
                halftone.setFractionalWidthOfAPixel(halftone_val);
            }
        };
        thread.start();
    }

    void changeSolarize(final double final_solarize, final double time_ms) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                double change_amount = final_solarize - solarize_val;
                double amount_step = change_amount / time_ms;
                float solarize_value = solarize_val;
                for (int i = 0; i < time_ms; i++) {
                    solarize_value += amount_step;
                    Log.d("SOLARIZE_VAL", solarize_value + "");
                    solarize.setThreshold(solarize_value);
                    try {
                        sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                solarize_val = (float) final_solarize;
                solarize.setThreshold(solarize_val);
            }
        };
        thread.start();
    }
}

