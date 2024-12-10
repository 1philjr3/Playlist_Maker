package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.listener;

public interface OnRenderListener {

    /**
     * Called only once, when document is rendered
     * @param nbPages number of pages
     */
    void onInitiallyRendered(int nbPages);
}
