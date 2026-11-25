package android.app;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.ViewGroup;

/**
 * Stub class for ActivityView to allow compilation.
 * This class is hidden in the Android SDK but present on devices.
 * 
 * Update: StateCallback is a class on some devices, not an interface.
 */
public class ActivityView extends ViewGroup {
    public ActivityView(Context context) { super(context); }
    public ActivityView(Context context, AttributeSet attrs) { super(context, attrs); }
    public ActivityView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}

    public abstract static class StateCallback {
        public abstract void onActivityViewReady(ActivityView view);
        public abstract void onActivityViewDestroyed(ActivityView view);
        public abstract void onTaskMovedToFront(int taskId);
    }

    public void setCallback(StateCallback callback) {}
    public void startActivity(Intent intent) {}
    public void release() {}
}
