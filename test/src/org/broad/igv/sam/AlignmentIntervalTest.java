/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.sam;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.Locus;
import org.broad.igv.track.RenderContextImpl;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.TestUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.*;

/**
 * User: jacob
 * Date: 2012-Jul-09
 */
public class AlignmentIntervalTest extends AbstractHeadlessTest {

    @Test
    public void testMerge() throws Exception {
        String infilepath = TestUtils.LARGE_DATA_DIR + "HG00171.hg18.bam";
        ResourceLocator locator = new ResourceLocator(infilepath);
        AlignmentDataManager baseManager = new AlignmentDataManager(locator, genome);
        AlignmentDataManager testManager = new AlignmentDataManager(locator, genome);

        String chr = "chr1";
        int start = 151666494;
        int halfwidth = 1024;
        int end = start + 2 * halfwidth;
        Locus locus = new Locus(chr, start, end);


        ReferenceFrame frame = new ReferenceFrame("test");
        frame.jumpTo(locus);

        frame.setBounds(0, halfwidth);
        RenderContextImpl context = new RenderContextImpl(null, null, frame, null);


        //End is not exact due to zooming
        end = (int) frame.getEnd();

        AlignmentTrack.RenderOptions renderOptions = new AlignmentTrack.RenderOptions();
        baseManager.preload(context, renderOptions, false);

        ArrayList<AlignmentInterval> baseIntervals = (ArrayList) baseManager.getLoadedIntervals();
        assertEquals(1, baseIntervals.size());
        AlignmentInterval baseInterval = baseIntervals.get(0);


        Locus begLocus = new Locus(chr, start, start + halfwidth);
        ReferenceFrame begFrame = new ReferenceFrame(frame);
        begFrame.jumpTo(begLocus);
        RenderContextImpl begContext = new RenderContextImpl(null, null, begFrame, null);

        Locus endLocus = new Locus(chr, start + halfwidth / 2, end);
        ReferenceFrame endFrame = new ReferenceFrame(frame);
        endFrame.jumpTo(endLocus);
        RenderContextImpl endContext = new RenderContextImpl(null, null, endFrame, null);

        testManager.preload(begContext, renderOptions, false);
        ArrayList<AlignmentInterval> begInterval = (ArrayList) testManager.getLoadedIntervals();
        assertEquals(1, begInterval.size());

        testManager.clear();
        testManager.preload(endContext, renderOptions, false);
        ArrayList<AlignmentInterval> endInterval = (ArrayList) testManager.getLoadedIntervals();
        assertEquals(1, endInterval.size());
        AlignmentInterval merged = begInterval.get(0);
        merged.merge(endInterval.get(0));

        TestUtils.assertFeatureListsEqual(baseInterval.getCounts().iterator(), merged.getCounts().iterator());

        TestUtils.assertFeatureListsEqual(baseInterval.getDownsampledIntervals().iterator(), merged.getDownsampledIntervals().iterator());

        TestUtils.assertFeatureListsEqual(baseInterval.getAlignmentIterator(), merged.getAlignmentIterator());

    }

    @Test
    public void testTrimTo() throws Exception {

        String chr = "chr1";
        int start = 151666494;
        int halfwidth = 1000;
        int end = start + 2 * halfwidth;
        int panInterval = halfwidth;

        int numPans = AlignmentDataManager.MAX_INTERVAL_MULTIPLE;
        List<AlignmentInterval> intervals = (ArrayList<AlignmentInterval>)
                AlignmentDataManagerTest.performPanning(chr, start, end, numPans, panInterval);
        AlignmentInterval interval = intervals.get(0);
        assertTrue(interval.contains(chr, start, end + numPans * panInterval));


        //Now trim to the middle
        int trimShift = (numPans / 2) * panInterval;
        int trimStart = start + trimShift;
        int trimEnd = end + trimShift;
        assertTrue(interval.trimTo(chr, trimStart, trimEnd, -1));
        assertFalse(interval.trimTo(chr, trimStart, trimEnd, -1));
        assertFalse(interval.trimTo(chr, trimStart, trimEnd, -1));
        assertFalse(interval.trimTo(chr, trimStart, trimEnd, -1));

        assertFalse(interval.contains(chr, start, end + numPans * panInterval));
        assertTrue(interval.contains(chr, trimStart, trimEnd, -1));

    }


}