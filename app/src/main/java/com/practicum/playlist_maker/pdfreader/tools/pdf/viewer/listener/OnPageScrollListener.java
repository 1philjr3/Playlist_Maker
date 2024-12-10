package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.listener;

import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.PDFView;

/**
 * Implements this interface to receive events from PDFView
 * when a page has been scrolled
 */
public interface OnPageScrollListener {

    /**
     * Called on every move while scrolling
     *
     * @param page current page index
     * @param positionOffset see {@link PDFView#getPositionOffset()}
     */
    void onPageScrolled(int page, float positionOffset);
}
