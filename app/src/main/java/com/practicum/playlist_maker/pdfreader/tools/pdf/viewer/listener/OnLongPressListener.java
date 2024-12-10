package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.listener;

import android.view.MotionEvent;

/**
 * Implement this interface to receive events from PDFView
 * when view has been long pressed
 */
public interface OnLongPressListener {

    /**
     * Called when the user has a long tap gesture, before processing scroll handle toggling
     *
     * @param e MotionEvent that registered as a confirmed long press
     */
    void onLongPress(MotionEvent e);
}
