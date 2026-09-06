package app.onepve.geelyconsole.utils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;

public class ApkProvider extends ContentProvider {
    public static final String AUTHORITY = "app.onepve.geelyconsole.apk";
    public static final String DEFAULT_APK_NAME = "geelytoolbox_update.apk";
    private static final String TAG = "GeelyToolbox.ApkProvider";

    public static Uri buildApkUri(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            fileName = DEFAULT_APK_NAME;
        }
        return Uri.parse("content://" + AUTHORITY + "/" + fileName);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("..") || name.contains("/")) {
            throw new FileNotFoundException("Invalid filename: " + name);
        }
        if (getContext() == null) {
            throw new FileNotFoundException("Context is null");
        }

        // Check internal cache dir first
        File f = new File(getContext().getCacheDir(), name);
        if (!f.exists()) {
            // Check app private external files dir
            File ext = getContext().getExternalFilesDir(null);
            if (ext != null) {
                File f2 = new File(ext, name);
                if (f2.exists()) {
                    f = f2;
                }
            }
        }

        if (!f.exists()) {
            Log.w(TAG, "apk file not found: " + f.getAbsolutePath());
            throw new FileNotFoundException("apk file not found: " + name);
        }

        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
