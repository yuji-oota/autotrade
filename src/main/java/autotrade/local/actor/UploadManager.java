package autotrade.local.actor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import autotrade.local.exception.ApplicationException;
import autotrade.local.utility.AwsS3ClientWrapper;

public class UploadManager {

    private long lastUploadMillis;

    public UploadManager() {
        lastUploadMillis = System.currentTimeMillis();
    }

    public void upload(Path path) {
        try {
            if (lastUploadMillis < Files.getLastModifiedTime(path).toMillis()) {
                AwsS3ClientWrapper.upload(path);
                lastUploadMillis = System.currentTimeMillis();
            }
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }
}
