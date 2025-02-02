package com.bruszow.laser;
import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import com.chaquo.python.PyObject;
import java.util.Arrays;

/**
 * Checks audio for trigger sound
 */
public class AudioProcessor {
    protected static int audioFreqSample = 22050; // sampling rate
    protected AudioRecord recorder; // class to get audio input
    protected static int batchSize = 10; // size of samples to process
    protected static int sampleWindow = 256; // number of samples to take
    protected static FFT fft; // class for fast fourier transform
    protected static int listenBinStart; // lowest frequency range processed
    protected static int listenBinEnd; // highest frequency range processed
    protected static double listenBinTarget = 7500000000.0; // target average frequency
    protected static double rmsTarget = 1500; // target rms (loudness)
    protected PyObject soundTriggerObj; // Used to control Pi's GPIO pins


    /**
     * Constructor
     * @param soundTriggerObj PyObject; used to call Python code controlling Pi's GPIO pins
     */
    @SuppressLint("MissingPermission") // audio permission granted in ReporterActivity
    public AudioProcessor(PyObject soundTriggerObj) {
        this.soundTriggerObj = soundTriggerObj;
        fft = new FFT(sampleWindow * 2);
        listenBinStart = (int) (5000.0 / (audioFreqSample / (2.0 * sampleWindow)));
        listenBinEnd = (int) (7000.0 / (audioFreqSample / (2.0 * sampleWindow)));

        recorder = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(audioFreqSample).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).build();
        new Thread(new AudioRecorderThread()).start();

    }

    public void processAudio(short[] inputArr) {
        double[] freqArr = new double[sampleWindow];
        double[] xArr = new double[sampleWindow * 2];
        double[] yArr = new double[sampleWindow * 2];
        int batchOffset = 0;
        double rms = 0;
        for (int i = 0; i < batchSize; i++) {
            // process each sample in batch
            // reset arrays and update rms
            Arrays.fill(yArr, 0);
            for (int j = 0; j < sampleWindow * 2; j++) {
                rms += Math.pow(inputArr[j + batchOffset], 2);
                xArr[j] = inputArr[j + batchOffset];
            }
            // fft to get frequency chart
            fft.fft(xArr, yArr);
            for (int j = sampleWindow; j < sampleWindow * 2; j++) {
                // update frequencies of interest
                freqArr[j - sampleWindow] += Math.pow(xArr[j], 2) + Math.pow(yArr[j], 2);
            }
            batchOffset += 2 * sampleWindow;
        }
        rms /= inputArr.length;
        rms = Math.sqrt(rms);

        double avgVal = 0;
        for (int i = listenBinStart; i < listenBinEnd; i++) {
            avgVal += freqArr[i];
        }
        avgVal /= listenBinEnd - listenBinStart;

        if (rms >= rmsTarget && avgVal >= listenBinTarget) {
            // detection conditions met; fire laser
            soundTriggerObj.callAttr("fire_trigger");
        }
    }

    /**
     * Reads and processes incoming audio in batches
     */
    public class AudioRecorderThread implements Runnable {

        @Override
        public void run() {
            while (true) {
                short[] readArr = new short[sampleWindow * 2 * batchSize];
                recorder.read(readArr, 0, readArr.length, AudioRecord.READ_BLOCKING);
                new Thread(() -> {processAudio(readArr);}).start();
            }
        }
    }


    /**
     * Class for fast fourier transform
     * Code adapted from https://www.ee.columbia.edu/~ronw/code/MEAPsoft-2.0/doc/doxygen/FFT_8java-source.html
     */
    public static class FFT {

        int n, m;

        // Lookup tables. Only need to recompute when size of FFT changes.
        double[] cos;
        double[] sin;


        public FFT(int n) {
            this.n = n;
            this.m = (int) (Math.log(n) / Math.log(2));

            // Make sure n is a power of 2
            if (n != (1 << m))
                throw new RuntimeException("FFT length must be power of 2");

            // precompute tables
            cos = new double[n / 2];
            sin = new double[n / 2];

            for (int i = 0; i < n / 2; i++) {
                cos[i] = Math.cos(-2 * Math.PI * i / n);
                sin[i] = Math.sin(-2 * Math.PI * i / n);
            }

        }

        /***************************************************************
         * fft.c Douglas L. Jones University of Illinois at Urbana-Champaign January
         * 19, 1992 http://cnx.rice.edu/content/m12016/latest/
         *
         * fft: in-place radix-2 DIT DFT of a complex input
         *
         * input: n: length of FFT: must be a power of two m: n = 2**m input/output
         * x: double array of length n with real part of data y: double array of
         * length n with imag part of data
         *
         * Permission to copy and use this program is granted as long as this header
         * is included.
         ****************************************************************/
        public void fft(double[] x, double[] y) {
            int i, j, k, n1, n2, a;
            double c, s, t1, t2;

            // Bit-reverse
            j = 0;
            n2 = n / 2;
            for (i = 1; i < n - 1; i++) {
                n1 = n2;
                while (j >= n1) {
                    j = j - n1;
                    n1 = n1 / 2;
                }
                j = j + n1;

                if (i < j) {
                    t1 = x[i];
                    x[i] = x[j];
                    x[j] = t1;
                    t1 = y[i];
                    y[i] = y[j];
                    y[j] = t1;
                }
            }

            // FFT
            n1 = 0;
            n2 = 1;

            for (i = 0; i < m; i++) {
                n1 = n2;
                n2 = n2 + n2;
                a = 0;

                for (j = 0; j < n1; j++) {
                    c = cos[a];
                    s = sin[a];
                    a += 1 << (m - i - 1);

                    for (k = j; k < n; k = k + n2) {
                        t1 = c * x[k + n1] - s * y[k + n1];
                        t2 = s * x[k + n1] + c * y[k + n1];
                        x[k + n1] = x[k] - t1;
                        y[k + n1] = y[k] - t2;
                        x[k] = x[k] + t1;
                        y[k] = y[k] + t2;
                    }
                }
            }
        }
    }
}
