package com.bruszow.laser;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * Prompts user for information required to connect to DetectorActivity device
 */
public class ClientConnect extends AppCompatActivity {
    // Used to store last input IP and port
    protected SharedPreferences sharedPreferences;
    protected SharedPreferences.Editor editor;

    /**
     * Initializes View and creates connections
     * @param savedInstanceState Prior data if being re-initialized
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up view
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_connect);

        // Set view values to prior input
        sharedPreferences = getPreferences(MODE_PRIVATE);
        String targetIP = sharedPreferences.getString("targetIP", null);
        if (targetIP != null) {
            EditText ipEditText = findViewById(R.id.connectionServerIPEditText);
            ipEditText.setText(targetIP);
        }
        String targetPort = sharedPreferences.getString("targetPort", null);
        if (targetPort != null) {
            EditText portEditText = findViewById(R.id.connectionServerPortEditText);
            portEditText.setText(targetPort);
        }
        String piIP = sharedPreferences.getString("piIP", null);
        if (piIP != null) {
            EditText piIPEditText = findViewById(R.id.connectionPIIPEditText);
            piIPEditText.setText(piIP);
        }

        // Check for microphone permissions; needed for ReporterActivity
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityResultLauncher<String> requestPermissionLauncher =
                    registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                        if (!isGranted) {
                            // Permission not granted; return to main activity
                            Intent switchActivityIntent = new Intent(this, MainActivity.class);
                            startActivity(switchActivityIntent);
                        }
                    });
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    /**
     * Gets values from textboxes and switches to ReporterActivity
     * @param inputView View that was clicked
     */
    public void connectToServer(View inputView) {
        // Prepare intent
        Intent switchActivityIntent = new Intent(this, ReporterActivity.class);
        EditText ipEditText = findViewById(R.id.connectionServerIPEditText);
        switchActivityIntent.putExtra("targetIP", ipEditText.getText().toString());
        EditText portEditText = findViewById(R.id.connectionServerPortEditText);
        switchActivityIntent.putExtra("targetPort", portEditText.getText().toString());
        EditText piIPEditText = findViewById(R.id.connectionPIIPEditText);
        switchActivityIntent.putExtra("piIP", piIPEditText.getText().toString());

        // Update saved values
        editor = sharedPreferences.edit();
        editor.putString("targetIP", ipEditText.getText().toString());
        editor.putString("targetPort", portEditText.getText().toString());
        editor.putString("piIP", piIPEditText.getText().toString());
        editor.commit();

        startActivity(switchActivityIntent);
    }
}