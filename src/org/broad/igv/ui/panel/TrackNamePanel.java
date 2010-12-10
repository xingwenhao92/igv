/*
 * Copyright (c) 2007-2011 by The Broad Institute, Inc. and the Massachusetts Institute of
 * Technology.  All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR
 * WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING,
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES,
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */
/*
 * TrackPanel.java
 *
 * Created on Sep 5, 2007, 4:09:39 PM
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.broad.igv.ui.panel;


import org.apache.log4j.Logger;
import org.broad.igv.track.Track;
import org.broad.igv.track.TrackClickEvent;
import org.broad.igv.track.TrackGroup;
import org.broad.igv.track.TrackMenuUtils;
import org.broad.igv.ui.IGVMainFrame;
import org.broad.igv.ui.UIConstants;
import org.broad.igv.ui.dnd.AbstractGhostDropManager;
import org.broad.igv.ui.dnd.GhostDropEvent;
import org.broad.igv.ui.dnd.GhostDropListener;
import org.broad.igv.ui.dnd.GhostGlassPane;
import org.broad.igv.ui.util.UIUtilities;
import org.jdesktop.layout.GroupLayout;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 */
public class TrackNamePanel extends TrackPanelComponent implements AdjustmentListener, Paintable {

    private static Logger log = Logger.getLogger(TrackNamePanel.class);


    // TODO -- this use of a static is really bad,  bugs and memory leaks waiting to happen.  Redesign this.
    static List<DropListener> dropListeners = new ArrayList();


    static void addGhostDropListener(DropListener listener) {
        if (listener != null) {
            dropListeners.add(listener);
        }
    }

    public static void removeDropListenerFor(TrackNamePanel panel) {
        List<DropListener> removeThese = new ArrayList();
        for (DropListener dl : dropListeners) {
            if (dl.panel == panel) {
                removeThese.add(dl);
            }
        }
        dropListeners.removeAll(removeThese);
    }


    List<GroupExtent> groupExtents = new ArrayList();

    BufferedImage dndImage = null;

    TrackGroup selectedGroup = null;

    boolean showGroupNames = true;

    boolean showSampleNamesWhenGrouped = false;


    public TrackNamePanel(TrackPanel trackPanel) {
        super(trackPanel);
        init();
    }

    Collection<TrackGroup> getGroups() {
        TrackPanel dataTrackView = (TrackPanel) getParent();
        return dataTrackView.getGroups();
    }

    private boolean isGrouped() {
        Collection<TrackGroup> groups = getGroups();
        return groups.size() > 1;
    }


    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        removeMousableRegions();
        Rectangle visibleRect = getVisibleRect();
        paintImpl(g, visibleRect);


    }


    public void paintOffscreen(Graphics2D g, Rectangle rect) {
        g.setColor(Color.white);
        g.fill(rect);
        paintImpl(g, rect);
        super.paintBorder(g);
    }


    private void paintImpl(Graphics g, Rectangle visibleRect) {
        // Get available tracks
        Collection<TrackGroup> groups = getGroups();
        boolean isGrouped = groups.size() > 1;


        if (!groups.isEmpty()) {
            final Graphics2D graphics2D = (Graphics2D) g.create();
            graphics2D.setColor(Color.BLACK);

            final Graphics2D greyGraphics = (Graphics2D) g.create();
            greyGraphics.setColor(UIConstants.VERY_LIGHT_GRAY);

            int regionY = 0;

            groupExtents.clear();
            for (Iterator<TrackGroup> groupIter = groups.iterator(); groupIter.hasNext();) {
                TrackGroup group = groupIter.next();

                if (group.isVisible()) {
                    if (isGrouped) {
                        if (regionY + UIConstants.groupGap >= visibleRect.y && regionY < visibleRect.getMaxY()) {
                            greyGraphics.fillRect(0, regionY + 1, getWidth(), UIConstants.groupGap - 1);
                        }
                        regionY += UIConstants.groupGap;
                    }

                    if (group.isDrawBorder() && regionY + UIConstants.groupGap >= visibleRect.y &&
                            regionY < visibleRect.getMaxY()) {
                        g.drawLine(0, regionY - 1, getWidth(), regionY - 1);
                    }

                    int y = regionY;
                    regionY = printTrackNames(group, visibleRect, graphics2D, 0, regionY);

                    if (isGrouped) {
                        int h = group.getHeight();
                        groupExtents.add(new GroupExtent(group, y, y + h));
                        if (showGroupNames) {
                            Rectangle rect = new Rectangle(visibleRect.x, y, visibleRect.width, h);
                            Rectangle displayableRect = getDisplayableRect(rect, visibleRect);
                            group.renderName(graphics2D, displayableRect, group == selectedGroup);
                        }
                    }

                    if (group.isDrawBorder()) {
                        g.drawLine(0, regionY, getWidth(), regionY);
                    }
                }

            }
        }
    }

    private Rectangle getDisplayableRect(Rectangle trackRectangle, Rectangle visibleRect) {
        Rectangle rect = null;
        if (visibleRect != null) {
            Rectangle intersectedRect = trackRectangle.intersection(visibleRect);
            if (intersectedRect.height > 15) {
                rect = intersectedRect;
            } else {
                rect = new Rectangle(trackRectangle);
            }
        }
        return rect;

    }

    private int printTrackNames(TrackGroup group, Rectangle visibleRect, Graphics2D graphics2D, int regionX, int regionY) {

        List<Track> tmp = new ArrayList(group.getTracks());
        for (Track track : tmp) {
            track.setTop(regionY);
            int trackHeight = track.getHeight();
            if (track.isVisible()) {

                if (regionY + trackHeight >= visibleRect.y && regionY < visibleRect.getMaxY()) {
                    int width = getWidth();
                    int height = track.getHeight();

                    Rectangle region = new Rectangle(regionX, regionY, width, height);
                    addMousableRegion(new MouseableRegion(region, track));

                    if (!isGrouped() || showSampleNamesWhenGrouped) {
                        Rectangle rect = new Rectangle(regionX, regionY, width, height);
                        track.renderName(graphics2D, rect, visibleRect);
                    }

                }
                regionY += trackHeight;
            }
        }
        return regionY;
    }


    private void init() {

        setBorder(javax.swing.BorderFactory.createLineBorder(Color.black));
        setBackground(new java.awt.Color(255, 255, 255));
        GroupLayout dataTrackNamePanelLayout = new org.jdesktop.layout.GroupLayout(this);
        setLayout(dataTrackNamePanelLayout);
        dataTrackNamePanelLayout.setHorizontalGroup(
                dataTrackNamePanelLayout.createParallelGroup(GroupLayout.LEADING).add(0, 148, Short.MAX_VALUE));
        dataTrackNamePanelLayout.setVerticalGroup(
                dataTrackNamePanelLayout.createParallelGroup(GroupLayout.LEADING).add(0, 528, Short.MAX_VALUE));

        NamePanelMouseAdapter mouseAdapter = new NamePanelMouseAdapter();
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        DropListener dndListener = new DropListener(this);
        addGhostDropListener(dndListener);
    }


    @Override
    protected void openPopupMenu(TrackClickEvent te) {
        MouseEvent e = te.getMouseEvent();

        // If there is a single selected track give it a chance to handle the click
        Collection<Track> selectedTracs = getSelectedTracks();
        String title = getPopupMenuTitle(0, 0);

        JPopupMenu menu = TrackMenuUtils.getEmptyPopup(title);

        if (isGrouped()) {
            final JMenuItem item = new JCheckBoxMenuItem("Show group names");
            item.setSelected(showGroupNames);
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    showGroupNames = item.isSelected();
                    clearTrackSelections();
                    repaint();
                }
            });
            menu.add(item);

            final JMenuItem item2 = new JCheckBoxMenuItem("Show sample names");
            item2.setSelected(showSampleNamesWhenGrouped);
            item2.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    showSampleNamesWhenGrouped = item2.isSelected();
                    clearTrackSelections();
                    repaint();
                }
            });
            menu.add(item2);
            menu.addSeparator();
        }

        TrackMenuUtils.addStandardItems(menu, selectedTracs, te);

        menu.show(e.getComponent(), e.getX(), e.getY());

    }


    public String getPopupMenuTitle(int x, int y) {

        Collection<Track> tracks = getSelectedTracks();

        String popupTitle;
        if (tracks.size() == 1) {
            popupTitle = tracks.iterator().next().getName();
        } else {
            popupTitle = "Total Tracks Selected: " + tracks.size();
        }

        return popupTitle;
    }


    public String getTooltipTextForLocation(int x, int y) {

        List<MouseableRegion> mouseableRegions = TrackNamePanel.this.getTrackRegions();

        for (MouseableRegion mouseableRegion : mouseableRegions) {
            if (mouseableRegion.containsPoint(x, y)) {
                return mouseableRegion.getText();
            }
        }
        return "";
    }

    /**
     * Listener for scroll pane events
     *
     * @param evt
     */
    public void adjustmentValueChanged(AdjustmentEvent evt) {
        if (evt.getValueIsAdjusting()) {
            // The user is dragging the knob
            return;
        }
        repaint();

    }

    private synchronized void createDnDImage() {
        dndImage = new BufferedImage(getWidth(), 2, BufferedImage.TYPE_INT_ARGB);
        Graphics g = dndImage.getGraphics();
        g.setColor(Color.blue);
        g.drawLine(1, 0, getWidth() - 2, 0);
        g.drawLine(1, 1, getWidth() - 2, 1);

    }

    protected void shiftSelectTracks(MouseEvent e) {
        for (MouseableRegion mouseRegion : trackRegions) {
            if (mouseRegion.containsPoint(e.getX(), e.getY())) {
                Collection<Track> clickedTracks = mouseRegion.getTracks();
                if (clickedTracks != null && clickedTracks.size() > 0) {
                    Track t = clickedTracks.iterator().next();
                    IGVMainFrame.getInstance().getTrackManager().shiftSelectTracks(t);
                }
                return;
            }
        }
    }


    private TrackGroup getGroup(int y) {
        for (GroupExtent ge : groupExtents) {
            if (ge.contains(y)) {
                return ge.group;
            }
        }
        return null;
    }


    /**
     * Mouse adapter for the track name panel.  Supports multiple selection,
     * popup menu, and drag & drop within or between name panels.
     */
    class NamePanelMouseAdapter extends MouseInputAdapter {

        boolean isDragging = false;
        List<Track> dragTracks = new ArrayList();
        Point dragStart = null;

        @Override
        /**
         * Mouse down.  Track selection logic goes here.
         */
        public void mousePressed(MouseEvent e) {

            if (log.isDebugEnabled()) {
                log.debug("Enter mousePressed");
            }

            dragStart = e.getPoint();

            requestFocus();
            grabFocus();

            boolean isGrouped = isGrouped();

            if (e.isPopupTrigger()) {
                if (isGrouped) {

                } else if (!isTrackSelected(e)) {
                    clearTrackSelections();
                    selectTracks(e);
                }
                TrackClickEvent te = new TrackClickEvent(e, null);
                openPopupMenu(te);
            } // meta (mac) or control,  toggle selection]
            else if (e.getButton() == MouseEvent.BUTTON1) {

                if (isGrouped) {
                    clearTrackSelections();
                    TrackGroup g = getGroup(e.getY());
                    if (g == selectedGroup) {
                        selectedGroup = null;
                    } else {
                        selectGroup(getGroup(e.getY()));
                    }
                } else {
                    if (e.isMetaDown() || e.isControlDown()) {
                        toggleTrackSelections(e);
                    } else if (e.isShiftDown()) {
                        shiftSelectTracks(e);
                    } else if (!isTrackSelected(e)) {
                        clearTrackSelections();
                        selectTracks(e);
                    }
                }
            } else {
                if (isGrouped) {

                } else if (!isTrackSelected(e)) {
                    clearTrackSelections();
                    selectTracks(e);
                }
            }


            IGVMainFrame.getInstance().repaintNamePanels();

        }

        public void mouseReleased(MouseEvent e) {

            if (log.isDebugEnabled()) {
                log.debug("Enter mouseReleased");
            }

            if (isDragging) {


                Component c = e.getComponent();

                IGVMainFrame.getInstance().endDnD();
                GhostGlassPane glassPane = IGVMainFrame.getInstance().getDnDGlassPane();

                Point p = (Point) e.getPoint().clone();
                SwingUtilities.convertPointToScreen(p, c);

                Point eventPoint = (Point) p.clone();
                SwingUtilities.convertPointFromScreen(p, glassPane);

                glassPane.setPoint(p);
                glassPane.setVisible(false);
                glassPane.setImage(null);

                fireGhostDropEvent(new GhostDropEvent(dragStart, eventPoint, dragTracks));

                if (selectedGroup != null) {
                    int idx = getGroupGapNumber(e.getY());
                    TrackPanel dataTrackView = (TrackPanel) getParent();
                    dataTrackView.moveGroup(selectedGroup, idx);
                    dataTrackView.repaint();
                }
                selectedGroup = null;


            }

            if (e.isPopupTrigger()) {
                TrackClickEvent te = new TrackClickEvent(e, null);
                openPopupMenu(te);
            } else {
                if (!isDragging && !e.isMetaDown() && !e.isControlDown() &&
                        !e.isShiftDown()) {
                    clearTrackSelections();
                    selectTracks(e);
                    IGVMainFrame.getInstance().repaintNamePanels();
                }
            }

            isDragging = false;
            dragTracks.clear();
            dndImage = null;


        }


        public void mouseDragged(MouseEvent e) {

            Component c = e.getComponent();
            if (e.isPopupTrigger()) {
                return;
            }
            if (!isDragging) {

                if (dragStart == null) {
                    dragStart = e.getPoint();
                    return;
                } else if (e.getPoint().distance(dragStart) < 5) {
                    return;
                }

                dragStart.x = getWidth() / 2;
                IGVMainFrame.getInstance().startDnD();

                if (dndImage == null) {
                    createDnDImage();
                }
                IGVMainFrame.getInstance().getDnDGlassPane().setImage(dndImage);
                isDragging = true;
                dragTracks.clear();
                dragTracks.addAll(IGVMainFrame.getInstance().getTrackManager().getSelectedTracks());


                if (getGroups().size() > 0) {
                    selectedGroup = getGroup(e.getY());
                } else {
                    selectedGroup = null;
                }

                // Code below paints target component on the dndImage.  It needs modified to paint some representation
                // of the selectect tracks, probably the track names printed as a list.
            }
            if (isDragging) {

                final GhostGlassPane glassPane = IGVMainFrame.getInstance().getDnDGlassPane();

                Point p = (Point) e.getPoint().clone();
                p.x = getWidth() / 2;
                SwingUtilities.convertPointToScreen(p, c);
                SwingUtilities.convertPointFromScreen(p, glassPane);

                glassPane.setPoint(p);

                UIUtilities.invokeOnEventThread(new Runnable() {

                    public void run() {
                        Rectangle bounds = new Rectangle(getBounds());
                        bounds.height = 10000;
                        glassPane.paintImmediately(bounds);
                    }
                });
            }
        }

        public void mouseMoved(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            setToolTipText(getTooltipTextForLocation(x, y));
        }

        protected void fireGhostDropEvent(GhostDropEvent evt) {
            Iterator it = TrackNamePanel.dropListeners.iterator();
            while (it.hasNext()) {
                ((GhostDropListener) it.next()).ghostDropped(evt);
            }
        }
    }


    class DropListener extends AbstractGhostDropManager {

        TrackNamePanel panel;

        public DropListener(TrackNamePanel target) {
            super(target);
            this.panel = target;

        }

        public void ghostDropped(GhostDropEvent e) {
            Point startPoint = e.getStartLocation();
            Point dropPoint = getTranslatedPoint(e.getDropLocation());


            Rectangle bounds = component.getVisibleRect();
            boolean isInTarget = dropPoint.y > bounds.y && dropPoint.y < bounds.getMaxY();

            if (isInTarget) {
                tracksDropped(startPoint, dropPoint, e.getTracks());
                e.removeTracksFromSource();
                e.setTracksDropped(true);
            } else {
                TrackPanel view = ((TrackPanel) getParent());
                if (e.isTracksDropped()) {
                    view.removeTracks(e.getTracks());
                } else {
                    // Defer removal until we are sure the tracks are dropped in another panel
                    e.addSourcePanel(view);
                }
            }
        }

        void tracksDropped(Point startPoint, Point dropPoint, List<Track> tracks) {

            // This cast is horrid but we can't fix everything at once.
            TrackPanel view = ((TrackPanel) getParent());
            List<MouseableRegion> regions = getTrackRegions();


            // Find the regions containing the startPoint and point
            boolean before = true;
            MouseableRegion dropRegion = null;
            MouseableRegion startRegion = null;
            for (MouseableRegion region : regions) {
                if (region.containsPoint(dropPoint.x, dropPoint.y)) {
                    dropRegion = region;
                    Rectangle bnds = dropRegion.getBounds();
                    int dy1 = (dropPoint.y - bnds.y);
                    int dy2 = bnds.height - dy1;
                    before = dy1 < dy2;
                }
                if (region.containsPoint(startPoint.x, startPoint.y)) {
                    startRegion = region;
                }
                if (dropRegion != null && startRegion != null) {
                    break;
                }
            }


            Track dropTrack = null;
            if (dropRegion != null) {
                Iterator<Track> tmp = dropRegion.getTracks().iterator();
                if (tmp.hasNext()) {
                    dropTrack = tmp.next();
                }
            }
            view.moveSelectedTracksTo(tracks, dropTrack, before);


        }
    }

    private void selectGroup(TrackGroup group) {
        selectedGroup = group;
        for (Track t : selectedGroup.getTracks()) {
            t.setSelected(true);
        }
    }


    class GroupExtent {
        TrackGroup group;
        int minY;
        int maxY;

        GroupExtent(TrackGroup group, int minY, int maxY) {
            this.group = group;
            this.maxY = maxY;
            this.minY = minY;
        }

        boolean contains(int y) {
            return y > minY && y <= maxY;
        }

        boolean isAfter(int y) {
            return minY > y;
        }
    }

    int getGroupGapNumber(int y) {
        for (int i = 0; i < groupExtents.size(); i++) {
            if (groupExtents.get(i).isAfter(y)) {
                return i;
            }
        }
        return groupExtents.size();
    }


}
