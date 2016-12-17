package course.labs.notificationslab.listener;

public interface SelectionListener {
  void onItemSelected(int position);

  boolean canAllowUserClicks();
}