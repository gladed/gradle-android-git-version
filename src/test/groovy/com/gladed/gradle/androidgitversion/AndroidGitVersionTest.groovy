package com.gladed.gradle.androidgitversion

import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.rules.TemporaryFolder

class AndroidGitVersionTest extends GroovyTestCase {

    def projectFolder = new TemporaryFolder()

    // These properties don't exist until touched
    @Lazy Git git = { initGit() }()
    @Lazy AndroidGitVersionExtension plugin = { makePlugin() }()

    void testNoGitRepo() {
        assertEquals('unknown', plugin.name())
        assertEquals(0, plugin.code())
    }

    void testNoCommits() {
        git // touch to build
        assertEquals('unknown', plugin.name())
        assertEquals(0, plugin.code())
    }

    void testNoTags() {
        addCommit()
        assert plugin.name().startsWith('untagged-1-')
        assertFalse plugin.name().contains("master") // Due to hideBranches
        assertEquals(0, plugin.code())
    }

    void testTag() {
        addCommit()
        addTag('1.0')
        assertEquals('1.0', plugin.name())
   }

    void testNonVersionTag() {
        addCommit()
        addTag('checkpoint-1')
        assert plugin.name().startsWith('untagged-1-')
        assertEquals(0, plugin.code())
    }

    void testTagPrefix() {
        addCommit()
        addTag("lib-1.0");
        plugin.prefix = "lib-"
        assertEquals('1.0', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testMultiTag() {
        addCommit()
        addTag("1.0")
        addCommit()
        addTag("1.1")
        assertEquals('1.1', plugin.name())
        assertEquals(1001000, plugin.code())
    }

    void testMultiTagOnSameCommit() {
        addCommit()
        addTag("1.10")
        addTag("1.7")
        addTag("1.90")
        addTag("1.8")
        assertEquals('1.90', plugin.name())
        assertEquals(1090000, plugin.code())
    }

    void testCommitsAfterTag() {
        addCommit();
        addTag("1.0")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assertEquals(1000000, plugin.code())
    }

    void testAddDirty() {
        addCommit();
        addTag("1.0")
        new File(projectFolder.root, "build.gradle").append("// addition")
        assertEquals('1.0-dirty', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testLightWeightTag() {
        addCommit();
        addLightweightTag("1.0")
        assertEquals('1.0', plugin.name())
    }

    void testAddNotDirty() {
        addCommit();
        addTag("1.0")
        File otherFile = new File(projectFolder.root, "build.gradle2")
        otherFile.createNewFile()
        otherFile.append("// addition")
        assertEquals('1.0', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testOnlyInTagOutside() {
        // Add a tag outside the onlyIn folder
        addCommit();
        addTag("1.0");

        // Add a commit inside the onlyIn folder
        File subDir = new File(projectFolder.root, "sub")
        subDir.mkdirs()
        File subFile = new File(subDir, "file")
        subFile.createNewFile()
        subFile.append("// addition")
        git.add().addFilepattern("sub/file").call();
        git.commit().setMessage("new subfolder").call();

        plugin.onlyIn = "sub"
        assert plugin.name().startsWith("1.0-1-")
    }

    void testOnlyInChangeOutside() {
        File subDir = new File(projectFolder.root, "sub")
        subDir.mkdirs()
        File subFile = new File(subDir, "file")
        subFile.createNewFile()
        subFile.append("// addition")
        git.add().addFilepattern("sub/file").call();
        git.commit().setMessage("new subfolder").call();
        addTag("1.0")

        // Add another commit after the tag, but outside the onlyIn subfolder
        addCommit()

        plugin.onlyIn = "sub"
        assertEquals('1.0', plugin.name())
    }

    void testWeirdBranchName() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assert plugin.name().endsWith('-release_1.x')
    }

    void testHideBranch() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assert plugin.name().endsWith('-release_1.x')
    }

    void testBaseCode() {
        addCommit()
        addTag("1.1")
        plugin.baseCode = 55555
        assertEquals(1056555, plugin.code())
    }

    void testTagWithJunk() {
        addCommit()
        addTag("1.1-release")
        assertEquals(1001000, plugin.code())
        assertEquals("1.1", plugin.name())
    }

    private Git initGit() {
        return Git.init().setDirectory(projectFolder.root).call();
    }

    private void addCommit() {
        new File(projectFolder.root, "build.gradle").append("// addition")
        git.add().addFilepattern("build.gradle").call()
        git.commit().setMessage("addition").call()
    }

    private void addTag(String tagName) {
        git.tag().setName(tagName).call()
    }

    private void addLightweightTag(String tagName) {
        git.tag().setName(tagName).setAnnotated(false).call()
    }

    private void addBranch(String branchName) {
        git.checkout().setCreateBranch(true).setName(branchName).call()
    }

    private AndroidGitVersionExtension makePlugin() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectFolder.root)
                .build()
        project.pluginManager.apply 'com.gladed.androidgitversion'
        def extension = project.getExtensions().getByName('androidGitVersion')
        assertTrue(extension instanceof AndroidGitVersionExtension)
        return (AndroidGitVersionExtension) extension
    }

    @Override
    protected void setUp() throws Exception {
        projectFolder.create()
        projectFolder.newFile("build.gradle")
//        System.setProperty("user.dir", projectFolder.root.getAbsolutePath())
    }

    @Override
    protected void tearDown() throws Exception {
        projectFolder.delete()
    }
}
