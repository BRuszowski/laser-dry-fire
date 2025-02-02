package com.bruszow.laser;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * Initial page
 * Choose between Camera and Reporter modes
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Initializes View
     * @param savedInstanceState Prior data if being re-initialized
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     * Initializes and loads the DetectorActivity
     * @param inputView View that was clicked
     */
    public void switchToCameraMode(View inputView) {
        Intent switchActivityIntent = new Intent(this, DetectorActivity.class);
        startActivity(switchActivityIntent);
    }

    /**
     * Initializes and loads the ReporterActivity
     * @param inputView View that was clicked
     */
    public void switchToReporterMode(View inputView) {
        Intent switchActivityIntent = new Intent(this, ClientConnect.class);
        startActivity(switchActivityIntent);
    }
}