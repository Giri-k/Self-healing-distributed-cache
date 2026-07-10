package com.cache.ring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    private ConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(150);
    }

    @Test
    void singleNodeOwnsAllKeys() {
        ring.addNode("node-1");
        for (int i = 0; i < 100; i++) {
            assertEquals("node-1", ring.getNode("key-" + i));
        }
    }

    @Test
    void keysDistributeAcrossNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String owner = ring.getNode("key-" + i);
            counts.merge(owner, 1, Integer::sum);
        }

        assertEquals(3, counts.size());
        for (int count : counts.values()) {
            assertTrue(count > 200, "Each node should own at least 200 keys, got " + count);
        }
    }

    @Test
    void addingNodeOnlyRemapsSmallFraction() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            before.put("key-" + i, ring.getNode("key-" + i));
        }

        ring.addNode("node-4");

        int changed = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            if (!before.get(key).equals(ring.getNode(key))) {
                changed++;
            }
        }

        assertTrue(changed < 400, "Adding a node should remap ~1/4 of keys, but remapped " + changed);
    }

    @Test
    void removingNodeRedistributes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        ring.removeNode("node-2");

        for (int i = 0; i < 100; i++) {
            String owner = ring.getNode("key-" + i);
            assertTrue(owner.equals("node-1") || owner.equals("node-3"),
                "Key should map to node-1 or node-3, got " + owner);
        }
    }

    @Test
    void getNodesReturnsDistinctNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        List<String> nodes = ring.getNodes("somekey", 2);
        assertEquals(2, nodes.size());
        assertNotEquals(nodes.get(0), nodes.get(1));
    }
}
