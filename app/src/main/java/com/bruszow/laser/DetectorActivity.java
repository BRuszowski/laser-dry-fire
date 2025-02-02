package com.bruszow.laser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Detects and reports the laser's position
 */
public class DetectorActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private CameraBridgeViewBase mOpenCvCameraView;

    private static final String TAG = "DetectorActivity";
    protected android.graphics.Bitmap backgroundBM = null; // Bitmap of target
    protected int skipFrames = 3; // Frames to skip between processing frames
    protected int skipFrameCount = 0; // Counter to track frames since last processed frame
    protected int skipAfterDetect = 10; // Frames to skip after detection
    protected int detectWaitCounter = 0; // Counter for frames to skip after detection
    protected int processingSkipPixels = 16; // Pixels to skip when processing image
    protected Thread connectionThread;
    protected SocketUtil.DetectorSocket detectorSocket; // Websocket to ReporterActivity

    protected String serverIP = ""; // DetectorActivity device's IP
    protected int serverPort = 8811; // DetectorActivity device's port
    protected String connectedServer = ""; // indicates if ReporterActivity is connected
    protected long lastHitReport = 0; // time of last detection
    protected long min_hit_wait = 400; // time to wait between detections
    protected int cameraWidth = 800; // pixels for camera image width
    protected int cameraHeight = 480; // pixels for camera image height


    /**
     * Initializes View and creates connections
     * @param savedInstanceState Prior data if being re-initialized
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up view and passed variables
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detector);

        // Get device's IP address
        try {
            serverIP = getLocalIpAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        updateConnectionInfo("");

        // Start listening for connections
//        new Thread(() -> {new DetectorActivity.InitConnectionRunnable(serverIP, serverPort, this);}).start();
        connectionThread = new Thread(new DetectorActivity.InitConnectionRunnable(serverIP, serverPort, this));
        connectionThread.start();

        // Set up camera
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            // OpenCV load failed; return to main activity
            Log.i(TAG, "OpenCV loading failed");
            (Toast.makeText(this, "OpenCV loading failed", Toast.LENGTH_LONG)).show();
            Intent switchActivityIntent = new Intent(this, MainActivity.class);
            startActivity(switchActivityIntent);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mOpenCvCameraView = findViewById(R.id.VideoFeedView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize(cameraWidth, cameraHeight);

    }

    /**
     * Controls camera pause behavior
     * Required method from interface
     */
    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    /**
     * Cleans up after camera is closed
     * Required method from interface
     */
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

    }

    /**
     * Called when camera starts
     * Required method from interface
     */
    public void onCameraViewStarted(int width, int height) {
    }

    /**
     * Called when camera stops
     * Required method from interface
     */
    public void onCameraViewStopped() {
    }

    /**
     * Controls resume behavior
     * Required method from interface
     */
    @Override
    public void onResume()
    {
        super.onResume();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.enableView();
        }
    }

    /**
     * Required method from parent class
     * @return List of camera views
     */
    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    /**
     * Processes frames from camera
     * Required method from interface
     * @param inputFrame input frame's data
     * @return Matrix with image data
     */
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat output = inputFrame.rgba();
        if (backgroundBM == null) {
            // set image of target
            this.setBackgroundBM(inputFrame);
        }
        if (detectWaitCounter > 0) {
            detectWaitCounter -= 1;
        } else {
            if (skipFrameCount < skipFrames) {
                skipFrameCount += 1;
            } else {
                skipFrameCount = 0;
                new Thread(() -> {processImage(output);}).start();
            }
        }
        return output;
    }

    /**
     * Sets the target image
     * @param inputFrame input frame data from camera
     */
    public void setBackgroundBM(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // Create Bitmap
        Mat output = inputFrame.rgba();
        int bmWidth = output.width();
        int bmHeight = output.height();
        backgroundBM = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(output, backgroundBM);

        // Update view
        runOnUiThread(() -> {
            ImageView backgroundImageView = findViewById(R.id.backgroundImageView);
            backgroundImageView.setImageBitmap(backgroundBM);
        });
    }

    /**
     * Checks the image for a laser dot
     * If detected, sends a message to the ReporterActivity device
     * @param input Matrix with image data
     */
    public void processImage(Mat input) {
        int[] largestConnect = {0, 0, 0, 0, 0}; // largest detected area
        int[][] connectCountArr = new int[input.rows()][input.cols()]; // marks checked coordinates
        int greenThreshold = 250; // target level of green
        int minConnect = 4; // minimum size of continuous area
        boolean found = false;
        for (int row = 1; row < input.rows() - 1; row += processingSkipPixels) {
            for (int col = 1; col < input.cols() - 1; col += processingSkipPixels) {
                double[] data = input.get(row, col);
                if (data[1] > greenThreshold && connectCountArr[row][col] == 0) {
                    countConnect(row, col, new AtomicInteger(), new int[] {row, col, row, col}, input, connectCountArr, largestConnect, greenThreshold);
                    if (largestConnect[0] > minConnect) {
                        found = true;
                        break;
                    }
                }
            }
        }
        if (found) {
            detectWaitCounter += skipAfterDetect;

            // Find middle of detection area
            int midRow = largestConnect[1] + (largestConnect[3] - largestConnect[1]) / 2;
            int midCol = largestConnect[2] + (largestConnect[4] - largestConnect[2]) / 2;

            if (!connectedServer.isEmpty()) {
                // Send coordinates to ReporterActivity
                long currentTime = Clock.systemDefaultZone().millis();
                if (currentTime - lastHitReport > min_hit_wait) {
                    lastHitReport = currentTime;
                    detectorSocket.sendHit(midRow, midCol);
                }
            }
        }
    }

    /**
     * Counts connected cells that exceed the input brightness threshold
     * @param row int row to check
     * @param col int col to check
     * @param currentCount AtomicInteger with contiguous block size
     * @param minMaxRowCol int array; contains the block's lowest/highest x/y coordinates
     * @param inputMat Matrix of image data
     * @param countedArr 2d int array; stores previously counted coords
     * @param maxResult int array; stores largest value and block's extremity coordinates
     * @param matchThreshold int of minimum valid green
     */
    public void countConnect(int row, int col, AtomicInteger currentCount, int[] minMaxRowCol, Mat inputMat, int[][] countedArr, int[] maxResult, double matchThreshold) {
        if (countedArr[row][col] != 0) {
            // Already processed; return
            return;
        }
        double[] cellData = inputMat.get(row, col);
        if (cellData[1] < matchThreshold) {
            // Pixel doesn't meet criteria; mark and return
            countedArr[row][col] = 1;
            return;
        }

        // Update boundaries of detection area
        minMaxRowCol[0] = Math.min(minMaxRowCol[0], row);
        minMaxRowCol[1] = Math.min(minMaxRowCol[1], col);
        minMaxRowCol[2] = Math.max(minMaxRowCol[2], row);
        minMaxRowCol[3] = Math.max(minMaxRowCol[3], col);

        currentCount.getAndIncrement();

        if (currentCount.get() > maxResult[0]) {
            // Update current maximum
            maxResult[0] = currentCount.get();
            maxResult[1] = minMaxRowCol[0];
            maxResult[2] = minMaxRowCol[1];
            maxResult[3] = minMaxRowCol[2];
            maxResult[4] = minMaxRowCol[3];
        }

        countedArr[row][col] = 2;
        // Recursive call on neighboring pixels
        if (row > processingSkipPixels) {
            countConnect(row - processingSkipPixels, col, currentCount, minMaxRowCol, inputMat, countedArr, maxResult, matchThreshold);
        }
        if (col > processingSkipPixels) {
            countConnect(row, col - processingSkipPixels, currentCount, minMaxRowCol, inputMat, countedArr, maxResult, matchThreshold);
        }
        if (row < inputMat.rows() - 1 - processingSkipPixels) {
            countConnect(row + processingSkipPixels, col, currentCount, minMaxRowCol, inputMat, countedArr, maxResult, matchThreshold);
        }
        if (col < inputMat.cols() - 1 - processingSkipPixels) {
            countConnect(row, col + processingSkipPixels, currentCount, minMaxRowCol, inputMat, countedArr, maxResult, matchThreshold);
        }
    }

    /**
     * Gets the device's IP address
     * @return String with device's address
     * @throws UnknownHostException Exception that device's IP address couldn't be determined
     */
    private String getLocalIpAddress() throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
    }

    /**
     * Updates the displayed connection info
     * @param connectedDevice boolean indicating if a ReporterActivity is connected
     */
    protected void updateConnectionInfo(String connectedDevice) {
        connectedServer = connectedDevice;
        runOnUiThread(() -> {
            TextView serverTextView = findViewById(R.id.serverStatusView);
            if (connectedDevice.isEmpty()) {
                serverTextView.setText(getString(R.string.server_info, getString(R.string.listening), serverIP, Integer.toString(serverPort)));
            } else {
                serverTextView.setText(getString(R.string.server_info_short, getString(R.string.connected), connectedDevice));
            }
        });
    }

    /**
     * Resets the image of the target
     * @param view View that was clicked
     */
    public void setBackgroundImageButton(View view) {
        backgroundBM = null;
    }


    /**
     * Initializes connections outside of main thread
     */
    class InitConnectionRunnable implements Runnable {
        String serverIP;
        int serverPort;
        DetectorActivity parentActivity;

        /**
         * Constructor
         * @param serverIP String of DetectorActivity device's IP address
         * @param serverPort int of DetectorActivity device's port
         * @param parentActivity DetectorActivity instance; passed so threads can call methods to
         *                       update UI
         */
        public InitConnectionRunnable(String serverIP, int serverPort, DetectorActivity parentActivity) {
            this.serverIP = serverIP;
            this.serverPort = serverPort;
            this.parentActivity = parentActivity;
        }

        /**
         * Connects websocket
         */
        @Override
        public void run() {
            detectorSocket = new SocketUtil.DetectorSocket(this.serverIP, this.serverPort, this.parentActivity);
            Thread cameraThread = new Thread(detectorSocket);
            cameraThread.start();
        }
    }
}