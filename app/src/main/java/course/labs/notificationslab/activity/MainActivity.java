package course.labs.notificationslab.activity;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import course.labs.notificationslab.R;
import course.labs.notificationslab.fragment.DownloaderTaskFragment;
import course.labs.notificationslab.fragment.FeedFragment;
import course.labs.notificationslab.fragment.FriendsFragment;
import course.labs.notificationslab.listener.DownloadFinishedListener;
import course.labs.notificationslab.listener.SelectionListener;

public class MainActivity extends Activity implements SelectionListener, DownloadFinishedListener {

  private static final long TWO_MIN = 2 * 60 * 1000;
  private static final String TAG_NAME = "name";
  private static final String TAG_USER = "user";
  private static final String TAG_TEXT = "text";
  private static final String TAG_FRIENDS_FRAGMENT = "friends_fragment";
  private static final String TAG_FEED_FRAGMENT = "feed_fragment";
  private static final String TAG_DOWNLOADER_FRAGMENT = "downloader_fragment";
  private static final String TAG_IS_DATA_AVAILABLE = "is_data_available";
  private static final String TAG_PROCESSED_FEEDS = "processed_feeds";
  public static final String TAG_TWEET_DATA = "data";
  public static final String TAG_FRIEND_RESOURCE_IDS = "friends";

  public static final String TWEET_FILENAME = "tweets.txt";
  public static final String[] FRIENDS_NAMES = {"taylorswift13", "msrebeccablack", "ladygaga"};
  public static final String DATA_REFRESHED_ACTION = "course.labs.notificationslabnew.DATA_REFRESHED";
  public static final int IS_ALIVE = Activity.RESULT_FIRST_USER;

  // Raw feed file IDs used to reference stored tweet data
  public static final ArrayList<Integer> rawTextFeedIds = new ArrayList<Integer>(
      Arrays.asList(R.raw.tswift, R.raw.rblack, R.raw.lgaga));

  private FragmentManager fragmentManager;
  private FriendsFragment friendsFragment;
  private FeedFragment feedFragment;
  private DownloaderTaskFragment downloaderTaskFragment;
  private boolean isInteractionEnabled;
  private String[] formattedFeeds = new String[rawTextFeedIds.size()];
  private BroadcastReceiver refreshReceiver;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    fragmentManager = getFragmentManager();

    if (savedInstanceState == null) {
      setupFragments();
    } else {
      restoreState(savedInstanceState);
    }
  }

  // One time setup of UI and retained (headless) Fragment
  private void setupFragments() {
    installFriendsFragment();

    // The feed is fresh if it was downloaded less than 2 minutes ago
    if (!isFresh()) {
      installDownloaderTaskFragment();
      // Done: Show a Toast message displaying
      Toast.makeText(this, R.string.download_in_progress_string, Toast.LENGTH_LONG).show();
      // Set up a BroadcastReceiver to receive an Intent when download
      // finishes.
      refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          // Done:
          // Check to make sure this is an ordered broadcast
          // Let sender know that the Intent was received
          // by setting result code to MainActivity.IS_ALIVE
          if (isOrderedBroadcast()) {
            setResultCode(MainActivity.IS_ALIVE);
          }
        }
      };
    } else {
      // Process Twitter data taken from stored file
      parseJSON(loadTweetsFromFile());

      // Enable user interaction
      isInteractionEnabled = true;
    }
  }

  // The feed is fresh if it was downloaded less than 2 minutes ago
  private boolean isFresh() {
    return (System.currentTimeMillis() - getFileStreamPath(TWEET_FILENAME).lastModified()) < TWO_MIN;
  }

  // Add Friends Fragment to Activity
  private void installFriendsFragment() {
    friendsFragment = new FriendsFragment();
    // Give Fragment to the FragmentManager
    fragmentManager.beginTransaction()
        .replace(R.id.fragment_container, friendsFragment, TAG_FRIENDS_FRAGMENT)
        .commit();
  }

  // Add DownloaderTaskFragment to Activity
  private void installDownloaderTaskFragment() {
    downloaderTaskFragment = new DownloaderTaskFragment();
    // Set DownloaderTaskFragment arguments
    Bundle args = new Bundle();
    args.putIntegerArrayList(TAG_FRIEND_RESOURCE_IDS, rawTextFeedIds);
    downloaderTaskFragment.setArguments(args);
    // Give Fragment to the FragmentManager
    fragmentManager.beginTransaction()
        .add(downloaderTaskFragment, TAG_DOWNLOADER_FRAGMENT)
        .commit();
  }

  // Add FeedFragment to Activity
  private void installFeedFragment(String tweetData) {
    // Make new Fragment
    feedFragment = new FeedFragment();
    // Set Fragment arguments
    Bundle args = new Bundle();
    args.putString(TAG_TWEET_DATA, tweetData);
    feedFragment.setArguments(args);
    // Give Fragment to the FragmentManager
    fragmentManager.beginTransaction()
        .replace(R.id.fragment_container, feedFragment, TAG_FEED_FRAGMENT)
        .addToBackStack(null)
        .commit();
  }

  // Register the BroadcastReceiver
  @Override
  protected void onResume() {
    super.onResume();

    // Done:
    // Register the BroadcastReceiver to receive a
    // DATA_REFRESHED_ACTION broadcast
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(DATA_REFRESHED_ACTION);
    registerReceiver(refreshReceiver, intentFilter);


  }

  @Override
  protected void onPause() {

    // Done:
    // Unregister the BroadcastReceiver if it has been registered
    // Note: check that refreshReceiver is not null before attempting to
    // unregister in order to work around an Instrumentation issue
    if (refreshReceiver != null) {
      unregisterReceiver(refreshReceiver);
    }
    super.onPause();
  }

	/*
   * DownloadFinishedListener method
	 */

  // Called back by DownloaderTask after data has been loaded
  @Override
  public void notifyDataDownloadFinished(String[] feeds) {

    // Process downloaded data
    parseJSON(feeds);

    // Enable user interaction
    isInteractionEnabled = true;
    allowUserClicks();
  }

  // Enable user interaction with FriendFragment
  private void allowUserClicks() {
    friendsFragment.setAllowUserClicks(true);
  }

	/*
   * SelectionListener methods
	 */

  // Report whether users interaction is enabled
  @Override
  public boolean canAllowUserClicks() {
    return isInteractionEnabled;
  }

  // Installs the FeedFragment when a Friend name is
  // selected in the FriendsFragment
  @Override
  public void onItemSelected(int position) {
    installFeedFragment(formattedFeeds[position]);
  }

  @Override
  protected void onSaveInstanceState(Bundle savedInstanceState) {
    if (friendsFragment != null) {
      savedInstanceState.putString(TAG_FRIENDS_FRAGMENT, friendsFragment.getTag());
    }
    if (feedFragment != null) {
      savedInstanceState.putString(TAG_FEED_FRAGMENT, feedFragment.getTag());
    }
    if (downloaderTaskFragment != null) {
      savedInstanceState.putString(TAG_DOWNLOADER_FRAGMENT, downloaderTaskFragment.getTag());
    }
    savedInstanceState.putBoolean(TAG_IS_DATA_AVAILABLE, isInteractionEnabled);
    savedInstanceState.putStringArray(TAG_PROCESSED_FEEDS, formattedFeeds);

    super.onSaveInstanceState(savedInstanceState);

  }

  // Restore saved instance state
  private void restoreState(Bundle savedInstanceState) {

    // Fragments tags were saved in onSavedInstanceState
    friendsFragment = (FriendsFragment) fragmentManager
        .findFragmentByTag(savedInstanceState.getString(TAG_FRIENDS_FRAGMENT));

    feedFragment = (FeedFragment) fragmentManager
        .findFragmentByTag(savedInstanceState.getString(TAG_FEED_FRAGMENT));

    downloaderTaskFragment = (DownloaderTaskFragment) fragmentManager
        .findFragmentByTag(savedInstanceState.getString(TAG_DOWNLOADER_FRAGMENT));

    isInteractionEnabled = savedInstanceState.getBoolean(TAG_IS_DATA_AVAILABLE);
    if (isInteractionEnabled) {
      formattedFeeds = savedInstanceState.getStringArray(TAG_PROCESSED_FEEDS);
    }
  }

  // Convert raw data (in JSON format) into text for display
  private void parseJSON(String[] feeds) {
    JSONArray[] JSONFeeds = new JSONArray[feeds.length];
    for (int i = 0; i < JSONFeeds.length; i++) {
      try {
        JSONFeeds[i] = new JSONArray(feeds[i]);
      } catch (JSONException e) {
        e.printStackTrace();
      }

      String name = "";
      String tweet = "";
      JSONArray tmp = JSONFeeds[i];

      // string buffer for feeds
      StringBuffer tweetRec = new StringBuffer("");
      for (int j = 0; j < tmp.length(); j++) {
        try {
          tweet = tmp.getJSONObject(j).getString(TAG_TEXT);
          JSONObject user = (JSONObject) tmp.getJSONObject(j).get(TAG_USER);
          name = user.getString(TAG_NAME);
        } catch (JSONException e) {
          e.printStackTrace();
        }
        tweetRec.append(name + " - " + tweet + "\n\n");
      }
      formattedFeeds[i] = tweetRec.toString();
    }
  }

  // Retrieve feeds text from a file
  // Store them in mRawTextFeed[]

  private String[] loadTweetsFromFile() {
    BufferedReader reader = null;
    ArrayList<String> rawFeeds = new ArrayList<String>();
    try {
      FileInputStream fis = openFileInput(TWEET_FILENAME);
      reader = new BufferedReader(new InputStreamReader(fis));
      String s = null;
      int i = 0;
      while (null != (s = reader.readLine())) {
        rawFeeds.add(i, s);
        i++;
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (null != reader) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return rawFeeds.toArray(new String[rawFeeds.size()]);
  }
}
