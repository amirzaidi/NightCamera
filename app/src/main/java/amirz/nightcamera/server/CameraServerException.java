package amirz.nightcamera.server;

public class CameraServerException extends RuntimeException {
    public enum Problem {
        ManagerIsNull,
        CaptureFailed,
        ConfigureFailed,
        CameraDeviceNull
    }

    public CameraServerException(Problem problem) {
        super("CameraServerException: " + problem.toString());
    }
}
