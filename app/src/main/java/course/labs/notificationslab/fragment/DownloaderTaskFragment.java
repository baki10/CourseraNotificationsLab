package course.labs.notificationslab.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;

import course.labs.notificationslab.R;
import course.labs.notificationslab.activity.MainActivity;
import course.labs.notificationslab.listener.DownloadFinishedListener;

public class DownloaderTaskFragment extends Fragment {

  private DownloadFinishedListener downloadFinishedListener;
  private Context context;
  private final int MY_NOTIFICATION_ID = 11151990;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Preserve across reconfigurations
    setRetainInstance(true);
    // Retrieve friends resource ids from arguments
    ArrayList<Integer> integers =
        getArguments().getIntegerArrayList(MainActivity.TAG_FRIEND_RESOURCE_IDS);
    if (integers != null) {
      new DownloaderTask().execute(integers.toArray(new Integer[integers.size()]));
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    context = activity.getApplicationContext();
    // Make sure that the hosting activity has implemented
    // the correct callback interface.
    try {
      downloadFinishedListener = (DownloadFinishedListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement DownloadFinishedListener");
    }
  }

  // Null out downloadFinishedListener
  @Override
  public void onDetach() {
    super.onDetach();
    downloadFinishedListener = null;
  }

  // Done: Implement an AsyncTask subclass called DownLoaderTask.
  // This class must use the downloadTweets method (currently commented
  // out). Ultimately, it must also pass newly available data back to
  // the hosting Activity using the DownloadFinishedListener interface.
  public class DownloaderTask extends AsyncTask<Integer, Void, String[]> {

    @Override
    protected String[] doInBackground(Integer... resourceIDS) {
      return downloadTweets(resourceIDS);
    }

    @Override
    protected void onPostExecute(String[] strings) {
      if (downloadFinishedListener != null)
        downloadFinishedListener.notifyDataDownloadFinished(strings);
    }
  }

  private String[] downloadTweets(Integer resourceIDS[]) {

    final int simulatedDelay = 2000;
    String[] feeds = new String[resourceIDS.length];
    boolean downLoadCompleted = false;

    try {
      for (int idx = 0; idx < resourceIDS.length; idx++) {
        InputStream inputStream;
        BufferedReader in;
        try {
          // Pretend downloading takes a long time
          Thread.sleep(simulatedDelay);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        inputStream = context.getResources().openRawResource(
            resourceIDS[idx]);
        in = new BufferedReader(new InputStreamReader(inputStream));

        String readLine;
        StringBuffer buf = new StringBuffer();

        while ((readLine = in.readLine()) != null) {
          buf.append(readLine);
        }

        feeds[idx] = buf.toString();

        if (null != in) {
          in.close();
        }
      }

      downLoadCompleted = true;
      saveTweetsToFile(feeds);

    } catch (IOException e) {
      e.printStackTrace();
    }

    // Notify user that downloading has finished
    notify(downLoadCompleted);

    return feeds;

  }

  // If necessary, notifies the user that the tweet downloads are
  // complete. Sends an ordered broadcast back to the BroadcastReceiver in
  // MainActivity to determine whether the notification is necessary.

  private void notify(final boolean success) {

    final Intent restartMainActivityIntent = new Intent(context, MainActivity.class);
    restartMainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    // Sends an ordered broadcast to determine whether MainActivity is
    // active and in the foreground. Creates a new BroadcastReceiver
    // to receive a result indicating the state of MainActivity

    // The Action for this broadcast Intent is
    // MainActivity.DATA_REFRESHED_ACTION
    // The result, MainActivity.IS_ALIVE, indicates that MainActivity is
    // active and in the foreground.

    context.sendOrderedBroadcast(new Intent(MainActivity.DATA_REFRESHED_ACTION), null,
        new BroadcastReceiver() {

          final String failMsg = context
              .getString(R.string.download_failed_string);
          final String successMsg = context
              .getString(R.string.download_succes_string);
          final String notificationSentMsg = context
              .getString(R.string.notification_sent_string);

          @Override
          public void onReceive(Context context, Intent intent) {

            // Done: Check whether or not the MainActivity
            // received the broadcast
            if (getResultCode() != MainActivity.IS_ALIVE) {

              // Done: If not, create a PendingIntent using
              // the
              // restartMainActivityIntent and set its flags
              // to FLAG_UPDATE_CURRENT
              final PendingIntent pendingIntent =
                  PendingIntent.getActivity(
                      DownloaderTaskFragment.this.context,
                      0,
                      restartMainActivityIntent,
                      PendingIntent.FLAG_UPDATE_CURRENT);


              // Uses R.layout.custom_notification for the
              // layout of the notification View. The xml
              // file is in res/layout/custom_notification.xml

              RemoteViews mContentView = new RemoteViews(
                  DownloaderTaskFragment.this.context.getPackageName(),
                  R.layout.custom_notification);

              // Done: Set the notification View's text to
              // reflect whether the download completed
              // successfully
              if (success) {
                mContentView.setTextViewText(R.id.text, successMsg);
              } else {
                mContentView.setTextViewText(R.id.text, failMsg);
              }

              // Done: Use the Notification.Builder class to
              // create the Notification. You will have to set
              // several pieces of information. You can use
              // android.R.drawable.stat_sys_warning
              // for the small icon. You should also
              // setAutoCancel(true).

              Notification notification =
                  new Notification.Builder(DownloaderTaskFragment.this.context)
                      .setAutoCancel(true)
                      .setSmallIcon(android.R.drawable.stat_sys_warning)
                      .setContent(mContentView)
                      .setContentIntent(pendingIntent)
                      .build();

              // Done: Send the notification
              NotificationManager mNotificationManager =
                  (NotificationManager) DownloaderTaskFragment.this.context.getSystemService(Context.NOTIFICATION_SERVICE);
              mNotificationManager.notify(MY_NOTIFICATION_ID, notification);

              Toast.makeText(DownloaderTaskFragment.this.context, notificationSentMsg,
                  Toast.LENGTH_LONG).show();

            } else {
              Toast.makeText(DownloaderTaskFragment.this.context,
                  success ? successMsg : failMsg,
                  Toast.LENGTH_LONG).show();
            }
          }
        }, null, 0, null, null);
  }

  private void saveTweetsToFile(String[] result) {
    PrintWriter writer = null;
    try {
      FileOutputStream fos = context.openFileOutput(
          MainActivity.TWEET_FILENAME, Context.MODE_PRIVATE);
      writer = new PrintWriter(new BufferedWriter(
          new OutputStreamWriter(fos)));

      for (String s : result) {
        writer.println(s);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (null != writer) {
        writer.close();
      }
    }
  }

}