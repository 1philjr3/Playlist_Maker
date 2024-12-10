package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.listener;

/**
 * Implement this interface to receive events from PDFView
 * when loading is complete.
 */
public interface OnLoadCompleteListener {

    /**
     * Called when the PDF is loaded
     * @param nbPages the number of pages in this PDF file
     */
    void loadComplete(int nbPages);
}
