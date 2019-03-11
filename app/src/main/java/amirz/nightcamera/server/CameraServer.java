package amirz.nightcamera.server;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import amirz.nightcamera.device.DevicePreset;

public class CameraServer {
    public final static String TAG = "CameraServer";

    private final Context mContext;
    private final CameraManager mManager;

    //Give camera handling a thread to prevent UI stutters
    private final HandlerThread mThread = new HandlerThread(TAG);
    private final Handler mHandler;

    /**
     * Preferred stream type in descending order
     */
    private final static int[] FORMAT_PREFERENCE = {
            ImageFormat.RAW_SENSOR,
            ImageFormat.YUV_420_888,
            ImageFormat.JPEG
    };

    /**
     * Use as referenced struct
     */
    public final static class CameraStreamFormat {
        public final String id;
        public final CameraCharacteristics characteristics;
        public final int format;
        public final Size size;

        CameraStreamFormat(String id, CameraCharacteristics characteristics, int format, Size size) {
            this.id = id;
            this.characteristics = characteristics;
            this.format = format;
            this.size = size;
        }
    }

    private final List<CameraStreamFormat> mCameras = new ArrayList<>();

    /**
     * Constructor loads all metadata from the camera manager.
     * @param context Context
     * @throws CameraAccessException
     */
    public CameraServer(Context context) throws CameraAccessException {
        mContext = context;
        mManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());

        if (mManager == null)  {
            throw new CameraServerException(CameraServerException.Problem.ManagerIsNull);
        }

        for (String id : mManager.getCameraIdList()) {
            CameraCharacteristics characteristics = mManager.getCameraCharacteristics(id);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                int format = -1;

                List<Integer> outFormats = new ArrayList<>();
                for (int outFormat : map.getOutputFormats()) {
                    outFormats.add(outFormat);
                }

                for (int prefFormat : FORMAT_PREFERENCE) {
                    if (outFormats.contains(prefFormat) && format == -1) {
                        format = prefFormat;
                    }
                    Size[] sizes = map.getOutputSizes(prefFormat);
                    Log.d(TAG, "Sizes for " + prefFormat + ": " + Arrays.toString(sizes));
                }

                if (format != -1) {
                    Size[] sizes = map.getOutputSizes(format);
                    mCameras.add(new CameraStreamFormat(id, characteristics, format, sizes.length > 0 ? sizes[0] : null));
                }
            }
        }
    }

    public List<CameraStreamFormat> getStreamFormats() {
        return Collections.unmodifiableList(mCameras);
    }

    public CameraStream requestOpen(CameraStreamFormat streamFormat, CameraStreamCallbacks cb) {
        CameraStream stream = new CameraStream(streamFormat, cb);
        try {
            mManager.openCamera(streamFormat.id, stream, mHandler);
        } catch (CameraAccessException | SecurityException e) {
            stream = null;
        }
        return stream;
    }

    public void requestClose(CameraStream stream) {
        stream.closeStream();
    }

    public void requestShutdown() {

    }
}
