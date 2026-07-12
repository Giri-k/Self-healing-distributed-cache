package com.cache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "cache.node-id=test-node",
    "cache.peers=",
    "cache.virtual-nodes=150",
    "cache.replication-factor=2",
    "grpc.server.port=0"
})
class CacheNodeApplicationTests {

	@Test
	void contextLoads() {
	}

}
