package com.example.gauvain.bottomnavigationbehavior;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;


/**
 * Created by gse on 08/02/2017.
 */

public class BottomNavigationBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {

    /**
     * Callback for monitoring events about bottom sheets.
     */
    public abstract static class BottomNavigationCallback {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomnav The BottomNavigationView view.
         * @param newState  The new state. This will be one of
         *                  {@link #STATE_SETTLING}, {@link #STATE_EXPANDED},
         *                   , or {@link #STATE_HIDDEN}.
         */
        public abstract void onStateChanged(@NonNull View bottomnav, @State int newState);

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomnav   The BottomNavigationView view.
         * @param slideOffset The new offset of this BottomNavigationView within [-1,1] range. Offset
         *                    increases as this bottom sheet is moving upward. From 0 to 1 the sheet
         *                    is between collapsed and expanded states and from -1 to 0 it is
         *                    between hidden and collapsed states.
         */
        public abstract void onSlide(@NonNull View bottomnav, float slideOffset);
    }


    /**
     * The BottomNavigationView is settling. Behavior between HIDDEN AND EXPANDED
     */
    public static final int STATE_SETTLING = 1;

    /**
     * The BottomNavigationView is expanded.
     */
    public static final int STATE_EXPANDED = 2;

    /**
     * The BottomNavigationView is hidden.
     */
    public static final int STATE_HIDDEN = 3;

    @IntDef({STATE_EXPANDED, STATE_SETTLING, STATE_HIDDEN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    private static final float HIDE_THRESHOLD = 0.5f; //0.5f
    private static final float HIDE_FRICTION = 0.1f; //0.1f
    private float mMaximumVelocity;

    private int mheight;

    int mMinOffset;

    int mMaxOffset;

    int mRegularOffset;

    boolean mDisableShiftMode;

    public boolean mHideable;

    @State
    int mState = STATE_EXPANDED; //DEFAULT STATE

    ViewDragHelper mViewDragHelper;

    private boolean mIgnoreEvents;

    private int mLastNestedScrollDy;

    private boolean mNestedScrolled;

    int mParentHeight;

    WeakReference<V> mViewRef;

    WeakReference<View> mNestedScrollingChildRef;

    private BottomNavigationCallback mCallback;

    private VelocityTracker mVelocityTracker;

    int mActivePointerId;

    private int mInitialY;

    boolean mTouchingScrollingChild;

    private Snackbar.SnackbarLayout snackbarLayout;

    public Snackbar snackbar;

    private int mSnackbarHeight = -1;



    /**
     * Default constructor for instantiating BottomNavigationViewBehavior based on {@link android.support.design.widget.BottomSheetBehavior]
     */
    public BottomNavigationBehavior() {

    }


    /**
     * Default constructor for inflating BottomSheetBehavior from layout.
     *
     * @param context The {@link Context}.
     * @param attrs   The {@link AttributeSet}.
     */
    public BottomNavigationBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomNavigationViewBehavior_Params);
        setHideable(a.getBoolean(R.styleable.BottomNavigationViewBehavior_Params_behavior_hideable, false));
        DisableshiftMode(a.getBoolean(R.styleable.BottomNavigationViewBehavior_Params_behavior_shift_mode, false)); //todo : specifiy
        a.recycle();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

    }

    @Override
    public Parcelable onSaveInstanceState(CoordinatorLayout parent, V child) {
        return new BottomNavigationBehavior.SavedState(super.onSaveInstanceState(parent, child), mState);
    }

    @Override
    public void onRestoreInstanceState(CoordinatorLayout parent, V child, Parcelable state) {
        BottomNavigationBehavior.SavedState ss = (BottomNavigationBehavior.SavedState) state;
        super.onRestoreInstanceState(parent, child, ss.getSuperState());
        //retrieve state after change like screen orientation change
        // Intermediate states are restored as EXPDANDED state
        if (ss.state == STATE_SETTLING) {
            mState = STATE_EXPANDED;
        } else {
            mState = ss.state;
        }
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            ViewCompat.setFitsSystemWindows(child, true);
        }
        int savedTop = child.getTop();
        // First let the parent lay it out
        parent.onLayoutChild(child, layoutDirection);
        //get parent height
        mParentHeight = parent.getHeight();
        //get height of bottomnavigation
        mheight= parent.getResources().getDimensionPixelSize(R.dimen.bottom_navigation_height);
        //regular offset of the bottomNav
        mRegularOffset = (mParentHeight - mheight);
        //minimum offset of the bottomNav
        mMinOffset = Math.max(0, mParentHeight - child.getHeight());
        //MaxOffset offset of the bottomNav
        mMaxOffset = Math.max(mParentHeight, mMinOffset);

        //TOP OFFSET when bottomnavigationview is expdanded
        if (mState == STATE_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, mMinOffset);
        //TOP OFFSET when bottomnavigationview is hidden
        } else if (mHideable && mState == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, mParentHeight);
        //TOP OFFSET when bottomnavigationview is settling
        } else if (mState == STATE_SETTLING) {
            ViewCompat.offsetTopAndBottom(child, savedTop - child.getTop());
        }
        //able to settling bottomNav
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, myDragCallback);
        }
        //View References
        mViewRef = new WeakReference<>(child);
        mNestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));

        return true;

    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            mIgnoreEvents = true;
            return false;
        }
        int action = MotionEventCompat.getActionMasked(event);
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchingScrollingChild = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                // Reset the ignore flag
                if (mIgnoreEvents) {
                    mIgnoreEvents = false;
                    return false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                int initialX = (int) event.getX();
                mInitialY = (int) event.getY();
                View scroll = mNestedScrollingChildRef.get();
                if (scroll != null && parent.isPointInChildBounds(scroll, initialX, mInitialY)) {
                    mActivePointerId = event.getPointerId(event.getActionIndex());
                    mTouchingScrollingChild = true;
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID &&
                        !parent.isPointInChildBounds(child, initialX, mInitialY);
                break;
        }
        if (!mIgnoreEvents && mViewDragHelper.shouldInterceptTouchEvent(event)) {
            return true;
        }

        // We have to handle cases that the ViewDragHelper does not capture the BottomNavigationView because
        // it is not the top most view of its parent. This is not necessary when the touch event is
        // happening over the scrolling content as nested scrolling logic handles that case.
        View scroll = mNestedScrollingChildRef.get();
        return action == MotionEvent.ACTION_MOVE && scroll != null &&
                !mIgnoreEvents &&
                !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY()) &&
                Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop();
    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            return false;
        }
        int action = MotionEventCompat.getActionMasked(event);
        mViewDragHelper.processTouchEvent(event);
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
        // to capture the bottomnav in case it is not captured and the touch slop is passed.
        //TODO : DELETE IT ?
        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop()) {
                mViewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
            }
        }
        return !mIgnoreEvents;
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, V child,
                                       View directTargetChild, View target, int nestedScrollAxes) {
        mLastNestedScrollDy = 0;
        mNestedScrolled = false;
        return (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed) {
        View scrollingChild = mNestedScrollingChildRef.get();
        if (target != scrollingChild) {
            return;
        }

        dispatchOnSlide(child.getTop());
        mLastNestedScrollDy = dy;
        mNestedScrolled = true;
    }

    @Override
    public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target) {
        if (child.getTop() == mMinOffset) {
            setStateInternal(STATE_EXPANDED);
            return;
        }
        if (target != mNestedScrollingChildRef.get() || !mNestedScrolled) {
            return;
        }
        int top;
        int targetState;

        if (mLastNestedScrollDy > 0) {
            top = mMinOffset;
            targetState = STATE_EXPANDED;
        } else if (mHideable && shouldHide(child, getYVelocity())) {
            top = mParentHeight;
            targetState = STATE_HIDDEN;
        } else if (mLastNestedScrollDy == 0) {
            int currentTop = child.getTop();
            if (Math.abs(currentTop - mMinOffset) < Math.abs(currentTop - mMaxOffset)) {
                top = mMinOffset;
                targetState = STATE_EXPANDED;
            } else {
                top = mMinOffset;
                targetState = STATE_EXPANDED;
            }
        } else {
            top = mMinOffset;
            targetState = STATE_EXPANDED;
        }

        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new BottomNavigationBehavior.SettleRunnable(child, targetState));
        } else {
            setStateInternal(targetState);
        }
        mNestedScrolled = false;

    }

    @Override
    public boolean onNestedPreFling(CoordinatorLayout coordinatorLayout, V child, View target,
                                    float velocityX, float velocityY) {
        return target == mNestedScrollingChildRef.get() &&
                (mState != STATE_EXPANDED ||
                        super.onNestedPreFling(coordinatorLayout, child, target,
                                velocityX, velocityY));
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        if (dyConsumed < 0) {
            setState(STATE_EXPANDED);
        } else if (dyConsumed > 0) {
            if (isHideable() == true) {
                setState(STATE_HIDDEN);
            } else {
                setState(STATE_EXPANDED);
            }
        }


    }


    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, V child, View dependency) {
        if (dependency != null && dependency instanceof Snackbar.SnackbarLayout) {
            setSnackbarPosition(child, dependency);
            return true;
        }
        return super.layoutDependsOn(parent, child, dependency);
    }

    //Give SETTLING ANIMATION to Layout
    private final ViewDragHelper.Callback myDragCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            //for now i disable dragging on bottomnav.. even if its hideable.

            return false;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            dispatchOnSlide(top);
        }

    };

    private class SettleRunnable implements Runnable {
        private final View mView;

        @State
        private final int mTargetState;

        SettleRunnable(View view, @State int targetState) {
            mView = view;
            mTargetState = targetState;
        }

        @Override
        public void run() {
            if (mViewDragHelper != null && mViewDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this);
            } else {
                setStateInternal(mTargetState);
            }
        }
    }

    protected static class SavedState extends AbsSavedState {
        @State
        final int state;

        public SavedState(Parcel source) {
            this(source, null);
        }

        public SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            //noinspection ResourceType
            state = source.readInt();
        }

        public SavedState(Parcelable superState, @State int state) {
            super(superState);
            this.state = state;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(state);
        }

        public static final Creator<BottomNavigationBehavior.SavedState> CREATOR = ParcelableCompat.newCreator(
                new ParcelableCompatCreatorCallbacks<BottomNavigationBehavior.SavedState>() {
                    @Override
                    public BottomNavigationBehavior.SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new BottomNavigationBehavior.SavedState(in, loader);
                    }

                    @Override
                    public BottomNavigationBehavior.SavedState[] newArray(int size) {
                        return new BottomNavigationBehavior.SavedState[size];
                    }
                });
    }






    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable {@code true} to make this bottom sheet hideable.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    public void setHideable(boolean hideable) {
        mHideable = hideable;
    }

    /**
     * Gets whether this bottom sheet can hide when it is swiped down.
     *
     * @return {@code true} if this bottom sheet can hide.
     * @attr ref R.styleable#BottomNavigationViewBehavior_Params_behavior_hideable
     */
    public boolean isHideable() {
        return mHideable;
    }

    //DisableshiftMode
    public void DisableshiftMode(boolean disableshiftmode) {
        mDisableShiftMode = disableshiftmode;
    }

    public boolean isDisableShiftMode() {
        return mDisableShiftMode;
    }

    /**
     * Sets a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    public void setBottomNavigationCallback(BottomNavigationCallback callback) {
        mCallback = callback;
    }

    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of  {@link #STATE_EXPANDED}, or
     *              {@link #STATE_HIDDEN}.
     */
    public final void setState(final @State int state) {
        if (state == mState) {
            return;
        }
        if (mViewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_EXPANDED ||
                    (mHideable && state == STATE_HIDDEN)) {
                mState = state;
            }
            return;
        }
        final V child = mViewRef.get();
        if (child == null) {
            return;
        }
        // Start the animation; wait until a pending layout if there is one.
        ViewParent parent = child.getParent();
        if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
            child.post(new Runnable() {
                @Override
                public void run() {
                    startSettlingAnimation(child, state);
                }
            });
        } else {
            startSettlingAnimation(child, state);
        }
    }

    /**
     * Gets the current state of the bottom sheet.
     *
     * @return One of {@link #STATE_EXPANDED},
     * and {@link #STATE_SETTLING}.
     */
    @State
    public final int getState() {
        return mState;
    }

    void setStateInternal(@State int state) {
        if (mState == state) {
            return;
        }
        mState = state;
        View bottomnav = mViewRef.get();
        if (bottomnav != null && mCallback != null) {
            mCallback.onStateChanged(bottomnav, state);
        }

    }

    private void reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    boolean shouldHide(View child, float yvel) {
        final float newTop = child.getTop() + yvel * HIDE_FRICTION;
        return Math.abs(newTop - mMaxOffset) / (float) mheight > HIDE_THRESHOLD;

    }

    private View findScrollingChild(View view) {
        if (view instanceof NestedScrollingChild) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                View scrollingChild = findScrollingChild(group.getChildAt(i));
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    private float getYVelocity() {
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity); //1000
        return VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId);
    }

    void startSettlingAnimation(View child, int state) {
        int top;
        if (state == STATE_EXPANDED) {
            top = mMinOffset;
        } else if (mHideable && state == STATE_HIDDEN) {
            top = mParentHeight;
        } else {
            //Error...
            throw new IllegalArgumentException("Illegal state argument: " + state);
        }
        setStateInternal(STATE_SETTLING);
        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            ViewCompat.postOnAnimation(child, new BottomNavigationBehavior.SettleRunnable(child, state));
        }
    }


    //useful when snackbar showup -- it reduce risk of sackbar don't follows the bottomnav
    void dispatchOnSlide(int top) {
        View bottomNav = mViewRef.get();
        if (bottomNav != null && mCallback != null) {
            if (top > mMaxOffset) {
                mCallback.onSlide(bottomNav, (float) (mMaxOffset - top) /
                        (mParentHeight - mMaxOffset));
            } else {
                mCallback.onSlide(bottomNav,
                        (float) (mMaxOffset - top) / ((mMaxOffset - mMinOffset)));
            }
        }
    }

    /**
     * Update Snackbar bottom margin
     * todo : some issue on activity on create snackbar doesn't follow bottombar the first time...
     */
    public void setSnackbarPosition(View child, View dependency) {
        float topvariation = child.getTop() - (mRegularOffset) ;
        int bottomSnackBarMargin = (int) -(topvariation+3) + mheight; //add 3px because snackbar not came of very top of bottomnav

        //todo : some bug on swipe animation with this code
        CoordinatorLayout.LayoutParams param = (CoordinatorLayout.LayoutParams) dependency.getLayoutParams();
        param.setMargins(0, 0, 0, bottomSnackBarMargin);
        dependency.setLayoutParams(param);

        //todo -- some issue with fab when snackbar is removing/dismissing... make a callbck help yo change this
        //may be apply this code only when snackbar is showed
        //après étude il apparait que le fab ne sait plus à quelle view il est anchored...

    }


    /**
     * A utility function to get the {@link BottomNavigationBehavior} associated with the {@code view}.
     *
     * @param view The {@link View} with {@link BottomNavigationBehavior}.
     * @return The {@link BottomNavigationBehavior} associated with the {@code view}.
     */
    @SuppressWarnings("unchecked")
    public static <V extends View> BottomNavigationBehavior<V> from(V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params)
                .getBehavior();
        if (!(behavior instanceof BottomNavigationBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated with BottomNavigationViewBehavior");
        }
        return (BottomNavigationBehavior<V>) behavior;
    }


}
