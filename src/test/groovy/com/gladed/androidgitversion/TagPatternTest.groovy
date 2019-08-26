package com.gladed.androidgitversion

class TagPatternTest extends AndroidGitVersionTest {
    void testTagPrefix() {
        addCommit()
        addTag("lib-1.0")
        plugin.prefix = "lib-"
        plugin.tagPattern = /^${plugin.prefix}[0-9]+(\\.[0-9]+){0,2}.*/
        assertEquals('1.0', plugin.name())
        assertEquals(1000000, plugin.code())
    }
    void testTagPattern() {
        addCommit()
        addTag("1.0")
        addTag("2.0")
        // Ignore anything that doesn't start with 1
        plugin.tagPattern = /^1\..*/
        assertEquals("1.0", plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testTagVPattern() {
        addCommit()
        addTag("1.0")
        addTag("v2.0")
        plugin.tagPattern = /^v[0-9]+.*/
        assertEquals("v2.0", plugin.name())
        assertEquals(2000000, plugin.code())
    }

    void testUnmatchedPrefixedPattern() {
        addCommit()
        addTag("v1.0")
        plugin.tagPattern = /^v2\\.*/
        plugin.prefix = 'v'
        assert plugin.name().startsWith('untagged-1-')
    }

    void testMultiTagWithPrefix() {
        plugin.prefix 'v'
        addCommit()
        addTag('v1.0.0-rc.1')
        addTag('v1.0.0')
        assertEquals("1.0.0", plugin.name())
    }
}
