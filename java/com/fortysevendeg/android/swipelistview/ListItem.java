package com.fortysevendeg.android.swipelistview;

/**
 * This holds the information about a list item we have acted upon.
 */
public class ListItem {
    private int position;
    private SwipeDirection swipeDirection;

    private enum SwipeDirection {LEFT, RIGHT, CLOSED}


    public ListItem(final int position) {
        this.position = position;
        this.swipeDirection = SwipeDirection.CLOSED;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public boolean wasSwipedRight() {
        return swipeDirection == SwipeDirection.RIGHT;
    }

    public boolean wasSwipedLeft() {
        return swipeDirection == SwipeDirection.LEFT;
    }

    public void swipeRight() {
        this.swipeDirection = SwipeDirection.RIGHT;
    }

    public void swipeLeft() {
        this.swipeDirection = SwipeDirection.LEFT;
    }

    public void close() {
        this.swipeDirection = SwipeDirection.CLOSED;
    }

    public boolean isOpen() {
        return swipeDirection != SwipeDirection.CLOSED;
    }

}
