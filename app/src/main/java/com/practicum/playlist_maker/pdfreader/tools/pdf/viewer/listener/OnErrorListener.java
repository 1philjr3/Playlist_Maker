package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.listener;

public interface OnErrorListener {

    /**
     * Called if error occurred while opening PDF
     * @param t Throwable with error
     */
    void onError(Throwable t);
}
