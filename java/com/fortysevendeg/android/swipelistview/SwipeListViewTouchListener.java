/*
 * Copyright (C) 2013 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Copyright 2012 Roman Nurik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fortysevendeg.android.swipelistview;

import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;

import java.util.ArrayList;

import static com.nineoldandroids.view.ViewHelper.setAlpha;
import static com.nineoldandroids.view.ViewHelper.setTranslationX;

/**
 * Touch listener impl for the SwipeListView
 */
public class SwipeListViewTouchListener implements View.OnTouchListener {

    private boolean swipeOpenOnLongPress = true;

    private int swipeFrontView = 0;
    private int swipeBackView = 0;

    private Rect rect = new Rect();

    // Cached ViewConfiguration and system-wide constant values
    private int slop;
    private int minFlingVelocity;
    private int maxFlingVelocity;
    private long configShortAnimationTime;
    private long animationTime;

    private float leftOffset = 0;
    private float rightOffset = 0;

    // Fixed properties
    private SwipeListView listView;
    private int viewWidth = 1; // 1 and not 0 to prevent dividing by zero


    private float downX;
    private boolean swiping;
    private VelocityTracker velocityTracker;
    private int downPosition;
    private View parentView;
    private View frontView;
    private View backView;
    private boolean paused;

    private int swipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;

    private int swipeActionLeft = SwipeListView.SWIPE_ACTION_REVEAL;
    private int swipeActionRight = SwipeListView.SWIPE_ACTION_REVEAL;

    private ListItem lastItem;
    private boolean listViewMoving;

    /**
     * Constructor
     *
     * @param listView       SwipeListView
     * @param swipeFrontView front view Identifier
     * @param swipeBackView  back view Identifier
     */
    public SwipeListViewTouchListener(SwipeListView listView, int swipeFrontView, int swipeBackView) {
        this.swipeFrontView = swipeFrontView;
        this.swipeBackView = swipeBackView;
        ViewConfiguration vc = ViewConfiguration.get(listView.getContext());
        slop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        configShortAnimationTime = listView.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
        animationTime = configShortAnimationTime;
        this.listView = listView;
        lastItem = new ListItem(-1);
    }

    /**
     * Sets current item's parent view
     *
     * @param parentView Parent view
     */
    private void setParentView(View parentView) {
        this.parentView = parentView;
    }

    /**
     * Sets current item's front view
     *
     * @param frontView Front view
     */
    private void setFrontView(View frontView) {
        this.frontView = frontView;
        frontView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listView.onClickFrontView(downPosition);
            }
        });
        if (swipeOpenOnLongPress) {
            frontView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    openAnimate(downPosition);
                    return false;
                }
            });
        }
    }

    /**
     * Set current item's back view
     *
     * @param backView Back view
     */
    private void setBackView(View backView) {
        this.backView = backView;
        backView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listView.onClickBackView(downPosition);
            }
        });
    }

    /**
     * @return true if the list is in motion
     */
    public boolean isListViewMoving() {
        return listViewMoving;
    }

    /**
     * Adds new items when adapter is modified
     */
    public void resetItems() {
    }

    /**
     * Open item
     *
     * @param position Position of list
     */
    protected void openAnimate(int position) {
        openAnimate(findFrontViewByPosition(position), position);
    }

    /**
     * Close item
     *
     * @param position Position of list
     */
    protected void closeAnimate(int position) {
        closeAnimate(findFrontViewByPosition(position), position);
    }

    private View findFrontViewByPosition(final int position) {
        final int realPosition = position - listView.getFirstVisiblePosition();
        return listView.getChildAt(realPosition).findViewById(swipeFrontView);
    }

    private View findBackViewByPosition(final int position) {
        final int realPosition = position - listView.getFirstVisiblePosition();
        return listView.getChildAt(realPosition).findViewById(swipeBackView);
    }

    /**
     * Open item
     *
     * @param view     affected view
     * @param position Position of list
     */
    private void openAnimate(View view, int position) {
        if (!isOpen(position)) {
            animateReveal(view, true, false, new ListItem(position));
        }
    }

    /**
     * Close item
     *
     * @param view     affected view
     * @param position Position of list
     */
    private void closeAnimate(View view, int position) {
        if (isOpen(position)) {
            animateReveal(view, true, false, new ListItem(position));
        }
    }

    /**
     * Create animation
     *
     * @param view      affected view
     * @param swap      If state should change. If "false" returns to the original position
     * @param swapRight If swap is true, this parameter tells if move is to the right or left
     * @param position  Position of list
     */
    private void chooseAnimation(final View view, final boolean swap, final boolean swapRight, final int position) {
        ListItem newItem = new ListItem(position);
        if (lastItem.getPosition() == position) newItem = lastItem;

        if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_REVEAL) {
            animateReveal(view, swap, swapRight, newItem);
        }
    }

    private void animateCrush(final ListItem openItem) {
        animateCrush(openItem, null);
    }

    /**
     * Actually dismisses the openItem, leaving the view of the position normal.
     * or if there is another item open, it leaves it open where openItem used to be
     *
     * @param openItem - the item that will be dismissed
     * @param newItem  - item to replace openItem, null if no such object.
     */
    private void animateCrush(final ListItem openItem, final ListItem newItem) {
        final View frontView = findFrontViewByPosition(openItem.getPosition());
        final View backView = findBackViewByPosition(openItem.getPosition());
        final View view = (View) frontView.getParent();
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        final int originalHeight = view.getHeight();

        ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(animationTime);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                lp.height = (Integer) valueAnimator.getAnimatedValue();
                view.setLayoutParams(lp);
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                listView.onDismiss(openItem.getPosition());
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        lp.height = originalHeight;
                        fixLastView();
                        fixNewView();
                        openItem.close();
                    }

                    private void fixNewView() {
                        if (newItem == null) return;
                        int position = newItem.getPosition();
                        final View front = findFrontViewByPosition(position);
                        newItem.setPosition(--position);
                        final View newFront = findFrontViewByPosition(position);
                        final float openX = front.getX();
                        if (position < openItem.getPosition()) return;

                        front.setX(0f);
                        newFront.setX(openX);
                    }

                    private void fixLastView() {
                        backView.setAlpha(1f);
                        frontView.setX(0f);
                    }
                });
            }
        });
        animator.start();
    }

    /**
     * Create reveal animation
     *
     * @param view      affected view
     * @param swap      If will change state. If "false" returns to the original position
     * @param swapRight If swap is true, this parameter tells if movement is toward right or left
     * @param item      the item we wish to act upon
     */
    private void animateReveal(final View view, final boolean swap, final boolean swapRight, final ListItem item) {

        int moveTo = calcSwipeTranslationX(swap, swapRight, item.getPosition());

        ArrayList<Animator> animators = new ArrayList<Animator>();
        animators.add(ObjectAnimator.ofFloat(view, "translationX", view.getX(), moveTo));

        final boolean isLastItemOpenAndNotMe = item.getPosition() != lastItem.getPosition() && lastItem.isOpen();
        if (isLastItemOpenAndNotMe) {
            View openView = findBackViewByPosition(lastItem.getPosition());      // we play with it's alpha.
            animators.add(ObjectAnimator.ofFloat(openView, "alpha", openView.getAlpha(), calculateAlpha((float) moveTo)));
        }

        AnimatorSet set = new AnimatorSet();
        set.setDuration(animationTime);
        set.playTogether(animators);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                listView.resetScrolling();
                if (swap) {
                    if (item.isOpen()) {
                        listView.onClosed(item.getPosition(), item.wasSwipedRight());
                        item.close();
                    } else {
                        listView.onOpened(item.getPosition(), swapRight);
                        if (swapRight) item.swipeRight();
                        else item.swipeLeft();
                    }
                    if (isLastItemOpenAndNotMe) animateCrush(lastItem, item);
                    lastItem = item;
                }
            }
        });
        set.start();
    }


    private int calcSwipeTranslationX(final boolean swap, final boolean swapRight, final int position) {
        int moveTo = 0;
        if (isOpen(position)) {
            if (!swap) {
                moveTo = wasSwipedRight(position) ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);
            }
        } else { //not open
            if (swap) {
                moveTo = swapRight ? (int) (viewWidth - rightOffset) : (int) (-viewWidth + leftOffset);
            }
        }
        return moveTo;
    }

    private float calculateAlpha(final float deltaX) {
        return Math.max(0f, Math.min(1f, 1f - 2f * Math.abs(deltaX) / viewWidth));
    }

    /**
     * Set enabled
     *
     * @param enabled is the view enabled
     */
    public void setEnabled(boolean enabled) {
        paused = !enabled;
    }

    /**
     * Return ScrollListener for ListView
     *
     * @return OnScrollListener
     */
    public AbsListView.OnScrollListener makeScrollListener() {
        return new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
//                    dismissOpenItem();
                    crushOpenItem();
                    listViewMoving = true;
                    setEnabled(false);
                }
                if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_FLING && scrollState != SCROLL_STATE_TOUCH_SCROLL) {
                    listViewMoving = false;
                    listView.resetScrolling();
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            setEnabled(true);
                        }
                    }, 500);
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
            }
        };
    }

    /**
     * dismisses last opened item.
     */
    public void crushOpenItem() {
        if (!lastItem.isOpen()) return;
        animateCrush(lastItem);
    }

    /**
     * Close last opened item if still open, otherwise does nothing.
     */
    public void closeOpenItem() {
        if (!lastItem.isOpen()) return;
        View view = findFrontViewByPosition(lastItem.getPosition());
        animateReveal(view, true, lastItem.wasSwipedLeft(), lastItem);
        lastItem.close();
    }

    /**
     * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (viewWidth < 2) {
            viewWidth = listView.getWidth();
        }

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (paused) {
                    return false;
                }
                swipeCurrentAction = SwipeListView.SWIPE_ACTION_NONE;

                int childCount = listView.getChildCount();
                int[] listViewCoords = new int[2];
                listView.getLocationOnScreen(listViewCoords);
                int x = (int) motionEvent.getRawX() - listViewCoords[0];
                int y = (int) motionEvent.getRawY() - listViewCoords[1];
                View child;
                for (int i = 0; i < childCount; i++) {
                    child = listView.getChildAt(i);
                    child.getHitRect(rect);
                    if (rect.contains(x, y) && listView.getAdapter().isEnabled(listView.getFirstVisiblePosition() + i)) {
                        setParentView(child);
                        setFrontView(child.findViewById(swipeFrontView));
                        downX = motionEvent.getRawX();
                        downPosition = listView.getPositionForView(child);

                        frontView.setClickable(!isOpen(downPosition));
                        frontView.setLongClickable(!isOpen(downPosition));

                        velocityTracker = VelocityTracker.obtain();
                        velocityTracker.addMovement(motionEvent);
                        if (swipeBackView > 0) {
                            setBackView(child.findViewById(swipeBackView));
                        }
                        break;
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (velocityTracker == null || !swiping) {
                    break;
                }

                float deltaX = motionEvent.getRawX() - downX;
                velocityTracker.addMovement(motionEvent);
                velocityTracker.computeCurrentVelocity(1000);
                float velocityX = Math.abs(velocityTracker.getXVelocity());
                if (!isOpen(downPosition)) velocityX = 0;

                float velocityY = Math.abs(velocityTracker.getYVelocity());
                boolean swap = false;
                boolean swapRight = false;
                if (minFlingVelocity <= velocityX && velocityX <= maxFlingVelocity && velocityY < velocityX) {
                    swapRight = velocityTracker.getXVelocity() > 0;
                    if (isOpen(downPosition) && wasSwipedRight(downPosition) && swapRight) {
                        swap = false;
                    } else if (isOpen(downPosition) && !wasSwipedRight(downPosition) && !swapRight) {
                        swap = false; //here as well
                    } else {
                        swap = true;
                    }
                } else if (Math.abs(deltaX) > viewWidth / 2) {
                    swap = true;
                    swapRight = deltaX > 0;
                }
                chooseAnimation(frontView, swap, swapRight, downPosition);

                velocityTracker.recycle();
                velocityTracker = null;
                downX = 0;
                // change clickable front view
                if (swap) {
                    frontView.setClickable(isOpen(downPosition));
                    frontView.setLongClickable(isOpen(downPosition));
                }
                frontView = null;
                backView = null;
                this.downPosition = ListView.INVALID_POSITION;
                swiping = false;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (velocityTracker == null || paused) {
                    break;
                }

                velocityTracker.addMovement(motionEvent);
                velocityTracker.computeCurrentVelocity(1000);
                float velocityX = Math.abs(velocityTracker.getXVelocity());
                float velocityY = Math.abs(velocityTracker.getYVelocity());

                float deltaX = motionEvent.getRawX() - downX;
                float deltaMode = Math.abs(deltaX);

                if (deltaMode > slop && swipeCurrentAction == SwipeListView.SWIPE_ACTION_NONE && velocityY < velocityX) {
                    swiping = true;
                    boolean swipingRight = (deltaX > 0);
                    swipeCurrentAction = SwipeListView.SWIPE_ACTION_REVEAL;
                    if (isOpen(downPosition)) {
                        listView.onStartClose(downPosition, swipingRight);
                    } else {
                        listView.onStartOpen(downPosition, swipeCurrentAction, swipingRight);
                    }
                    listView.requestDisallowInterceptTouchEvent(true);
                    MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL | (motionEvent.getActionIndex() << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                    listView.onTouchEvent(cancelEvent);
                }

                if (swiping) {
                    if (isOpen(downPosition)) {
                        deltaX += wasSwipedRight(downPosition) ? viewWidth - rightOffset : -viewWidth + leftOffset;
                    }
                    move(deltaX);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private boolean wasSwipedRight(final int position) {
        return lastItem.getPosition() == position && lastItem.wasSwipedRight();
    }

    private boolean isOpen(final int position) {
        return lastItem.getPosition() == position && lastItem.isOpen();
    }

    /**
     * Moves the view
     *
     * @param deltaX delta
     */
    public void move(float deltaX) {
        listView.onMove(downPosition, deltaX);
        if (swipeCurrentAction == SwipeListView.SWIPE_ACTION_DISMISS) {
            setTranslationX(parentView, deltaX);
            setAlpha(parentView, Math.max(0f, Math.min(1f, 1f - 2f * Math.abs(deltaX) / viewWidth)));
        } else {
            if (downPosition != lastItem.getPosition() && lastItem.isOpen()) {
                setAlpha(findBackViewByPosition(lastItem.getPosition()), calculateAlpha(deltaX));
            }
            setTranslationX(frontView, deltaX);
        }
    }
}