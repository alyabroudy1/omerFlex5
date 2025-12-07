package com.omarflex5.temp.omerflex.service.update;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import java.io.File;

public class StorageUtils {

    public static boolean isEnoughSpace(long requiredSpace) {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        long availableSpace = availableBlocks * blockSize;
        return availableSpace >= requiredSpace;
    }

    public static File getUpdateDirectory(Context context) {
        File updateDir = new File(context.getExternalFilesDir(null), Constants.UPDATE_DIR);
        if (!updateDir.exists()) {
            updateDir.mkdirs();
        }
        return updateDir;
    }

    public static File getApkFile(Context context) {
        return new File(getUpdateDirectory(context), Constants.APK_FILE_NAME);
    }

    public static void cleanupOldFiles(Context context) {
        File updateDir = getUpdateDirectory(context);
        if (updateDir.exists() && updateDir.isDirectory()) {
            File[] files = updateDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".apk") || file.getName().endsWith(".tmp")) {
                        file.delete();
                    }
                }
            }
        }
    }
}