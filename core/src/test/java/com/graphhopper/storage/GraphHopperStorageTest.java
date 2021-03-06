/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class GraphHopperStorageTest extends AbstractGraphStorageTester
{
    private GraphStorage gs;

    @Override
    public void setUp()
    {
        super.setUp();
        if (gs != null)
            gs.close();
    }

    @Override
    public GraphStorage createGraph( String location, int size )
    {
        // reduce segment size in order to test the case where multiple segments come into the game
        GraphStorage gs = newGraph(new RAMDirectory(location));
        gs.setSegmentSize(size / 2);
        gs.create(size);
        return gs;
    }

    protected GraphStorage newGraph( Directory dir )
    {
        return new GraphHopperStorage(dir, encodingManager);
    }

    protected GraphStorage createGraphStorage( Directory dir )
    {
        return newGraph(dir).create(defaultSize);
    }

    @Test
    public void testNoCreateCalled() throws IOException
    {
        GraphHopperStorage gs = (GraphHopperStorage) new GraphBuilder(encodingManager).build();
        try
        {
            gs.ensureNodeIndex(123);
            assertFalse("AssertionError should be raised", true);
        } catch (AssertionError err)
        {
            assertTrue(true);
        } catch (Exception ex)
        {
            assertFalse("AssertionError should be raised", true);
        }
        gs.close();
    }

    @Test
    public void testSave_and_fileFormat() throws IOException
    {
        GraphStorage graph = createGraphStorage(new RAMDirectory(defaultGraph, true));
        graph.setNode(0, 10, 10);
        graph.setNode(1, 11, 20);
        graph.setNode(2, 12, 12);

        EdgeIteratorState iter2 = graph.edge(0, 1, 100, true);
        iter2.setWayGeometry(Helper.createPointList(1.5, 1, 2, 3));
        EdgeIteratorState iter1 = graph.edge(0, 2, 200, true);
        iter1.setWayGeometry(Helper.createPointList(3.5, 4.5, 5, 6));
        graph.edge(9, 10, 200, true);
        graph.edge(9, 11, 200, true);
        graph.edge(1, 2, 120, false);

        iter1.setName("named street1");
        iter2.setName("named street2");

        checkGraph(graph);
        graph.flush();
        graph.close();

        graph = newGraph(new MMapDirectory(defaultGraph));
        assertTrue(graph.loadExisting());

        assertEquals(12, graph.getNodes());
        checkGraph(graph);

        assertEquals("named street1", graph.getEdgeProps(iter1.getEdge(), iter1.getAdjNode()).getName());
        assertEquals("named street2", graph.getEdgeProps(iter2.getEdge(), iter2.getAdjNode()).getName());
        graph.edge(3, 4, 123, true).setWayGeometry(Helper.createPointList(4.4, 5.5, 6.6, 7.7));
        checkGraph(graph);
        graph.close();
    }

    protected void checkGraph( Graph g )
    {
        assertEquals(new BBox(10, 20, 10, 12), g.getBounds());
        assertEquals(10, g.getLatitude(0), 1e-2);
        assertEquals(10, g.getLongitude(0), 1e-2);
        EdgeExplorer explorer = g.createEdgeExplorer(carOutFilter);
        assertEquals(2, GHUtility.count(explorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(2, 1), GHUtility.getNeighbors(explorer.setBaseNode(0)));

        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(Helper.createPointList(3.5, 4.5, 5, 6), iter.fetchWayGeometry(0));

        assertTrue(iter.next());
        assertEquals(Helper.createPointList(1.5, 1, 2, 3), iter.fetchWayGeometry(0));
        assertEquals(Helper.createPointList(10, 10, 1.5, 1, 2, 3), iter.fetchWayGeometry(1));
        assertEquals(Helper.createPointList(1.5, 1, 2, 3, 11, 20), iter.fetchWayGeometry(2));

        assertEquals(11, g.getLatitude(1), 1e-2);
        assertEquals(20, g.getLongitude(1), 1e-2);
        assertEquals(2, GHUtility.count(explorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(2, 0), GHUtility.getNeighbors(explorer.setBaseNode(1)));

        assertEquals(12, g.getLatitude(2), 1e-2);
        assertEquals(12, g.getLongitude(2), 1e-2);
        assertEquals(1, GHUtility.count(explorer.setBaseNode(2)));

        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(2)));

        EdgeIteratorState eib = GHUtility.getEdge(g, 1, 2);
        assertEquals(Helper.createPointList(), eib.fetchWayGeometry(0));
        assertEquals(Helper.createPointList(11, 20), eib.fetchWayGeometry(1));
        assertEquals(Helper.createPointList(12, 12), eib.fetchWayGeometry(2));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(2)));
    }

    @Test
    public void internalDisconnect()
    {
        GraphHopperStorage tmpGS = (GraphHopperStorage) createGraph();
        EdgeIteratorState iter0 = tmpGS.edge(0, 1, 10, true);
        EdgeIteratorState iter2 = tmpGS.edge(1, 2, 10, true);
        EdgeIteratorState iter3 = tmpGS.edge(0, 3, 10, true);

        EdgeExplorer explorer = tmpGS.createEdgeExplorer();

        assertEquals(GHUtility.asSet(3, 1), GHUtility.getNeighbors(explorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(2, 0), GHUtility.getNeighbors(explorer.setBaseNode(1)));
        // remove edge "1-2" but only from 1 not from 2
        tmpGS.internalEdgeDisconnect(iter2.getEdge(), -1, iter2.getBaseNode(), iter2.getAdjNode());
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(1), GHUtility.getNeighbors(explorer.setBaseNode(2)));
        // let 0 unchanged -> no side effects
        assertEquals(GHUtility.asSet(3, 1), GHUtility.getNeighbors(explorer.setBaseNode(0)));

        // remove edge "0-1" but only from 0
        tmpGS.internalEdgeDisconnect(iter0.getEdge(), (long) iter3.getEdge() * tmpGS.edgeEntryBytes, iter0.getBaseNode(), iter0.getAdjNode());
        assertEquals(GHUtility.asSet(3), GHUtility.getNeighbors(explorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(3)));
        assertEquals(GHUtility.asSet(0), GHUtility.getNeighbors(explorer.setBaseNode(1)));
        tmpGS.close();
    }

    @Test
    public void testEnsureSize()
    {
        Directory dir = new RAMDirectory();
        gs = new GraphHopperStorage(dir, encodingManager).create(defaultSize);
        int testIndex = dir.find("edges").getSegmentSize() * 3;
        gs.edge(0, testIndex, 10, true);

        // test if optimize works without error
        gs.optimize();
    }

    @Test
    public void testBigDataEdge()
    {
        Directory dir = new RAMDirectory();
        GraphHopperStorage tmpGS = new GraphHopperStorage(dir, encodingManager);
        tmpGS.create(defaultSize);
        tmpGS.setEdgeCount(Integer.MAX_VALUE / 2);
        assertTrue(tmpGS.getAllEdges().next());
        tmpGS.close();
    }

    @Test
    public void testDetachEdge()
    {
        Directory dir = new RAMDirectory();
        gs = new GraphHopperStorage(dir, encodingManager).create(defaultSize);
        gs.edge(0, 1, 2, true);
        gs.edge(0, 2, 2, true);
        gs.edge(1, 2, 2, true);

        EdgeIterator iter = gs.createEdgeExplorer().setBaseNode(0);
        try
        {
            // currently not possible to implement without a new property inside EdgeIterable
            iter.detach();
            assertTrue(false);
        } catch (Exception ex)
        {
        }

        iter.next();
        EdgeIteratorState iter2 = iter.detach();
        assertEquals(2, iter.getAdjNode());
        assertEquals(2, iter2.getAdjNode());

        iter.next();
        assertEquals(1, iter.getAdjNode());
        assertEquals(2, iter2.getAdjNode());
    }
}
