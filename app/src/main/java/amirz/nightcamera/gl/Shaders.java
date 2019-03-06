package amirz.nightcamera.gl;

import android.content.Context;
import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import amirz.nightcamera.R;

public class Shaders {
    public static String VS;

    public static String FS_SCALE;
    public static String FS_ALIGN;
    public static String FS_MERGE;

    public static void load(Context context) {
        Resources res = context.getResources();

        VS = readRaw(res, R.raw.passthrough_vs);

        FS_SCALE = readRaw(res, R.raw.stage0_scale_fs);
        FS_ALIGN = readRaw(res, R.raw.stage1_align_fs);
        FS_MERGE = readRaw(res, R.raw.stage2_merge_fs);
    }

    private static String readRaw(Resources res, int resId) {
        try (InputStream inputStream = res.openRawResource(resId)) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuilder text = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }

            return text.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
