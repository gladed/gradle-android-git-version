package com.gladed.androidgitversion

import org.eclipse.jgit.lib.ObjectId

class FormatTest extends AndroidGitVersionTest {
    void testFormat() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        new File(projectFolder.root, "build.gradle").append("// addition 1")
        addCommit()
        new File(projectFolder.root, "build.gradle").append("// addition 2") // Dirty

        plugin.format = '%tag%%!count%%_commit%%.branch%%+dirty%'
        // Should be something like '1.0!1_10a984.release_1.x+dirty'
        assert plugin.name().startsWith('1.0!1_')
        assert plugin.name().endsWith('.release_1.x+dirty')
    }

    void testCleanFormat() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        new File(projectFolder.root, "build.gradle").append("// addition 1")
        addCommit()

        plugin.format = '%tag%%!count%%(commit)%%.branch%%+dirty%'
        // Should be something like '1.0!1_10a984.release_1.x'
        assert plugin.name().startsWith('1.0!1(')
        assert plugin.name().endsWith(').release_1.x')
    }

    void testBranchNameOnGitDescribe() {
        plugin.matchGitDescribe = true
        addCommit()
        addTag("1.0")
        addBranch("feature-foo")
        new File(projectFolder.root, "build.gradle").append("// addition 1")
        addCommit()

        assert plugin.name().startsWith('1.0-1-g')
        assert plugin.name().endsWith('-feature-foo')
    }

    void testDirtyOnGitDescribe() {
        plugin.matchGitDescribe = true
        addCommit()
        addTag("1.0")
        new File(projectFolder.root, "build.gradle").append("// addition 2") // Dirty

        assert plugin.name().equals("1.0-dirty")
    }

    void testDirtyBranchOnGitDescribe() {
        this.plugin.matchGitDescribe = true
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        new File(projectFolder.root, "build.gradle").append("// addition 1")
        addCommit()
        new File(projectFolder.root, "build.gradle").append("// addition 2") // Dirty

        assert plugin.name().startsWith('1.0-1-g')
        assert plugin.name().endsWith('release_1.x-dirty')
    }

    void testLongCommitHash() {
        addCommit()
        addTag("1.4")
        addCommit()
        addCommit()
        plugin.format = "%tag%%-count%%-branch%%-dirty%"
        assertEquals("1.4-2", plugin.name())
    }

    void testHashLength() {
        plugin.commitHashLength = 8
        addCommit()
        addTag("1.0")
        ObjectId commitId = addCommit().toObjectId();
        String hash = ObjectId.toString(commitId)
        assert plugin.name().contains(hash[0..7])
    }
}
