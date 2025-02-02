package com.bruszow.laser;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/**
 * Shows detection results
 * Includes utilities to control the camera, detect the trigger sound, and send a fire command
 */
public class ReporterActivity extends AppCompatActivity {
    protected String serverIP = ""; // IP address of DetectorActivity device
    protected int serverPort = 8811; // Port of DetectorActivity device

    protected SocketUtil.ReporterSocket reporterSocket; // Websocket to DetectorActivity

    protected Bitmap backgroundBM; // Bitmap of target
    protected Bitmap originalBackgroundBM; // Bitmap of target without overlaid markers
    protected static int[] currentColorArr = {Color.RED, Color.rgb(255, 127, 0),
            Color.YELLOW, Color.GREEN, Color.BLUE, Color.rgb(75, 0, 211),
            Color.rgb(148, 0, 211)}; // Colors used for markers
    protected int currentColorIndex = 0; // Color to use for next marker
    protected String piIP = ""; // IP address of Pi
    protected PyObject soundTriggerObj; // Used to control Pi's GPIO pins
    protected AudioProcessor audioProcessor; // Used to detect trigger's sound
    protected int imageScale = 2; // Scales Bitmap of target
    protected int markerSize = 20; // Sets size of maker in pixels
    protected Thread connectionThread;


    /**
     * Initializes View and creates connections
     * @param savedInstanceState Prior data if being re-initialized
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up view and passed variables
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reporter);
        serverIP = getIntent().getStringExtra("targetIP");
        serverPort = Integer.parseInt(getIntent().getStringExtra("targetPort"));
        piIP = getIntent().getStringExtra("piIP");

        // Connect to DetectorActivity device
        connectionThread = new Thread(new InitConnectionRunnable(serverIP, serverPort, this));
        connectionThread.start();

        // Connect to Pi
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(getApplicationContext()));
        }
        Python python = Python.getInstance();
        soundTriggerObj = python.getModule("fire_trigger");
        new Thread(() -> {soundTriggerObj.callAttr("set_pin_out", piIP);}).start();

        // Set up audio processing
        audioProcessor = new AudioProcessor(soundTriggerObj);
    }

    /**
     * Sends a message to the DetectorActivity device requesting a background update
     * @param view View that was clicked
     */
    public void updateBackgroundButton(View view) {
        new Thread(() -> {reporterSocket.sendMessage("updateBackground" + ((char) 0));}).start();
    }

    /**
     * Clears all markings on the current image
     * @param view View that was clicked
     */
    public void clearShotsButton(View view) {
        backgroundBM = Bitmap.createBitmap(originalBackgroundBM);
        runOnUiThread(() -> {
            ImageView reporterImageView = findViewById(R.id.reporterBackgroundImageView);
            reporterImageView.setImageBitmap(backgroundBM);
        });
    }

    /**
     * Updates the displayed connection status
     * @param inputIP String of the DetectorActivity device's IP address
     */
    protected void updateConnectionStatus(String inputIP) {
        runOnUiThread(() -> {
            TextView serverTextView = findViewById(R.id.connectionText);
            serverTextView.setText(getString(R.string.server_info_short, getString(R.string.connected), inputIP));
        });
    }

    /**
     * Updates the displayed background image
     * @param byteArray byte array containing the BitMap data
     */
    protected void updateBackground(byte[] byteArray) {
        // Convert byte array to Bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        backgroundBM = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, options);

        // Scale Bitmap
        backgroundBM = Bitmap.createScaledBitmap(backgroundBM, backgroundBM.getWidth() * imageScale, backgroundBM.getHeight() * imageScale, true);

        // Set view
        runOnUiThread(() -> {
            ImageView reporterImageView = findViewById(R.id.reporterBackgroundImageView);
            reporterImageView.setImageBitmap(backgroundBM);
        });

        // Store Bitmap without markers
        originalBackgroundBM = Bitmap.createBitmap(backgroundBM);
    }

    /**
     * Marks the location of a hit
     * @param row int y-coordinate
     * @param col int x-coordinate
     */
    protected void updateTarget(int row, int col) {
        if (backgroundBM == null) {
            // No image to mark; return
            return;
        }

        int colOffset = 0; // Used to make a circle
        int scaledRow = row * imageScale;
        int scaledCol = col * imageScale;
        int maxWidth = backgroundBM.getWidth() * 2;
        int maxHeight = backgroundBM.getHeight() * 2;
        // Set color in region around input coordinates
        for (int i = scaledRow - markerSize; i <= scaledRow + markerSize; i++) {
            if (i >= 0 && i < maxHeight) {
                for (int j = scaledCol - colOffset; j < scaledCol; j++) {
                    if (j >= 0 && j < maxWidth) {
                        backgroundBM.setPixel(j, i, currentColorArr[currentColorIndex]);
                    }
                }
                for (int j = scaledCol; j <= scaledCol + colOffset; j++) {
                    if (j >= 0 && j < maxWidth) {
                        backgroundBM.setPixel(j, i, currentColorArr[currentColorIndex]);
                    }
                }
            }
            if (i > scaledRow) {
                colOffset -= 1;
            } else {
                colOffset += 1;
            }
        }
        // Set new color for next method call
        currentColorIndex += 1;
        currentColorIndex %= currentColorArr.length;

        // Update view
        runOnUiThread(() -> {
            ImageView reporterImageView = findViewById(R.id.reporterBackgroundImageView);
            reporterImageView.setImageBitmap(backgroundBM);
        });
    }

    /**
     * Initializes connections outside of main thread
     */
    class InitConnectionRunnable implements Runnable {
        String serverIP;
        int serverPort;
        ReporterActivity parentActivity;

        /**
         * Constructor
         * @param serverIP String of DetectorActivity device's IP address
         * @param serverPort int of DetectorActivity device's port
         * @param parentActivity ReporterActivity instance; passed so threads can call methods to
         *                       update UI
         */
        public InitConnectionRunnable(String serverIP, int serverPort, ReporterActivity parentActivity) {
            this.serverIP = serverIP;
            this.serverPort = serverPort;
            this.parentActivity = parentActivity;
        }

        /**
         * Connects websocket
         */
        @Override
        public void run() {
            reporterSocket = new SocketUtil.ReporterSocket(this.serverIP, this.serverPort, this.parentActivity);
            Thread reporterThread = new Thread(reporterSocket);
            reporterThread.start();
        }
    }
}