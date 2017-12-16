package ru.kazantsev.gallery.fragment;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


import com.bumptech.glide.load.data.mediastore.MediaStoreUtil;

import net.vrallev.android.cat.Cat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import ru.kazantsev.gallery.util.ImageObserver;
import ru.kazantsev.template.adapter.ItemListAdapter;
import ru.kazantsev.template.adapter.LazyItemListAdapter;
import ru.kazantsev.template.fragments.BaseFragment;
import ru.kazantsev.gallery.R;
import ru.kazantsev.template.fragments.ErrorFragment;
import ru.kazantsev.template.fragments.ListFragment;
import ru.kazantsev.template.util.AndroidSystemUtils;
import ru.kazantsev.template.util.GuiUtils;
import ru.kazantsev.template.util.SystemUtils;

public class GalleryFragment extends ListFragment<GalleryFragment.ImageItem> implements ImageObserver.OnChangeListener {

    private static String MESSAGE = "item";

    File[] dirsToScan = new File[]{Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)};
    boolean addAll = true;
    boolean hasNew = false;
    final Set<String> buffer = new LinkedHashSet<>();
    Integer allSize = 0;
    Integer added = 0;
    ImageObserver observer;
    Timer timer = null;
    UpdateList updateList;
    BufferUpdate task;
    ReentrantLock lock = new ReentrantLock();

    public static GalleryFragment show(BaseFragment fragment) {
        return show(fragment, GalleryFragment.class);
    }


    @Override
    protected void firstLoad(boolean scroll) {
        stopLoading();
        pageSize = 3;
        loadMoreBar.setIndeterminate(false);
        loadMoreBar.setMax(100);
        loadMoreBar.setProgress(0);
        loadMoreBar.setVisibility(View.VISIBLE);
    }


    @Override
    public void onResume() {
        super.onResume();
        if (timer != null) timer.cancel();
        timer = new Timer();
        updateList = new UpdateList();
        if (observer == null)
            observer = new ImageObserver(getActivity().getContentResolver(), this);
        task = new BufferUpdate(task == null ? observer.listImages() : task.initialImages);
        timer.scheduleAtFixedRate(task, 0, 10000);
    }

    @Override
    public void onPause() {
        observer.stop();
        observer = null;
        timer.cancel();
        timer = null;
        super.onPause();
    }


    @Override
    public void onChange(boolean self, ArrayList<String> newData) {
        synchronized (buffer) {
            if (timer != null) {
                ArrayList<String> initial = task.initialImages;
                ArrayList<String> additions = new ArrayList<>(newData);
                ArrayList<String> deletions = new ArrayList<>(initial);
                additions.removeAll(initial);
                deletions.removeAll(newData);
                if (!deletions.isEmpty()) {
                    Iterator<ImageItem> iterator = adapter.getItems().iterator();
                    while (iterator.hasNext()) {
                        if (deletions.contains(iterator.next().path)) {
                            iterator.remove();
                        }
                    }
                    updateList.post(() -> adapter.notifyChanged());
                    initial.removeAll(deletions);
                }
                if (!additions.isEmpty()) {
                    initial.addAll(additions);
                    buffer.addAll(additions);
                    hasNew = true;
                }
            }
        }
    }

    @Override
    public void refreshData(boolean showProgress) {
        if(buffer.isEmpty()) {
            synchronized (buffer) {
                addAll = true;
                hasNew = false;
                buffer.clear();
                adapter.clear();
                adapter.notifyChanged();
                onResume();
            }
        } else {
            swipeRefresh.setRefreshing(false);
        }
    }

    private ImageItem processPath(String path) throws IOException {
        if (path != null && isAdded()) {
            File image = new File(path);
            if (image.exists()) {
                ImageItem item = new ImageItem();
                item.md5 = calculateMD5(image);
                String name = image.getName();
                item.name = !name.contains(".") ? name : name.substring(0, name.lastIndexOf("."));
                item.size = String.format("%.3f", (((double) image.length() / 1024) / 1024));
                item.path = image.getAbsolutePath();
                Bitmap existDefault = observer.getThumbnail(path);
                if(existDefault == null) {
                    Bitmap thumb = generateThrumbnail(item.path);
                    File cache = getContext().getCacheDir();
                    if (cache.canWrite()) {
                        File thumbFile = new File(cache, name + ".PNG");
                        FileOutputStream outputStream = null;
                        try {
                            outputStream = new FileOutputStream(thumbFile);
                            thumb.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
                        } finally {
                            SystemUtils.close(outputStream);
                        }
                        item.thrumbnailPath = thumbFile.getAbsolutePath();
                    }
                    thumb.recycle();
                } else {
                    existDefault.recycle();
                }
                return item;
            }
        }
        return null;
    }

    private Bitmap generateThrumbnail(String path) {
        Bitmap source = BitmapFactory.decodeFile(path);
        int viewHeight = GuiUtils.dpToPx(150, getContext());
        float scale = (float) viewHeight / source.getHeight();
        Bitmap thrumb = ThumbnailUtils.extractThumbnail(source, (int) (source.getWidth() * scale), viewHeight);
        source.recycle();
        return thrumb;
    }

    @Override
    protected ItemListAdapter<ImageItem> newAdapter() {
        return new GalleryAdapter();
    }

    public class GalleryAdapter extends LazyItemListAdapter<ImageItem> {

        public GalleryAdapter() {
            super(R.layout.item_gallery);
        }

        @Override
        public void onBindHolder(ViewHolder viewHolder, @Nullable ImageItem item) {
            ViewGroup root = (ViewGroup) viewHolder.getItemView();
            GuiUtils.setText(root, R.id.item_gallery_name, item.name);
            GuiUtils.setText(root, R.id.item_gallery_md5, item.md5);
            GuiUtils.setText(root, R.id.item_gallery_size, item.size);
            ImageView imageView = GuiUtils.getView(root, R.id.item_gallery_image);
            if(item.thrumbnailPath != null) {
                File thrumb = new File(item.thrumbnailPath);
                if(thrumb.exists()) {
                    imageView.setImageBitmap(BitmapFactory.decodeFile(item.thrumbnailPath));
                } else {
                    imageView.setImageBitmap(generateThrumbnail(item.thrumbnailPath));
                }
            } else {
                imageView.setImageBitmap(observer.getThumbnail(item.path));
            }
        }
    }


    public static String calculateMD5(File updateFile) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Cat.e("Exception while getting digest", e);
            return null;
        }

        InputStream is;
        try {
            is = new FileInputStream(updateFile);
        } catch (FileNotFoundException e) {
            Cat.e("Exception while getting FileInputStream", e);
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Cat.e("Exception on closing MD5 input stream", e);
            }
        }
    }

    public class BufferUpdate extends TimerTask {

        final ArrayList<String> initialImages;
        boolean scanning = false;
        MediaScannerConnection connection;

        public BufferUpdate(ArrayList<String> initialImages) {
            this.initialImages = initialImages;
        }

        @Override
        public void run() {
            synchronized (buffer) {
                if (buffer.isEmpty() && addAll) {
                    buffer.addAll(initialImages);
                    addAll = false;
                    allSize = buffer.size();
                    added = 0;
                    updateList.post(() -> {
                        loadMoreBar.setProgress(0);
                        loadMoreBar.setVisibility(View.VISIBLE);
                        swipeRefresh.setRefreshing(false);
                    });
                } else if (hasNew && !buffer.isEmpty()) {
                    allSize = buffer.size();
                    added = 0;
                    hasNew = false;
                    updateList.post(() -> {
                        loadMoreBar.setProgress(0);
                        loadMoreBar.setVisibility(View.VISIBLE);
                        swipeRefresh.setRefreshing(false);
                    });
                } else if (!scanning) {
                    scanning = true;
                    final ArrayList<String> paths = new ArrayList<>();
                    ArrayList<String> delete = new ArrayList<>();
                    TreeSet<File> fileTree = new TreeSet<>();
                    for (File dir : dirsToScan) {
                        SystemUtils.listFilesRecursive(dir, fileTree);
                    }
                    for (File file : fileTree) {
                        if (!initialImages.contains(file.getAbsolutePath())) {
                            paths.add(file.getAbsolutePath());
                        }
                    }
                    Iterator<String> it = initialImages.iterator();
                    while (it.hasNext()) {
                        String path = it.next();
                        if (!new File(path).exists()) {
                            delete.add(path);
                        }
                    }
                    paths.addAll(delete);
                    if (!paths.isEmpty()) {
                        if (connection == null) {
                            connection = new MediaScannerConnection(getContext(), new MediaScannerConnection.MediaScannerConnectionClient() {
                                @Override
                                public void onMediaScannerConnected() {
                                    synchronized (paths) {
                                        scanPaths(paths);
                                    }
                                }

                                @Override
                                public void onScanCompleted(String s, Uri uri) {
                                    synchronized (paths) {
                                        paths.remove(s);
                                        if (paths.isEmpty()) {
                                            scanning = false;
                                        }
                                    }
                                }
                            });
                            connection.connect();
                        } else {
                            synchronized (paths) {
                                scanPaths(paths);
                            }
                        }
                    } else {
                        scanning = false;
                    }
                }
                if (!buffer.isEmpty()) {
                    Iterator<String> it = buffer.iterator();
                    ImageItem next;
                    while (it.hasNext()) {
                        try {
                            next = processPath(it.next());
                        } catch (Exception ex) {
                            next = null;
                        }
                        it.remove();
                        addItem(next);
                    }
                }
            }
        }

        private void scanPaths(ArrayList<String> paths) {
            boolean triggered = false;
            Iterator<String> it = paths.iterator();
            while (it.hasNext()) {
                String path = it.next();
                String mimeType = null;
                int index = path.lastIndexOf(".");
                if (index > 0 && path.length() - 1 != index) {
                    String postfix = path.substring(index + 1);
                    String[] fileType = new String[]{"png", "jpg", "jpeg"};
                    for (String type : fileType) {
                        if (type.equalsIgnoreCase(postfix)) {
                            mimeType = "image/" + type;
                            break;
                        }
                    }
                }
                if (mimeType != null) {
                    triggered = true;
                    connection.scanFile(path, mimeType);
                } else {
                    it.remove();
                }
            }
            if (!triggered) {
                scanning = false;
            }
        }
    }

    private void addItem(ImageItem item) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(MESSAGE, item);
        Message message = new Message();
        message.setData(bundle);
        updateList.sendMessage(message);
    }

    public class UpdateList extends Handler {
        public void handleMessage(Message msg) {
            if (updateList == this) {
                ImageItem item = (ImageItem) msg.getData().getSerializable(MESSAGE);
                if (item != null)
                    adapter.addItem((ImageItem) msg.getData().getSerializable(MESSAGE));
                added++;
                loadMoreBar.setProgress((added * 100) / allSize);
                if (allSize <= added) {
                    loadMoreBar.setVisibility(View.GONE);
                }
            } else {
                addItem((ImageItem) msg.getData().getSerializable(MESSAGE));
            }
        }
    }

    public static class ImageItem implements Serializable {
        String name;
        String md5;
        String size;
        String path;
        String thrumbnailPath;
    }
}
