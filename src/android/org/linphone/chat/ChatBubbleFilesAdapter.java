package org.linphone.chat;

/*
ChatBubbleFilesAdapter.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.core.ChatMessage;
import org.linphone.core.Content;
import org.linphone.mediastream.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class ChatBubbleFilesAdapter extends RecyclerView.Adapter<ChatBubbleFilesAdapter.ChatBubbleFilesViewHolder> {
    private Context mContext;
    private ChatMessage mMessage;
    private List<Content> mContents;

    public ChatBubbleFilesAdapter(Context context, ChatMessage message, List<Content> contents) {
        mContext = context;
        mMessage = message;
        mContents = contents;
    }

    @NonNull
    @Override
    public ChatBubbleFilesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_content, parent, false);
        ChatBubbleFilesViewHolder holder = new ChatBubbleFilesViewHolder(layoutView);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ChatBubbleFilesViewHolder holder, int position) {
        holder.file.setVisibility(View.GONE);
        holder.image.setVisibility(View.GONE);
        holder.download.setVisibility(View.GONE);
        holder.fileTransferInProgress.setVisibility(View.GONE);

        Content c = mContents.get(position);
        c.setUserData(holder);

        if (c.isFileTransfer()) {
            if (mMessage.isOutgoing()) {
                holder.fileTransferInProgress.setVisibility(View.VISIBLE);
                final String appData = c.getFilePath();
                if (LinphoneUtils.isExtensionImage(appData)) {
                    holder.image.setVisibility(View.VISIBLE);
                    loadBitmap(appData, holder.image);
                } else {
                    holder.file.setVisibility(View.VISIBLE);
                    holder.file.setTag(appData);
                    holder.file.setText(LinphoneUtils.getNameFromFilePath(appData));
                }
            } else {
                if (mMessage.isFileTransferInProgress() && mMessage.getFileTransferInformation() == c) {
                    holder.fileTransferInProgress.setVisibility(View.VISIBLE);
                }
                holder.download.setVisibility(View.VISIBLE);
                holder.download.setTag(c);
                holder.download.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mContext.getPackageManager().checkPermission(WRITE_EXTERNAL_STORAGE, mContext.getPackageName()) == PackageManager.PERMISSION_GRANTED) {
                            v.setEnabled(false);
                            Content content = (Content) v.getTag();

                            String filename = content.getName();
                            File file = new File(LinphoneUtils.getStorageDirectory(mContext), filename);
                            int prefix = 1;
                            while (file.exists()) {
                                file = new File(LinphoneUtils.getStorageDirectory(mContext), prefix + "_" + filename);
                                Log.w("File with that name already exists, renamed to " + prefix + "_" + filename);
                                prefix += 1;
                            }
                            content.setFilePath(file.getPath());
                            mMessage.downloadContent(content);
                        } else {
                            Log.w("WRITE_EXTERNAL_STORAGE permission not granted, won't be able to store the downloaded file");
                            LinphoneActivity.instance().checkAndRequestExternalStoragePermission();
                        }
                    }
                });
            }
        } else {
            final String appData = c.getFilePath();
            if (LinphoneUtils.isExtensionImage(appData)) {
                holder.image.setVisibility(View.VISIBLE);
                loadBitmap(appData, holder.image);
            } else {
                holder.file.setVisibility(View.VISIBLE);
                holder.file.setTag(appData);
                holder.file.setText(LinphoneUtils.getNameFromFilePath(appData));
                holder.file.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String appData = (String) v.getTag();
                        openFile(appData);
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return mContents.size();
    }

    class ChatBubbleFilesViewHolder extends RecyclerView.ViewHolder {
        public TextView file;
        public ImageView image;
        public Button download;
        public ProgressBar fileTransferInProgress;

        public ChatBubbleFilesViewHolder(View view) {
            super(view);

            file = view.findViewById(R.id.file);
            image = view.findViewById(R.id.image);
            download = view.findViewById(R.id.download);
            fileTransferInProgress = view.findViewById(R.id.fileTransferInProgress);
        }
    }

    private void loadBitmap(String path, ImageView imageView) {
        if (cancelPotentialWork(path, imageView)) {
            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            AsyncBitmap asyncBitmap = new AsyncBitmap(mContext.getResources(), BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_file_default), task);
            imageView.setImageDrawable(asyncBitmap);
            task.execute(path);
        }
    }

    private void openFile(String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file;
        Uri contentUri;
        if (path.startsWith("file://")) {
            path = path.substring("file://".length());
            file = new File(path);
            contentUri = FileProvider.getUriForFile(mContext, mContext.getResources().getString(R.string.file_provider), file);
        } else if (path.startsWith("content://")) {
            contentUri = Uri.parse(path);
        } else {
            file = new File(path);
            try {
                contentUri = FileProvider.getUriForFile(mContext, mContext.getResources().getString(R.string.file_provider), file);
            } catch (Exception e) {
                contentUri = Uri.parse(path);
            }
        }
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(contentUri.toString());
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if (type != null) {
            intent.setDataAndType(contentUri, type);
        } else {
            intent.setDataAndType(contentUri, "*/*");
        }
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
        mContext.startActivity(intent);
    }

    /*
     * Bitmap related classes and methods
     */

    private class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private static final int SIZE_SMALL = 100;
        private final WeakReference<ImageView> imageViewReference;
        public String path;

        public BitmapWorkerTask(ImageView imageView) {
            path = null;
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            path = params[0];
            Bitmap bm = null;
            Bitmap thumbnail = null;
            if (LinphoneUtils.isExtensionImage(path)) {
                if (path.startsWith("content")) {
                    try {
                        bm = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(path));
                    } catch (FileNotFoundException e) {
                        Log.e(e);
                    } catch (IOException e) {
                        Log.e(e);
                    }
                } else {
                    bm = BitmapFactory.decodeFile(path);
                }

                // Rotate the bitmap if possible/needed, using EXIF data
                try {
                    Bitmap bm_tmp;
                    ExifInterface exif = new ExifInterface(path);
                    int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                    Matrix matrix = new Matrix();
                    if (pictureOrientation == 6) {
                        matrix.postRotate(90);
                    } else if (pictureOrientation == 3) {
                        matrix.postRotate(180);
                    } else if (pictureOrientation == 8) {
                        matrix.postRotate(270);
                    }
                    bm_tmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                    if (bm_tmp != bm) {
                        bm.recycle();
                        bm = bm_tmp;
                    }
                } catch (Exception e) {
                    Log.e(e);
                }

                if (bm != null) {
                    thumbnail = ThumbnailUtils.extractThumbnail(bm, SIZE_SMALL, SIZE_SMALL);
                    bm.recycle();
                }
                return thumbnail;
            }
            return BitmapFactory.decodeResource(mContext.getResources(), R.drawable.chat_file_default);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask && imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setTag(path);
                    imageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openFile((String) v.getTag());
                        }
                    });
                }
            }
        }
    }

    class AsyncBitmap extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncBitmap(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    private boolean cancelPotentialWork(String path, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final String bitmapData = bitmapWorkerTask.path;
            // If bitmapData is not yet set or it differs from the new data
            if (bitmapData == null || bitmapData != path) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncBitmap) {
                final AsyncBitmap asyncDrawable = (AsyncBitmap) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }
}
