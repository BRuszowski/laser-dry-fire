package com.bruszow.laser;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Websocket classes for ReporterActivity and DetectorActivity
 */
public abstract class SocketUtil implements Runnable {
    protected Socket webSocket;
    protected OutputStream output;
    protected InputStream input;
    protected ArrayList<Integer> readBytes; // Stores incoming bytes until header end signal null aka ((char) 0) is detected
    protected int expectedBytes; // Size of expected byte array
    protected byte[] byteArr; // Incoming byte array
    protected int byteIndex; // Current index in byte array

    /**
     * Sends a message to the connected socket
     * @param message String message
     */
    public void sendMessage(String message) {
        try {
            this.output.write(message.getBytes());
            this.output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public abstract void run();

    /**
     * WebSocket class for ReporterActivity
     */
    public static class ReporterSocket extends SocketUtil {
        ReporterActivity parentActivity;

        /**
         * Connects WebSocket
         * @param serverIP String of DetectorActivity device's IP address
         * @param serverPort int of DetectorActivity device's port
         * @param parentActivity ReporterActivity; used to update UI
         */
        public ReporterSocket(String serverIP, int serverPort, ReporterActivity parentActivity) {
            try {
                this.webSocket = new Socket(serverIP, serverPort);
                this.output = webSocket.getOutputStream();
                this.input = webSocket.getInputStream();
                this.readBytes = new ArrayList<>();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.parentActivity = parentActivity;
            this.parentActivity.updateConnectionStatus(serverIP);
        }

        /**
         * Handles incoming messages
         */
        @Override
        public void run() {
            try {
                while (!this.webSocket.isClosed()) {
                    int readData = this.input.read();
                    if (readData > -1) {
                        // New data exists
                        if (this.byteArr != null) {
                            // Expecting incoming byte array
                            this.byteArr[this.byteIndex] = (byte) readData;
                            this.byteIndex += 1;
                            if (this.byteIndex >= this.byteArr.length) {
                                // Full target image received
                                parentActivity.updateBackground(this.byteArr);
                                this.byteArr = null;
                            }
                        } else {
                            if (readData == 0) {
                                // Full header received
                                // Convert to String
                                StringBuilder header = new StringBuilder();
                                for (Integer sentByte : this.readBytes) {
                                    header.append((char)(int) sentByte);
                                }
                                this.readBytes.clear();

                                // Process header
                                String[] splitHeader = header.toString().split("\\|");
                                if (splitHeader[0].equals("expect")) {
                                    // Incoming byte array for target image
                                    this.expectedBytes = Integer.parseInt(splitHeader[1]);
                                    this.byteArr = new byte[this.expectedBytes];
                                    this.byteIndex = 0;
                                } else if (splitHeader[0].equals("hit")) {
                                    // Hit detected
                                    parentActivity.updateTarget(Integer.parseInt(splitHeader[1]), Integer.parseInt(splitHeader[2]));
                                }
                            } else {
                                // Header still transmitting
                                this.readBytes.add(readData);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * WebSocket class for DetectorActivity
     */
    public static class DetectorSocket extends SocketUtil {
        DetectorActivity parentActivity;
        ServerSocket serverSocket;
        String serverIP;
        int serverPort;
        boolean blockTransmission;

        /**
         * Saves input variables
         * @param serverIP String of DetectorActivity device's IP address
         * @param serverPort int of DetectorActivity device's port
         * @param parentActivity DetectorActivity; used for UI updates
         */
        public DetectorSocket(String serverIP, int serverPort, DetectorActivity parentActivity) {
            this.readBytes = new ArrayList<>();
            this.serverIP = serverIP;
            this.serverPort = serverPort;
            this.parentActivity = parentActivity;
            this.blockTransmission = false;
        }

        /**
         * Sends detected coordinates to ReporterActivity device
         * @param midRow int with y-coordinate
         * @param midCol int with x-coordinate
         */
        public void sendHit(int midRow, int midCol) {
            if (blockTransmission) {
                return;
            }
            String reportString = "hit|" + midRow + "|" + midCol + "\n";
            this.sendMessage(reportString);
        }

        /**
         * Handles incoming messages
         */
        @Override
        public void run() {
            try {
                // Start listening for websocket connections
                serverSocket = new ServerSocket(this.serverPort);

                // Blocks until connection request received
                webSocket = serverSocket.accept();
                this.output = webSocket.getOutputStream();
                this.input = webSocket.getInputStream();
                parentActivity.updateConnectionInfo(webSocket.getRemoteSocketAddress().toString());

                while (!this.webSocket.isClosed()) {
                    int readData = this.input.read();
                    if (readData > -1) {
                        // New data exists
                        if (readData == 0) {
                            // Full header received
                            // Convert to String
                            StringBuilder header = new StringBuilder();
                            for (Integer sentByte : this.readBytes) {
                                header.append((char)(int) sentByte);
                            }
                            this.readBytes.clear();

                            // Process header
                            String[] splitHeader = header.toString().split("\\|");
                            if (splitHeader[0].equals("updateBackground")) {
                                this.blockTransmission = true;

                                // Get new image of target
                                parentActivity.setBackgroundImageButton(null);
                                Thread.sleep(200); // wait until new image is set

                                // Send image of target
                                Bitmap backgroundBM = parentActivity.backgroundBM;
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                backgroundBM.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                byte[] byteArray = stream.toByteArray();
                                this.output.flush();
                                this.output.write(("expect|" + byteArray.length + ((char) 0)).getBytes());
                                this.output.flush();
                                this.output.write(byteArray);
                                this.output.flush();

                                this.blockTransmission = false;
                            }
                        } else {
                            this.readBytes.add(readData);
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
