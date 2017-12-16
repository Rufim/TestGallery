package ru.kazantsev.gallery.util;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;

import java.util.ArrayList;

import ru.kazantsev.template.util.SystemUtils;

/**
 * Created by Admin on 13.12.2017.
 */

public class ImageObserver {
    private final Cursor cursor;
    private final ContentObserver contentObserver;
    private final ContentResolver resolver;
    private String[] projection = {MediaStore.MediaColumns.DATA};
    private Uri IMAGE_URI_CONTENT = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private boolean running = true;

    public Bitmap getThumbnail(String path) {
        Cursor ca = resolver.query(IMAGE_URI_CONTENT, new String[] { MediaStore.MediaColumns._ID }, MediaStore.MediaColumns.DATA + "=?", new String[] {path}, null);
        Bitmap thumb = null;
        if (ca != null && ca.moveToFirst()) {
            int id = ca.getInt(ca.getColumnIndex(MediaStore.MediaColumns._ID));
            SystemUtils.close(ca);
            thumb = MediaStore.Images.Thumbnails.getThumbnail(resolver, id, MediaStore.Images.Thumbnails.MICRO_KIND, null );
        }
        SystemUtils.close(ca);
        return thumb;
    }


    private class ObserverWithListener extends ContentObserver {
        private final OnChangeListener mListener;

        public ObserverWithListener(OnChangeListener listener) {
            super(new Handler());
            mListener = listener;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (running) {
                mListener.onChange(selfChange, listImages());
            }
        }
    }

    public ArrayList<String> listImages() {
        Uri uri;
        Cursor cursor;
        int column_index_data;
        ArrayList<String> listOfAllImages = new ArrayList<String>();
        String absolutePathOfImage = null;

        cursor = resolver.query(IMAGE_URI_CONTENT, projection, null,
                null, null);

        column_index_data = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        while (cursor.moveToNext()) {
            absolutePathOfImage = cursor.getString(column_index_data);
            listOfAllImages.add(absolutePathOfImage);
        }
        cursor.close();
        return listOfAllImages;
    }

    public ImageObserver(ContentResolver resolver, final OnChangeListener listener) {
        this.resolver = resolver;
        cursor = resolver.query(IMAGE_URI_CONTENT, projection, null,
                null, null);
        contentObserver = new ObserverWithListener(listener);
        cursor.registerContentObserver(contentObserver);
    }

    public void stop() {
        cursor.unregisterContentObserver(contentObserver);
        running = false;
        cursor.close();
    }

    public interface OnChangeListener {
        public void onChange(boolean self, ArrayList<String> newData);
    }
}