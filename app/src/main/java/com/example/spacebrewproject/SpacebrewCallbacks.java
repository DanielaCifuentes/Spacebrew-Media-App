package com.example.spacebrewproject;

import android.util.Log;
import processing.core.PApplet;

/** This is the processing sketch used by spacebrew.
 * Spacebrew uses the predefined callbacks inside the sketch
 * to execute code when messages have been received from the server.
 * To maintain certain order on the code, this callback functions are
 * defined inside the main activity app, and accessed here through the
 * activity argument.*/
public class SpacebrewCallbacks extends PApplet {
    MainActivity act; // This is the main activity


    /** Sketch constructor has been declared to allow passing an activity as an argument */
    public SpacebrewCallbacks(MainActivity activity) {
        // Store the activity argument inside the sketch
        act = activity;
    }


    /** Define spacebrew's callback inside the sketch. This is the callback that spacebrew uses */
    public void onStringMessage( String name, String value ){
        Log.d("MESSAGE", "Received a boolean message");

        // Access the main activity callback. This way, spacebrew can interact with the main activity through processing
        act.onStringMessage(name, value);
    }

}
