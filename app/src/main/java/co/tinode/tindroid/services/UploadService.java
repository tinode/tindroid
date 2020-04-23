package co.tinode.tindroid.services;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import co.tinode.tindroid.Cache;
import co.tinode.tinodesdk.LargeFileHelper;

public class UploadService extends JobService {
    private static final String TAG = "UploadService";

    private static final int UPLOAD_JOB_ID = 101;

    public static final String ACTION_PROGRESS = "action.UPLOAD_PROGRESS";
    public static final String ACTION_DONE = "action.UPLOAD_COMPLETED";
    public static final String ACTION_CANCEL = "action.UPLOAD_CANCEL";

    LocalBroadcastManager mBroadcastManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mBroadcastManager = LocalBroadcastManager.getInstance(this);
        mBroadcastManager.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        }, new IntentFilter(ACTION_CANCEL));
    }

    public static boolean startUpload(Context context, @NonNull Uri source, String topicName, long msgId,
                                      String fileName, String mimeType, long fileSize, long loaderId) {

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            return false;
        }

        PersistableBundle args = new PersistableBundle(6);
        args.putString("uri", source.toString());
        args.putString("topicName", topicName);
        args.putString("fileName", fileName);
        args.putString("mimeType", mimeType);
        args.putLong("fileSize", fileSize);
        args.putLong("msgId", msgId);
        args.putLong("loaderId", loaderId);
        return jobScheduler.schedule(
                new JobInfo.Builder(UPLOAD_JOB_ID,
                        new ComponentName(context, UploadService.class))
                        .setOverrideDeadline(100)
                        .setExtras(args)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build()) > 0;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        UploadTask task = new UploadTask(this);
        task.execute(params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private static class UploadTask extends AsyncTask<JobParameters, Bundle, Bundle> {
        private WeakReference<UploadService> mContext;
        private JobParameters mJobParams;

        UploadTask(UploadService context) {
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Bundle doInBackground(JobParameters[] args) {
            Context context = mContext.get();
            if (context == null) {
                Bundle result = new Bundle();
                result.putString("error", "Missing context");
                return result;
            }
            mJobParams = args[0];
            PersistableBundle arg = mJobParams.getExtras();

            Uri uri = Uri.parse(arg.getString("uri"));
            final String topicName = arg.getString("topicName");
            String fileName = arg.getString("fileName");
            String mimeType = arg.getString("mimeType");
            long fileSize = arg.getLong("fileSize", 0);
            final long msgId = arg.getLong("msgId", 0);
            final long loaderId = arg.getLong("loaderId", 0);

            final ContentResolver resolver = context.getContentResolver();
            InputStream is;
            try {
                assert uri != null;
                is = resolver.openInputStream(uri);
            } catch (IOException ex) {
                Log.e(TAG, "Failed to open file at " + uri.toString(), ex);
                // Inform caller that action has failed.
                Bundle result = new Bundle();
                result.putString("error", ex.getMessage());
                return result;
            }

            final LargeFileHelper uploader = Cache.getTinode().getFileUploader();
            try {
                uploader.upload(is, fileName, mimeType, fileSize,
                        new LargeFileHelper.FileHelperProgress() {
                            @Override
                            public void onProgress(long progress, long size) {
                                Bundle result = new Bundle();
                                result.putLong("progress", progress);
                                result.putLong("size", size);
                                result.putString("topicName", topicName);
                                result.putLong("msgId", msgId);
                                result.putLong("loaderId", loaderId);
                                publishProgress(result);
                            }
                        });
            } catch (IOException ex) {
                Log.e(TAG, "Upload failed", ex);
                // Inform caller that action has failed.
                Bundle result = new Bundle();
                result.putString("error", ex.getMessage());
                return result;
            }

            Bundle result = new Bundle();
            result.putBoolean("success", true);
            return result;
        }

        @Override
        protected void onProgressUpdate(Bundle... args) {
            super.onProgressUpdate(args);
            UploadService context = mContext.get();
            if (context != null) {
                Intent progress = new Intent(ACTION_PROGRESS);
                progress.putExtras(args[0]);
                context.mBroadcastManager.sendBroadcast(progress);
            }
        }

        @Override
        protected void onPostExecute(Bundle result) {
            UploadService context = mContext.get();
            if (context != null) {
                context.jobFinished(mJobParams, false);
                Intent done = new Intent(ACTION_DONE);
                done.putExtra("result", result);
                context.mBroadcastManager.sendBroadcast(done);
            }
        }
    }
}
