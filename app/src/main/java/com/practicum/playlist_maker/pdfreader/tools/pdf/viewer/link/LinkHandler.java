package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.link;

import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.LinkTapEvent;

public interface LinkHandler {

    /**
     * Called when link was tapped by user
     *
     * @param event current event
     */
    void handleLinkEvent(LinkTapEvent event);
}
